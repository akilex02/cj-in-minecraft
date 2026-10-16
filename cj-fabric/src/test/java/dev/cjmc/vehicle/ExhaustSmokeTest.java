package dev.cjmc.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExhaustSmokeTest {
    @Test
    void theTailpipeIsAtTheBackRightAndLowOfACarFacingSouth() {
        // yaw 0 faces +z; the car's right hand is -x
        double[] p = ExhaustSmoke.position(10, 64, 20, 0);
        assertEquals(10 + ExhaustSmoke.RIGHT * -1, p[0], 1e-9);  // to the car's right: -x
        assertEquals(64 + ExhaustSmoke.HEIGHT, p[1], 1e-9);
        assertEquals(20 - ExhaustSmoke.BACK, p[2], 1e-9);         // behind: -z
    }

    @Test
    void thePipeTurnsWithTheCar() {
        // facing west (yaw 90, -x): behind is +x and the car's right hand is -z
        double[] p = ExhaustSmoke.position(0, 0, 0, 90);
        assertEquals(ExhaustSmoke.BACK, p[0], 1e-9);
        assertEquals(-ExhaustSmoke.RIGHT, p[2], 1e-9);
        assertTrue(Math.hypot(p[0], p[2]) > 2.0);
    }

    @Test
    void theSmokeDriftsBackAndUpAndMoreSoFasterButNeverAwayFromTheCar() {
        double[] still = ExhaustSmoke.velocity(0, 0);
        assertTrue(still[1] > 0, "up");
        double[] quick = ExhaustSmoke.velocity(0, 20);
        assertTrue(quick[2] < 0, "behind a car facing +z is -z");
        assertTrue(Math.abs(quick[2]) > Math.abs(still[2]));
    }

    @Test
    void aLightPuffIsMadeMostTicksAndNoneWhenTheSpeedIsBroken() {
        assertTrue(ExhaustSmoke.chance(0) > 0 && ExhaustSmoke.chance(0) < 1);
        assertEquals(1.0, ExhaustSmoke.chance(10), 1e-9);
        assertEquals(1.0, ExhaustSmoke.chance(-10), 1e-9); // in reverse too
        assertEquals(0.0, ExhaustSmoke.chance(Double.NaN));
        assertEquals(0.0, ExhaustSmoke.chance(Double.POSITIVE_INFINITY));
    }
}
