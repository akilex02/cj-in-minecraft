package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SendSchedulerTest {
    private static final long SECOND = 1_000_000_000L;
    private static final long INTERVAL = SECOND / 60;

    private static int frames;

    /** Counts sends over one second of frames spaced stepNs apart (plus optional jitter), starting at startNs. */
    private static int sendsPerSecond(long startNs, long stepNs, long jitterNs) {
        SendScheduler s = new SendScheduler(INTERVAL);
        int sends = 0;
        frames = 0;
        for (long t = 0; t < SECOND; t += stepNs, frames++) {
            long jitter = jitterNs == 0 ? 0 : ((frames % 2 == 0) ? jitterNs : -jitterNs);
            if (s.shouldSend(startNs + t + jitter)) {
                sends++;
            }
        }
        return sends;
    }

    @Test
    void sixtyHzAtHighFrameRates() {
        int sends = sendsPerSecond(0, SECOND / 144, 0);
        assertTrue(sends >= 59 && sends <= 61, "sent " + sends + " per second at 144 fps");
    }

    @Test
    void sixtyHzOnJitteryVsyncFrames() {
        int sends = sendsPerSecond(0, INTERVAL, 1_000_000L); // 60 fps with +-1 ms jitter
        assertTrue(sends >= 58 && sends <= 62, "sent " + sends + " per second at jittery 60 fps");
    }

    @Test
    void oneSendPerFrameWhenFramesAreSlowerThanTheInterval() {
        int sends = sendsPerSecond(0, SECOND / 30, 0);
        assertEquals(frames, sends);
    }

    @Test
    void worksWithNegativeNanoTime() {
        int sends = sendsPerSecond(-5 * SECOND, SECOND / 144, 0);
        assertTrue(sends >= 59 && sends <= 61, "sent " + sends + " per second with negative nanoTime");
    }

    @Test
    void aStallDoesNotCauseABurst() {
        SendScheduler s = new SendScheduler(INTERVAL);
        assertTrue(s.shouldSend(0));
        // The game freezes for a second, then frames resume at 144 fps.
        long t = SECOND;
        int burst = 0;
        for (int i = 0; i < 3; i++, t += SECOND / 144) {
            if (s.shouldSend(t)) {
                burst++;
            }
        }
        assertTrue(burst <= 2, "sent " + burst + " packets in the first three frames after a stall");
    }
}
