package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.cjmc.vehicle.VehicleSession;
import org.junit.jupiter.api.Test;

class CarViewTest {
    private static VehicleSession.Pose pose(double yaw) {
        return new VehicleSession.Pose(10, 64, -5, yaw, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void theHeadIsOverTheSeatAboveTheCentreOfTheCar() {
        // the car's origin is ORIGIN_ABOVE_GROUND over the player's spot; a seat at the centre and the head 0.6 over it
        float[] at = CarView.headOffset(pose(0), new float[] {0, 0, 0}, 0f, new float[] {0, 0.6f, 0});
        assertArrayEquals(new float[] {0, (float) (VehicleSession.ORIGIN_ABOVE_GROUND + 0.6), 0}, at, 1e-5f);
    }

    @Test
    void aSeatAheadInACarFacingWestIsWestOfItsCentre() {
        // yaw 90 faces -x in Minecraft: a seat 1 ahead (+z of the car) is 1 toward -x
        float[] at = CarView.headOffset(pose(90), new float[] {0, 0, 1}, 0f, new float[] {0, 0, 0});
        assertArrayEquals(new float[] {-1, (float) VehicleSession.ORIGIN_ABOVE_GROUND, 0}, at, 1e-5f);
    }

    @Test
    void theViewTurnsAsMuchAsTheCarTheShortWayRound() {
        assertEquals(5f, CarView.yawDelta(10f, 15f), 1e-5f);
        assertEquals(-5f, CarView.yawDelta(15f, 10f), 1e-5f);
        assertEquals(20f, CarView.yawDelta(170f, -170f), 1e-5f); // through 180, not 340 back
    }
}
