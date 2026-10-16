package dev.cjmc;

/**
 * The one place where GTA's axes become Minecraft's. GTA's model space is X forward, Y left, Z up. A Minecraft
 * entity model facing south (yaw 0) has x = the entity's left, y = up, z = forward, so (x, y, z) becomes (y, z, x):
 * a cyclic permutation (determinant +1), a turn and not a mirror.
 */
public final class Axes {
    private Axes() {}

    public static void toMinecraft(float[] v, int o) {
        float gx = v[o], gy = v[o + 1], gz = v[o + 2];
        v[o] = gy;
        v[o + 1] = gz;
        v[o + 2] = gx;
    }
}
