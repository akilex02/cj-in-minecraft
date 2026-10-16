package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JumpIntentTest {
    // wanted(rawPressed, cjActive, onGround, canJump, frozen, flying, riding, inFluid, climbing)
    private static boolean ok() {
        return JumpIntent.wanted(true, true, true, true, false, false, false, false, false);
    }

    @Test
    void aPressedKeyOnTheGroundAsCjAnnouncesTheJump() {
        assertTrue(ok());
    }

    @Test
    void noKeyNoIntent() {
        assertFalse(JumpIntent.wanted(false, true, true, true, false, false, false, false, false));
    }

    @Test
    void firstPersonOrNoCjMeansNoIntent() {
        assertFalse(JumpIntent.wanted(true, false, true, true, false, false, false, false, false));
    }

    @Test
    void inTheAirNoIntentBecauseMinecraftDoesNotJumpThere() {
        assertFalse(JumpIntent.wanted(true, true, false, true, false, false, false, false, false));
    }

    @Test
    void whereMinecraftWillNotJumpNoIntent() {
        assertFalse(JumpIntent.wanted(true, true, true, false, false, false, false, false, false), "the weapon forbids it (the minigun)");
        assertFalse(JumpIntent.wanted(true, true, true, true, true, false, false, false, false), "frozen: knocked down");
        assertFalse(JumpIntent.wanted(true, true, true, true, false, true, false, false, false), "flying");
        assertFalse(JumpIntent.wanted(true, true, true, true, false, false, true, false, false), "riding");
        assertFalse(JumpIntent.wanted(true, true, true, true, false, false, false, true, false), "in a fluid: the key swims up");
        assertFalse(JumpIntent.wanted(true, true, true, true, false, false, false, false, true), "climbing");
    }
}
