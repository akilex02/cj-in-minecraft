package dev.cjmc.combat;

/** The numbers of a pellet's ray: how long it is, its random seed, and what it hits. Pure maths, no Minecraft. */
public final class PelletRay {
    /** The farthest a shot reaches, whatever a weapon's `range` says. */
    public static final double MAX_RANGE = 100.0;

    public enum Hit { MISS, BLOCK, ENTITY }

    private PelletRay() {}

    /** The ray length for a weapon range: between 1 and MAX_RANGE blocks (a broken range gives 1, not a crash). */
    public static double length(float range) {
        if (!(range >= 1f)) {
            return 1.0;
        }
        return Math.min(range, MAX_RANGE);
    }

    /** A seed for each pellet of a shot, derived from the shot's own seed (SplitMix64), so that every pellet spreads differently. */
    public static long seed(long base, int pellet) {
        long z = base + 0x9E3779B97F4A7C15L * (pellet + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * What a pellet hits, given the distance to the first block and to the first entity along its ray (infinity when there is none).
     * The nearest wins and a tie goes to the block, so nothing is hit through a wall; anything beyond `length` is a miss.
     */
    public static Hit classify(double blockDistance, double entityDistance, double length) {
        double nearest = Math.min(blockDistance, entityDistance);
        if (!(nearest <= length)) {
            return Hit.MISS; // also NaN
        }
        return entityDistance < blockDistance ? Hit.ENTITY : Hit.BLOCK;
    }
}
