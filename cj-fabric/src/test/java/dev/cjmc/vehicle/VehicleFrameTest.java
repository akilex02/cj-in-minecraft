package dev.cjmc.vehicle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VehicleFrameTest {
    private static final double EPS = 1e-9;

    private static void assertPair(double[] expected, double[] actual) {
        assertArrayEquals(expected, actual, EPS);
    }

    @Test
    void withoutMovementTheCarIsAtTheAnchor() {
        VehicleFrame f = VehicleFrame.anchor(10.5, -3.25, 90, 1500, -2500, 0.7);
        assertPair(new double[] {10.5, -3.25}, f.mcPosition(1500, -2500));
        assertEquals(90, f.mcYawDegrees(0.7), EPS);
    }

    @Test
    void goingForwardIsGoingForwardWhicheverWayEachWorldIsOriented() {
        // GTA north, Minecraft south: 5 metres north are 5 blocks south
        assertPair(new double[] {0, 5}, VehicleFrame.anchor(0, 0, 0, 0, 0, 0).mcPosition(0, 5));
        // Minecraft west (yaw 90): forward is -x
        assertPair(new double[] {-5, 0}, VehicleFrame.anchor(0, 0, 90, 0, 0, 0).mcPosition(0, 5));
        // GTA heading pi/2 faces west (-x): moving -5 in x is moving forward, and with yaw 0 forward is +z
        assertPair(new double[] {0, 5}, VehicleFrame.anchor(0, 0, 0, 0, 0, Math.PI / 2).mcPosition(-5, 0));
    }

    @Test
    void movingToTheRightOfTheCarIsMovingToTheRightOfThePlayer() {
        // facing south, the right hand is west: GTA +x (east) with heading 0 is the right of the car
        assertPair(new double[] {-5, 0}, VehicleFrame.anchor(0, 0, 0, 0, 0, 0).mcPosition(5, 0));
        // the same with the anchor offset: the frame translates
        assertPair(new double[] {95, 202}, VehicleFrame.anchor(100, 200, 0, 40, 60, 0).mcPosition(45, 62));
    }

    @Test
    void turningRightIsAnIncreasingMinecraftYaw() {
        VehicleFrame f = VehicleFrame.anchor(0, 0, 30, 0, 0, 1.0);
        assertEquals(120, f.mcYawDegrees(1.0 - Math.PI / 2), EPS);
        assertEquals(-60, f.mcYawDegrees(1.0 + Math.PI / 2), EPS);
    }

    @Test
    void theYawIsNormalisedToTheHalfOpenRangeFromMinus180() {
        VehicleFrame f = VehicleFrame.anchor(0, 0, 170, 0, 0, 0);
        assertEquals(-170, f.mcYawDegrees(-Math.toRadians(20)), 1e-7);  // 190 wraps to -170
        assertEquals(180, VehicleFrame.anchor(0, 0, 0, 0, 0, 0).mcYawDegrees(-Math.PI), 1e-7);  // 180 stays (the range includes +180)
        assertEquals(180, VehicleFrame.anchor(0, 0, 0, 0, 0, 0).mcYawDegrees(Math.PI), 1e-7);   // -180 is the same direction
        assertTrue(VehicleFrame.anchor(0, 0, 0, 0, 0, 0).mcYawDegrees(10 * Math.PI + 0.1) > -180);
    }

    @Test
    void aCorrectionIsAMinecraftDisplacementInGtaAxes() {
        for (double h0 : new double[] {0, 0.7, Math.PI / 2, 3.0, -2.0}) {
            for (double yaw0 : new double[] {0, 45, 90, -135, 180}) {
                VehicleFrame f = VehicleFrame.anchor(12, -7, yaw0, 1500, -2500, h0);
                double[][] points = {{1503, -2490}, {1490, -2511}, {1500, -2500}, {1512.5, -2480.25}};
                for (double[] p : points) {
                    for (double[] q : points) {
                        double[] a = f.mcPosition(p[0], p[1]);
                        double[] b = f.mcPosition(q[0], q[1]);
                        assertPair(new double[] {p[0] - q[0], p[1] - q[1]}, f.gtaCorrection(a[0] - b[0], a[1] - b[1]));
                    }
                }
            }
        }
        assertPair(new double[] {0, 0}, VehicleFrame.anchor(0, 0, 30, 0, 0, 1).gtaCorrection(0, 0));
    }

    @Test
    void aCorrectionUsesTheRightAxes() {
        // heading 0, yaw 0: 1 block south (+z) is 1 metre north (+y); 1 block west (-x) is 1 metre east (+x)
        VehicleFrame f = VehicleFrame.anchor(0, 0, 0, 0, 0, 0);
        assertPair(new double[] {0, 1}, f.gtaCorrection(0, 1));
        assertPair(new double[] {1, 0}, f.gtaCorrection(-1, 0));
    }

    @Test
    void brokenNumbersGiveTheAnchorAndNeverNaN() {
        VehicleFrame f = VehicleFrame.anchor(10, 20, 45, 100, 200, 0.5);
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertPair(new double[] {10, 20}, f.mcPosition(bad, 200));
            assertPair(new double[] {10, 20}, f.mcPosition(100, bad));
            assertEquals(45, f.mcYawDegrees(bad), EPS);
            assertPair(new double[] {0, 0}, f.gtaCorrection(bad, 1));
            assertPair(new double[] {0, 0}, f.gtaCorrection(1, bad));
        }
    }
}
