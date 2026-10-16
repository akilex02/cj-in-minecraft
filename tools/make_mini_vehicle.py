#!/usr/bin/env python3
"""Writes cj-fabric/src/test/resources/mini_vehicle.glb: a tiny synthetic car (the one tools/tests/test_vehicles.py builds) for the Java tests of VehicleModel."""
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent / "tests"))

from cjconvert import glb, vehicles  # noqa: E402
import test_vehicles  # noqa: E402

out = Path(__file__).resolve().parent.parent / "cj-fabric" / "src" / "test" / "resources" / "mini_vehicle.glb"
with tempfile.TemporaryDirectory() as root:
    model = vehicles.load_vehicle(test_vehicles.make_install(root), "greenwoo")
out.write_bytes(glb.to_glb(model))
print(f"wrote {out}")
