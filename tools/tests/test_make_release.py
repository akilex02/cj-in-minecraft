import io
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import make_release  # noqa: E402

GENERIC = b"\x89PNG generic"


def jar_with(entries):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        for name, data in entries.items():
            z.writestr(name, data)
    return buf.getvalue()


def release_zip(d, entries):
    path = Path(d) / "r.zip"
    with zipfile.ZipFile(path, "w") as z:
        for name, data in entries.items():
            z.writestr(name, data)
    return path


class AuditTest(unittest.TestCase):
    def test_a_clean_package_passes(self):
        with tempfile.TemporaryDirectory() as d:
            jar = jar_with({"assets/cj_fabric/textures/item/gta/ak47.png": GENERIC, "dev/cjmc/CjClient.class": b"x"})
            z = release_zip(d, {"cj-mod-0.1.0/cj-fabric-0.1.0.jar": jar, "cj-mod-0.1.0/cj_bridge.asi": b"x", "cj-mod-0.1.0/setup/cj_setup.py": b"x"})
            self.assertEqual(make_release.problems(z, [GENERIC]), [])

    def test_game_files_are_refused(self):
        with tempfile.TemporaryDirectory() as d:
            for name in ("cj-mod/models/gta3.img", "cj-mod/x.dff", "cj-mod/x.txd", "cj-mod/default.xex", "cj-mod/config/cj.glb"):
                self.assertTrue(make_release.problems(release_zip(d, {name: b"x"}), [GENERIC]), name)

    def test_a_community_icon_inside_the_jar_is_refused(self):
        with tempfile.TemporaryDirectory() as d:
            jar = jar_with({"assets/cj_fabric/textures/item/gta/ak47.png": b"\x89PNG the community's"})
            z = release_zip(d, {"cj-mod/cj-fabric-0.1.0.jar": jar})
            self.assertEqual(len(make_release.problems(z, [GENERIC])), 1)

    def test_the_users_folders_are_refused(self):
        with tempfile.TemporaryDirectory() as d:
            for name in ("cj-mod/weapon-icons/AK47.png", "cj-mod/GTA San Andreas/gta_sa.exe"):
                self.assertTrue(make_release.problems(release_zip(d, {name: b"x"}), [GENERIC]), name)


    def test_the_icons_that_ship_are_the_generic_ones_and_the_ammo(self):
        icons = make_release.generic_icons()
        self.assertEqual(len(icons), 6)  # weapon, jetpack, skateboard, and the three ammo boxes the user drew

    def test_the_package_takes_the_script_of_the_floor(self):
        self.assertIn("make_platform_script.py", make_release.SETUP_FILES)

if __name__ == "__main__":
    unittest.main()
