package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrouchRulesTest {
    /** A crouched player with nothing that stands him up. */
    private static boolean stays(boolean jump, boolean sprint, boolean flying, boolean jetpack, boolean driving, boolean inFluid, boolean climbing,
                                 boolean knocked, boolean dead, boolean bridgeLive, boolean standsOnFire) {
        return CrouchRules.stays(jump, sprint, flying, jetpack, driving, inFluid, climbing, knocked, dead, bridgeLive, standsOnFire);
    }

    @Test
    void crouchingIsSlownessThreeAndHalvesTheSpread() {
        assertEquals(0.55f, CrouchRules.SPEED, 1e-6f); // Slowness III: 3 levels of -15 %
        assertEquals(0.5, CrouchRules.spreadFactor(true), 1e-9);
        assertEquals(1.0, CrouchRules.spreadFactor(false), 1e-9);
    }

    @Test
    void nothingStandsHimUpWhileHeWalksAroundCrouched() {
        assertTrue(stays(false, false, false, false, false, false, false, false, false, true, false));
    }

    @Test
    void eachOfTheseStandsHimUp() { // as in GTA: jumping, sprinting, and whatever takes the body over
        assertFalse(stays(true, false, false, false, false, false, false, false, false, true, false));  // jump
        assertFalse(stays(false, true, false, false, false, false, false, false, false, true, false));  // sprint
        assertFalse(stays(false, false, true, false, false, false, false, false, false, true, false));  // creative flight
        assertFalse(stays(false, false, false, true, false, false, false, false, false, true, false));  // jetpack
        assertFalse(stays(false, false, false, false, true, false, false, false, false, true, false));  // the car
        assertFalse(stays(false, false, false, false, false, true, false, false, false, true, false));  // swimming
        assertFalse(stays(false, false, false, false, false, false, true, false, false, true, false));  // climbing
        assertFalse(stays(false, false, false, false, false, false, false, true, false, true, false));  // knocked down
        assertFalse(stays(false, false, false, false, false, false, false, false, true, true, false));  // dead
        assertFalse(stays(false, false, false, false, false, false, false, false, false, false, false)); // GTA is not answering
        assertFalse(stays(false, false, false, false, false, false, false, false, false, true, true));  // firing a weapon that does not fire crouched
    }

    @Test
    void theInputIsScaledToTheSlownessOverWhatTheSneakAlreadyTook() {
        // the sneak scales the input by its own factor (0.3) once the pose is the sneaking one: the rest brings it up to the wanted share of the speed
        assertEquals(0.55f / 0.3f, CrouchRules.inputScale(true, 0.3f, 0.55f), 1e-6f);
        assertEquals(0.55f, CrouchRules.inputScale(false, 0.3f, 0.55f), 1e-6f); // the first tick, before the pose changes
        assertEquals(0f, CrouchRules.inputScale(true, 0.3f, 0f), 1e-6f);         // standing still while he fires
    }
}
