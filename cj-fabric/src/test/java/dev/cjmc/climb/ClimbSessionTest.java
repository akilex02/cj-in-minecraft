package dev.cjmc.climb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClimbSessionTest {
    @Test
    void notActiveBeforeItStarts() {
        assertFalse(new ClimbSession().active(0));
    }

    @Test
    void activeUntilTheEndOfTheSettle() {
        ClimbSession s = new ClimbSession();
        s.start(3, 1000);
        assertEquals(3, s.height());
        long end = 1000 + ClimbPath.TOTAL_MS + ClimbPath.SETTLE_MS;
        assertTrue(s.active(1000));
        assertTrue(s.active(end - 1));
        assertFalse(s.active(end));
        assertEquals(0L, s.elapsed(1000));
        assertEquals(1234L, s.elapsed(2234));
    }

    @Test
    void theAnimationFlagLastsOnlyTheAnimation() {
        ClimbSession s = new ClimbSession();
        assertFalse(s.animating(0));
        s.start(2, 1000);
        assertTrue(s.animating(1000));
        assertTrue(s.animating(1000 + ClimbPath.TOTAL_MS - 1));
        assertFalse(s.animating(1000 + ClimbPath.TOTAL_MS)); // the settle follows with the flag down
        assertFalse(s.animating(999));
        s.start(2, 5000);
        s.cancel();
        assertFalse(s.animating(5001));
    }

    @Test
    void cancelEndsIt() {
        ClimbSession s = new ClimbSession();
        s.start(2, 1000);
        s.cancel();
        assertFalse(s.active(1001));
    }

    @Test
    void aClockThatJumpsOrGoesBackEndsIt() {
        ClimbSession s = new ClimbSession();
        s.start(2, 1000);
        assertFalse(s.active(1000 + ClimbPath.TOTAL_MS + ClimbPath.SETTLE_MS + 500));
        assertFalse(s.active(999));
    }

    @Test
    void aHeightOutsideTheLimitsIsRejected() {
        ClimbSession s = new ClimbSession();
        assertThrows(IllegalArgumentException.class, () -> s.start(1, 0));
        assertThrows(IllegalArgumentException.class, () -> s.start(4, 0));
        assertFalse(s.active(0));
    }
}
