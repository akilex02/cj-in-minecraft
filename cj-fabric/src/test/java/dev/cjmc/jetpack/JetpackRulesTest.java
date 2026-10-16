package dev.cjmc.jetpack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JetpackRulesTest {
    // allowed(wearing, riding, inFluid, gliding, climbingLadder, creativeFlying, knockedDown, dead, wallClimbing)

    @Test
    void allowedOnlyWhenWearingAndFree() {
        assertTrue(JetpackRules.allowed(true, false, false, false, false, false, false, false, false));
        assertFalse(JetpackRules.allowed(false, false, false, false, false, false, false, false, false), "not wearing it");
        assertFalse(JetpackRules.allowed(true, true, false, false, false, false, false, false, false), "riding");
        assertFalse(JetpackRules.allowed(true, false, true, false, false, false, false, false, false), "in a fluid");
        assertFalse(JetpackRules.allowed(true, false, false, true, false, false, false, false, false), "gliding");
        assertFalse(JetpackRules.allowed(true, false, false, false, true, false, false, false, false), "on a ladder");
        assertFalse(JetpackRules.allowed(true, false, false, false, false, true, false, false, false), "creative flying");
        assertFalse(JetpackRules.allowed(true, false, false, false, false, false, true, false, false), "knocked down");
        assertFalse(JetpackRules.allowed(true, false, false, false, false, false, false, true, false), "dead");
        assertFalse(JetpackRules.allowed(true, false, false, false, false, false, false, false, true), "climbing a wall");
    }

    @Test
    void thrustAcceleratesUpToTheMaximum() {
        assertEquals(JetpackRules.RISE_ACCEL, JetpackRules.verticalSpeed(0, true), 1e-9);
        assertEquals(JetpackRules.MAX_RISE, JetpackRules.verticalSpeed(JetpackRules.MAX_RISE - 0.01, true), 1e-9);
        assertEquals(JetpackRules.MAX_RISE, JetpackRules.verticalSpeed(JetpackRules.MAX_RISE, true), 1e-9);
        assertEquals(-0.3 + JetpackRules.RISE_ACCEL, JetpackRules.verticalSpeed(-0.3, true), 1e-9);
    }

    @Test
    void releasingLetsHimFallButNotFasterThanTheTerminalSpeed() {
        assertEquals(0.2, JetpackRules.verticalSpeed(0.2, false), 1e-9);    // the game's own gravity wears the climb off
        assertEquals(-0.1, JetpackRules.verticalSpeed(-0.1, false), 1e-9);
        assertEquals(JetpackRules.TERMINAL_FALL, JetpackRules.verticalSpeed(-0.5, false), 1e-9);
        assertEquals(JetpackRules.TERMINAL_FALL, JetpackRules.verticalSpeed(JetpackRules.TERMINAL_FALL, false), 1e-9);
    }

    @Test
    void horizontalSpeedMovesTowardTheWishWithoutOvershooting() {
        assertEquals(JetpackRules.AIR_CONTROL * JetpackRules.HORIZONTAL_SPEED, JetpackRules.horizontalSpeed(0, 1), 1e-9);
        double v = 0;
        for (int i = 0; i < 100; i++) {
            double next = JetpackRules.horizontalSpeed(v, 1);
            assertTrue(next >= v && next <= JetpackRules.HORIZONTAL_SPEED + 1e-9);
            v = next;
        }
        assertEquals(JetpackRules.HORIZONTAL_SPEED, v, 1e-6);
        double w = 0.2;
        for (int i = 0; i < 100; i++) {
            double next = JetpackRules.horizontalSpeed(w, 0);
            assertTrue(next <= w && next >= 0.0);       // decays toward zero without changing sign
            w = next;
        }
        assertEquals(0.0, w, 1e-6);
        assertEquals(-0.1 + (JetpackRules.HORIZONTAL_SPEED + 0.1) * JetpackRules.AIR_CONTROL, JetpackRules.horizontalSpeed(-0.1, 1), 1e-9);
        assertEquals(-JetpackRules.AIR_CONTROL * JetpackRules.HORIZONTAL_SPEED, JetpackRules.horizontalSpeed(0, -1), 1e-9);
    }

    @Test
    void tiltGrowsWithSpeedAndIsCapped() {
        assertEquals(0.0, JetpackRules.tiltDegrees(0), 1e-9);
        assertEquals(JetpackRules.MAX_TILT_DEGREES / 2, JetpackRules.tiltDegrees(JetpackRules.HORIZONTAL_SPEED / 2), 1e-9);
        assertEquals(JetpackRules.MAX_TILT_DEGREES, JetpackRules.tiltDegrees(JetpackRules.HORIZONTAL_SPEED), 1e-9);
        assertEquals(JetpackRules.MAX_TILT_DEGREES, JetpackRules.tiltDegrees(5), 1e-9);
        assertEquals(JetpackRules.MAX_TILT_DEGREES / 2, JetpackRules.tiltDegrees(-JetpackRules.HORIZONTAL_SPEED / 2), 1e-9);
    }

    @Test
    void onlyFallDamageIsCancelledAndOnlyWearing() {
        assertTrue(JetpackRules.cancelsDamage(true, true));
        assertFalse(JetpackRules.cancelsDamage(false, true));
        assertFalse(JetpackRules.cancelsDamage(true, false));
        assertFalse(JetpackRules.cancelsDamage(false, false));
    }

    @Test
    void theJetpackStartsOnThrustAndKeepsGoingUntilHeLands() {
        // flyingNext(allowed, thrust, wasFlying, onGround)
        assertTrue(JetpackRules.flyingNext(true, true, false, true), "thrust on the ground: lift off");
        assertTrue(JetpackRules.flyingNext(true, false, true, false), "already flying and still in the air");
        assertFalse(JetpackRules.flyingNext(true, false, true, true), "landed");
        assertFalse(JetpackRules.flyingNext(true, false, false, false), "stepping down a stair is not flying");
        assertFalse(JetpackRules.flyingNext(false, true, true, false), "not allowed");
        assertTrue(JetpackRules.flyingNext(true, true, true, false));
    }

    @Test
    void theServerForgetsTheFallOnlyWhileHeWearsIt() {
        assertTrue(JetpackRules.resetsFallDistance(true));
        assertFalse(JetpackRules.resetsFallDistance(false));
    }

    @Test
    void theLeanIsWorkedOutInTheAxesOfTheBodyNotOfTheCamera() {
        // localLean(dirX, dirZ, bodyYawDegrees): (left, forward) components of the world direction he moves in
        double[] ahead = JetpackRules.localLean(0, 1, 0);         // facing south, moving south
        assertEquals(0.0, ahead[0], 1e-9);
        assertEquals(1.0, ahead[1], 1e-9);
        double[] left = JetpackRules.localLean(1, 0, 0);          // facing south, moving east: east is his left
        assertEquals(1.0, left[0], 1e-9);
        assertEquals(0.0, left[1], 1e-9);
        double[] west = JetpackRules.localLean(-1, 0, 90);        // facing west (yaw 90), moving west: straight ahead
        assertEquals(0.0, west[0], 1e-9);
        assertEquals(1.0, west[1], 1e-9);
        double[] back = JetpackRules.localLean(0, -1, 0);         // facing south, moving north: backwards
        assertEquals(0.0, back[0], 1e-9);
        assertEquals(-1.0, back[1], 1e-9);
        double[] turned = JetpackRules.localLean(0, 1, 90);       // facing west, moving south: south is his left
        assertEquals(1.0, turned[0], 1e-9);
        assertEquals(0.0, turned[1], 1e-9);
    }

    @Test
    void constantsAreTheDocumentedOnes() {
        assertEquals(0.4, JetpackRules.MAX_RISE);
        assertEquals(0.16, JetpackRules.RISE_ACCEL);
        assertEquals(-0.3, JetpackRules.TERMINAL_FALL);
        assertEquals(0.35, JetpackRules.HORIZONTAL_SPEED);
        assertEquals(0.2, JetpackRules.AIR_CONTROL);
        assertEquals(25.0, JetpackRules.MAX_TILT_DEGREES);
    }
}
