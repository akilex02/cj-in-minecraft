package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CrosshairMathTest {
    @Test
    void straightAheadIsTheCentre() {
        float[] p = CrosshairMath.project(0, 0, -10, 70, 800, 600);
        assertEquals(400f, p[0], 1e-3f);
        assertEquals(300f, p[1], 1e-3f);
    }

    @Test
    void theTopEdgeOfTheFieldOfViewIsTheTopOfTheScreen() {
        double t = Math.tan(Math.toRadians(35));
        float[] p = CrosshairMath.project(0, 10 * t, -10, 70, 800, 600);
        assertEquals(0f, p[1], 1e-2f);
        assertEquals(400f, p[0], 1e-3f);
    }

    @Test
    void theRightEdgeUsesTheAspectRatio() {
        double t = Math.tan(Math.toRadians(35)) * 800.0 / 600.0;
        float[] p = CrosshairMath.project(10 * t, 0, -10, 70, 800, 600);
        assertEquals(800f, p[0], 1e-2f);
        assertEquals(300f, p[1], 1e-3f);
    }

    @Test
    void leftIsLeftAndDownIsDown() {
        float[] p = CrosshairMath.project(-1, -1, -10, 70, 800, 600);
        assertEquals(true, p[0] < 400f && p[1] > 300f);
    }

    @Test
    void behindTheCameraOrBadInputsGiveNothing() {
        assertNull(CrosshairMath.project(0, 0, 5, 70, 800, 600));
        assertNull(CrosshairMath.project(0, 0, 0, 70, 800, 600));
        assertNull(CrosshairMath.project(Double.NaN, 0, -1, 70, 800, 600));
        assertNull(CrosshairMath.project(0, 0, -1, 0, 800, 600));
        assertNull(CrosshairMath.project(0, 0, -1, 70, 0, 600));
        assertNull(CrosshairMath.project(0, 0, -1, 180, 800, 600));
    }
}
