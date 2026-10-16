package dev.cjmc.skate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

/**
 * The blocks around the skater as the engine's collision world: the sections (16 x 16 x 16) near him are meshed into quads (BlockQuads) and sent as regions, the
 * ones that fall behind are cleared, and a section is sent again when a block in it or next to it changes. Client thread only.
 */
final class SkateWorld {
    /** The sections around the one he is in that the engine knows: this many to each side horizontally and vertically. */
    static final int RADIUS_H = 2;
    static final int RADIUS_V = 1;
    private static final float[][] NONE = {};

    private final Map<Long, Integer> sent = new HashMap<>(); // section -> parts sent
    private final Set<Long> dirty = new LinkedHashSet<>();

    private static long key(int sx, int sy, int sz) {
        return ((long) (sx & 0x3FFFFF) << 42) | ((long) (sy & 0xFFFFF) << 22) | (sz & 0x3FFFFF);
    }

    private static int sectionX(long key) {
        return (int) (key >> 42) << 10 >> 10;
    }

    private static int sectionY(long key) {
        return (int) ((key >> 22) & 0xFFFFF) << 12 >> 12;
    }

    private static int sectionZ(long key) {
        return (int) (key & 0x3FFFFF) << 10 >> 10;
    }

    /** The blocks of a world as the solids of BlockQuads: the boxes of the collision shape, none in the chunks that have not arrived. */
    private record Solids(ClientWorld world) implements BlockQuads.Solids {
        @Override
        public float[][] boxes(int x, int y, int z) {
            if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
                return NONE;
            }
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) {
                return NONE;
            }
            VoxelShape shape = state.getCollisionShape(world, pos);
            if (shape.isEmpty()) {
                return NONE;
            }
            List<Box> boxes = shape.getBoundingBoxes();
            float[][] out = new float[boxes.size()][];
            for (int i = 0; i < out.length; i++) {
                Box b = boxes.get(i);
                float top = (float) b.maxY;
                if (b.minY < 1e-4 && top >= 0.8f && top < 1f && b.minX < 1e-4 && b.minZ < 1e-4 && b.maxX > 1 - 1e-4 && b.maxZ > 1 - 1e-4) {
                    top = 1f; // dirt paths, farmland and the like sit a sixteenth below their neighbours: a ledge the wheels would catch on, so they count as full blocks
                }
                out[i] = new float[] {(float) b.minX, (float) b.minY, (float) b.minZ, (float) b.maxX, top, (float) b.maxZ};
            }
            return out;
        }
    }

    /** A block changed: the sections it touches (its own, and the next ones when it is on an edge, whose faces look at it) are sent again, if the engine has them. */
    void blockChanged(BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long k = key((pos.getX() + dx) >> 4, (pos.getY() + dy) >> 4, (pos.getZ() + dz) >> 4);
                    if (sent.containsKey(k)) {
                        dirty.add(k);
                    }
                }
            }
        }
    }

    /**
     * Sends the missing sections nearest first (at most `budget` of them, and the dirty ones), and clears the far ones. Returns whether anything is still waiting.
     */
    boolean update(ClientWorld world, Vec3d centre, SkateLink link, int budget) {
        int cx = (int) Math.floor(centre.x) >> 4;
        int cy = (int) Math.floor(centre.y) >> 4;
        int cz = (int) Math.floor(centre.z) >> 4;
        // the far ones go
        List<Long> far = new ArrayList<>();
        for (long k : sent.keySet()) {
            if (Math.abs(sectionX(k) - cx) > RADIUS_H + 1 || Math.abs(sectionY(k) - cy) > RADIUS_V + 1 || Math.abs(sectionZ(k) - cz) > RADIUS_H + 1) {
                far.add(k);
            }
        }
        for (long k : far) {
            clear(k, link);
        }
        // the dirty ones, then the missing ones nearest first
        int left = budget;
        List<Long> redo = new ArrayList<>(dirty);
        dirty.clear();
        for (long k : redo) {
            if (left <= 0) {
                dirty.add(k);
                continue;
            }
            if (sent.containsKey(k)) {
                send(world, k, link);
                left--;
            }
        }
        boolean waiting = !dirty.isEmpty();
        List<long[]> missing = new ArrayList<>(); // {distance, key}
        for (int dx = -RADIUS_H; dx <= RADIUS_H; dx++) {
            for (int dy = -RADIUS_V; dy <= RADIUS_V; dy++) {
                for (int dz = -RADIUS_H; dz <= RADIUS_H; dz++) {
                    long k = key(cx + dx, cy + dy, cz + dz);
                    if (!sent.containsKey(k)) {
                        missing.add(new long[] {dx * dx + dy * dy * 4 + dz * dz, k});
                    }
                }
            }
        }
        missing.sort((a, b) -> Long.compare(a[0], b[0]));
        for (long[] m : missing) {
            long k = m[1];
            if (!world.getChunkManager().isChunkLoaded(sectionX(k), sectionZ(k))) {
                continue; // its chunk has not arrived: it is tried again on a later tick
            }
            if (left <= 0) {
                waiting = true;
                break;
            }
            send(world, k, link);
            left--;
        }
        return waiting;
    }

    private void send(ClientWorld world, long k, SkateLink link) {
        int sx = sectionX(k), sy = sectionY(k), sz = sectionZ(k);
        BlockQuads.Result mesh = BlockQuads.build(new Solids(world), sx << 4, sy << 4, sz << 4, 16, 16, 16);
        List<float[]> parts = BlockQuads.split(mesh.quads(), mesh.count(), SkateProtocol.MAX_QUADS);
        int partCount = Math.min(parts.size(), 16);
        for (int i = 0; i < partCount; i++) {
            float[] quads = parts.get(i);
            link.send(SkateProtocol.encodeWorldSet(BlockQuads.regionId(sx, sy, sz, i), quads, quads.length / 12));
        }
        int before = sent.getOrDefault(k, 0);
        for (int i = partCount; i < before; i++) {
            link.send(SkateProtocol.encodeWorldClear(BlockQuads.regionId(sx, sy, sz, i))); // the section lost faces: its extra parts go
        }
        sent.put(k, partCount);
    }

    private void clear(long k, SkateLink link) {
        int parts = sent.remove(k);
        dirty.remove(k);
        for (int i = 0; i < parts; i++) {
            link.send(SkateProtocol.encodeWorldClear(BlockQuads.regionId(sectionX(k), sectionY(k), sectionZ(k), i)));
        }
    }

    void clearAll(SkateLink link) {
        for (long k : new ArrayList<>(sent.keySet())) {
            clear(k, link);
        }
        dirty.clear();
    }
}
