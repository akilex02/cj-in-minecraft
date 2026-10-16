package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class SkinnerTest {
    private static final float R = 0.70710678f;

    private static GlbModel model() throws IOException {
        return GlbModel.read(GlbModelTest.mini());
    }

    /** The bind pose of every joint of mini.glb: joint 0 is the inverse of its RwMatrix, the others are the identity. */
    private static JointFrame rest() {
        JointFrame f = new JointFrame(3);
        // joint 0's inverse-bind is columns right=(0,1,0) up=(-1,0,0) at=(0,0,1) pos=(1,2,3); its inverse is a rotation by
        // -90 degrees about Z with position -R^T p = (-2, 1, -3)
        f.has[0] = true;
        f.pos[0] = -2;
        f.pos[1] = 1;
        f.pos[2] = -3;
        f.quat[0] = 0;
        f.quat[1] = 0;
        f.quat[2] = -R;
        f.quat[3] = R;
        for (int j = 1; j < 3; j++) {
            f.has[j] = true;
            f.quat[4 * j + 3] = 1;
        }
        return f;
    }

    /** Where a clump-space vertex of mini.glb ends up in Minecraft space in the bind pose: z is lifted by 1, then (x,y,z)->(y,z,x). */
    private static float[] expected(float x, float y, float z) {
        return new float[] {y, z + 1f, x};
    }

    @Test
    void theBindPoseLeavesTheMeshWhereItIs() throws IOException {
        GlbModel m = model();
        Skinner s = new Skinner(m);
        s.skin(null);
        float[] p = m.parts.get(0).positions();
        for (int v = 0; v < 4; v++) {
            assertArrayEquals(expected(p[3 * v], p[3 * v + 1], p[3 * v + 2]),
                    new float[] {s.positions[0][3 * v], s.positions[0][3 * v + 1], s.positions[0][3 * v + 2]}, 1e-5f);
        }
        // the normal (0,0,1) points up in GTA, so it points up in Minecraft
        assertArrayEquals(new float[] {0, 1, 0}, new float[] {s.normals[0][0], s.normals[0][1], s.normals[0][2]}, 1e-5f);
        assertEquals(0.0f, s.positions[0][3 * 2 + 1], 1e-5f); // vertex 2 has z = -1, the lowest point: after the lift it sits at y = 0
    }

    @Test
    void theRestPoseGivesTheSameResultAsTheBindPose() throws IOException {
        GlbModel m = model();
        Skinner s = new Skinner(m);
        s.skin(null);
        float[] bind = s.positions[0].clone();
        s.skin(rest());
        assertArrayEquals(bind, s.positions[0], 1e-5f);
    }

    @Test
    void theLowestPointOfTheBindPoseTouchesTheGround() throws IOException {
        Skinner s = new Skinner(model());
        s.skin(null);
        float min = Float.MAX_VALUE;
        for (float[] part : s.positions) {
            for (int i = 1; i < part.length; i += 3) {
                min = Math.min(min, part[i]);
            }
        }
        assertEquals(0.0f, min, 1e-5f);
    }

    @Test
    void movingAJointMovesOnlyTheVerticesWeightedToIt() throws IOException {
        Skinner s = new Skinner(model());
        s.skin(null);
        float[] bind = s.positions[0].clone();
        JointFrame f = rest();
        f.pos[3 * 1] = 0.5f; // joint 1 moves 0.5 forward (GTA +X)
        s.skin(f);
        float[] out = s.positions[0];
        // vertex 0 (joint 0 only): unchanged
        assertArrayEquals(new float[] {bind[0], bind[1], bind[2]}, new float[] {out[0], out[1], out[2]}, 1e-5f);
        // vertex 3 (100% joint 1): moved 0.5 along Minecraft +Z (forward)
        assertEquals(bind[3 * 3 + 2] + 0.5f, out[3 * 3 + 2], 1e-5f);
        assertEquals(bind[3 * 3], out[3 * 3], 1e-5f);
        // vertex 1 (25% joint 1, 75% joint 2): moved a quarter of that
        assertEquals(bind[3 + 2] + 0.125f, out[3 + 2], 1e-5f);
    }

    @Test
    void rotatingAJointRotatesItsVerticesAndNormalsAboutIt() throws IOException {
        GlbModel m = model();
        Skinner s = new Skinner(m);
        JointFrame f = rest();
        f.quat[4 * 2] = 0;
        f.quat[4 * 2 + 1] = 0;
        f.quat[4 * 2 + 2] = R; // joint 2: 90 degrees about GTA Z (up)
        f.quat[4 * 2 + 3] = R;
        s.skin(f);
        // vertex 2 is (0,2,-1) in GTA space and belongs 100% to joint 2: (x,y)->(-y,x) gives (-2,0,-1)
        // -> lift z by 1 -> (-2,0,0) -> Minecraft (y,z,x) = (0,0,-2)
        assertArrayEquals(new float[] {0, 0, -2},
                new float[] {s.positions[0][6], s.positions[0][7], s.positions[0][8]}, 1e-5f);
        // a rotation about Z leaves the up normal alone
        assertArrayEquals(new float[] {0, 1, 0}, new float[] {s.normals[0][6], s.normals[0][7], s.normals[0][8]}, 1e-5f);
    }

    @Test
    void jointsWithoutAPoseKeepTheirBindPose() throws IOException {
        // Review Focus #4
        Skinner s = new Skinner(model());
        s.skin(null);
        float[] bind = s.positions[0].clone();
        JointFrame empty = new JointFrame(3); // has[] all false
        s.skin(empty);
        assertArrayEquals(bind, s.positions[0], 1e-5f);
    }

    @Test
    void neverProducesNonFiniteNumbers() throws IOException {
        // Review Focus #5: zero weights and zero-length normals must not leak a NaN to the graphics card
        GlbModel real = model();
        GlbModel.Part p = real.parts.get(0);
        float[] zeroWeights = new float[p.weights().length]; // every weight 0
        float[] zeroNormals = new float[p.normals().length]; // every normal (0,0,0)
        GlbModel.Part broken = new GlbModel.Part("broken", p.positions(), zeroNormals, p.uvs(), p.indices(), p.joints(), zeroWeights, 0);
        GlbModel model = new GlbModelAccess(real, broken).model();
        Skinner s = new Skinner(model);
        s.skin(rest());
        for (float v : s.positions[0]) {
            assertTrue(Float.isFinite(v));
        }
        for (float v : s.normals[0]) {
            assertTrue(Float.isFinite(v));
        }
    }

    @Test
    void theSkinnerReusesItsOutputArrays() throws IOException {
        Skinner s = new Skinner(model());
        float[] before = s.positions[0];
        s.skin(rest());
        s.skin(null);
        assertSame(before, s.positions[0]);
    }

    @Test
    void anExplicitGroundOffsetReplacesTheModelsOwn() throws IOException {
        GlbModel m = model();
        Skinner a = new Skinner(m);
        Skinner b = new Skinner(m);
        a.skin(rest());
        b.skin(rest(), m.groundOffset + 2f);
        // the Minecraft Y axis is the model's Z: every vertex is 2 higher
        for (int i = 0; i < a.positions[0].length; i += 3) {
            assertEquals(a.positions[0][i + 1] + 2f, b.positions[0][i + 1], 1e-5f);
        }
    }
}

final class GlbModelAccess {
    private final GlbModel model;

    GlbModelAccess(GlbModel base, GlbModel.Part replacement) {
        try {
            var c = GlbModel.class.getDeclaredConstructor(java.util.List.class, int[].class, float[].class, java.util.List.class, float.class);
            c.setAccessible(true);
            this.model = c.newInstance(java.util.List.of(replacement), base.jointNodeIds, base.inverseBind, base.images, base.groundOffset);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    GlbModel model() {
        return model;
    }
}
