package dev.cjmc.combat;

/** Small decisions about a rocket that do not need Minecraft, so they can be tested. */
public final class RocketRules {
    private RocketRules() {}

    /** A rocket whose shooter is gone (disconnected, dead, in another dimension) vanishes without exploding. */
    public static boolean ownerGone(boolean ownerIsPlayer, boolean ownerAlive, boolean ownerInThisWorld) {
        return !(ownerIsPlayer && ownerAlive && ownerInThisWorld);
    }
}
