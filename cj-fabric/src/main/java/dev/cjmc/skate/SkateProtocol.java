package dev.cjmc.skate;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The datagrams of the Skate engine's host mode (skate-host/README.md): the controller actions, the world as quads, a teleport, and the pose that comes back.
 * Little-endian, a header of magic, version and type. Pure: no Minecraft.
 */
public final class SkateProtocol {
    public static final int MAGIC = 0x48384B53; // "SK8H"
    public static final short VERSION = 1;
    public static final short TYPE_INPUT = 1;
    public static final short TYPE_POSE = 2;
    public static final short TYPE_WORLD_SET = 3;
    public static final short TYPE_WORLD_CLEAR = 4;
    public static final short TYPE_TELEPORT = 5;
    public static final int ACTIONS = 18;
    /** The most quads of one region, and the farthest a coordinate may be: what the engine accepts. */
    public static final int MAX_QUADS = 1000;
    public static final float MAX_COORDINATE = 100_000f;

    private SkateProtocol() {}

    /** The engine's answer after each tick: the skater's global bone matrices, the root and the deck of the board, in Minecraft's world. */
    public record Pose(int tick, float[] root, float[] deckBasis, float[] deckPosition, float[] bones) {
        public int boneCount() {
            return bones.length / 16;
        }

        /** The 16 floats of bone `i` (a column-major matrix). */
        public float[] bone(int i) {
            float[] out = new float[16];
            System.arraycopy(bones, 16 * i, out, 0, 16);
            return out;
        }
    }

    private static ByteBuffer buffer(int size, short type) {
        ByteBuffer b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(MAGIC).putShort(VERSION).putShort(type);
        return b;
    }

    /** The 18 actions of a controller (see skate-core's gameplay_map), each from -1 to 1; a number that is not one counts as released. */
    public static byte[] encodeInput(int seq, float[] actions) {
        if (actions.length != ACTIONS) {
            throw new IllegalArgumentException("the engine takes " + ACTIONS + " actions, not " + actions.length);
        }
        ByteBuffer b = buffer(8 + 4 + 4 + ACTIONS * 4, TYPE_INPUT);
        b.putInt(seq).putInt(0);
        for (float a : actions) {
            b.putFloat(Float.isFinite(a) ? Math.max(-1f, Math.min(1f, a)) : 0f);
        }
        return b.array();
    }

    /** A region: `quadCount` quads of four corners of three floats each, in `quads`. See the README of the patch for the order of the corners. */
    public static byte[] encodeWorldSet(int region, float[] quads, int quadCount) {
        if (quadCount < 0 || quadCount > MAX_QUADS) {
            throw new IllegalArgumentException("a region holds at most " + MAX_QUADS + " quads, not " + quadCount);
        }
        if (quads.length < quadCount * 12) {
            throw new IllegalArgumentException(quadCount + " quads need " + quadCount * 12 + " floats, not " + quads.length);
        }
        ByteBuffer b = buffer(8 + 8 + quadCount * 48, TYPE_WORLD_SET);
        b.putInt(region).putInt(quadCount);
        for (int i = 0; i < quadCount * 12; i++) {
            float v = quads[i];
            if (!Float.isFinite(v) || Math.abs(v) > MAX_COORDINATE) {
                throw new IllegalArgumentException("a corner coordinate (" + v + ") is out of the engine's reach");
            }
            b.putFloat(v);
        }
        return b.array();
    }

    public static byte[] encodeWorldClear(int region) {
        return buffer(12, TYPE_WORLD_CLEAR).putInt(region).array();
    }

    /** Puts the skater, on the board, at (x, y, z) facing `heading` (radians about Y; 0 faces +Z) and moving at (vx, vy, vz). */
    public static byte[] encodeTeleport(float x, float y, float z, float heading, float vx, float vy, float vz) {
        ByteBuffer b = buffer(8 + 7 * 4, TYPE_TELEPORT);
        for (float v : new float[] {x, y, z, heading, vx, vy, vz}) {
            if (!Float.isFinite(v) || Math.abs(v) > MAX_COORDINATE) {
                throw new IllegalArgumentException("a teleport number (" + v + ") is out of the engine's reach");
            }
            b.putFloat(v);
        }
        return b.array();
    }

    /** Returns null when the datagram is not a valid pose. */
    public static Pose decodePose(byte[] data, int len) {
        if (len < 16) {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(data, 0, len).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getInt() != MAGIC || b.getShort() != VERSION || b.getShort() != TYPE_POSE) {
            return null;
        }
        int tick = b.getInt();
        int count = b.getInt();
        if (count < 0 || count > 256 || len != 16 + 64 + 48 + count * 64) {
            return null;
        }
        float[] root = new float[16];
        float[] basis = new float[9];
        float[] position = new float[3];
        float[] bones = new float[count * 16];
        read(b, root);
        read(b, basis);
        read(b, position);
        read(b, bones);
        return new Pose(tick, root, basis, position, bones);
    }

    private static void read(ByteBuffer b, float[] into) {
        for (int i = 0; i < into.length; i++) {
            into[i] = b.getFloat();
        }
    }
}
