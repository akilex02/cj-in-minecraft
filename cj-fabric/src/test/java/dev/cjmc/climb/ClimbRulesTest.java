package dev.cjmc.climb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClimbRulesTest {
    // heightOf(wallBlocks, clearAbove, standable, pushing, onGround, sneaking, eligible)

    @Test
    void wallsOfTwoAndThreeClimb() {
        assertEquals(2, ClimbRules.heightOf(2, 2, true, true, true, false, true));
        assertEquals(3, ClimbRules.heightOf(3, 2, true, true, true, false, true));
    }

    @Test
    void oneBlockOrFourDoNotClimb() {
        assertEquals(0, ClimbRules.heightOf(0, 2, true, true, true, false, true));
        assertEquals(0, ClimbRules.heightOf(1, 2, true, true, true, false, true));
        assertEquals(0, ClimbRules.heightOf(4, 2, true, true, true, false, true));
    }

    @Test
    void needsRoomAboveAndAPlaceToStand() {
        assertEquals(0, ClimbRules.heightOf(2, 0, true, true, true, false, true));
        assertEquals(0, ClimbRules.heightOf(2, 1, true, true, true, false, true));
        assertEquals(3, ClimbRules.heightOf(3, 5, true, true, true, false, true));
        assertEquals(0, ClimbRules.heightOf(2, 2, false, true, true, false, true));
    }

    @Test
    void needsPushingOnGroundNotSneakingAndEligible() {
        assertEquals(0, ClimbRules.heightOf(2, 2, true, false, true, false, true), "not pushing");
        assertEquals(0, ClimbRules.heightOf(2, 2, true, true, false, false, true), "in the air");
        assertEquals(0, ClimbRules.heightOf(2, 2, true, true, true, true, true), "sneaking");
        assertEquals(0, ClimbRules.heightOf(2, 2, true, true, true, false, false), "not eligible");
    }

    @Test
    void eligibleRejectsEveryBadState() {
        assertTrue(ClimbRules.eligible(false, false, false, false, false, false, false));
        assertFalse(ClimbRules.eligible(true, false, false, false, false, false, false), "riding");
        assertFalse(ClimbRules.eligible(false, true, false, false, false, false, false), "in a fluid");
        assertFalse(ClimbRules.eligible(false, false, true, false, false, false, false), "on a ladder");
        assertFalse(ClimbRules.eligible(false, false, false, true, false, false, false), "flying");
        assertFalse(ClimbRules.eligible(false, false, false, false, true, false, false), "gliding");
        assertFalse(ClimbRules.eligible(false, false, false, false, false, true, false), "frozen");
        assertFalse(ClimbRules.eligible(false, false, false, false, false, false, true), "dead");
    }

    @Test
    void canGrabOnTheGroundOrWhileRisingInTheAir() {
        assertTrue(ClimbRules.canGrab(true, -3.0));          // on the ground, whatever the speed was
        assertTrue(ClimbRules.canGrab(false, 0.42));          // a jump going up
        assertTrue(ClimbRules.canGrab(false, 0.0));           // the top of the jump
        assertTrue(ClimbRules.canGrab(false, -0.1));          // the limit
        assertFalse(ClimbRules.canGrab(false, -0.11));        // falling: no grabbing (a cliff next to a wall)
    }

    @Test
    void theApproachIsTheGapToTheWallLeftByTheBody() {
        assertEquals(0.49, ClimbRules.approach(0.8), 1e-9);   // short of the wall by a skin, or the box would touch it and not fit
        assertEquals(0.0, ClimbRules.approach(ClimbRules.HUG), 1e-9);
        assertEquals(0.0, ClimbRules.approach(ClimbRules.HUG + ClimbRules.SKIN / 2), 1e-9);
        assertEquals(0.0, ClimbRules.approach(0.1), 1e-9);    // never backwards
    }

    @Test
    void aKnockedDownPlayerAWeaponThatForbidsJumpingOrAForcedSneakCannotClimb() {
        assertTrue(ClimbRules.permitted(false, true, false, false));
        assertFalse(ClimbRules.permitted(true, true, false, false), "knocked down");
        assertFalse(ClimbRules.permitted(false, false, false, false), "a weapon that forbids jumping");
        assertFalse(ClimbRules.permitted(false, true, true, false), "sneak forced while striking");
        assertFalse(ClimbRules.permitted(false, true, false, true), "wearing the jetpack: the key lifts off, it does not climb");
    }

    @Test
    void theWallIsCountedFromTheGroundTheJumpLeftNotFromTheFeetInTheAir() {
        assertEquals(64, ClimbRules.groundRow(64.0));
        assertEquals(64, ClimbRules.groundRow(63.995));      // standing a hair low on the block
        assertEquals(63, ClimbRules.groundRow(63.98));
        assertEquals(64, ClimbRules.groundRow(64.5));         // on a slab
        // the ground of the jump is the reference while he is within the height of a jump above it
        assertEquals(64.0, ClimbRules.referenceY(64.0, 65.0), 1e-9);
        assertEquals(64.0, ClimbRules.referenceY(64.0, 65.6), 1e-9);
        assertEquals(65.8, ClimbRules.referenceY(64.0, 65.8), 1e-9);   // above the height of a jump
        assertEquals(64.0, ClimbRules.referenceY(64.0, 64.02), 1e-9);  // a hair above the ground
        assertEquals(64.0, ClimbRules.referenceY(64.0, 63.97), 1e-9);  // a hair below it
        assertEquals(63.9, ClimbRules.referenceY(64.0, 63.9), 1e-9);   // a little below: he stepped down
        assertEquals(66.7, ClimbRules.referenceY(64.0, 66.7), 1e-9);   // far above: something else (a fall's end); use his feet
        assertEquals(63.0, ClimbRules.referenceY(64.0, 63.0), 1e-9);   // below that ground: he stepped down
        assertEquals(64.2, ClimbRules.referenceY(Double.NaN, 64.2), 1e-9); // no ground seen yet
    }

    @Test
    void aTeleportOrAnyBigStrayEndsTheClimb() {
        assertFalse(ClimbRules.strayed(0.0, -0.0784, 0.0));   // the gravity of a tick before the controller puts him back
        assertFalse(ClimbRules.strayed(0.3, 0.3, 0.2));
        assertTrue(ClimbRules.strayed(0.5, 0.0, 0.0));        // exactly the limit
        assertFalse(ClimbRules.strayed(0.49, 0.0, 0.0));
        assertTrue(ClimbRules.strayed(0.0, 0.0, 0.6));
        assertTrue(ClimbRules.strayed(0.0, 0.6, 0.0));
        assertTrue(ClimbRules.strayed(0.3, 0.3, 0.4));
        assertTrue(ClimbRules.strayed(0.5, 0.5, 0.0));
        assertTrue(ClimbRules.strayed(100.0, 0.0, 0.0));
        assertEquals(0.5, ClimbRules.MAX_STRAY);
    }

    @Test
    void limitsAreTheDocumentedOnes() {
        assertEquals(0.8, ClimbRules.REACH);
        assertEquals(0.3, ClimbRules.HUG);
        assertEquals(0.01, ClimbRules.SKIN);
        assertEquals(-0.1, ClimbRules.MIN_RISING_SPEED);
        assertEquals(2, ClimbRules.MIN_HEIGHT);
        assertEquals(3, ClimbRules.MAX_HEIGHT);
        assertEquals(2, ClimbRules.CLEAR_ABOVE);
    }
}
