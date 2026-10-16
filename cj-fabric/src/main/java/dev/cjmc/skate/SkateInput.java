package dev.cjmc.skate;

/**
 * A gamepad (in GLFW's standard layout) as the 18 actions the Skate engine reads: the two sticks, their clicks, the triggers, the bumpers, the D-pad and
 * the four face buttons, in the order of skate-core's gameplay_map. Pure: the client copies the GLFW state into the two arrays.
 */
public final class SkateInput {
    /** GLFW's gamepad axes. */
    private static final int AXIS_LEFT_X = 0, AXIS_LEFT_Y = 1, AXIS_RIGHT_X = 2, AXIS_RIGHT_Y = 3, AXIS_LEFT_TRIGGER = 4, AXIS_RIGHT_TRIGGER = 5;
    /** GLFW's gamepad buttons. */
    private static final int BTN_A = 0, BTN_B = 1, BTN_X = 2, BTN_Y = 3, BTN_LB = 4, BTN_RB = 5, BTN_LSB = 9, BTN_RSB = 10, BTN_UP = 11, BTN_RIGHT = 12, BTN_DOWN = 13,
            BTN_LEFT = 14;
    /** The engine's actions. */
    private static final int LX = 0, LY = 1, LCLICK = 2, RX = 3, RY = 4, RCLICK = 5, LT = 6, RT = 7, LBUMPER = 8, RBUMPER = 9, DUP = 10, DDOWN = 11, DLEFT = 12,
            DRIGHT = 13, FACE_X = 14, FACE_Y = 15, FACE_A = 16, FACE_B = 17;

    public static final int AXES = 6;
    public static final int BUTTONS = 15;
    static final float STICK_DEAD_ZONE = 0.15f;
    static final float TRIGGER_DEAD_ZONE = 0.05f;

    private SkateInput() {}

    /** `axes` has the 6 axes and `buttons` the 15 buttons of GLFW's gamepad; shorter arrays read as released. */
    public static float[] map(float[] axes, boolean[] buttons) {
        float[] out = new float[SkateProtocol.ACTIONS];
        stick(axis(axes, AXIS_LEFT_X, 0f), -axis(axes, AXIS_LEFT_Y, 0f), out, LX, LY); // GLFW has up negative; the engine has it positive
        stick(axis(axes, AXIS_RIGHT_X, 0f), -axis(axes, AXIS_RIGHT_Y, 0f), out, RX, RY);
        out[LT] = trigger(axis(axes, AXIS_LEFT_TRIGGER, -1f));
        out[RT] = trigger(axis(axes, AXIS_RIGHT_TRIGGER, -1f));
        out[LCLICK] = button(buttons, BTN_LSB);
        out[RCLICK] = button(buttons, BTN_RSB);
        out[LBUMPER] = button(buttons, BTN_LB);
        out[RBUMPER] = button(buttons, BTN_RB);
        out[DUP] = button(buttons, BTN_UP);
        out[DDOWN] = button(buttons, BTN_DOWN);
        out[DLEFT] = button(buttons, BTN_LEFT);
        out[DRIGHT] = button(buttons, BTN_RIGHT);
        out[FACE_X] = button(buttons, BTN_X);
        out[FACE_Y] = button(buttons, BTN_Y);
        out[FACE_A] = button(buttons, BTN_A);
        out[FACE_B] = button(buttons, BTN_B);
        return out;
    }

    private static float axis(float[] axes, int index, float fallback) {
        return index < axes.length && Float.isFinite(axes[index]) ? axes[index] : fallback;
    }

    private static float button(boolean[] buttons, int index) {
        return index < buttons.length && buttons[index] ? 1f : 0f;
    }

    /** A radial dead zone, rescaled so the edge of it is zero and the rim is one, and never outside the unit circle. */
    private static void stick(float x, float y, float[] out, int ix, int iy) {
        double magnitude = Math.hypot(x, y);
        if (magnitude < STICK_DEAD_ZONE) {
            return;
        }
        double scale = (Math.min(magnitude, 1.0) - STICK_DEAD_ZONE) / (1.0 - STICK_DEAD_ZONE) / magnitude;
        out[ix] = (float) (x * scale);
        out[iy] = (float) (y * scale);
    }

    /** GLFW rests a trigger at -1 and presses it to 1; the engine wants 0 to 1. */
    private static float trigger(float glfw) {
        float value = Math.max(0f, Math.min(1f, (glfw + 1f) / 2f));
        return value < TRIGGER_DEAD_ZONE ? 0f : value;
    }
}
