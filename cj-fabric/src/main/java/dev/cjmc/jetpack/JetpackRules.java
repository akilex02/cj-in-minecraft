package dev.cjmc.jetpack;

/** The rules of the jetpack: when it works and how it moves the player. Pure: the client applies them every tick. */
public final class JetpackRules {
    public static final double MAX_RISE = 0.4;          // blocks per tick (8 blocks a second)
    public static final double RISE_ACCEL = 0.16;       // per tick; the game takes its own 0.08 of gravity back, so the net gain is 0.08
    public static final double TERMINAL_FALL = -0.3;    // blocks per tick (6 blocks a second; 3 felt too slow when trying it)
    public static final double HORIZONTAL_SPEED = 0.35; // blocks per tick (7 blocks a second)
    public static final double AIR_CONTROL = 0.2;       // how much of the way to the wanted speed is made each tick
    public static final double MAX_TILT_DEGREES = 25;

    private JetpackRules() {}

    public static boolean allowed(boolean wearing, boolean riding, boolean inFluid, boolean gliding, boolean climbingLadder,
                                  boolean creativeFlying, boolean knockedDown, boolean dead, boolean wallClimbing) {
        return wearing && !(riding || inFluid || gliding || climbingLadder || creativeFlying || knockedDown || dead || wallClimbing);
    }

    /** The vertical speed (blocks per tick) after this tick: climbing while the key is held, otherwise falling no faster than the terminal speed. */
    public static double verticalSpeed(double vy, boolean thrust) {
        return thrust ? Math.min(MAX_RISE, vy + RISE_ACCEL) : Math.max(vy, TERMINAL_FALL);
    }

    /** One axis of the horizontal speed: it moves toward `wish` (-1 to 1, already normalised) times the top speed. */
    public static double horizontalSpeed(double current, double wish) {
        return current + (wish * HORIZONTAL_SPEED - current) * AIR_CONTROL;
    }

    public static double tiltDegrees(double horizontalSpeed) {
        return MAX_TILT_DEGREES * Math.min(1.0, Math.abs(horizontalSpeed) / HORIZONTAL_SPEED);
    }

    /**
     * Whether he is flying with the jetpack next tick: it starts with the thrust and goes on until he lands. Leaving the ground without
     * thrusting (down a stair, off a slab) is not flying, or the pose and the sound would blink on every step.
     */
    public static boolean flyingNext(boolean allowed, boolean thrust, boolean wasFlying, boolean onGround) {
        return allowed && (thrust || (wasFlying && !onGround));
    }

    /** The server forgets the distance fallen while he wears it: it only grows on the way down, so a long hover would add up to a lethal fall. */
    public static boolean resetsFallDistance(boolean wearing) {
        return wearing;
    }

    /**
     * The world direction (dirX, dirZ) he moves in, in the axes of the body that is drawn: {left, forward}. `bodyYawDegrees` is the yaw the
     * body is drawn with (it turns toward where he goes), not the camera's: the lean is applied in the frame of the drawn body.
     */
    public static double[] localLean(double dirX, double dirZ, double bodyYawDegrees) {
        double yaw = Math.toRadians(bodyYawDegrees);
        double left = dirX * Math.cos(yaw) + dirZ * Math.sin(yaw);
        double forward = dirX * -Math.sin(yaw) + dirZ * Math.cos(yaw);
        return new double[] {left, forward};
    }

    public static boolean cancelsDamage(boolean wearing, boolean fall) {
        return wearing && fall;
    }
}
