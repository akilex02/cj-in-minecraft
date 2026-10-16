package dev.cjmc;

/** The numbers of the fuel bar on the screen (FuelHud draws it). Pure. */
public final class FuelBar {
    public static final int WIDTH = 81;
    public static final int HEIGHT = 5;

    private FuelBar() {}

    /** How many of `width` pixels are filled for a level from 0 to 1 (anything outside is clamped). */
    public static int fillPixels(double level, int width) {
        return (int) Math.round(Math.max(0.0, Math.min(1.0, level)) * width);
    }
}
