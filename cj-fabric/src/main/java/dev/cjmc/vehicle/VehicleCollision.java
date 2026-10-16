package dev.cjmc.vehicle;

/**
 * The car as a box (LENGTH x WIDTH x HEIGHT) against the blocks of Minecraft: moving it, climbing a low step, sliding along a wall.
 * Pure. y is the height of the underside of the car; yaw is the Minecraft yaw in degrees (0 faces south, +z).
 */
public final class VehicleCollision {
    public static final double LENGTH = 4.9;
    public static final double WIDTH = 1.9;
    public static final double HEIGHT = 1.5;
    public static final double STEP_HEIGHT = 1.0;   // the highest step the car climbs without stopping: a full block, as a horse does
    public static final double WHEELBASE = 3.41;    // distance between the axles (the wheel frames of the Greenwood)
    public static final double TRACK = 1.85;        // distance between the wheels of an axle
    static final double EPS = 1e-9;
    private static final double MAX_STEP = 0.25;    // blocks moved at a time, so a wall of one block is never jumped over
    private static final int BISECTIONS = 10;

    public record Result(double x, double y, double z, boolean blocked) {}

    private VehicleCollision() {}

    /** Moves the car from (x, z) toward (wantedX, wantedZ): one axis at a time, so it slides along a wall; stops where the box touches. */
    public static Result move(Terrain world, double x, double y, double z, double yawDegrees, double wantedX, double wantedZ) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(yawDegrees)
                || !Double.isFinite(wantedX) || !Double.isFinite(wantedZ)) {
            return new Result(x, y, z, true);
        }
        double dx = wantedX - x;
        double dz = wantedZ - z;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dz)) / MAX_STEP));
        double sx = dx / steps;
        double sz = dz / steps;
        boolean blocked = false;
        for (int i = 0; i < steps; i++) {
            if (sx != 0) {
                double t = reach(world, x, y, z, yawDegrees, sx, 0);
                if (t > 0) {
                    y = placeY(world, x + sx * t, y, z, yawDegrees);
                    x += sx * t;
                }
                if (t < 1) {
                    blocked = true;
                }
            }
            if (sz != 0) {
                double t = reach(world, x, y, z, yawDegrees, 0, sz);
                if (t > 0) {
                    y = placeY(world, x, y, z + sz * t, yawDegrees);
                    z += sz * t;
                }
                if (t < 1) {
                    blocked = true;
                }
            }
        }
        return new Result(x, y, z, blocked);
    }

    /** Whether the box fits where it is (no block inside it, with the step it would need). */
    public static boolean fits(Terrain world, double x, double y, double z, double yawDegrees) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Double.isFinite(yawDegrees)
                && !Double.isNaN(placeY(world, x, y, z, yawDegrees));
    }

    /** The share (0 to 1) of the displacement (sx, sz) the box can make from where it is. */
    private static double reach(Terrain world, double x, double y, double z, double yaw, double sx, double sz) {
        if (!Double.isNaN(placeY(world, x + sx, y, z + sz, yaw))) {
            return 1;
        }
        double low = 0;
        double high = 1;
        for (int i = 0; i < BISECTIONS; i++) {
            double mid = (low + high) / 2;
            if (Double.isNaN(placeY(world, x + sx * mid, y, z + sz * mid, yaw))) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return low;
    }

    /** The height of the underside of the box if it is put at (x, z) from height y (never lower than y), or NaN if it does not fit there. */
    static double placeY(Terrain world, double x, double y, double z, double yawDegrees) {
        double[] u = forward(yawDegrees);
        double[] v = right(yawDegrees);
        double hl = LENGTH / 2;
        double hw = WIDTH / 2;
        double extX = hl * Math.abs(u[0]) + hw * Math.abs(v[0]);
        double extZ = hl * Math.abs(u[1]) + hw * Math.abs(v[1]);
        double limit = y + STEP_HEIGHT;
        double newY = y;
        for (int pass = 0; pass < 2; pass++) {
            for (int bx = (int) Math.floor(x - extX); bx <= (int) Math.floor(x + extX); bx++) {
                for (int bz = (int) Math.floor(z - extZ); bz <= (int) Math.floor(z + extZ); bz++) {
                    if (!overlaps(bx, bz, x, z, u, v, hl, hw)) {
                        continue;
                    }
                    if (pass == 0) {
                        newY = Math.max(newY, world.top(bx, bz, limit));
                    } else if (world.ceiling(bx, bz, newY) < newY + HEIGHT) {
                        return Double.NaN;
                    }
                }
            }
        }
        return newY;
    }

    /**
     * Whether the block column (bx, bz) is under the rectangle centred at (cx, cz) with the axes u (length) and v (width), the touching of an edge not
     * counting. The caller only asks about columns inside the bounding box of the rectangle, so the axes of the world need no test.
     */
    static boolean overlaps(int bx, int bz, double cx, double cz, double[] u, double[] v, double hl, double hw) {
        double dx = bx + 0.5 - cx;
        double dz = bz + 0.5 - cz;
        if (Math.abs(dx * u[0] + dz * u[1]) >= hl + 0.5 * (Math.abs(u[0]) + Math.abs(u[1])) - EPS) {
            return false;
        }
        return Math.abs(dx * v[0] + dz * v[1]) < hw + 0.5 * (Math.abs(v[0]) + Math.abs(v[1])) - EPS;
    }

    /** The direction the car faces on the (x, z) plane. */
    static double[] forward(double yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return new double[] {-Math.sin(yaw), Math.cos(yaw)};
    }

    /** The direction of the right hand of the car on the (x, z) plane. */
    static double[] right(double yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return new double[] {-Math.cos(yaw), -Math.sin(yaw)};
    }
}
