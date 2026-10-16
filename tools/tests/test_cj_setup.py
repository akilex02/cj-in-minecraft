import contextlib
import io
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import cj_setup  # noqa: E402


def fake_gta(root, size=cj_setup.GTA_10_US_SIZE, loader="vorbisHooked.dll"):
    gta = Path(root) / "Juegos" / "GTA San Andreas (1.0 US)"
    gta.mkdir(parents=True)
    with open(gta / "gta_sa.exe", "wb") as f:
        f.truncate(size)
    if loader:
        (gta / loader).write_bytes(b"x")
    return gta


class ChecksTest(unittest.TestCase):
    def test_a_10_us_exe_with_a_loader_passes(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(cj_setup.check_gta(fake_gta(d)), [])

    def test_a_folder_without_the_exe_is_refused(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(cj_setup.SetupError) as e:
                cj_setup.check_gta(Path(d))
            self.assertIn("gta_sa.exe", str(e.exception))

    def test_another_version_is_refused_unless_forced(self):
        with tempfile.TemporaryDirectory() as d:
            gta = fake_gta(d, size=14405632)
            with self.assertRaises(cj_setup.SetupError) as e:
                cj_setup.check_gta(gta)
            self.assertIn("1.0 US", str(e.exception))
            self.assertEqual(len(cj_setup.check_gta(gta, force=True)), 1)  # a warning, not an error

    def test_no_asi_loader_is_refused(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(cj_setup.SetupError) as e:
                cj_setup.check_gta(fake_gta(d, loader=None))
            self.assertIn("ASI", str(e.exception))

    def test_each_known_loader_is_recognised(self):
        for name in cj_setup.ASI_LOADERS:
            with tempfile.TemporaryDirectory() as d:
                self.assertEqual(cj_setup.asi_loader(fake_gta(d, loader=name)), name)

    def test_the_default_minecraft_folder_of_each_system(self):
        self.assertEqual(cj_setup.default_minecraft_dir("Windows", {"APPDATA": r"C:\Users\Ana\AppData\Roaming"}, Path("/x")),
                         Path(r"C:\Users\Ana\AppData\Roaming") / ".minecraft")
        self.assertEqual(cj_setup.default_minecraft_dir("Linux", {}, Path("/home/ana")), Path("/home/ana/.minecraft"))

    def test_the_jar_replaces_an_older_one_and_fabric_api_is_looked_for(self):
        with tempfile.TemporaryDirectory() as d:
            mods = Path(d) / "instances" / "Mi instancia" / "minecraft" / "mods"
            mods.mkdir(parents=True)
            (mods / "cj-fabric-0.0.9.jar").write_bytes(b"old")
            jar = Path(d) / "cj-fabric-0.1.0.jar"
            jar.write_bytes(b"new")
            self.assertFalse(cj_setup.install_jar(jar, mods))
            self.assertEqual(sorted(p.name for p in mods.iterdir()), ["cj-fabric-0.1.0.jar"])
            (mods / "fabric-api-0.116.17+1.21.1.jar").write_bytes(b"api")
            self.assertTrue(cj_setup.install_jar(jar, mods))




class CommandsTest(unittest.TestCase):
    def test_install_checks_copies_the_bridge_converts_and_remembers_the_folders(self):
        with tempfile.TemporaryDirectory() as d:
            gta = fake_gta(d)
            minecraft = Path(d) / "mc"
            minecraft.mkdir()
            bridge = Path(d) / "cj_bridge.asi"
            bridge.write_bytes(b"asi")
            calls = []
            config = Path(d) / "cj_setup.json"
            code = cj_setup.main(["install", "--gta", str(gta), "--minecraft", str(minecraft)], convert=lambda argv: calls.append(argv) or 0,
                                 bridge=bridge, config=config)
            self.assertEqual(code, 0)
            self.assertEqual((gta / "cj_bridge.asi").read_bytes(), b"asi")
            self.assertEqual(calls, [["--gta", str(gta), "--out", str(minecraft / "config" / "cj_fabric"), "--weapons", "--vehicles"]])
            self.assertEqual(json.loads(config.read_text()), {"gta": str(gta), "minecraft": str(minecraft)})

    def test_install_stops_on_a_bad_gta_folder_with_the_reason(self):
        with tempfile.TemporaryDirectory() as d:
            minecraft = Path(d) / "mc"
            minecraft.mkdir()
            code = cj_setup.main(["install", "--gta", d, "--minecraft", str(minecraft)], convert=lambda argv: 0,
                                 bridge=Path(d) / "x.asi", config=Path(d) / "c.json")
            self.assertEqual(code, 1)

    def test_dress_needs_the_outfit_the_bridge_writes(self):
        with tempfile.TemporaryDirectory() as d:
            gta = fake_gta(d)
            minecraft = Path(d) / "mc"
            minecraft.mkdir()
            calls = []
            args = ["dress", "--gta", str(gta), "--minecraft", str(minecraft)]
            self.assertEqual(cj_setup.main(args, convert=lambda argv: calls.append(argv) or 0, config=Path(d) / "c.json"), 1)
            (gta / "cj_outfit.json").write_text("{}")
            self.assertEqual(cj_setup.main(args, convert=lambda argv: calls.append(argv) or 0, config=Path(d) / "c.json"), 0)
            self.assertEqual(calls, [["--gta", str(gta), "--out", str(minecraft / "config" / "cj_fabric"), "--outfit", str(gta / "cj_outfit.json")]])

    def test_the_icon_pack_takes_the_pngs_there_are_and_skips_the_rest(self):
        with tempfile.TemporaryDirectory() as d:
            src = Path(d) / "iconos"
            src.mkdir()
            (src / "AK47.png").write_bytes(b"\x89PNG\r\n\x1a\n ak")
            (src / "Pistol.png").write_bytes(b"not a png")
            minecraft = Path(d) / "mc"
            minecraft.mkdir()
            copied = cj_setup.make_icon_pack(src, minecraft)
            pack = minecraft / "resourcepacks" / "cj-icons"
            self.assertEqual(copied, ["ak47"])
            self.assertEqual((pack / "assets" / "cj_fabric" / "textures" / "item" / "gta" / "ak47.png").read_bytes(), b"\x89PNG\r\n\x1a\n ak")
            self.assertFalse((pack / "assets" / "cj_fabric" / "textures" / "item" / "gta" / "colt45.png").exists())
            self.assertEqual(json.loads((pack / "pack.mcmeta").read_text())["pack"]["pack_format"], cj_setup.PACK_FORMAT)

    def test_play_without_install_says_so(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(cj_setup.main(["play"], config=Path(d) / "missing.json"), 1)

    def test_play_opens_gta_natively_on_windows_and_with_wine_on_linux(self):
        gta = Path("/juegos/GTA San Andreas")
        self.assertEqual(cj_setup.play_command("Windows", gta), [str(gta / "gta_sa.exe")])
        self.assertEqual(cj_setup.play_command("Linux", gta), ["wine", "gta_sa.exe"])

    def test_icons_before_install_uses_the_default_minecraft_folder_and_needs_no_gta(self):
        with tempfile.TemporaryDirectory() as d:
            home = Path(d) / "home"
            (home / ".minecraft").mkdir(parents=True)
            src = Path(d) / "iconos"
            src.mkdir()
            (src / "M4.png").write_bytes(b"\x89PNG\r\n\x1a\n m4")
            old = cj_setup.Path.home
            cj_setup.Path.home = staticmethod(lambda: home)
            try:
                code = cj_setup.main(["icons", str(src)], system="Linux", config=Path(d) / "missing.json")
            finally:
                cj_setup.Path.home = old
            self.assertEqual(code, 0)
            self.assertTrue((home / ".minecraft" / "resourcepacks" / "cj-icons" / "assets" / "cj_fabric" / "textures" / "item" / "gta" / "m4.png").is_file())


class PlatformTest(unittest.TestCase):
    def test_install_writes_the_floor_of_the_car_where_cleo_reads_it(self):
        with tempfile.TemporaryDirectory() as d:
            gta = fake_gta(d)
            (gta / "CLEO.asi").write_bytes(b"x")
            path = cj_setup.install_platform(gta)
            self.assertEqual(path, gta / "CLEO" / "cj_platform.cs")
            self.assertTrue(path.read_bytes().startswith(b"\x01\x00"))  # the script starts with a wait

    def test_with_modloader_the_floor_goes_in_its_own_folder(self):
        with tempfile.TemporaryDirectory() as d:
            gta = fake_gta(d)
            (gta / "CLEO.asi").write_bytes(b"x")
            (gta / "modloader.asi").write_bytes(b"x")
            self.assertEqual(cj_setup.install_platform(gta), gta / "modloader" / "CjPlatform" / "cleo" / "cj_platform.cs")

    def test_without_cleo_there_is_no_floor_and_install_says_the_car_will_not_come(self):
        with tempfile.TemporaryDirectory() as d:
            gta = fake_gta(d)
            self.assertIsNone(cj_setup.install_platform(gta))
            minecraft = Path(d) / "mc"
            minecraft.mkdir()
            bridge = Path(d) / "cj_bridge.asi"
            bridge.write_bytes(b"asi")
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                code = cj_setup.main(["install", "--gta", str(gta), "--minecraft", str(minecraft)], convert=lambda argv: 0,
                                     bridge=bridge, config=Path(d) / "c.json")
            self.assertEqual(code, 0)
            self.assertIn("CLEO", out.getvalue())


class SkateTest(unittest.TestCase):
    def installation(self, motor, name, mtime):
        glb = motor / "data" / "installations" / name / "assets" / "private" / "skater.glb"
        glb.parent.mkdir(parents=True)
        glb.write_bytes(b"glb")
        os.utime(glb, (mtime, mtime))
        return glb

    def test_the_newest_converted_skater_is_found(self):
        with tempfile.TemporaryDirectory() as d:
            motor = Path(d) / "cj skate" / "motor"
            self.installation(motor, "old", 1000)
            new = self.installation(motor, "new", 2000)
            self.assertEqual(cj_setup.find_skater_glb(motor), new)
            self.assertEqual(cj_setup.assets_of(new), new.parent.parent)

    def test_without_a_conversion_the_user_is_told_to_run_the_engine_once(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(cj_setup.SetupError) as e:
                cj_setup.find_skater_glb(Path(d))
            self.assertIn("skate3rust", str(e.exception))

    def test_the_executable_of_each_system(self):
        motor = Path("/p/motor")
        self.assertEqual(cj_setup.engine_executable(motor, "Windows"), motor / "skate3rust.exe")
        self.assertEqual(cj_setup.engine_executable(motor, "Linux"), motor / "skate3rust")

    def test_the_linux_conversion_command(self):
        pack = Path("/juegos/cj skate")
        cmd = cj_setup.convert_command("/usr/bin/python3", pack, Path("/juegos/Skate 3"), "Linux")
        self.assertEqual(cmd, ["/usr/bin/python3", str(cj_setup.HERE / "skate_convert.py"), "--motor", str(pack / "motor"),
                               "--xex", str(Path("/juegos/Skate 3") / "default.xex"), "--game-exe", str(pack / "motor" / "skate3rust")])

    def test_the_engine_is_opened_as_a_host_without_sound(self):
        exe, assets = Path("/p/motor/skate3rust"), Path("/p/motor/data/installations/a/assets")
        self.assertEqual(cj_setup.engine_command(exe, assets),
                         [str(exe), "--assets", str(assets), "--test-world", "--host", "127.0.0.1:34600", "--mute"])

    def test_missing_python_modules_are_listed(self):
        self.assertEqual(cj_setup.check_python_modules(["json", "no_such_module_cj"]), ["no_such_module_cj"])

    def test_play_skate_without_skate_says_so(self):
        with tempfile.TemporaryDirectory() as d:
            config = Path(d) / "c.json"
            config.write_text(json.dumps({"gta": "x", "minecraft": "y"}))
            self.assertEqual(cj_setup.main(["play-skate"], config=config), 1)

    def test_skate_on_windows_writes_the_rig_and_the_board_and_remembers_the_engine(self):
        with tempfile.TemporaryDirectory() as d:
            pack = Path(d) / "cj skate"
            glb = self.installation(pack / "motor", "a", 1000)
            minecraft = Path(d) / "mc"
            minecraft.mkdir()
            config = Path(d) / "c.json"
            config.write_text(json.dumps({"gta": "g", "minecraft": str(minecraft)}))
            written = []
            code = cj_setup.main(["skate", "--engine", str(pack)], system="Windows", config=config,
                                 write_skate=lambda src, out: written.append((src, out)))
            self.assertEqual(code, 0)
            self.assertEqual(written, [(glb, minecraft / "config" / "cj_fabric")])
            saved = json.loads(config.read_text())
            self.assertEqual(saved["engine"], str(pack / "motor" / "skate3rust.exe"))
            self.assertEqual(saved["engine_assets"], str(glb.parent.parent))
            self.assertEqual(saved["gta"], "g")  # what install saved stays

    def test_skate_on_linux_converts_first_and_needs_the_skate3_folder(self):
        with tempfile.TemporaryDirectory() as d:
            pack = Path(d) / "cj skate"
            (pack / "motor").mkdir(parents=True)
            minecraft = Path(d) / "mc"
            minecraft.mkdir()
            config = Path(d) / "c.json"
            config.write_text(json.dumps({"gta": "g", "minecraft": str(minecraft)}))
            self.assertEqual(cj_setup.main(["skate", "--engine", str(pack)], system="Linux", config=config), 1)  # no --skate3
            ran = []

            def run(cmd):
                ran.append(cmd)
                self.installation(pack / "motor", "a", 1000)
                return 0
            code = cj_setup.main(["skate", "--engine", str(pack), "--skate3", str(Path(d) / "Skate 3")], system="Linux", config=config,
                                 run=run, write_skate=lambda src, out: None, modules=lambda names: [])
            self.assertEqual(code, 0)
            self.assertEqual(ran[0][1:3], [str(cj_setup.HERE / "skate_convert.py"), "--motor"])


class RelativePathsTest(unittest.TestCase):
    """The folders are saved for later commands run from elsewhere (skate.sh, jugar.sh): they must be saved absolute."""

    def test_skate_saves_the_engine_absolute_when_given_relative(self):
        with tempfile.TemporaryDirectory() as d:
            pack = Path(d) / "cj-skate-0.2.0-linux"
            glb = pack / "motor" / "data" / "installations" / "a" / "assets" / "private" / "skater.glb"
            glb.parent.mkdir(parents=True)
            glb.write_bytes(b"glb")
            minecraft = Path(d) / "mc"
            minecraft.mkdir()
            config = Path(d) / "c.json"
            config.write_text(json.dumps({"gta": "g", "minecraft": str(minecraft)}))
            old = os.getcwd()
            os.chdir(d)
            try:
                code = cj_setup.main(["skate", "--engine", "cj-skate-0.2.0-linux"], system="Windows", config=config, write_skate=lambda src, out: None)
            finally:
                os.chdir(old)
            self.assertEqual(code, 0)
            saved = json.loads(config.read_text())
            self.assertTrue(Path(saved["engine"]).is_absolute(), saved["engine"])
            self.assertTrue(Path(saved["engine_assets"]).is_absolute(), saved["engine_assets"])

    def test_install_saves_gta_and_minecraft_absolute_when_given_relative(self):
        with tempfile.TemporaryDirectory() as d:
            gta = fake_gta(d)
            (Path(d) / "mc").mkdir()
            bridge = Path(d) / "cj_bridge.asi"
            bridge.write_bytes(b"asi")
            config = Path(d) / "c.json"
            old = os.getcwd()
            os.chdir(d)
            try:
                code = cj_setup.main(["install", "--gta", str(gta.relative_to(d)), "--minecraft", "mc"], convert=lambda argv: 0,
                                     bridge=bridge, config=config)
            finally:
                os.chdir(old)
            self.assertEqual(code, 0)
            saved = json.loads(config.read_text())
            self.assertTrue(Path(saved["gta"]).is_absolute() and Path(saved["minecraft"]).is_absolute(), saved)

    def test_skate_on_linux_without_skate3_uses_the_conversion_already_there(self):
        with tempfile.TemporaryDirectory() as d:
            pack = Path(d) / "cj skate"
            glb = pack / "motor" / "data" / "installations" / "a" / "assets" / "private" / "skater.glb"
            glb.parent.mkdir(parents=True)
            glb.write_bytes(b"glb")
            minecraft = Path(d) / "mc"
            minecraft.mkdir()
            config = Path(d) / "c.json"
            config.write_text(json.dumps({"gta": "g", "minecraft": str(minecraft)}))
            ran = []
            code = cj_setup.main(["skate", "--engine", str(pack)], system="Linux", config=config, run=lambda cmd: ran.append(cmd) or 0,
                                 write_skate=lambda src, out: None, modules=lambda names: [])
            self.assertEqual(code, 0)
            self.assertEqual(ran, [])  # nothing converted again

if __name__ == "__main__":
    unittest.main()
