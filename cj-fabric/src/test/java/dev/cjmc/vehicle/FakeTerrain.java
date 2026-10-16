package dev.cjmc.vehicle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A world of mock columns: ground at a height everywhere (a solid from far below to it) plus extra solid ranges and holes. */
final class FakeTerrain implements Terrain {
    private final double ground;
    private final Map<Long, List<double[]>> extra = new HashMap<>();
    private final Map<Long, Boolean> holes = new HashMap<>();

    FakeTerrain(double ground) {
        this.ground = ground;
    }

    private static long key(int bx, int bz) {
        return ((long) bx << 32) ^ (bz & 0xFFFFFFFFL);
    }

    /** A solid range [bottom, top] over the columns x0..x1 and z0..z1 (inclusive, block coordinates). */
    FakeTerrain solid(int x0, int x1, int z0, int z1, double bottom, double top) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                extra.computeIfAbsent(key(x, z), k -> new ArrayList<>()).add(new double[] {bottom, top});
            }
        }
        return this;
    }

    FakeTerrain hole(int x0, int x1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                holes.put(key(x, z), true);
            }
        }
        return this;
    }

    @Override
    public double top(int bx, int bz, double maxY) {
        double best = holes.containsKey(key(bx, bz)) ? Double.NEGATIVE_INFINITY : (ground <= maxY ? ground : Double.NEGATIVE_INFINITY);
        for (double[] range : extra.getOrDefault(key(bx, bz), List.of())) {
            if (range[1] <= maxY) {
                best = Math.max(best, range[1]);
            }
        }
        return best;
    }

    @Override
    public double ceiling(int bx, int bz, double fromY) {
        double best = Double.POSITIVE_INFINITY;
        for (double[] range : extra.getOrDefault(key(bx, bz), List.of())) {
            if (range[0] >= fromY - 1e-9) {
                best = Math.min(best, range[0]);
            }
        }
        return best;
    }
}
