package dev.cjmc;

/**
 * The third-person camera while aiming, as in San Andreas: it slides over CJ's right shoulder and comes in close to him, eased in and
 * out. The shift is a sideways offset; the pull-in is the distance to the eyes, which is never made longer than the game put it.
 */
public final class CameraShoulder {
    public static final double OFFSET = 0.45; // compared with a screenshot of San Andreas: CJ stays almost centred, a little left of the crosshair
    public static final double AIM_DISTANCE = 1.3; // blocks from the eyes to the camera while aiming (the third person normally keeps 4); CJ fills the picture as in San Andreas
    private static final double SECONDS_TO_FULL = 0.2;

    private double amount; // 0 (centred) to 1 (full shift)

    /** How far the transition has gone, 0 (not aiming) to 1 (aiming), after the last step. */
    public double amount() {
        return amount;
    }

    /** The distance from the eyes to the camera for this frame: `current` (what the game worked out, wall clipping included) pulled in toward AIM_DISTANCE by `amount`. */
    public static double distanceFor(double current, double amount) {
        double target = Math.min(current, AIM_DISTANCE);
        return current + (target - current) * amount;
    }

    /** @return the shift in blocks to the right for this frame; @param dtSeconds time since the last frame */
    public double step(boolean aiming, double dtSeconds) {
        double dt = Double.isFinite(dtSeconds) ? Math.max(0, dtSeconds) : 0;
        double delta = dt / SECONDS_TO_FULL;
        amount = aiming ? Math.min(1, amount + delta) : Math.max(0, amount - delta);
        return amount * OFFSET;
    }
}
