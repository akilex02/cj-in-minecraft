#!/usr/bin/env python3
"""Draws the generic 16x16 weapon icon of the mod's items (our own artwork, not from the game)."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from cjconvert.txd import encode_png  # noqa: E402

ROWS = (
    "................",
    "................",
    "................",
    "..KKKKKKKKKKKK..",
    ".KGGGGGGGGGGGGK.",
    ".KGGGGGGGGGGGGK.",
    ".KKKKKKKKKKKKKK.",
    "...KGGK.........",
    "...KGGK.........",
    "...KGGK.........",
    "...KKKK.........",
    "................",
    "................",
    "................",
    "................",
    "................",
)
COLOURS = {".": (0, 0, 0, 0), "K": (30, 30, 30, 255), "G": (110, 110, 120, 255)}

assert len(ROWS) == 16 and all(len(r) == 16 for r in ROWS)
rgba = b"".join(bytes(COLOURS[c]) for row in ROWS for c in row)
JETPACK_ROWS = (
    "................",
    "...KKKK..KKKK...",
    "..KGGGGKKGGGGK..",
    "..KGGGGKKGGGGK..",
    "..KGRRGKKGRRGK..",
    "..KGGGGKKGGGGK..",
    "..KGGGGKKGGGGK..",
    "..KGGGGKKGGGGK..",
    "..KGGGGKKGGGGK..",
    "..KGGGGKKGGGGK..",
    "..KKKKKKKKKKKK..",
    "...KYK....KYK...",
    "....Y......Y....",
    "...YOY....YOY...",
    "....O......O....",
    "................",
)
JETPACK_COLOURS = {".": (0, 0, 0, 0), "K": (30, 30, 30, 255), "G": (150, 150, 160, 255), "R": (200, 40, 40, 255),
                   "Y": (240, 200, 60, 255), "O": (230, 120, 30, 255)}

out = Path(__file__).resolve().parent.parent / "cj-fabric" / "src" / "main" / "resources" / "assets" / "cj_fabric" / "textures" / "item" / "weapon_icon.png"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_bytes(encode_png(16, 16, rgba))
print(f"wrote {out}")


assert len(JETPACK_ROWS) == 16 and all(len(r) == 16 for r in JETPACK_ROWS)
jetpack = b"".join(bytes(JETPACK_COLOURS[c]) for row in JETPACK_ROWS for c in row)
out_jetpack = out.with_name("jetpack_icon.png")
out_jetpack.write_bytes(encode_png(16, 16, jetpack))
print(f"wrote {out_jetpack}")
