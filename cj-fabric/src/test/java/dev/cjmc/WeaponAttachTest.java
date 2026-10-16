package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WeaponAttachTest {
    private static final float S = 0.70710678f;

    private static JointFrame hand(float px, float py, float pz, float qx, float qy, float qz, float qw) {
        JointFrame f = new JointFrame(3);
        f.has[2] = true;
        f.pos[6] = px;
        f.pos[7] = py;
        f.pos[8] = pz;
        f.quat[8] = qx;
        f.quat[9] = qy;
        f.quat[10] = qz;
        f.quat[11] = qw;
        return f;
    }

    /** Rotates v by the quaternion q (x, y, z, w). */
    private static float[] rotate(float[] q, float[] v) {
        float[] out = new float[3];
        WeaponAttach.rotate(q, v, out);
        return out;
    }

    private static float[] quatOf(JointFrame f) {
        return new float[] {f.quat[0], f.quat[1], f.quat[2], f.quat[3]};
    }

    @Test
    void anIdentityHoldPutsTheWeaponOnTheHand() {
        JointFrame out = new JointFrame(1);
        WeaponAttach.handFrame(hand(1, 2, 3, 0, 0, S, S), 2, new float[] {0, 0, 0}, new float[] {0, 0, 0}, out);
        assertTrue(out.has[0]);
        assertArrayEquals(new float[] {1, 2, 3}, new float[] {out.pos[0], out.pos[1], out.pos[2]}, 1e-5f);
        assertArrayEquals(new float[] {0, 0, S, S}, quatOf(out), 1e-5f);
    }

    @Test
    void theOffsetTurnsWithTheHand() {
        JointFrame out = new JointFrame(1);
        // the hand is turned 90 degrees about Z: its X axis points along the model's Y
        WeaponAttach.handFrame(hand(1, 2, 3, 0, 0, S, S), 2, new float[] {1, 0, 0}, new float[] {0, 0, 0}, out);
        assertArrayEquals(new float[] {1, 3, 3}, new float[] {out.pos[0], out.pos[1], out.pos[2]}, 1e-5f);
    }

    @Test
    void theHoldRotationIsAppliedInTheHandFrameAboutXThenYThenZ() {
        float[] q = WeaponAttach.euler(90, 0, 90);
        // X first: (0,1,0) -> (0,0,1); then Z leaves it alone. The other order would give (-1,0,0).
        assertArrayEquals(new float[] {0, 0, 1}, rotate(q, new float[] {0, 1, 0}), 1e-5f);
        assertArrayEquals(new float[] {0, 1, 0}, rotate(q, new float[] {1, 0, 0}), 1e-5f);
    }

    @Test
    void theHandRotationComposesOutsideTheHoldRotation() {
        JointFrame out = new JointFrame(1);
        WeaponAttach.handFrame(hand(0, 0, 0, 0, 0, S, S), 2, new float[] {0, 0, 0}, new float[] {90, 0, 0}, out);
        // hold X90 maps (0,1,0) to (0,0,1); the hand's Z90 then leaves (0,0,1) alone
        assertArrayEquals(new float[] {0, 0, 1}, rotate(quatOf(out), new float[] {0, 1, 0}), 1e-5f);
        // hold X90 leaves (1,0,0); the hand's Z90 turns it to (0,1,0)
        assertArrayEquals(new float[] {0, 1, 0}, rotate(quatOf(out), new float[] {1, 0, 0}), 1e-5f);
    }

    @Test
    void theResultIsAUnitQuaternion() {
        JointFrame out = new JointFrame(1);
        WeaponAttach.handFrame(hand(0, 0, 0, 0.1f, 0.2f, 0.3f, 0.9f), 2, new float[] {0, 0, 0}, new float[] {10, 20, 30}, out);
        float len = 0;
        for (int k = 0; k < 4; k++) {
            len += out.quat[k] * out.quat[k];
        }
        assertEquals(1f, len, 1e-4f);
    }

    @Test
    void aMissingHandMeansNoWeapon() {
        JointFrame cj = new JointFrame(3); // no joint has data
        JointFrame out = new JointFrame(1);
        out.has[0] = true;
        WeaponAttach.handFrame(cj, 2, new float[] {0, 0, 0}, new float[] {0, 0, 0}, out);
        assertFalse(out.has[0]);
    }

    @Test
    void theSecondGunOfTheTwinPistolsIsTheLeftHandTurnedOverAndShifted() {
        // the game: the left hand's matrix, turned 180 degrees about X, then shifted (0.04, -0.05, 0) in that turned frame (CVisibilityPlugins, 0x7343A0)
        JointFrame out = new JointFrame(1);
        WeaponAttach.leftHandFrame(hand(1, 2, 3, 0, 0, 0, 1), 2, new float[] {0, 0, 0}, new float[] {0, 0, 0}, out);
        assertTrue(out.has[0]);
        assertArrayEquals(new float[] {1.04f, 2.05f, 3}, new float[] {out.pos[0], out.pos[1], out.pos[2]}, 1e-5f);
        assertArrayEquals(new float[] {1, 0, 0, 0}, quatOf(out), 1e-5f);
    }

    @Test
    void theLeftHoldIsTheRightOneInTheTurnedFrameOfTheLeftHand() {
        JointFrame cj = hand(1, 2, 3, 0, 0, S, S);
        float[] offset = {0.01f, 0.02f, 0.03f};
        float[] rotation = {10, 20, 30};
        JointFrame out = new JointFrame(1);
        WeaponAttach.leftHandFrame(cj, 2, offset, rotation, out);
        // a point p of the weapon: the hold (turn, then offset), the shift of the twin, the half turn about X, and the hand
        float[] p = {0.3f, -0.2f, 0.5f};
        float[] held = rotate(WeaponAttach.euler(10, 20, 30), p);
        float[] shifted = {held[0] + offset[0] + 0.04f, held[1] + offset[1] - 0.05f, held[2] + offset[2]};
        float[] turned = {shifted[0], -shifted[1], -shifted[2]};
        float[] hq = {0, 0, S, S};
        float[] atHand = rotate(hq, turned);
        float[] expected = {1 + atHand[0], 2 + atHand[1], 3 + atHand[2]};
        float[] r = rotate(quatOf(out), p);
        assertArrayEquals(expected, new float[] {out.pos[0] + r[0], out.pos[1] + r[1], out.pos[2] + r[2]}, 1e-5f);
    }

    @Test
    void withoutALeftHandThereIsNoSecondGun() {
        JointFrame out = new JointFrame(1);
        out.has[0] = true;
        WeaponAttach.leftHandFrame(hand(0, 0, 0, 0, 0, 0, 1), 0, new float[] {0, 0, 0}, new float[] {0, 0, 0}, out); // joint 0 has no pose in this frame
        assertFalse(out.has[0]);
        WeaponAttach.leftHandFrame(hand(0, 0, 0, 0, 0, 0, 1), -1, new float[] {0, 0, 0}, new float[] {0, 0, 0}, out);
        assertFalse(out.has[0]);
    }
}
