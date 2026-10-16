package dev.cjmc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The skinned CJ read from cj.glb (written by tools/convert_assets.py), in GTA's model space: X forward, Y left, Z up. */
public final class GlbModel {
    public record Part(String name, float[] positions, float[] normals, float[] uvs, int[] indices,
                       int[] joints, float[] weights, int image) {
        public int vertexCount() {
            return positions.length / 3;
        }
    }

    private static final int GLB_MAGIC = 0x46546C67;
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;

    public final List<Part> parts;
    public final int[] jointNodeIds;
    public final float[] inverseBind;
    public final List<byte[]> images;
    /** How far to raise the model (along Z) so that the lowest point of its bind pose touches the ground. */
    public final float groundOffset;
    /** What the converter wrote in the file's asset.extras (the places of a vehicle's wheels...): an empty object when there is none. */
    public final JsonObject assetExtras;

    private GlbModel(List<Part> parts, int[] jointNodeIds, float[] inverseBind, List<byte[]> images, float groundOffset) {
        this(parts, jointNodeIds, inverseBind, images, groundOffset, new JsonObject());
    }

    private GlbModel(List<Part> parts, int[] jointNodeIds, float[] inverseBind, List<byte[]> images, float groundOffset, JsonObject assetExtras) {
        this.parts = Collections.unmodifiableList(parts);
        this.jointNodeIds = jointNodeIds;
        this.inverseBind = inverseBind;
        this.images = Collections.unmodifiableList(images);
        this.groundOffset = groundOffset;
        this.assetExtras = assetExtras;
    }

    /** The same model with other parts (the vehicle splits its parts into the body and the wheels). */
    public GlbModel withParts(List<Part> other) {
        return new GlbModel(new ArrayList<>(other), jointNodeIds, inverseBind, new ArrayList<>(images), groundOffset, assetExtras);
    }

