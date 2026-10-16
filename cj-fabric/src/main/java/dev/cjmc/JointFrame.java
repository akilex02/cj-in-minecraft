package dev.cjmc;

/** A pose by joint, in the model's own joint order: model-space position (X forward, Y left, Z up) and rotation (x, y, z, w). */
public final class JointFrame {
    public final float[] pos;
    public final float[] quat;
    /** False for a joint the source pose did not carry; such a joint keeps its bind pose. */
    public final boolean[] has;
    /** Where the GTA ped faces, radians counter-clockwise from north, in [0, 2*pi). The pose is relative to it. */
    public float heading;

    public JointFrame(int joints) {
        this.pos = new float[3 * joints];
        this.quat = new float[4 * joints];
        this.has = new boolean[joints];
    }
}
