package dev.cjmc.skate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cjmc.JointFrame;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SkateRigTest {
    private static final int[] NODES = {0, 1, 2, 3, 4, 5, 6, 7, 8, 201, 31, 32, 33, 34, 35, 36, 21, 22, 23, 24, 25, 26, 301, 302, 41, 42, 43, 44, 51, 52, 53, 54};

    /** CJ at rest: every bone turned like the world, the position of a bone is made by its node id; the left foot is where the test says. */
    private static float[] cjInverseBind(java.util.function.IntFunction<float[]> positionOf) {
        float[] ib = new float[16 * NODES.length];
        for (int j = 0; j < NODES.length; j++) {
            float[] p = positionOf.apply(NODES[j]);
            ib[16 * j] = ib[16 * j + 5] = ib[16 * j + 10] = ib[16 * j + 15] = 1f;
            ib[16 * j + 12] = -p[0];
            ib[16 * j + 13] = -p[1];
            ib[16 * j + 14] = -p[2];
        }
        return ib;
    }

    private static float[] rest(int node) {
        return switch (node) {
            case 32 -> new float[] {0f, 0.2f, 0.4f};
            case 33 -> new float[] {0f, 0.4f, 0.4f};
            case 43 -> new float[] {0f, 0.17f, -0.934f};
            case 53 -> new float[] {0f, -0.17f, -0.934f};
            default -> new float[] {0f, 0f, 0f};
        };
    }

    private static float[] identityBones() {
        float[] bones = new float[16 * 36];
        for (int b = 0; b < 36; b++) {
            bones[16 * b] = bones[16 * b + 5] = bones[16 * b + 10] = bones[16 * b + 15] = 1f;
        }
        setTranslation(bones, 1, 0f, 1f, 0f); // the hips, a metre over the board
        setTranslation(bones, 19, -0.17f, 0.066f, 0f); // the right ankle (z_cj -0.934 + 1 = 0.066, y_cj = -0.17 -> x of the engine)
        setTranslation(bones, 23, 0.17f, 0.066f, 0f); // the left ankle
        return bones;
    }

    private static void setTranslation(float[] bones, int bone, float x, float y, float z) {
        bones[16 * bone + 12] = x;
        bones[16 * bone + 13] = y;
        bones[16 * bone + 14] = z;
    }

    private static float[] identityRest() {
        float[] r = new float[9 * 35];
        for (int j = 0; j < 35; j++) {
            r[9 * j] = r[9 * j + 4] = r[9 * j + 8] = 1f;
        }
        return r;
    }

    private static SkateRig rig() {
        return new SkateRig(identityRest(), new float[3 * 35], NODES, cjInverseBind(SkateRigTest::rest));
    }

    private static int indexOfLinear(int node) {
        for (int i = 0; i < NODES.length; i++) {
            if (NODES[i] == node) {
                return i;
            }
        }
        throw new IllegalArgumentException();
    }

    @Test
    void theSkaterAtRestLeavesCjAtRestRaisedToTheHips() {
        JointFrame frame = new JointFrame(NODES.length);
        SkateRig rig = rig();
        assertTrue(rig.usable());
        rig.pose(identityBones(), 36, frame);
        for (int node : NODES) {
            int j = indexOfLinear(node);
            float[] r = rest(node);
            assertTrue(frame.has[j]);
            assertArrayEquals(new float[] {r[0], r[1], r[2] + 1f}, Arrays.copyOfRange(frame.pos, 3 * j, 3 * j + 3), 1e-5f, "bone " + node);
            assertArrayEquals(new float[] {0, 0, 0, 1}, Arrays.copyOfRange(frame.quat, 4 * j, 4 * j + 4), 1e-5f, "bone " + node);
        }
    }

    @Test
    void theAnklesOfCjFollowThoseOfTheSkater() {
        float[] bones = identityBones();
        setTranslation(bones, 19, -0.17f, 0.166f, 0f);
        setTranslation(bones, 23, 0.17f, 0.166f, 0f); // both ankles 10 cm higher
        JointFrame frame = new JointFrame(NODES.length);
        rig().pose(bones, 36, frame);
        int hips = indexOfLinear(1);
        assertEquals(1.1f, frame.pos[3 * hips + 2], 1e-5f);
    }

    @Test
    void anArmTurnedAboutTheVerticalTurnsCjsArmAboutHisVerticalAndCarriesTheForearm() {
        float[] bones = identityBones();
        // the left arm of the skater is bone 13 + 1 of the pose; a quarter turn about the engine's up axis (y): x -> -z, z -> x
        int b = 14;
        float[] m = {0, 0, -1, 0, 0, 1, 0, 0, 1, 0, 0, 0}; // columns of the rotation (x axis goes to -z, y stays, z goes to x)
        System.arraycopy(m, 0, bones, 16 * b, 3);
        bones[16 * b] = 0;
        bones[16 * b + 1] = 0;
        bones[16 * b + 2] = -1;
        bones[16 * b + 4] = 0;
        bones[16 * b + 5] = 1;
        bones[16 * b + 6] = 0;
        bones[16 * b + 8] = 1;
        bones[16 * b + 9] = 0;
        bones[16 * b + 10] = 0;
        JointFrame frame = new JointFrame(NODES.length);
        rig().pose(bones, 36, frame);
        int arm = indexOfLinear(32), forearm = indexOfLinear(33);
        // a quarter turn about CJ's z (up): the quaternion has |z| = |w| = sqrt(1/2)
        assertEquals(0f, frame.quat[4 * arm], 1e-5f);
        assertEquals(0f, frame.quat[4 * arm + 1], 1e-5f);
        assertEquals(Math.sqrt(0.5), Math.abs(frame.quat[4 * arm + 2]), 1e-4f);
        assertEquals(Math.sqrt(0.5), Math.abs(frame.quat[4 * arm + 3]), 1e-4f);
        // the forearm hangs 0.2 along y from the arm at rest; turned a quarter about z it hangs along x
        float dx = frame.pos[3 * forearm] - frame.pos[3 * arm];
        float dy = frame.pos[3 * forearm + 1] - frame.pos[3 * arm + 1];
        assertEquals(0f, dy, 1e-5f);
        assertEquals(0.2f, Math.abs(dx), 1e-5f);
    }

    @Test
    void aSkeletonMissingABoneIsNotUsable() {
        SkateRig rig = new SkateRig(identityRest(), new float[3 * 35], Arrays.copyOf(NODES, NODES.length - 1), cjInverseBind(SkateRigTest::rest).length > 0 ? Arrays.copyOf(cjInverseBind(SkateRigTest::rest), 16 * (NODES.length - 1)) : null);
        assertTrue(!rig.usable());
    }
}
