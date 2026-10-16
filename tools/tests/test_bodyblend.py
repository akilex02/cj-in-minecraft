import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from cjconvert.bodyblend import blend_parts, body_ratios  # noqa: E402
from cjconvert.dff import Part  # noqa: E402


def part(x, weights=((1.0, 0.0, 0.0, 0.0),), indices=((0, 0, 0, 0),)):
    return Part([(x, 0.0, 0.0), (x, 1.0, 0.0), (x, 0.0, 1.0)], [(0.0, 0.0, 1.0)] * 3, [(0.0, 0.0), (1.0, 0.0), (0.0, 1.0)], [(0, 1, 2)],
                list(indices) * 3, list(weights) * 3, (0, 1))


class RatioTests(unittest.TestCase):
    # (ripped, fat, normal), as the game takes them: ripped = muscle / 1000, fat = (fat - 200) / 800, normal is what is left
    def test_the_start_of_the_game_is_the_normal_body(self):
        self.assertEqual(body_ratios(0, 0), (0.0, 0.0, 1.0))
        self.assertEqual(body_ratios(0, 200), (0.0, 0.0, 1.0)) # up to 200 of fat is no fat at all

    def test_full_muscle_is_the_ripped_body_and_full_fat_the_fat_one(self):
        self.assertEqual(body_ratios(1000, 0), (1.0, 0.0, 0.0))
        self.assertEqual(body_ratios(0, 1000), (0.0, 1.0, 0.0))

    def test_half_muscle_is_half_ripped_and_half_normal(self):
        self.assertEqual(body_ratios(500, 0), (0.5, 0.0, 0.5))

    def test_muscle_and_fat_together_share_the_body_between_them(self):
        r = body_ratios(1000, 1000)
        self.assertAlmostEqual(r[0], 0.5)
        self.assertAlmostEqual(r[1], 0.5)
        self.assertEqual(r[2], 0.0)
        self.assertAlmostEqual(sum(body_ratios(800, 700)), 1.0)

    def test_stats_out_of_range_are_clamped(self):
        self.assertEqual(body_ratios(5000, -30), (1.0, 0.0, 0.0))


class BlendTests(unittest.TestCase):
    def test_the_vertices_are_the_weighted_sum_of_the_three_bodies(self):
        ripped, fat, normal = part(0.0), part(10.0), part(20.0)
        blended = blend_parts((ripped, fat, normal), (0.5, 0.0, 0.5))
        self.assertAlmostEqual(blended.positions[0][0], 10.0)
        self.assertAlmostEqual(blended.positions[1][1], 1.0)
        blended = blend_parts((ripped, fat, normal), (0.25, 0.25, 0.5))
        self.assertAlmostEqual(blended.positions[0][0], 0.25 * 0 + 0.25 * 10 + 0.5 * 20)

    def test_a_pure_body_is_that_body(self):
        ripped, fat, normal = part(0.0), part(10.0), part(20.0)
        self.assertEqual(blend_parts((ripped, fat, normal), (0.0, 0.0, 1.0)).positions, normal.positions)
        self.assertEqual(blend_parts((ripped, fat, normal), (1.0, 0.0, 0.0)).positions, ripped.positions)

    def test_the_normals_stay_unit_length(self):
        a = Part([(0.0, 0.0, 0.0)] * 3, [(1.0, 0.0, 0.0)] * 3, [(0.0, 0.0)] * 3, [(0, 1, 2)], [(0, 0, 0, 0)] * 3, [(1.0, 0.0, 0.0, 0.0)] * 3, (0,))
        b = Part(a.positions, [(0.0, 1.0, 0.0)] * 3, a.uvs, a.triangles, a.bone_indices, a.weights, a.hierarchy_ids)
        n = blend_parts((a, b, a), (0.5, 0.5, 0.0)).normals[0]
        self.assertAlmostEqual(sum(c * c for c in n), 1.0)

    def test_the_skin_weights_are_those_of_the_body_that_weighs_the_most(self):
        w_ripped, w_normal = ((1.0, 0.0, 0.0, 0.0),), ((0.5, 0.5, 0.0, 0.0),)
        ripped, fat, normal = part(0.0, w_ripped), part(10.0), part(20.0, w_normal)
        self.assertEqual(blend_parts((ripped, fat, normal), (0.7, 0.0, 0.3)).weights[0], (1.0, 0.0, 0.0, 0.0))
        self.assertEqual(blend_parts((ripped, fat, normal), (0.3, 0.0, 0.7)).weights[0], (0.5, 0.5, 0.0, 0.0))

    def test_bodies_with_different_vertex_counts_cannot_be_blended(self):
        short = Part([(0.0, 0.0, 0.0)] * 3, [(0.0, 0.0, 1.0)] * 3, [(0.0, 0.0)] * 3, [(0, 1, 2)], [(0, 0, 0, 0)] * 3, [(1.0, 0.0, 0.0, 0.0)] * 3, (0,))
        longer = Part(short.positions + [(0.0, 0.0, 0.0)], short.normals + [(0.0, 0.0, 1.0)], short.uvs + [(0.0, 0.0)], short.triangles,
                      short.bone_indices + [(0, 0, 0, 0)], short.weights + [(1.0, 0.0, 0.0, 0.0)], short.hierarchy_ids)
        with self.assertRaises(ValueError):
            blend_parts((short, longer, short), (0.3, 0.3, 0.4))


if __name__ == "__main__":
    unittest.main()
