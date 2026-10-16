#!/usr/bin/env python3
"""Stand-in for the Minecraft mod while the Skate engine runs in host mode (`skate3rust --host 127.0.0.1:34600 --test-world`):
sends the controller actions at 60 Hz and reports what the skater does. Pushes (A and X in turn, as the game's pushes are) after a second.
Usage: skate_host_probe.py [--blocks] [seconds, default 8] [host:port, default 127.0.0.1:34600]
--blocks first sends a world of blocks (a floor and a wall) and puts the skater on it."""
import math
import socket
import struct
import sys
import time

MAGIC = 0x48384B53  # "SK8H"
VERSION = 1
TYPE_INPUT, TYPE_POSE, TYPE_WORLD_SET, TYPE_WORLD_CLEAR, TYPE_TELEPORT = 1, 2, 3, 4, 5
ACTIONS = 18
ACTION = {"left_stick_x": 0, "left_stick_y": 1, "right_stick_x": 3, "right_stick_y": 4, "left_trigger": 6, "right_trigger": 7,
          "x": 14, "y": 15, "a": 16, "b": 17}


def pack_input(seq, actions):
    return struct.pack("<IHHII", MAGIC, VERSION, TYPE_INPUT, seq, 0) + struct.pack(f"<{ACTIONS}f", *actions)


def header(kind):
    return struct.pack("<IHH", MAGIC, VERSION, kind)


def pack_world_set(region, quads):
    """quads: lists of four (x, y, z) corners in the order the engine documents (a floor is (x0,z0), (x1,z0), (x1,z1), (x0,z1))."""
    body = b"".join(struct.pack("<12f", *[c for corner in quad for c in corner]) for quad in quads)
    return header(TYPE_WORLD_SET) + struct.pack("<II", region, len(quads)) + body


def pack_teleport(x, y, z, heading=0.0, velocity=(0.0, 0.0, 0.0)):
    return header(TYPE_TELEPORT) + struct.pack("<7f", x, y, z, heading, *velocity)


def facing(corners, normal):
    """The four corners in the order that makes the engine's triangles (0, 2, 1) and (0, 3, 2) face along `normal` (they are one-sided: a face seen from
    behind is not there). The engine's triangle is (corner 0, corner 2, corner 1) and its normal (corner 2 - corner 0) x (corner 1 - corner 0): the documented
    floor order (x0,z0), (x1,z0), (x1,z1), (x0,z1) gives (0, 1, 0)."""
    u = [corners[1][i] - corners[0][i] for i in range(3)]
    v = [corners[2][i] - corners[0][i] for i in range(3)]
    n = [v[1] * u[2] - v[2] * u[1], v[2] * u[0] - v[0] * u[2], v[0] * u[1] - v[1] * u[0]]
    return corners if sum(n[i] * normal[i] for i in range(3)) >= 0 else [corners[0], corners[3], corners[2], corners[1]]


def blocks_world(top=2.0, half=20, wall_z=15, wall_height=3):
    """A flat floor of 1 m blocks whose tops are at `top` (a square of 2*half+1 blocks centred on the origin) and a wall across x in [-5, 5] at z = wall_z,
    as the faces a Minecraft area would show. Returns the regions (id -> quads), at most 1000 quads each."""
    quads = []
    for x in range(-half, half + 1):
        for z in range(-half, half + 1):
            quads.append(facing([(x - .5, top, z - .5), (x + .5, top, z - .5), (x + .5, top, z + .5), (x - .5, top, z + .5)], (0, 1, 0)))
    wall = []
    for x in range(-5, 6):
        for y in range(wall_height):
            y0 = top + y
            # the face toward -z: seen from the skater, so its normal points to -z
            wall.append(facing([(x + .5, y0, wall_z - .5), (x - .5, y0, wall_z - .5), (x - .5, y0 + 1, wall_z - .5), (x + .5, y0 + 1, wall_z - .5)], (0, 0, -1)))
    regions, chunk = {}, 900
    for i in range(0, len(quads), chunk):
        regions[len(regions) + 1] = quads[i:i + chunk]
    regions[len(regions) + 1] = wall
    return regions


def pack_pose(tick, root, deck_basis, deck_position, bones):
    """What the engine sends after each tick: the tick, the root matrix (16 floats), the deck's basis (9) and position (3), and each bone's matrix (16)."""
    body = struct.pack(f"<{16 + 12}f", *root, *deck_basis, *deck_position) + b"".join(struct.pack("<16f", *bone) for bone in bones)
    return header(TYPE_POSE) + struct.pack("<II", tick, len(bones)) + body


def unpack_pose(data):
    """(tick, root matrix 16 floats, deck basis 9 + position 3, bones as a list of 16 floats) or None."""
    if len(data) < 24:
        return None
    magic, version, kind, tick, count = struct.unpack_from("<IHHII", data, 0)
    if magic != MAGIC or version != VERSION or kind != TYPE_POSE or len(data) != 8 + 8 + 64 + 48 + count * 64:
        return None
    floats = struct.unpack_from(f"<{16 + 12 + count * 16}f", data, 16)
    return tick, floats[:16], floats[16:25], floats[25:28], [floats[28 + 16 * i:44 + 16 * i] for i in range(count)]


def main(argv):
    blocks = "--blocks" in argv
    argv = [a for a in argv if a != "--blocks"]
    seconds = float(argv[1]) if len(argv) > 1 else 8.0
    host, _, port = (argv[2] if len(argv) > 2 else "127.0.0.1:34600").partition(":")
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(0.001)
    address = (host, int(port))
    if blocks:
        for region, quads in blocks_world().items():
            sock.sendto(pack_world_set(region, quads), address)
        sock.sendto(pack_teleport(0.0, 2.3, -10.0, 0.0), address)  # on the blocks, facing +z, toward the wall
        print("sent a world of blocks (floor top at y=2, wall at z=15) and a teleport to (0, 2.3, -10)")
    start = time.monotonic()
    seq = 0
    poses = []
    last_report = 0.0
    next_send = start
    while time.monotonic() - start < seconds:
        now = time.monotonic()
        t = now - start
        if now >= next_send:
            seq += 1
            next_send += 1 / 60
            actions = [0.0] * ACTIONS
            if t > 1.0:
                actions[ACTION["left_stick_y"]] = 0.0
                actions[ACTION["a" if int(t * 2) % 2 == 0 else "x"]] = 1.0  # push, alternating feet
            sock.sendto(pack_input(seq, actions), address)
        try:
            data, _ = sock.recvfrom(65535)
        except (socket.timeout, BlockingIOError):
            continue
        pose = unpack_pose(data)
        if pose:
            poses.append((t, pose))
            if t - last_report >= 1.0:
                last_report = t
                x, y, z = pose[3]
                print(f"t={t:4.1f}s tick={pose[0]:5d} bones={len(pose[4])} deck=({x:7.2f}, {y:6.2f}, {z:7.2f})")
    if not poses:
        print("no pose arrived: is the engine running with --host?")
        return 1
    ys = [p[1][3][1] for p in poses]
    zs = [p[1][3][2] for p in poses]
    print(f"height of the deck: from {min(ys):.2f} to {max(ys):.2f} m; farthest z reached: {max(zs):.2f}")
    first, last = poses[0][1][3], poses[-1][1][3]
    moved = math.dist(first, last)
    print(f"{len(poses)} poses in {seconds:g}s; the deck moved {moved:.2f} m (from {tuple(round(v, 2) for v in first)} to {tuple(round(v, 2) for v in last)})")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
