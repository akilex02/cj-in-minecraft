package dev.cjmc.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MeleeTargetTest {
    private static final double[] EYE = {0, 0, 0};
    private static final double[] LOOK = {0, 0, 1}; // looking along +z

    private static int pick(double reach, double cone, double[][] centers, double[] radii) {
        return MeleeTarget.pick(EYE, LOOK, reach, cone, centers, radii);
    }

    @Test
    void aMobStraightAheadWithinReachIsPicked() {
        assertEquals(0, pick(3.0, 35.0, new double[][] {{0, 0, 2.0}}, new double[] {0.4}));
    }

    @Test
    void nothingGivesMinusOne() {
        assertEquals(-1, pick(3.0, 35.0, new double[][] {}, new double[] {}));
    }

    @Test
    void aMobBeyondReachIsNotPickedButItsNearSurfaceCounts() {
        assertEquals(-1, pick(3.0, 35.0, new double[][] {{0, 0, 3.6}}, new double[] {0.4})); // surface at 3.2 > 3.0
        assertEquals(0, pick(3.0, 35.0, new double[][] {{0, 0, 3.3}}, new double[] {0.4}));  // surface at 2.9 <= 3.0
    }

    @Test
    void aMobBehindThePlayerIsNeverPicked() {
        assertEquals(-1, pick(3.0, 90.0, new double[][] {{0, 0, -1.0}}, new double[] {0.4}));
    }

    @Test
    void aMobOutsideTheConeIsNotPickedButABigOneAtTheEdgeIs() {
        // 50 degrees off the look direction, 2 blocks away
        double a = Math.toRadians(50);
        double[][] c = {{2.0 * Math.sin(a), 0, 2.0 * Math.cos(a)}};
        assertEquals(-1, pick(3.0, 35.0, c, new double[] {0.1}));
        assertEquals(0, pick(3.0, 35.0, c, new double[] {1.0})); // it subtends asin(1/2) = 30 degrees: effective angle 20
    }

    @Test
    void theSmallestAngleWinsAndTheNearerOneBreaksATie() {
        // the second one is more central (its radius covers its small offset): angle 0 against about 11 degrees
        double[][] c = {{0.8, 0, 2.0}, {0.2, 0, 2.5}};
        assertEquals(1, pick(3.0, 60.0, c, new double[] {0.4, 0.4}));
        assertEquals(0, pick(3.0, 60.0, new double[][] {c[1], c[0]}, new double[] {0.4, 0.4})); // and the order does not matter
        // the same angle at two distances: the nearer
        double[][] same = {{0, 0, 2.5}, {0, 0, 1.5}};
        assertEquals(1, pick(3.0, 35.0, same, new double[] {0.4, 0.4}));
        assertEquals(0, pick(3.0, 35.0, new double[][] {same[1], same[0]}, new double[] {0.4, 0.4}));
    }

    @Test
    void anUnusableInputGivesMinusOne() {
        assertEquals(-1, MeleeTarget.pick(EYE, new double[] {0, 0, 0}, 3.0, 35.0, new double[][] {{0, 0, 2}}, new double[] {0.4}));
        assertEquals(-1, MeleeTarget.pick(EYE, new double[] {Double.NaN, 0, 1}, 3.0, 35.0, new double[][] {{0, 0, 2}}, new double[] {0.4}));
        assertEquals(-1, MeleeTarget.pick(EYE, LOOK, Double.NaN, 35.0, new double[][] {{0, 0, 2}}, new double[] {0.4}));
        assertEquals(-1, MeleeTarget.pick(EYE, LOOK, 3.0, 0.0, new double[][] {{0, 0, 2}}, new double[] {0.4}));
        assertEquals(-1, MeleeTarget.pick(EYE, LOOK, 3.0, 35.0, new double[][] {{0, 0, Double.NaN}}, new double[] {0.4}));
        assertEquals(-1, MeleeTarget.pick(EYE, LOOK, 3.0, 35.0, new double[][] {{0, 0, 2}}, new double[] {})); // radii shorter than centers
    }

    @Test
    void aMobRightInsideTheEyeIsStillPicked() {
        assertEquals(0, pick(3.0, 35.0, new double[][] {{0, 0, 0.0}}, new double[] {0.4}));
    }

    @Test
    void aBigSphereBehindThePlayerIsNotPickedEvenIfItsRadiusWouldCoverTheCone() {
        // centre 0.5 behind, radius 0.6: the radius alone would bring its effective angle down to the cone's edge
        assertEquals(-1, pick(3.0, 90.0, new double[][] {{0, 0, -0.5}}, new double[] {0.6}));
    }

    @Test
    void noReachOrANegativeRadiusPicksNothing() {
        assertEquals(-1, pick(0.0, 35.0, new double[][] {{0, 0, 0.0}}, new double[] {0.4}));
        assertEquals(-1, pick(-1.0, 35.0, new double[][] {{0, 0, 0.0}}, new double[] {0.4}));
        assertEquals(-1, pick(3.0, 35.0, new double[][] {{0, 0, 2.0}}, new double[] {-1.0}));
    }
}
