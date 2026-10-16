package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CarCameraTest {
    @Test
    void startsAtTheNormalDistanceAndMovesBackWhileDriving() {
        CarCamera c = new CarCamera();
        assertEquals(0.0, c.step(false, 0.1));
        double half = c.step(true, 0.2);
        assertTrue(half > 0 && half < 1);
        assertEquals(1.0, c.step(true, 5.0), 1e-9);
    }

    @Test
    void comesBackWhenTheDrivingStopsAndNeverGoesBelowZero() {
        CarCamera c = new CarCamera();
        c.step(true, 5.0);
        double back = c.step(false, 0.1);
        assertTrue(back > 0 && back < 1);
        assertEquals(0.0, c.step(false, 5.0));
        assertEquals(0.0, c.step(false, 5.0));
    }

    @Test
    void aHugeOrBrokenFrameTimeDoesNotJump() {
        assertEquals(0.0, new CarCamera().step(true, Double.NaN));
        assertEquals(0.0, new CarCamera().step(true, -3));
        assertTrue(new CarCamera().step(true, 1e9) <= 1.0);
    }

    @Test
    void theDistanceGoesFromWhatTheGameChoseToTheDistanceOfTheCar() {
        assertEquals(4.0, CarCamera.distanceFor(4.0, 0.0), 1e-9);
        assertEquals(CarCamera.DISTANCE, CarCamera.distanceFor(4.0, 1.0), 1e-9);
        assertEquals((4.0 + CarCamera.DISTANCE) / 2, CarCamera.distanceFor(4.0, 0.5), 1e-9);
    }

    @Test
    void theCameraIsNeverBroughtCloserThanTheGameHadIt() {
        // a player who set the camera farther than the car's distance keeps it
        assertEquals(12.0, CarCamera.distanceFor(12.0, 1.0), 1e-9);
    }
}
