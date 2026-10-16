#!/usr/bin/env python3
"""Writes the tiny synthetic one-joint weapon .glb that the Java tests read, shaped like the ones convert_assets.py --weapons writes
(a single joint on the right hand, node 24, identity inverse bind). It contains nothing from the game."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from cjconvert import glb  # noqa: E402
from cjconvert.assemble import MeshPart, Model  # noqa: E402
from cjconvert.dff import Skeleton  # noqa: E402
from cjconvert.txd import Texture  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "cj-fabric" / "src" / "test" / "resources" / "mini_weapon.glb"
RW_IDENT = (1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)


def mini_weapon():
    positions = [(0.0, 0.0, 0.0), (0.3, 0.0, 0.0), (0.0, 0.05, 0.1), (0.3, 0.05, 0.1)]
    mesh = MeshPart("gun", positions, [(0.0, 0.0, 1.0)] * 4, [(0.0, 0.0), (1.0, 0.0), (0.0, 1.0), (1.0, 1.0)],
                    [(0, 1, 2), (2, 1, 3)], [(0, 0, 0, 0)] * 4, [(1.0, 0.0, 0.0, 0.0)] * 4, "gun:tex")
    texture = Texture("tex", 2, 1, bytes([255, 0, 0, 255, 0, 255, 0, 255]))
    return Model(Skeleton((24,), (RW_IDENT,)), [mesh], {"gun:tex": texture})


if __name__ == "__main__":
    OUT.write_bytes(glb.to_glb(mini_weapon()))
    print(f"wrote {OUT}")
