package dev.cjmc;

/** CPU skinning: skin matrix = pose x inverse bind; each vertex is the weighted sum of its (up to 4) joints' transforms. */
public final class Skinner {
    private final GlbModel model;
    private final int joints;
    private final float[] skin;
    private final float[] pose = new float[16];
    /** Per part: 3 floats per vertex, in Minecraft model space (feet at y = 0, facing +Z). */
    public final float[][] positions;
    public final float[][] normals;

    public Skinner(GlbModel model) {
        this.model = model;
        this.joints = model.jointNodeIds.length;
        this.skin = new float[16 * joints];
        this.positions = new float[model.parts.size()][];
        this.normals = new float[model.parts.size()][];
        for (int i = 0; i < model.parts.size(); i++) {
            positions[i] = new float[model.parts.get(i).positions().length];
            normals[i] = new float[model.parts.get(i).normals().length];
        }
    }

    public void skin(JointFrame frame) {
        skin(frame, model.groundOffset);
    }

    /**
     * frame == null, or a joint the frame does not carry, means the bind pose for that joint.
     * `groundOffset` lifts the result so the feet of the model that owns the skeleton stand on y = 0.
     */
    public void skin(JointFrame frame, float groundOffset) {
        for (int j = 0; j < joints; j++) {
            if (frame != null && frame.has[j]) {
                Mat4.fromPose(frame.pos[3 * j], frame.pos[3 * j + 1], frame.pos[3 * j + 2],
                        frame.quat[4 * j], frame.quat[4 * j + 1], frame.quat[4 * j + 2], frame.quat[4 * j + 3], pose, 0);
                Mat4.mul(pose, 0, model.inverseBind, 16 * j, skin, 16 * j);
            } else {
                Mat4.identity(skin, 16 * j);
            }
        }
        for (int i = 0; i < model.parts.size(); i++) {
            skinPart(model.parts.get(i), positions[i], normals[i], groundOffset);
        }
    }

    private void skinPart(GlbModel.Part part, float[] outPos, float[] outNormal, float groundOffset) {
        float[] pos = part.positions();
        float[] nrm = part.normals();
        int[] jt = part.joints();
        float[] wt = part.weights();
        for (int v = 0; v < part.vertexCount(); v++) {
            float x = pos[3 * v], y = pos[3 * v + 1], z = pos[3 * v + 2];
            float nx = nrm[3 * v], ny = nrm[3 * v + 1], nz = nrm[3 * v + 2];
            float ox = 0, oy = 0, oz = 0, onx = 0, ony = 0, onz = 0, total = 0;
            for (int k = 0; k < 4; k++) {
                float w = wt[4 * v + k];
                if (w == 0f) {
                    continue;
                }
                int o = 16 * jt[4 * v + k];
                ox += w * (skin[o] * x + skin[o + 4] * y + skin[o + 8] * z + skin[o + 12]);
                oy += w * (skin[o + 1] * x + skin[o + 5] * y + skin[o + 9] * z + skin[o + 13]);
                oz += w * (skin[o + 2] * x + skin[o + 6] * y + skin[o + 10] * z + skin[o + 14]);
                onx += w * (skin[o] * nx + skin[o + 4] * ny + skin[o + 8] * nz);
                ony += w * (skin[o + 1] * nx + skin[o + 5] * ny + skin[o + 9] * nz);
                onz += w * (skin[o + 2] * nx + skin[o + 6] * ny + skin[o + 10] * nz);
                total += w;
            }
            if (total == 0f) { // an unweighted vertex stays where it is
                ox = x;
                oy = y;
                oz = z;
                onx = nx;
                ony = ny;
                onz = nz;
            }
            oz += groundOffset;
            float len = (float) Math.sqrt(onx * onx + ony * ony + onz * onz);
            if (len > 1e-6f) {
                onx /= len;
                ony /= len;
                onz /= len;
            } else { // a zero-length normal: point it up rather than hand a NaN to the graphics card
                onx = 0;
                ony = 0;
                onz = 1;
            }
            outPos[3 * v] = ox;
            outPos[3 * v + 1] = oy;
            outPos[3 * v + 2] = oz;
            outNormal[3 * v] = onx;
            outNormal[3 * v + 1] = ony;
            outNormal[3 * v + 2] = onz;
            Axes.toMinecraft(outPos, 3 * v);
            Axes.toMinecraft(outNormal, 3 * v);
        }
    }
}
