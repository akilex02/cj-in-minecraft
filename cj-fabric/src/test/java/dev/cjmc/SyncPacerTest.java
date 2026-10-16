package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SyncPacerTest {
    @Test
    void neverWantedNeverSyncs() {
        SyncPacer p = new SyncPacer(20);
        for (int i = 0; i < 100; i++) {
            assertFalse(p.tick(false), "tick " + i);
        }
    }

    @Test
    void syncsTheMomentItBecomesWantedAndThenOnceEveryPeriod() {
        SyncPacer p = new SyncPacer(20);
        assertTrue(p.tick(true)); // the change
        for (int i = 0; i < 19; i++) {
            assertFalse(p.tick(true), "tick " + i);
        }
        assertTrue(p.tick(true)); // the 20th tick after: a re-check (an effect may have been cleared by a death or a bucket of milk)
        for (int i = 0; i < 19; i++) {
            assertFalse(p.tick(true));
        }
        assertTrue(p.tick(true));
    }

    @Test
    void syncsTheMomentItStopsBeingWantedAndThenGoesQuiet() {
        SyncPacer p = new SyncPacer(20);
        p.tick(true);
        assertTrue(p.tick(false)); // the change back: take the effects away
        for (int i = 0; i < 100; i++) {
            assertFalse(p.tick(false), "tick " + i);
        }
    }

    @Test
    void flickeringStillSyncsEveryChange() {
        SyncPacer p = new SyncPacer(20);
        assertTrue(p.tick(true));
        assertTrue(p.tick(false));
        assertTrue(p.tick(true));
        assertFalse(p.tick(true));
    }

    @Test
    void aChangeRestartsThePeriod() {
        SyncPacer p = new SyncPacer(20);
        p.tick(true);
        for (int i = 0; i < 5; i++) {
            p.tick(true);
        }
        p.tick(false);
        assertTrue(p.tick(true)); // wanted again: a full period before the next re-check, not what was left of the old one
        for (int i = 0; i < 19; i++) {
            assertFalse(p.tick(true), "tick " + i);
        }
        assertTrue(p.tick(true));
    }
}
