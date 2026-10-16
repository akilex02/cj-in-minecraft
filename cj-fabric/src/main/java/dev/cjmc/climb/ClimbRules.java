package dev.cjmc.climb;

/** Whether a jump against a wall becomes a climb, and how high: pure rules over what the world around the player looks like. */
public final class ClimbRules {
    public static final int MIN_HEIGHT = 2;   // blocks; one block is the normal jump of Minecraft
    public static final int MAX_HEIGHT = 3;
    public static final int CLEAR_ABOVE = 2;  // free blocks the body needs on top of the ledge
    public static final double REACH = 0.8;   // blocks from the centre of the player to the face of the wall a climb can start from
    public static final double HUG = 0.3;     // blocks from the centre of the player to the wall when he stands against it (half his width)
    public static final double MAX_STRAY = 0.5; // blocks the player may be away from where the controller last put him before the climb is given up
    public static final double JUMP_HEIGHT = 1.6; // blocks above the ground of a jump within which a wall is still counted from that ground
    public static final double SKIN = 0.01;   // blocks left between the body and the wall when he is taken to it
    public static final double MIN_RISING_SPEED = -0.1; // blocks per tick: slower than this going down is falling, and nothing is grabbed

    private ClimbRules() {}

    /** The height of the climb (MIN_HEIGHT..MAX_HEIGHT) or 0 when the player just jumps. */
    public static int heightOf(int wallBlocks, int clearAbove, boolean standable, boolean pushing, boolean canGrab,
                               boolean sneaking, boolean eligible) {
        if (!eligible || !pushing || !canGrab || sneaking || !standable || clearAbove < CLEAR_ABOVE) {
            return 0;
        }
        return wallBlocks >= MIN_HEIGHT && wallBlocks <= MAX_HEIGHT ? wallBlocks : 0;
    }

    /** Whether the player can start a climb now: on the ground (he pressed the jump key) or going up in a jump (he grabs the ledge). */
    public static boolean canGrab(boolean onGround, double verticalSpeedPerTick) {
        return onGround || verticalSpeedPerTick >= MIN_RISING_SPEED;
    }

    /** How far the player has to be taken to the wall to hang against it: the gap left by the body less a skin, never backwards. */
    public static double approach(double distanceToWall) {
        return Math.max(0.0, distanceToWall - HUG - SKIN);
    }

    /**
     * Whether the movement rules of the moment let him climb at all: not knocked down, a weapon that allows jumping, no sneak forced, and
     * not wearing the jetpack (with it the jump key lifts off, and a climb would take the flight over).
     */
    public static boolean permitted(boolean knockedDown, boolean canJump, boolean forcedSneak, boolean wearingJetpack) {
        return !knockedDown && canJump && !forcedSneak && !wearingJetpack;
    }

    /** The row of blocks a standing player is on (a hair below the top of the block still counts as on it). */
    public static int groundRow(double groundY) {
        return (int) Math.floor(groundY + 0.01);
    }

    /**
     * The height the wall is counted from: the ground of the jump while he is within a jump's height above it (in the air his feet are
     * above the real start of the wall, and a wall of 4 would read as 3), otherwise his feet (no ground seen, stepped down, or far above).
     */
    public static double referenceY(double lastGroundY, double feetY) {
        // every comparison is false for NaN (no ground seen yet): then his feet are the reference
        boolean usable = feetY >= lastGroundY - 0.05 && feetY - lastGroundY <= JUMP_HEIGHT;
        return usable ? lastGroundY : feetY;
    }

    /** Whether he is too far from where the controller last put him: a teleport, a new world, anything that is not the climb. */
    public static boolean strayed(double dx, double dy, double dz) {
        return dx * dx + dy * dy + dz * dz >= MAX_STRAY * MAX_STRAY;
    }

    public static boolean eligible(boolean riding, boolean inFluid, boolean climbing, boolean flying, boolean gliding,
                                   boolean frozen, boolean dead) {
        return !(riding || inFluid || climbing || flying || gliding || frozen || dead);
    }
}
