package dev.cjmc;

/**
 * Whether the State announces a jump now: the jump key is down as CJ in third person and Minecraft is going to jump (on the ground, the
 * weapon allows it, not knocked down, not flying, riding, swimming or climbing). CJ then starts his jump at once while the Minecraft jump
 * is delayed (JumpDelay).
 */
public final class JumpIntent {
    private JumpIntent() {}

    public static boolean wanted(boolean rawPressed, boolean cjActive, boolean onGround, boolean canJump, boolean frozen,
                                 boolean flying, boolean riding, boolean inFluid, boolean climbing) {
        return rawPressed && cjActive && onGround && canJump && !frozen && !flying && !riding && !inFluid && !climbing;
    }
}
