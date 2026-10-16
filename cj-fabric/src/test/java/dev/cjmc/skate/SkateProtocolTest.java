package dev.cjmc.skate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The datagrams of the Skate engine's host mode (skate-host/README.md), against bytes made by tools/make_skate_golden.py. */
class SkateProtocolTest {
    private static final Path GOLDEN = Path.of("..", "protocol", "golden");
    private static final float[] ACTIONS = {0.5f, -0.25f, 0f, 1f, -1f, 0f, 0.75f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 0f};
    private static final float[] FLOOR = {-1, 2, -1, 1, 2, -1, 1, 2, 1, -1, 2, 1};
    private static final float[] WALL = {1, 2, 4.5f, -1, 2, 4.5f, -1, 3, 4.5f, 1, 3, 4.5f};

    private static byte[] golden(String name) throws Exception {
        return Files.readAllBytes(GOLDEN.resolve(name));
    }

    private static float[] both(float[] a, float[] b) {
        float[] out = new float[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    @Test
    void anInputIsTheBytesOfTheGolden() throws Exception {
        assertArrayEquals(golden("skate_input_v1.bin"), SkateProtocol.encodeInput(7, ACTIONS));
    }

    @Test
    void aWorldRegionIsTheBytesOfTheGolden() throws Exception {
        assertArrayEquals(golden("skate_world_v1.bin"), SkateProtocol.encodeWorldSet(9, both(FLOOR, WALL), 2));
    }

    @Test
    void aClearAndATeleportAreTheBytesOfTheirGoldens() throws Exception {
        assertArrayEquals(golden("skate_clear_v1.bin"), SkateProtocol.encodeWorldClear(9));
        assertArrayEquals(golden("skate_teleport_v1.bin"), SkateProtocol.encodeTeleport(1f, 2.5f, 3f, 0.5f, 0.25f, 0f, -0.5f));
    }

    @Test
    void aPoseDecodesFromTheGolden() throws Exception {
        byte[] data = golden("skate_pose_v1.bin");
        SkateProtocol.Pose pose = SkateProtocol.decodePose(data, data.length);
        assertNotNull(pose);
        assertEquals(77, pose.tick());
        assertEquals(3, pose.boneCount());
        assertEquals(1.0f, pose.root()[4]); // 4 / 4
        assertEquals(0.5f, pose.deckBasis()[4]); // 4 / 8
        assertArrayEquals(new float[] {1.5f, 2.0f, -3.25f}, pose.deckPosition());
        assertEquals(8.0f, pose.bone(1)[0]); // (0 + 16) / 2
        assertEquals(7.5f, pose.bone(0)[15]); // 15 / 2
    }

    @Test
    void aBrokenPoseIsRefused() throws Exception {
        byte[] data = golden("skate_pose_v1.bin");
        assertNull(SkateProtocol.decodePose(data, data.length - 1));
        byte[] magic = data.clone();
        magic[0] ^= 1;
        assertNull(SkateProtocol.decodePose(magic, magic.length));
        byte[] type = data.clone();
        type[6] = 1; // an input is not a pose
        assertNull(SkateProtocol.decodePose(type, type.length));
        byte[] count = data.clone();
        count[12] = 9; // nine bones declared, three carried
        assertNull(SkateProtocol.decodePose(count, count.length));
    }

    @Test
    void theInputOfTheOtherSideIsKeptInRange() {
        float[] wild = new float[18];
        wild[0] = 5f;
        wild[1] = Float.NaN;
        byte[] data = SkateProtocol.encodeInput(1, wild);
        float first = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN).getFloat(16);
        float second = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN).getFloat(20);
        assertEquals(1f, first); // clamped
        assertEquals(0f, second); // a NaN is no input at all
    }

    @Test
    void whatTheEngineWouldRefuseIsNotEvenSent() {
        assertThrows(IllegalArgumentException.class, () -> SkateProtocol.encodeInput(1, new float[17]));
        assertThrows(IllegalArgumentException.class, () -> SkateProtocol.encodeWorldSet(1, new float[12 * 1001], 1001)); // more than 1000 quads
        assertThrows(IllegalArgumentException.class, () -> SkateProtocol.encodeWorldSet(1, new float[11], 1)); // fewer floats than quads
        float[] far = FLOOR.clone();
        far[0] = 2.0e5f;
        assertThrows(IllegalArgumentException.class, () -> SkateProtocol.encodeWorldSet(1, far, 1)); // beyond the engine's reach
    }
}
