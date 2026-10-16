package dev.cjmc.body;

/**
 * Counts the times the player's health went down (0 to 15, wrapping), which the bridge reads as "he was hurt". The blow that kills,
 * being dead and coming back to life are not hurts: death has its own reaction.
 */
public final class HurtCounter {
    private float last = Float.NaN;
    private int count;

    public int update(float health, boolean alive) {
        if (alive && health < last) { // the first reading has no last one (NaN compares false); a dead player is never hurt; respawning raises the health
            count = (count + 1) & 0xF;
        }
        last = health;
        return count;
    }
}
