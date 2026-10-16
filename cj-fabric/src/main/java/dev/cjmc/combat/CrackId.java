package dev.cjmc.combat;

/**
 * The id a block's cracks are shown under. The client keeps one crack per id, so each block needs its own id; it is negative so it
 * never equals an entity id (the game skips the player whose entity id matches).
 */
final class CrackId {
    private CrackId() {}

    static int of(long packedPos) {
        long h = packedPos * 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        return -1 - (int) (h & 0x7FFFFFFF);
    }
}
