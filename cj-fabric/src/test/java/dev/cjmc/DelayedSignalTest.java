package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DelayedSignalTest {
    private static String run(DelayedSignal s, String input) {
        StringBuilder out = new StringBuilder();
        for (char c : input.toCharArray()) {
            out.append(s.push(c == 'T') ? 'T' : '.');
        }
        return out.toString();
    }

    @Test
    void noDelayPassesTheSignalThrough() {
        assertEquals("T..TT.", run(new DelayedSignal(0), "T..TT."));
    }

    @Test
    void aTapComesOutThreeTicksLater() {
        assertEquals("...T..", run(new DelayedSignal(3), "T....."));
        assertEquals("...TT.", run(new DelayedSignal(3), "TT...."));
    }

    @Test
    void aHeldSignalStartsLateAndStopsLate() {
        assertEquals("...TTTT...", run(new DelayedSignal(3), "TTTT......"));
    }

    @Test
    void theDelayedOutputStartsOff() {
        assertEquals("....", run(new DelayedSignal(4), "...."));
        assertEquals("...", run(new DelayedSignal(5), "TTT")); // nothing has come through yet
    }

    @Test
    void resetForgetsWhatWasWaiting() {
        DelayedSignal s = new DelayedSignal(3);
        run(s, "TT");
        s.reset();
        assertEquals("....", run(s, "...."));
        s.reset();
        assertEquals("...T", run(s, "T..."));  // after a reset the delay starts again
        assertFalse(new DelayedSignal(2).push(true));
    }

    @Test
    void aNegativeDelayIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DelayedSignal(-1));
        assertTrue(new DelayedSignal(0).push(true));
    }

    @Test
    void theJumpDelayIsATenthOfASecond() {
        assertEquals(2, JumpDelay.TICKS); // 0.10 s at 20 ticks a second (0.5, 0.2 and 0.15 s were too late: the user tuned it)
    }

    @Test
    void theJumpIsDelayedOnlyWhileActiveAndRestartsEachTimeItBecomesActive() {
        JumpDelay.resetForTests();
        // inactive: untouched
        assertTrue(JumpDelay.apply(true, false));
        assertFalse(JumpDelay.apply(false, false));
        // active: the jump comes out TICKS ticks later
        assertFalse(JumpDelay.apply(true, true));
        for (int i = 1; i < JumpDelay.TICKS; i++) {
            assertFalse(JumpDelay.apply(false, true), "tick " + i);
        }
        assertTrue(JumpDelay.apply(false, true));   // the tick it arrives
        assertFalse(JumpDelay.apply(false, true));
        // a jump waiting when the player leaves third person is dropped, not played later
        JumpDelay.apply(true, true);
        assertFalse(JumpDelay.apply(false, false));
        for (int i = 0; i < JumpDelay.TICKS + 2; i++) {
            assertFalse(JumpDelay.apply(false, true), "after returning, tick " + i);
        }
    }
}
