package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SneakRulesTest {
    @Test
    void aWalkingPlayerWhoStrikesWithGtaAliveIsMadeToSneak() {
        assertTrue(SneakRules.shouldForce(true, true, false, false, false, false, false));
    }

    @Test
    void noBlowNoForcedSneak() {
        assertFalse(SneakRules.shouldForce(false, true, false, false, false, false, false));
    }

    @Test
    void withoutGtaAliveTheSneakIsNotForced() {
        assertFalse(SneakRules.shouldForce(true, false, false, false, false, false, false));
    }

    @Test
    void theSneakKeyStaysFreeWhereItDoesSomethingElse() {
        // sneaking dismounts, flies down, sinks and goes down a ladder; in spectator mode it flies down too
        assertFalse(SneakRules.shouldForce(true, true, true, false, false, false, false), "flying");
        assertFalse(SneakRules.shouldForce(true, true, false, true, false, false, false), "riding");
        assertFalse(SneakRules.shouldForce(true, true, false, false, true, false, false), "in a fluid");
        assertFalse(SneakRules.shouldForce(true, true, false, false, false, true, false), "climbing");
        assertFalse(SneakRules.shouldForce(true, true, false, false, false, false, true), "spectator");
    }
}
