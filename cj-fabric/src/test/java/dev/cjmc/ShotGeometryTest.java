package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ShotGeometryTest {
    @Test
    void tracerPointsAreEvenlySpacedAlongTheRay() {
        double[][] p = ShotGeometry.tracerPoints(new double[] {1, 2, 3}, new double[] {0, 0, 1}, 10, 5);
        assertEquals(5, p.length);
        assertArrayEquals(new double[] {1, 2, 5}, p[0], 1e-9);   // the first point is one step in, not on the muzzle
        assertArrayEquals(new double[] {1, 2, 13}, p[4], 1e-9);  // the last point is at the full length
    }

    @Test
    void aNonUnitDirectionIsNormalised() {
        double[][] p = ShotGeometry.tracerPoints(new double[] {0, 0, 0}, new double[] {0, 0, 4}, 8, 2);
        assertArrayEquals(new double[] {0, 0, 4}, p[0], 1e-9);
        assertArrayEquals(new double[] {0, 0, 8}, p[1], 1e-9);
    }

    @Test
    void aDegenerateRayGivesNoPoints() {
        assertEquals(0, ShotGeometry.tracerPoints(new double[] {0, 0, 0}, new double[] {0, 0, 0}, 8, 4).length);
        assertEquals(0, ShotGeometry.tracerPoints(new double[] {0, 0, 0}, new double[] {0, 0, 1}, 0, 4).length);
        assertEquals(0, ShotGeometry.tracerPoints(new double[] {0, 0, 0}, new double[] {0, 0, 1}, 8, 0).length);
        assertEquals(0, ShotGeometry.tracerPoints(new double[] {0, 0, 0}, new double[] {Double.NaN, 0, 1}, 8, 4).length);
    }
}
