package dev.cjmc;

/** The "CJ bridge" line on the screen can be hidden and shown with a key: whether it is shown, and the one moment it has to be cleared away. */
public final class InfoSwitch {
    private boolean visible = true;
    private boolean clearPending;

    public boolean visible() {
        return visible;
    }

    /** `presses` presses of the key this tick (an odd number switches it). */
    public void press(int presses) {
        if (presses > 0 && (presses & 1) == 1) {
            visible = !visible;
            clearPending = !visible;
        }
    }

    /** True once after it was hidden: the text that is still on the screen has to be taken off. */
    public boolean takeClear() {
        boolean clear = clearPending;
        clearPending = false;
        return clear;
    }
}
