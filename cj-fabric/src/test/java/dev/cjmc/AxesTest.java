package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AxesTest {
    private static float[] convert(float x, float y, float z) {
        float[] v = {x, y, z};
        Axes.toMinecraft(v, 0);
        return v;
    }

    @Test
    void gtaForwardLeftAndUpBecomeMinecraftForwardLeftAndUp() {
        // Review Focus #9. Minecraft model space (facing south at yaw 0): x = the player's left, y = up, z = forward.
        assertArrayEquals(new float[] {0, 0, 1}, convert(1, 0, 0)); // GTA X (forward) -> +Z
        assertArrayEquals(new float[] {1, 0, 0}, convert(0, 1, 0)); // GTA Y (left)    -> +X
        assertArrayEquals(new float[] {0, 1, 0}, convert(0, 0, 1)); // GTA Z (up)      -> +Y
    }

    @Test
    void theConversionTurnsAndDoesNotMirror() {
        float[] f = convert(1, 0, 0), l = convert(0, 1, 0), u = convert(0, 0, 1);
        // determinant of the matrix whose columns are the images of the three axes
        float det = f[0] * (l[1] * u[2] - l[2] * u[1]) - l[0] * (f[1] * u[2] - f[2] * u[1]) + u[0] * (f[1] * l[2] - f[2] * l[1]);
        assertEquals(1.0f, det);
    }

    @Test
    void worksInPlaceOnASliceOfAnArray() {
        float[] a = {9, 9, 9, 1, 2, 3, 9, 9, 9};
        Axes.toMinecraft(a, 3);
        assertArrayEquals(new float[] {9, 9, 9, 2, 3, 1, 9, 9, 9}, a);
    }
}
