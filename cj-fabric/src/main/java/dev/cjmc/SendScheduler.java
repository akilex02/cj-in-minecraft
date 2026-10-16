package dev.cjmc;

/**
 * Decides on which rendered frames to send a state packet so the average rate stays at 1/interval
 * even when frames do not line up with the interval. Uses wraparound-safe nanoTime comparisons.
 */
public final class SendScheduler {
    private final long intervalNs;
    private final long toleranceNs;
    private long nextNs;
    private boolean started;

    public SendScheduler(long intervalNs) {
        this.intervalNs = intervalNs;
        // A frame that arrives slightly before its slot still sends, otherwise on vsync'd frames every
        // early frame is skipped and the next one is almost a full interval late (about half the rate).
        this.toleranceNs = intervalNs / 2;
    }

    public boolean shouldSend(long nowNs) {
        if (!started) {
            started = true;
            nextNs = nowNs + intervalNs;
            return true;
        }
        if (nowNs - nextNs < -toleranceNs) {
            return false;
        }
        nextNs += intervalNs; // keep the schedule instead of restarting it from now: no drift
        if (nowNs - nextNs >= 0) {
            nextNs = nowNs + intervalNs; // far behind (stall): resume instead of bursting to catch up
        }
        return true;
    }
}
