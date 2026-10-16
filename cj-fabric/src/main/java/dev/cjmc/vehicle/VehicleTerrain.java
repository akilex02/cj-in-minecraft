package dev.cjmc.vehicle;

/** How the car sits on the terrain of Minecraft: falling when there is no ground, and the pitch and roll that follow the ground under its wheels. Pure. */
public final class VehicleTerrain {
    public static final double GRAVITY = 0.08;     // blocks per tick squared, as in Minecraft
    public static final double DRAG = 0.98;        // the share of the fall speed kept each tick
    public static final double SMOOTHING = 0.3;    // the share of the way to the target pitch and roll made each tick

    public record Settle(double y, double vy, boolean grounded) {}

    private VehicleTerrain() {}

    /**
     * The car at height y with vertical speed vy (blocks per tick, negative going down): it sticks to the ground if that is within a step below, else it falls.
     * With no ground under it at all it keeps falling.
     */
    public static Settle settle(Terrain world, double x, double y, double z, double yawDegrees, double vy) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(yawDegrees) || !Double.isFinite(vy)) {
            return new Settle(y, 0, false);
        }
        double support = Double.NEGATIVE_INFINITY;
        double[] u = VehicleCollision.forward(yawDegrees);
        double[] v = VehicleCollision.right(yawDegrees);
        double hl = VehicleCollision.LENGTH / 2;
        double hw = VehicleCollision.WIDTH / 2;
        double extX = hl * Math.abs(u[0]) + hw * Math.abs(v[0]);
        double extZ = hl * Math.abs(u[1]) + hw * Math.abs(v[1]);
        for (int bx = (int) Math.floor(x - extX); bx <= (int) Math.floor(x + extX); bx++) {
            for (int bz = (int) Math.floor(z - extZ); bz <= (int) Math.floor(z + extZ); bz++) {
                if (VehicleCollision.overlaps(bx, bz, x, z, u, v, hl, hw)) {
                    support = Math.max(support, world.top(bx, bz, y + VehicleCollision.STEP_HEIGHT));
                }
            }
        }
        if (y - support <= VehicleCollision.STEP_HEIGHT) {
            return new Settle(Math.max(support, y - VehicleCollision.STEP_HEIGHT), 0, true);
        }
        double speed = (vy - GRAVITY) * DRAG;
        double next = y + speed;
        if (next <= support) {
            return new Settle(support, 0, true);
        }
        return new Settle(next, speed, false);
    }

    /** {pitch, roll} in radians of the car resting at height y: nose up and right side up are positive. The wheel over no ground counts as level with the car. */
    public static double[] pitchRoll(Terrain world, double x, double y, double z, double yawDegrees) {
        double[] u = VehicleCollision.forward(yawDegrees);
        double[] v = VehicleCollision.right(yawDegrees);
        double front = 0;
        double rear = 0;
        double rightSide = 0;
        double leftSide = 0;
        for (int fs = -1; fs <= 1; fs += 2) {
            for (int rs = -1; rs <= 1; rs += 2) {
                double wx = x + fs * VehicleCollision.WHEELBASE / 2 * u[0] + rs * VehicleCollision.TRACK / 2 * v[0];
                double wz = z + fs * VehicleCollision.WHEELBASE / 2 * u[1] + rs * VehicleCollision.TRACK / 2 * v[1];
                double h = world.top((int) Math.floor(wx), (int) Math.floor(wz), y + VehicleCollision.STEP_HEIGHT);
                if (h == Double.NEGATIVE_INFINITY) {
                    h = y;
                }
                if (fs > 0) {
                    front += h / 2;
                } else {
                    rear += h / 2;
                }
                if (rs > 0) {
                    rightSide += h / 2;
                } else {
                    leftSide += h / 2;
                }
            }
        }
        return new double[] {Math.atan2(front - rear, VehicleCollision.WHEELBASE), Math.atan2(rightSide - leftSide, VehicleCollision.TRACK)};
    }

    /** Eases the pitch and roll toward their targets so the car does not jerk at every block. */
    public static final class Smoother {
        private double pitch;
        private double roll;

        public double pitch() {
            return pitch;
        }

        public double roll() {
            return roll;
        }

        public void update(double targetPitch, double targetRoll) {
            if (Double.isFinite(targetPitch) && Double.isFinite(targetRoll)) {
                pitch += (targetPitch - pitch) * SMOOTHING;
                roll += (targetRoll - roll) * SMOOTHING;
            }
        }

        public void reset() {
            pitch = 0;
            roll = 0;
        }
    }
}
