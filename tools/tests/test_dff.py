import math
import struct
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import rwbuild as b  # noqa: E402
from cjconvert import dff  # noqa: E402
from cjconvert.rwchunk import FormatError  # noqa: E402

POS = [(0.0, 0.0, 0.0), (1.0, 0.0, 0.0), (0.0, 1.0, 0.5), (1.0, 1.0, 0.5)]
NRM = [(0.0, 0.0, 1.0)] * 4
UVS = [(0.0, 0.0), (1.0, 0.0), (0.0, 1.0), (1.0, 1.0)]
TRIS = [(0, 1, 2), (2, 1, 3)]
HIER = [0, 1, 5, 303]  # the part's own hierarchy, with a node (303) the skeleton does not have
INDICES = [(0, 0, 0, 0), (1, 2, 0, 0), (2, 0, 0, 0), (1, 0, 0, 0)]
WEIGHTS = [(1.0, 0.0, 0.0, 0.0), (0.25, 0.75, 0.0, 0.0), (1.0, 0.0, 0.0, 0.0), (1.0, 0.0, 0.0, 0.0)]
IDENT = (1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)


def part_dff(**kw):
    sk = b.skin(len(HIER), [0, 1, 2], INDICES, WEIGHTS, [IDENT] * len(HIER))
    return b.dff(HIER, b.geometry(POS, NRM, UVS, TRIS, skin=sk, **kw))


