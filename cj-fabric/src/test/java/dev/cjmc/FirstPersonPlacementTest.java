package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class FirstPersonPlacementTest {
    private static final float[] HEAD = {0f, 1.6f, 0f};

    @Test
    void theHeadIsTheEye() {
        float[] m = FirstPersonPlacement.modelToCamera(30f, 10f, 30f, HEAD);
        assertArrayEquals(new float[] {0, 0, 0}, FirstPersonPlacement.apply(m, HEAD), 1e-5f);
    }

    @Test
    void lookingTheWayTheBodyFacesWhatIsAheadOfHimIsAheadOfTheCamera() {
        // the model faces +z; the camera looks down -z with +x to its right (his left is the camera's left: -x)
        float[] m = FirstPersonPlacement.modelToCamera(0f, 0f, 0f, HEAD);
        assertArrayEquals(new float[] {0, 0, -1}, FirstPersonPlacement.apply(m, new float[] {0, 1.6f, 1}), 1e-5f);
        assertArrayEquals(new float[] {-1, 0, 0}, FirstPersonPlacement.apply(m, new float[] {1, 1.6f, 0}), 1e-5f);
        assertArrayEquals(new float[] {0, 1, 0}, FirstPersonPlacement.apply(m, new float[] {0, 2.6f, 0}), 1e-5f);
    }

    @Test
    void aBodyTurnedAwayFromTheLookShowsHisFrontToTheSide() {
        // body facing yaw 90 (toward -x in the world), camera facing yaw 0 (+z, so +x is at its left and -x at its right): his front is at the camera's right
        float[] m = FirstPersonPlacement.modelToCamera(0f, 0f, 90f, HEAD);
        assertArrayEquals(new float[] {1, 0, 0}, FirstPersonPlacement.apply(m, new float[] {0, 1.6f, 1}), 1e-5f);
    }

    @Test
    void lookingDownTheThingsAheadRiseInTheView() {
        // pitch 90: looking straight down; what is ahead of him (level) is now above the middle of the screen
        float[] m = FirstPersonPlacement.modelToCamera(0f, 90f, 0f, HEAD);
        assertArrayEquals(new float[] {0, 1, 0}, FirstPersonPlacement.apply(m, new float[] {0, 1.6f, 1}), 1e-5f);
    }

    @Test
    void theHeadComesFromTheJointInMinecraftsAxes() {
        // GTA's model space (x forward, y left, z up) -> Minecraft's (x left, y up, z forward), lifted by the ground offset
        JointFrame f = new JointFrame(2);
        f.pos[3] = 0.1f;   // forward
        f.pos[4] = 0.2f;   // left
        f.pos[5] = 0.6f;   // up
        assertArrayEquals(new float[] {0.2f, 1.6f, 0.1f}, FirstPersonPlacement.headInModel(f, 1, 1.0f), 1e-6f);
    }
}
