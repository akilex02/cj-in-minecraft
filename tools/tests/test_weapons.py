import io
import struct
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import rwbuild as b  # noqa: E402
import convert_assets  # noqa: E402
from cjconvert import assemble, glb  # noqa: E402
from cjconvert.weapons import ASSEMBLED, ATTACH_NODES, WEAPON_MODELS  # noqa: E402

IDENT = (1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)
GUN_POS = [(0.0, 0.0, 0.0), (1.0, 0.0, 0.0), (0.0, 1.0, 0.5), (1.0, 1.0, 0.5)]
UVS = [(0.0, 0.0), (1.0, 0.0), (0.0, 1.0), (1.0, 1.0)]
SHIFTED = [(x + 10.0, y, z) for x, y, z in GUN_POS[:3]]  # a second triangle somewhere else, to tell the meshes apart by content
SHIFTED_UVS = [(u * 0.5, v * 0.5) for u, v in UVS[:3]]


def make_install(root, weapons=("colt45",), two_textures=(), assembled=()):
    """A synthetic install: player.dff in gta3.img (so CJ converts) plus the given weapons, each with a DXT1 texture."""
    models = Path(root) / "models"
    models.mkdir(parents=True)
    ids = [0, 1, 5]
    stub = b.geometry([(0, 0, 0)] * 3, [(0, 0, 1)] * 3, [(0, 0)] * 3, [(0, 1, 2)],
                      skin=b.skin(3, [0], [(0, 0, 0, 0)] * 3, [(1, 0, 0, 0)] * 3, [IDENT] * 3))
    files = {"player.dff": b.dff(ids, stub)}
    dxt1 = struct.pack("<HHI", 0xF800, 0x001F, 0xE4)
    for name in weapons:
        if name in two_textures:
            gun = b.geometry(GUN_POS[:3] + SHIFTED, [(0.0, 0.0, 1.0)] * 3 + [(0.0, 1.0, 0.0)] * 3, UVS[:3] + SHIFTED_UVS, [(0, 1, 2), (3, 4, 5)], flags=0x37,
                             texture_names=[name, "other"], materials=[0, 1])
        else:
            gun = b.geometry(GUN_POS, [(0.0, 0.0, 1.0)] * 4, UVS, [(0, 1, 2), (2, 1, 3)], flags=0x37, texture_names=[name])
        flash = b.geometry(GUN_POS[:3], [(0.0, 0.0, 1.0)] * 3, UVS[:3], [(0, 1, 2)], flags=0x37, texture_names=["muzzle"])
        if name in assembled:
            barrels = b.geometry(GUN_POS[:3], [(0.0, 0.0, 1.0)] * 3, UVS[:3], [(0, 1, 2)], flags=0x37, texture_names=[name])
            files[f"{name}.dff"] = b.assembled_dff(gun, barrels, flash)
        else:
            files[f"{name}.dff"] = b.weapon_dff(gun, flash)
        textures = [b.texture_native(name, 4, 4, dxt1, flags=0x08, d3d=0x31545844, depth=16, raster=0x200),
                    b.texture_native("muzzle", 4, 4, bytes(16), flags=0x09, d3d=0x33545844, depth=16, raster=0x300)]
        if name in two_textures:
            textures.append(b.texture_native("other", 4, 4, dxt1, flags=0x08, d3d=0x31545844, depth=16, raster=0x200))
        files[f"{name}.txd"] = b.txd(*textures)
    (models / "gta3.img").write_bytes(b.img(files))
    return Path(root)