    public static GlbModel read(byte[] data) {
        if (data.length < 28) {
            throw bad("the file is too short to be a GLB");
        }
        ByteBuffer file = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (file.getInt(0) != GLB_MAGIC) {
            throw bad("not a GLB file");
        }
        if (file.getInt(4) != 2 || file.getInt(8) != data.length) {
            throw bad("unsupported or truncated GLB");
        }
        int jsonLength = file.getInt(12);
        if (file.getInt(16) != CHUNK_JSON || jsonLength < 0 || 20L + jsonLength + 8 > data.length) {
            throw bad("the GLB has no JSON chunk");
        }
        int binStart = 20 + jsonLength;
        int binLength = file.getInt(binStart);
        if (file.getInt(binStart + 4) != CHUNK_BIN || binLength < 0 || (long) binStart + 8 + binLength > data.length) {
            throw bad("the GLB has no BIN chunk");
        }
        JsonObject gltf;
        try {
            gltf = JsonParser.parseString(new String(data, 20, jsonLength, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            throw bad("the JSON chunk is not valid: " + e.getMessage());
        }
        ByteBuffer bin = ByteBuffer.wrap(data, binStart + 8, binLength).slice().order(ByteOrder.LITTLE_ENDIAN);
        try {
            return build(gltf, bin);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) { // a missing field, a wrong type, an index out of range...
            throw bad("the model is malformed (" + e + ")");
        }
    }

    private static GlbModel build(JsonObject g, ByteBuffer bin) {
        JsonObject skin = g.getAsJsonArray("skins").get(0).getAsJsonObject();
        JsonArray jointNodes = skin.getAsJsonArray("joints");
        int jointCount = jointNodes.size();
        int[] nodeIds = new int[jointCount];
        for (int i = 0; i < jointCount; i++) {
            JsonObject node = g.getAsJsonArray("nodes").get(jointNodes.get(i).getAsInt()).getAsJsonObject();
            nodeIds[i] = node.getAsJsonObject("extras").get("nodeId").getAsInt();
            for (int k = 0; k < i; k++) {
                if (nodeIds[k] == nodeIds[i]) {
                    throw bad("joints " + k + " and " + i + " share the node id " + nodeIds[i]);
                }
            }
        }
        float[] inverseBind = floats(g, bin, skin.get("inverseBindMatrices").getAsInt(), "MAT4");
        if (inverseBind.length != 16 * jointCount) {
            throw bad("the skin has " + jointCount + " joints but " + inverseBind.length / 16 + " inverse bind matrices");
        }

        List<byte[]> images = new ArrayList<>();
        for (JsonElement e : g.getAsJsonArray("images")) {
            images.add(viewBytes(g, bin, e.getAsJsonObject().get("bufferView").getAsInt()));
        }

        JsonArray partNames = null;
        JsonObject extras = g.getAsJsonObject("asset").getAsJsonObject("extras");
        if (extras != null && extras.has("parts")) {
            partNames = extras.getAsJsonArray("parts");
        }
        List<Part> parts = new ArrayList<>();
        float minZ = Float.MAX_VALUE;
        JsonArray primitives = g.getAsJsonArray("meshes").get(0).getAsJsonObject().getAsJsonArray("primitives");
        for (int p = 0; p < primitives.size(); p++) {
            JsonObject prim = primitives.get(p).getAsJsonObject();
            JsonObject attrs = prim.getAsJsonObject("attributes");
            float[] positions = floats(g, bin, attrs.get("POSITION").getAsInt(), "VEC3");
            int n = positions.length / 3;
            float[] normals = floats(g, bin, attrs.get("NORMAL").getAsInt(), "VEC3");
            float[] uvs = floats(g, bin, attrs.get("TEXCOORD_0").getAsInt(), "VEC2");
            int[] joints = ints(g, bin, attrs.get("JOINTS_0").getAsInt(), "VEC4");
            float[] weights = floats(g, bin, attrs.get("WEIGHTS_0").getAsInt(), "VEC4");
            int[] indices = ints(g, bin, prim.get("indices").getAsInt(), "SCALAR");
            String name = partNames != null && p < partNames.size() ? partNames.get(p).getAsString() : "part" + p;
            if (normals.length != 3 * n || uvs.length != 2 * n || joints.length != 4 * n || weights.length != 4 * n) {
                throw bad(name + ": the vertex attributes do not have the same count");
            }
            if (indices.length % 3 != 0) {
                throw bad(name + ": the index count is not a multiple of 3");
            }
            for (int i : indices) {
                if (i < 0 || i >= n) {
                    throw bad(name + ": an index points past the last vertex");
                }
            }
            for (int j : joints) {
                if (j < 0 || j >= jointCount) {
                    throw bad(name + ": a vertex uses joint " + j + " but the skeleton has " + jointCount);
                }
            }
            for (int i = 2; i < positions.length; i += 3) {
                minZ = Math.min(minZ, positions[i]);
            }
            int material = prim.get("material").getAsInt();
            int texture = g.getAsJsonArray("materials").get(material).getAsJsonObject()
                    .getAsJsonObject("pbrMetallicRoughness").getAsJsonObject("baseColorTexture").get("index").getAsInt();
            int image = g.getAsJsonArray("textures").get(texture).getAsJsonObject().get("source").getAsInt();
            if (image < 0 || image >= images.size()) {
                throw bad(name + ": its texture points at image " + image + " which does not exist");
            }
            parts.add(new Part(name, positions, normals, uvs, indices, joints, weights, image));
        }
        if (parts.isEmpty()) {
            throw bad("the model has no parts");
        }
        return new GlbModel(parts, nodeIds, inverseBind, images, -minZ, extras == null ? new JsonObject() : extras);
    }

    private static int componentSize(int componentType) {
        return switch (componentType) {
            case 5121 -> 1;
            case 5123 -> 2;
            case 5125, 5126 -> 4;
            default -> throw bad("unsupported component type " + componentType);
        };
    }

    private static int width(String type) {
        return switch (type) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            case "MAT4" -> 16;
            default -> throw bad("unsupported accessor type " + type);
        };
    }

    /** Position of the first byte of an accessor inside the BIN chunk, after checking that all of it fits. */
    private static int accessorStart(JsonObject g, ByteBuffer bin, JsonObject acc, int bytes) {
        JsonObject view = g.getAsJsonArray("bufferViews").get(acc.get("bufferView").getAsInt()).getAsJsonObject();
        long start = (long) view.get("byteOffset").getAsInt() + (acc.has("byteOffset") ? acc.get("byteOffset").getAsInt() : 0);
        if (start < 0 || start + bytes > bin.capacity() || bytes > view.get("byteLength").getAsInt()) {
            throw bad("an accessor does not fit in the binary data");
        }
        return (int) start;
    }

    private static float[] floats(JsonObject g, ByteBuffer bin, int index, String type) {
        JsonObject acc = g.getAsJsonArray("accessors").get(index).getAsJsonObject();
        if (acc.get("componentType").getAsInt() != 5126 || !acc.get("type").getAsString().equals(type)) {
            throw bad("accessor " + index + " is not float " + type);
        }
        int count = acc.get("count").getAsInt() * width(type);
        int start = accessorStart(g, bin, acc, count * 4);
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            out[i] = bin.getFloat(start + 4 * i);
        }
        return out;
    }

    private static int[] ints(JsonObject g, ByteBuffer bin, int index, String type) {
        JsonObject acc = g.getAsJsonArray("accessors").get(index).getAsJsonObject();
        int component = acc.get("componentType").getAsInt();
        if (component == 5126 || !acc.get("type").getAsString().equals(type)) {
            throw bad("accessor " + index + " is not an integer " + type);
        }
        int size = componentSize(component);
        int count = acc.get("count").getAsInt() * width(type);
        int start = accessorStart(g, bin, acc, count * size);
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            int at = start + size * i;
            out[i] = switch (size) {
                case 1 -> bin.get(at) & 0xFF;
                case 2 -> bin.getShort(at) & 0xFFFF;
                default -> bin.getInt(at);
            };
        }
        return out;
    }

    private static byte[] viewBytes(JsonObject g, ByteBuffer bin, int viewIndex) {
        JsonObject view = g.getAsJsonArray("bufferViews").get(viewIndex).getAsJsonObject();
        int start = view.get("byteOffset").getAsInt();
        int length = view.get("byteLength").getAsInt();
        if (start < 0 || length < 0 || (long) start + length > bin.capacity()) {
            throw bad("an image does not fit in the binary data");
        }
        byte[] out = new byte[length];
        bin.get(start, out);
        return out;
    }

    private static IllegalArgumentException bad(String message) {
        return new IllegalArgumentException("cj.glb: " + message);
    }
}
