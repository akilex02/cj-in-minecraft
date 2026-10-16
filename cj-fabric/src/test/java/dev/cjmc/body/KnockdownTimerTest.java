package dev.cjmc.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KnockdownTimerTest {
    @Test
    void nothingIsOnBeforeAStart() {
        KnockdownTimer t = new KnockdownTimer();
        assertFalse(t.knocked(0));
        assertFalse(t.frozen(0));
    }

    @Test
    void knockedForTheDownTimeFrozenForTheDownTimePlusTheGetUp() {
        KnockdownTimer t = new KnockdownTimer();
        assertTrue(t.start(0));
        assertTrue(t.knocked(0));
        assertTrue(t.knocked(1199));
        assertFalse(t.knocked(1200));
        assertTrue(t.frozen(1200)); // getting up: still frozen
        assertTrue(t.frozen(2699));
        assertFalse(t.frozen(2700));
        assertEquals(1200L, KnockdownTimer.DOWN_MS); // the user asked for 1.2 s down (it was 2.5 s)
        assertEquals(1500L, KnockdownTimer.GETUP_MS);
    }

    @Test
    void aKnockdownDoesNotChainIntoAnother() {
        KnockdownTimer t = new KnockdownTimer();
        assertTrue(t.start(0));
        assertFalse(t.start(1000));        // under way: ignored and not restarted
        assertFalse(t.start(2000));        // getting up: ignored too
        assertFalse(t.frozen(2700));       // so it ends when the first one was going to
    }

    @Test
    void clearTurnsEverythingOff() {
        KnockdownTimer t = new KnockdownTimer();
        t.start(0);
        t.clear();
        assertFalse(t.knocked(10));
        assertFalse(t.frozen(10));
        assertTrue(t.start(20)); // and a new one may start
    }

    @Test
    void aClockThatGoesBackNeverLeavesItOnForever() {
        KnockdownTimer t = new KnockdownTimer();
        t.start(10_000);
        assertFalse(t.knocked(5_000));
        assertFalse(t.frozen(5_000));
        assertFalse(t.frozen(-1));
    }

    @Test
    void afterGettingUpTheHeroCannotBeKnockedDownAgainForAWhile() {
        // otherwise a ravager or a run of explosions would keep him frozen for ever
        KnockdownTimer t = new KnockdownTimer();
        assertEquals(3000L, KnockdownTimer.IMMUNE_MS);
        assertTrue(t.start(0));
        assertFalse(t.frozen(2700));
        assertFalse(t.start(2700));                 // up, but still immune
        assertFalse(t.start(2700 + 2999));
        assertTrue(t.start(2700 + 3000));           // the immunity is over
        assertTrue(t.knocked(5800));
    }

    @Test
    void theImmunityAlsoHoldsWhenTheClockGoesBackAndIsClearedByClear() {
        KnockdownTimer t = new KnockdownTimer();
        t.start(10_000);
        assertTrue(t.start(5_000));                 // a clock that went back is a new situation, not an eternal immunity
        t.clear();
        assertTrue(t.start(5_001));                 // clear (death, no GTA) lifts it too
    }
}
