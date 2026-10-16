import struct
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import skate_host_probe as probe  # noqa: E402

FLOOR = [(-.5, 2, -.5), (.5, 2, -.5), (.5, 2, .5), (-.5, 2, .5)]


class PacketTests(unittest.TestCase):
    def test_an_input_has_the_header_a_sequence_and_eighteen_actions(self):
        data = probe.pack_input(7, [0.5] + [0.0] * 17)
        self.assertEqual(len(data), 88)
        self.assertEqual(struct.unpack_from("<IHHI", data, 0), (0x48384B53, 1, 1, 7))
        self.assertEqual(struct.unpack_from("<f", data, 16)[0], 0.5)

    def test_a_region_is_its_id_its_count_and_forty_eight_bytes_per_quad(self):
        data = probe.pack_world_set(9, [FLOOR, FLOOR])
        self.assertEqual(len(data), 16 + 2 * 48)
        self.assertEqual(struct.unpack_from("<IHHII", data, 0), (0x48384B53, 1, 3, 9, 2))

    def test_a_teleport_is_seven_floats(self):
        data = probe.pack_teleport(1.0, 2.0, 3.0, 0.5, (4.0, 5.0, 6.0))
        self.assertEqual(len(data), 8 + 28)
        self.assertEqual(struct.unpack_from("<HH", data, 4), (1, 5))
        self.assertEqual(struct.unpack_from("<7f", data, 8), (1.0, 2.0, 3.0, 0.5, 4.0, 5.0, 6.0))


class PoseTests(unittest.TestCase):
    def test_a_pose_packs_and_unpacks(self):
        root = tuple(float(i) for i in range(16))
        basis = tuple(float(i) for i in range(9))
        bones = [tuple(float(i + b) for i in range(16)) for b in range(2)]
        data = probe.pack_pose(77, root, basis, (1.0, 2.0, 3.0), bones)
        self.assertEqual(len(data), 16 + 64 + 48 + 2 * 64)
        tick, r, b, position, unpacked = probe.unpack_pose(data)
        self.assertEqual((tick, r, b, position), (77, root, basis, (1.0, 2.0, 3.0)))
        self.assertEqual([tuple(x) for x in unpacked], bones)


class FacingTests(unittest.TestCase):
    def test_the_documented_floor_order_faces_up_and_is_kept(self):
        self.assertEqual(probe.facing(FLOOR, (0, 1, 0)), FLOOR)

    def test_a_face_that_must_look_the_other_way_is_reordered(self):
        self.assertNotEqual(probe.facing(FLOOR, (0, -1, 0)), FLOOR)
        wall = [(.5, 0, 0), (-.5, 0, 0), (-.5, 1, 0), (.5, 1, 0)]
        self.assertNotEqual(probe.facing(wall, (0, 0, -1)), probe.facing(wall, (0, 0, 1)))

    def test_the_world_of_blocks_has_regions_of_at_most_a_thousand_quads(self):
        regions = probe.blocks_world()
        self.assertTrue(all(len(quads) <= 1000 for quads in regions.values()))
        self.assertEqual(sum(len(q) for q in regions.values()), 41 * 41 + 11 * 3)


if __name__ == "__main__":
    unittest.main()
