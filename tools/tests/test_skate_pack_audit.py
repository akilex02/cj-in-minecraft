import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import skate_pack_audit  # noqa: E402


def pack(d, names):
    path = Path(d) / "p.zip"
    with zipfile.ZipFile(path, "w") as z:
        for name in names:
            z.writestr(name, b"x")
    return path


class SkatePackAuditTest(unittest.TestCase):
    def test_a_clean_pack_passes(self):
        with tempfile.TemporaryDirectory() as d:
            names = ["cj-skate-0.2.0-linux/motor/skate3rust", "cj-skate-0.2.0-linux/motor/tools/setup.py", "cj-skate-0.2.0-linux/motor/licenses/LICENSE.txt",
                     "cj-skate-0.2.0-linux/skate.sh", "cj-skate-0.2.0-linux/FUENTE.md", "cj-skate-0.2.0-linux/skate-engine-host.patch"]
            self.assertEqual(skate_pack_audit.problems(pack(d, names)), [])

    def test_skate3_files_and_conversions_are_refused(self):
        with tempfile.TemporaryDirectory() as d:
            for name in ("p/default.xex", "p/Skate 3.iso", "p/motor/data/installations/abc/assets/private/skater.glb",
                         "p/motor/data/installation.json", "p/skate-spike-assets/x", "p/board.glb"):
                self.assertTrue(skate_pack_audit.problems(pack(d, [name])), name)


    def test_the_engines_own_example_mods_may_have_models(self):
        # the engine's repository ships Skyline_Drive_Mod (its own content); its packaging bundles it
        with tempfile.TemporaryDirectory() as d:
            names = ["p/motor/mods/Skyline_Drive_Mod/skyline.glb", "p/motor/mods/Skyline_Drive_Mod/fallback_car.glb"]
            self.assertEqual(skate_pack_audit.problems(pack(d, names)), [])

    def test_a_model_anywhere_else_is_still_refused(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertTrue(skate_pack_audit.problems(pack(d, ["p/motor/skater.glb"])))
            self.assertTrue(skate_pack_audit.problems(pack(d, ["p/mods/x.glb"])))  # not the engine's mods folder

if __name__ == "__main__":
    unittest.main()