class WeaponModelTests(unittest.TestCase):
    def test_the_weapon_list_is_the_six_of_m2_the_minigun_the_rocket_launcher_and_the_bat(self):
        self.assertEqual(WEAPON_MODELS, ("colt45", "desert_eagle", "chromegun", "micro_uzi", "ak47", "m4", "minigun", "rocketla", "bat", "jetpack"))
        self.assertEqual(ASSEMBLED, ("minigun", "jetpack"))  # the jetpack's nozzles are child frames of its model
        self.assertEqual(ATTACH_NODES, {"jetpack": 3})       # on the upper torso (Spine1): everything else is in the right hand

    def test_a_weapon_with_two_textures_makes_one_mesh_per_texture(self):
        with tempfile.TemporaryDirectory() as d:
            model = assemble.load_weapon(make_install(d, weapons=("colt45",), two_textures=("colt45",)), "colt45")
        self.assertEqual(len(model.parts), 2)
        self.assertEqual(sorted(len(p.positions) for p in model.parts), [3, 3])  # each mesh keeps only the vertices it uses
        self.assertEqual(sorted(len(p.triangles) for p in model.parts), [1, 1])
        self.assertEqual(len(model.textures), 2)
        self.assertEqual({p.texture for p in model.parts}, set(model.textures))
        by_texture = {p.texture.split(":")[1]: p for p in model.parts}
        self.assertEqual(by_texture["colt45"].positions, GUN_POS[:3])   # each mesh has its own triangle's vertices, not the first ones
        self.assertEqual(by_texture["colt45"].uvs, UVS[:3])
        self.assertEqual(by_texture["other"].positions, SHIFTED)
        self.assertEqual(by_texture["other"].uvs, SHIFTED_UVS)
        self.assertEqual(by_texture["colt45"].normals, [(0.0, 0.0, 1.0)] * 3)
        self.assertEqual(by_texture["other"].normals, [(0.0, 1.0, 0.0)] * 3)
        self.assertEqual(by_texture["other"].triangles, [(0, 1, 2)])    # renumbered from zero
        for part in model.parts:
            self.assertTrue(all(0 <= v < len(part.positions) for tri in part.triangles for v in tri))
            self.assertEqual(part.joints, [(0, 0, 0, 0)] * len(part.positions))
        glb.read_glb(glb.to_glb(model))  # still a valid one-joint file

    def test_the_minigun_is_assembled_with_its_barrels_and_the_other_weapons_are_not(self):
        with tempfile.TemporaryDirectory() as d:
            install = make_install(d, weapons=("colt45", "minigun"), assembled=("minigun",))
            colt = assemble.load_weapon(install, "colt45")
            minigun = assemble.load_weapon(install, "minigun")
        self.assertEqual(sum(len(p.positions) for p in colt.parts), 4)  # the body only: the extra part is a child frame
        self.assertEqual(sum(len(p.positions) for p in minigun.parts), 4 + 3)  # body and barrels

    def test_the_jetpack_is_a_one_joint_model_on_the_torso_with_its_nozzles(self):
        with tempfile.TemporaryDirectory() as d:
            install = make_install(d, weapons=("colt45", "jetpack"), assembled=("jetpack",))
            jetpack = assemble.load_weapon(install, "jetpack")
            colt = assemble.load_weapon(install, "colt45")
        self.assertEqual(jetpack.skeleton.node_ids, (3,))
        self.assertEqual(colt.skeleton.node_ids, (24,))                        # the weapons are still in the right hand
        self.assertEqual(sum(len(p.positions) for p in jetpack.parts), 4 + 3)  # body and nozzles
        gltf, _binary = glb.read_glb(glb.to_glb(jetpack))
        self.assertEqual(gltf["nodes"][gltf["skins"][0]["joints"][0]]["extras"]["nodeId"], 3)

    def test_the_real_jetpack_converts(self):
        gta = Path(__file__).resolve().parent.parent.parent / "GTA San Andreas"
        if not (gta / "models" / "gta3.img").is_file():
            self.skipTest("no GTA San Andreas install here")
        model = assemble.load_weapon(gta, "jetpack")
        self.assertEqual(model.skeleton.node_ids, (3,))
        self.assertEqual(sum(len(p.positions) for p in model.parts), 974)      # measured in docs/jetpack-spike.md
        self.assertEqual(list(model.textures), ["jetpack:Jetpac8bit128"])

    def test_load_weapon_builds_a_one_joint_model_on_the_right_hand(self):
        with tempfile.TemporaryDirectory() as d:
            model = assemble.load_weapon(make_install(d), "colt45")
        self.assertEqual(model.skeleton.node_ids, (24,))
        self.assertEqual(len(model.parts), 1)
        part = model.parts[0]
        self.assertEqual(part.positions, GUN_POS)
        self.assertEqual(part.joints, [(0, 0, 0, 0)] * 4)
        self.assertEqual(part.weights, [(1.0, 0.0, 0.0, 0.0)] * 4)
        (key, texture), = model.textures.items()
        self.assertEqual((texture.width, texture.height), (4, 4))
        self.assertEqual(tuple(texture.rgba[0:4]), (255, 0, 0, 255))  # the DXT1 block's first pixel is red

    def test_the_glb_reads_back_with_one_joint_and_the_texture(self):
        with tempfile.TemporaryDirectory() as d:
            model = assemble.load_weapon(make_install(d), "colt45")
        gltf, binary = glb.read_glb(glb.to_glb(model))
        self.assertEqual(len(gltf["skins"][0]["joints"]), 1)
        node = gltf["nodes"][gltf["skins"][0]["joints"][0]]
        self.assertEqual(node["extras"]["nodeId"], 24)
        self.assertEqual(len(gltf["images"]), 1)

    def test_a_missing_weapon_file_is_reported_by_name(self):
        with tempfile.TemporaryDirectory() as d:
            install = make_install(d)
            with self.assertRaises(assemble.ConversionError) as ctx:
                assemble.load_weapon(install, "ak47")
        self.assertIn("ak47", str(ctx.exception))

    def test_a_missing_texture_is_reported_by_name(self):
        with tempfile.TemporaryDirectory() as d:
            install = make_install(d)
            gun = b.geometry(GUN_POS, [(0.0, 0.0, 1.0)] * 4, UVS, [(0, 1, 2)], flags=0x37, texture_names=["not_there"])
            flash = b.geometry(GUN_POS[:3], [(0.0, 0.0, 1.0)] * 3, UVS[:3], [(0, 1, 2)], flags=0x37, texture_names=["muzzle"])
            stub = b.geometry([(0, 0, 0)] * 3, [(0, 0, 1)] * 3, [(0, 0)] * 3, [(0, 1, 2)],
                              skin=b.skin(3, [0], [(0, 0, 0, 0)] * 3, [(1, 0, 0, 0)] * 3, [IDENT] * 3))
            files = {"player.dff": b.dff([0, 1, 5], stub),
                     "colt45.dff": b.weapon_dff(gun, flash),
                     "colt45.txd": b.txd(b.texture_native("colt45", 4, 4, struct.pack("<HHI", 0xF800, 0x001F, 0), flags=0x08,
                                                         d3d=0x31545844, depth=16, raster=0x200))}
            (install / "models" / "gta3.img").write_bytes(b.img(files))
            with self.assertRaises(assemble.ConversionError) as ctx:
                assemble.load_weapon(install, "colt45")
        self.assertIn("not_there", str(ctx.exception))


