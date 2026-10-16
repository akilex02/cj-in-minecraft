package dev.cjmc.body;

/** The time CJ is down: knocked for DOWN_MS, then getting up for GETUP_MS more, frozen throughout, and immune for IMMUNE_MS after. One knockdown does not chain into another. */
public final class KnockdownTimer {
    public static final long DOWN_MS = 1200;
    public static final long GETUP_MS = 1500;
    public static final long IMMUNE_MS = 3000; // after getting up, so that a run of blows cannot keep him down for ever

    private long startMs;
    private boolean started;

    /** Starts a knockdown; false (and nothing changes) when one is under way or he has only just got up. */
    public boolean start(long nowMs) {
        if (started && nowMs >= startMs && nowMs - startMs < DOWN_MS + GETUP_MS + IMMUNE_MS) {
            return false;
        }
        startMs = nowMs;
        started = true;
        return true;
    }

    public boolean knocked(long nowMs) {
        return started && nowMs >= startMs && nowMs - startMs < DOWN_MS;
    }

    public boolean frozen(long nowMs) {
        return started && nowMs >= startMs && nowMs - startMs < DOWN_MS + GETUP_MS;
    }

    public void clear() {
        started = false;
    }
}
