#!/usr/bin/env python3
"""Checks a skate pack before it is shared: nothing of the user's Skate 3 (the game, an ISO, a conversion, the converted skater or board) may be in it.
Usage: skate_pack_audit.py <pack.zip>"""
import sys
import zipfile
from pathlib import Path

FORBIDDEN_EXTENSIONS = (".xex", ".iso", ".glb")
FORBIDDEN_PARTS = ("/installations/", "/data/", "skate-spike-assets/")


def problems(zip_path):
    found = []
    with zipfile.ZipFile(zip_path) as z:
        for name in z.namelist():
            low = "/" + name.lower()
            if low.endswith(".glb") and "/motor/mods/" in low:
                continue  # the engine's own example mods (Skyline_Drive_Mod), tracked in its repository and bundled by its packaging
            if low.endswith(FORBIDDEN_EXTENSIONS):
                found.append(f"{name}: un archivo de Skate 3 o convertido")
            elif any(part in low for part in FORBIDDEN_PARTS):
                found.append(f"{name}: una conversión de Skate 3")
    return found


def main(argv):
    if len(argv) != 2:
        print(__doc__)
        return 2
    found = problems(Path(argv[1]))
    for line in found:
        print(line)
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
