package dev.cjmc.swim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SwimRulesTest {
    private static final double SURFACE = 64.0;

    // allowed(inWater, riding, jetpackFlying, gliding, climbingLadder, creativeFlying, knockedDown, dead, wallClimbing)
    @Test
    void allowedOnlyInWaterAndFree() {
        assertTrue(SwimRules.allowed(true, false, false, false, false, false, false, false, false));
        assertFalse(SwimRules.allowed(false, false, false, false, false, false, false, false, false), "not in water");
        assertFalse(SwimRules.allowed(true, true, false, false, false, false, false, false, false), "riding");
        assertFalse(SwimRules.allowed(true, false, true, false, false, false, false, false, false), "flying with the jetpack");
        assertFalse(SwimRules.allowed(true, false, false, true, false, false, false, false, false), "gliding");
        assertFalse(SwimRules.allowed(true, false, false, false, true, false, false, false, false), "on a ladder");
        assertFalse(SwimRules.allowed(true, false, false, false, false, true, false, false, false), "creative flying");
        assertFalse(SwimRules.allowed(true, false, false, false, false, false, true, false, false), "knocked down");
        assertFalse(SwimRules.allowed(true, false, false, false, false, false, false, true, false), "dead");
        assertFalse(SwimRules.allowed(true, false, false, false, false, false, false, false, true), "climbing a wall");
    }

    @Test
    void floatHeightKeepsTheBackAtTheSurfaceWhenSwimmingAndTheShouldersWhenTreading() {
        assertEquals(SURFACE - SwimRules.FLOAT_DEPTH, SwimRules.floatHeight(SURFACE, true), 1e-9);    // lying down: the back at the water
        assertEquals(SURFACE - SwimRules.TREAD_DEPTH, SwimRules.floatHeight(SURFACE, false), 1e-9);   // upright: the water at his shoulders, his arms under it
        assertTrue(SwimRules.TREAD_DEPTH > SwimRules.FLOAT_DEPTH);
    }

    @Test
    void onTheFloatHeightThereIsNoVerticalSpeed() {
        assertEquals(0.0, SwimRules.verticalSpeed(0.3, SwimRules.floatHeight(SURFACE, false), SURFACE, false, false, false), 1e-9);
    }

    @Test
    void belowTheFloatHeightHeRisesUpToTheSurfaceSpeed() {
        double float_ = SwimRules.floatHeight(SURFACE, false);
        assertEquals(0.2 * SwimRules.GAIN, SwimRules.verticalSpeed(0.0, float_ - 0.2, SURFACE, false, false, false), 1e-9);
        assertEquals(SwimRules.SURFACE_SPEED, SwimRules.verticalSpeed(0.0, float_ - 1.0, SURFACE, false, false, false), 1e-9);
        assertEquals(SwimRules.SURFACE_SPEED, SwimRules.verticalSpeed(-0.5, float_ - 60.0, SURFACE, false, false, false), 1e-9);
    }

    @Test
    void aboveTheFloatHeightHeSinksGentlyButNotFasterThanTheMaximum() {
        double float_ = SwimRules.floatHeight(SURFACE, false);
        assertEquals(-0.2 * SwimRules.GAIN, SwimRules.verticalSpeed(0.0, float_ + 0.2, SURFACE, false, false, false), 1e-9);
        assertEquals(-SwimRules.SINK_MAX, SwimRules.verticalSpeed(0.0, float_ + 5.0, SURFACE, false, false, false), 1e-9);
    }

    @Test
    void swimmingFloatsHigherThanTreadingBecauseHeLiesDown() {
        double swim = SwimRules.floatHeight(SURFACE, true);
        double tread = SwimRules.floatHeight(SURFACE, false);
        assertEquals(0.0, SwimRules.verticalSpeed(0.0, swim, SURFACE, true, false, false), 1e-9);              // on the swimming height while swimming
        assertEquals((swim - tread) * SwimRules.GAIN, SwimRules.verticalSpeed(0.0, tread, SURFACE, true, false, false), 1e-9);  // starting to swim: he rises a little
        assertEquals((tread - swim) * SwimRules.GAIN, SwimRules.verticalSpeed(0.0, swim, SURFACE, false, false, false), 1e-9);  // stopping: he sinks a little
    }

    @Test
    void shiftDivesAtAFixedSpeedWhateverTheDepth() {
        assertEquals(-SwimRules.DIVE_SPEED, SwimRules.verticalSpeed(0.0, SwimRules.floatHeight(SURFACE, false), SURFACE, false, true, false), 1e-9);
        assertEquals(-SwimRules.DIVE_SPEED, SwimRules.verticalSpeed(0.2, SURFACE - 10.0, SURFACE, false, true, false), 1e-9);
    }

    @Test
    void spaceLeavesTheVerticalSpeedToMinecraftWithOrWithoutShift() {
        assertEquals(0.04, SwimRules.verticalSpeed(0.04, SURFACE - 3.0, SURFACE, false, false, true), 1e-9);
        assertEquals(0.04, SwimRules.verticalSpeed(0.04, SURFACE - 3.0, SURFACE, false, true, true), 1e-9);
        assertEquals(-0.3, SwimRules.verticalSpeed(-0.3, SURFACE - 3.0, SURFACE, false, false, true), 1e-9);
    }

    @Test
    void aPuddleIsNotDeepEnoughToSwimIn() {
        assertEquals(1.0, SwimRules.MIN_DEPTH);
        // deepEnough(depthOverFeet, wasSwimming, onGround)
        assertFalse(SwimRules.deepEnough(0.89, false, false));         // one block of water: he walks on the bottom
        assertFalse(SwimRules.deepEnough(0.999, false, true));
        assertTrue(SwimRules.deepEnough(1.0, false, false));
        assertTrue(SwimRules.deepEnough(SwimRules.FLOAT_DEPTH, false, true));   // and afloat he is still deep enough: no blinking at the float height
        assertTrue(SwimRules.deepEnough(30.0, false, false));
        // holding Space lifts his feet almost to the surface: while he is swimming and off the ground he stays in the water
        assertTrue(SwimRules.deepEnough(0.3, true, false));
        assertTrue(SwimRules.deepEnough(0.0, true, false));
        assertFalse(SwimRules.deepEnough(0.3, true, true), "his feet touch the bottom: he walks");
        assertFalse(SwimRules.deepEnough(0.3, false, false), "he was not swimming: a shallow edge does not start it");
    }

    @Test
    void theCrawlIsTheRunKeyHeldWhileMovingInTheWater() {
        // Minecraft cancels the sprint of a player who is in the water with his head out, so the key itself is read
        assertTrue(SwimRules.crawling(true, true));
        assertFalse(SwimRules.crawling(false, true), "not running");
        assertFalse(SwimRules.crawling(true, false), "not moving");
        assertFalse(SwimRules.crawling(false, false));
    }

    @Test
    void theCrawlOnTheSurfaceIsFasterAndGetsThereGradually() {
        assertEquals(0.22, SwimRules.CRAWL_SPEED);
        assertEquals(0.2, SwimRules.CRAWL_CONTROL);
        assertEquals(SwimRules.CRAWL_CONTROL * SwimRules.CRAWL_SPEED, SwimRules.crawlStep(0.0, 1.0), 1e-9);
        assertEquals(-SwimRules.CRAWL_CONTROL * SwimRules.CRAWL_SPEED, SwimRules.crawlStep(0.0, -1.0), 1e-9);
        double v = 0;
        for (int i = 0; i < 100; i++) {
            double next = SwimRules.crawlStep(v, 1.0);
            assertTrue(next >= v && next <= SwimRules.CRAWL_SPEED + 1e-9);
            v = next;
        }
        assertEquals(SwimRules.CRAWL_SPEED, v, 1e-6);
        assertEquals(0.05 + (0.0 - 0.05) * SwimRules.CRAWL_CONTROL, SwimRules.crawlStep(0.05, 0.0), 1e-9);   // with no wish it slows down
    }

    @Test
    void theWishIsTheMovementKeysTurnedToTheWayHeFaces() {
        double[] ahead = SwimRules.wish(1.0, 0.0, 0.0);               // facing south, forward key
        assertEquals(0.0, ahead[0], 1e-9);
        assertEquals(1.0, ahead[1], 1e-9);
        double[] left = SwimRules.wish(0.0, 1.0, 0.0);                // the left key: his left is east (+x)
        assertEquals(1.0, left[0], 1e-9);
        assertEquals(0.0, left[1], 1e-9);
        double[] west = SwimRules.wish(1.0, 0.0, 90.0);               // facing west
        assertEquals(-1.0, west[0], 1e-9);
        assertEquals(0.0, west[1], 1e-9);
        double[] westLeft = SwimRules.wish(0.0, 1.0, 90.0);           // facing west, the left key: his left is south (+z)
        assertEquals(0.0, westLeft[0], 1e-9);
        assertEquals(1.0, westLeft[1], 1e-9);
        double[] diagonal = SwimRules.wish(1.0, 1.0, 0.0);            // two keys: never faster than one
        assertEquals(1.0, Math.sqrt(diagonal[0] * diagonal[0] + diagonal[1] * diagonal[1]), 1e-9);
        assertEquals(diagonal[0], diagonal[1], 1e-9);                 // and the direction is kept
        double[] none = SwimRules.wish(0.0, 0.0, 33.0);
        assertEquals(0.0, none[0], 1e-9);
        assertEquals(0.0, none[1], 1e-9);
        double[] half = SwimRules.wish(0.5, 0.0, 0.0);                // a short push stays short
        assertEquals(0.5, half[1], 1e-9);
    }

    @Test
    void theSwimPoseTakesAWhileToFadeAndTheBaselineWaitsForIt() {
        assertEquals(400L, SwimRules.POSE_FADE_MS);
        assertTrue(SwimRules.poseFading(0));
        assertTrue(SwimRules.poseFading(400));
        assertFalse(SwimRules.poseFading(401));
        assertFalse(SwimRules.baselineStale(400), "the pose still carries the stroke's motion: keep the old baseline");
        assertTrue(SwimRules.baselineStale(401));
        assertTrue(SwimRules.baselineStale(Long.MAX_VALUE));          // never in the water
    }

    @Test
    void aStrongPushOfMinecraftOrAStepIsLeftToMinecraft() {
        assertEquals(0.2, SwimRules.PUSH_LIMIT);
        assertFalse(SwimRules.leavesToMinecraft(0.0, false));
        assertFalse(SwimRules.leavesToMinecraft(SwimRules.SURFACE_SPEED, false));     // what the buoyancy itself asks for is never a push
        assertFalse(SwimRules.leavesToMinecraft(0.2, false));
        assertTrue(SwimRules.leavesToMinecraft(0.21, false), "a bubble column, a riptide, an explosion");
        assertTrue(SwimRules.leavesToMinecraft(-0.21, false), "a down column of magma");
        assertTrue(SwimRules.leavesToMinecraft(0.0, true), "against a step: the hop of Minecraft");
        assertTrue(SwimRules.leavesToMinecraft(-0.05, true));
    }

    @Test
    void lookingDownWhileSwimmingFastIsADive() {
        assertEquals(20.0, SwimRules.LOOK_DIVE_PITCH);
        assertTrue(SwimRules.lookDiving(true, 45.0));
        assertTrue(SwimRules.lookDiving(true, 20.1));
        assertFalse(SwimRules.lookDiving(true, 20.0));
        assertFalse(SwimRules.lookDiving(true, -30.0), "looking up");
        assertFalse(SwimRules.lookDiving(false, 80.0), "not in the swimming pose");
    }

    @Test
    void movingNeedsMoreThanTheThreshold() {
        assertFalse(SwimRules.moving(SwimRules.MOVING_SPEED));
        assertTrue(SwimRules.moving(SwimRules.MOVING_SPEED + 0.001));
        assertTrue(SwimRules.moving(-0.1));
        assertFalse(SwimRules.moving(0.0));
    }

    @Test
    void onlyWaterForgetsTheFall() {
        assertTrue(SwimRules.resetsFallDistance(true));
        assertFalse(SwimRules.resetsFallDistance(false));
    }

    @Test
    void constantsAreTheDocumentedOnes() {
        assertEquals(1.15, SwimRules.FLOAT_DEPTH);
        assertEquals(1.4, SwimRules.TREAD_DEPTH);
        assertEquals(0.08, SwimRules.DIVE_SPEED);
        assertEquals(0.08, SwimRules.SURFACE_SPEED);          // 0.12 felt too quick when trying it
        assertEquals(0.1, SwimRules.SINK_MAX);
        assertEquals(0.25, SwimRules.GAIN);
        assertEquals(0.03, SwimRules.MOVING_SPEED);
    }
}
