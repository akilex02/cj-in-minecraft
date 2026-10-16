package dev.cjmc.body;

/** Whether the player is in free fall: in the air, and either far enough above where he started falling or falling fast. */
public final class FallRules {
    public static final double FALL_DISTANCE = 2.5; // blocks
    public static final double FALL_SPEED = -0.6;   // blocks per tick (negative is down)

    private FallRules() {}

    public static boolean falling(boolean onGround, boolean inFluid, boolean climbing, boolean flying, boolean riding,
                                  double fallDistance, double verticalSpeedPerTick, boolean gliding) {
        if (gliding || onGround || inFluid || climbing || flying || riding) {
            return false;
        }
        return fallDistance >= FALL_DISTANCE || verticalSpeedPerTick <= FALL_SPEED;
    }
}
