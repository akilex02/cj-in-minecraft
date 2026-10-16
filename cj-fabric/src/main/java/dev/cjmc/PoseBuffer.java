package dev.cjmc;

import java.util.ArrayDeque;
import java.util.Map;

/**
 * Keeps the latest poses and answers "what does CJ look like right now?". The network thread pushes and the
 * render thread samples, so every method is synchronized. Frames are drawn DELAY_NS late so there are always two
 * real poses to interpolate between.
 */
public final class PoseBuffer {
    public static final long STALE_NS = 500_000_000L;
    public static final long DELAY_NS = 50_000_000L;
    /** Positions beyond this (model units, metres) are broken data and are clamped. */
    public static final float MAX_POSITION = 100f;
    private static final int KEEP = 8;

    private record Entry(long arrivalNs, JointFrame frame) {}

    private final Map<Integer, Integer> jointOfNode;
    private final int joints;
    private final ArrayDeque<Entry> frames = new ArrayDeque<>();

    public PoseBuffer(Map<Integer, Integer> jointOfNode, int jointCount) {
        this.jointOfNode = jointOfNode;
        this.joints = jointCount;
    }

    public synchronized void push(Protocol.Pose pose, long arrivalNs) {
        JointFrame frame = new JointFrame(joints);
        frame.heading = Float.isFinite(pose.heading()) ? pose.heading() : 0f;
        for (Protocol.Bone b : pose.bones()) {
            Integer j = jointOfNode.get(b.nodeId());
            if (j == null) {
                continue; // a node the model does not have
            }
            frame.has[j] = true;
            frame.pos[3 * j] = clamp(b.px());
            frame.pos[3 * j + 1] = clamp(b.py());
            frame.pos[3 * j + 2] = clamp(b.pz());
            float len = (float) Math.sqrt(b.qx() * b.qx() + b.qy() * b.qy() + b.qz() * b.qz() + b.qw() * b.qw());
            boolean usable = len > 1e-6f && Float.isFinite(len);
            frame.quat[4 * j] = usable ? b.qx() / len : 0f;
            frame.quat[4 * j + 1] = usable ? b.qy() / len : 0f;
            frame.quat[4 * j + 2] = usable ? b.qz() / len : 0f;
            frame.quat[4 * j + 3] = usable ? b.qw() / len : 1f;
        }
        frames.addLast(new Entry(arrivalNs, frame));
        while (frames.size() > KEEP) {
            frames.removeFirst();
        }
    }

    private static float clamp(float v) {
        return Math.max(-MAX_POSITION, Math.min(MAX_POSITION, v));
    }

    public synchronized int frameCount() {
        return frames.size();
    }

    /** The frame to draw at nowNs, or null when there is none or the newest pose is older than STALE_NS. */
    public synchronized JointFrame sample(long nowNs) {
        Entry newest = frames.peekLast();
        if (newest == null || nowNs - newest.arrivalNs > STALE_NS) {
            return null;
        }
        long t = nowNs - DELAY_NS;
        Entry before = null;
        Entry after = null;
        for (Entry e : frames) {
            if (e.arrivalNs - t <= 0) {
                before = e;
            } else {
                after = e;
                break;
            }
        }
        if (before == null) {
            return frames.peekFirst().frame; // earlier than the oldest pose we have
        }
        if (after == null) {
            return before.frame; // later than the newest: hold it
        }
        float alpha = (float) (t - before.arrivalNs) / (float) (after.arrivalNs - before.arrivalNs);
        return blend(before.frame, after.frame, alpha);
    }

    /** Interpolates two angles in [0, 2*pi) the short way round, so a turn across north does not swing through south. */
    private static float blendAngle(float a, float b, float alpha) {
        final float twoPi = (float) (2 * Math.PI);
        float diff = b - a;
        if (diff > Math.PI) {
            diff -= twoPi;
        } else if (diff < -Math.PI) {
            diff += twoPi;
        }
        float r = (a + diff * alpha) % twoPi;
        return r < 0 ? r + twoPi : r;
    }

    private JointFrame blend(JointFrame a, JointFrame b, float alpha) {
        JointFrame out = new JointFrame(joints);
        out.heading = blendAngle(a.heading, b.heading, alpha);
        for (int j = 0; j < joints; j++) {
            if (a.has[j] && b.has[j]) {
                out.has[j] = true;
                for (int k = 0; k < 3; k++) {
                    out.pos[3 * j + k] = a.pos[3 * j + k] + (b.pos[3 * j + k] - a.pos[3 * j + k]) * alpha;
                }
                float dot = 0;
                for (int k = 0; k < 4; k++) {
                    dot += a.quat[4 * j + k] * b.quat[4 * j + k];
                }
                float sign = dot < 0 ? -1f : 1f; // q and -q are the same rotation: take the short way round
                float len = 0;
                for (int k = 0; k < 4; k++) {
                    float q = a.quat[4 * j + k] + (sign * b.quat[4 * j + k] - a.quat[4 * j + k]) * alpha;
                    out.quat[4 * j + k] = q;
                    len += q * q;
                }
                len = (float) Math.sqrt(len);
                for (int k = 0; k < 4; k++) {
                    out.quat[4 * j + k] = len > 1e-6f ? out.quat[4 * j + k] / len : (k == 3 ? 1f : 0f);
                }
            } else if (a.has[j] || b.has[j]) {
                JointFrame only = a.has[j] ? a : b;
                out.has[j] = true;
                System.arraycopy(only.pos, 3 * j, out.pos, 3 * j, 3);
                System.arraycopy(only.quat, 4 * j, out.quat, 4 * j, 4);
            }
        }
        return out;
    }
}
