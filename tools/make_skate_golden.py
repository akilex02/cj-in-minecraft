#!/usr/bin/env python3
"""Writes the golden datagrams of the skate host protocol (protocol/golden/skate_*.bin) with the packers of skate_host_probe.py, so the Java client can be
tested against bytes it did not make itself. Run it again only when the protocol changes (skate-host/README.md)."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import skate_host_probe as p  # noqa: E402

OUT = Path(__file__).resolve().parent.parent / "protocol" / "golden"

ACTIONS = [0.5, -0.25, 0.0, 1.0, -1.0, 0.0, 0.75, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0]
FLOOR = [(-1.0, 2.0, -1.0), (1.0, 2.0, -1.0), (1.0, 2.0, 1.0), (-1.0, 2.0, 1.0)]
WALL = [(1.0, 2.0, 4.5), (-1.0, 2.0, 4.5), (-1.0, 3.0, 4.5), (1.0, 3.0, 4.5)]


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    root = tuple(float(i) / 4 for i in range(16))
    basis = tuple(float(i) / 8 for i in range(9))
    bones = [tuple(float(i + 16 * b) / 2 for i in range(16)) for b in range(3)]
    files = {
        "skate_input_v1.bin": p.pack_input(7, ACTIONS),
        "skate_world_v1.bin": p.pack_world_set(9, [FLOOR, WALL]),
        "skate_clear_v1.bin": p.header(p.TYPE_WORLD_CLEAR) + (9).to_bytes(4, "little"),
        "skate_teleport_v1.bin": p.pack_teleport(1.0, 2.5, 3.0, 0.5, (0.25, 0.0, -0.5)),
        "skate_pose_v1.bin": p.pack_pose(77, root, basis, (1.5, 2.0, -3.25), bones),
    }
    for name, data in files.items():
        (OUT / name).write_bytes(data)
        print(f"wrote {name} ({len(data)} bytes)")


if __name__ == "__main__":
    main()
