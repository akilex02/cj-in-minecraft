package dev.cjmc.skate;

import dev.cjmc.JointFrame;

/**
 * CJ posed with the skater's bones. The Skate skeleton (a Mixamo-style rig of 35 joints) and CJ's (the 32 bones of GTA) are matched bone by bone, in the manner of
 * the GTA San AnSkateas plugin (ryglizzy/GTA-San-AnSkateas, skate-ffi rig): each bone of CJ is first turned so its segment (toward its child) lies along the
 * skater's segment at rest, and then takes the turn the skater's bone makes from rest to the pose. The bones CJ has no twist bone for (shins, forearms, upper arms, the
 * neck) may roll only so far about their own axis against their parent, or the skin collapses at the knees and elbows; the clavicles follow the chest half way; the
 * chest bones on the shoulders (301, 302) ride on the clavicles and turn half way with the upper arms, or the skin of the shoulders stretches. Positions come from
 * walking CJ's own skeleton from the hips, so his limbs keep their own lengths, and the whole body is then shifted so his ankles are where the skater's are.
 * Pure: arrays only, no game classes.
 */
public final class SkateRig {
    private static final int NO = -1;

    /**
     * The bones of CJ, parents first: {node of GTA, parent node, skater's joint that moves it (or NO: it moves as its parent), node of the child its segment ends at
     * (or NO), skater's joint at the end of that segment (or NO), largest roll about its own axis against the parent in degrees (0: unlimited)}.
     */
    private static final int[][] BONES = {
        {1, NO, 0, 3, 1, 0}, {2, 1, 0, 3, 1, 0}, {3, 2, 1, 4, 3, 40}, {4, 3, 3, 5, 5, 40}, {5, 4, 7, NO, NO, 40},
        {6, 5, NO, NO, NO, 0}, {7, 5, NO, NO, NO, 0}, {8, 5, NO, NO, NO, 0}, {201, 1, NO, NO, NO, 0},
        {31, 3, 12, 32, 13, 0}, {32, 31, 13, 33, 14, 45}, {33, 32, 14, 34, 15, 35}, {34, 33, NO, NO, NO, 0}, {35, 34, NO, NO, NO, 0}, {36, 35, NO, NO, NO, 0},
        {21, 3, 8, 22, 9, 0}, {22, 21, 9, 23, 10, 45}, {23, 22, 10, 24, 11, 35}, {24, 23, NO, NO, NO, 0}, {25, 24, NO, NO, NO, 0}, {26, 25, NO, NO, NO, 0},
        {302, 31, NO, NO, NO, 0}, {301, 21, NO, NO, NO, 0},
        {41, 1, 20, 42, 21, 60}, {42, 41, 21, 43, 22, 20}, {43, 42, 22, 44, 23, 0}, {44, 43, 23, NO, NO, 0},
        {51, 1, 16, 52, 17, 60}, {52, 51, 17, 53, 18, 20}, {53, 52, 18, 54, 19, 0}, {54, 53, 19, NO, NO, 0},
    };
    /** The shoulder helpers (chest bones on the shoulder joints) and the upper arm each turns half way with. */
    private static final int[][] HELPERS = {{302, 32}, {301, 22}};
    /** The hands take the turn of the forearm (CJ has no wrist to bend), and the clavicles follow the chest this much. */
    private static final int[][] HANDS = {{34, 33}, {24, 23}};
    private static final int[][] CLAVICLES = {{31, 4}, {21, 4}};
    private static final float CLAVICLE_FOLLOW = 0.5f;
    /** The skater's joints in the pose of the engine come one place later than in the rig: bone 0 of the pose is the root of the animation. */
    private static final int POSE_SHIFT = 1;
    private static final int HIPS = 0, LEFT_FOOT = 22, RIGHT_FOOT = 18; // joints of the skater
    private static final int CJ_LEFT_FOOT = 43, CJ_RIGHT_FOOT = 53; // nodes of CJ

    private final float[][] skateRestQuat; // by joint of the skater: world rotation at rest
    private final float[][] skateRestPos; // by joint: world position at rest (the world of the engine: x left, y up, z forward)
    private final int[] jointOfNode; // by node id of CJ -> its joint in the model, or -1
    private final float[][] cjRestQuat; // by joint of the model: world rotation at rest (CJ's model space: x forward, y left, z up)
    private final float[][] cjRestPos; // by joint: world position at rest
    private final int[] boneOfNode = new int[512];

