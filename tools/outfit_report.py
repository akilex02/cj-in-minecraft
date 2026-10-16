#!/usr/bin/env python3
"""Says what CJ wears in a cj_outfit.json (the file the bridge writes in the GTA folder): the body of his stats and the name of each model and texture
the game keeps as a key. A key that no file of the game has the name of is shown with a question mark."""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from cjconvert.bodyblend import body_ratios  # noqa: E402
from cjconvert.clothes import MODEL_SLOTS, TEXTURE_SLOTS, key_tables, load_outfit, name_keys  # noqa: E402
from cjconvert.img import ImgArchive  # noqa: E402
from cjconvert.rwchunk import FormatError  # noqa: E402
from cjconvert.txd import parse_txd  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_GTA = ROOT / "GTA San Andreas"


def key_names(player):
    """{key: name} for the models (the .dff of player.img) and the textures (the .txd of player.img)."""
    return key_tables(player.names(), lambda name: parse_txd(player.read(name)))


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("outfit", nargs="?", default=str(DEFAULT_GTA / "cj_outfit.json"), help="the cj_outfit.json (default: the one in the GTA folder)")
    ap.add_argument("--gta", default=str(DEFAULT_GTA), help="the GTA San Andreas folder (the one with models/)")
    args = ap.parse_args(argv)
    try:
        outfit = load_outfit(args.outfit)
        player = ImgArchive(Path(args.gta) / "models" / "player.img")
    except (ValueError, FormatError, OSError) as e:
        print(f"error: {e}")
        return 1
    ripped, fatness, normal = body_ratios(outfit.muscle, outfit.fat)
    print(f"muscle {outfit.muscle:g}, fat {outfit.fat:g}: the body is {ripped:.0%} ripped, {fatness:.0%} fat, {normal:.0%} normal")
    models, textures = key_names(player)
    print("models:")
    for slot, name in zip(MODEL_SLOTS, name_keys(outfit.models, models)):
        print(f"  {slot:14} {name or '-'}")
    print("textures (layers over the body):")
    for slot, name in zip(TEXTURE_SLOTS, name_keys(outfit.textures, textures)):
        print(f"  {slot:14} {name or '-'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
