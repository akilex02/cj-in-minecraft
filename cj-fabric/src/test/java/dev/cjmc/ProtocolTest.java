package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtocolTest {
    private static final Path GOLDEN = Path.of("..", "protocol", "golden");

    @Test
    void stateEncodingMatchesGolden() throws Exception {
        var state = new Protocol.State(7, 1234, 10.5f, 64.0f, -3.25f, 0.1f, 0.0f, -0.2f, 90.0f, -15.0f,
                Protocol.FLAG_ON_GROUND | Protocol.FLAG_SPRINT, Protocol.INTENT_AIM | Protocol.INTENT_FIRE, (short) 22,
                1.5f, 1.25f, -20.0f);
        assertArrayEquals(Files.readAllBytes(GOLDEN.resolve("state_v2.bin")), Protocol.encodeState(state));
    }

    @Test
    void echoDecodesGolden() throws Exception {
        byte[] data = Files.readAllBytes(GOLDEN.resolve("echo_v2.bin"));
        Protocol.Echo echo = Protocol.decodeEcho(data, data.length);
        assertNotNull(echo);
        assertEquals(7, echo.ackSeq());
        assertEquals(4321, echo.gtaTimeMs());
        assertEquals(2488.5f, echo.x());
        assertEquals(-1666.25f, echo.y());
        assertEquals(13.5f, echo.z());
        assertEquals(22, echo.weaponHeld());
    }

    @Test
    void shotDecodesGolden() throws Exception {
        byte[] data = Files.readAllBytes(GOLDEN.resolve("shot_v2.bin"));
        Protocol.Shot shot = Protocol.decodeShot(data, data.length);
        assertNotNull(shot);
        assertEquals(new Protocol.Shot(7, 4321, 12, 22, 3), shot);
    }

    @Test
    void theEchoFlagsSayWhetherCjHoldsTwoGuns() throws Exception {
        byte[] data = Files.readAllBytes(GOLDEN.resolve("echo_v2.bin"));
        assertEquals(0, Protocol.decodeEcho(data, data.length).flags());
        assertFalse(Protocol.decodeEcho(data, data.length).dualWield());
        data[30] = 1; // bit 0 of the two last bytes
        assertEquals(1, Protocol.decodeEcho(data, data.length).flags());
        assertTrue(Protocol.decodeEcho(data, data.length).dualWield());
        data[30] = 2; // a bit nobody knows yet is not the twin pistols
        assertFalse(Protocol.decodeEcho(data, data.length).dualWield());
    }

    @Test
    void rejectsInvalidEchoesAndShots() throws Exception {
        byte[] echo = Files.readAllBytes(GOLDEN.resolve("echo_v2.bin"));
        byte[] shot = Files.readAllBytes(GOLDEN.resolve("shot_v2.bin"));
        for (byte[] good : new byte[][] {echo, shot}) {
            boolean isEcho = good == echo;
            java.util.function.BiFunction<byte[], Integer, Object> decode =
                    (d, n) -> isEcho ? Protocol.decodeEcho(d, n) : Protocol.decodeShot(d, n);

            assertNull(decode.apply(good, good.length - 1));

            byte[] badMagic = good.clone();
            badMagic[0] ^= (byte) 0xFF;
            assertNull(decode.apply(badMagic, badMagic.length));

            byte[] badVersion = good.clone();
            badVersion[4] = 3;
            assertNull(decode.apply(badVersion, badVersion.length));

            byte[] wrongType = good.clone();
            wrongType[6] = 1; // State
            assertNull(decode.apply(wrongType, wrongType.length));
        }
        assertNull(Protocol.decodeShot(echo, echo.length)); // an Echo is not a Shot
        assertNull(Protocol.decodeEcho(shot, shot.length)); // and a Shot is not an Echo
    }

    @Test
    void waterFlagsAreFree() {
        int used = Protocol.FLAG_ON_GROUND | Protocol.FLAG_SPRINT | Protocol.FLAG_SNEAK | Protocol.FLAG_SWIM | Protocol.FLAG_CLIMBING
                | Protocol.FLAG_FALLING | Protocol.FLAG_KNOCKED | Protocol.FLAG_DEAD | Protocol.HURT_MASK | Protocol.FLAG_BLASTED
                | Protocol.FLAG_WALL_CLIMB | Protocol.FLAG_JETPACK | Protocol.FLAG_JETPACK_THRUST;
        assertEquals(1 << 16, Protocol.FLAG_IN_WATER);
        assertEquals(1 << 17, Protocol.FLAG_UNDERWATER);
        assertEquals(1 << 18, Protocol.FLAG_WATER_MOVING);
        assertEquals(0, (Protocol.FLAG_IN_WATER | Protocol.FLAG_UNDERWATER | Protocol.FLAG_WATER_MOVING) & used);
        assertEquals(0, Protocol.FLAG_IN_WATER & Protocol.FLAG_UNDERWATER);
        assertEquals(0, Protocol.FLAG_IN_WATER & Protocol.FLAG_WATER_MOVING);
        assertEquals(0, Protocol.FLAG_UNDERWATER & Protocol.FLAG_WATER_MOVING);
    }

    @Test
    void jetpackFlagsAreFree() {
        int used = Protocol.FLAG_ON_GROUND | Protocol.FLAG_SPRINT | Protocol.FLAG_SNEAK | Protocol.FLAG_SWIM | Protocol.FLAG_CLIMBING
                | Protocol.FLAG_FALLING | Protocol.FLAG_KNOCKED | Protocol.FLAG_DEAD | Protocol.HURT_MASK | Protocol.FLAG_BLASTED
                | Protocol.FLAG_WALL_CLIMB;
        assertEquals(1 << 14, Protocol.FLAG_JETPACK);
        assertEquals(1 << 15, Protocol.FLAG_JETPACK_THRUST);
        assertEquals(0, Protocol.FLAG_JETPACK & used);
        assertEquals(0, Protocol.FLAG_JETPACK_THRUST & used);
        assertEquals(0, Protocol.FLAG_JETPACK & Protocol.FLAG_JETPACK_THRUST);
    }

    @Test
    void wallClimbFlagIsFree() {
        int used = Protocol.FLAG_ON_GROUND | Protocol.FLAG_SPRINT | Protocol.FLAG_SNEAK | Protocol.FLAG_SWIM | Protocol.FLAG_CLIMBING
                | Protocol.FLAG_FALLING | Protocol.FLAG_KNOCKED | Protocol.FLAG_DEAD | Protocol.HURT_MASK | Protocol.FLAG_BLASTED;
        assertEquals(1 << 13, Protocol.FLAG_WALL_CLIMB);
        assertEquals(0, Protocol.FLAG_WALL_CLIMB & used);
    }

    @Test
    void bodyFlagsDoNotOverlapAndRoundTrip() {
        int older = Protocol.FLAG_ON_GROUND | Protocol.FLAG_SPRINT | Protocol.FLAG_SNEAK | Protocol.FLAG_SWIM | Protocol.FLAG_CLIMBING;
        int[] fields = {Protocol.FLAG_FALLING, Protocol.FLAG_KNOCKED, Protocol.FLAG_DEAD, Protocol.HURT_MASK, Protocol.FLAG_BLASTED};
        assertEquals(1 << 5, Protocol.FLAG_FALLING);
        assertEquals(1 << 6, Protocol.FLAG_KNOCKED);
        assertEquals(1 << 7, Protocol.FLAG_DEAD);
        assertEquals(8, Protocol.HURT_SHIFT);
        assertEquals(0xF00, Protocol.HURT_MASK);
        assertEquals(1 << 12, Protocol.FLAG_BLASTED);
        for (int i = 0; i < fields.length; i++) {
            assertEquals(0, fields[i] & older);
            for (int j = i + 1; j < fields.length; j++) {
                assertEquals(0, fields[i] & fields[j]);
            }
        }
        assertEquals(Protocol.FLAG_FALLING | Protocol.FLAG_DEAD | (5 << 8), Protocol.bodyFlags(true, false, true, 5, false));
        assertEquals(5, Protocol.hurtOf(Protocol.bodyFlags(true, false, true, 5, false)));
        assertEquals(Protocol.FLAG_KNOCKED, Protocol.bodyFlags(false, true, false, 0, false));
        assertEquals(0, Protocol.bodyFlags(false, false, false, 16, false)); // the counter wraps modulo 16
        assertEquals(Protocol.FLAG_KNOCKED | Protocol.FLAG_BLASTED, Protocol.bodyFlags(false, true, false, 0, true)); // knocked down by an explosion
        assertEquals(15, Protocol.hurtOf(Protocol.bodyFlags(false, false, false, 31, false)));
    }

    @Test
    void aStateCarriesTheBodyFlagsOnTheWire() {
        int flags = Protocol.FLAG_ON_GROUND | Protocol.bodyFlags(true, true, false, 9, false);
        var state = new Protocol.State(1, 2, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, flags, 0, (short) 0, 0f, 0f, 0f);
        byte[] data = Protocol.encodeState(state);
        int onWire = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(48); // header 8 + 10 numbers of 4 bytes
        assertEquals(flags, onWire);
        assertEquals(9, Protocol.hurtOf(onWire));
    }

    @Test
    void theJumpIntentIsItsOwnBitAndTravelsInAState() {
        assertEquals(1 << 3, Protocol.INTENT_JUMP);
        assertEquals(0, Protocol.INTENT_JUMP & (Protocol.INTENT_AIM | Protocol.INTENT_FIRE | Protocol.INTENT_MELEE));
        var state = new Protocol.State(1, 2, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0, Protocol.INTENT_JUMP | Protocol.INTENT_MELEE, (short) 0, 0f, 0f, 0f);
        int onWire = java.nio.ByteBuffer.wrap(Protocol.encodeState(state)).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(52); // after the flags
        assertEquals(Protocol.INTENT_JUMP | Protocol.INTENT_MELEE, onWire);
    }

    @Test
    void vehicleCommandAndInputEncodeToGolden() throws Exception {
        var command = new Protocol.VehicleCommand(7, 1234, Protocol.VEHICLE_CMD_ENTER, (short) 492, 90.0f);
        assertArrayEquals(Files.readAllBytes(GOLDEN.resolve("vehicle_command_v2.bin")), Protocol.encodeVehicleCommand(command));
        var input = new Protocol.VehicleInput(8, 1300, 200, 30, (short) -64, 255, Protocol.VEHICLE_CORR_PRESENT, 0.5f, -0.25f);
        assertArrayEquals(Files.readAllBytes(GOLDEN.resolve("vehicle_input_v2.bin")), Protocol.encodeVehicleInput(input));
        assertEquals(24, Protocol.encodeVehicleCommand(command).length);
        assertEquals(36, Protocol.encodeVehicleInput(input).length);
    }

    @Test
    void vehicleStateDecodesGolden() throws Exception {
        byte[] data = Files.readAllBytes(GOLDEN.resolve("vehicle_state_v2.bin"));
        Protocol.VehicleState v = Protocol.decodeVehicleState(data, data.length);
        assertNotNull(v);
        assertEquals(new Protocol.VehicleState(8, 4321, Protocol.VEHICLE_FLAG_DRIVER_IN, 492, 1514.5f, -2585.5f, 13.25f, 1.5f, 0.0625f, -0.03125f,
                12.5f, -0.25f, -0.5f, 0.125f, -0.0625f, 0.5f), v);
        assertEquals(68, data.length);
    }

    @Test
    void vehicleStateRejectsMalformedAndOtherPackets() throws Exception {
        byte[] data = Files.readAllBytes(GOLDEN.resolve("vehicle_state_v2.bin"));
        byte[] badMagic = data.clone();
        badMagic[0] ^= (byte) 0xFF;
        byte[] badVersion = data.clone();
        badVersion[4] = 3;
        byte[] wrongType = data.clone();
        wrongType[6] = 1;
        for (byte[] bad : new byte[][] {badMagic, badVersion, wrongType}) {
            assertNull(Protocol.decodeVehicleState(bad, bad.length));
        }
        assertNull(Protocol.decodeVehicleState(data, data.length - 1));
        byte[] longer = java.util.Arrays.copyOf(data, 69); // a real header with a byte too many
        assertNull(Protocol.decodeVehicleState(longer, 69));
        byte[] state = Files.readAllBytes(GOLDEN.resolve("state_v2.bin"));
        assertNull(Protocol.decodeVehicleState(state, state.length));
        byte[] echo = Files.readAllBytes(GOLDEN.resolve("echo_v2.bin"));
        assertNull(Protocol.decodeEcho(data, data.length)); // a VehicleState is not an Echo
        assertNotNull(echo);
    }

    @Test
    void typeOfReadsOnlyTheHeader() throws Exception {
        byte[] state = Files.readAllBytes(GOLDEN.resolve("vehicle_state_v2.bin"));
        assertEquals(Protocol.TYPE_VEHICLE_STATE, Protocol.typeOf(state, state.length));
        assertEquals(Protocol.TYPE_VEHICLE_STATE, Protocol.typeOf(state, 8));
        assertEquals(-1, Protocol.typeOf(state, 7));
        assertEquals(Protocol.TYPE_POSE, Protocol.typeOf(Files.readAllBytes(GOLDEN.resolve("pose_v2.bin")), 24));
        assertEquals(Protocol.TYPE_STATE, Protocol.typeOf(Files.readAllBytes(GOLDEN.resolve("state_v2.bin")), 72));
        byte[] badMagic = state.clone();
        badMagic[0] ^= (byte) 0xFF;
        byte[] badVersion = state.clone();
        badVersion[4] = 3;
        assertEquals(-1, Protocol.typeOf(badMagic, badMagic.length));
        assertEquals(-1, Protocol.typeOf(badVersion, badVersion.length));
        byte[] high = state.clone();
        high[6] = 1;
        high[7] = (byte) 0x80;
        assertEquals(0x8001, Protocol.typeOf(high, high.length)); // the type is unsigned
        assertEquals(5, Protocol.TYPE_VEHICLE_COMMAND);
        assertEquals(6, Protocol.TYPE_VEHICLE_INPUT);
        assertEquals(7, Protocol.TYPE_VEHICLE_STATE);
    }
}
