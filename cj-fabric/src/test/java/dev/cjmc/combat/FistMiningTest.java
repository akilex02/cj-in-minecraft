package dev.cjmc.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FistMiningTest {
    @Test
    void theFistsInTheHandMineTwiceAsFast() {
        assertEquals(2.0, FistMining.MULTIPLIER);
        assertEquals(1.0, FistMining.extra(), 1e-9);          // the attribute adds 100 % on top of the base speed
        assertTrue(FistMining.boosted(true));
    }

    @Test
    void anyOtherItemOrNothingInTheHandLeavesTheSpeedAlone() {
        assertFalse(FistMining.boosted(false));
    }
}
