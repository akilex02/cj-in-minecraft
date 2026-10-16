package dev.cjmc;

import java.util.ArrayList;
import java.util.List;

/**
 * The part of CJ's mesh that is drawn in first person: the triangles of his arms (sleeves and skin of the upper arms, forearms, hands and fingers of both sides).
 * The torso, the head and the clavicles would cover the view from his eyes, so they are left out. Pure.
 */
public final class FirstPersonArms {
    /** The nodes of GTA's skeleton that make the arms: upper arm, forearm, hand and two finger bones, right (22-26) and left (32-36). */
    public static final int[] ARM_NODES = {22, 23, 24, 25, 26, 32, 33, 34, 35, 36};
    /**
     * A vertex belongs to the arms when at least this much of its weight is on arm joints. Half was too little: the seam of the sleeve at the shoulder, partly on the
     * torso, came along and its cut edge showed as black spikes from the eyes.
     */
    static final float MIN_ARM_WEIGHT = 0.9f;

    private FirstPersonArms() {}

    /** Which joints of a model (by their node ids) are arm joints. */
    public static boolean[] armJoints(int[] jointNodeIds) {
        boolean[] arm = new boolean[jointNodeIds.length];
        for (int j = 0; j < jointNodeIds.length; j++) {
            for (int node : ARM_NODES) {
                arm[j] |= jointNodeIds[j] == node;
            }
        }
        return arm;
    }

    /** The indices of the part's triangles whose three vertices are on the arms. */
    public static int[] armTriangles(GlbModel.Part part, boolean[] armJoint) {
        int[] idx = part.indices();
        int[] kept = new int[idx.length];
        int n = 0;
        for (int t = 0; t + 2 < idx.length; t += 3) {
            if (onArm(part, idx[t], armJoint) && onArm(part, idx[t + 1], armJoint) && onArm(part, idx[t + 2], armJoint)) {
                kept[n++] = idx[t];
                kept[n++] = idx[t + 1];
                kept[n++] = idx[t + 2];
            }
        }
        return java.util.Arrays.copyOf(kept, n);
    }

    private static boolean onArm(GlbModel.Part part, int v, boolean[] armJoint) {
        float onArm = 0;
        for (int k = 0; k < 4; k++) {
            int j = part.joints()[4 * v + k];
            if (j >= 0 && j < armJoint.length && armJoint[j]) {
                onArm += part.weights()[4 * v + k];
            }
        }
        return onArm >= MIN_ARM_WEIGHT;
    }

    /** The model's parts with only their arm triangles (the same vertices, so the skinned positions of the whole part serve), in the model's order; empty parts too. */
    public static List<GlbModel.Part> armParts(GlbModel model) {
        boolean[] arm = armJoints(model.jointNodeIds);
        List<GlbModel.Part> out = new ArrayList<>();
        for (GlbModel.Part p : model.parts) {
            out.add(new GlbModel.Part(p.name(), p.positions(), p.normals(), p.uvs(), armTriangles(p, arm), p.joints(), p.weights(), p.image()));
        }
        return out;
    }

    /** Whether any part has an arm triangle. */
    public static boolean any(List<GlbModel.Part> parts) {
        for (GlbModel.Part p : parts) {
            if (p.indices().length > 0) {
                return true;
            }
        }
        return false;
    }

    /** The nodes of GTA's skeleton of the head: the head, the brows and the jaw. Seen from inside, they would cover the view. */
    public static final int[] HEAD_NODES = {5, 6, 7, 8};

    public static boolean[] headJoints(int[] jointNodeIds) {
        boolean[] head = new boolean[jointNodeIds.length];
        for (int j = 0; j < jointNodeIds.length; j++) {
            for (int node : HEAD_NODES) {
                head[j] |= jointNodeIds[j] == node;
            }
        }
        return head;
    }

    /** The indices of the part's triangles none of whose vertices is mostly on the head (CJ seen from his own eyes, in the car). */
    public static int[] withoutHead(GlbModel.Part part, boolean[] headJoint) {
        int[] idx = part.indices();
        int[] kept = new int[idx.length];
        int n = 0;
        for (int t = 0; t + 2 < idx.length; t += 3) {
            if (!onHead(part, idx[t], headJoint) && !onHead(part, idx[t + 1], headJoint) && !onHead(part, idx[t + 2], headJoint)) {
                kept[n++] = idx[t];
                kept[n++] = idx[t + 1];
                kept[n++] = idx[t + 2];
            }
        }
        return java.util.Arrays.copyOf(kept, n);
    }

    private static boolean onHead(GlbModel.Part part, int v, boolean[] headJoint) {
        float onHead = 0;
        for (int k = 0; k < 4; k++) {
            int j = part.joints()[4 * v + k];
            if (j >= 0 && j < headJoint.length && headJoint[j]) {
                onHead += part.weights()[4 * v + k];
            }
        }
        return onHead >= 0.5f;
    }

    /** The model's parts without the head (the same vertices). */
    public static List<GlbModel.Part> headlessParts(GlbModel model) {
        boolean[] head = headJoints(model.jointNodeIds);
        List<GlbModel.Part> out = new ArrayList<>();
        for (GlbModel.Part p : model.parts) {
            out.add(new GlbModel.Part(p.name(), p.positions(), p.normals(), p.uvs(), withoutHead(p, head), p.joints(), p.weights(), p.image()));
        }
        return out;
    }
}
