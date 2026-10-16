import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import install_item_icons as icons  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent.parent
RESOURCES = ROOT / "cj-fabric" / "src" / "main" / "resources"
PNG = b"\x89PNG\r\n\x1a\n" + b"fake-image-data"


def make_dirs(root, present=()):
    src = Path(root) / "src"
    generic = Path(root) / "generic"
    out = Path(root) / "out"
    src.mkdir()
    generic.mkdir()
    (generic / "weapon_icon.png").write_bytes(PNG + b"-generic-weapon")
    (generic / "jetpack_icon.png").write_bytes(PNG + b"-generic-jetpack")
    (generic / "skateboard.png").write_bytes(PNG + b"-generic-skateboard")
    for n in (1, 2, 3):
        (generic / f"ammo{n}_icon.png").write_bytes(PNG + f"-ammo{n}".encode())
    for name in present:
        (src / name).write_bytes(PNG + name.encode())
    return src, generic, out


class ItemIconTests(unittest.TestCase):
    def test_every_item_of_the_mod_has_an_icon_name(self):
        weapons = json.loads((RESOURCES / "weapons.json").read_text())["weapons"]
        items = {w["id"] for w in weapons if w["item"]}
        self.assertEqual(set(icons.ICONS), items | {"jetpack", "ammo1", "ammo2", "ammo3", "skateboard"})
        self.assertEqual(icons.ICONS["skateboard"], "Skateboard.png")
        self.assertEqual(icons.ICONS["colt45"], "Pistol.png")
        self.assertEqual(icons.ICONS["chromegun"], "Shotgun.png")
        self.assertEqual(icons.ICONS["rocket_launcher"], "Rocket.png")
        self.assertEqual(icons.ICONS["baseball_bat"], "Bat.png")
        self.assertNotIn("fists", icons.ICONS)  # the fists are no item any more

    def test_the_icons_that_exist_are_copied_under_the_id_of_the_item(self):
        with tempfile.TemporaryDirectory() as d:
            src, generic, out = make_dirs(d, present=("AK47.png", "Jetpack.png"))
            report = icons.install(src, out, generic)
            self.assertEqual((out / "ak47.png").read_bytes(), PNG + b"AK47.png")
            self.assertEqual((out / "jetpack.png").read_bytes(), PNG + b"Jetpack.png")
        self.assertEqual(sorted(report.copied), ["ak47", "jetpack"])

    def test_the_ones_that_are_missing_fall_back_to_the_generic_icons(self):
        with tempfile.TemporaryDirectory() as d:
            src, generic, out = make_dirs(d, present=("AK47.png",))
            report = icons.install(src, out, generic)
            self.assertEqual((out / "m4.png").read_bytes(), PNG + b"-generic-weapon")
            self.assertEqual((out / "jetpack.png").read_bytes(), PNG + b"-generic-jetpack")
            self.assertEqual((out / "skateboard.png").read_bytes(), PNG + b"-generic-skateboard")  # the drawn board of the mod
            self.assertEqual((out / "ammo2.png").read_bytes(), PNG + b"-ammo2")  # the user's own ammo icons ship with the mod
            self.assertEqual(len(list(out.glob("*.png"))), len(icons.ICONS))   # one file for every item, always
        self.assertIn("m4", report.fallback)
        self.assertIn("jetpack", report.fallback)
        self.assertNotIn("ak47", report.fallback)

    def test_a_file_that_is_not_a_png_is_not_used(self):
        with tempfile.TemporaryDirectory() as d:
            src, generic, out = make_dirs(d)
            (src / "M4.png").write_bytes(b"not an image")
            report = icons.install(src, out, generic)
            self.assertEqual((out / "m4.png").read_bytes(), PNG + b"-generic-weapon")
        self.assertIn("m4", report.fallback)

    def test_without_the_icon_folder_everything_still_gets_a_texture(self):
        with tempfile.TemporaryDirectory() as d:
            src, generic, out = make_dirs(d)
            report = icons.install(Path(d) / "missing", out, generic)
            self.assertEqual(len(list(out.glob("*.png"))), len(icons.ICONS))
        self.assertEqual(sorted(report.fallback), sorted(icons.ICONS))

    def test_the_source_folder_is_never_touched(self):
        with tempfile.TemporaryDirectory() as d:
            src, generic, out = make_dirs(d, present=("M4.png",))
            before = {p.name: (p.read_bytes(), p.stat().st_mtime_ns) for p in src.iterdir()}
            icons.install(src, out, generic)
            after = {p.name: (p.read_bytes(), p.stat().st_mtime_ns) for p in src.iterdir()}
        self.assertEqual(before, after)

    def test_the_models_of_the_items_use_those_textures(self):
        for item_id in icons.ICONS:
            model = json.loads((RESOURCES / "assets" / "cj_fabric" / "models" / "item" / f"{item_id}.json").read_text())
            self.assertEqual(model["textures"]["layer0"], f"cj_fabric:item/gta/{item_id}", item_id)


if __name__ == "__main__":
    unittest.main()
