package dev.cjmc.combat;

/** How far a rocket has flown: it moves speed/20 blocks per tick (the server runs 20 ticks a second) until its range is used up. */
public final class RocketFlight {
    private static final double EPS = 1e-9;

    private final double stepLength;
    private final double maxRange;
    private double travelled;

    public RocketFlight(double speedBlocksPerSecond, double maxRange) {
        if (!(speedBlocksPerSecond > 0) || Double.isInfinite(speedBlocksPerSecond) || !(maxRange > 0) || Double.isInfinite(maxRange)) {
            throw new IllegalArgumentException("a rocket needs a finite speed and range above 0, got " + speedBlocksPerSecond + " and " + maxRange);
        }
        this.stepLength = speedBlocksPerSecond / 20.0;
        this.maxRange = maxRange;
    }

    /** The distance to fly this tick: a whole step, or what is left of the range. Zero once the range is used up. */
    public double advance() {
        double step = Math.min(stepLength, maxRange - travelled);
        if (step <= 0) {
            return 0.0;
        }
        travelled += step;
        return step;
    }

    public boolean expired() {
        return travelled >= maxRange - EPS;
    }
}