class WeaponsCliTests(unittest.TestCase):
    def test_weapons_only_writes_one_glb_per_weapon(self):
        with tempfile.TemporaryDirectory() as d, tempfile.TemporaryDirectory() as out:
            install = make_install(d, weapons=WEAPON_MODELS)
            with redirect_stdout(io.StringIO()):
                code = convert_assets.main(["--gta", str(install), "--out", out, "--weapons-only"])
            self.assertEqual(code, 0)
            written = sorted(p.name for p in (Path(out) / "weapons").glob("*.glb"))
            self.assertEqual(written, sorted(f"{n}.glb" for n in WEAPON_MODELS))
            self.assertFalse((Path(out) / "cj.glb").exists())

    def test_a_missing_weapon_is_an_error_not_a_traceback(self):
        with tempfile.TemporaryDirectory() as d, tempfile.TemporaryDirectory() as out:
            install = make_install(d, weapons=("colt45",))
            buf = io.StringIO()
            with redirect_stdout(buf):
                code = convert_assets.main(["--gta", str(install), "--out", out, "--weapons-only"])
            self.assertEqual(code, 1)
            self.assertIn("error:", buf.getvalue())
            self.assertFalse((Path(out) / "weapons").exists())  # nothing half-written


if __name__ == "__main__":
    unittest.main()
