#!/usr/bin/env python3
"""Draws the 16 x 16 icon of the Skateboard item (a deck seen from the side with two wheels) into the mod's textures. Needs Pillow."""
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "cj-fabric" / "src" / "main" / "resources" / "assets" / "cj_fabric" / "textures" / "item" / "skateboard.png"

DECK = (176, 124, 70, 255)
DECK_EDGE = (120, 78, 40, 255)
GRIP = (40, 40, 44, 255)
TRUCK = (170, 175, 180, 255)
WHEEL = (235, 235, 235, 255)
WHEEL_SHADE = (150, 150, 155, 255)


def main():
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = image.load()
    # the deck, a little tilted at its two ends (the nose and the tail)
    for x in range(1, 15):
        y = 7
        if x <= 2:
            y = 6 if x == 1 else 7
        if x >= 13:
            y = 6 if x == 14 else 7
        px[x, y] = GRIP
        px[x, y + 1] = DECK
        px[x, y + 2] = DECK_EDGE
    px[1, 6] = GRIP
    px[14, 6] = GRIP
    # trucks and wheels
    for cx in (4, 11):
        px[cx, 10] = TRUCK
        px[cx + 1, 10] = TRUCK
        for dx in (0, 1):
            for dy in (11, 12):
                px[cx + dx, dy] = WHEEL if dy == 11 else WHEEL_SHADE
    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
