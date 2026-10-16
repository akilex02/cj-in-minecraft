package dev.cjmc.skate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkateInputTest {
    // GLFW's gamepad layout
    private static final int A = 0, B = 1, X = 2, Y = 3, LB = 4, RB = 5, LSB = 9, RSB = 10, UP = 11, RIGHT = 12, DOWN = 13, LEFT = 14;
    // the engine's action indices
    private static final int LX = 0, LY = 1, LCLICK = 2, RX = 3, RY = 4, RCLICK = 5, LT = 6, RT = 7, LBUMP = 8, RBUMP = 9, DUP = 10, DDOWN = 11, DLEFT = 12,
            DRIGHT = 13, BX = 14, BY = 15, BA = 16, BB = 17;

    private static float[] rest() {
        return new float[] {0, 0, 0, 0, -1, -1}; // the sticks centred and the triggers released (GLFW rests them at -1)
    }

    private static float[] map(float[] axes, int... pressed) {
        boolean[] buttons = new boolean[15];
        for (int b : pressed) {
            buttons[b] = true;
        }
        return SkateInput.map(axes, buttons);
    }

    @Test
    void aControllerAtRestIsNoInput() {
        float[] actions = map(rest());
        assertEquals(18, actions.length);
        for (float a : actions) {
            assertEquals(0f, a);
        }
    }

    @Test
    void theSticksGoUpWithTheValueOfTheEngineNotOfGlfw() {
        float[] axes = rest();
        axes[0] = 1f; // left stick right
        assertEquals(1f, map(axes)[LX]);
        axes[0] = 0f;
        axes[1] = -1f; // GLFW: up is negative; the engine's up is positive
        assertEquals(1f, map(axes)[LY]);
        axes[1] = 1f; // down
        assertEquals(-1f, map(axes)[LY]);
        axes[1] = 0f;
        axes[2] = -1f; // right stick left
        assertEquals(-1f, map(axes)[RX]);
        axes[2] = 0f;
        axes[3] = -1f; // right stick up
        assertEquals(1f, map(axes)[RY]);
    }

    @Test
    void aStickInsideTheDeadZoneIsStillAndOutsideItIsRescaled() {
        float[] axes = rest();
        axes[0] = 0.1f;
        assertEquals(0f, map(axes)[LX]);
        axes[0] = 0.5f;
        assertEquals((0.5f - 0.15f) / (1f - 0.15f), map(axes)[LX], 1e-5f);
        axes[0] = 1f;
        assertEquals(1f, map(axes)[LX], 1e-5f);
    }

    @Test
    void aDiagonalNeverGoesPastTheUnitCircle() {
        float[] axes = rest();
        axes[0] = 1f;
        axes[1] = 1f;
        float[] actions = map(axes);
        assertTrue(Math.hypot(actions[LX], actions[LY]) <= 1.0001, "the stick is on the unit circle");
        assertEquals(actions[LX], -actions[LY], 1e-5f);
    }

    @Test
    void theTriggersGoFromGlfwsRangeToZeroToOne() {
        float[] axes = rest();
        axes[4] = 1f;
        axes[5] = 0f; // half pressed
        float[] actions = map(axes);
        assertEquals(1f, actions[LT], 1e-5f);
        assertEquals(0.5f, actions[RT], 1e-5f);
        axes[4] = -0.98f; // nearly released: a trigger that does not come back all the way
        assertEquals(0f, map(axes)[LT]);
    }

    @Test
    void everyButtonIsTheActionOfTheEnginesFace() {
        assertEquals(1f, map(rest(), A)[BA]);
        assertEquals(1f, map(rest(), B)[BB]);
        assertEquals(1f, map(rest(), X)[BX]);
        assertEquals(1f, map(rest(), Y)[BY]);
        assertEquals(1f, map(rest(), LB)[LBUMP]);
        assertEquals(1f, map(rest(), RB)[RBUMP]);
        assertEquals(1f, map(rest(), LSB)[LCLICK]);
        assertEquals(1f, map(rest(), RSB)[RCLICK]);
        assertEquals(1f, map(rest(), UP)[DUP]);
        assertEquals(1f, map(rest(), DOWN)[DDOWN]);
        assertEquals(1f, map(rest(), LEFT)[DLEFT]);
        assertEquals(1f, map(rest(), RIGHT)[DRIGHT]);
        // and nothing else moves with one of them
        float[] a = map(rest(), A);
        for (int i = 0; i < 18; i++) {
            assertEquals(i == BA ? 1f : 0f, a[i]);
        }
    }

    @Test
    void shortArraysOfAnOddDeviceGiveNoInputInsteadOfFailing() {
        float[] actions = SkateInput.map(new float[2], new boolean[3]);
        assertEquals(18, actions.length);
        for (float a : actions) {
            assertEquals(0f, a);
        }
    }
}
