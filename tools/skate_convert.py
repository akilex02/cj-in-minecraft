#!/usr/bin/env python3
"""Converts the user's Skate 3 (default.xex and its data folder) with the Skate engine's own asset pipeline, as its setup window would, but without the
audio step: on Linux that step downloads a Windows program and fails, and the mod runs the engine muted. Run with the Python that has numpy and Pillow.
Usage: skate_convert.py --motor <the pack's motor folder> --xex <default.xex> --game-exe <the engine's executable>"""
import argparse
import importlib
import sys
from pathlib import Path


def without_audio(asset_exports):
    """The audio export does nothing (the engine is run with --mute)."""
    asset_exports.audio = lambda *args, **kwargs: print("(audio skipped: not needed, the engine runs muted)")


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--motor", required=True)
    ap.add_argument("--xex", required=True)
    ap.add_argument("--game-exe", required=True)
    args = ap.parse_args(argv)
    motor = Path(args.motor).resolve()
    sys.path.insert(0, str(motor))
    try:
        without_audio(importlib.import_module("tools.asset_pipeline.asset_exports"))
        install = importlib.import_module("tools.asset_pipeline.customiser_setup").install
        install(Path(args.xex), motor / "data", Path(args.game_exe), print)
    finally:
        sys.path.remove(str(motor))
        for name in [n for n in sys.modules if n == "tools" or n.startswith("tools.")]:
            del sys.modules[name]  # the next run (or a test) imports its own engine tools
    return 0


if __name__ == "__main__":
    sys.exit(main())
