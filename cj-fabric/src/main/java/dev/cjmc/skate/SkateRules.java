package dev.cjmc.skate;

import java.net.InetSocketAddress;

/** The decisions of the skate mode that need no Minecraft: when a player may start, where the engine is, when it counts as gone, and the board's heading. */
public final class SkateRules {
    public static final int DEFAULT_PORT = 34600;
    static final long FIRST_ANSWER_NS = 3_000_000_000L;
    static final long SILENCE_NS = 1_500_000_000L;

    private SkateRules() {}

    public static boolean canRide(boolean riding, boolean flying, boolean spectator, boolean dead, boolean inFluid) {
        return !(riding || flying || spectator || dead || inFluid);
    }

    /** `host:port` of the engine (CJ_SKATE_ENGINE), or the default 127.0.0.1:34600 when nothing sensible is given. */
    public static InetSocketAddress engineAddress(String setting) {
        InetSocketAddress fallback = new InetSocketAddress("127.0.0.1", DEFAULT_PORT);
        if (setting == null) {
            return fallback;
        }
        int colon = setting.lastIndexOf(':');
        if (colon <= 0 || colon == setting.length() - 1) {
            return fallback;
        }
        try {
            int port = Integer.parseInt(setting.substring(colon + 1));
            return port >= 1 && port <= 65535 ? new InetSocketAddress(setting.substring(0, colon), port) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The engine is gone: it has never answered and the first answer is overdue, or it answered but has been silent for too long. */
    public static boolean engineSilent(boolean hasAnswered, long nanosSinceLast) {
        return nanosSinceLast > (hasAnswered ? SILENCE_NS : FIRST_ANSWER_NS);
    }

    /** The engine's heading (radians; 0 faces +Z, positive turns toward +X) for a Minecraft yaw (degrees; 0 faces +Z, positive turns toward -X). */
    public static float headingOf(float yawDegrees) {
        return (float) -Math.toRadians(yawDegrees);
    }

    /** Riding, the root of the animation is the centre of the deck; off the board (Y: walking with it in the hand) the root is on the ground and the deck elsewhere. */
    static final double ON_BOARD = 0.05;
    /** Off the board the root is on the ground: the player stands this much over it, never in the block under it. */
    static final double OVER_GROUND = 0.005;

    /** Where the player goes for a pose: under the deck when riding, on the skater's own spot when he is off the board. */
    public static double[] playerPosition(float[] root, float[] deck) {
        double dx = deck[0] - root[0], dy = deck[1] - root[1], dz = deck[2] - root[2];
        boolean riding = dx * dx + dy * dy + dz * dz < ON_BOARD * ON_BOARD;
        return new double[] {root[0], root[1] + (riding ? -SkateRide.DECK_ABOVE_GROUND : OVER_GROUND), root[2]};
    }

    /** Below this speed (m/s) the camera is left where it is. */
    static final double FOLLOW_SPEED = 1.0;
    /** How quickly the camera swings behind the skater (per second: about 95% of the way in a second). */
    static final double FOLLOW_RATE = 3.0;

    /**
     * The yaw (Minecraft degrees: 0 faces +z, 90 faces -x) the camera turns to after `seconds`, from `yaw`, following a skater who moves at (vx, vz) m/s: smoothly,
     * the short way round, and not at all while he hardly moves.
     */
    public static float followYaw(float yaw, double vx, double vz, double seconds) {
        if (vx * vx + vz * vz < FOLLOW_SPEED * FOLLOW_SPEED || seconds <= 0) {
            return yaw;
        }
        float target = (float) Math.toDegrees(Math.atan2(-vx, vz));
        float diff = ((target - yaw) % 360f + 540f) % 360f - 180f;
        return yaw + diff * (float) (1.0 - Math.exp(-FOLLOW_RATE * seconds));
    }

    /** L3 and R3 (both sticks pressed in) together: gets on the board and off it, as the right click with the skateboard does. */
    public static boolean toggleCombo(float[] actions) {
        return actions != null && actions[2] > 0.5f && actions[5] > 0.5f;
    }

    /** A button's press: true once when it goes down, not again until it has been let go. */
    public static final class Edge {
        private boolean down;

        public boolean rose(boolean now) {
            boolean rose = now && !down;
            down = now;
            return rose;
        }
    }

    /** What the player is told when the engine does not answer: skating needs the optional skate pack, started before getting on the board. */
    public static final String ENGINE_MISSING = "Skate: the Skate engine is not running. Skating needs the optional skate pack (see LEEME-skate.md); "
            + "start it with skate.bat / skate.sh before getting on the board.";
}
