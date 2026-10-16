package dev.cjmc;

/**
 * A tank that burns while it is used and fills while it is not (the jetpack's fuel, the car's). Once it runs dry it gives nothing until a share of
 * it (`resumeAt`) is back, or holding the key would flicker on every tick that recharges a little. Pure logic: the client calls update every tick.
 */
public final class FuelTank {
    private static final double EMPTY = 1e-9;

    private final double drainPerSecond;
    private final double rechargePerSecond;
    private final double resumeAt;
    private double level = 1.0; // 0 to 1
    private boolean locked;

    public FuelTank(double secondsToEmpty, double secondsToFull, double resumeAt) {
        this.drainPerSecond = 1.0 / secondsToEmpty;
        this.rechargePerSecond = 1.0 / secondsToFull;
        this.resumeAt = resumeAt;
    }

    /** 12 seconds of thrust, 20 to fill from empty, the thrust is back at a quarter. */
    public static FuelTank jetpack() {
        return new FuelTank(12.0, 20.0, 0.25);
    }

    /** 40 seconds with the pedals down, 60 to fill from empty, the car runs again at a quarter. */
    public static FuelTank car() {
        return new FuelTank(40.0, 60.0, 0.25);
    }

    /** How much is left, from 0 to 1. */
    public double level() {
        return level;
    }

    /** Dry and waiting for a quarter to be back: nothing can be spent. */
    public boolean locked() {
        return locked;
    }

    public void fill() {
        level = 1.0;
        locked = false;
    }

    /** Moves the tank on by `seconds`; returns whether fuel was spent this tick (so the thrust or the throttle may go on). */
    public boolean update(boolean wantsToSpend, double seconds) {
        if (!locked && wantsToSpend) {
            level -= drainPerSecond * seconds;
            if (level <= EMPTY) {
                level = 0.0;
                locked = true;
            }
            return true;
        }
        level = Math.min(1.0, level + rechargePerSecond * seconds);
        if (locked && level >= resumeAt) {
            locked = false;
        }
        return false;
    }
}