    /**
     * @param skateRestRot the world rotation of each joint of the skater at rest, 9 floats each, column-major
     * @param skateRestPos the world position of each joint of the skater at rest, 3 floats each
     * @param cjNodeIds the node id of each joint of CJ's model, in the model's order
     * @param cjInverseBind the inverse bind matrix of each joint (16 floats, column-major)
     */
    public SkateRig(float[] skateRestRot, float[] skateRestPos, int[] cjNodeIds, float[] cjInverseBind) {
        int skateJoints = skateRestRot.length / 9;
        this.skateRestQuat = new float[skateJoints][];
        this.skateRestPos = new float[skateJoints][];
        for (int j = 0; j < skateJoints; j++) {
            skateRestQuat[j] = quaternion(java.util.Arrays.copyOfRange(skateRestRot, 9 * j, 9 * j + 9));
            this.skateRestPos[j] = skateRestPos.length >= 3 * (j + 1) ? java.util.Arrays.copyOfRange(skateRestPos, 3 * j, 3 * j + 3) : new float[3];
        }
        int joints = cjNodeIds.length;
        this.jointOfNode = new int[512];
        java.util.Arrays.fill(jointOfNode, -1);
        java.util.Arrays.fill(boneOfNode, -1);
        for (int j = 0; j < joints; j++) {
            jointOfNode[cjNodeIds[j]] = j;
        }
        for (int i = 0; i < BONES.length; i++) {
            boneOfNode[BONES[i][0]] = i;
        }
        this.cjRestQuat = new float[joints][];
        this.cjRestPos = new float[joints][];
        for (int j = 0; j < joints; j++) {
            int o = 16 * j;
            // inverse bind = [R^T | -R^T p]: the rest rotation is the transpose of its 3x3, the position is -R t
            float[] r = new float[9];
            for (int c = 0; c < 3; c++) {
                for (int row = 0; row < 3; row++) {
                    r[c * 3 + row] = cjInverseBind[o + row * 4 + c];
                }
            }
            cjRestQuat[j] = quaternion(r);
            float tx = cjInverseBind[o + 12], ty = cjInverseBind[o + 13], tz = cjInverseBind[o + 14];
            cjRestPos[j] = new float[] {-(r[0] * tx + r[3] * ty + r[6] * tz), -(r[1] * tx + r[4] * ty + r[7] * tz), -(r[2] * tx + r[5] * ty + r[8] * tz)};
        }
    }

    /** Whether every bone of CJ this needs is in the model and the skater's rest has the joints it reads. */
    public boolean usable() {
        if (skateRestQuat.length < 24) {
            return false;
        }
        for (int[] b : BONES) {
            if (jointOfNode[b[0]] < 0) {
                return false;
            }
        }
        return jointOfNode[0] >= 0;
    }

