package dev.cjmc;

/**
 * Whether the jump key is taken away from the player. Only a plain jump from solid ground is: the key also opens the elytra (in the
 * air), starts and rides creative flight, makes a mount jump, swims up and climbs, and none of that is what GTA forbids.
 */
public final class JumpBlock {
    private JumpBlock() {}

    public static boolean applies(boolean canJump, boolean onGround, boolean flying, boolean riding, boolean inFluid, boolean climbing) {
        return !canJump && onGround && !flying && !riding && !inFluid && !climbing;
    }
}
