package dev.cjmc.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VehicleTerrainTest {
    private static final double GROUND = 64;

    @Test
    void onTheGroundOrWithinAStepOfItTheCarSticksToIt() {
        var s = VehicleTerrain.settle(new FakeTerrain(GROUND), 0.5, GROUND, 0.5, 0, 0);
        assertTrue(s.grounded());
        assertEquals(GROUND, s.y(), 1e-9);
        var near = VehicleTerrain.settle(new FakeTerrain(GROUND), 0.5, GROUND + 0.99609375, 0.5, 0, -0.3);
        assertTrue(near.grounded());
        assertEquals(GROUND, near.y(), 1e-9);
        assertEquals(0, near.vy());
    }

    @Test
    void justOverAStepAboveTheGroundItFalls() {
        var s = VehicleTerrain.settle(new FakeTerrain(GROUND), 0.5, GROUND + 1.0078125, 0.5, 0, 0);
        assertFalse(s.grounded());
        assertTrue(s.y() < GROUND + 1.0078125);
        assertEquals(-0.0784, s.vy(), 1e-12); // (0 - 0.08) * 0.98
        var high = VehicleTerrain.settle(new FakeTerrain(GROUND), 0.5, GROUND + 3, 0.5, 0, -0.0784);
        assertEquals((-0.0784 - 0.08) * 0.98, high.vy(), 1e-12);
        assertEquals(GROUND + 3 + high.vy(), high.y(), 1e-12);
    }

    @Test
    void aFallAcceleratesAndStopsAtTheGround() {
        FakeTerrain world = new FakeTerrain(GROUND);
        double y = GROUND + 20;
        double vy = 0;
        double previousSpeed = 0;
        boolean landed = false;
        for (int i = 0; i < 200 && !landed; i++) {
            var s = VehicleTerrain.settle(world, 0.5, y, 0.5, 0, vy);
            assertTrue(s.y() >= GROUND - 1e-9, "never under the ground");
            if (!s.grounded()) {
                assertTrue(s.vy() < previousSpeed || i == 0, "it speeds up");
                previousSpeed = s.vy();
            }
            y = s.y();
            vy = s.vy();
            landed = s.grounded();
        }
        assertTrue(landed);
        assertEquals(GROUND, y, 1e-9);
    }

    @Test
    void withNoGroundAtAllItKeepsFalling() {
        FakeTerrain world = new FakeTerrain(GROUND).hole(-10, 10, -10, 10);
        double y = GROUND;
        double vy = 0;
        for (int i = 0; i < 50; i++) {
            var s = VehicleTerrain.settle(world, 0.5, y, 0.5, 0, vy);
            assertFalse(s.grounded());
            assertTrue(s.y() < y);
            y = s.y();
            vy = s.vy();
        }
    }

    @Test
    void theCarRestsOnTheHighestGroundUnderIt() {
        FakeTerrain world = new FakeTerrain(GROUND).solid(0, 0, 0, 0, GROUND, GROUND + 0.5);
        var s = VehicleTerrain.settle(world, 0.5, GROUND + 0.5, 0.5, 0, 0);
        assertTrue(s.grounded());
        assertEquals(GROUND + 0.5, s.y(), 1e-9);
        // a block of the next column that the box does not cover does not lift it
        FakeTerrain beside = new FakeTerrain(GROUND).solid(5, 5, 0, 0, GROUND, GROUND + 0.5);
        assertEquals(GROUND, VehicleTerrain.settle(beside, 0.5, GROUND, 0.5, 0, 0).y(), 1e-9);
    }

    @Test
    void aBrokenNumberDoesNotMoveIt() {
        var s = VehicleTerrain.settle(new FakeTerrain(GROUND), Double.NaN, GROUND + 5, 0, 0, 0);
        assertEquals(GROUND + 5, s.y());
        assertFalse(s.grounded());
        assertFalse(VehicleTerrain.settle(new FakeTerrain(GROUND), 0, GROUND, 0, 0, Double.NaN).grounded());
        var yaw = VehicleTerrain.settle(new FakeTerrain(GROUND), 0.5, GROUND + 5, 0.5, Double.NaN, -0.2);
        assertEquals(GROUND + 5, yaw.y());
        assertEquals(0, yaw.vy());
        assertFalse(yaw.grounded());
    }

    @Test
    void onLevelGroundThereIsNoPitchOrRoll() {
        double[] pr = VehicleTerrain.pitchRoll(new FakeTerrain(GROUND), 0.5, GROUND, 0.5, 0);
        assertEquals(0, pr[0], 1e-12);
        assertEquals(0, pr[1], 1e-12);
    }

    @Test
    void raisedFrontWheelsPitchTheNoseUp() {
        // yaw 0 faces +z: the front axle is at z + 1.705 (cell 2 from the centre 0.5), the rear at z - 1.705 (cell -2)
        FakeTerrain world = new FakeTerrain(GROUND).solid(-5, 5, 1, 5, GROUND, GROUND + 0.5);
        double[] pr = VehicleTerrain.pitchRoll(world, 0.5, GROUND + 0.5, 0.5, 0);
        assertEquals(Math.atan2(0.5, 3.41), pr[0], 1e-9);
        assertEquals(0, pr[1], 1e-12);
        // facing the other way the same ramp tips the nose down
        double[] back = VehicleTerrain.pitchRoll(world, 0.5, GROUND + 0.5, 0.5, 180);
        assertEquals(-Math.atan2(0.5, 3.41), back[0], 1e-9);
    }

    @Test
    void thePitchFollowsTheCarWhicheverWayItFaces() {
        // yaw 90 faces -x: the front axle is at x - 1.705
        FakeTerrain west = new FakeTerrain(GROUND).solid(-5, -1, -5, 5, GROUND, GROUND + 0.5);
        double[] pr = VehicleTerrain.pitchRoll(west, 0.5, GROUND + 0.5, 0.5, 90);
        assertEquals(Math.atan2(0.5, 3.41), pr[0], 1e-9);
        assertEquals(0, pr[1], 1e-9);
        // yaw -90 faces +x: the same ramp is behind the car
        assertEquals(-Math.atan2(0.5, 3.41), VehicleTerrain.pitchRoll(west, 0.5, GROUND + 0.5, 0.5, -90)[0], 1e-9);
        // yaw 90 faces -x: the right hand is -z, so a ramp to the south is on the left and tips the car to its left
        FakeTerrain south = new FakeTerrain(GROUND).solid(-5, 5, 1, 5, GROUND, GROUND + 0.5);
        assertEquals(-Math.atan2(0.5, 1.85), VehicleTerrain.pitchRoll(south, 0.5, GROUND + 0.5, 0.5, 90)[1], 1e-9);
    }

    @Test
    void raisedRightWheelsRollTheRightSideUp() {
        // yaw 0: the right hand is -x
        FakeTerrain world = new FakeTerrain(GROUND).solid(-5, 0, -5, 5, GROUND, GROUND + 0.5);
        double[] pr = VehicleTerrain.pitchRoll(world, 0.5, GROUND + 0.5, 0.5, 0);
        assertEquals(Math.atan2(0.5, 1.85), pr[1], 1e-9);
        assertEquals(0, pr[0], 1e-12);
    }

    @Test
    void aWheelOverNoGroundCountsAsLevelWithTheCar() {
        FakeTerrain world = new FakeTerrain(GROUND).hole(-5, 5, 1, 5);
        double[] pr = VehicleTerrain.pitchRoll(world, 0.5, GROUND, 0.5, 0);
        assertEquals(0.0, pr[0], 1e-9);
    }

    @Test
    void theSmootherApproachesTheTargetWithoutPassingIt() {
        VehicleTerrain.Smoother s = new VehicleTerrain.Smoother();
        s.update(0.2, -0.1);
        assertEquals(0.06, s.pitch(), 1e-12); // 30% of the way
        assertEquals(-0.03, s.roll(), 1e-12);
        double last = s.pitch();
        for (int i = 0; i < 100; i++) {
            s.update(0.2, -0.1);
            assertTrue(s.pitch() >= last && s.pitch() <= 0.2);
            assertTrue(s.roll() >= -0.1 - 1e-12 && s.roll() <= 0);
            last = s.pitch();
        }
        assertEquals(0.2, s.pitch(), 1e-6);
        assertEquals(-0.1, s.roll(), 1e-6);
        s.update(Double.NaN, 0);
        assertEquals(0.2, s.pitch(), 1e-6);
        s.update(0, Double.NaN);
        assertEquals(0.2, s.pitch(), 1e-6);
        s.reset();
        assertEquals(0, s.pitch());
        assertEquals(0, s.roll());
    }
}
