import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import skate_convert  # noqa: E402


class SkateConvertTest(unittest.TestCase):
    def test_the_engines_conversion_runs_without_its_audio_step(self):
        # a stand-in of the engine's tools/asset_pipeline: install() calls asset_exports.audio, which fails on Linux
        with tempfile.TemporaryDirectory() as d:
            motor = Path(d) / "mi pack" / "motor"
            pipeline = motor / "tools" / "asset_pipeline"
            pipeline.mkdir(parents=True)
            (motor / "tools" / "__init__.py").write_text("")
            (pipeline / "__init__.py").write_text("")
            (pipeline / "asset_exports.py").write_text("def audio(*a, **k):\n    raise RuntimeError('downloads a Windows exe')\n")
            (pipeline / "customiser_setup.py").write_text(textwrap.dedent("""
                from pathlib import Path
                def install(iso, base, game_exe, report, refresh=False):
                    from tools.asset_pipeline import asset_exports
                    asset_exports.audio()
                    Path(base).mkdir(parents=True, exist_ok=True)
                    (Path(base) / "done").write_text(str(iso) + "|" + str(game_exe))
            """))
            xex = Path(d) / "Skate 3" / "default.xex"
            xex.parent.mkdir()
            xex.write_bytes(b"x")
            code = skate_convert.main(["--motor", str(motor), "--xex", str(xex), "--game-exe", str(motor / "skate3rust")])
            self.assertEqual(code, 0)
            self.assertEqual((motor / "data" / "done").read_text(), f"{xex}|{motor / 'skate3rust'}")
