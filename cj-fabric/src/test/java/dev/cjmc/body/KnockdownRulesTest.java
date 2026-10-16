package dev.cjmc.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KnockdownRulesTest {
    @Test
    void aHardFallKnocksDownASoftOneDoesNot() {
        assertTrue(KnockdownRules.causes(true, false, 3.0f));
        assertTrue(KnockdownRules.causes(true, false, 10.0f));
        assertFalse(KnockdownRules.causes(true, false, 2.9f));
    }

    @Test
    void anOrdinaryBlowOfThatSizeIsNotAFall() {
        assertFalse(KnockdownRules.causes(false, false, 3.0f));
        assertFalse(KnockdownRules.causes(false, false, 7.9f));
    }

    @Test
    void anExplosionAlwaysKnocksDownIfItHurts() {
        assertTrue(KnockdownRules.causes(false, true, 0.5f));
        assertTrue(KnockdownRules.causes(false, true, 20f));
        assertFalse(KnockdownRules.causes(false, true, 0f));
    }

    @Test
    void aHeavyBlowKnocksDown() {
        assertTrue(KnockdownRules.causes(false, false, 8.0f));
        assertTrue(KnockdownRules.causes(false, false, 20f));
        assertFalse(KnockdownRules.causes(false, false, 7.9f));
    }

    @Test
    void noDamageNeverKnocksDown() {
        assertFalse(KnockdownRules.causes(true, true, 0f));
        assertFalse(KnockdownRules.causes(true, true, -5f));
        assertFalse(KnockdownRules.causes(true, true, Float.NaN));
    }

    @Test
    void theKindTellsAnExplosionFromAnyOtherKnockdown() {
        assertEquals(KnockdownRules.BLAST, KnockdownRules.kind(false, true, 3f));    // an explosion: on his back
        assertEquals(KnockdownRules.BLAST, KnockdownRules.kind(true, true, 12f));    // an explosion that is also a fall damage: still an explosion
        assertEquals(KnockdownRules.NORMAL, KnockdownRules.kind(true, false, 3f));   // a hard fall: face down
        assertEquals(KnockdownRules.NORMAL, KnockdownRules.kind(false, false, 8f));  // a heavy blow: face down
        assertEquals(KnockdownRules.NONE, KnockdownRules.kind(false, false, 7.9f));
        assertEquals(KnockdownRules.NONE, KnockdownRules.kind(true, false, 2.9f));
        assertEquals(KnockdownRules.NONE, KnockdownRules.kind(false, true, 0f));     // an explosion that did no harm
        assertEquals(KnockdownRules.NONE, KnockdownRules.kind(true, true, Float.NaN));
        assertTrue(KnockdownRules.NONE < KnockdownRules.NORMAL && KnockdownRules.NORMAL < KnockdownRules.BLAST);
    }

    @Test
    void ridingSwimmingClimbingFlyingOrGlidingAreNeverKnockedDown() {
        // eligible(riding, inFluid, climbing, flying, gliding)
        assertTrue(KnockdownRules.eligible(false, false, false, false, false));
        assertFalse(KnockdownRules.eligible(true, false, false, false, false), "riding");
        assertFalse(KnockdownRules.eligible(false, true, false, false, false), "in a fluid");
        assertFalse(KnockdownRules.eligible(false, false, true, false, false), "climbing");
        assertFalse(KnockdownRules.eligible(false, false, false, true, false), "flying");
        assertFalse(KnockdownRules.eligible(false, false, false, false, true), "gliding");
    }
}
