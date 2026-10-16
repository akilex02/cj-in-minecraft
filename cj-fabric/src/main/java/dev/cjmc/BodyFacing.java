package dev.cjmc;

/**
 * Turns CJ's body towards the direction he moves, like GTA does. When he has stood still for a moment it turns
 * towards where the player looks, so that the camera (which follows the look direction) sees his back.
 */
public final class BodyFacing {
    public static final float MOVE_SPEED = 0.3f;   // blocks per second of horizontal speed that counts as moving
    public static final float TURN_RATE = 720f;    // degrees per second
    public static final long IDLE_TURN_DELAY_NS = 300_000_000L; // standing still this long makes him face the look direction

    private float yaw;
    private boolean started;
    private long idleNs;

    /**
     * @param vx x velocity in blocks/s (east is +x)
     * @param vz z velocity in blocks/s (south is +z)
     * @param lookYaw the player's own yaw: the first frame's facing, and where he turns to once he has stood still
     * @return the body yaw in degrees, Minecraft convention (0 = south, -90 = east, 90 = west, 180 = north)
     */
    public float update(float vx, float vz, float lookYaw, long dtNs) {
        return update(vx, vz, lookYaw, dtNs, false);
    }

    /** @param aiming while true the body faces the look direction at once (GTA turns CJ towards his target), moving or not */
    public float update(float vx, float vz, float lookYaw, long dtNs, boolean aiming) {
        if (!started) {
            started = true;
            yaw = lookYaw;
            return yaw;
        }
        if (dtNs <= 0) {
            return yaw;
        }
        float target;
        if (aiming) {
            idleNs = 0;
            target = lookYaw;
        } else if (Math.hypot(vx, vz) >= MOVE_SPEED) {
            idleNs = 0;
            target = (float) Math.toDegrees(Math.atan2(-vx, vz));
        } else {
            idleNs = Math.min(idleNs + dtNs, 10 * IDLE_TURN_DELAY_NS);
            if (idleNs < IDLE_TURN_DELAY_NS) {
                return yaw;
            }
            target = lookYaw;
        }
        float diff = wrap(target - yaw);
        float maxStep = TURN_RATE * dtNs / 1e9f;
        yaw = wrap(yaw + Math.max(-maxStep, Math.min(maxStep, diff)));
        return yaw;
    }

    static float wrap(float degrees) {
        degrees %= 360f;
        if (degrees >= 180f) {
            degrees -= 360f;
        }
        if (degrees < -180f) {
            degrees += 360f;
        }
        return degrees;
    }
}
