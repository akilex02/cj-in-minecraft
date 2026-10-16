package dev.cjmc.climb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClimbPathTest {
    @Test
    void constantsAreTheChosenOnes() {
        assertEquals(500L, ClimbPath.JUMP_MS);
        assertEquals(1300L, ClimbPath.PULL_END_MS);
        assertEquals(1700L, ClimbPath.RISE_END_MS);
        assertEquals(2100L, ClimbPath.TOTAL_MS);
        assertEquals(450L, ClimbPath.SETTLE_MS);
        assertEquals(2.2, ClimbPath.HANG_BLOCKS);
        assertEquals(0.3, ClimbPath.PULL_BELOW);
    }

    @Test
    void theLiftHangsThenPullsThenReachesTheTop() {
        // height 3: the hands reach the ledge when the feet are 0.8 up; the pull ends 0.3 below the top; the top is reached at RISE_END
        assertEquals(0.0, ClimbPath.lift(3, 0), 1e-9);
        assertEquals(0.4, ClimbPath.lift(3, 250), 1e-9);
        assertEquals(0.8, ClimbPath.lift(3, 500), 1e-9);
        assertEquals(1.75, ClimbPath.lift(3, 900), 1e-9);
        assertEquals(2.7, ClimbPath.lift(3, 1300), 1e-9);
        assertEquals(2.85, ClimbPath.lift(3, 1500), 1e-9);
        assertEquals(3.0, ClimbPath.lift(3, 1700), 1e-9);
        assertEquals(3.0, ClimbPath.lift(3, 9000), 1e-9);
        assertEquals(0.0, ClimbPath.lift(3, -500), 1e-9);
    }

    @Test
    void aLowWallDoesNotRiseBeforeThePullAndWorksWithFractions() {
        assertEquals(0.0, ClimbPath.lift(2, 500), 1e-9);
        assertEquals(0.85, ClimbPath.lift(2, 900), 1e-9);
        assertEquals(1.7, ClimbPath.lift(2, 1300), 1e-9);
        assertEquals(2.0, ClimbPath.lift(2, 1700), 1e-9);
        assertEquals(2.5, ClimbPath.lift(2.5, 2000), 1e-9);  // a wall of 2.5 (a slab on top)
    }

    @Test
    void theApproachToTheWallIsMadeDuringTheJump() {
        assertEquals(0.0, ClimbPath.approachFraction(0), 1e-9);
        assertEquals(0.5, ClimbPath.approachFraction(250), 1e-9);
        assertEquals(1.0, ClimbPath.approachFraction(500), 1e-9);
        assertEquals(1.0, ClimbPath.approachFraction(9000), 1e-9);
        assertEquals(0.0, ClimbPath.approachFraction(-100), 1e-9);
    }

    @Test
    void theForwardMoveStartsWhenTheTopIsReached() {
        assertEquals(0.0, ClimbPath.forwardFraction(0), 1e-9);
        assertEquals(0.0, ClimbPath.forwardFraction(1700), 1e-9);
        assertEquals(0.5, ClimbPath.forwardFraction(1900), 1e-9);
        assertEquals(1.0, ClimbPath.forwardFraction(2100), 1e-9);
        assertEquals(1.0, ClimbPath.forwardFraction(9000), 1e-9);
    }

    @Test
    void theAnimationLastsTheTotalAndTheMoveEndsAfterTheSettle() {
        assertTrue(ClimbPath.animating(ClimbPath.TOTAL_MS - 1));
        assertFalse(ClimbPath.animating(ClimbPath.TOTAL_MS));
        assertFalse(ClimbPath.done(ClimbPath.TOTAL_MS + ClimbPath.SETTLE_MS - 1));
        assertTrue(ClimbPath.done(ClimbPath.TOTAL_MS + ClimbPath.SETTLE_MS));
    }
}
