package dev.cjmc;

/**
 * In third person (as CJ) the jump key reaches the game TICKS ticks late: in GTA the jump starts a little after the key, and Minecraft
 * would jump at once. Tuned by the user by trying it.
 */
public final class JumpDelay {
    public static final int TICKS = 2; // 0.10 s at 20 ticks a second (0.5, 0.2 and 0.15 s were too late)

    private static final DelayedSignal SIGNAL = new DelayedSignal(TICKS);
    private static boolean wasActive;
    private static volatile boolean rawPressed;

    private JumpDelay() {}

    /** The jump signal of this tick: delayed while `active` (CJ drawn in third person), untouched otherwise; the wait restarts each time it becomes active. */
    public static boolean apply(boolean rawJump, boolean active) {
        rawPressed = rawJump;
        if (!active) {
            if (wasActive) {
                SIGNAL.reset();
                wasActive = false;
            }
            return rawJump;
        }
        wasActive = true;
        return SIGNAL.push(rawJump);
    }

    /** The jump key as it is pressed now, before the delay (what CJ in GTA has to start jumping on). */
    public static boolean rawPressed() {
        return rawPressed;
    }

    static void resetForTests() {
        rawPressed = false;
        SIGNAL.reset();
        wasActive = false;
    }
}
