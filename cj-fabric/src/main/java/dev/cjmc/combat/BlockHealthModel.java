package dev.cjmc.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The health of the blocks that are being shot, for one world. A block has health = max(hardness, minHardness) * healthPerHardness;
 * each hit takes `power` off it, and a block left alone for regenDelayTicks heals regenPerTick per tick. Positions are
 * BlockPos.asLong() values. Pure bookkeeping, no Minecraft: the caller reads the hardness and shows the cracks.
 */
public final class BlockHealthModel {
    public record Rules(double healthPerHardness, double minHardness, long regenDelayTicks, double regenPerTick) {}

    /** immune: the block cannot be broken by shots; broken: this hit finished it; stage: the cracks to show, 0..9 (-1: none). */
    public record Hit(boolean immune, boolean broken, int stage) {}

    /** The cracks of a position changed (stage -1: they are gone and the block is forgotten). */
    public record Change(long pos, int stage) {}

    private static final double EPS = 1e-9;

    private static final class Entry {
        double hardness;
        double max;
        double health;
        long lastHit;
        int reportedStage;
    }

    private final Rules rules;
    private final Map<Long, Entry> entries = new HashMap<>();

    public BlockHealthModel(Rules rules) {
        this.rules = rules;
    }

    public Hit hit(long pos, double hardness, double power, long tick) {
        if (!(hardness >= 0) || !Double.isFinite(hardness)) {
            return new Hit(true, false, -1); // bedrock and the like (negative hardness), or nonsense
        }
        Entry e = entries.get(pos);
        if (e != null && Math.abs(e.hardness - hardness) > 1e-6) {
            entries.remove(pos); // another block now: it does not inherit the damage
            e = null;
        }
        if (!(power > 0) || !Double.isFinite(power)) {
            return new Hit(false, false, e == null ? -1 : e.reportedStage);
        }
        if (e == null) {
            e = new Entry();
            e.hardness = hardness;
            e.max = Math.max(hardness, rules.minHardness()) * rules.healthPerHardness();
            e.health = e.max;
            e.reportedStage = -1;
            entries.put(pos, e);
        }
        e.health -= power;
        e.lastHit = tick;
        if (e.health <= EPS) {
            entries.remove(pos);
            return new Hit(false, true, -1);
        }
        e.reportedStage = stage(e);
        return new Hit(false, false, e.reportedStage);
    }

    /** Heals the blocks that have been left alone long enough; returns the positions whose cracks changed. */
    public List<Change> tick(long now) {
        List<Change> changes = new ArrayList<>();
        if (rules.regenPerTick() <= 0) {
            return changes;
        }
        Iterator<Map.Entry<Long, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Entry> en = it.next();
            Entry e = en.getValue();
            if (now - e.lastHit <= rules.regenDelayTicks()) {
                continue;
            }
            e.health = Math.min(e.max, e.health + rules.regenPerTick());
            if (e.health >= e.max - EPS) {
                it.remove();
                changes.add(new Change(en.getKey(), -1));
                continue;
            }
            int s = stage(e);
            if (s != e.reportedStage) {
                e.reportedStage = s;
                changes.add(new Change(en.getKey(), s));
            }
        }
        return changes;
    }

    public void clear() {
        entries.clear();
    }

    public int tracked() {
        return entries.size();
    }

    private static int stage(Entry e) {
        int s = (int) Math.floor((1.0 - e.health / e.max) * 10.0);
        return Math.max(0, Math.min(9, s));
    }
}
