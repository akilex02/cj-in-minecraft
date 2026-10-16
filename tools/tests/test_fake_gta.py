import math
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent.parent / "protocol"))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import cjproto  # noqa: E402
import fake_gta  # noqa: E402
import make_mini_glb  # noqa: E402
from cjconvert import glb  # noqa: E402


def mini():
    return glb.read_glb(glb.to_glb(make_mini_glb.mini_model()))


def quat_to_matrix(pos, q):
    """Column-major 4x4 of a rigid transform: rotation by the unit quaternion, then translation."""
    x, y, z, w = q
    return [1 - 2 * (y * y + z * z), 2 * (x * y + w * z), 2 * (x * z - w * y), 0,
            2 * (x * y - w * z), 1 - 2 * (x * x + z * z), 2 * (y * z + w * x), 0,
            2 * (x * z + w * y), 2 * (y * z - w * x), 1 - 2 * (x * x + y * y), 0,
            pos[0], pos[1], pos[2], 1]


def mul(a, b):
    return [sum(a[k * 4 + r] * b[c * 4 + k] for k in range(4)) for c in range(4) for r in range(4)]


class RestPoseTests(unittest.TestCase):
    def test_pose_times_inverse_bind_is_the_identity(self):
        gltf, binary = mini()
        bones = fake_gta.rest_pose(gltf, binary)
        inverse_bind = glb.read_accessor(gltf, binary, gltf["skins"][0]["inverseBindMatrices"])
        self.assertEqual([b[0] for b in bones], [0, 5, 1])
        for (node_id, pos, quat), ib in zip(bones, inverse_bind):
            product = mul(quat_to_matrix(pos, quat), list(ib))
            for i in range(16):
                self.assertAlmostEqual(product[i], 1.0 if i % 5 == 0 else 0.0, places=5, msg=f"node {node_id} element {i}")
            self.assertAlmostEqual(math.sqrt(sum(c * c for c in quat)), 1.0, places=6)

    def test_matrix_to_quaternion_covers_every_branch(self):
        for axis in range(3):
            for angle in (0.0, 0.5, 1.5, 3.0, math.pi, -2.0):
                half = angle / 2
                q = [0.0, 0.0, 0.0, math.cos(half)]
                q[axis] = math.sin(half)
                back = fake_gta.matrix_to_quat(quat_to_matrix((0, 0, 0), q))
                same = all(abs(a - b) < 1e-6 for a, b in zip(back, q))
                neg = all(abs(a + b) < 1e-6 for a, b in zip(back, q))
                self.assertTrue(same or neg, f"axis {axis} angle {angle}: {back} vs {q}")


class AnimationTests(unittest.TestCase):
    def test_spin_turns_every_bone_about_the_up_axis(self):
        gltf, binary = mini()
        bones = fake_gta.rest_pose(gltf, binary)
        spun = fake_gta.spin(bones, math.pi / 2)
        for (_, pos, _), (_, new_pos, new_quat) in zip(bones, spun):
            self.assertAlmostEqual(new_pos[0], -pos[1], places=5)  # (x, y) -> (-y, x)
            self.assertAlmostEqual(new_pos[1], pos[0], places=5)
            self.assertAlmostEqual(new_pos[2], pos[2], places=5)
            self.assertAlmostEqual(math.sqrt(sum(c * c for c in new_quat)), 1.0, places=6)

    def test_spin_equals_a_rotation_applied_to_the_whole_pose(self):
        # the spun pose matrix must be Rz(angle) x the original pose matrix, for the rotation part as well as the position
        gltf, binary = mini()
        bones = fake_gta.rest_pose(gltf, binary)
        # give the bones some orientation, so the rotation part of the composition is exercised
        tilted = [(n, p, (0.38268343, 0.0, 0.0, 0.9238795)) for n, p, _ in bones]  # 45 degrees about X
        angle = 0.7
        c, s = math.cos(angle), math.sin(angle)
        rz = [c, s, 0, 0, -s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]
        for (_, pos, quat), (_, new_pos, new_quat) in zip(tilted, fake_gta.spin(tilted, angle)):
            expected = mul(rz, quat_to_matrix(pos, quat))
            got = quat_to_matrix(new_pos, new_quat)
            for i in range(16):
                self.assertAlmostEqual(expected[i], got[i], places=5, msg=f"element {i}")

    def test_a_spin_of_zero_changes_nothing(self):
        gltf, binary = mini()
        bones = fake_gta.rest_pose(gltf, binary)
        for a, b in zip(bones, fake_gta.spin(bones, 0.0)):
            for u, v in zip(list(a[1]) + list(a[2]), list(b[1]) + list(b[2])):
                self.assertAlmostEqual(u, v, places=6)

    def test_bob_only_changes_the_height(self):
        gltf, binary = mini()
        bones = fake_gta.rest_pose(gltf, binary)
        for (_, pos, quat), (_, new_pos, new_quat) in zip(bones, fake_gta.bob(bones, 0.25)):
            self.assertEqual((pos[0], pos[1]), (new_pos[0], new_pos[1]))
            self.assertAlmostEqual(new_pos[2], pos[2] + 0.25, places=6)
            self.assertEqual(quat, new_quat)

    def test_the_packet_is_a_valid_pose(self):
        gltf, binary = mini()
        pose = fake_gta.to_pose(7, fake_gta.rest_pose(gltf, binary))
        back = cjproto.unpack_pose(cjproto.pack_pose(pose))
        self.assertEqual(back.ack_seq, 7)
        self.assertEqual([b.node_id for b in back.bones], [0, 5, 1])


if __name__ == "__main__":
    unittest.main()
