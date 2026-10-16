package dev.cjmc.vehicle;

/** The small rules of driving the car: when you may get in or must get out, the wheel, the pedals, the wheels' spin. Pure. */
public final class VehicleRules {
    public static final double STEER_RISE_PER_S = 4.0;       // the wheel reaches full lock in a quarter of a second
    public static final double STEER_RETURN_PER_S = 6.67;    // and comes back to the middle in 0.15 s
    public static final double MAX_DT = 0.1;                 // seconds: a longer frame is a hitch, and the wheel does not jump through it
    public static final double MAX_WHEEL_ANGLE = 0.6;        // radians: the most the front wheels show

    private VehicleRules() {}

    public static boolean canEnter(boolean bridgeLive, boolean riding, boolean inWater, boolean jetpackFlying, boolean climbing,
                                   boolean knockedDown, boolean dead, boolean screenOpen, boolean fits) {
        return bridgeLive && !(riding || inWater || jetpackFlying || climbing || knockedDown || dead || screenOpen) && fits;
    }

    public static boolean mustExit(boolean bridgeLive, boolean dead, boolean connected, boolean inCar) {
        return inCar && (!bridgeLive || dead || !connected);
    }

    /** The wheel (-1 to 1, positive to the right) after dtSeconds: it moves toward the wanted angle, away from the middle slower than back toward it. */
    public static double steer(double current, double wanted, double dtSeconds) {
        double from = Double.isFinite(current) ? Math.max(-1, Math.min(1, current)) : 0;
        if (!Double.isFinite(wanted) || !Double.isFinite(dtSeconds)) {
            return from;
        }
        double to = Math.max(-1, Math.min(1, wanted));
        boolean away = from * to >= 0 && Math.abs(to) > Math.abs(from);
        double limit = (away ? STEER_RISE_PER_S : STEER_RETURN_PER_S) * Math.max(0, Math.min(dtSeconds, MAX_DT));
        return from + Math.max(-limit, Math.min(limit, to - from));
    }

    /** {throttle, brake} (0 to 255) from the keys. GTA reverses with the brake when the car is stopped, and accelerating brakes it when it rolls backward. */
    public static int[] throttle(boolean forwardKey, boolean backKey) {
        if (backKey) {
            return new int[] {0, 255};
        }
        return new int[] {forwardKey ? 255 : 0, 0};
    }

    /** {throttle, brake, handbrake} as sent to GTA: with the tank dry the pedals do nothing and the handbrake stops the car (the brake alone would reverse it once stopped). */
    public static int[] pedalsWithFuel(boolean dry, int throttle, int brake, int handbrake) {
        return dry ? new int[] {0, 0, 255} : new int[] {throttle, brake, handbrake};
    }

    /** The wheel as the byte of the stick (-127 to 127, positive to the right); a broken number gives 0 (the cast of NaN). */
    public static int steerByte(double steer) {
        double clamped = Math.max(-1, Math.min(1, steer));
        return (int) Math.signum(clamped) * (int) Math.round(Math.abs(clamped) * 127);
    }

    /** The angle of a wheel, in (-pi, pi], after the car has gone distanceMeters forward (negative backward). */
    public static double wheelSpin(double previousAngle, double distanceMeters, double radius) {
        if (!Double.isFinite(previousAngle) || !Double.isFinite(distanceMeters) || !Double.isFinite(radius) || radius <= 0) {
            return Double.isFinite(previousAngle) ? previousAngle : 0;
        }
        double angle = (previousAngle + distanceMeters / radius) % (2 * Math.PI);
        if (angle <= -Math.PI) {
            angle += 2 * Math.PI;
        } else if (angle > Math.PI) {
            angle -= 2 * Math.PI;
        }
        return angle;
    }

    /** How far the front wheels are turned, in radians, positive to the right, from GTA's steer angle (negative to the right). */
    public static double frontWheelAngle(double gtaSteerRadians) {
        if (!Double.isFinite(gtaSteerRadians)) {
            return 0;
        }
        return Math.max(-MAX_WHEEL_ANGLE, Math.min(MAX_WHEEL_ANGLE, -gtaSteerRadians));
    }

    /**
     * The number of the first car command of a Minecraft that starts at `epochMs`. The bridge (which lives as long as GTA) drops a command not ahead of the last it
     * took, so a new Minecraft must start ahead of the old one: the clock in hundredths of a second does it as long as a session sends fewer commands than that
     * (they are a few per minute). A random start, as before, was behind half of the time, and the car could not be had until GTA restarted.
     */
    public static int firstCommandSeq(long epochMs) {
        return (int) (epochMs / 10L);
    }
}
