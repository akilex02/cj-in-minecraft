package dev.cjmc.body;

/** What knocks the player down: a hard fall, an explosion or a heavy blow. */
public final class KnockdownRules {
    public static final float FALL_DAMAGE = 3f;
    public static final float STRONG_HIT = 8f;

    private KnockdownRules() {}

    public static final int NONE = 0;    // no knockdown
    public static final int NORMAL = 1;  // a hard fall or a heavy blow: he falls face down
    public static final int BLAST = 2;   // an explosion: he is thrown on his back

    /** The kind of knockdown this damage causes (an explosion wins over the rest). */
    public static int kind(boolean fall, boolean explosion, float damage) {
        if (!(damage > 0f)) {
            return NONE;
        }
        if (explosion) {
            return BLAST;
        }
        return (fall && damage >= FALL_DAMAGE) || damage >= STRONG_HIT ? NORMAL : NONE;
    }

    /** Whether the player can be knocked down at all: not riding, in a fluid, on a ladder, flying or gliding. */
    public static boolean eligible(boolean riding, boolean inFluid, boolean climbing, boolean flying, boolean gliding) {
        return !(riding || inFluid || climbing || flying || gliding);
    }

    public static boolean causes(boolean fall, boolean explosion, float damage) {
        return kind(fall, explosion, damage) != NONE;
    }
}
