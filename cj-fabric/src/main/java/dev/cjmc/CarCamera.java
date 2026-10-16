package dev.cjmc;

/**
 * The third-person camera at the wheel, as in San Andreas: it goes back from the car so the whole of it is in the picture, without touching the field of
 * view (a wide one stretches the car). Eased in and out; the distance is never shorter than what the game chose.
 */
public final class CarCamera {
    public static final double DISTANCE = 7.5; // blocks from the eyes to the camera (the third person normally keeps 4); the car is 4.9 long
    private static final double SECONDS_TO_FULL = 0.5;

    private double amount; // 0 (the normal camera) to 1 (the camera of the car)

    /** @return how far the move back has gone, 0 to 1; @param dtSeconds time since the last frame */
    public double step(boolean driving, double dtSeconds) {
        double dt = Double.isFinite(dtSeconds) ? Math.max(0, dtSeconds) : 0;
        double delta = dt / SECONDS_TO_FULL;
        amount = driving ? Math.min(1, amount + delta) : Math.max(0, amount - delta);
        return amount;
    }

    /** The distance from the eyes to the camera: `current` (what the game chose) moved toward DISTANCE by `amount`, never shorter than `current`. */
    public static double distanceFor(double current, double amount) {
        double target = Math.max(current, DISTANCE);
        return current + (target - current) * amount;
    }
}
