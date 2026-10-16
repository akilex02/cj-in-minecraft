package dev.cjmc.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RunOverTest {
    @Test
    void slowerThanAWalkingPaceHurtsNobody() {
        assertEquals(0.0, RunOver.damage(0));
        assertEquals(0.0, RunOver.damage(RunOver.MIN_SPEED));
        assertEquals(0.0, RunOver.damage(-RunOver.MIN_SPEED)); // in reverse too
    }

    @Test
    void theFasterTheMoreItHurtsInEitherDirectionUpToACap() {
        double slow = RunOver.damage(8);
        double quick = RunOver.damage(15);
        assertTrue(slow > 0 && quick > slow);
        assertEquals(RunOver.damage(15), RunOver.damage(-15), 1e-12);
        assertEquals(RunOver.MAX_DAMAGE, RunOver.damage(1000), 1e-12);
        assertTrue(RunOver.damage(30) >= 20, "a sprint kills a zombie (20 health)");
    }

    @Test
    void brokenSpeedsAreHarmless() {
        assertEquals(0.0, RunOver.damage(Double.NaN));
        assertEquals(0.0, RunOver.damage(Double.POSITIVE_INFINITY));
    }

    @Test
    void knockbackGrowsWithSpeedAndHasACap() {
        assertEquals(0.0, RunOver.knockback(2));
        assertTrue(RunOver.knockback(20) > RunOver.knockback(8));
        assertEquals(RunOver.MAX_KNOCKBACK, RunOver.knockback(1000), 1e-12);
    }

    @Test
    void anEntityInFrontOfTheCarFacingSouthIsHitAndOneBesideItOrBehindIsNot() {
        // yaw 0 faces +z; the car is 4.9 long and 1.9 wide, its centre at (0, 0)
        assertTrue(RunOver.hits(0, 0, 64, 0, 0, 2.6, 0.3, 64, 65.8));       // just past the nose, a mob's radius touches
        assertFalse(RunOver.hits(0, 0, 64, 0, 0, 3.0, 0.3, 64, 65.8));      // beyond reach
        assertTrue(RunOver.hits(0, 0, 64, 0, 0, -2.6, 0.3, 64, 65.8));      // behind: the tail hits too
        assertTrue(RunOver.hits(0, 0, 64, 0, 1.1, 0, 0.3, 64, 65.8));       // beside the door
        assertFalse(RunOver.hits(0, 0, 64, 0, 1.4, 0, 0.3, 64, 65.8));      // too far to the side
    }

    @Test
    void theWayOfTheCarIsAUnitVectorOfItsYaw() {
        assertEquals(1.0, RunOver.forward(0)[1], 1e-9);   // south is +z
        assertEquals(-1.0, RunOver.forward(90)[0], 1e-9); // west is -x
    }

    @Test
    void theCarIsTurnedByItsYaw() {
        // yaw 90 faces -x: now the length is along x
        assertTrue(RunOver.hits(90, 0, 64, 0, -2.6, 0, 0.3, 64, 65.8));
        assertFalse(RunOver.hits(90, 0, 64, 0, 0, 2.6, 0.3, 64, 65.8));
    }

    @Test
    void theHeightHasToOverlapSoTheCarDoesNotHitWhatFliesOverIt() {
        assertTrue(RunOver.hits(0, 0, 64, 0, 0, 0, 0.3, 64, 65.8));
        assertTrue(RunOver.hits(0, 0, 64, 0, 0, 0, 0.3, 65.0, 66.0));   // the roof is at 65.5
        assertFalse(RunOver.hits(0, 0, 64, 0, 0, 0, 0.3, 65.6, 67.0));  // above the roof
        assertFalse(RunOver.hits(0, 0, 64, 0, 0, 0, 0.3, 62.0, 63.9));  // under the car
    }

    @Test
    void anEntityIsHitOncePerCooldown() {
        RunOver.Cooldown c = new RunOver.Cooldown();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(c.ready(a, 100));
        assertFalse(c.ready(a, 100 + RunOver.COOLDOWN_TICKS - 1));
        assertTrue(c.ready(b, 101));
        assertTrue(c.ready(a, 100 + RunOver.COOLDOWN_TICKS));
    }

    @Test
    void theCooldownForgetsOldEntitiesSoItDoesNotGrowForever() {
        RunOver.Cooldown c = new RunOver.Cooldown();
        for (int i = 0; i < 1000; i++) {
            c.ready(UUID.randomUUID(), i);
        }
        c.prune(100000);
        assertEquals(0, c.size());
    }
}
