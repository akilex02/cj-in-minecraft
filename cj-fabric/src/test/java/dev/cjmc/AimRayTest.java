package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class AimRayTest {
    private static final double[] FEET = {10, 64, -5};
    private static final double[] EYE = {10, 65.62, -5};

    @Test
    void withNothingInTheWayTheAimPointIsAtMaxDistance() {
        float[] a = AimRay.aimVector(FEET, EYE, new double[] {0, 0, 1}, Double.NaN);
        assertArrayEquals(new float[] {0, 1.62f, 50}, a, 1e-4f);
    }

    @Test
    void aHitSetsTheDistance() {
        float[] a = AimRay.aimVector(FEET, EYE, new double[] {0, 0, 1}, 10);
        assertArrayEquals(new float[] {0, 1.62f, 10}, a, 1e-4f);
    }

    @Test
    void aHitCloserThanTheMinimumIsPushedOut() {
        // the arm would point backwards through the shoulder: the aim point never gets closer than 2 blocks
        float[] a = AimRay.aimVector(FEET, EYE, new double[] {0, 0, 1}, 0.5);
        assertArrayEquals(new float[] {0, 1.62f, 2}, a, 1e-4f);
    }

    @Test
    void aFarHitIsClampedToTheMaximum() {
        float[] a = AimRay.aimVector(FEET, EYE, new double[] {1, 0, 0}, 500);
        assertArrayEquals(new float[] {50, 1.62f, 0}, a, 1e-4f);
    }

    @Test
    void lookingDownAimsBelowTheFeetLevel() {
        float[] a = AimRay.aimVector(FEET, EYE, new double[] {0, -1, 0}, 3.62);
        assertArrayEquals(new float[] {0, -2.0f, 0}, a, 1e-4f);
    }

    @Test
    void unusableInputsGiveNoAim() {
        // all zero = "no aim" in the protocol; the bridge then creates no task
        assertArrayEquals(new float[] {0, 0, 0}, AimRay.aimVector(FEET, EYE, new double[] {Double.NaN, 0, 1}, 5));
        assertArrayEquals(new float[] {0, 0, 0}, AimRay.aimVector(FEET, new double[] {Double.POSITIVE_INFINITY, 0, 0}, new double[] {0, 0, 1}, 5));
    }
}
