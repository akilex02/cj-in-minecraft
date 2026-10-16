package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RadioDisplayTest {
    @Test
    void nothingShowsBeforeAStation() {
        RadioDisplay d = new RadioDisplay();
        assertNull(d.text(0));
        d.observe(-1, true, 100);
        assertNull(d.text(100));
    }

    @Test
    void aNewStationShowsItsNameForFiveSecondsAndThenGoes() {
        RadioDisplay d = new RadioDisplay();
        d.observe(3, true, 1000);
        assertEquals(RadioStations.nameOf(3), d.text(1000));
        assertEquals(RadioStations.nameOf(3), d.text(1000 + RadioDisplay.SHOW_MS - 1));
        assertNull(d.text(1000 + RadioDisplay.SHOW_MS));
    }

    @Test
    void theSameStationDoesNotShowItAgainButAnotherOneDoes() {
        RadioDisplay d = new RadioDisplay();
        d.observe(3, true, 1000);
        d.observe(3, true, 7000);
        assertNull(d.text(7000));
        d.observe(4, true, 8000);
        assertEquals(RadioStations.nameOf(4), d.text(8000));
        d.observe(3, true, 9000); // back to the first one: it is a change
        assertEquals(RadioStations.nameOf(3), d.text(9000));
    }

    @Test
    void theNameRestartsItsFiveSecondsWithEachChange() {
        RadioDisplay d = new RadioDisplay();
        d.observe(3, true, 0);
        d.observe(4, true, 4000);
        assertEquals(RadioStations.nameOf(4), d.text(8000));
        assertNull(d.text(4000 + RadioDisplay.SHOW_MS));
    }

    @Test
    void gettingOutHidesItAndTheNextRideShowsTheStationAgain() {
        RadioDisplay d = new RadioDisplay();
        d.observe(3, true, 0);
        d.observe(3, false, 1000);
        assertNull(d.text(1000));
        d.observe(3, true, 20000);
        assertEquals(RadioStations.nameOf(3), d.text(20000));
    }

    @Test
    void theNameFadesInTheLastMomentAndIsOpaqueBefore() {
        RadioDisplay d = new RadioDisplay();
        d.observe(2, true, 0);
        assertEquals(1.0, d.alpha(0), 1e-9);
        assertEquals(1.0, d.alpha(RadioDisplay.SHOW_MS - RadioDisplay.FADE_MS), 1e-9);
        double half = d.alpha(RadioDisplay.SHOW_MS - RadioDisplay.FADE_MS / 2);
        assertTrue(half > 0 && half < 1);
        assertEquals(0.0, d.alpha(RadioDisplay.SHOW_MS), 1e-9);
        assertEquals(0.0, new RadioDisplay().alpha(0), 1e-9);
    }

    @Test
    void aClockThatGoesBackwardsDoesNotKeepItForever() {
        RadioDisplay d = new RadioDisplay();
        d.observe(2, true, 100000);
        assertNull(d.text(50000));
    }
}
