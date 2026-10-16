package dev.cjmc;

/** Where a point in camera space lands on the screen (camera space: x right, y up, forward is -z, as Minecraft's camera). */
public final class CrosshairMath {
    private CrosshairMath() {}

    /** @return {x, y} in pixels from the top-left, or null when the point is behind the camera or an input is unusable */
    public static float[] project(double x, double y, double z, double fovDegrees, int width, int height) {
        if (!(z < -1e-6) || !Double.isFinite(x) || !Double.isFinite(y) || !(fovDegrees > 0) || fovDegrees >= 180 || width <= 0 || height <= 0) {
            return null;
        }
        double t = Math.tan(Math.toRadians(fovDegrees) / 2.0);
        double ndcY = (y / -z) / t;
        double ndcX = (x / -z) / (t * width / height);
        return new float[] {(float) (width / 2.0 + ndcX * width / 2.0), (float) (height / 2.0 - ndcY * height / 2.0)};
    }
}
