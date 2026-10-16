package dev.cjmc.skate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class SkateRulesTest {
    @Test
    void aPlayerOnFootAndAliveCanRide() {
        assertTrue(SkateRules.canRide(false, false, false, false, false));
    }

    @Test
    void ridingAVehicleFlyingSpectatingDeadOrInTheWaterCannot() {
        assertFalse(SkateRules.canRide(true, false, false, false, false));
        assertFalse(SkateRules.canRide(false, true, false, false, false));
        assertFalse(SkateRules.canRide(false, false, true, false, false));
        assertFalse(SkateRules.canRide(false, false, false, true, false));
        assertFalse(SkateRules.canRide(false, false, false, false, true));
    }

    @Test
    void theEngineIsReachedAtItsDefaultAddressUnlessTheUserSaysOtherwise() {
        assertEquals(new InetSocketAddress("127.0.0.1", 34600), SkateRules.engineAddress(null));
        assertEquals(new InetSocketAddress("127.0.0.1", 34700), SkateRules.engineAddress("127.0.0.1:34700"));
        assertEquals(new InetSocketAddress("localhost", 5000), SkateRules.engineAddress("localhost:5000"));
    }

    @Test
    void anAddressThatMakesNoSenseIsTheDefault() {
        for (String bad : new String[] {"", "nonsense", "127.0.0.1", "127.0.0.1:", ":34600", "127.0.0.1:0", "127.0.0.1:70000", "127.0.0.1:abc"}) {
            assertEquals(new InetSocketAddress("127.0.0.1", 34600), SkateRules.engineAddress(bad), bad);
        }
    }

    @Test
    void theEngineIsGivenTimeToAnswerAtTheStartAndLessOnceItHas() {
        assertFalse(SkateRules.engineSilent(false, 2_000_000_000L)); // not yet answered: three seconds of grace
        assertTrue(SkateRules.engineSilent(false, 3_100_000_000L));
        assertFalse(SkateRules.engineSilent(true, 1_000_000_000L));  // it has answered: a second of silence is fine
        assertTrue(SkateRules.engineSilent(true, 1_600_000_000L));
    }

    @Test
    void theHeadingOfTheBoardIsMinusTheYawOfThePlayer() {
        // Minecraft's yaw 0 faces +Z and turns clockwise seen from above; the engine's heading 0 faces +Z and turns toward +X
        assertEquals(0f, SkateRules.headingOf(0f), 1e-6f);
        assertEquals((float) -Math.PI / 2, SkateRules.headingOf(90f), 1e-6f); // yaw 90 faces -X; heading -90 degrees too
        assertEquals((float) Math.PI / 2, SkateRules.headingOf(-90f), 1e-6f); // yaw -90 faces +X
    }

    @Test
    void onTheBoardThePlayerRidesJustOverTheGroundUnderTheDeck() {
        // riding, the root of the animation is the centre of the deck, 0.087 over the ground
        double[] at = SkateRules.playerPosition(new float[] {1f, 2.087f, 3f}, new float[] {1f, 2.087f, 3f});
        assertEquals(1.0, at[0], 1e-6);
        assertEquals(2.087 - SkateRide.DECK_ABOVE_GROUND, at[1], 1e-6);
        assertTrue(at[1] > 2.0, "his feet stay over the ground");
        assertEquals(3.0, at[2], 1e-6);
    }

    @Test
    void offTheBoardThePlayerStandsWhereTheSkaterStandsNotWhereTheBoardIs() {
        // walking with the board in the hand: the root is on the ground, the deck in the hand 0.7 above
        double[] at = SkateRules.playerPosition(new float[] {0f, 2.0f, -20f}, new float[] {-0.25f, 2.7f, -19.9f});
        assertEquals(0.0, at[0], 1e-6);
        assertTrue(at[1] >= 2.0 && at[1] < 2.02, "on the ground, not under it: " + at[1]);
        assertEquals(-20.0, at[2], 1e-6);
    }

    @Test
    void theCameraTurnsTowardWhereTheSkaterGoes() {
        // going +x (west in Minecraft is -x, so +x is yaw -90): from yaw 0 it turns toward -90, part of the way in a short time
        float yaw = SkateRules.followYaw(0f, 5.0, 0.0, 0.05);
        assertTrue(yaw < 0f && yaw > -90f, "part of the way: " + yaw);
        // a long time later it is there
        assertEquals(-90f, SkateRules.followYaw(0f, 5.0, 0.0, 10.0), 0.5f);
    }

    @Test
    void theCameraTakesTheShortWayRound() {
        // from 170 toward -170 (going a little east of north): 20 degrees on through 180, not 340 back the other way
        double a = Math.toRadians(-170);
        float yaw = SkateRules.followYaw(170f, -Math.sin(a) * 5.0, Math.cos(a) * 5.0, 0.05);
        assertTrue(yaw > 170f && yaw < 190f, "on through 180: " + yaw);
    }

    @Test
    void standingStillLeavesTheCameraAlone() {
        assertEquals(37f, SkateRules.followYaw(37f, 0.1, 0.05, 1.0), 1e-6f);
    }

    @Test
    void bothSticksPressedTogetherAreTheComboThatTogglesSkating() {
        float[] actions = new float[SkateProtocol.ACTIONS];
        assertFalse(SkateRules.toggleCombo(actions));
        actions[2] = 1f; // L3 alone
        assertFalse(SkateRules.toggleCombo(actions));
        actions[5] = 1f; // and R3
        assertTrue(SkateRules.toggleCombo(actions));
        actions[2] = 0f; // R3 alone
        assertFalse(SkateRules.toggleCombo(actions));
        assertFalse(SkateRules.toggleCombo(null)); // no pad
    }

    @Test
    void theComboTogglesOnceWhenPressedNotWhileHeld() {
        SkateRules.Edge edge = new SkateRules.Edge();
        assertFalse(edge.rose(false));
        assertTrue(edge.rose(true));
        assertFalse(edge.rose(true)); // still held
        assertFalse(edge.rose(false));
        assertTrue(edge.rose(true)); // pressed again
    }

    @Test
    void withoutTheEngineThePlayerIsToldAboutTheOptionalPack() {
        assertTrue(SkateRules.ENGINE_MISSING.contains("optional skate pack"));
        assertTrue(SkateRules.ENGINE_MISSING.contains("LEEME-skate.md"));
        assertTrue(SkateRules.ENGINE_MISSING.contains("skate.bat / skate.sh"));
    }
}
