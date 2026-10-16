package dev.cjmc;

/**
 * Turns the shot events (each sent twice, maybe lost, maybe from before a GTA restart) into "rounds that are new".
 * shotSeq is the total of rounds GTA has fired, including the event's own; it only ever goes up within one GTA run.
 */
public final class ShotDedup {
    private boolean started;
    private int last;

    public synchronized int newRounds(int shotSeq, int rounds) {
        if (!started) {
            started = true;
            last = shotSeq - rounds; // the first event counts its own rounds, not the whole history
        }
        int fresh = shotSeq - last; // wraparound-safe
        if (fresh < 0) {
            last = shotSeq - rounds; // the counter went back: GTA restarted (loopback UDP does not reorder datagrams)
            fresh = rounds;
        }
        if (fresh <= 0) {
            return 0;
        }
        last = shotSeq;
        return Math.min(fresh, rounds);
    }
}
