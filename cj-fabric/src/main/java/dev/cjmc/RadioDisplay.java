package dev.cjmc;

/**
 * What the screen says about the radio: the name of the station for a few seconds when it is a new one (also when getting in), fading out at the end.
 * Pure; the clock is the caller's, in milliseconds.
 */
public final class RadioDisplay {
    public static final long SHOW_MS = 5000;
    public static final long FADE_MS = 700;
    private static final int UNSET = -2;

    private int lastStation = UNSET;
    private String name;
    private long shownAtMs;

    /** Called every tick with the station the bridge says (-1: unknown) and whether CJ is at the wheel. */
    public void observe(int station, boolean driving, long nowMs) {
        if (!driving) {
            lastStation = UNSET;
            name = null;
            return;
        }
        if (station == lastStation) {
            return;
        }
        lastStation = station;
        String n = RadioStations.nameOf(station);
        if (n != null) {
            name = n;
            shownAtMs = nowMs;
        }
    }

    /** The name to show now, or null. */
    public String text(long nowMs) {
        long age = nowMs - shownAtMs;
        return name != null && age >= 0 && age < SHOW_MS ? name : null;
    }

    /** 1 while the name is fully shown, falling to 0 in the last FADE_MS, 0 when there is nothing to show. */
    public double alpha(long nowMs) {
        if (text(nowMs) == null) {
            return 0.0;
        }
        long left = SHOW_MS - (nowMs - shownAtMs);
        return left >= FADE_MS ? 1.0 : (double) left / FADE_MS;
    }
}
