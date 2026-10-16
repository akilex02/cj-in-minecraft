package dev.cjmc.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RocketRulesTest {
    @Test
    void aRocketOfALivingShooterInTheSameWorldFliesOn() {
        assertFalse(RocketRules.ownerGone(true, true, true));
    }

    @Test
    void aRocketVanishesWhenItsShooterIsNotAPlayerAnyMoreIsDeadOrIsInAnotherWorld() {
        assertTrue(RocketRules.ownerGone(false, true, true), "no player (disconnected, never set)");
        assertTrue(RocketRules.ownerGone(true, false, true), "dead");
        assertTrue(RocketRules.ownerGone(true, true, false), "another dimension");
        assertTrue(RocketRules.ownerGone(false, false, false));
    }
}
