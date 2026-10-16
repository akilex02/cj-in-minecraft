package dev.cjmc.vehicle;

/**
 * The light smoke of the tailpipe of the Greenwood: where it comes out (the back right, low), which way it drifts and how often a puff is made.
 * Pure. The car is the box of VehicleCollision (x, z its centre, y its underside, yaw in Minecraft degrees, 0 faces south).
 */
public final class ExhaustSmoke {
    public static final double BACK = 2.45;   // blocks behind the centre: the end of the 4.9 long car
    public static final double RIGHT = 0.55;  // blocks to the car's right (its right hand, not Minecraft's)
    public static final double HEIGHT = 0.3;  // above the underside
    private static final double REST_CHANCE = 0.35;
    private static final double FULL_AT = 3.0; // m/s from which a puff is made every tick

    private ExhaustSmoke() {}

    /** Where the smoke comes out, in the world of Minecraft. */
    public static double[] position(double x, double y, double z, double yawDegrees) {
        double[] forward = VehicleCollision.forward(yawDegrees);
        double[] right = VehicleCollision.right(yawDegrees);
        return new double[] {x - BACK * forward[0] + RIGHT * right[0], y + HEIGHT, z - BACK * forward[1] + RIGHT * right[1]};
    }

    /** The velocity of a puff (blocks per tick): up, and back with the wind of the car, a little more the faster it goes. */
    public static double[] velocity(double yawDegrees, double speed) {
        double[] forward = VehicleCollision.forward(yawDegrees);
        double s = Double.isFinite(speed) ? Math.min(Math.abs(speed), 30.0) : 0.0;
        double back = 0.01 + s * 0.004;
        return new double[] {-forward[0] * back, 0.03, -forward[1] * back};
    }

    /** The chance (0 to 1) that a puff is made in a tick: some at rest, every tick when moving, nothing for a broken speed. */
    public static double chance(double speed) {
        if (!Double.isFinite(speed)) {
            return 0.0;
        }
        double v = Math.abs(speed);
        return v >= FULL_AT ? 1.0 : REST_CHANCE + (1.0 - REST_CHANCE) * v / FULL_AT;
    }
}
