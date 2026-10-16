package dev.cjmc.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VehicleCollisionTest {
    private static final double GROUND = 64;

    private static FakeTerrain flat() {
        return new FakeTerrain(GROUND);
    }

    @Test
    void onFlatGroundItMovesWhereItWants() {
        var r = VehicleCollision.move(flat(), 0.5, GROUND, 3, 0, 0.5, 6);
        assertFalse(r.blocked());
        assertEquals(6, r.z(), 1e-9);
        assertEquals(0.5, r.x(), 1e-9);
        assertEquals(GROUND, r.y(), 1e-9);
    }

    @Test
    void aWallStopsItWithTheEdgeOfTheBoxAtTheWallWhateverTheStepSize() {
        FakeTerrain wall = flat().solid(-5, 5, 10, 10, GROUND, GROUND + 3);
        double stop = 7.55; // the wall at z = 10 minus half of the 4.9 length
        var far = VehicleCollision.move(wall, 0.5, GROUND, 3, 0, 0.5, 20);
        assertTrue(far.blocked());
        assertEquals(stop, far.z(), 0.01);
        assertEquals(0.5, far.x(), 1e-9);
        var small = VehicleCollision.move(wall, 0.5, GROUND, 7, 0, 0.5, 7.01); // a tiny step never goes through
        assertFalse(small.blocked());
        assertEquals(7.01, small.z(), 1e-9);
        var near = VehicleCollision.move(wall, 0.5, GROUND, stop - 0.005, 0, 0.5, stop + 5);
        assertTrue(near.blocked());
        assertEquals(stop, near.z(), 0.01);
        var jump = VehicleCollision.move(wall, 0.5, GROUND, 3, 0, 0.5, 40); // a move bigger than the wall is thick does not jump it
        assertEquals(stop, jump.z(), 0.01);
    }

    @Test
    void aBlockedStepThatGetsMostOfTheWayStillCountsAsBlocked() {
        FakeTerrain wall = flat().solid(-5, 5, 10, 10, GROUND, GROUND + 3);
        var r = VehicleCollision.move(wall, 0.5, GROUND, 7.4, 0, 0.5, 7.65); // one step of 0.25: 0.15 of it fits
        assertTrue(r.blocked());
        assertEquals(7.55, r.z(), 0.01);
    }

    @Test
    void theSameAlongX() {
        FakeTerrain wall = flat().solid(-10, -10, -5, 5, GROUND, GROUND + 3);
        var r = VehicleCollision.move(wall, -6.4, GROUND, 0.5, 90, -6.65, 0.5); // faces -x; the wall's face at x = -9 stops it at -6.55
        assertTrue(r.blocked());
        assertEquals(-6.55, r.x(), 0.01);
    }

    @Test
    void standingStillIsNotBlockedEvenInsideABlock() {
        FakeTerrain wall = flat().solid(0, 0, 0, 0, GROUND, GROUND + 3);
        var r = VehicleCollision.move(wall, 0.5, GROUND, 0.5, 0, 0.5, 0.5);
        assertFalse(r.blocked());
        assertEquals(0.5, r.z(), 1e-9);
    }

    @Test
    void facingWestTheLengthIsAlongX() {
        FakeTerrain wall = flat().solid(-10, -10, -5, 5, GROUND, GROUND + 3);
        var r = VehicleCollision.move(wall, 3, GROUND, 0.5, 90, -20, 0.5); // yaw 90 faces -x
        assertTrue(r.blocked());
        assertEquals(-6.55, r.x(), 0.01); // the wall's face at x = -9, half of 4.9 away
    }

    @Test
    void stepsOfUpToAFullBlockAreClimbedAndHigherOnesStopIt() {
        for (double h : new double[] {0.5, 0.6, 0.99609375, 1.0}) {
            FakeTerrain step = flat().solid(-5, 5, 10, 20, GROUND, GROUND + h);
            var r = VehicleCollision.move(step, 0.5, GROUND, 3, 0, 0.5, 15);
            assertFalse(r.blocked(), "step " + h);
            assertEquals(GROUND + h, r.y(), 1e-9);
            assertEquals(15, r.z(), 1e-9);
        }
        for (double h : new double[] {1.0078125, 1.0625, 1.5}) {
            FakeTerrain step = flat().solid(-5, 5, 10, 20, GROUND, GROUND + h);
            var r = VehicleCollision.move(step, 0.5, GROUND, 3, 0, 0.5, 15);
            assertTrue(r.blocked(), "step " + h);
            assertEquals(GROUND, r.y(), 1e-9);
            assertEquals(7.55, r.z(), 0.01);
        }
    }

    @Test
    void aWallParallelToTheMovementLetsTheFreeComponentThrough() {
        FakeTerrain wall = flat().solid(3, 3, -50, 50, GROUND, GROUND + 3);
        var r = VehicleCollision.move(wall, 0.5, GROUND, 0, 0, 5.5, 5);
        assertTrue(r.blocked());
        assertEquals(5, r.z(), 1e-9);
        assertEquals(2.05, r.x(), 0.01); // the wall at x = 3 minus half of the 1.9 width
    }

    @Test
    void aStepIsClimbedWhateverTheDirectionItIsClimbedIn() {
        FakeTerrain west = flat().solid(-20, -10, -5, 5, GROUND, GROUND + 0.5);
        var r = VehicleCollision.move(west, 3, GROUND, 0.5, 90, -15, 0.5); // yaw 90 faces -x
        assertFalse(r.blocked());
        assertEquals(GROUND + 0.5, r.y(), 1e-9);
        assertEquals(-15, r.x(), 1e-9);
        FakeTerrain diagonal = flat().solid(-5, 30, 10, 30, GROUND, GROUND + 0.5);
        var d = VehicleCollision.move(diagonal, 0.5, GROUND, 3, 0, 8.5, 20);
        assertFalse(d.blocked());
        assertEquals(GROUND + 0.5, d.y(), 1e-9);
    }

    @Test
    void touchingAWallIsNotBlocked() {
        // the right edge of the box (1.9 wide) at x = 3.0 exactly, along a wall whose face is at x = 3
        FakeTerrain side = flat().solid(3, 3, -50, 50, GROUND, GROUND + 3);
        var along = VehicleCollision.move(side, 2.05, GROUND, 0, 0, 2.05, 10);
        assertFalse(along.blocked());
        assertEquals(10, along.z(), 1e-9);
        // the front edge (2.45 from the centre) at z = 10.0 exactly, sliding sideways in front of a wall at z = 10
        FakeTerrain front = flat().solid(-50, 50, 10, 10, GROUND, GROUND + 3);
        var across = VehicleCollision.move(front, 0.5, GROUND, 7.55, 0, 5.5, 7.55);
        assertFalse(across.blocked());
        assertEquals(5.5, across.x(), 1e-9);
    }

    @Test
    void aLowCeilingStopsItAndOneOfTheCarsHeightDoesNot() {
        FakeTerrain low = flat().solid(-5, 5, 10, 10, GROUND + 1.4, GROUND + 2);
        assertTrue(VehicleCollision.move(low, 0.5, GROUND, 3, 0, 0.5, 15).blocked());
        FakeTerrain enough = flat().solid(-5, 5, 10, 10, GROUND + 1.5, GROUND + 2);
        var r = VehicleCollision.move(enough, 0.5, GROUND, 3, 0, 0.5, 15);
        assertFalse(r.blocked());
        assertEquals(15, r.z(), 1e-9);
    }

    @Test
    void aCeilingThatLeavesExactlyTheCarsHeightLetsItThrough() {
        FakeTerrain exact = flat().solid(-5, 5, 10, 10, GROUND + 1.5, GROUND + 2);
        assertFalse(VehicleCollision.move(exact, 0.5, GROUND, 3, 0, 0.5, 15).blocked());
        FakeTerrain tight = flat().solid(-5, 5, 10, 10, GROUND + 1.4375, GROUND + 2);
        assertTrue(VehicleCollision.move(tight, 0.5, GROUND, 3, 0, 0.5, 15).blocked());
    }

    @Test
    void aSlenderPostBetweenTheCornersStillStopsIt() {
        FakeTerrain post = flat().solid(0, 0, 10, 10, GROUND, GROUND + 3); // x in [0, 1): between the centre line and the right edge of the car at x 0.5? the car covers [-0.45, 1.45]
        var r = VehicleCollision.move(post, 0.5, GROUND, 3, 0, 0.5, 20);
        assertTrue(r.blocked());
        assertEquals(7.55, r.z(), 0.01);
    }

    @Test
    void aTurnedBoxNeverGoesIntoTheWall() {
        FakeTerrain wall = flat().solid(-30, 30, 10, 10, GROUND, GROUND + 3);
        for (double yaw : new double[] {0, 20, 45, 70, 135, -150}) {
            var r = VehicleCollision.move(wall, 0.5, GROUND, 3, yaw, 0.5 - 20 * Math.sin(Math.toRadians(yaw)), 3 + 20 * Math.cos(Math.toRadians(yaw)));
            double[] u = VehicleCollision.forward(yaw);
            double[] v = VehicleCollision.right(yaw);
            double maxZ = Double.NEGATIVE_INFINITY;
            for (int a = -1; a <= 1; a += 2) {
                for (int b = -1; b <= 1; b += 2) {
                    maxZ = Math.max(maxZ, r.z() + a * VehicleCollision.LENGTH / 2 * u[1] + b * VehicleCollision.WIDTH / 2 * v[1]);
                }
            }
            if (Math.cos(Math.toRadians(yaw)) > 0) {
                assertTrue(maxZ <= 10 + 0.01, "yaw " + yaw + " reaches " + maxZ);
                assertTrue(r.blocked(), "yaw " + yaw);
            }
        }
    }

    @Test
    void theWidthIsWhatMattersForAWallBesideIt() {
        // yaw 0: the box is 1.9 wide in x. Facing +z beside a wall at x = 3: it can sit with its edge at x = 3
        FakeTerrain wall = flat().solid(3, 3, -50, 50, GROUND, GROUND + 3);
        assertFalse(VehicleCollision.move(wall, 1.0, GROUND, 0, 0, 1.0, 10).blocked()); // right edge 1.95 < 3
        // yaw 90: it is 4.9 long in x, so the same place is inside the wall's column strip
        assertTrue(VehicleCollision.move(wall, 1.0, GROUND, 0, 90, 1.0, 10).blocked());
    }

    @Test
    void whenItAlreadyDoesNotFitItFitsIsFalseAndNothingMoves() {
        FakeTerrain wall = flat().solid(0, 0, 0, 0, GROUND, GROUND + 3);
        assertFalse(VehicleCollision.fits(wall, 0.5, GROUND, 0.5, 0));
        assertTrue(VehicleCollision.fits(flat(), 0.5, GROUND, 0.5, 0));
        assertTrue(VehicleCollision.move(wall, 0.5, GROUND, 0.5, 0, 5, 5).blocked());
    }

    @Test
    void itNeverGoesDownOnItsOwnWhileMoving() {
        FakeTerrain pit = flat().hole(-5, 5, 5, 20);
        var r = VehicleCollision.move(pit, 0.5, GROUND, 0, 0, 0.5, 15);
        assertEquals(GROUND, r.y(), 1e-9);
        assertEquals(15, r.z(), 1e-9);
        assertFalse(r.blocked());
    }

    @Test
    void brokenNumbersLeaveItWhereItIsAndBlocked() {
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            for (int which = 0; which < 6; which++) {
                double[] a = {0.5, GROUND, 3, 0, 0.5, 6};
                a[which] = bad;
                var r = VehicleCollision.move(flat(), a[0], a[1], a[2], a[3], a[4], a[5]);
                assertTrue(r.blocked());
                assertEquals(a[0], r.x());
                assertEquals(a[1], r.y());
                assertEquals(a[2], r.z());
            }
        }
        assertFalse(VehicleCollision.fits(flat(), Double.NaN, GROUND, 0, 0));
    }
}
