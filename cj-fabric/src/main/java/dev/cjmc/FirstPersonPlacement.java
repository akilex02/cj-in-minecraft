package dev.cjmc;

/**
 * Where CJ's arms are seen from in first person: his model space (Minecraft's axes, feet at the origin, facing +z, as the Skinner leaves it) turned the way his
 * body faces in the world, moved so his head is at the eye, and turned to the camera (which looks down -z, +x to its right, +y up). Pure.
 */
public final class FirstPersonPlacement {
    private FirstPersonPlacement() {}

    /** The 4x4 (column-major) that takes a point of the model to the camera: RotX(pitch) * RotY(cameraYaw + 180) * RotY(-bodyYaw) * Translate(-head). */
    public static float[] modelToCamera(float cameraYaw, float cameraPitch, float bodyYaw, float[] headModel) {
        float[] translate = new float[16];
        Mat4.identity(translate, 0);
        translate[12] = -headModel[0];
        translate[13] = -headModel[1];
        translate[14] = -headModel[2];
        float[] body = rotY(-bodyYaw);
        float[] view = mul(rotX(cameraPitch), rotY(cameraYaw + 180f));
        return mul(view, mul(body, translate));
    }

    /** The head joint of a pose in Minecraft's model axes, as the Skinner places the mesh (GTA's z up, lifted by the ground offset). */
    public static float[] headInModel(JointFrame frame, int headJoint, float groundOffset) {
        float x = frame.pos[3 * headJoint], y = frame.pos[3 * headJoint + 1], z = frame.pos[3 * headJoint + 2] + groundOffset;
        float[] v = {x, y, z};
        Axes.toMinecraft(v, 0);
        return v;
    }

    public static float[] apply(float[] m, float[] p) {
        return new float[] {m[0] * p[0] + m[4] * p[1] + m[8] * p[2] + m[12], m[1] * p[0] + m[5] * p[1] + m[9] * p[2] + m[13],
                m[2] * p[0] + m[6] * p[1] + m[10] * p[2] + m[14]};
    }

    // Minecraft's RotationAxis.POSITIVE_X / POSITIVE_Y by degrees
    static float[] rotX(float degrees) {
        float c = (float) Math.cos(Math.toRadians(degrees)), s = (float) Math.sin(Math.toRadians(degrees));
        return new float[] {1, 0, 0, 0, 0, c, s, 0, 0, -s, c, 0, 0, 0, 0, 1};
    }

    static float[] rotY(float degrees) {
        float c = (float) Math.cos(Math.toRadians(degrees)), s = (float) Math.sin(Math.toRadians(degrees));
        return new float[] {c, 0, -s, 0, 0, 1, 0, 0, s, 0, c, 0, 0, 0, 0, 1};
    }

    static float[] rotZ(float degrees) {
        float c = (float) Math.cos(Math.toRadians(degrees)), s = (float) Math.sin(Math.toRadians(degrees));
        return new float[] {c, s, 0, 0, -s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    static float[] mul(float[] a, float[] b) {
        float[] out = new float[16];
        Mat4.mul(a, 0, b, 0, out, 0);
        return out;
    }
}
