package dev.cjmc.body;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FallRulesTest {
    private static boolean air(double fallDistance, double speed) {
        return FallRules.falling(false, false, false, false, false, fallDistance, speed, false);
    }

    @Test
    void fallingFarEnoughIsFreeFall() {
        assertTrue(air(3.0, -0.2));
        assertTrue(air(2.5, 0.0));   // exactly the distance
        assertFalse(air(2.49, -0.3));
    }

    @Test
    void fallingFastIsFreeFallEvenFromLow() {
        assertTrue(air(0.5, -0.6));  // exactly the speed
        assertFalse(air(0.5, -0.59));
        assertTrue(air(0.0, -1.5));
    }

    @Test
    void aShortSlowDropIsNotFreeFall() {
        assertFalse(air(1.0, -0.3));
        assertFalse(air(0.0, 0.0));
        assertFalse(air(0.0, 0.4)); // going up
    }

    @Test
    void onTheGroundIsNeverFreeFall() {
        assertFalse(FallRules.falling(true, false, false, false, false, 50.0, -3.0, false));
    }

    @Test
    void waterLaddersFlightAndMountsExcludeIt() {
        assertFalse(FallRules.falling(false, true, false, false, false, 50.0, -3.0, false), "in a fluid");
        assertFalse(FallRules.falling(false, false, true, false, false, 50.0, -3.0, false), "climbing");
        assertFalse(FallRules.falling(false, false, false, true, false, 50.0, -3.0, false), "flying");
        assertFalse(FallRules.falling(false, false, false, false, true, 50.0, -3.0, false), "riding");
    }

    @Test
    void glidingWithAnElytraIsNotFreeFall() {
        assertFalse(FallRules.falling(false, false, false, false, false, 50.0, -3.0, true));
        assertTrue(FallRules.falling(false, false, false, false, false, 50.0, -3.0, false));
    }
}
