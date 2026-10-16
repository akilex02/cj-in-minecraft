package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FirstPersonArmsTest {
    // joints of a model: 0 = pelvis (node 1), 1 = right upper arm (node 22), 2 = right hand (node 24)
    private static final int[] NODES = {1, 22, 24};

    private static GlbModel.Part part(int[] joints, float[] weights, int[] indices) {
        int vertices = joints.length / 4;
        return new GlbModel.Part("p", new float[3 * vertices], new float[3 * vertices], new float[2 * vertices], indices, joints, weights, 0);
    }

    @Test
    void theArmJointsAreTheArmsHandsAndFingersOfBothSides() {
        boolean[] arm = FirstPersonArms.armJoints(new int[] {1, 3, 21, 22, 23, 24, 25, 26, 31, 32, 33, 34, 35, 36, 41});
        assertArrayEquals(new boolean[] {false, false, false, true, true, true, true, true, false, true, true, true, true, true, false}, arm);
    }

    @Test
    void aTriangleAllOnTheArmIsKeptOneOnTheBodyIsNot() {
        // vertices 0, 1, 2 on the arm (joint 1 or 2); 3, 4, 5 on the pelvis (joint 0)
        int[] joints = {1, 0, 0, 0, 2, 0, 0, 0, 1, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        float[] weights = {1, 0, 0, 0, 1, 0, 0, 0, 0.5f, 0.5f, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0};
        int[] kept = FirstPersonArms.armTriangles(part(joints, weights, new int[] {0, 1, 2, 3, 4, 5}), FirstPersonArms.armJoints(NODES));
        assertArrayEquals(new int[] {0, 1, 2}, kept);
    }

    @Test
    void aTriangleWithOneVertexMostlyOnTheBodyIsLeftOut() {
        // the shoulder: vertex 2 has only 0.4 of its weight on the arm
        int[] joints = {1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0};
        float[] weights = {1, 0, 0, 0, 1, 0, 0, 0, 0.4f, 0.6f, 0, 0};
        assertEquals(0, FirstPersonArms.armTriangles(part(joints, weights, new int[] {0, 1, 2}), FirstPersonArms.armJoints(NODES)).length);
    }

    @Test
    void aModelWithoutArmBonesHasNoArmParts() {
        int[] joints = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        float[] weights = {1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0};
        GlbModel.Part body = part(joints, weights, new int[] {0, 1, 2});
        assertTrue(FirstPersonArms.armTriangles(body, FirstPersonArms.armJoints(new int[] {1})).length == 0);
    }

    @Test
    void aShoulderVertexPartlyOnTheTorsoIsLeftOutSoTheCutEdgeDoesNotShow() {
        // 0.8 on the arm and 0.2 on the pelvis: the seam where the sleeve meets the shoulder, whose stretched edge would show as a spike from the eyes
        int[] joints = {1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0};
        float[] weights = {1, 0, 0, 0, 1, 0, 0, 0, 0.8f, 0.2f, 0, 0};
        assertEquals(0, FirstPersonArms.armTriangles(part(joints, weights, new int[] {0, 1, 2}), FirstPersonArms.armJoints(NODES)).length);
    }

    @Test
    void withoutTheHeadTheBodyLosesTheTrianglesOnTheHeadAndKeepsTheRest() {
        // joints: 0 = pelvis (node 1), 1 = head (node 5); vertices 0-2 on the pelvis, 3-5 on the head, 6 half and half
        int[] nodes = {1, 5};
        int[] joints = new int[4 * 7];
        float[] weights = new float[4 * 7];
        for (int v = 0; v < 7; v++) {
            joints[4 * v] = v >= 3 && v < 6 ? 1 : 0;
            weights[4 * v] = 1f;
        }
        joints[4 * 6] = 0;
        weights[4 * 6] = 0.5f;
        joints[4 * 6 + 1] = 1;
        weights[4 * 6 + 1] = 0.5f;
        GlbModel.Part body = part(joints, weights, new int[] {0, 1, 2, 3, 4, 5, 0, 1, 6});
        assertArrayEquals(new int[] {0, 1, 2}, FirstPersonArms.withoutHead(body, FirstPersonArms.headJoints(nodes)));
    }
}
