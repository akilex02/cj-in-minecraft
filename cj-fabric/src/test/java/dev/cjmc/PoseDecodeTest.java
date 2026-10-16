package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PoseDecodeTest {
    private static final Path GOLDEN = Path.of("..", "protocol", "golden", "pose_v2.bin");

    private static byte[] golden() throws Exception {
        return Files.readAllBytes(GOLDEN);
    }

    @Test
    void decodesTheGoldenPose() throws Exception {
        byte[] data = golden();
        Protocol.Pose pose = Protocol.decodePose(data, data.length);
        assertNotNull(pose);
        assertEquals(7, pose.ackSeq());
        assertEquals(4321, pose.gtaTimeMs());
        assertEquals(0, pose.flags());
        assertEquals(1.5f, pose.heading(), 2 * Math.PI / 65536); // the golden pose faces 1.5 rad (counter-clockwise from north)
        assertEquals(3, pose.bones().length);
        assertEquals(1, pose.bones()[0].nodeId());
        assertEquals(0.1f, pose.bones()[0].pz());
        assertEquals(53, pose.bones()[2].nodeId());
        assertEquals(0.1f, pose.bones()[2].px());
        assertEquals(0.2f, pose.bones()[2].py());
        assertEquals(-0.9f, pose.bones()[2].pz());
        assertEquals(0.5f, pose.bones()[2].qx());
        assertEquals(0.5f, pose.bones()[2].qw());
    }

    @Test
    void rejectsMalformedDatagrams() throws Exception {
        // Review Focus #1
        byte[] good = golden();
        assertNull(Protocol.decodePose(good, 10));                  // shorter than the header
        assertNull(Protocol.decodePose(good, good.length - 1));     // a bone is cut off
        byte[] longer = java.util.Arrays.copyOf(good, good.length + 1);
        assertNull(Protocol.decodePose(longer, longer.length));     // trailing byte

        byte[] badMagic = good.clone();
        badMagic[0] ^= (byte) 0xFF;
        assertNull(Protocol.decodePose(badMagic, badMagic.length));

        byte[] badVersion = good.clone();
        badVersion[4] = 3;
        assertNull(Protocol.decodePose(badVersion, badVersion.length));

        byte[] wrongType = good.clone();
        wrongType[6] = 1; // State
        assertNull(Protocol.decodePose(wrongType, wrongType.length));

        byte[] tooMany = good.clone();
        tooMany[20] = 49; // boneCount (u16 at offset 20)
        assertNull(Protocol.decodePose(tooMany, tooMany.length));
    }

    @Test
    void rejectsNonFiniteNumbers() throws Exception {
        // Review Focus #1: a NaN or an infinity must never reach the renderer
        for (float bad : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            for (int field : new int[] {4, 8, 12, 16, 20, 24, 28}) { // px, py, pz, qx, qy, qz, qw of the first bone
                byte[] data = golden();
                ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putFloat(Protocol.POSE_HEADER_SIZE + field, bad);
                assertNull(Protocol.decodePose(data, data.length), "field " + field + " = " + bad);
            }
        }
    }

    @Test
    void acceptsTheLargestPose() {
        int count = Protocol.POSE_MAX_BONES;
        ByteBuffer b = ByteBuffer.allocate(Protocol.MAX_DATAGRAM).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(Protocol.MAGIC).putShort(Protocol.VERSION).putShort(Protocol.TYPE_POSE);
        b.putInt(1).putInt(2).putInt(0).putShort((short) count).putShort((short) 0);
        for (int i = 0; i < count; i++) {
            b.putInt(i).putFloat(0).putFloat(0).putFloat(0).putFloat(0).putFloat(0).putFloat(0).putFloat(1);
        }
        Protocol.Pose pose = Protocol.decodePose(b.array(), b.capacity());
        assertNotNull(pose);
        assertEquals(count, pose.bones().length);
        assertEquals(1560, Protocol.MAX_DATAGRAM);
    }
}
