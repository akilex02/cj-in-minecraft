package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RadioStationsTest {
    @Test
    void theElevenStationsAndTheRadioOffHaveNames() {
        for (int id = 1; id <= 11; id++) {
            assertTrue(RadioStations.nameOf(id) != null && !RadioStations.nameOf(id).isBlank(), "station " + id);
        }
        assertEquals(RadioStations.OFF_NAME, RadioStations.nameOf(RadioStations.OFF));
        assertEquals(12, java.util.stream.IntStream.rangeClosed(1, 11).mapToObj(RadioStations::nameOf).distinct().count() + 1, "all different");
    }

    @Test
    void anythingElseHasNoName() {
        for (int id : new int[] {-1, 0, 12, 14, 99, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertNull(RadioStations.nameOf(id), "id " + id);
        }
    }

    @Test
    void theStationIsInTheUpperByteOfTheFlagsAsIdPlusOne() {
        assertEquals(-1, RadioStations.stationOf(Protocol.VEHICLE_FLAG_DRIVER_IN));
        assertEquals(5, RadioStations.stationOf(Protocol.VEHICLE_FLAG_DRIVER_IN | (6 << 8)));
        assertEquals(13, RadioStations.stationOf(1 | (14 << 8)));
        assertEquals(0, RadioStations.stationOf(1 | (1 << 8)));
        assertEquals(-1, RadioStations.stationOf(0xFFFF0001 & 0x00FF)); // only the low byte set
    }

    @Test
    void theCommandsAreTheOnesOfTheProtocol() {
        assertEquals(3, Protocol.VEHICLE_CMD_RADIO_NEXT);
        assertEquals(4, Protocol.VEHICLE_CMD_RADIO_PREVIOUS);
    }
}
