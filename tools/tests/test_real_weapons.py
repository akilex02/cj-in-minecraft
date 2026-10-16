import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from cjconvert import assemble, glb  # noqa: E402
from cjconvert.weapons import WEAPON_MODELS  # noqa: E402

# vertices and triangles of the six weapons of M2, measured before the minigun work: they must not change
SIX_COUNTS = {"colt45": (152, 144), "desert_eagle": (222, 138), "chromegun": (342, 388), "micro_uzi": (444, 282),
              "ak47": (568, 446), "m4": (644, 420)}

GTA = Path(__file__).resolve().parent.parent.parent / "GTA San Andreas"


@unittest.skipUnless((GTA / "models" / "gta3.img").is_file(), "no GTA San Andreas install next to the project")
class RealWeaponTests(unittest.TestCase):
    def test_the_six_weapons_of_m2_are_one_mesh_and_unchanged(self):
        for name, (vertices, triangles) in SIX_COUNTS.items():
            with self.subTest(weapon=name):
                model = assemble.load_weapon(GTA, name)
                self.assertEqual(len(model.parts), 1)
                self.assertEqual((len(model.parts[0].positions), len(model.parts[0].triangles)), (vertices, triangles))

    def test_every_weapon_converts_and_is_the_size_of_a_gun(self):
        for name in WEAPON_MODELS:
            with self.subTest(weapon=name):
                model = assemble.load_weapon(GTA, name)
                positions = [p for part in model.parts for p in part.positions]
                self.assertGreater(len(positions), 20)
                self.assertGreater(sum(len(part.triangles) for part in model.parts), 20)
                extent = [max(p[k] for p in positions) - min(p[k] for p in positions) for k in range(3)]
                self.assertTrue(0.1 < max(extent) < 2.0, f"{name} spans {extent} m")
                for texture in model.textures.values():
                    self.assertGreater(sum(texture.rgba[3::4]) // (len(texture.rgba) // 4), 100)  # mostly opaque, not a black square
                glb.read_glb(glb.to_glb(model))

    def test_the_bat_is_one_mesh(self):
        model = assemble.load_weapon(GTA, "bat")
        self.assertEqual(len(model.parts), 1)
        self.assertEqual((len(model.parts[0].positions), len(model.parts[0].triangles)), (91, 128))
        self.assertEqual(list(model.textures), ["bat:alum_bat"])

    def test_the_rocket_launcher_is_one_mesh(self):
        model = assemble.load_weapon(GTA, "rocketla")
        self.assertEqual(len(model.parts), 1)
        self.assertEqual((len(model.parts[0].positions), len(model.parts[0].triangles)), (554, 496))
        self.assertEqual(list(model.textures), ["rocketla:gun_rocket_launcher"])

    def test_the_minigun_has_a_body_and_a_belt_and_its_barrels(self):
        model = assemble.load_weapon(GTA, "minigun")
        self.assertEqual(len(model.parts), 2)  # minigun_2 and bulletbelt
        self.assertEqual(sum(len(p.triangles) for p in model.parts), 582 + 208)


if __name__ == "__main__":
    unittest.main()