class PartTests(unittest.TestCase):
    def test_reads_geometry_skin_and_hierarchy(self):
        part = dff.parse_part(part_dff())
        self.assertEqual(part.positions, POS)
        self.assertEqual(part.normals, NRM)
        self.assertEqual(part.uvs, UVS)
        self.assertEqual(part.triangles, TRIS)  # (vertex1, vertex2, vertex3), undoing the file's stored order
        self.assertEqual(part.bone_indices, INDICES)
        self.assertEqual([tuple(round(x, 4) for x in w) for w in part.weights], WEIGHTS)
        self.assertEqual(part.hierarchy_ids, tuple(HIER))

    def test_prelit_colours_are_skipped(self):
        part = dff.parse_part(part_dff(prelit=True))
        self.assertEqual(part.positions, POS)
        self.assertEqual(part.triangles, TRIS)

    def test_tri_strip_geometry_is_unsupported(self):
        # Review Focus #3
        with self.assertRaises(dff.UnsupportedGeometry):
            dff.parse_part(part_dff(flags=0x37))

    def test_a_part_without_skin_or_hierarchy_is_rejected(self):
        no_skin = b.dff(HIER, b.geometry(POS, NRM, UVS, TRIS))
        with self.assertRaises(FormatError):
            dff.parse_part(no_skin)
        sk = b.skin(len(HIER), [0], INDICES, WEIGHTS, [IDENT] * len(HIER))
        no_hierarchy = b.dff([], b.geometry(POS, NRM, UVS, TRIS, skin=sk))
        with self.assertRaises(FormatError):
            dff.parse_part(no_hierarchy)

    def test_corrupt_or_truncated_files_are_rejected(self):
        # Review Focus #1
        data = part_dff()
        for cut in (20, 200, len(data) // 2):
            with self.subTest(cut=cut):
                with self.assertRaises(FormatError):
                    dff.parse_part(data[:cut])
        with self.assertRaises(FormatError):
            dff.parse_part(b"\0" * 100)

    def test_a_skin_that_does_not_fit_in_its_chunk_is_rejected(self):
        sk = b.skin(len(HIER), [0, 1, 2], INDICES[:2], WEIGHTS[:2], [IDENT] * len(HIER))  # two vertices too few
        with self.assertRaises(FormatError):
            dff.parse_part(b.dff(HIER, b.geometry(POS, NRM, UVS, TRIS, skin=sk)))


class SkeletonTests(unittest.TestCase):
    def test_reads_node_ids_in_index_order_and_the_inverse_bind_matrices(self):
        ids = [0, 1, 5]
        mats = [tuple(float(i + 10 * k) for k in range(16)) for i in range(3)]
        stub = b.geometry([(0, 0, 0)] * 3, [(0, 0, 1)] * 3, [(0, 0)] * 3, [(0, 1, 2)],
                          skin=b.skin(3, [0], [(0, 0, 0, 0)] * 3, [(1, 0, 0, 0)] * 3, mats))
        sk = dff.parse_skeleton(b.dff(ids, stub))
        self.assertEqual(sk.node_ids, (0, 1, 5))
        self.assertEqual(len(sk.inverse_bind), 3)
        self.assertEqual(sk.inverse_bind[2], mats[2])

    def test_the_matrix_count_must_match_the_node_count(self):
        stub = b.geometry([(0, 0, 0)] * 3, [(0, 0, 1)] * 3, [(0, 0)] * 3, [(0, 1, 2)],
                          skin=b.skin(2, [0], [(0, 0, 0, 0)] * 3, [(1, 0, 0, 0)] * 3, [IDENT] * 2))
        with self.assertRaises(FormatError):
            dff.parse_skeleton(b.dff([0, 1, 5], stub))


GUN_POS = [(0.0, 0.0, 0.0), (1.0, 0.0, 0.0), (0.0, 1.0, 0.5), (1.0, 1.0, 0.5)]
FLASH_POS = [(5.0, 0.0, 0.0), (6.0, 0.0, 0.0), (5.0, 1.0, 0.0)]


def gun_geometry(texture_names=("gun_tex",), strip=True):
    return b.geometry(GUN_POS, NRM, UVS, TRIS, flags=0x37 if strip else 0x36, texture_names=list(texture_names))


def flash_geometry():
    return b.geometry(FLASH_POS, [(0.0, 0.0, 1.0)] * 3, UVS[:3], [(0, 1, 2)], flags=0x37, texture_names=["muzzle"])


class StaticPartTests(unittest.TestCase):
    def test_reads_the_gun_not_the_muzzle_flash(self):
        part = dff.parse_static_part(b.weapon_dff(gun_geometry(), flash_geometry()))
        self.assertEqual(part.positions, GUN_POS)
        self.assertEqual(part.normals, NRM)
        self.assertEqual(part.uvs, UVS)
        self.assertEqual(part.triangles, TRIS)
        self.assertEqual(part.texture, "gun_tex")

    def test_tri_strip_flag_is_fine_for_a_static_part(self):
        # GTA stores the triangle list next to the strips; the flag alone must not stop a static part
        dff.parse_static_part(b.weapon_dff(gun_geometry(strip=True), flash_geometry()))
        dff.parse_static_part(b.weapon_dff(gun_geometry(strip=False), flash_geometry()))

    def test_a_model_whose_root_frame_is_moved_is_refused(self):
        with self.assertRaises(FormatError):
            dff.parse_static_part(b.weapon_dff(gun_geometry(), flash_geometry(), root_pos=(1.0, 0.0, 0.0)))

    def test_two_textures_become_two_groups_by_the_material_of_each_triangle(self):
        gun = b.geometry(GUN_POS, NRM, UVS, TRIS, flags=0x37, texture_names=["belt", "body"], materials=[0, 1])
        part = dff.parse_static_part(b.weapon_dff(gun, flash_geometry()))
        self.assertEqual(part.groups, (("belt", ((0, 1, 2),)), ("body", ((2, 1, 3),))))
        self.assertEqual(part.texture, "belt")
        self.assertEqual(part.triangles, TRIS)

    def test_a_triangle_with_a_material_that_does_not_exist_is_refused(self):
        gun = b.geometry(GUN_POS, NRM, UVS, TRIS, flags=0x37, texture_names=["belt", "body"], materials=[0, 2])
        with self.assertRaises(FormatError):
            dff.parse_static_part(b.weapon_dff(gun, flash_geometry()))

    def test_a_used_material_without_a_texture_is_refused_but_an_unused_one_is_fine(self):
        used = b.geometry(GUN_POS, NRM, UVS, TRIS, flags=0x37, texture_names=["belt", None], materials=[0, 1])
        with self.assertRaises(FormatError):
            dff.parse_static_part(b.weapon_dff(used, flash_geometry()))
        unused = b.geometry(GUN_POS, NRM, UVS, TRIS, flags=0x37, texture_names=["belt", None], materials=[0, 0])
        self.assertEqual(dff.parse_static_part(b.weapon_dff(unused, flash_geometry())).groups[0][0], "belt")

    def test_the_child_atomics_are_left_out_unless_asked_for(self):
        part_geometry = b.geometry(FLASH_POS, [(0.0, 0.0, 1.0)] * 3, UVS[:3], [(0, 1, 2)], flags=0x37, texture_names=["body"])
        data = b.assembled_dff(gun_geometry(), part_geometry, flash_geometry(), part_pos=(10.0, 0.0, 0.0))
        part = dff.parse_static_part(data)
        self.assertEqual(part.positions, GUN_POS)

    def test_children_are_added_moved_by_their_frame_and_the_flash_stays_out(self):
        part_geometry = b.geometry(FLASH_POS, [(0.0, 0.0, 1.0)] * 3, UVS[:3], [(0, 1, 2)], flags=0x37, texture_names=["gun_tex"])
        data = b.assembled_dff(gun_geometry(), part_geometry, flash_geometry(), part_pos=(10.0, 0.0, 0.0))
        part = dff.parse_static_part(data, children=True)
        self.assertEqual(len(part.positions), 4 + 3)  # the body and the barrels; the muzzle flash (texture "muzzle") is not added
        self.assertEqual(part.positions[4:], [(15.0, 0.0, 0.0), (16.0, 0.0, 0.0), (15.0, 1.0, 0.0)])
        self.assertEqual(part.triangles, TRIS + [(4, 5, 6)])  # indices of the added part continue after the body's
        self.assertEqual(part.groups, (("gun_tex", tuple(TRIS + [(4, 5, 6)])),))
        self.assertEqual(part.normals[4:], [(0.0, 0.0, 1.0)] * 3)  # a normal is a direction: the frame's position does not move it
        self.assertEqual(part.uvs[4:], UVS[:3])
        self.assertEqual(part.normals[:4], NRM)
        self.assertEqual(part.uvs[:4], UVS)

    def test_a_frame_nested_below_a_child_is_refused(self):
        part_geometry = b.geometry(FLASH_POS, [(0.0, 0.0, 1.0)] * 3, UVS[:3], [(0, 1, 2)], flags=0x37, texture_names=["gun_tex"])
        data = b.assembled_dff(gun_geometry(), part_geometry, flash_geometry(), part_parent=1)  # a child of the flash's frame
        with self.assertRaises(FormatError):
            dff.parse_static_part(data, children=True)

    def test_a_rotated_child_frame_rotates_its_vertices(self):
        # a quarter turn about the up axis: right = (0, 0, -1), up = (0, 1, 0), at = (1, 0, 0) (x goes to -z, z goes to x)
        rot = (0.0, 0.0, -1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0)
        part_geometry = b.geometry(FLASH_POS, [(0.0, 0.0, 1.0)] * 3, UVS[:3], [(0, 1, 2)], flags=0x37, texture_names=["gun_tex"])
        data = b.assembled_dff(gun_geometry(), part_geometry, flash_geometry(), part_pos=(0.0, 0.0, 0.0), part_rot=rot)
        part = dff.parse_static_part(data, children=True)
        for got, want in zip(part.positions[4:], [(0.0, 0.0, -5.0), (0.0, 0.0, -6.0), (0.0, 1.0, -5.0)]):
            self.assertTrue(all(math.isclose(g, w, abs_tol=1e-5) for g, w in zip(got, want)), f"{got} != {want}")
        self.assertTrue(all(math.isclose(g, w, abs_tol=1e-5) for g, w in zip(part.normals[4], (1.0, 0.0, 0.0))), part.normals[4])

    def test_a_gun_without_a_textured_material_is_refused(self):
        with self.assertRaises(FormatError):
            dff.parse_static_part(b.weapon_dff(gun_geometry(texture_names=()), flash_geometry()))

    def test_a_skinned_part_is_still_refused_when_it_is_a_tri_strip(self):
        # the strip flag stays an error for CJ's own body parts
        with self.assertRaises(dff.UnsupportedGeometry):
            dff.parse_part(part_dff(flags=0x37))


if __name__ == "__main__":
    unittest.main()
