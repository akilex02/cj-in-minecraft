package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class FirstPersonHoldsTest {
    @Test
    void eachWeaponHasItsKind() {
        assertEquals(FirstPersonHolds.Kind.PISTOL, FirstPersonHolds.kindOf("colt45"));
        assertEquals(FirstPersonHolds.Kind.PISTOL, FirstPersonHolds.kindOf("desert_eagle"));
        assertEquals(FirstPersonHolds.Kind.PISTOL, FirstPersonHolds.kindOf("micro_uzi"));
        assertEquals(FirstPersonHolds.Kind.RIFLE, FirstPersonHolds.kindOf("chromegun"));
        assertEquals(FirstPersonHolds.Kind.RIFLE, FirstPersonHolds.kindOf("ak47"));
        assertEquals(FirstPersonHolds.Kind.RIFLE, FirstPersonHolds.kindOf("m4"));
        assertEquals(FirstPersonHolds.Kind.HEAVY, FirstPersonHolds.kindOf("minigun"));
        assertEquals(FirstPersonHolds.Kind.HEAVY, FirstPersonHolds.kindOf("rocket_launcher"));
        assertEquals(FirstPersonHolds.Kind.BAT, FirstPersonHolds.kindOf("baseball_bat"));
        assertEquals(FirstPersonHolds.Kind.FISTS, FirstPersonHolds.kindOf(null));
        assertNull(FirstPersonHolds.kindOf("jetpack"));
    }

    @Test
    void theKindOfTheCommandIsReadByName() {
        assertEquals(FirstPersonHolds.Kind.RIFLE, FirstPersonHolds.parseKind("rifle"));
        assertEquals(FirstPersonHolds.Kind.FISTS, FirstPersonHolds.parseKind("FISTS"));
        assertNull(FirstPersonHolds.parseKind("sword"));
    }

    @Test
    void aimingTakesTheHoldAwayIn015SecondsAndLettingGoBringsItBack() {
        FirstPersonHolds h = new FirstPersonHolds();
        assertEquals(1f, h.blend(false, 0.05), 1e-6f);
        assertEquals(1f - 0.075f / 0.15f, h.blend(true, 0.075), 1e-5f);
        assertEquals(0f, h.blend(true, 0.2), 1e-6f);
        assertEquals(0f, h.blend(true, 1.0), 1e-6f);
        assertEquals(0.5f, h.blend(false, 0.075), 1e-5f);
    }

    @Test
    void aCalibratedHoldReplacesTheDefault() {
        FirstPersonHolds h = new FirstPersonHolds();
        float[] six = {0.1f, -0.2f, 0.3f, 10f, 20f, 30f};
        h.setHold(FirstPersonHolds.Kind.BAT, six);
        assertArrayEquals(six, h.hold(FirstPersonHolds.Kind.BAT), 0f);
    }

    @Test
    void noHoldIsNoChangeAndAFullOneMovesByItsOffset() {
        float[] six = {0.1f, 0.2f, -0.3f, 0f, 0f, 0f};
        assertArrayEquals(new float[] {0, 0, 0}, FirstPersonPlacement.apply(FirstPersonHolds.holdMatrix(six, 0f), new float[] {0, 0, 0}), 1e-6f);
        assertArrayEquals(new float[] {0.1f, 0.2f, -0.3f}, FirstPersonPlacement.apply(FirstPersonHolds.holdMatrix(six, 1f), new float[] {0, 0, 0}), 1e-6f);
        assertArrayEquals(new float[] {0.05f, 0.1f, -0.15f}, FirstPersonPlacement.apply(FirstPersonHolds.holdMatrix(six, 0.5f), new float[] {0, 0, 0}), 1e-6f);
    }
}
