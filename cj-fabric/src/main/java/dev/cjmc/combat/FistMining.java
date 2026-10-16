package dev.cjmc.combat;

/** Mining with the bare hand is twice as fast (asked by the user, 2026-10-07): the rules; the server applies them as an attribute modifier. */
public final class FistMining {
    public static final double MULTIPLIER = 2.0;

    private FistMining() {}

    /** Whether the player mines faster: only with an empty main hand (any item mines as it always did). */
    public static boolean boosted(boolean emptyHand) {
        return emptyHand;
    }

    /** What the attribute modifier adds on top of the base speed (a multiplier of MULTIPLIER is +100 % of it). */
    public static double extra() {
        return MULTIPLIER - 1.0;
    }
}
