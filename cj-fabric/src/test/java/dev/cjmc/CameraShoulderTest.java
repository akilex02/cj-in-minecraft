package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CameraShoulderTest {
    @Test
    void startsCentredAndSlidesToTheFullOffsetWhileAiming() {
        CameraShoulder s = new CameraShoulder();
        assertEquals(0.0, s.step(false, 0.1));
        double half = s.step(true, 0.1);
        assertTrue(half > 0 && half < CameraShoulder.OFFSET);
        assertEquals(CameraShoulder.OFFSET, s.step(true, 0.5), 1e-9);
    }

    @Test
    void slidesBackWhenAimingStopsAndNeverGoesBelowZero() {
        CameraShoulder s = new CameraShoulder();
        s.step(true, 0.25);
        s.step(true, 0.25);
        double back = s.step(false, 0.05);
        assertTrue(back > 0 && back < CameraShoulder.OFFSET);
        assertEquals(0.0, s.step(false, 1.0));
        assertEquals(0.0, s.step(false, 1.0));
    }

    @Test
    void aHugeOrBrokenFrameTimeDoesNotJump() {
        CameraShoulder s = new CameraShoulder();
        assertTrue(s.step(true, 100) <= CameraShoulder.OFFSET);
        CameraShoulder t = new CameraShoulder();
        assertEquals(0.0, t.step(true, Double.NaN));
        assertEquals(0.0, t.step(true, -5));
    }

    @Test
    void theEasingAmountGoesFromZeroToOneWithTheSameTransitionAsTheShift() {
        CameraShoulder s = new CameraShoulder();
        assertEquals(0.0, s.amount());
        s.step(true, 0.1);
        assertEquals(0.5, s.amount(), 1e-9);                       // 0.2 s to full
        s.step(true, 0.5);
        assertEquals(1.0, s.amount(), 1e-9);
        s.step(false, 0.1);
        assertEquals(0.5, s.amount(), 1e-9);
        s.step(false, 5);
        assertEquals(0.0, s.amount(), 1e-9);
    }

    @Test
    void aimingPullsTheCameraInToTheAimDistanceAndNeverFartherOut() {
        assertEquals(1.3, CameraShoulder.AIM_DISTANCE);                           // compared with San Andreas' own screenshot
        assertEquals(0.45, CameraShoulder.OFFSET);
        assertEquals(4.0, CameraShoulder.distanceFor(4.0, 0.0), 1e-9);            // not aiming: where the game put it
        assertEquals(1.3, CameraShoulder.distanceFor(4.0, 1.0), 1e-9);            // aiming: pulled in to the shoulder
        assertEquals(2.65, CameraShoulder.distanceFor(4.0, 0.5), 1e-9);           // and on the way in between
        assertEquals(1.0, CameraShoulder.distanceFor(1.0, 1.0), 1e-9);            // already closer (against a wall): left alone
        assertEquals(1.3, CameraShoulder.distanceFor(1.3, 1.0), 1e-9);
        assertEquals(1.2, CameraShoulder.distanceFor(1.2, 0.3), 1e-9);
    }
}
