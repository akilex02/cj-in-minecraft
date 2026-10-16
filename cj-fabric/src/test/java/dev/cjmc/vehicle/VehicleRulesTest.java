package dev.cjmc.vehicle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VehicleRulesTest {
    private static boolean enter(boolean live, boolean riding, boolean water, boolean jetpack, boolean climbing, boolean knocked, boolean dead,
                                 boolean screen, boolean fits) {
        return VehicleRules.canEnter(live, riding, water, jetpack, climbing, knocked, dead, screen, fits);
    }

    @Test
    void youCanGetInOnlyWhenEverythingIsInOrder() {
        assertTrue(enter(true, false, false, false, false, false, false, false, true));
        assertFalse(enter(false, false, false, false, false, false, false, false, true), "GTA is not answering");
        assertFalse(enter(true, true, false, false, false, false, false, false, true), "riding");
        assertFalse(enter(true, false, true, false, false, false, false, false, true), "in the water");
        assertFalse(enter(true, false, false, true, false, false, false, false, true), "flying with the jetpack");
        assertFalse(enter(true, false, false, false, true, false, false, false, true), "climbing");
        assertFalse(enter(true, false, false, false, false, true, false, false, true), "knocked down");
        assertFalse(enter(true, false, false, false, false, false, true, false, true), "dead");
        assertFalse(enter(true, false, false, false, false, false, false, true, true), "a screen is open");
        assertFalse(enter(true, false, false, false, false, false, false, false, false), "the car does not fit");
    }

    @Test
    void whenTheCarMustBeLeft() {
        assertTrue(VehicleRules.mustExit(false, false, true, true), "GTA stopped answering");
        assertTrue(VehicleRules.mustExit(true, true, true, true), "the player died");
        assertTrue(VehicleRules.mustExit(true, false, false, true), "disconnected");
        assertFalse(VehicleRules.mustExit(true, false, true, true));
        // when not in the car there is nothing to force
        assertFalse(VehicleRules.mustExit(false, true, false, false));
    }

    @Test
    void theWheelTurnsToTheWantedAngleAtTheRiseRate() {
        assertEquals(0.25, VehicleRules.steer(0, 1, 0.0625), 1e-9);   // 4 per second
        assertEquals(1, VehicleRules.steer(0.9, 1, 0.1), 1e-9);       // it stops at the wanted angle
        assertEquals(-0.25, VehicleRules.steer(0, -1, 0.0625), 1e-9);
        double lock = 0;
        for (int i = 0; i < 5; i++) {
            lock = VehicleRules.steer(lock, 1, 0.05);          // full lock in a quarter of a second
        }
        assertEquals(1, lock, 1e-9);
    }

    @Test
    void theWheelComesBackFasterThanItGoes() {
        assertEquals(1 - 6.67 * 0.075, VehicleRules.steer(1, 0, 0.075), 1e-9);  // 6.67 per second: about half way back in 0.075 s
        assertEquals(0, VehicleRules.steer(0.1, 0, 0.1), 1e-9);       // and it stops at the middle
        assertEquals(-1 + 6.67 * 0.075, VehicleRules.steer(-1, 0, 0.075), 1e-9);
        assertEquals(0.5, VehicleRules.steer(1, 0.5, 0.1), 1e-9);     // easing the key (a smaller wanted angle in the same side) is a return
    }

    @Test
    void crossingFromOneSideToTheOtherPassesTheMiddle() {
        double a = VehicleRules.steer(1, -1, 0.075);
        assertEquals(1 - 6.67 * 0.075, a, 1e-9);
        double b = VehicleRules.steer(a, -1, 0.075);
        assertEquals(1 - 2 * 6.67 * 0.075, b, 1e-9); // through the middle without stopping at it
        double c = VehicleRules.steer(b, -1, 0.075);
        assertTrue(c < b && c > -1);
    }

    @Test
    void theWantedAngleIsClampedAndBrokenNumbersDoNothing() {
        assertEquals(0.4, VehicleRules.steer(0, 5, 0.1), 1e-9);    // the wanted angle is clamped to 1, not used as it is
        assertEquals(1, VehicleRules.steer(0.9, 5, 0.1), 1e-9);
        assertEquals(-1, VehicleRules.steer(-0.9, -5, 0.1), 1e-9);
        assertEquals(0.4, VehicleRules.steer(0, 1, 0.1), 1e-9);   // a huge step is capped to 0.1 s, so it does not jump
        assertEquals(0.4, VehicleRules.steer(0, 1, 100), 1e-9);
        assertEquals(0.3, VehicleRules.steer(0.3, 1, 0), 1e-9);
        assertEquals(0.3, VehicleRules.steer(0.3, 1, -1), 1e-9);
        assertEquals(0.3, VehicleRules.steer(0.3, 1, Double.NaN), 1e-9);
        assertEquals(0.3, VehicleRules.steer(0.3, Double.NaN, 0.1), 1e-9);
        assertEquals(0.4, VehicleRules.steer(Double.NaN, 1, 0.1), 1e-9); // a broken current angle counts as the middle
        assertEquals(1, VehicleRules.steer(7, 1, 0.1), 1e-9);
    }

    @Test
    void theKeysBecomeThrottleAndBrake() {
        assertArrayEquals(new int[] {255, 0}, VehicleRules.throttle(true, false));
        assertArrayEquals(new int[] {0, 255}, VehicleRules.throttle(false, true)); // GTA reverses with the brake when the car is stopped
        assertArrayEquals(new int[] {0, 255}, VehicleRules.throttle(true, true));
        assertArrayEquals(new int[] {0, 0}, VehicleRules.throttle(false, false));
    }

    @Test
    void theWheelBecomesAByteForTheStick() {
        assertEquals(-127, VehicleRules.steerByte(-1));
        assertEquals(0, VehicleRules.steerByte(0));
        assertEquals(127, VehicleRules.steerByte(1));
        assertEquals(64, VehicleRules.steerByte(0.5));
        assertEquals(-64, VehicleRules.steerByte(-0.5));
        assertEquals(127, VehicleRules.steerByte(3));
        assertEquals(-127, VehicleRules.steerByte(-3));
        assertEquals(0, VehicleRules.steerByte(Double.NaN));
        assertEquals(1, VehicleRules.steerByte(0.0079));
        assertEquals(0, VehicleRules.steerByte(0.0039));
    }

    @Test
    void theWheelsTurnByTheDistanceOverTheirRadius() {
        double r = 0.35;
        assertEquals(0, VehicleRules.wheelSpin(0, 2 * Math.PI * r, r), 1e-9);              // a whole turn
        assertEquals(Math.PI, VehicleRules.wheelSpin(0, Math.PI * r, r), 1e-9);            // half a turn
        assertEquals(1.0, VehicleRules.wheelSpin(0, 0.35, r), 1e-9);                        // one radian
        assertEquals(-1.0, VehicleRules.wheelSpin(0, -0.35, r), 1e-9);                      // backwards, the other way
        assertEquals(1.5, VehicleRules.wheelSpin(0.5, 0.35, r), 1e-9);                      // it adds to the angle it had
        double wrapped = VehicleRules.wheelSpin(3.0, 0.35 * 0.5, r);                       // 3.5 wraps into (-pi, pi]
        assertEquals(3.5 - 2 * Math.PI, wrapped, 1e-9);
        assertTrue(VehicleRules.wheelSpin(0, 1000, r) <= Math.PI && VehicleRules.wheelSpin(0, 1000, r) > -Math.PI);
        assertTrue(VehicleRules.wheelSpin(0, -1000, r) <= Math.PI && VehicleRules.wheelSpin(0, -1000, r) > -Math.PI);
        assertEquals(0.7, VehicleRules.wheelSpin(0.7, 1, 0), 1e-9);                         // a broken radius or distance does nothing
        assertEquals(0.7, VehicleRules.wheelSpin(0.7, 1, -1), 1e-9);
        assertEquals(0.7, VehicleRules.wheelSpin(0.7, Double.NaN, r), 1e-9);
        assertEquals(0.7, VehicleRules.wheelSpin(0.7, Double.POSITIVE_INFINITY, r), 1e-9);
        assertEquals(0.7, VehicleRules.wheelSpin(0.7, 1, Double.NaN), 1e-9);
        assertEquals(0.7, VehicleRules.wheelSpin(0.7, 1, Double.POSITIVE_INFINITY), 1e-9);
        assertEquals(0, VehicleRules.wheelSpin(Double.NaN, 1, r), 1e-9);                   // a broken angle starts over from 0
        assertEquals(0, VehicleRules.wheelSpin(Double.NaN, Double.NaN, r), 1e-9);
        assertEquals(Math.PI, VehicleRules.wheelSpin(0, -Math.PI, 1), 1e-12);              // the range is (-pi, pi]: -pi is pi
        assertEquals(Math.PI, VehicleRules.wheelSpin(0, Math.PI, 1), 1e-12);
    }

    @Test
    void theFrontWheelsFollowTheWheelUpToALimit() {
        // GTA's steer angle is negative to the right; the wheel angle here is positive to the right
        assertEquals(0.32, VehicleRules.frontWheelAngle(-0.32), 1e-9);
        assertEquals(-0.32, VehicleRules.frontWheelAngle(0.32), 1e-9);
        assertEquals(0.6, VehicleRules.frontWheelAngle(-1), 1e-9);
        assertEquals(-0.6, VehicleRules.frontWheelAngle(1), 1e-9);
        assertEquals(0, VehicleRules.frontWheelAngle(Double.NaN), 1e-9);
        assertEquals(0, VehicleRules.frontWheelAngle(0), 1e-9);
    }

    @Test
    void withoutFuelTheEngineIsOffAndTheCarIsHeldStill() {
        // {throttle, brake, handbrake}: the pedals do nothing and the handbrake stops it (the brake alone would reverse it once stopped)
        assertArrayEquals(new int[] {0, 0, 255}, VehicleRules.pedalsWithFuel(true, 255, 0, 0));
        assertArrayEquals(new int[] {0, 0, 255}, VehicleRules.pedalsWithFuel(true, 0, 255, 0));
        assertArrayEquals(new int[] {0, 0, 255}, VehicleRules.pedalsWithFuel(true, 0, 0, 255));
        // with fuel nothing changes
        assertArrayEquals(new int[] {255, 0, 0}, VehicleRules.pedalsWithFuel(false, 255, 0, 0));
        assertArrayEquals(new int[] {0, 255, 255}, VehicleRules.pedalsWithFuel(false, 0, 255, 255));
    }

    @Test
    void aMinecraftStartedLaterCountsItsCarCommandsAheadOfTheLastOne() {
        // the bridge drops a command whose number is not ahead of the last it took (a repeat); a new Minecraft must start ahead of the old one's last command
        long first = 1_791_800_000_000L;
        int oldLast = VehicleRules.firstCommandSeq(first) + 5_000; // the old session sent thousands of commands
        for (long later : new long[] {first + 60_000, first + 3_600_000, first + 86_400_000L * 30}) {
            int start = VehicleRules.firstCommandSeq(later);
            assertTrue(start - oldLast > 0, "a minute, an hour or a month later the new start is ahead: " + (start - oldLast));
        }
    }
}
