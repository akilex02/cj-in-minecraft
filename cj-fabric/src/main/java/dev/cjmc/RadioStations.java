package dev.cjmc;

/**
 * The radio stations of the car, by the ids the track manager of GTA uses (found live): 1 to 11 are the eleven stations in the order of the game, 13 is the
 * radio off. The bridge tells the one that is tuned in the upper byte of the flags of the vehicle state, as id + 1 (0: it does not say).
 */
public final class RadioStations {
    public static final int OFF = 13;
    public static final String OFF_NAME = "Radio Off";
    private static final String[] NAMES = {
            "Playback FM", "K Rose", "K-DST", "Bounce FM", "SF-UR", "Radio Los Santos", "Radio X", "CSR 103.9", "K-JAH West", "Master Sounds 98.3", "WCTR"};

    private RadioStations() {}

    /** The name shown for a station id, or null when the id is not on the dial. */
    public static String nameOf(int id) {
        if (id == OFF) {
            return OFF_NAME;
        }
        return id >= 1 && id <= NAMES.length ? NAMES[id - 1] : null;
    }

    /** The station in the flags of a vehicle state, or -1 when they do not say. */
    public static int stationOf(int flags) {
        int code = (flags >> 8) & 0xFF;
        return code == 0 ? -1 : code - 1;
    }
}
