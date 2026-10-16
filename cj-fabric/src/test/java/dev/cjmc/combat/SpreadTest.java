package dev.cjmc.combat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class SpreadTest {
    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] unit(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / l, y / l, z / l};
    }

    private static double angle(double[] a, double[] b) {
        return Math.acos(Math.max(-1.0, Math.min(1.0, dot(a, b))));
    }

    @Test
    void zeroSpreadKeepsTheDirectionAndNormalisesIt() {
        assertArrayEquals(new double[] {0, 0, 1}, Spread.perturb(new double[] {0, 0, 4}, 0.0, new Random(1)), 1e-12);
    }

    @Test
    void everyPelletStaysInsideTheConeAndIsAUnitVector() {
        double[] d = unit(0.3, -0.5, 0.8);
        double limit = Math.atan(0.08) + 1e-9;
        Random rng = new Random(42);
        for (int i = 0; i < 5000; i++) {
            double[] p = Spread.perturb(d, 0.08, rng);
            assertEquals(1.0, Math.sqrt(dot(p, p)), 1e-9);
            assertTrue(angle(d, p) <= limit, "angle " + angle(d, p));
        }
    }

    @Test
    void theConeIsFilledNotJustItsAxisOrItsEdge() {
        double[] d = unit(0, 0, 1);
        double cone = Math.atan(0.08);
        double max = 0;
        double min = Double.MAX_VALUE;
        Random rng = new Random(7);
        for (int i = 0; i < 5000; i++) {
            double a = angle(d, Spread.perturb(d, 0.08, rng));
            max = Math.max(max, a);
            min = Math.min(min, a);
        }
        assertTrue(max > 0.9 * cone, "the cone is not reached: " + max);
        assertTrue(min < 0.1 * cone, "the middle is empty: " + min);
    }

    @Test
    void theSpreadIsCentredOnTheAimDirection() {
        double[] d = unit(1, 2, -2);
        double[] sum = new double[3];
        Random rng = new Random(11);
        int n = 20000;
        for (int i = 0; i < n; i++) {
            double[] p = Spread.perturb(d, 0.08, rng);
            for (int k = 0; k < 3; k++) {
                sum[k] += p[k] / n;
            }
        }
        double along = dot(sum, d);
        double lateral = Math.sqrt(Math.max(0, dot(sum, sum) - along * along));
        assertTrue(lateral < 0.005, "the pellets lean to one side: " + lateral);
    }

    @Test
    void worksForAnyDirectionIncludingStraightUpAndDown() {
        double cone = Math.atan(0.05) + 1e-9;
        Random rng = new Random(3);
        for (double[] d : new double[][] {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}}) {
            for (int i = 0; i < 200; i++) {
                assertTrue(angle(d, Spread.perturb(d, 0.05, rng)) <= cone);
            }
        }
    }

    @Test
    void theSameSeedGivesTheSameDirectionAndAnotherSeedAnotherOne() {
        double[] d = unit(0, 0, 1);
        assertArrayEquals(Spread.perturb(d, 0.05, new Random(5)), Spread.perturb(d, 0.05, new Random(5)), 0.0);
        assertNotEquals(Spread.perturb(d, 0.05, new Random(5))[0], Spread.perturb(d, 0.05, new Random(6))[0]);
    }

    @Test
    void unusableInputsAreRejected() {
        Random rng = new Random(1);
        assertThrows(IllegalArgumentException.class, () -> Spread.perturb(new double[] {0, 0, 0}, 0.05, rng));
        assertThrows(IllegalArgumentException.class, () -> Spread.perturb(new double[] {Double.NaN, 0, 1}, 0.05, rng));
        assertThrows(IllegalArgumentException.class, () -> Spread.perturb(new double[] {0, Double.POSITIVE_INFINITY, 1}, 0.05, rng));
        assertThrows(IllegalArgumentException.class, () -> Spread.perturb(new double[] {0, 0, 1}, -0.01, rng));
        assertThrows(IllegalArgumentException.class, () -> Spread.perturb(new double[] {0, 0, 1}, Double.NaN, rng));
    }

    @Test
    void thePelletsAreUniformOverTheDiscNotBunchedInTheMiddle() {
        // half of the area of a disc lies within 1/sqrt(2) of its radius: half of the pellets must land there
        double[] d = unit(0.2, 0.7, -0.4);
        double spread = 0.08;
        Random rng = new Random(21);
        int n = 20000;
        int inside = 0;
        for (int i = 0; i < n; i++) {
            double[] p = Spread.perturb(d, spread, rng);
            if (Math.tan(angle(d, p)) <= spread / Math.sqrt(2.0)) {
                inside++;
            }
        }
        assertEquals(0.5, (double) inside / n, 0.02);
    }

    @Test
    void theSpreadIsTheSameInEveryDirectionAroundTheAxis() {
        // near-diagonal directions make the helper perpendicular far from unit length, and the helper axis differs between them
        checkIsotropy(unit(1, 1, 1));
        checkIsotropy(unit(1.05, 1, 1.1));
        checkIsotropy(unit(1.1, 1.05, 1));
        checkIsotropy(unit(1.1, 1.05, 1.2));
    }

    private static void checkIsotropy(double[] d) {
        // two perpendicular unit vectors, built here independently of the code under test
        double[] a = unit(d[1], -d[0], 0);
        if (Math.abs(a[0]) + Math.abs(a[1]) < 1e-9) {
            a = unit(0, d[2], -d[1]);
        }
        double[] b = unit(d[1] * a[2] - d[2] * a[1], d[2] * a[0] - d[0] * a[2], d[0] * a[1] - d[1] * a[0]);
        double v1 = 0;
        double v2 = 0;
        Random rng = new Random(33);
        int n = 20000;
        for (int i = 0; i < n; i++) {
            double[] p = Spread.perturb(d, 0.08, rng);
            v1 += Math.pow(dot(p, a), 2) / n;
            v2 += Math.pow(dot(p, b), 2) / n;
        }
        // a uniform disc of radius s has a variance of s^2 / 4 along any axis: the perpendiculars must be unit vectors
        double expected = 0.08 * 0.08 / 4.0;
        assertEquals(1.0, v1 / expected, 0.07);
        assertEquals(1.0, v2 / expected, 0.07);
    }
}
