package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CjEffectsTest {
    private static CjEffects.Plan plan(boolean wanted, boolean speedPresent, boolean speedOurs, boolean jumpPresent, boolean jumpOurs) {
        return CjEffects.plan(wanted, speedPresent, speedOurs, false, jumpPresent, jumpOurs, false);
    }

    private static CjEffects.Plan planWithOldJump(boolean wanted, boolean speedPresent, boolean speedOurs) {
        return CjEffects.plan(wanted, speedPresent, speedOurs, false, true, false, true);
    }

    @Test
    void wantedAndMissingAddsBoth() {
        CjEffects.Plan p = plan(true, false, false, false, false);
        assertTrue(p.addSpeed() && p.addJump());
        assertFalse(p.removeSpeed() || p.removeJump());
    }

    @Test
    void wantedAndAlreadyThereChangesNothing() {
        CjEffects.Plan p = plan(true, true, true, true, true);
        assertEquals(new CjEffects.Plan(false, false, false, false), p);
    }

    @Test
    void wantedAddsOnlyWhatIsMissing() {
        CjEffects.Plan p = plan(true, true, true, false, false);
        assertFalse(p.addSpeed());
        assertTrue(p.addJump());
    }

    @Test
    void anEffectThePlayerHasOfHisOwnIsNeverReplacedNorRemoved() {
        // a speed potion or a beacon: present and not ours
        assertEquals(new CjEffects.Plan(false, true, false, false), plan(true, true, false, false, false)); // only the missing jump is added
        assertEquals(new CjEffects.Plan(false, false, false, false), plan(false, true, false, true, false)); // nothing of ours to take away
    }

    @Test
    void notWantedRemovesOnlyOurOwn() {
        CjEffects.Plan p = plan(false, true, true, true, true);
        assertTrue(p.removeSpeed() && p.removeJump());
        assertFalse(p.addSpeed() || p.addJump());
        assertEquals(new CjEffects.Plan(false, false, true, false), plan(false, true, true, true, false));
        assertEquals(new CjEffects.Plan(false, false, false, false), plan(false, false, false, false, false));
    }

    @Test
    void theJumpBoostIIOfTheEarlierVersionIsReplacedByTheJumpBoostI() {
        // the old infinite Jump Boost II with no particles is still on a player until a sync finds it: it is taken off and the right one is put on
        assertEquals(new CjEffects.Plan(false, true, false, true), planWithOldJump(true, true, true));
        assertEquals(new CjEffects.Plan(false, false, false, true), planWithOldJump(false, false, false)); // not wanted: only taken off
    }

    @Test
    void theOldJumpBoostIIIsRecognisedByItsLevel() {
        assertEquals(1, CjEffects.OLD_JUMP_AMPLIFIER);
        assertTrue(CjEffects.isOurs(true, CjEffects.OLD_JUMP_AMPLIFIER, false, CjEffects.OLD_JUMP_AMPLIFIER));
        assertFalse(CjEffects.isOurs(true, CjEffects.JUMP_AMPLIFIER, false, CjEffects.OLD_JUMP_AMPLIFIER));
    }

    @Test
    void theEffectsAreTheOnesTheUserChose() {
        assertEquals(0, CjEffects.SPEED_AMPLIFIER);  // Speed I
        assertEquals(0, CjEffects.JUMP_AMPLIFIER);   // Jump Boost I (the user chose it on 2026-10-10 instead of II)
    }

    @Test
    void anEffectIsOursWhenItIsInfiniteHasOurLevelAndShowsNoParticles() {
        assertTrue(CjEffects.isOurs(true, 1, false, 1));
        assertFalse(CjEffects.isOurs(false, 1, false, 1)); // a timed effect
        assertFalse(CjEffects.isOurs(true, 2, false, 1));  // another level
        assertFalse(CjEffects.isOurs(true, 1, true, 1));   // one with particles: somebody else's
    }

    @Test
    void anEffectOfOursThatStillShowsItsIconIsReplacedByOneWithoutIt() {
        // the effects of the earlier version showed their icons in the corner: found on a player, they come off and the hidden ones go on
        assertEquals(new CjEffects.Plan(true, true, true, true), CjEffects.plan(true, true, true, true, true, true, true));
        assertEquals(new CjEffects.Plan(false, false, true, true), CjEffects.plan(false, true, true, true, true, true, true)); // not wanted: only taken off
        // only the one that shows its icon is replaced
        assertEquals(new CjEffects.Plan(true, false, true, false), CjEffects.plan(true, true, true, true, true, true, false));
    }

    @Test
    void theEffectsHideTheirIcons() {
        assertFalse(CjEffects.SHOW_ICON);
    }
}
