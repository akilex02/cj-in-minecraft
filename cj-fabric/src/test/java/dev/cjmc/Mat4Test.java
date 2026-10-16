package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class Mat4Test {
    private static float[] transformPoint(float[] m, float x, float y, float z) {
        return new float[] {m[0] * x + m[4] * y + m[8] * z + m[12], m[1] * x + m[5] * y + m[9] * z + m[13],
                m[2] * x + m[6] * y + m[10] * z + m[14]};
    }

    @Test
    void identityTimesAnythingIsItself() {
        float[] a = new float[16];
        Mat4.fromPose(1, 2, 3, 0, 0, 0.7071068f, 0.7071068f, a, 0);
        float[] id = new float[16];
        Mat4.identity(id, 0);
        float[] out = new float[16];
        Mat4.mul(id, 0, a, 0, out, 0);
        assertArrayEquals(a, out, 1e-6f);
        Mat4.mul(a, 0, id, 0, out, 0);
        assertArrayEquals(a, out, 1e-6f);
    }

    @Test
    void aPoseRotatesThenTranslates() {
        float[] m = new float[16];
        Mat4.fromPose(10, 20, 30, 0, 0, 0.7071068f, 0.7071068f, m, 0); // 90 degrees about Z, then move
        float[] p = transformPoint(m, 1, 0, 0);
        assertArrayEquals(new float[] {10, 21, 30}, p, 1e-5f); // (1,0,0) -> (0,1,0), then + (10,20,30)
        float[] q = transformPoint(m, 0, 1, 0);
        assertArrayEquals(new float[] {9, 20, 30}, q, 1e-5f);  // (0,1,0) -> (-1,0,0)
    }

    @Test
    void theColumnsOfAPoseAreTheImagesOfTheAxes() {
        // Review Focus #9 relies on this: the bridge sends the columns (right, up, at) of the bone matrix as a quaternion
        float[] m = new float[16];
        Mat4.fromPose(0, 0, 0, 0.7071068f, 0, 0, 0.7071068f, m, 0); // 90 degrees about X
        assertArrayEquals(new float[] {1, 0, 0}, new float[] {m[0], m[1], m[2]}, 1e-5f);
        assertArrayEquals(new float[] {0, 0, 1}, new float[] {m[4], m[5], m[6]}, 1e-5f);   // y axis -> z
        assertArrayEquals(new float[] {0, -1, 0}, new float[] {m[8], m[9], m[10]}, 1e-5f); // z axis -> -y
    }

    @Test
    void multiplicationComposesRightToLeft() {
        float[] move = new float[16];
        Mat4.fromPose(5, 0, 0, 0, 0, 0, 1, move, 0);
        float[] turn = new float[16];
        Mat4.fromPose(0, 0, 0, 0, 0, 0.7071068f, 0.7071068f, turn, 0);
        float[] both = new float[16];
        Mat4.mul(move, 0, turn, 0, both, 0); // turn first, then move
        assertArrayEquals(new float[] {5, 1, 0}, transformPoint(both, 1, 0, 0), 1e-5f);
        Mat4.mul(turn, 0, move, 0, both, 0); // move first, then turn
        assertArrayEquals(new float[] {0, 6, 0}, transformPoint(both, 1, 0, 0), 1e-5f);
    }

    @Test
    void worksOnSlicesOfALargerArray() {
        float[] big = new float[48];
        Mat4.identity(big, 16);
        Mat4.fromPose(1, 1, 1, 0, 0, 0, 1, big, 32);
        float[] out = new float[48];
        Mat4.mul(big, 32, big, 16, out, 16);
        assertEquals(1f, out[16 + 12]);
        assertEquals(1f, out[16]);
    }
}
