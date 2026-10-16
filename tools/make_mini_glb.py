#!/usr/bin/env python3
"""Writes the tiny synthetic cj.glb that the Java tests read. It contains nothing from the game."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from cjconvert import glb  # noqa: E402
from cjconvert.assemble import MeshPart, Model  # noqa: E402
from cjconvert.dff import Skeleton  # noqa: E402
from cjconvert.txd import Texture  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "cj-fabric" / "src" / "test" / "resources" / "mini.glb"

# a rigid RwMatrix: right=(0,1,0) up=(-1,0,0) at=(0,0,1), position (1,2,3); a pad float follows each row
RW = (0.0, 1.0, 0.0, 0.0, -1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 2.0, 3.0, 1.0)
RW_IDENT = (1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)


def mini_model():
    skeleton = Skeleton((0, 5, 1), (RW, RW_IDENT, RW_IDENT))
    positions = [(0.0, 0.0, 0.0), (1.0, 0.0, 0.5), (0.0, 2.0, -1.0), (1.0, 2.0, 0.0)]
    args = (positions, [(0.0, 0.0, 1.0)] * 4, [(0.0, 0.0), (1.0, 0.0), (0.0, 1.0), (1.0, 1.0)],
            [(0, 1, 2), (2, 1, 3)],
            [(0, 0, 0, 0), (1, 2, 0, 0), (2, 0, 0, 0), (1, 0, 0, 0)],
            [(1.0, 0.0, 0.0, 0.0), (0.25, 0.75, 0.0, 0.0), (1.0, 0.0, 0.0, 0.0), (1.0, 0.0, 0.0, 0.0)])
    parts = [MeshPart("torso", *args, "tex:a"), MeshPart("hands", *args, "tex:a")]
    texture = Texture("a", 2, 1, bytes([255, 0, 0, 255, 0, 255, 0, 255]))
    return Model(skeleton, parts, {"tex:a": texture})


def main():
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(glb.to_glb(mini_model()))
    print("wrote", OUT)


if __name__ == "__main__":
    main()
