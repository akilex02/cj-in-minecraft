import io
import os
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

IDENT = (1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)
BGRA = bytes([0, 0, 255, 255, 0, 255, 0, 255])


def make_full_install(root):
    """A synthetic install with the five default parts and the four default textures."""
    models = Path(root) / "models"
    models.mkdir(parents=True)
    ids = [0, 1, 5]
    stub = b.geometry([(0, 0, 0)] * 3, [(0, 0, 1)] * 3, [(0, 0)] * 3, [(0, 1, 2)],
                      skin=b.skin(3, [0], [(0, 0, 0, 0)] * 3, [(1, 0, 0, 0)] * 3, [IDENT] * 3))
    (models / "gta3.img").write_bytes(b.img({"player.dff": b.dff(ids, stub)}))
    files = {}
    for spec in assemble.OUTFIT:
        sk = b.skin(4, [0, 1], [(0, 0, 0, 0), (1, 2, 0, 0), (2, 0, 0, 0)],
                    [(1.0, 0.0, 0.0, 0.0), (0.5, 0.5, 0.0, 0.0), (1.0, 0.0, 0.0, 0.0)], [IDENT] * 4)
        files[spec.dff] = b.dff([0, 1, 5, 303], b.geometry(
            [(0, 0, 0), (1, 0, 0), (0, 1, 0)], [(0, 0, 1)] * 3, [(0, 0), (1, 0), (0, 1)], [(0, 1, 2)], skin=sk))
        files.setdefault(spec.txd, [])
        files[spec.txd].append(b.texture_native(spec.texture, 2, 1, BGRA))
        if spec.overlay:
            files.setdefault(spec.overlay[0], [])
            files[spec.overlay[0]].append(b.texture_native(spec.overlay[1], 2, 1, BGRA, alpha=True))
    files = {k: (b.txd(*v) if isinstance(v, list) else v) for k, v in files.items()}
    (models / "player.img").write_bytes(b.img(files))
    return Path(root)


class CliTests(unittest.TestCase):
    def run_cli(self, *args):
        out = io.StringIO()
        with redirect_stdout(out):
            code = convert_assets.main(list(args))
        return code, out.getvalue()

    def test_writes_cj_glb_and_reports(self):
        with tempfile.TemporaryDirectory() as tmp:
            install = make_full_install(Path(tmp) / "gta")
            out_dir = Path(tmp) / "deep" / "out"  # Review Focus #6: the folder does not exist yet
            code, text = self.run_cli("--gta", str(install), "--out", str(out_dir))
            self.assertEqual(code, 0, text)
            data = (out_dir / "cj.glb").read_bytes()
            gltf, _ = glb.read_glb(data)
            self.assertEqual(len(gltf["meshes"][0]["primitives"]), 5)
            self.assertEqual(len(gltf["skins"][0]["joints"]), 3)
            self.assertIn("torso", text)
            self.assertIn("cj.glb", text)
            self.assertEqual(os.listdir(out_dir), ["cj.glb"])  # no temporary files left behind

    def test_running_twice_replaces_the_file_with_identical_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            install = make_full_install(Path(tmp) / "gta")
            out_dir = Path(tmp) / "out"
            self.run_cli("--gta", str(install), "--out", str(out_dir))
            first = (out_dir / "cj.glb").read_bytes()
            code, _ = self.run_cli("--gta", str(install), "--out", str(out_dir))
            self.assertEqual(code, 0)
            self.assertEqual((out_dir / "cj.glb").read_bytes(), first)
            self.assertEqual(os.listdir(out_dir), ["cj.glb"])

    def test_the_defaults_do_not_depend_on_the_working_directory(self):
        # Review: running from tools/ must not drop the game-derived preview outside the git-ignored folder
        root = Path(convert_assets.__file__).resolve().parent.parent
        self.assertTrue(convert_assets.DEFAULT_OUT.is_absolute())
        self.assertTrue(convert_assets.DEFAULT_GTA.is_absolute())
        self.assertEqual(convert_assets.DEFAULT_OUT, root / "cj-fabric" / "run" / "config" / "cj_fabric")
        self.assertEqual(convert_assets.DEFAULT_GTA, root / "GTA San Andreas")

    def test_game_derived_outputs_are_git_ignored_wherever_they_land(self):
        import shutil
        import subprocess
        root = Path(convert_assets.__file__).resolve().parent.parent
        if shutil.which("git") is None or not (root / ".git").exists():
            self.skipTest("not inside a git checkout")
        for name in ("cj_preview.png", "cj.glb", ".cj-abc123.tmp"):
            with self.subTest(name):
                result = subprocess.run(["git", "check-ignore", "-q", str(root / "tools" / "somewhere" / name)], cwd=root)
                self.assertEqual(result.returncode, 0, f"{name} would not be ignored by git")

    def test_a_failure_leaves_no_output_and_does_not_touch_an_existing_file(self):
        # Review Focus #6
        with tempfile.TemporaryDirectory() as tmp:
            out_dir = Path(tmp) / "out"
            out_dir.mkdir()
            (out_dir / "cj.glb").write_bytes(b"previous")
            code, text = self.run_cli("--gta", str(Path(tmp) / "nowhere"), "--out", str(out_dir))
            self.assertEqual(code, 1)
            self.assertIn("error:", text)
            self.assertIn("gta3.img", text)
            self.assertEqual((out_dir / "cj.glb").read_bytes(), b"previous")
            self.assertEqual(os.listdir(out_dir), ["cj.glb"])


if __name__ == "__main__":
    unittest.main()
