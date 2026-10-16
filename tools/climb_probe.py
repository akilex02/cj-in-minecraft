#!/usr/bin/env python3
"""Stand-in for the Minecraft mod: stands still, then holds FLAG_WALL_CLIMB, and prints how the skeleton moves (lowest bone, mean height,
mean offset from the ped along the facing) relative to the standing pose. GTA must be running with a game loaded; close Minecraft first."""
import socket
import statistics
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "protocol"))
import cjproto as p  # noqa: E402

GTA_ADDR = ("127.0.0.1", 34560)
ECHO_ADDR = ("127.0.0.1", 34561)


def main(argv):
    variant = argv[1] if len(argv) > 1 else "plain"  # plain | air (no on-ground flag, as Minecraft sends) | jump (air + a jump intent at the start)
    rx = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    rx.bind(ECHO_ADDR)
    rx.setblocking(False)
    tx = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    seq = 0
    poses = []  # (monotonic, pose)

    def send(flags, intents=0):
        nonlocal seq
        state = p.State(seq=seq, ts_ms=int(time.monotonic() * 1000) & 0xFFFFFFFF, pos=(100.0, 64.0, 100.0), vel=(0.0, 0.0, 0.0),
                        yaw=0.0, pitch=0.0, flags=flags, intents=intents)
        tx.sendto(p.pack_state(state), GTA_ADDR)
        seq += 1

    def run(flags, seconds, intents=0):
        end = time.monotonic() + seconds
        while time.monotonic() < end:
            send(flags, intents)
            try:
                while True:
                    data, _ = rx.recvfrom(4096)
                    try:
                        poses.append((time.monotonic(), p.unpack_pose(data)))
                    except ValueError:
                        pass
            except BlockingIOError:
                pass
            time.sleep(1 / 60)

    run(p.FLAG_ON_GROUND, 2.0)
    mark = time.monotonic()
    base_poses = [po for t, po in poses]
    if variant == "plain":
        run(p.FLAG_ON_GROUND | p.FLAG_WALL_CLIMB, 3.2)
    else:
        if variant == "jump":
            run(p.FLAG_WALL_CLIMB, 0.1, p.INTENT_JUMP)
            run(p.FLAG_WALL_CLIMB, 3.1)
        else:
            run(p.FLAG_WALL_CLIMB, 3.2)
    run(p.FLAG_ON_GROUND, 1.5)
    if not base_poses:
        print("no pose received: is a game loaded in GTA and Minecraft closed?")
        return 1

    def metrics(po):
        ys = [b.pos[1] for b in po.bones]
        return min(ys), statistics.fmean(ys), max(ys), statistics.fmean(b.pos[0] for b in po.bones), statistics.fmean(b.pos[2] for b in po.bones)

    base = metrics(base_poses[-1])
    print("standing: min y %.3f mean y %.3f x %.3f z %.3f" % (base[0], base[1], base[3], base[4]))
    print("standing max y %.3f" % base[2])
    last = -1.0
    for t, po in poses:
        if t < mark or t - last < 0.1:
            continue
        last = t
        m = metrics(po)
        print("t=%.2f  d(min y) %+.3f  d(mean y) %+.3f  d(max y) %+.3f  dx %+.3f dz %+.3f" % (t - mark, m[0] - base[0], m[1] - base[1], m[2] - base[2], m[3] - base[3], m[4] - base[4]))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
