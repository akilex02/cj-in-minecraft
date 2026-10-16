package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BodyFacingTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    void theFirstFrameUsesTheLookYaw() {
        assertEquals(37f, new BodyFacing().update(0, 0, 37f, 0));
    }

    @Test
    void standingStillForAMomentKeepsTheLastFacing() {
        BodyFacing f = new BodyFacing();
        f.update(0, 0, 0f, 0);
        f.update(5f, 0f, 0f, 10 * SECOND); // walks east: faces -90
        assertEquals(-90f, f.update(0.1f, 0.1f, 0f, (long) (0.2f * SECOND)), 1e-3f); // 0.2 s still: below the delay
    }

    @Test
    void afterAMomentStandingStillItTurnsToTheLookDirection() {
        BodyFacing f = new BodyFacing();
        f.update(0, 0, 0f, 0);
        f.update(5f, 0f, 0f, 10 * SECOND); // faces -90
        f.update(0, 0, 0f, (long) (0.2f * SECOND));
        assertEquals(0f, f.update(0, 0, 0f, (long) (0.2f * SECOND)), 1e-3f); // 0.4 s still: turns to the look yaw (0)
        // and keeps following where the player looks
        assertEquals(36f, f.update(0, 0, 90f, (long) (0.05f * SECOND)), 1e-3f); // 50 ms at 720 deg/s = 36 degrees
    }

    @Test
    void movingResetsTheIdleTimer() {
        BodyFacing f = new BodyFacing();
        f.update(0, 0, 0f, 0);
        f.update(5f, 0f, 0f, 10 * SECOND); // faces -90
        f.update(0, 0, 0f, (long) (0.25f * SECOND)); // almost at the delay
        f.update(5f, 0f, 0f, (long) (0.01f * SECOND)); // moves again: the timer starts over
        assertEquals(-90f, f.update(0, 0, 0f, (long) (0.25f * SECOND)), 1e-3f); // 0.25 s still, not 0.5
    }

    @Test
    void whileMovingTheMovementDirectionWinsOverTheLookDirection() {
        BodyFacing f = new BodyFacing();
        f.update(0, 0, 0f, 0);
        assertEquals(-90f, f.update(5f, 0f, 90f, 10 * SECOND), 1e-3f); // looks west (90) but walks east: faces east
    }

    @Test
    void facesTheDirectionOfMovementWithMinecraftYawConvention() {
        // yaw 0 faces south (+Z), -90 east (+X), 90 west (-X), 180 north (-Z)
        float[][] cases = {{0f, 5f, 0f}, {5f, 0f, -90f}, {-5f, 0f, 90f}, {0f, -5f, 180f}};
        for (float[] c : cases) {
            BodyFacing f = new BodyFacing();
            f.update(0, 0, 0f, 0);
            float yaw = f.update(c[0], c[1], 0f, 10 * SECOND); // plenty of time to arrive
            assertEquals(Math.abs(c[2]), Math.abs(yaw), 1e-3f); // 180 and -180 are the same facing
            if (Math.abs(c[2]) != 180f) {
                assertEquals(c[2], yaw, 1e-3f);
            }
        }
    }

    @Test
    void turnsTheShortWayRoundTheSeam() {
        // Review Focus #7: from 170 to -170 degrees is 20 degrees, not 340
        BodyFacing f = new BodyFacing();
        f.update(0, 0, 170f, 0);
        // a velocity whose target yaw is -170: direction (-sin(-170), cos(-170)) = (0.1736, -0.9848)
        float yaw = f.update(0.1736f * 5, -0.9848f * 5, 0f, (long) (0.01f * SECOND)); // 10 ms -> at most 7.2 degrees
        assertEquals(177.2f, yaw, 0.01f); // it moved +7.2, towards the seam, not -7.2 the long way
    }

    @Test
    void turnsToTheLookDirectionTheShortWayRoundTheSeam() {
        BodyFacing f = new BodyFacing();
        f.update(0, 0, 170f, 0); // first frame: faces 170
        f.update(0, 0, -170f, (long) (0.3f * SECOND)); // the delay is reached: the look yaw is -170, 20 degrees away through the seam
        assertEquals(-170f, f.update(0, 0, -170f, (long) (0.05f * SECOND)), 1e-3f);
    }

    @Test
    void neverOvershootsTheTarget() {
        BodyFacing f = new BodyFacing();
        f.update(0, 0, 80f, 0);
        float yaw = f.update(-5f, 0f, 0f, 10 * SECOND); // target is 90, a huge time step
        assertEquals(90f, yaw, 1e-3f);
    }

    @Test
    void turnRateLimitsEachStep() {
        BodyFacing f = new BodyFacing();
        f.update(0, 0, 0f, 0);
        float yaw = f.update(5f, 0f, 0f, (long) (0.05f * SECOND)); // target -90; 50 ms at 720 deg/s = 36 degrees
        assertEquals(-36f, yaw, 0.01f);
        assertTrue(f.update(5f, 0f, 0f, (long) (0.05f * SECOND)) < yaw);
    }

    @Test
    void aZeroOrNegativeTimeStepDoesNotMove() {
        BodyFacing f = new BodyFacing();
        f.update(0, 0, 10f, 0);
        assertEquals(10f, f.update(5f, 0f, 0f, 0));
        assertEquals(10f, f.update(5f, 0f, 0f, -SECOND));
    }

    @Test
    void whileAimingTheBodyFacesTheLookDirectionAtOnce() {
        // GTA turns CJ towards his target when he aims, so the drawn body must face where the player looks, moving or not
        BodyFacing f = new BodyFacing();
        f.update(0, 0, 0f, 0);
        f.update(5f, 0f, 0f, 10 * SECOND); // walked east: faces -90
        assertEquals(-54f, f.update(0, 0, 0f, (long) (0.05f * SECOND), true), 1e-3f); // no idle delay: 36 degrees in 50 ms
        BodyFacing g = new BodyFacing();
        g.update(0, 0, 0f, 0);
        // moving east while aiming almost north (yaw 170): the look direction wins over the movement direction
        assertEquals(36f, g.update(5f, 0f, 170f, (long) (0.05f * SECOND), true), 1e-3f);
    }

    @Test
    void aimingDoesNotChangeTheDefaultBehaviour() {
        BodyFacing f = new BodyFacing();
        f.update(0, 0, 0f, 0);
        f.update(5f, 0f, 0f, 10 * SECOND);
        assertEquals(-90f, f.update(0, 0, 0f, (long) (0.05f * SECOND), false), 1e-3f); // not aiming: waits for the idle delay
    }
}
