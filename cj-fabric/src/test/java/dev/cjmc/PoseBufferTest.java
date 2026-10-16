package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PoseBufferTest {
    private static final long MS = 1_000_000L;
    // node ids 10 and 20 are joints 0 and 1; node 99 does not exist in the model
    private static final Map<Integer, Integer> JOINT_OF_NODE = Map.of(10, 0, 20, 1);

    private static Protocol.Pose pose(float x, float qz, float qw) {
        return new Protocol.Pose(1, 0, 0, new Protocol.Bone[] {
                new Protocol.Bone(10, x, 0, 0, 0, 0, qz, qw),
                new Protocol.Bone(20, 0, x, 0, 0, 0, 0, 1),
                new Protocol.Bone(99, 5, 5, 5, 0, 0, 0, 1)}); // unknown node: ignored
    }

    private static PoseBuffer buffer() {
        return new PoseBuffer(JOINT_OF_NODE, 2);
    }

    @Test
    void nothingBeforeTheFirstPose() {
        assertNull(buffer().sample(0));
    }

    @Test
    void holdsTheOnlyPoseUntilItGoesStale() {
        PoseBuffer b = buffer();
        long t0 = 1_000 * MS;
        b.push(pose(1, 0, 1), t0);
        JointFrame f = b.sample(t0 + 100 * MS);
        assertNotNull(f);
        assertEquals(1.0f, f.pos[0]);
        assertEquals(1.0f, f.pos[3 + 1]);
        assertTrue(f.has[0] && f.has[1]);
        assertNotNull(b.sample(t0 + PoseBuffer.STALE_NS)); // exactly at the limit: still fresh
        assertNull(b.sample(t0 + PoseBuffer.STALE_NS + 1)); // Review Focus #3: past 500 ms the vanilla player comes back
    }

    @Test
    void interpolatesBetweenTheTwoPosesAroundTheDelayedTime() {
        PoseBuffer b = buffer();
        long t0 = 1_000 * MS;
        b.push(pose(0, 0, 1), t0);
        b.push(pose(1, 0, 1), t0 + 20 * MS);
        // render time t = now - DELAY: a quarter of the way between the two arrivals
        long now = t0 + 5 * MS + PoseBuffer.DELAY_NS;
        JointFrame f = b.sample(now);
        assertEquals(0.25f, f.pos[0], 1e-5f);
        assertEquals(0.25f, f.pos[3 + 1], 1e-5f);
    }

    @Test
    void usesTheOldestBeforeAndTheNewestAfter() {
        PoseBuffer b = buffer();
        long t0 = 1_000 * MS;
        b.push(pose(2, 0, 1), t0);
        b.push(pose(4, 0, 1), t0 + 20 * MS);
        assertEquals(2.0f, b.sample(t0 + PoseBuffer.DELAY_NS - 10 * MS).pos[0]); // delayed time before the oldest
        assertEquals(4.0f, b.sample(t0 + 100 * MS).pos[0]); // after the newest: hold it
    }

    @Test
    void rotationsInterpolateTheShortWay() {
        PoseBuffer b = buffer();
        long t0 = 1_000 * MS;
        // q and -q are the same rotation: halfway between them must still be that rotation, not a zero quaternion
        b.push(pose(0, 0.38268343f, 0.9238795f), t0); // +45 degrees about Z
        b.push(pose(0, -0.38268343f, -0.9238795f), t0 + 20 * MS);
        JointFrame f = b.sample(t0 + 10 * MS + PoseBuffer.DELAY_NS);
        float len = (float) Math.sqrt(f.quat[0] * f.quat[0] + f.quat[1] * f.quat[1] + f.quat[2] * f.quat[2] + f.quat[3] * f.quat[3]);
        assertEquals(1.0f, len, 1e-5f);
        assertEquals(0.38268343f, Math.abs(f.quat[2]), 1e-4f);
    }

    @Test
    void halfwayBetweenTwoRotationsIsTheirMidRotation() {
        PoseBuffer b = buffer();
        long t0 = 1_000 * MS;
        b.push(pose(0, 0, 1), t0);                         // 0 degrees
        b.push(pose(0, 0.7071068f, 0.7071068f), t0 + 20 * MS); // 90 degrees about Z
        JointFrame f = b.sample(t0 + 10 * MS + PoseBuffer.DELAY_NS);
        assertEquals(0.38268343f, f.quat[2], 1e-4f);   // 45 degrees
        assertEquals(0.9238795f, f.quat[3], 1e-4f);
    }

    @Test
    void jointsTheMissingPoseDoesNotHaveStayUnset() {
        // Review Focus #4: a pose with fewer bones, or with node ids the model lacks, never throws
        PoseBuffer b = buffer();
        long t0 = 1_000 * MS;
        b.push(new Protocol.Pose(1, 0, 0, new Protocol.Bone[] {new Protocol.Bone(10, 1, 0, 0, 0, 0, 0, 1)}), t0);
        JointFrame f = b.sample(t0 + 30 * MS);
        assertTrue(f.has[0]);
        assertFalse(f.has[1]);
        b.push(new Protocol.Pose(2, 0, 0, new Protocol.Bone[0]), t0 + 20 * MS);
        JointFrame g = b.sample(t0 + 20 * MS + PoseBuffer.DELAY_NS + MS); // the delayed time is now past the empty pose
        assertNotNull(g);
        assertFalse(g.has[0] || g.has[1]);
    }

    @Test
    void aJointThatOnlyOneOfTwoPosesHasIsTakenFromThatPose() {
        PoseBuffer b = buffer();
        long t0 = 1_000 * MS;
        b.push(new Protocol.Pose(1, 0, 0, new Protocol.Bone[] {new Protocol.Bone(10, 1, 0, 0, 0, 0, 0, 1)}), t0);
        b.push(new Protocol.Pose(2, 0, 0, new Protocol.Bone[] {new Protocol.Bone(10, 3, 0, 0, 0, 0, 0, 1),
                new Protocol.Bone(20, 0, 7, 0, 0, 0, 0, 1)}), t0 + 20 * MS);
        JointFrame f = b.sample(t0 + 10 * MS + PoseBuffer.DELAY_NS);
        assertEquals(2.0f, f.pos[0], 1e-5f);  // joint 0 is in both: blended
        assertTrue(f.has[1]);
        assertEquals(7.0f, f.pos[3 + 1]);     // joint 1 only in the newer one
    }

    @Test
    void keepsOnlyTheMostRecentPoses() {
        PoseBuffer b = buffer();
        for (int i = 0; i < 50; i++) {
            b.push(pose(i, 0, 1), 1_000 * MS + i * 20 * MS);
        }
        assertEquals(8, b.frameCount());
    }

    @Test
    void worksWithNegativeNanoTime() {
        PoseBuffer b = buffer();
        long t0 = -5_000 * MS;
        b.push(pose(0, 0, 1), t0);
        b.push(pose(1, 0, 1), t0 + 20 * MS);
        assertEquals(0.5f, b.sample(t0 + 10 * MS + PoseBuffer.DELAY_NS).pos[0], 1e-5f);
    }

    @Test
    void keepsInterpolatingWhenPosesArriveEvery33ms() {
        // review: GTA at 30 fps sends a pose every ~33 ms; the render time must never run past the newest pose
        PoseBuffer b = buffer();
        long t0 = 1_000 * MS;
        int pushed = 0;
        for (long now = t0; now < t0 + 300 * MS; now += MS) {
            while (pushed * 33 * MS + t0 <= now) { // the poses that have arrived by now
                b.push(pose(pushed, 0, 1), t0 + pushed * 33 * MS);
                pushed++;
            }
            long t = now - PoseBuffer.DELAY_NS;
            if (t < t0 + 33 * MS) {
                continue; // warming up: fewer than two poses
            }
            float expected = (t - t0) / (33f * MS);
            assertEquals(expected, b.sample(now).pos[0], 1e-3f, "now=" + (now - t0) / MS);
        }
    }

    @Test
    void normalisesRotationsAndClampsPositionsFromTheNetwork() {
        // review: a hostile or broken packet must not scale or blow up the mesh
        PoseBuffer b = buffer();
        long t0 = 1_000 * MS;
        b.push(new Protocol.Pose(1, 0, 0, new Protocol.Bone[] {
                new Protocol.Bone(10, 1e30f, 0, 0, 10, 0, 0, 0)}), t0);
        JointFrame f = b.sample(t0);
        float len = 0;
        for (int k = 0; k < 4; k++) {
            len += f.quat[k] * f.quat[k];
        }
        assertEquals(1f, len, 1e-5f);
        assertTrue(Math.abs(f.pos[0]) <= PoseBuffer.MAX_POSITION);
    }

    @Test
    void aZeroRotationBecomesTheIdentity() {
        PoseBuffer b = buffer();
        b.push(new Protocol.Pose(1, 0, 0, new Protocol.Bone[] {
                new Protocol.Bone(10, 0, 0, 0, 0, 0, 0, 0)}), 1_000 * MS);
        JointFrame f = b.sample(1_000 * MS);
        assertEquals(1f, f.quat[3]);
    }

    private static Protocol.Pose facing(float heading) {
        return new Protocol.Pose(1, 0, 0, heading, new Protocol.Bone[] {new Protocol.Bone(10, 0, 0, 0, 0, 0, 0, 1)});
    }

    @Test
    void theHeadingOfThePoseTravelsWithTheFrame() {
        PoseBuffer b = buffer();
        long t0 = 1_000 * MS;
        b.push(facing(1.25f), t0);
        assertEquals(1.25f, b.sample(t0 + 100 * MS).heading, 1e-6f);
    }

    @Test
    void theHeadingInterpolatesTheShortWayAcrossNorth() {
        // from just before a full turn to just after: the middle is north, not south
        PoseBuffer b = buffer();
        long t0 = 1_000 * MS;
        b.push(facing(6.2f), t0);
        b.push(facing(0.2f), t0 + 20 * MS);
        float mid = b.sample(t0 + 10 * MS + PoseBuffer.DELAY_NS).heading;
        float twoPi = (float) (2 * Math.PI);
        float expected = (6.2f + (0.2f + twoPi - 6.2f) / 2) % twoPi;
        assertEquals(expected, mid, 1e-4f);
        assertTrue(mid < twoPi && mid >= 0f);
    }

    @Test
    void aBrokenHeadingNeverReachesTheFrame() {
        PoseBuffer b = buffer();
        long t0 = 1_000 * MS;
        b.push(facing(Float.NaN), t0);
        assertEquals(0f, b.sample(t0).heading);
    }
}
