package dev.cjmc.skate;

import java.util.ArrayList;
import java.util.List;

/**
 * The faces of the solid blocks of an area as the quads the Skate engine takes for its collision world (skate-host/README.md): one quad per face of each box
 * of each block, the corners in the order that makes the face look outward (the engine's triangles are one-sided). A face is hidden when it fills a whole face
 * of its cell and a full cube is across it. Pure: the world comes through `Solids`.
 */
public final class BlockQuads {
    private BlockQuads() {}

    /** The solid boxes of the block at a cell, each as {minX, minY, minZ, maxX, maxY, maxZ} in the cell's own 0 to 1; empty for air, fluids and anything one walks through. */
    public interface Solids {
        float[][] boxes(int x, int y, int z);
    }

    /** `quads` holds 12 floats per quad (four corners of three); only the first `count` quads are valid. */
    public record Result(float[] quads, int count) {}

    private static final float EPSILON = 1e-4f;

    /** A plane of whole-cell faces waiting to be merged: which face, where along its axis, and which cells of the area have one. */
    private record Plane(int face, float at) {}

    /**
     * The quads of the cells of the area [minX, minX + sizeX) x [minY, minY + sizeY) x [minZ, minZ + sizeZ). The faces that fill a whole cell face are merged
     * into the biggest rectangles they make (fewer quads and no seams between neighbours for the board's wheels to catch on); the others stay one by one.
     */
    public static Result build(Solids world, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
        float[] quads = new float[12 * 64];
        int count = 0;
        int[] min = {minX, minY, minZ};
        int[] size = {sizeX, sizeY, sizeZ};
        java.util.Map<Plane, boolean[]> planes = new java.util.LinkedHashMap<>();
        for (int x = minX; x < minX + sizeX; x++) {
            for (int y = minY; y < minY + sizeY; y++) {
                for (int z = minZ; z < minZ + sizeZ; z++) {
                    for (float[] box : world.boxes(x, y, z)) {
                        for (int face = 0; face < 6; face++) {
                            if (hidden(world, x, y, z, box, face)) {
                                continue;
                            }
                            int axis = face / 2;
                            int[] cell = {x, y, z};
                            if (fillsCellFace(box, axis)) {
                                float at = cell[axis] + ((face & 1) == 1 ? box[3 + axis] : box[axis]);
                                int u = (axis + 1) % 3, v = (axis + 2) % 3;
                                boolean[] grid = planes.computeIfAbsent(new Plane(face, at), k -> new boolean[size[u] * size[v]]);
                                grid[(cell[u] - min[u]) * size[v] + (cell[v] - min[v])] = true;
                                continue;
                            }
                            if ((count + 1) * 12 > quads.length) {
                                quads = java.util.Arrays.copyOf(quads, quads.length * 2);
                            }
                            writeFace(quads, count * 12, x, y, z, box, face);
                            count++;
                        }
                    }
                }
            }
        }
        for (var entry : planes.entrySet()) {
            int face = entry.getKey().face();
            int axis = face / 2;
            int u = (axis + 1) % 3, v = (axis + 2) % 3;
            boolean[] grid = entry.getValue();
            for (int i = 0; i < size[u]; i++) {
                for (int j = 0; j < size[v]; j++) {
                    if (!grid[i * size[v] + j]) {
                        continue;
                    }
                    int w = 1;
                    while (j + w < size[v] && grid[i * size[v] + j + w]) {
                        w++;
                    }
                    int h = 1;
                    grow:
                    while (i + h < size[u]) {
                        for (int k = 0; k < w; k++) {
                            if (!grid[(i + h) * size[v] + j + k]) {
                                break grow;
                            }
                        }
                        h++;
                    }
                    for (int a = 0; a < h; a++) {
                        for (int k = 0; k < w; k++) {
                            grid[(i + a) * size[v] + j + k] = false;
                        }
                    }
                    float[] lo = new float[3], hi = new float[3];
                    lo[axis] = hi[axis] = entry.getKey().at();
                    lo[u] = min[u] + i;
                    hi[u] = min[u] + i + h;
                    lo[v] = min[v] + j;
                    hi[v] = min[v] + j + w;
                    if ((count + 1) * 12 > quads.length) {
                        quads = java.util.Arrays.copyOf(quads, quads.length * 2);
                    }
                    // A box flat on the plane, in the world's own coordinates: writeFace takes the face on the side `face` names.
                    writeFace(quads, count * 12, 0, 0, 0, new float[] {lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]}, face);
                    count++;
                }
            }
        }
        return new Result(java.util.Arrays.copyOf(quads, count * 12), count);
    }

    private static boolean fillsCellFace(float[] box, int axis) {
        for (int other = 0; other < 3; other++) {
            if (other != axis && (box[other] > EPSILON || box[3 + other] < 1f - EPSILON)) {
                return false;
            }
        }
        return true;
    }

    // The six faces: -X, +X, -Y, +Y, -Z, +Z.
    private static final int[][] OUTWARD = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};

    private static boolean hidden(Solids world, int x, int y, int z, float[] box, int face) {
        int axis = face / 2;
        boolean high = (face & 1) == 1;
        float plane = high ? box[3 + axis] : box[axis];
        if (Math.abs(plane - (high ? 1f : 0f)) > EPSILON) {
            return false; // the face is inside the cell: another block, or the air, is not across it
        }
        for (int other = 0; other < 3; other++) {
            if (other != axis && (box[other] > EPSILON || box[3 + other] < 1f - EPSILON)) {
                return false; // it does not fill the whole face of the cell
            }
        }
        int[] d = OUTWARD[face];
        return fullCube(world.boxes(x + d[0], y + d[1], z + d[2]));
    }

    private static boolean fullCube(float[][] boxes) {
        if (boxes.length != 1) {
            return false;
        }
        float[] b = boxes[0];
        return b[0] < EPSILON && b[1] < EPSILON && b[2] < EPSILON && b[3] > 1f - EPSILON && b[4] > 1f - EPSILON && b[5] > 1f - EPSILON;
    }

    /**
     * The four corners of a face of a box. The order is the one the engine reads: its triangle is (corner 0, corner 2, corner 1) and its normal
     * (corner 2 - corner 0) x (corner 1 - corner 0), so the floor (x0,z0), (x1,z0), (x1,z1), (x0,z1) looks up. The other faces are the same shape turned.
     */
    private static void writeFace(float[] out, int at, int cx, int cy, int cz, float[] box, int face) {
        float x0 = cx + box[0], y0 = cy + box[1], z0 = cz + box[2], x1 = cx + box[3], y1 = cy + box[4], z1 = cz + box[5];
        float[][] c;
        switch (face) {
            case 3 -> c = new float[][] {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}}; // +Y: the documented floor
            case 2 -> c = new float[][] {{x0, y0, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}}; // -Y: the floor turned over
            case 1 -> c = new float[][] {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}}; // +X
            case 0 -> c = new float[][] {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}}; // -X
            case 5 -> c = new float[][] {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}}; // +Z
            default -> c = new float[][] {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}}; // -Z
        }
        // The cross product says which way the engine will see this order; if it looks inward, the second and fourth corners swap.
        float[] n = engineNormal(c);
        int[] d = OUTWARD[face];
        if (n[0] * d[0] + n[1] * d[1] + n[2] * d[2] < 0) {
            float[] t = c[1];
            c[1] = c[3];
            c[3] = t;
        }
        for (int i = 0; i < 4; i++) {
            out[at + i * 3] = c[i][0];
            out[at + i * 3 + 1] = c[i][1];
            out[at + i * 3 + 2] = c[i][2];
        }
    }

    private static float[] engineNormal(float[][] c) {
        float[] u = {c[1][0] - c[0][0], c[1][1] - c[0][1], c[1][2] - c[0][2]};
        float[] v = {c[2][0] - c[0][0], c[2][1] - c[0][1], c[2][2] - c[0][2]};
        return new float[] {v[1] * u[2] - v[2] * u[1], v[2] * u[0] - v[0] * u[2], v[0] * u[1] - v[1] * u[0]};
    }

    /** `quads` cut into pieces of at most `perRegion` quads, in order. */
    public static List<float[]> split(float[] quads, int count, int perRegion) {
        List<float[]> parts = new ArrayList<>();
        for (int from = 0; from < count; from += perRegion) {
            int n = Math.min(perRegion, count - from);
            parts.add(java.util.Arrays.copyOfRange(quads, from * 12, (from + n) * 12));
        }
        return parts;
    }

    /** The id of a region: the section (x and z within +-512, y within -32 to 31) and the part of it (0 to 15). */
    public static int regionId(int sectionX, int sectionY, int sectionZ, int part) {
        return ((sectionX + 512) & 0x3FF) << 20 | ((sectionY + 32) & 0x3F) << 14 | ((sectionZ + 512) & 0x3FF) << 4 | (part & 0xF);
    }

    /** {sectionX, sectionY, sectionZ, part} of a region id. */
    public static int[] regionParts(int id) {
        return new int[] {((id >>> 20) & 0x3FF) - 512, ((id >>> 14) & 0x3F) - 32, ((id >>> 4) & 0x3FF) - 512, id & 0xF};
    }
}
