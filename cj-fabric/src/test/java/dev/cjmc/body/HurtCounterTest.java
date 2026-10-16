package dev.cjmc.body;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HurtCounterTest {
    @Test
    void theFirstReadingNeverCounts() {
        HurtCounter c = new HurtCounter();
        assertEquals(0, c.update(20f, true));
    }

    @Test
    void eachDropOfHealthCountsOnce() {
        HurtCounter c = new HurtCounter();
        c.update(20f, true);
        assertEquals(1, c.update(17f, true));
        assertEquals(1, c.update(17f, true)); // no change
        assertEquals(2, c.update(15f, true));
    }

    @Test
    void healingIsNotAHurtButTheNextDamageIs() {
        HurtCounter c = new HurtCounter();
        c.update(10f, true);
        assertEquals(0, c.update(12f, true));
        assertEquals(1, c.update(11f, true));
    }

    @Test
    void theCounterWrapsAfterFifteen() {
        HurtCounter c = new HurtCounter();
        float health = 20f;
        c.update(health, true);
        int value = 0;
        for (int i = 1; i <= 16; i++) {
            health -= 0.5f;
            value = c.update(health, true);
            assertEquals(i & 0xF, value, "hurt " + i);
        }
        assertEquals(0, value); // 16 wrapped
    }

    @Test
    void theLethalBlowTheDeadAndTheRespawnAreNotHurts() {
        HurtCounter c = new HurtCounter();
        c.update(20f, true);
        assertEquals(0, c.update(0f, false));  // the blow that killed him: death has its own reaction, no pain voice
        assertEquals(0, c.update(0f, false));  // dead: nothing more
        assertEquals(0, c.update(20f, true));  // respawned at full health
        assertEquals(1, c.update(19f, true));  // and the first real damage afterwards counts
    }

    @Test
    void aBlowThatLeavesHimAtZeroButAliveCounts() {
        HurtCounter c = new HurtCounter();
        c.update(5f, true);
        assertEquals(1, c.update(0f, true));
        assertEquals(1, c.update(0f, true));   // already at zero: not a new damage
    }
}
