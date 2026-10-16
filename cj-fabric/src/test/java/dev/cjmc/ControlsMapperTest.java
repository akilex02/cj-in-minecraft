package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ControlsMapperTest {
    @Test
    void rightClickAimsAndLeftClickFires() {
        assertEquals(Protocol.INTENT_AIM, ControlsMapper.intents(true, false, false, false, true, false));
        assertEquals(Protocol.INTENT_FIRE, ControlsMapper.intents(true, false, false, true, false, false));
        assertEquals(Protocol.INTENT_AIM | Protocol.INTENT_FIRE, ControlsMapper.intents(true, false, false, true, true, false));
        assertEquals(0, ControlsMapper.intents(true, false, false, false, false, false));
    }

    @Test
    void withoutAWeaponTheClicksAreVanillas() {
        assertEquals(0, ControlsMapper.intents(false, false, false, true, true, false));
    }

    @Test
    void anOpenScreenSilencesTheClicks() {
        // the inventory, chat or the pause menu are open: a click there must not fire
        assertEquals(0, ControlsMapper.intents(true, false, true, true, true, false));
        assertEquals(0, ControlsMapper.intents(true, false, true, false, true, false));
    }

    @Test
    void withFistsOrBatTheLeftClickStrikesAndTheRightOneDoesNothing() {
        assertEquals(Protocol.INTENT_MELEE, ControlsMapper.intents(false, true, false, true, false, false));
        assertEquals(Protocol.INTENT_MELEE, ControlsMapper.intents(true, true, false, true, false, false)); // the bat is an item too
        assertEquals(Protocol.INTENT_MELEE, ControlsMapper.intents(false, true, false, true, true, false)); // right click adds nothing
        assertEquals(0, ControlsMapper.intents(false, true, false, false, true, false));
        assertEquals(0, ControlsMapper.intents(false, true, false, false, false, false));
    }

    @Test
    void anOpenScreenSilencesTheBlowsToo() {
        assertEquals(0, ControlsMapper.intents(false, true, true, true, false, false));
    }

    @Test
    void neitherAWeaponNorMeleeMeansVanillaClicks() {
        assertEquals(0, ControlsMapper.intents(false, false, false, true, true, false));
    }

    @Test
    void knockedDownNothingGoesToGta() {
        // frozen (CJ is down): no aiming, firing or striking reaches the game, with a gun, the bat or the fists
        assertEquals(0, ControlsMapper.intents(true, false, false, true, true, true));
        assertEquals(0, ControlsMapper.intents(true, true, false, true, false, true));
        assertEquals(0, ControlsMapper.intents(false, true, false, true, false, true));
        // and the same clicks go through once he is up
        assertEquals(Protocol.INTENT_FIRE, ControlsMapper.intents(true, false, false, true, false, false));
    }
}
