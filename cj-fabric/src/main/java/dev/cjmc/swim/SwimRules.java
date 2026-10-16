package dev.cjmc.swim;

/** The rules of swimming: when the buoyancy works and where it takes the player vertically. Pure: the client applies them every tick. */
public final class SwimRules {
    public static final double FLOAT_DEPTH = 1.15;    // blocks of the feet below the surface when floating: CJ lies horizontal with his centre 1.0 above them, his back at the water
    public static final double TREAD_DEPTH = 1.4;     // and upright, treading water: the water at his shoulders, his arms under it
    public static final double DIVE_SPEED = 0.08;     // blocks per tick going down with Shift (1.6 blocks a second)
    public static final double SURFACE_SPEED = 0.08;  // blocks per tick coming up at most (1.6 blocks a second; 2.4 felt too quick when trying it)
    public static final double SINK_MAX = 0.1;        // blocks per tick going down at most toward the float height from above
    public static final double GAIN = 0.25;           // the share of the height error corrected each tick
    public static final double CRAWL_SPEED = 0.22;    // blocks per tick of horizontal speed crawling on the surface (4.4 blocks a second; 2.4 hardly beat swimming without it)
    public static final double CRAWL_CONTROL = 0.2;   // the share of the way to that speed made each tick
    public static final double MIN_DEPTH = 1.0;       // blocks of water over the feet below which he walks on the bottom (a puddle); less than FLOAT_DEPTH, so floating never blinks
    public static final long POSE_FADE_MS = 400;      // the swim pose takes this long to fade out after the water is left, with the stroke's own motion on it
    public static final double PUSH_LIMIT = 0.2;      // blocks per tick of vertical speed above which it is Minecraft's own push (a bubble column, a riptide) and not ours
    public static final double LOOK_DIVE_PITCH = 20;  // degrees of looking down above which swimming fast is a dive
    public static final double MOVING_SPEED = 0.03;   // blocks per tick of horizontal speed above which he counts as moving

    private SwimRules() {}

    public static boolean allowed(boolean inWater, boolean riding, boolean jetpackFlying, boolean gliding, boolean climbingLadder,
                                  boolean creativeFlying, boolean knockedDown, boolean dead, boolean wallClimbing) {
        return inWater && !(riding || jetpackFlying || gliding || climbingLadder || creativeFlying || knockedDown || dead || wallClimbing);
    }

    /** The height of his feet when floating: lying down (swimming) his back is at the water, upright (treading) the water is at his shoulders. */
    public static double floatHeight(double surfaceY, boolean swimming) {
        return surfaceY - (swimming ? FLOAT_DEPTH : TREAD_DEPTH);
    }

    /**
     * The vertical speed after this tick. Space is Minecraft's (swim up: left as it is); Shift dives at a fixed speed; otherwise the
     * buoyancy takes him toward the float height, coming up no faster than SURFACE_SPEED and going down no faster than SINK_MAX.
     */
    public static double verticalSpeed(double vy, double feetY, double surfaceY, boolean swimming, boolean diving, boolean ascending) {
        if (ascending) {
            return vy;
        }
        if (diving) {
            return -DIVE_SPEED;
        }
        double error = floatHeight(surfaceY, swimming) - feetY;
        return Math.max(-SINK_MAX, Math.min(SURFACE_SPEED, error * GAIN));
    }

    /**
     * Whether the water over his feet is deep enough to swim in: a puddle of one block is walked through. Once he swims, holding Space
     * lifts his feet almost to the surface, so while he is off the ground he goes on swimming however little water is left over them.
     */
    public static boolean deepEnough(double depthOverFeet, boolean wasSwimming, boolean onGround) {
        return depthOverFeet >= MIN_DEPTH || (wasSwimming && !onGround);
    }

    /** Whether the pose of the swim is still fading out `msSinceInWater` after the water was left (the picture must go on taking its motion away). */
    public static boolean poseFading(long msSinceInWater) {
        return msSinceInWater <= POSE_FADE_MS;
    }

    /** Whether the skeleton can be taken as the baseline again: the last swim pose has faded, so it carries no stroke. */
    public static boolean baselineStale(long msSinceInWater) {
        return msSinceInWater > POSE_FADE_MS;
    }

    /** Whether the vertical speed is a push of Minecraft that the buoyancy must not overwrite: a strong one, or the hop against a step. */
    public static boolean leavesToMinecraft(double vy, boolean horizontalCollision) {
        return horizontalCollision || Math.abs(vy) > PUSH_LIMIT;
    }

    /** Whether swimming fast and looking down is a dive: Minecraft pulls him down along the look and the buoyancy must not undo it. */
    public static boolean lookDiving(boolean swimmingPose, double pitchDegrees) {
        return swimmingPose && pitchDegrees > LOOK_DIVE_PITCH;
    }

    /** Whether he crawls: the run key is held while he moves. Minecraft cancels the sprint of a player in the water with his head out, so the key is read. */
    public static boolean crawling(boolean runKeyHeld, boolean moving) {
        return runKeyHeld && moving;
    }

    /** One axis of the horizontal speed crawling on the surface: it moves toward `wish` (-1 to 1) times CRAWL_SPEED, and slows down with none. */
    public static double crawlStep(double current, double wish) {
        return current + (wish * CRAWL_SPEED - current) * CRAWL_CONTROL;
    }

    /** The movement keys (forward and left, -1 to 1) turned to the way he faces (yaw in degrees), as the game does for walking: {x, z}, never longer than 1. */
    public static double[] wish(double forward, double left, double yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double x = left * Math.cos(yaw) - forward * Math.sin(yaw);
        double z = forward * Math.cos(yaw) + left * Math.sin(yaw);
        double scale = Math.max(1.0, Math.sqrt(x * x + z * z)); // two keys at once are not faster than one
        return new double[] {x / scale, z / scale};
    }

    public static boolean moving(double horizontalSpeedPerTick) {
        return Math.abs(horizontalSpeedPerTick) > MOVING_SPEED;
    }

    /** The server forgets the distance fallen while he is in the water: it only grows on the way down, and would add up over a long dive. */
    public static boolean resetsFallDistance(boolean inWater) {
        return inWater;
    }
}
