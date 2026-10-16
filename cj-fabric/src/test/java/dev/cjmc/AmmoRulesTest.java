package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AmmoRulesTest {
    @Test
    void aWeaponWithoutAmmoAlwaysFires() {
        assertTrue(AmmoRules.canFire("", 0, false));
        assertEquals(0, AmmoRules.cost("", false));
    }

    @Test
    void survivalNeedsABullet() {
        assertFalse(AmmoRules.canFire(AmmoRules.LIGHT, 0, false));
        assertTrue(AmmoRules.canFire(AmmoRules.LIGHT, 1, false));
        assertEquals(1, AmmoRules.cost(AmmoRules.HEAVY, false));
    }

    @Test
    void creativeFiresForFreeAndSpendsNothing() {
        assertTrue(AmmoRules.canFire(AmmoRules.ROCKET, 0, true));
        assertEquals(0, AmmoRules.cost(AmmoRules.ROCKET, true));
    }

    @Test
    void knownIdsAreTheThreeAmmosAndTheEmptyString() {
        assertTrue(AmmoRules.isKnown(""));
        assertTrue(AmmoRules.isKnown("ammo1") && AmmoRules.isKnown("ammo2") && AmmoRules.isKnown("ammo3"));
        assertFalse(AmmoRules.isKnown("ammo4"));
        assertFalse(AmmoRules.usesAmmo(""));
        assertTrue(AmmoRules.usesAmmo("ammo2"));
    }

    @Test
    void theDryFireIntentIsSentWhileTheButtonIsHeldOnAnEmptyWeapon() {
        assertTrue(AmmoRules.dryFire(true, AmmoRules.LIGHT, 0, false));
        assertFalse(AmmoRules.dryFire(false, AmmoRules.LIGHT, 0, false)); // not pressed
        assertFalse(AmmoRules.dryFire(true, AmmoRules.LIGHT, 5, false)); // has bullets
        assertFalse(AmmoRules.dryFire(true, AmmoRules.LIGHT, 0, true)); // creative
        assertFalse(AmmoRules.dryFire(true, "", 0, false)); // melee or fists
    }
}
