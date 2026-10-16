import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from cjconvert import assemble, glb  # noqa: E402

GTA = Path(__file__).resolve().parents[2] / "GTA San Andreas"


@unittest.skipUnless((GTA / "models" / "gta3.img").is_file(), "no GTA San Andreas install next to the project")
class RealInstallTests(unittest.TestCase):
    """Facts about the vanilla game that the plan measured by hand. Opt-in: needs the user's own copy."""

    @classmethod
    def setUpClass(cls):
        cls.model = assemble.load_model(GTA)

    def test_the_skeleton_has_the_32_known_nodes_in_order(self):
        self.assertEqual(self.model.skeleton.node_ids,
                         (0, 1, 2, 3, 4, 5, 8, 6, 7, 31, 32, 33, 34, 35, 36, 21, 22, 23, 24, 25, 26, 302, 301, 201,
                          41, 42, 43, 44, 51, 52, 53, 54))

    def test_the_parts_have_the_measured_sizes(self):
        sizes = {p.name: (len(p.positions), len(p.triangles)) for p in self.model.parts}
        self.assertEqual(sizes, {"torso": (405, 646), "head": (581, 756), "hands": (258, 300), "legs": (294, 428), "feet": (116, 128)})  # jeans and chucks

    def test_weights_uvs_and_indices_are_sane(self):
        for p in self.model.parts:
            with self.subTest(p.name):
                self.assertTrue(all(abs(sum(w) - 1.0) < 1e-4 for w in p.weights))
                self.assertTrue(all(max(j) < 32 for j in p.joints))
                self.assertTrue(all(abs(u) <= 4 and abs(v) <= 4 for u, v in p.uvs))  # the feet use v in [-1, 0]: the texture repeats
                self.assertTrue(all(max(t) < len(p.positions) for t in p.triangles))

    def test_cj_is_a_person_sized_and_standing_upright(self):
        zs = [pos[2] for p in self.model.parts for pos in p.positions]
        height = max(zs) - min(zs)
        self.assertTrue(1.5 < height < 2.1, height)
        head = next(p for p in self.model.parts if p.name == "head")
        feet = next(p for p in self.model.parts if p.name == "feet")
        self.assertGreater(min(pos[2] for pos in head.positions), max(pos[2] for pos in feet.positions))  # head above feet
        # forward is +X: the toes stick out ahead of the ankles
        self.assertGreater(max(pos[0] for pos in feet.positions), 0.1)

    def test_it_converts_to_a_glb_a_reader_accepts(self):
        gltf, binary = glb.read_glb(glb.to_glb(self.model))
        self.assertEqual(len(gltf["skins"][0]["joints"]), 32)
        for acc in range(len(gltf["accessors"])):
            glb.read_accessor(gltf, binary, acc)


if __name__ == "__main__":
    unittest.main()
