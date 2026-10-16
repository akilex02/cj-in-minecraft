package dev.cjmc;

/** Pure geometry for drawing a shot's tracer. */
public final class ShotGeometry {
    private ShotGeometry() {}

    /** `count` points along the ray, the first one step in from the origin and the last at `length`. Empty when the ray is not usable. */
    public static double[][] tracerPoints(double[] origin, double[] dir, double length, int count) {
        double len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
        if (count <= 0 || !(length > 0) || !(len > 1e-9) || !Double.isFinite(len)) {
            return new double[0][];
        }
        double[][] points = new double[count][3];
        for (int i = 0; i < count; i++) {
            double d = length * (i + 1) / count;
            for (int k = 0; k < 3; k++) {
                points[i][k] = origin[k] + dir[k] / len * d;
            }
        }
        return points;
    }
}
