package dev.cjmc.skate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlockQuadsTest {
    private static final float[][] CUBE = {{0, 0, 0, 1, 1, 1}};
    private static final float[][] SLAB = {{0, 0, 0, 1, 0.5f, 1}}; // a bottom slab
    private static final float[][] NONE = {};

    /** A world of full cubes (and maybe one other shape) at the given cells. */
    private static BlockQuads.Solids world(int[][] cubes, int[] otherAt, float[][] other) {
        Set<String> solid = new HashSet<>();
        for (int[] c : cubes) {
            solid.add(c[0] + "," + c[1] + "," + c[2]);
        }
        return (x, y, z) -> {
            if (other != null && x == otherAt[0] && y == otherAt[1] && z == otherAt[2]) {
                return other;
            }
            return solid.contains(x + "," + y + "," + z) ? CUBE : NONE;
        };
    }

    private static BlockQuads.Solids cubes(int[]... cells) {
        return world(cells, null, null);
    }

    /** The engine's normal of quad i: (corner 2 - corner 0) x (corner 1 - corner 0), normalised. */
    private static float[] normal(float[] quads, int i) {
        int o = i * 12;
        float[] u = {quads[o + 3] - quads[o], quads[o + 4] - quads[o + 1], quads[o + 5] - quads[o + 2]};
        float[] v = {quads[o + 6] - quads[o], quads[o + 7] - quads[o + 1], quads[o + 8] - quads[o + 2]};
        float[] n = {v[1] * u[2] - v[2] * u[1], v[2] * u[0] - v[0] * u[2], v[0] * u[1] - v[1] * u[0]};
        float length = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        return new float[] {n[0] / length, n[1] / length, n[2] / length};
    }

    private static float[] centre(float[] quads, int i) {
        float[] c = new float[3];
        for (int corner = 0; corner < 4; corner++) {
            for (int axis = 0; axis < 3; axis++) {
                c[axis] += quads[i * 12 + corner * 3 + axis] / 4f;
            }
        }
        return c;
    }

    @Test
    void aSingleCubeHasSixFacesEachLookingOutward() {
        BlockQuads.Result r = BlockQuads.build(cubes(new int[] {5, 7, 9}), 4, 6, 8, 3, 3, 3);
        assertEquals(6, r.count());
        float[] middle = {5.5f, 7.5f, 9.5f};
        Set<String> directions = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            float[] n = normal(r.quads(), i);
            float[] c = centre(r.quads(), i);
            // the face is on the cube's surface and its normal points away from the middle of the cube
            float towards = (c[0] - middle[0]) * n[0] + (c[1] - middle[1]) * n[1] + (c[2] - middle[2]) * n[2];
            assertTrue(towards > 0.49f && towards < 0.51f, "face " + i + " looks outward: " + towards);
            directions.add(Math.round(n[0]) + "," + Math.round(n[1]) + "," + Math.round(n[2]));
        }
        assertEquals(6, directions.size());
    }

    @Test
    void theTopOfAFloorFacesUpAsTheDocumentedOrderWants() {
        BlockQuads.Result r = BlockQuads.build(cubes(new int[] {0, 0, 0}), 0, 0, 0, 1, 1, 1);
        boolean found = false;
        for (int i = 0; i < r.count(); i++) {
            float[] c = centre(r.quads(), i);
            if (Math.abs(c[1] - 1f) < 1e-5f) {
                found = true;
                assertArrayEquals(new float[] {0, 1, 0}, normal(r.quads(), i), 1e-5f);
            }
        }
        assertTrue(found);
    }

    @Test
    void twoCubesSideBySideLoseTheFacesThatTouch() {
        BlockQuads.Result r = BlockQuads.build(cubes(new int[] {0, 0, 0}, new int[] {1, 0, 0}), 0, 0, 0, 2, 1, 1);
        // the top, the bottom and the two long sides each merge into one quad, and the two ends stay
        assertEquals(6, r.count());
    }

    @Test
    void aFloorOfThreeByThreeIsItsTopItsBottomAndItsSides() {
        int[][] cells = new int[9][];
        for (int i = 0; i < 9; i++) {
            cells[i] = new int[] {i % 3, 0, i / 3};
        }
        // a 3x3 floor is six quads once the faces of one plane merge: top, bottom and the four sides
        assertEquals(6, BlockQuads.build(cubes(cells), 0, 0, 0, 3, 1, 3).count());
    }

    @Test
    void aSlabKeepsItsTopFaceInsideTheCellAndLosesItsBottomAgainstACubeBelow() {
        BlockQuads.Solids w = world(new int[][] {{0, 0, 0}}, new int[] {0, 1, 0}, SLAB);
        BlockQuads.Result r = BlockQuads.build(w, 0, 0, 0, 1, 2, 1);
        // a face is hidden only when it fills the whole face of its cell and a full cube is across it. The cube's six faces stay (its top looks at a slab, which is
        // not a full cube); the slab loses its bottom, which fills the cell and rests on the cube, and keeps its top (half way up the cell) and its four sides
        assertEquals(6 + 5, r.count());
        boolean slabTop = false;
        for (int i = 0; i < r.count(); i++) {
            float[] c = centre(r.quads(), i);
            if (Math.abs(c[1] - 1.5f) < 1e-5f) {
                slabTop = true;
                assertArrayEquals(new float[] {0, 1, 0}, normal(r.quads(), i), 1e-5f);
            }
        }
        assertTrue(slabTop, "the top of the slab, half way up the cell, is there");
    }

    @Test
    void aSlabNextToACubeKeepsAllItsFacesBecauseItsSideDoesNotFillTheCellFace() {
        BlockQuads.Solids w = world(new int[][] {{0, 0, 0}}, new int[] {1, 0, 0}, SLAB);
        BlockQuads.Result r = BlockQuads.build(w, 0, 0, 0, 2, 1, 1);
        // the cube's +x face looks at a slab (not a full cube): it stays; the slab's -x face covers only the lower half of the cell face: it stays too;
        // the bottoms of the cube and the slab are in one plane and merge
        assertEquals(6 + 6 - 1, r.count());
    }

    @Test
    void anEmptyAreaHasNoFaces() {
        assertEquals(0, BlockQuads.build(cubes(), 0, 0, 0, 4, 4, 4).count());
    }

    @Test
    void theFacesOfAnAreaAreOnlyThoseOfItsOwnCells() {
        // a cube outside the area is not meshed (it belongs to another section), and a face toward it from inside still sees it as a full cube
        BlockQuads.Result r = BlockQuads.build(cubes(new int[] {0, 0, 0}, new int[] {1, 0, 0}), 0, 0, 0, 1, 1, 1);
        assertEquals(5, r.count());
    }

    @Test
    void manyQuadsAreSplitIntoRegionsOfAtMostAThousand() {
        float[] quads = new float[2500 * 12];
        for (int i = 0; i < quads.length; i++) {
            quads[i] = i;
        }
        List<float[]> parts = BlockQuads.split(quads, 2500, 1000);
        assertEquals(List.of(1000 * 12, 1000 * 12, 500 * 12), parts.stream().map(p -> p.length).toList());
        assertEquals(quads[1000 * 12], parts.get(1)[0]); // in order, none lost
        assertEquals(quads[2499 * 12 + 11], parts.get(2)[parts.get(2).length - 1]);
    }

    @Test
    void theIdOfARegionIsItsSectionAndItsPartAndItCanBeTakenApart() {
        Set<Integer> seen = new HashSet<>();
        for (int sx = -5; sx <= 5; sx++) {
            for (int sy = -4; sy <= 20; sy++) {
                for (int sz = -5; sz <= 5; sz++) {
                    for (int part = 0; part < 3; part++) {
                        int id = BlockQuads.regionId(sx, sy, sz, part);
                        assertTrue(seen.add(id), "unique");
                        assertArrayEquals(new int[] {sx, sy, sz, part}, BlockQuads.regionParts(id));
                    }
                }
            }
        }
        assertNotEquals(BlockQuads.regionId(0, 0, 0, 0), BlockQuads.regionId(0, 0, 0, 1));
    }

    @Test
    void aMergedFloorIsOneQuadOverTheWholeArea() {
        int[][] cells = new int[12][];
        for (int i = 0; i < 12; i++) {
            cells[i] = new int[] {i % 4, 0, i / 4};
        }
        BlockQuads.Result r = BlockQuads.build(cubes(cells), 0, 0, 0, 4, 1, 3);
        int tops = 0;
        for (int i = 0; i < r.count(); i++) {
            if (normal(r.quads(), i)[1] > 0.99f) {
                tops++;
                assertArrayEquals(new float[] {2f, 1f, 1.5f}, centre(r.quads(), i), 1e-4f);
            }
        }
        assertEquals(1, tops);
    }
}