    /**
     * Fills `frame` (CJ's joints in his model space, origin at the centre of the board, as the pose of the engine has it) from the pose of the skater: `bones` holds
     * the 4x4 matrices of the pose (16 floats each, column-major, in the world axes of the engine: x left, y up, z forward when the skater faces forward).
     */
    public void pose(float[] bones, int boneCount, JointFrame frame) {
        int n = BONES.length;
        float[][] delta = new float[n][]; // each bone's turn from its rest, in CJ's axes
        float[][] position = new float[n][];
        for (int i = 0; i < n; i++) {
            int node = BONES[i][0], parentNode = BONES[i][1], skate = BONES[i][2];
            int parent = parentNode == NO ? -1 : boneOfNode[parentNode];
            float[] d;
            if (skate != NO && skate + POSE_SHIFT < boneCount) {
                d = fitted(i, bones);
            } else {
                d = parent < 0 ? new float[] {0, 0, 0, 1} : delta[parent];
            }
            delta[i] = d;
            int j = jointOfNode[node];
            if (parent < 0) {
                position[i] = toModelVector(translation(bones, HIPS + POSE_SHIFT));
            } else {
                // a bone sits at CJ's own distance from its parent, in the direction the parent has turned to
                int pj = jointOfNode[parentNode];
                float[] offset = {cjRestPos[j][0] - cjRestPos[pj][0], cjRestPos[j][1] - cjRestPos[pj][1], cjRestPos[j][2] - cjRestPos[pj][2]};
                float[] moved = rotate(delta[parent], offset);
                position[i] = new float[] {position[parent][0] + moved[0], position[parent][1] + moved[1], position[parent][2] + moved[2]};
            }
            // what CJ has no bone for: roll about the bone's own axis limited against the parent; the clavicle partly follows the chest; the hands follow the forearms
            if (parent >= 0 && skate != NO) {
                int limit = BONES[i][5];
                if (limit > 0) {
                    float[] along = segment(i);
                    delta[i] = limitTwist(delta[i], delta[parent], rotate(delta[i], along), (float) Math.toRadians(limit));
                }
            }
            for (int[] c : CLAVICLES) {
                if (c[0] == node && skate != NO) {
                    delta[i] = slerp(delta[boneOfNode[c[1]]], delta[i], CLAVICLE_FOLLOW);
                }
            }
            for (int[] h : HANDS) {
                if (h[0] == node) {
                    delta[i] = delta[boneOfNode[h[1]]];
                }
            }
            for (int[] h : HELPERS) {
                if (h[0] == node) {
                    delta[i] = slerp(delta[parent], delta[boneOfNode[h[1]]], 0.5f);
                }
            }
        }
        // the positions above used the parents' final turns; the ones of the bones that follow another (hands, helpers) are redone from them
        redoPositions(delta, position, bones);
        // the ankles of CJ go to where the ankles of the skater are
        float[] shift = new float[3];
        int count = 0;
        for (int[] pair : new int[][] {{CJ_LEFT_FOOT, LEFT_FOOT}, {CJ_RIGHT_FOOT, RIGHT_FOOT}}) {
            if (pair[1] + POSE_SHIFT >= boneCount) {
                continue;
            }
            float[] ankle = toModelVector(translation(bones, pair[1] + POSE_SHIFT));
            float[] mine = position[boneOfNode[pair[0]]];
            for (int k = 0; k < 3; k++) {
                shift[k] += ankle[k] - mine[k];
            }
            count++;
        }
        if (count > 0) {
            for (int k = 0; k < 3; k++) {
                shift[k] /= count;
            }
        }
        for (int i = 0; i < n; i++) {
            put(frame, jointOfNode[BONES[i][0]], position[i], shift, mul(delta[i], cjRestQuat[jointOfNode[BONES[i][0]]]));
        }
        // the root of the skeleton (node 0) is where the pelvis is
        int pelvis = boneOfNode[1];
        put(frame, jointOfNode[0], position[pelvis], shift, mul(delta[pelvis], cjRestQuat[jointOfNode[0]]));
    }

    private void redoPositions(float[][] delta, float[][] position, float[] bones) {
        for (int i = 0; i < BONES.length; i++) {
            int parentNode = BONES[i][1];
            if (parentNode == NO) {
                continue;
            }
            int parent = boneOfNode[parentNode];
            int j = jointOfNode[BONES[i][0]], pj = jointOfNode[parentNode];
            float[] offset = {cjRestPos[j][0] - cjRestPos[pj][0], cjRestPos[j][1] - cjRestPos[pj][1], cjRestPos[j][2] - cjRestPos[pj][2]};
            float[] moved = rotate(delta[parent], offset);
            position[i] = new float[] {position[parent][0] + moved[0], position[parent][1] + moved[1], position[parent][2] + moved[2]};
        }
    }

    private static void put(JointFrame frame, int j, float[] position, float[] shift, float[] quat) {
        frame.has[j] = true;
        frame.pos[3 * j] = position[0] + shift[0];
        frame.pos[3 * j + 1] = position[1] + shift[1];
        frame.pos[3 * j + 2] = position[2] + shift[2];
        System.arraycopy(quat, 0, frame.quat, 4 * j, 4);
    }

