#!/usr/bin/env python3
"""Puts the icons of the items (weapon-icons/*.png, the user's folder) in the mod as item textures under textures/item/gta/<id>.png.
Those textures are not committed (the icons come from the GTA community, whose terms are not ours to redistribute): the folder is
git-ignored, and an item without an icon (or with a file that is not a PNG) gets the generic icon, so a clean checkout still builds."""
import shutil
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / "cj-fabric" / "src" / "main" / "resources" / "assets" / "cj_fabric" / "textures" / "item"
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"

# item id -> the file of weapon-icons/ that is its icon
ICONS = {
    "colt45": "Pistol.png",
    "desert_eagle": "Desert Eagle.png",
    "chromegun": "Shotgun.png",
    "micro_uzi": "Micro SMG.png",
    "ak47": "AK47.png",
    "m4": "M4.png",
    "minigun": "Minigun.png",
    "rocket_launcher": "Rocket.png",
    "baseball_bat": "Bat.png",
    "jetpack": "Jetpack.png",
    "ammo1": "ammo1.png",
    "ammo2": "ammo2.png",
    "ammo3": "ammo3.png",
    "skateboard": "Skateboard.png",
}
# the generic icon (in textures/item) of an item without its own; the weapons' otherwise
GENERIC = {"jetpack": "jetpack_icon.png", "skateboard": "skateboard.png",
           # the ammo boxes were drawn by the user for this mod: they ship with it
           "ammo1": "ammo1_icon.png", "ammo2": "ammo2_icon.png", "ammo3": "ammo3_icon.png"}


@dataclass
class Report:
    copied: list = field(default_factory=list)
    fallback: list = field(default_factory=list)


def is_png(path):
    try:
        with open(path, "rb") as f:
            return f.read(8) == PNG_SIGNATURE
    except OSError:
        return False


def install(src, out, generic):
    """Writes out/<id>.png for every item: the icon of `src` when there is a PNG for it, the generic one of `generic` otherwise."""
    src, out, generic = Path(src), Path(out), Path(generic)
    out.mkdir(parents=True, exist_ok=True)
    report = Report()
    for item_id, name in ICONS.items():
        source = src / name
        if is_png(source):
            shutil.copyfile(source, out / f"{item_id}.png")
            report.copied.append(item_id)
        else:
            fallback = generic / GENERIC.get(item_id, "weapon_icon.png")
            shutil.copyfile(fallback, out / f"{item_id}.png")
            report.fallback.append(item_id)
    return report


def main():
    report = install(ROOT / "weapon-icons", TEXTURES / "gta", TEXTURES)
    print(f"item icons: {len(report.copied)} from weapon-icons/, {len(report.fallback)} generic" +
          (f" ({', '.join(report.fallback)})" if report.fallback else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
