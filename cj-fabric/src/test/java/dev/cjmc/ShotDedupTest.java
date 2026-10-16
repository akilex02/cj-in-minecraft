package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ShotDedupTest {
    @Test
    void theFirstEventCountsItsOwnRounds() {
        assertEquals(2, new ShotDedup().newRounds(2, 2));
        assertEquals(5, new ShotDedup().newRounds(105, 5)); // joining late does not replay the old total
    }

    @Test
    void theRepeatedEventDoesNotCountAgain() {
        ShotDedup d = new ShotDedup();
        assertEquals(1, d.newRounds(1, 1));
        assertEquals(0, d.newRounds(1, 1)); // the second copy of the same datagram
        assertEquals(2, d.newRounds(3, 2));
        assertEquals(0, d.newRounds(3, 2));
    }

    @Test
    void aLostDatagramLosesAtMostThatEventsRounds() {
        ShotDedup d = new ShotDedup();
        assertEquals(1, d.newRounds(1, 1));
        assertEquals(1, d.newRounds(5, 1)); // events 2 to 4 never arrived: only this event's own round is reported
    }

    @Test
    void aCounterThatGoesBackIsARestartOfGtaWhateverTheSize() {
        // loopback UDP does not reorder, so a smaller counter means GTA started counting again: it must not be ignored until
        // it overtakes the old value (that silenced every shot after a restart)
        ShotDedup d = new ShotDedup();
        assertEquals(1, d.newRounds(30, 1));
        assertEquals(1, d.newRounds(1, 1));
        assertEquals(0, d.newRounds(1, 1)); // the second copy of the same datagram is still a duplicate
        assertEquals(1, d.newRounds(2, 1));
    }

    @Test
    void aRestartOfGtaStartsTheCountAgain() {
        // GTA closed and reopened while Minecraft stayed open: its counter starts from 0 again
        ShotDedup d = new ShotDedup();
        assertEquals(1, d.newRounds(5000, 1));
        assertEquals(1, d.newRounds(1, 1));
        assertEquals(0, d.newRounds(1, 1));
        assertEquals(1, d.newRounds(2, 1));
    }

    @Test
    void theCounterWrapsAroundWithoutLosingShots() {
        ShotDedup d = new ShotDedup();
        assertEquals(1, d.newRounds(Integer.MAX_VALUE, 1));
        assertEquals(1, d.newRounds(Integer.MIN_VALUE, 1)); // MAX_VALUE + 1 wraps
    }
}
