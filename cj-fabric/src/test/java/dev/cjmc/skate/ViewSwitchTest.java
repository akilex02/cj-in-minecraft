package dev.cjmc.skate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ViewSwitchTest {
    private enum View { FIRST, BACK, FRONT }

    @Test
    void fromFirstPersonTheBoardGoesBehindAndBackWhenItEnds() {
        ViewSwitch<View> v = new ViewSwitch<>(View.FIRST, View.BACK);
        assertEquals(View.BACK, v.onStart(View.FIRST));
        assertEquals(View.FIRST, v.onStop(View.BACK));
    }

    @Test
    void alreadyInThirdPersonNothingChanges() {
        ViewSwitch<View> v = new ViewSwitch<>(View.FIRST, View.BACK);
        assertNull(v.onStart(View.FRONT));
        assertNull(v.onStop(View.FRONT));
    }

    @Test
    void aViewChosenByHandWhileSkatingIsLeftAlone() {
        ViewSwitch<View> v = new ViewSwitch<>(View.FIRST, View.BACK);
        v.onStart(View.FIRST);
        assertNull(v.onStop(View.FRONT)); // he pressed F5 on the board: his choice stays
    }

    @Test
    void theSwitchIsForgottenOnceUsed() {
        ViewSwitch<View> v = new ViewSwitch<>(View.FIRST, View.BACK);
        v.onStart(View.FIRST);
        v.onStop(View.BACK);
        assertNull(v.onStop(View.BACK));
    }
}
