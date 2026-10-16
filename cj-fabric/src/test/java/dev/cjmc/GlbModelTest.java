package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GlbModelTest {
    static byte[] mini() throws IOException {
        try (InputStream in = GlbModelTest.class.getResourceAsStream("/mini.glb")) {
            return in.readAllBytes();
        }
    }

    @Test
    void readsPartsSkeletonAndTexture() throws IOException {
        GlbModel m = GlbModel.read(mini());
        assertEquals(2, m.parts.size());
        GlbModel.Part torso = m.parts.get(0);
        assertEquals("torso", torso.name());
        assertEquals(4, torso.vertexCount());
        assertArrayEquals(new float[] {0, 0, 0, 1, 0, 0.5f, 0, 2, -1, 1, 2, 0}, torso.positions());
        assertArrayEquals(new float[] {0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1}, torso.normals());
        assertArrayEquals(new float[] {0, 0, 1, 0, 0, 1, 1, 1}, torso.uvs());
        assertArrayEquals(new int[] {0, 1, 2, 2, 1, 3}, torso.indices());
        assertArrayEquals(new int[] {0, 0, 0, 0, 1, 2, 0, 0, 2, 0, 0, 0, 1, 0, 0, 0}, torso.joints());
        assertArrayEquals(new float[] {1, 0, 0, 0, 0.25f, 0.75f, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0}, torso.weights());
        assertEquals("hands", m.parts.get(1).name());
        assertEquals(0, torso.image());

        assertArrayEquals(new int[] {0, 5, 1}, m.jointNodeIds);
        assertEquals(48, m.inverseBind.length);
        assertArrayEquals(new float[] {0, 1, 0, 0, -1, 0, 0, 0, 0, 0, 1, 0, 1, 2, 3, 1},
                java.util.Arrays.copyOfRange(m.inverseBind, 0, 16));
        assertArrayEquals(new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1},
                java.util.Arrays.copyOfRange(m.inverseBind, 16, 32));

        assertEquals(1, m.images.size());
        assertEquals((byte) 0x89, m.images.get(0)[0]);
        assertEquals("PNG", new String(m.images.get(0), 1, 3, StandardCharsets.US_ASCII));
        assertEquals(1.0f, m.groundOffset); // the lowest vertex is at z = -1
    }

    @Test
    void rejectsFilesThatAreNotValidGlb() throws IOException {
        // Review Focus #2
        byte[] good = mini();
        assertThrows(IllegalArgumentException.class, () -> GlbModel.read(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> GlbModel.read("not a glb file at all, just text".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> GlbModel.read(java.util.Arrays.copyOf(good, good.length - 5)));
        assertThrows(IllegalArgumentException.class, () -> GlbModel.read(java.util.Arrays.copyOf(good, 30)));
        byte[] wrongVersion = good.clone();
        wrongVersion[4] = 1;
        assertThrows(IllegalArgumentException.class, () -> GlbModel.read(wrongVersion));
    }

    @Test
    void rejectsDataThatDoesNotMatchItsDeclarations() throws IOException {
        // Review Focus #2: a joint index past the skeleton, or an index past the vertices, must not reach the renderer
        byte[] good = mini();
        ByteBuffer b = ByteBuffer.wrap(good).order(ByteOrder.LITTLE_ENDIAN);
        int jsonLength = b.getInt(12);
        int binStart = 20 + jsonLength + 8;
        // the first accessor written after the inverse bind matrices and 4 images is not easy to locate by offset,
        // so corrupt the whole binary chunk: every joint byte becomes 200 and every index 60000
        byte[] corrupt = good.clone();
        for (int i = binStart; i < good.length; i++) {
            corrupt[i] = (byte) 0xC8;
        }
        assertThrows(IllegalArgumentException.class, () -> GlbModel.read(corrupt));
    }

    /** Overwrites the first element of one vertex attribute (or the indices) in the binary chunk with `value`. */
    private static byte[] corruptFirstByteOf(String attribute, int value) throws IOException {
        byte[] data = mini();
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int jsonLength = b.getInt(12);
        com.google.gson.JsonObject gltf = com.google.gson.JsonParser
                .parseString(new String(data, 20, jsonLength, StandardCharsets.UTF_8)).getAsJsonObject();
        com.google.gson.JsonObject prim = gltf.getAsJsonArray("meshes").get(0).getAsJsonObject()
                .getAsJsonArray("primitives").get(0).getAsJsonObject();
        int accessor = attribute.equals("indices") ? prim.get("indices").getAsInt()
                : prim.getAsJsonObject("attributes").get(attribute).getAsInt();
        int view = gltf.getAsJsonArray("accessors").get(accessor).getAsJsonObject().get("bufferView").getAsInt();
        int offset = gltf.getAsJsonArray("bufferViews").get(view).getAsJsonObject().get("byteOffset").getAsInt();
        data[20 + jsonLength + 8 + offset] = (byte) value;
        return data;
    }

    @Test
    void rejectsAJointPastTheSkeleton() throws IOException {
        // Review Focus #2: only the first vertex's first joint is wrong (the skeleton has 3 joints)
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GlbModel.read(corruptFirstByteOf("JOINTS_0", 9)));
        assertTrue(e.getMessage().contains("joint"), e.getMessage());
    }

    @Test
    void rejectsAnIndexPastTheVertices() throws IOException {
        // Review Focus #2: only the first index is wrong (the part has 4 vertices); indices are 16-bit, so the low byte is enough
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GlbModel.read(corruptFirstByteOf("indices", 200)));
        assertTrue(e.getMessage().contains("index"), e.getMessage());
    }

    @Test
    void theUncorruptedModelStillReads() throws IOException {
        // the helper itself must not be what breaks the file: writing the original value back changes nothing
        assertEquals(2, GlbModel.read(corruptFirstByteOf("JOINTS_0", 0)).parts.size());
    }

    @Test
    void theGroundOffsetLiftsTheLowestPointToZero() throws IOException {
        GlbModel m = GlbModel.read(mini());
        float min = Float.MAX_VALUE;
        for (GlbModel.Part p : m.parts) {
            for (int i = 2; i < p.positions().length; i += 3) {
                min = Math.min(min, p.positions()[i]);
            }
        }
        assertEquals(0.0f, min + m.groundOffset);
        assertTrue(m.groundOffset > 0);
    }

    @Test
    void rejectsTwoJointsWithTheSameNodeId() throws IOException {
        // review: with a duplicate the renderer's node->joint map would leave one joint without a pose
        byte[] data = mini();
        int jsonLength = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
        String json = new String(data, 20, jsonLength, StandardCharsets.UTF_8);
        String edited = json.replace("\"nodeId\":5", "\"nodeId\":1");
        assertTrue(!edited.equals(json) && edited.length() == json.length());
        System.arraycopy(edited.getBytes(StandardCharsets.UTF_8), 0, data, 20, jsonLength);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> GlbModel.read(data));
        assertTrue(e.getMessage().contains("node"), e.getMessage());
    }
}
