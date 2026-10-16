package dev.cjmc;

/** The aim vector the State packet carries: from the player's feet to the point the crosshair is on. */
public final class AimRay {
    public static final float MIN_DISTANCE = 2f;
    public static final float MAX_DISTANCE = 50f;

    private AimRay() {}

    /**
     * @param hitDistance distance from the eyes to what the crosshair hits; NaN, zero or negative when it hits nothing
     * @return the vector in Minecraft axes, or (0, 0, 0) ("no aim") when an input is not finite
     */
    public static float[] aimVector(double[] feet, double[] eye, double[] look, double hitDistance) {
        for (int k = 0; k < 3; k++) {
            if (!Double.isFinite(feet[k]) || !Double.isFinite(eye[k]) || !Double.isFinite(look[k])) {
                return new float[] {0, 0, 0};
            }
        }
        double distance = hitDistance > 0 ? hitDistance : MAX_DISTANCE; // NaN fails the comparison too
        distance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance));
        float[] out = new float[3];
        for (int k = 0; k < 3; k++) {
            out[k] = (float) (eye[k] - feet[k] + look[k] * distance);
        }
        return out;
    }
}
