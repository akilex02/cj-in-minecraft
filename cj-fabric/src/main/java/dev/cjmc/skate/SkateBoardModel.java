package dev.cjmc.skate;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cjmc.Mat4;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skate's own skateboard (deck, trucks and wheels), exported from the user's Skate 3 files by tools/make_skate_rig.py into skate_board.json and its PNGs. It is skinned
 * with the board's bones of the pose, in the skater's frame (the axes of the engine, which are Minecraft's), so it is under his feet when he rides and in his hand
 * when he walks with it. Render thread only.
 */
final class SkateBoardModel {
    private static final Logger LOG = LoggerFactory.getLogger("cj_fabric");
    /** The skater's joints in the pose come one place later than in the rig (bone 0 of the pose is the root of the animation). */
    private static final int POSE_SHIFT = 1;

    private record Part(String texture, float[] positions, float[] normals, float[] uvs, int[] joints, float[] weights, int[] indices) {}

    private final Path dir;
    private final List<Part> parts;
    private final float[] inverseBinds; // 16 per joint of the skater, column-major
    private final int joints;
    private final List<Identifier> textures = new ArrayList<>();
    private boolean uploaded;
    private final float[] skin;
    private final float[] scratch = new float[16];

    private SkateBoardModel(Path dir, List<Part> parts, float[] inverseBinds, int joints) {
        this.dir = dir;
        this.parts = parts;
        this.inverseBinds = inverseBinds;
        this.joints = joints;
        this.skin = new float[16 * joints];
    }

    /** skate_board.json of the folder, or null when it is not there (the mod then draws a board of its own). */
    static SkateBoardModel load(Path file) {
        if (!Files.isRegularFile(file)) {
            LOG.info("no {}: the skateboard is drawn with blocks (run tools/make_skate_rig.py)", file);
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            JsonObject binds = root.getAsJsonObject("inverseBinds");
            int joints = 0;
            for (String key : binds.keySet()) {
                joints = Math.max(joints, Integer.parseInt(key) + 1);
            }
            float[] inverse = new float[16 * joints];
            for (int j = 0; j < joints; j++) {
                Mat4.identity(inverse, 16 * j);
            }
            for (String key : binds.keySet()) {
                float[] m = floats(binds.getAsJsonArray(key));
                System.arraycopy(m, 0, inverse, 16 * Integer.parseInt(key), 16);
            }
            List<Part> parts = new ArrayList<>();
            for (var element : root.getAsJsonArray("parts")) {
                JsonObject p = element.getAsJsonObject();
                int[] partJoints = ints(p.getAsJsonArray("joints"));
                for (int j : partJoints) {
                    if (j < 0 || j >= joints) {
                        throw new IllegalArgumentException("a vertex of the board hangs on joint " + j + ", which has no inverse bind");
                    }
                }
                parts.add(new Part(p.get("texture").isJsonNull() ? null : p.get("texture").getAsString(), floats(p.getAsJsonArray("positions")),
                        floats(p.getAsJsonArray("normals")), floats(p.getAsJsonArray("uvs")), partJoints, floats(p.getAsJsonArray("weights")),
                        ints(p.getAsJsonArray("indices"))));
            }
            LOG.info("loaded Skate's skateboard: {} parts", parts.size());
            return new SkateBoardModel(file.getParent(), parts, inverse, joints);
        } catch (IOException | RuntimeException e) {
            LOG.warn("could not read {}; the skateboard is drawn with blocks", file, e);
            return null;
        }
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsFloat();
        }
        return out;
    }

    private static int[] ints(JsonArray a) {
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsInt();
        }
        return out;
    }

    private void upload() {
        uploaded = true;
        MinecraftClient client = MinecraftClient.getInstance();
        for (int i = 0; i < parts.size(); i++) {
            String name = parts.get(i).texture();
            Identifier id = Identifier.of("cj_fabric", "skate/board_" + i);
            try {
                NativeImage image = name == null ? null : NativeImage.read(new ByteArrayInputStream(Files.readAllBytes(dir.resolve(name))));
                if (image != null) {
                    client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
                    textures.add(id);
                    continue;
                }
            } catch (IOException | RuntimeException e) {
                LOG.warn("the board's texture {} is unreadable", name, e);
            }
            textures.add(Identifier.of("minecraft", "textures/block/dark_oak_planks.png"));
        }
    }

    /** The board in the skater's frame (as the stack has it): each vertex moved by the pose of the board's bones. */
    void draw(MatrixStack.Entry entry, VertexConsumerProvider vcp, int light, float[] bones, int boneCount) {
        if (!uploaded) {
            upload();
        }
        for (int j = 0; j < joints; j++) {
            if (j + POSE_SHIFT < boneCount) {
                System.arraycopy(bones, 16 * (j + POSE_SHIFT), scratch, 0, 16);
                Mat4.mul(scratch, 0, inverseBinds, 16 * j, skin, 16 * j);
            } else {
                Mat4.identity(skin, 16 * j);
            }
        }
        for (int p = 0; p < parts.size(); p++) {
            Part part = parts.get(p);
            VertexConsumer out = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(textures.get(p)));
            int[] idx = part.indices();
            for (int t = 0; t + 2 < idx.length; t += 3) {
                for (int corner = 0; corner < 4; corner++) { // the entity layers draw quads: the triangle's last corner twice
                    int v = idx[t + Math.min(corner, 2)];
                    float x = 0, y = 0, z = 0, nx = 0, ny = 0, nz = 0;
                    float px = part.positions()[3 * v], py = part.positions()[3 * v + 1], pz = part.positions()[3 * v + 2];
                    float qx = part.normals()[3 * v], qy = part.normals()[3 * v + 1], qz = part.normals()[3 * v + 2];
                    for (int k = 0; k < 4; k++) {
                        float w = part.weights()[4 * v + k];
                        if (w == 0f) {
                            continue;
                        }
                        int o = 16 * part.joints()[4 * v + k];
                        x += w * (skin[o] * px + skin[o + 4] * py + skin[o + 8] * pz + skin[o + 12]);
                        y += w * (skin[o + 1] * px + skin[o + 5] * py + skin[o + 9] * pz + skin[o + 13]);
                        z += w * (skin[o + 2] * px + skin[o + 6] * py + skin[o + 10] * pz + skin[o + 14]);
                        nx += w * (skin[o] * qx + skin[o + 4] * qy + skin[o + 8] * qz);
                        ny += w * (skin[o + 1] * qx + skin[o + 5] * qy + skin[o + 9] * qz);
                        nz += w * (skin[o + 2] * qx + skin[o + 6] * qy + skin[o + 10] * qz);
                    }
                    float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (len > 1e-6f) {
                        nx /= len;
                        ny /= len;
                        nz /= len;
                    } else {
                        nx = 0;
                        ny = 1;
                        nz = 0;
                    }
                    out.vertex(entry, x, y, z).color(255, 255, 255, 255).texture(part.uvs()[2 * v], part.uvs()[2 * v + 1])
                            .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, ny, nz);
                }
            }
        }
    }
}
