package dev.cjmc.climb;

/**
 * Time and shape of a climb. The animations of GTA (docs/climb-spike.md) bring their own motion (the skeleton rises and moves ahead
 * with them and goes back when they are cleared); the renderer takes it away so they play in place, and the player makes the whole
 * movement: the lift (the hands reach the ledge, the body is pulled over it, and the feet get to its top) and then the move forward
 * onto it. The settle is the time the animation takes to fade out after the flag drops.
 */
public final class ClimbPath {
    public static final long JUMP_MS = 500;        // CLIMB_PULL is asked for 500 ms after CLIMB_JUMP
    public static final long PULL_END_MS = 1300;   // and CLIMB_STAND 1300 ms after it
    public static final long RISE_END_MS = 1700;   // the feet are on the top of the wall; the rest of the stand moves forward
    public static final long TOTAL_MS = 2100;      // the stand lasts 0.8 s: the animation ends here and the flag drops
    public static final long SETTLE_MS = 450;      // the animation fades out in a quarter of a second, and the pose takes a little to arrive
    public static final double HANG_BLOCKS = 2.2;  // how far above his feet the hands reach hanging from the ledge (tuned by eye)
    public static final double PULL_BELOW = 0.3;   // how far below the top of the wall the feet are when the pull ends (tuned by eye)

    private ClimbPath() {}

    /** Blocks the player has risen at this time, for a wall `height` blocks high: the hang, the pull, and the last bit up to the top. */
    public static double lift(double height, long elapsedMs) {
        double hang = Math.max(0.0, height - HANG_BLOCKS);
        double pulled = height - PULL_BELOW;
        return between(0.0, hang, elapsedMs, 0, JUMP_MS)
                + between(0.0, pulled - hang, elapsedMs, JUMP_MS, PULL_END_MS)
                + between(0.0, height - pulled, elapsedMs, PULL_END_MS, RISE_END_MS);
    }

    /** 0 at the start, 1 when the hang begins: how far the player has been taken to the wall. */
    public static double approachFraction(long elapsedMs) {
        return between(0.0, 1.0, elapsedMs, 0, JUMP_MS);
    }

    /** 0 until the feet are on the top, 1 when the animation ends: how far onto the ledge the player has moved. */
    public static double forwardFraction(long elapsedMs) {
        return between(0.0, 1.0, elapsedMs, RISE_END_MS, TOTAL_MS);
    }

    /** Whether the climb animation is still playing (the flag for the bridge is up). */
    public static boolean animating(long elapsedMs) {
        return elapsedMs < TOTAL_MS;
    }

    public static boolean done(long elapsedMs) {
        return elapsedMs >= TOTAL_MS + SETTLE_MS;
    }

    private static double between(double from, double to, long elapsedMs, long startMs, long endMs) {
        double fraction = Math.max(0.0, Math.min(1.0, (double) (elapsedMs - startMs) / (endMs - startMs)));
        return from + (to - from) * fraction;
    }
}
