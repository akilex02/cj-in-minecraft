#!/usr/bin/env python3
"""Stand-in for the Minecraft mod: holds the aim and fire intents with one weapon and reports the rounds per second GTA fires.
Usage: gun_probe.py [weapon id, default 38] [seconds, default 6]. GTA must be running with a game loaded and nothing else
talking to the bridge (close Minecraft)."""
import socket
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "protocol"))
import cjproto as p  # noqa: E402

GTA_ADDR = ("127.0.0.1", 34560)
ECHO_ADDR = ("127.0.0.1", 34561)
RATE_HZ = 60.0


def main(argv):
    weapon = int(argv[1]) if len(argv) > 1 else 38
    seconds = float(argv[2]) if len(argv) > 2 else 6.0
    rx = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    rx.bind(ECHO_ADDR)
    rx.setblocking(False)
    tx = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    seq = 0
    shots = []  # (gta_time_ms, shot_seq, rounds)
    held = 0

    def send(intents):
        nonlocal seq
        state = p.State(seq=seq, ts_ms=int(time.monotonic() * 1000) & 0xFFFFFFFF, pos=(100.0, 64.0, 100.0), vel=(0.0, 0.0, 0.0),
                        yaw=0.0, pitch=0.0, flags=p.FLAG_ON_GROUND, intents=intents, weapon=weapon, aim=(0.0, 1.6, 20.0))
        tx.sendto(p.pack_state(state), GTA_ADDR)
        seq += 1

    def drain():
        nonlocal held
        while True:
            try:
                data, _ = rx.recvfrom(4096)
            except BlockingIOError:
                return
            try:
                shot = p.unpack_shot(data)
                shots.append((shot.gta_time_ms, shot.shot_seq, shot.rounds))
                continue
            except ValueError:
                pass
            try:
                held = p.unpack_echo(data).weapon_held
            except ValueError:
                pass

    print("holding the weapon for 3 s (aim only), then aim + fire for %.0f s ..." % seconds)
    for phase, intents, length in (("equip", 0, 3.0), ("fire", p.INTENT_AIM | p.INTENT_FIRE, seconds)):
        start = time.monotonic()
        next_send = start
        while time.monotonic() - start < length:
            send(intents)
            drain()
            next_send += 1.0 / RATE_HZ
            time.sleep(max(0.0, next_send - time.monotonic()))
        if phase == "equip":
            shots.clear()
    send(0)
    time.sleep(0.2)
    drain()
    print("weapon held according to GTA:", held)
    if len(shots) < 2:
        print("FAIL: fewer than two shot events (is the weapon held? is the game loaded?)")
        return 2
    first, last = shots[0], shots[-1]
    total = last[1] - first[1] + first[2]
    span_s = max(1e-3, (last[0] - first[0]) / 1000.0)
    biggest = max(r for _, _, r in shots)
    print(f"{len(shots)} shot events, {total} rounds over {span_s:.2f} s of GTA time = {total / span_s:.1f} rounds/s; "
          f"most rounds in one event: {biggest}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
