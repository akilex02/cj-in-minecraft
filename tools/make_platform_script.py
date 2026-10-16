#!/usr/bin/env python3
"""Writes the CLEO script that builds the flat floor the car drives on (modloader/CjPlatform/cleo/cj_platform.cs in the GTA folder).

Four flat tiles of the sea floor (models sbsbed4/5/3/6law2, collision of zero thickness) are lifted to z 1000, over the sea off Los Santos, where nothing else is:
x 148..596, y -2508..-2111 at least, the surface at z 1000. The script waits 8 s for the game to run, loads the models and makes the objects, not dynamic.
(A text IPL through modloader crashed the game while loading: the object instances need the script engine.) The constants of the bridge (vehicle_game.h)
say where the middle is: kPlatformX/Y/Z.
"""
import struct
import sys
from pathlib import Path

TILES = ((4241, 496.4140625, -2210.703125), (4242, 496.4140625, -2435.703125), (4246, 272.484375, -2210.703125), (4247, 272.484375, -2409.203125))
SURFACE_Z = 1000.0


def build():
    out = bytearray()

    def op(code, *args):
        out.extend(struct.pack("<H", code))
        for a in args:
            out.extend(a)

    i16 = lambda v: b"\x05" + struct.pack("<h", v)
    i8 = lambda v: b"\x04" + struct.pack("<b", v)
    f32 = lambda v: b"\x06" + struct.pack("<f", v)
    local = lambda n: b"\x03" + struct.pack("<H", n)
    op(0x0001, i16(8000))  # wait
    for model, _, _ in TILES:
        op(0x0247, i16(model))  # request model
    op(0x038B)  # load all requested models now
    for n, (model, x, y) in enumerate(TILES):
        op(0x0107, i16(model), f32(x), f32(y), f32(SURFACE_Z), local(n))  # n@ = create object
        op(0x0392, local(n), i8(0))  # set object dynamic: false
    op(0x0A93)  # end custom thread
    return bytes(out)


def main():
    gta = Path(sys.argv[1] if len(sys.argv) > 1 else "GTA San Andreas")
    target = gta / "modloader" / "CjPlatform" / "cleo" / "cj_platform.cs"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(build())
    print(f"wrote {target}")


if __name__ == "__main__":
    main()
