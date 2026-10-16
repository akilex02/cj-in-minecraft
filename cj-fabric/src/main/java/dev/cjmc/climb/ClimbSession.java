package dev.cjmc.climb;

/** One climb under way. It ends by itself when the path is over (animation and settle), whatever the clock does. */
public final class ClimbSession {
    private boolean started;
    private long startMs;
    private int height;

    public void start(int height, long nowMs) {
        if (height < ClimbRules.MIN_HEIGHT || height > ClimbRules.MAX_HEIGHT) {
            throw new IllegalArgumentException("height " + height);
        }
        this.height = height;
        startMs = nowMs;
        started = true;
    }

    public boolean active(long nowMs) {
        return started && nowMs >= startMs && !ClimbPath.done(nowMs - startMs);
    }

    /** Whether the climb animation is playing: the flag for the bridge is up only then (the settle after it is frozen but not climbing). */
    public boolean animating(long nowMs) {
        return active(nowMs) && ClimbPath.animating(nowMs - startMs);
    }

    public long elapsed(long nowMs) {
        return nowMs - startMs;
    }

    public int height() {
        return height;
    }

    public void cancel() {
        started = false;
    }
}
