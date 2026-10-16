package dev.cjmc.vehicle;

import dev.cjmc.Protocol;

/**
 * Riding in a car of GTA, as far as Minecraft is concerned: asking the bridge for the car, the controls it is sent every tick, and where the car is in the
 * world of Minecraft (GTA moves it; the blocks stop it; what it was stopped by goes back to GTA as a correction). Pure: the adapter (VehicleClient) reads the keys and the world,
 * sends what a tick asks to send and moves the player to the pose.
 */
public final class VehicleSession {
    public enum Mode { IDLE, ENTERING, DRIVING, LEAVING }

    public static final int NONE = 0;
    public static final int ENTER = 1;
    public static final int EXIT = 2;
    public static final short MODEL = 492;            // the Greenwood
    public static final long ENTER_RETRY_MS = 250;    // the request is made again this often until the bridge answers
    public static final long ENTER_TIMEOUT_MS = 2000; // and given up after this
    public static final long LEAVE_MS = 150;          // the request to get out is made on every tick this long (a datagram may be lost)
    public static final long STATE_TIMEOUT_MS = 1000; // without a state of the car for this long the ride is over
    public static final double TICK_SECONDS = 0.05;
    public static final double WHEEL_RADIUS = 0.35;   // metres (measured on the model)
    public static final double ORIGIN_ABOVE_GROUND = 0.70; // the origin of the model is the centre of the car: the wheels' bottoms are this far below
    public static final double MAX_JUMP = 30.0;       // blocks in one tick: more than this is GTA moving the car by itself, and the frame is set again where it is

    /** What decides whether he may get in, and whether he must get out. */
    public record Conditions(boolean bridgeLive, boolean riding, boolean inWater, boolean jetpackFlying, boolean climbing, boolean knockedDown, boolean dead,
                             boolean screenOpen, boolean connected) {}

    public record Keys(boolean enter, boolean forward, boolean back, boolean left, boolean right, boolean handbrake) {}

    /** The place of the car in the world of Minecraft: x, z the centre, y the underside (the ground under the wheels); yaw in Minecraft degrees; pitch nose up, roll right side up (radians). */
    public record Pose(double x, double y, double z, double yaw, double pitch, double roll, double wheelAngle, double frontWheelAngle, double seatRight,
                       double seatForward, double seatUp, double seatHeading) {}

    /** What the tick asks the adapter to send: a command (NONE, ENTER, EXIT) and the controls of the car. */
    public record Output(int command, float yaw, boolean input, int throttle, int brake, int steer, int handbrake, boolean correction, double corrX, double corrY,
                         boolean enterFailed) {
        static Output none() {
            return new Output(NONE, 0f, false, 0, 0, 0, 0, false, 0, 0, false);
        }
    }

    /** The pose a fraction t (0 to 1) of the way from a to b: the angles go the short way round; CJ's seat is the latest one (b's). */
    public static Pose blend(Pose a, Pose b, double t) {
        return new Pose(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t,
                angle(a.yaw(), b.yaw(), t, 360.0), a.pitch() + (b.pitch() - a.pitch()) * t, a.roll() + (b.roll() - a.roll()) * t,
                angle(a.wheelAngle(), b.wheelAngle(), t, 2 * Math.PI), a.frontWheelAngle() + (b.frontWheelAngle() - a.frontWheelAngle()) * t,
                b.seatRight(), b.seatForward(), b.seatUp(), b.seatHeading());
    }

    private static double angle(double from, double to, double t, double turn) {
        double half = turn / 2;
        double difference = ((to - from + turn + half) % turn + turn) % turn - half; // the short way, in [-half, half)
        double blended = from + difference * t; // from is in the range and the step is at most half a turn: one turn back is enough
        return blended <= -half ? blended + turn : blended > half ? blended - turn : blended;
    }

