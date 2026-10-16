package dev.cjmc;

/** A yes/no signal delayed by a number of ticks: what comes out is what went in `delay` ticks ago (nothing, at first). */
public final class DelayedSignal {
    private final boolean[] history;
    private int next;

    public DelayedSignal(int delayTicks) {
        if (delayTicks < 0) {
            throw new IllegalArgumentException("a delay cannot be negative: " + delayTicks);
        }
        this.history = new boolean[delayTicks];
    }

    /** One call per tick with the signal of this tick; returns the signal of `delay` ticks ago. */
    public boolean push(boolean value) {
        if (history.length == 0) {
            return value;
        }
        boolean out = history[next];
        history[next] = value;
        next = (next + 1) % history.length;
        return out;
    }

    public void reset() {
        java.util.Arrays.fill(history, false);
        next = 0;
    }
}
