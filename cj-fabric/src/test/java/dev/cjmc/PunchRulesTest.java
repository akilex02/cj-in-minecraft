package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PunchRulesTest {
    @Test
    void anEmptyHandOrAnItemThatDoesNotHitPunches() {
        assertTrue(PunchRules.punches(false, false)); // empty hand, blocks, food
    }

    @Test
    void anItemThatHitsOnItsOwnKeepsItsVanillaBlow() {
        assertFalse(PunchRules.punches(false, true)); // swords, axes, pickaxes, shovels, hoes, tridents
    }

    @Test
    void aWeaponOfTheModIsNeverAPunch() {
        assertFalse(PunchRules.punches(true, false));
        assertFalse(PunchRules.punches(true, true));
    }
}
