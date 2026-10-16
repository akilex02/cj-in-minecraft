package dev.cjmc;

/** Puts a weapon in CJ's right hand: the hand's pose, shifted and turned by the weapon's hold (set per weapon in weapons.json). */
public final class WeaponAttach {
    private WeaponAttach() {}

    /** Rotation of rxDeg about X, then ryDeg about Y, then rzDeg about Z, all about fixed axes: q = qz * qy * qx. Returns (x, y, z, w). */
    public static float[] euler(float rxDeg, float ryDeg, float rzDeg) {
        float[] qx = axisAngle(1, 0, 0, rxDeg);
        float[] qy = axisAngle(0, 1, 0, ryDeg);
        float[] qz = axisAngle(0, 0, 1, rzDeg);
        return mul(qz, mul(qy, qx));
    }

    private static float[] axisAngle(float x, float y, float z, float deg) {
        double half = Math.toRadians(deg) / 2;
        float s = (float) Math.sin(half);
        return new float[] {x * s, y * s, z * s, (float) Math.cos(half)};
    }

    /** Hamilton product a * b of two quaternions (x, y, z, w). */
    static float[] mul(float[] a, float[] b) {
        return new float[] {
                a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }

    /** out = q * v, for a unit quaternion q. */
    static void rotate(float[] q, float[] v, float[] out) {
        float tx = 2 * (q[1] * v[2] - q[2] * v[1]);
        float ty = 2 * (q[2] * v[0] - q[0] * v[2]);
        float tz = 2 * (q[0] * v[1] - q[1] * v[0]);
        out[0] = v[0] + q[3] * tx + (q[1] * tz - q[2] * ty);
        out[1] = v[1] + q[3] * ty + (q[2] * tx - q[0] * tz);
        out[2] = v[2] + q[3] * tz + (q[0] * ty - q[1] * tx);
    }

    /**
     * Fills joint 0 of `out` (a one-joint frame, the weapon's own) with the pose of the weapon: the pose of CJ's hand joint
     * `handJoint`, then `offset` and `rotationDeg` in the hand's own frame. out.has[0] is false when CJ's frame has no hand.
     */
    public static void handFrame(JointFrame cj, int handJoint, float[] offset, float[] rotationDeg, JointFrame out) {
        if (handJoint < 0 || handJoint >= cj.has.length || !cj.has[handJoint]) {
            out.has[0] = false;
            return;
        }
        float[] hq = {cj.quat[4 * handJoint], cj.quat[4 * handJoint + 1], cj.quat[4 * handJoint + 2], cj.quat[4 * handJoint + 3]};
        float[] shift = new float[3];
        rotate(hq, offset, shift);
        out.pos[0] = cj.pos[3 * handJoint] + shift[0];
        out.pos[1] = cj.pos[3 * handJoint + 1] + shift[1];
        out.pos[2] = cj.pos[3 * handJoint + 2] + shift[2];
        float[] q = mul(hq, euler(rotationDeg[0], rotationDeg[1], rotationDeg[2]));
        float len = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        for (int k = 0; k < 4; k++) {
            out.quat[k] = q[k] / len;
        }
        out.has[0] = true;
    }

    /** The twin pistols: the second gun is drawn from the left hand, turned half a turn about X and shifted this much in the turned frame (as the game does). */
    private static final float[] TWIN_SHIFT = {0.04f, -0.05f, 0.0f};

    /**
     * The pose of the second gun of the twin pistols: the left hand's pose, then the half turn about X and the shift of the game, then the weapon's hold.
     * With the hold (offset o, rotation r) of the right hand that is the left hand turned over: offset' = turn(shift + o), rotation' = turn * r.
     * out.has[0] is false when CJ's frame has no left hand.
     */
    public static void leftHandFrame(JointFrame cj, int leftHandJoint, float[] offset, float[] rotationDeg, JointFrame out) {
        if (leftHandJoint < 0 || leftHandJoint >= cj.has.length || !cj.has[leftHandJoint]) {
            out.has[0] = false;
            return;
        }
        float[] turned = {TWIN_SHIFT[0] + offset[0], -(TWIN_SHIFT[1] + offset[1]), -(TWIN_SHIFT[2] + offset[2])};
        float[] halfTurn = {1, 0, 0, 0}; // 180 degrees about X
        float[] hq = {cj.quat[4 * leftHandJoint], cj.quat[4 * leftHandJoint + 1], cj.quat[4 * leftHandJoint + 2], cj.quat[4 * leftHandJoint + 3]};
        float[] shift = new float[3];
        rotate(hq, turned, shift);
        out.pos[0] = cj.pos[3 * leftHandJoint] + shift[0];
        out.pos[1] = cj.pos[3 * leftHandJoint + 1] + shift[1];
        out.pos[2] = cj.pos[3 * leftHandJoint + 2] + shift[2];
        float[] q = mul(hq, mul(halfTurn, euler(rotationDeg[0], rotationDeg[1], rotationDeg[2])));
        float len = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        for (int k = 0; k < 4; k++) {
            out.quat[k] = q[k] / len;
        }
        out.has[0] = true;
    }
}