    private Mode mode = Mode.IDLE;
    private long enterStartMs;
    private long lastEnterSendMs;
    private long leaveStartMs;
    private VehicleFrame frame;
    private Pose pose = new Pose(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    private double fitYaw; // the last direction the box of the car fitted in: the box keeps it while GTA's heading would put it inside a wall
    private Pose previous = pose;
    private final VehicleTerrain.Smoother smoother = new VehicleTerrain.Smoother();
    private double steer;
    private double vy;
    private long usedStateMs; // when the last state that moved the car arrived: a state is new when it arrived at another time
    private boolean pendingCorrection;
    private double corrX;
    private double corrY;

    public Mode mode() {
        return mode;
    }

    /** The car is there to be seen: the player is at its wheel (or has just asked to get out). */
    public boolean riding() {
        return mode == Mode.DRIVING || mode == Mode.LEAVING;
    }

    public Pose pose() {
        return pose;
    }

    public Pose previousPose() {
        return previous;
    }

    /** Gives the ride up where it is; true when the bridge has to be told (there may be a car to delete). */
    public boolean cancel() {
        boolean tell = mode != Mode.IDLE;
        mode = Mode.IDLE;
        return tell;
    }

    /**
     * One client tick. The player's place and yaw are where he stands (used when he gets in); state is the last state of the car the bridge sent and
     * receivedMs when it arrived (state null: none yet); the clock is milliseconds.
     */
    public Output tick(long nowMs, Conditions conditions, Keys keys, double px, double py, double pz, double yaw, Terrain world,
                       Protocol.VehicleState state, long receivedMs) {
        switch (mode) {
            case IDLE:
                return idle(nowMs, conditions, keys, px, py, pz, yaw, world);
            case ENTERING:
                return entering(nowMs, px, py, pz, yaw, state, receivedMs);
            case DRIVING:
                return driving(nowMs, conditions, keys, world, state, receivedMs);
            default:
                return leaving(nowMs);
        }
    }

    private Output idle(long nowMs, Conditions c, Keys keys, double px, double py, double pz, double yaw, Terrain world) {
        if (!keys.enter() || !VehicleRules.canEnter(c.bridgeLive(), c.riding(), c.inWater(), c.jetpackFlying(), c.climbing(), c.knockedDown(), c.dead(),
                c.screenOpen(), VehicleCollision.fits(world, px, py, pz, yaw))) {
            return Output.none();
        }
        mode = Mode.ENTERING;
        enterStartMs = nowMs;
        lastEnterSendMs = nowMs;
        pose = new Pose(px, py, pz, yaw, 0, 0, 0, 0, 0, 0, 0, 0);
        fitYaw = yaw;
        return new Output(ENTER, (float) yaw, false, 0, 0, 0, 0, false, 0, 0, false);
    }

    private Output entering(long nowMs, double px, double py, double pz, double yaw, Protocol.VehicleState state, long receivedMs) {
        if (state != null && (state.flags() & Protocol.VEHICLE_FLAG_DRIVER_IN) != 0 && receivedMs >= enterStartMs) {
            // the car is there: it stands where the player stood, facing the way he faced, and from now on GTA moves it
            frame = VehicleFrame.anchor(pose.x(), pose.z(), pose.yaw(), state.px(), state.py(), state.heading());
            mode = Mode.DRIVING;
            steer = 0;
            vy = 0;
            smoother.reset();
            pendingCorrection = false;
            return Output.none();
        }
        if (nowMs - enterStartMs > ENTER_TIMEOUT_MS) {
            mode = Mode.IDLE;
            return new Output(EXIT, 0f, false, 0, 0, 0, 0, false, 0, 0, true); // it never came: if it comes late it must not stay
        }
        if (nowMs - lastEnterSendMs >= ENTER_RETRY_MS) {
            lastEnterSendMs = nowMs;
            return new Output(ENTER, (float) pose.yaw(), false, 0, 0, 0, 0, false, 0, 0, false);
        }
        return Output.none();
    }

    private Output driving(long nowMs, Conditions c, Keys keys, Terrain world, Protocol.VehicleState state, long receivedMs) {
        boolean fresh = receivedMs != usedStateMs; // a missing state has already ended the ride below
        boolean answering = c.bridgeLive() && state != null && nowMs - receivedMs <= STATE_TIMEOUT_MS;
        if (keys.enter() || VehicleRules.mustExit(answering, c.dead(), c.connected(), true)) {
            mode = Mode.LEAVING;
            leaveStartMs = nowMs;
            return new Output(EXIT, 0f, false, 0, 0, 0, 0, false, 0, 0, false);
        }
        previous = pose;
        steer = VehicleRules.steer(steer, keys.right() == keys.left() ? 0 : keys.right() ? 1 : -1, TICK_SECONDS);
        if (fresh) {
            usedStateMs = receivedMs;
            moveTo(state, world);
        }
        int[] pedals = VehicleRules.throttle(keys.forward(), keys.back());
        boolean correct = pendingCorrection;
        Output out = new Output(NONE, 0f, true, pedals[0], pedals[1], VehicleRules.steerByte(steer), keys.handbrake() ? 255 : 0, correct, correct ? corrX : 0,
                correct ? corrY : 0, false);
        pendingCorrection = false;
        return out;
    }

    private void moveTo(Protocol.VehicleState s, Terrain world) {
        double[] want = frame.mcPosition(s.px(), s.py());
        double newYaw = frame.mcYawDegrees(s.heading());
        double dx = want[0] - pose.x();
        double dz = want[1] - pose.z();
        if (Math.sqrt(dx * dx + dz * dz) > MAX_JUMP) {
            frame = VehicleFrame.anchor(pose.x(), pose.z(), newYaw, s.px(), s.py(), s.heading()); // GTA moved the car by itself: start again from where it is
            want = new double[] {pose.x(), pose.z()};
        }
        if (VehicleCollision.fits(world, pose.x(), pose.y(), pose.z(), newYaw)) {
            fitYaw = newYaw;
        }
        double boxYaw = fitYaw;
        VehicleCollision.Result moved = VehicleCollision.move(world, pose.x(), pose.y(), pose.z(), boxYaw, want[0], want[1]);
        VehicleTerrain.Settle settled = VehicleTerrain.settle(world, moved.x(), moved.y(), moved.z(), boxYaw, vy);
        vy = settled.vy();
        if (moved.blocked()) {
            double[] back = frame.gtaCorrection(moved.x() - want[0], moved.z() - want[1]);
            pendingCorrection = true;
            corrX = back[0];
            corrY = back[1];
        }
        double[] tilt = VehicleTerrain.pitchRoll(world, moved.x(), settled.y(), moved.z(), boxYaw);
        smoother.update(tilt[0], tilt[1]);
        pose = new Pose(moved.x(), settled.y(), moved.z(), newYaw, smoother.pitch(), smoother.roll(),
                VehicleRules.wheelSpin(pose.wheelAngle(), s.speed() * TICK_SECONDS, WHEEL_RADIUS), VehicleRules.frontWheelAngle(s.steer()),
                s.seatRight(), s.seatForward(), s.seatUp(), s.seatHeading());
    }

    private Output leaving(long nowMs) {
        if (nowMs - leaveStartMs >= LEAVE_MS) {
            mode = Mode.IDLE;
            return Output.none();
        }
        return new Output(EXIT, 0f, false, 0, 0, 0, 0, false, 0, 0, false);
    }
}
