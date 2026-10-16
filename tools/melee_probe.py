#!/usr/bin/env python3
"""Stand-in for the Minecraft mod: holds INTENT_MELEE (the left mouse button) with fists (weapon 0) or the bat (weapon 5), then lets go,
and reports the blows GTA counted as Shot packets. Usage: melee_probe.py [weapon id, default 0] [cycles, default 2].
GTA must be running with a game loaded and nothing else talking to the bridge (close Minecraft)."""
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
    weapon = int(argv[1]) if len(argv) > 1 else 0
    cycles = int(argv[2]) if len(argv) > 2 else 2
    rx = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    rx.bind(ECHO_ADDR)
    rx.setblocking(False)
    tx = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    seq = 0
    held = 0
    shots = {}  # shot_seq -> (gta_time_ms, rounds, weapon): each event arrives twice

    def send(intents):
        nonlocal seq
        state = p.State(seq=seq, ts_ms=int(time.monotonic() * 1000) & 0xFFFFFFFF, pos=(100.0, 64.0, 100.0), vel=(0.0, 0.0, 0.0),
                        yaw=0.0, pitch=0.0, flags=p.FLAG_ON_GROUND, intents=intents, weapon=weapon)
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
                shots[shot.shot_seq] = (shot.gta_time_ms, shot.rounds, shot.weapon)
                continue
            except ValueError:
                pass
            try:
                held = p.unpack_echo(data).weapon_held
            except ValueError:
                pass

    def hold(intents, seconds):
        start = time.monotonic()
        next_send = start
        while time.monotonic() - start < seconds:
            send(intents)
            drain()
            next_send += 1.0 / RATE_HZ
            time.sleep(max(0.0, next_send - time.monotonic()))

    hold(0, 3.0)  # equip
    for cycle in range(cycles):
        before = len(shots)
        print(f"cycle {cycle + 1}: striking for 8 s (watch the GTA window), then letting go for 3 s")
        hold(p.INTENT_MELEE, 8.0)
        hold(0, 3.0)
        blows = sum(r for _, r, _ in list(shots.values())[before:])
        print(f"  blows reported by the bridge: {blows}")
    print("weapon held according to GTA:", held)
    total = sum(r for _, r, _ in shots.values())
    weapons = sorted({w for _, _, w in shots.values()})
    print(f"{len(shots)} shot events, {total} blows in all; weapon ids in the events: {weapons}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