    /**
     * The turn of bone `i`: its segment is first laid along the skater's segment at rest (they are not the same pose: CJ's arms hang a little differently), then it
     * makes the turn the skater's bone makes from rest to the pose. In CJ's axes.
     */
    private float[] fitted(int i, float[] bones) {
        int skate = BONES[i][2];
        int j = jointOfNode[BONES[i][0]];
        float[] posedWorld = mulQ(quaternion(rotation(bones, skate + POSE_SHIFT)), conjugate(skateRestQuat[skate]));
        // the segments, in the axes of the engine
        float[] hostSegment, skateSegment;
        int childNode = BONES[i][3], skateChild = BONES[i][4];
        if (childNode != NO && skateChild != NO) {
            int cj = jointOfNode[childNode];
            hostSegment = toEngineVector(sub(cjRestPos[cj], cjRestPos[j]));
            skateSegment = sub(skateRestPos[skateChild], skateRestPos[skate]);
        } else {
            int parentNode = BONES[i][1];
            int parentSkate = parentNode == NO ? NO : BONES[boneOfNode[parentNode]][2];
            if (parentNode != NO && parentSkate != NO) {
                int pj = jointOfNode[parentNode];
                hostSegment = toEngineVector(sub(cjRestPos[j], cjRestPos[pj]));
                skateSegment = sub(skateRestPos[skate], skateRestPos[parentSkate]);
            } else {
                hostSegment = skateSegment = null;
            }
        }
        float[] align = arc(hostSegment, skateSegment);
        return toModelQuat(mulQ(posedWorld, align));
    }

    /** The bone's own axis in CJ's axes at rest: toward its child, or away from its parent. */
    private float[] segment(int i) {
        int j = jointOfNode[BONES[i][0]];
        if (BONES[i][3] != NO) {
            return sub(cjRestPos[jointOfNode[BONES[i][3]]], cjRestPos[j]);
        }
        int parentNode = BONES[i][1];
        return sub(cjRestPos[j], cjRestPos[jointOfNode[parentNode]]);
    }

