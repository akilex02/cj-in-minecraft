package dev.cjmc;

/** 4x4 matrices of 16 floats in column-major order (element (row r, column c) is at c*4 + r), as glTF stores them. */
public final class Mat4 {
    private Mat4() {}

    public static void identity(float[] m, int o) {
        for (int i = 0; i < 16; i++) {
            m[o + i] = (i % 5 == 0) ? 1f : 0f;
        }
    }

    /** out = a x b (b is applied first). The slices must not overlap. */
    public static void mul(float[] a, int ao, float[] b, int bo, float[] out, int oo) {
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                float sum = 0;
                for (int k = 0; k < 4; k++) {
                    sum += a[ao + k * 4 + r] * b[bo + c * 4 + k];
                }
                out[oo + c * 4 + r] = sum;
            }
        }
    }

    /** The matrix of a rigid transform: rotation by the unit quaternion (x, y, z, w), then translation by (px, py, pz). */
    public static void fromPose(float px, float py, float pz, float qx, float qy, float qz, float qw, float[] out, int oo) {
        float xx = qx * qx, yy = qy * qy, zz = qz * qz;
        float xy = qx * qy, xz = qx * qz, yz = qy * qz;
        float wx = qw * qx, wy = qw * qy, wz = qw * qz;
        out[oo] = 1 - 2 * (yy + zz);
        out[oo + 1] = 2 * (xy + wz);
        out[oo + 2] = 2 * (xz - wy);
        out[oo + 3] = 0;
        out[oo + 4] = 2 * (xy - wz);
        out[oo + 5] = 1 - 2 * (xx + zz);
        out[oo + 6] = 2 * (yz + wx);
        out[oo + 7] = 0;
        out[oo + 8] = 2 * (xz + wy);
        out[oo + 9] = 2 * (yz - wx);
        out[oo + 10] = 1 - 2 * (xx + yy);
        out[oo + 11] = 0;
        out[oo + 12] = px;
        out[oo + 13] = py;
        out[oo + 14] = pz;
        out[oo + 15] = 1;
    }
}
