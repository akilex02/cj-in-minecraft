package dev.cjmc.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RocketFlightTest {
    @Test
    void movesSpeedOverTwentyPerTickUntilTheRangeIsUsedUp() {
        RocketFlight f = new RocketFlight(40.0, 120.0); // 2 blocks per tick, 60 ticks
        double total = 0;
        int ticks = 0;
        while (!f.expired()) {
            double d = f.advance();
            assertEquals(2.0, d, 1e-9);
            total += d;
            ticks++;
            assertTrue(ticks <= 60);
        }
        assertEquals(60, ticks);
        assertEquals(120.0, total, 1e-9);
    }

    @Test
    void theLastStepIsTrimmedSoItNeverGoesPastTheRange() {
        RocketFlight f = new RocketFlight(40.0, 5.0); // 2, 2 and then 1
        assertEquals(2.0, f.advance(), 1e-9);
        assertFalse(f.expired());
        assertEquals(2.0, f.advance(), 1e-9);
        assertFalse(f.expired());
        assertEquals(1.0, f.advance(), 1e-9);
        assertTrue(f.expired());
    }

    @Test
    void anExpiredFlightDoesNotMoveAnyMore() {
        RocketFlight f = new RocketFlight(20.0, 1.0);
        assertEquals(1.0, f.advance(), 1e-9);
        assertTrue(f.expired());
        assertEquals(0.0, f.advance());
        assertTrue(f.expired());
    }

    @Test
    void unusableNumbersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RocketFlight(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new RocketFlight(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> new RocketFlight(Double.NaN, 10));
        assertThrows(IllegalArgumentException.class, () -> new RocketFlight(Double.POSITIVE_INFINITY, 10));
        assertThrows(IllegalArgumentException.class, () -> new RocketFlight(40, 0));
        assertThrows(IllegalArgumentException.class, () -> new RocketFlight(40, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new RocketFlight(40, -3));
        assertThrows(IllegalArgumentException.class, () -> new RocketFlight(40, Double.POSITIVE_INFINITY)); // it would never explode
    }
}