    /**
     * `turn` (a bone's turn from rest) with the roll about the bone's own axis `axis` (as posed) against `parentTurn` held to `limit` radians. The part of the turn that
     * is not roll (the bend) is kept whole.
     */
    private static float[] limitTwist(float[] turn, float[] parentTurn, float[] axis, float limit) {
        float len = (float) Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]);
        if (len < 1e-6f) {
            return turn;
        }
        float[] a = {axis[0] / len, axis[1] / len, axis[2] / len};
        float[] relative = mulQ(turn, conjugate(parentTurn));
        if (relative[3] < 0) {
            relative = new float[] {-relative[0], -relative[1], -relative[2], -relative[3]};
        }
        float dot = relative[0] * a[0] + relative[1] * a[1] + relative[2] * a[2];
        float[] twist = {a[0] * dot, a[1] * dot, a[2] * dot, relative[3]};
        float tl = (float) Math.sqrt(twist[0] * twist[0] + twist[1] * twist[1] + twist[2] * twist[2] + twist[3] * twist[3]);
        if (tl < 1e-6f) {
            return turn;
        }
        for (int k = 0; k < 4; k++) {
            twist[k] /= tl;
        }
        float[] swing = mulQ(relative, conjugate(twist));
        float angle = 2f * (float) Math.atan2(dot, relative[3]); // the roll, from -pi to pi because w >= 0
        float clamped = Math.max(-limit, Math.min(limit, angle));
        float s = (float) Math.sin(clamped / 2f);
        float[] limited = {a[0] * s, a[1] * s, a[2] * s, (float) Math.cos(clamped / 2f)};
        return normalize(mulQ(mulQ(swing, limited), parentTurn));
    }

    // ---- vectors and quaternions (x, y, z, w) ----

    private static float[] sub(float[] a, float[] b) {
        return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] rotation(float[] bones, int bone) {
        int o = 16 * bone;
        return new float[] {bones[o], bones[o + 1], bones[o + 2], bones[o + 4], bones[o + 5], bones[o + 6], bones[o + 8], bones[o + 9], bones[o + 10]};
    }

    private static float[] translation(float[] bones, int bone) {
        int o = 16 * bone;
        return new float[] {bones[o + 12], bones[o + 13], bones[o + 14]};
    }

    /** The engine's world (x left, y up, z forward) is Minecraft's; CJ's model space is x forward, y left, z up: an engine vector (x, y, z) is (z, x, y) of CJ. */
    private static float[] toModelVector(float[] v) {
        return new float[] {v[2], v[0], v[1]};
    }

    private static float[] toEngineVector(float[] v) {
        return new float[] {v[1], v[2], v[0]};
    }

    /** A rotation of the engine's axes as a rotation of CJ's: the axis changes the same way as a vector, the angle stays. */
    private static float[] toModelQuat(float[] q) {
        return new float[] {q[2], q[0], q[1], q[3]};
    }

    private static float[] mulQ(float[] a, float[] b) {
        return new float[] {
            a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
            a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
            a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
            a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }

    private static float[] mul(float[] a, float[] b) {
        return normalize(mulQ(a, b));
    }

    private static float[] conjugate(float[] q) {
        return new float[] {-q[0], -q[1], -q[2], q[3]};
    }

    private static float[] normalize(float[] q) {
        float len = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        return len < 1e-9f ? new float[] {0, 0, 0, 1} : new float[] {q[0] / len, q[1] / len, q[2] / len, q[3] / len};
    }

    /** The rotation of the vector v by the quaternion q. */
    private static float[] rotate(float[] q, float[] v) {
        float[] p = mulQ(mulQ(q, new float[] {v[0], v[1], v[2], 0}), conjugate(q));
        return new float[] {p[0], p[1], p[2]};
    }

    /** The shortest turn taking direction `from` onto direction `to`; none (identity) when either is missing or nothing. */
    private static float[] arc(float[] from, float[] to) {
        if (from == null || to == null) {
            return new float[] {0, 0, 0, 1};
        }
        float lf = (float) Math.sqrt(from[0] * from[0] + from[1] * from[1] + from[2] * from[2]);
        float lt = (float) Math.sqrt(to[0] * to[0] + to[1] * to[1] + to[2] * to[2]);
        if (lf < 1e-6f || lt < 1e-6f) {
            return new float[] {0, 0, 0, 1};
        }
        float[] f = {from[0] / lf, from[1] / lf, from[2] / lf}, t = {to[0] / lt, to[1] / lt, to[2] / lt};
        float dot = f[0] * t[0] + f[1] * t[1] + f[2] * t[2];
        if (dot < -0.999999f) { // opposite: any axis across them
            float[] axis = Math.abs(f[0]) < 0.9f ? new float[] {0, -f[2], f[1]} : new float[] {f[2], 0, -f[0]};
            float al = (float) Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]);
            return new float[] {axis[0] / al, axis[1] / al, axis[2] / al, 0};
        }
        float[] cross = {f[1] * t[2] - f[2] * t[1], f[2] * t[0] - f[0] * t[2], f[0] * t[1] - f[1] * t[0]};
        return normalize(new float[] {cross[0], cross[1], cross[2], 1f + dot});
    }

    private static float[] slerp(float[] a, float[] b, float t) {
        float dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
        float[] to = dot < 0 ? new float[] {-b[0], -b[1], -b[2], -b[3]} : b;
        dot = Math.abs(dot);
        if (dot > 0.9995f) {
            return normalize(new float[] {a[0] + t * (to[0] - a[0]), a[1] + t * (to[1] - a[1]), a[2] + t * (to[2] - a[2]), a[3] + t * (to[3] - a[3])});
        }
        float theta = (float) Math.acos(Math.min(1f, dot));
        float sa = (float) Math.sin((1 - t) * theta) / (float) Math.sin(theta), sb = (float) Math.sin(t * theta) / (float) Math.sin(theta);
        return normalize(new float[] {sa * a[0] + sb * to[0], sa * a[1] + sb * to[1], sa * a[2] + sb * to[2], sa * a[3] + sb * to[3]});
    }

    /** The unit quaternion (x, y, z, w) of a rotation matrix of 9 floats, column-major. */
    static float[] quaternion(float[] m) {
        float m00 = m[0], m10 = m[1], m20 = m[2], m01 = m[3], m11 = m[4], m21 = m[5], m02 = m[6], m12 = m[7], m22 = m[8];
        float trace = m00 + m11 + m22;
        float x, y, z, w;
        if (trace > 0) {
            float s = (float) Math.sqrt(trace + 1f) * 2f;
            w = 0.25f * s;
            x = (m21 - m12) / s;
            y = (m02 - m20) / s;
            z = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            float s = (float) Math.sqrt(1f + m00 - m11 - m22) * 2f;
            w = (m21 - m12) / s;
            x = 0.25f * s;
            y = (m01 + m10) / s;
            z = (m02 + m20) / s;
        } else if (m11 > m22) {
            float s = (float) Math.sqrt(1f + m11 - m00 - m22) * 2f;
            w = (m02 - m20) / s;
            x = (m01 + m10) / s;
            y = 0.25f * s;
            z = (m12 + m21) / s;
        } else {
            float s = (float) Math.sqrt(1f + m22 - m00 - m11) * 2f;
            w = (m10 - m01) / s;
            x = (m02 + m20) / s;
            y = (m12 + m21) / s;
            z = 0.25f * s;
        }
        return normalize(new float[] {x, y, z, w});
    }
}
