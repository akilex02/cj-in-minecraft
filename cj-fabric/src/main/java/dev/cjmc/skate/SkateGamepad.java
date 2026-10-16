package dev.cjmc.skate;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;

/** The first gamepad GLFW knows (its standard layout), as the 18 actions of the engine. Main thread only (GLFW). */
final class SkateGamepad {
    private SkateGamepad() {}

    /** The actions, or null when no gamepad is connected. */
    static float[] poll() {
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (!GLFW.glfwJoystickPresent(jid) || !GLFW.glfwJoystickIsGamepad(jid)) {
                continue;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                GLFWGamepadState state = GLFWGamepadState.malloc(stack);
                if (!GLFW.glfwGetGamepadState(jid, state)) {
                    continue;
                }
                float[] axes = new float[SkateInput.AXES];
                for (int i = 0; i < axes.length; i++) {
                    axes[i] = state.axes(i);
                }
                boolean[] buttons = new boolean[SkateInput.BUTTONS];
                for (int i = 0; i < buttons.length; i++) {
                    buttons[i] = state.buttons(i) != 0;
                }
                return SkateInput.map(axes, buttons);
            }
        }
        return null;
    }
}
