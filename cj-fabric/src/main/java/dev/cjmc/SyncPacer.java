package dev.cjmc;

/** Says on which client ticks the server has to be told what is wanted: when it changes, and every `period` ticks while it is wanted. */
public final class SyncPacer {
    private final int period;
    private boolean wasWanted;
    private int sinceSync;

    public SyncPacer(int period) {
        this.period = period;
    }

    /** One call per client tick; true when the server should be synced now. */
    public boolean tick(boolean wanted) {
        if (wanted != wasWanted) {
            wasWanted = wanted;
            sinceSync = 0;
            return true;
        }
        if (!wanted) {
            return false;
        }
        if (++sinceSync >= period) {
            sinceSync = 0;
            return true;
        }
        return false;
    }
}
