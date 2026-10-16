package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InfoSwitchTest {
    @Test
    void theInfoIsShownAtTheStart() {
        assertTrue(new InfoSwitch().visible());
    }

    @Test
    void eachPressSwitchesItAndSeveralInOneTickAddUp() {
        InfoSwitch s = new InfoSwitch();
        s.press(1);
        assertFalse(s.visible());
        s.press(1);
        assertTrue(s.visible());
        s.press(2); // an even number of presses in one tick: as it was
        assertTrue(s.visible());
        s.press(3);
        assertFalse(s.visible());
    }

    @Test
    void noPressesOrBrokenCountsChangeNothing() {
        InfoSwitch s = new InfoSwitch();
        s.press(0);
        s.press(-4);
        assertTrue(s.visible());
    }

    @Test
    void theSwitchTellsWhenTheTextHasToBeCleared() {
        InfoSwitch s = new InfoSwitch();
        assertFalse(s.takeClear()); // nothing to clear while it is shown
        s.press(1);
        assertTrue(s.takeClear());   // it has just been hidden: the text on the screen is taken away once
        assertFalse(s.takeClear());  // and not again every tick
        s.press(1);
        assertFalse(s.takeClear());  // showing it again clears nothing
    }
}
