#!/usr/bin/env python3
"""A fake Minecraft: sends v2 State packets to the bridge and prints what comes back (held weapon, shots, right hand).

Close Minecraft first: both listen on 127.0.0.1:34561. Examples:
  python3 tools/fake_mc.py --mode held  --weapon 22 --seconds 8   # CJ gets the weapon, no aiming
  python3 tools/fake_mc.py --mode aim   --weapon 22 --seconds 8   # right mouse button held, looking straight ahead
  python3 tools/fake_mc.py --mode sweep --weapon 22 --seconds 12  # aiming while the look direction sweeps up/down and sideways
  python3 tools/fake_mc.py --mode fire  --weapon 22 --seconds 8   # aiming and holding the left mouse button
"""
import argparse
import math
import socket
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "protocol"))
import cjproto  # noqa: E402

GTA_ADDR = ("127.0.0.1", 34560)
MC_ADDR = ("127.0.0.1", 34561)
HAND_NODE = 24  # nodeID of the right hand in CJ's skeleton


def look_vector(yaw_deg, pitch_deg):
    """Minecraft's look direction: yaw 0 faces south (+Z), yaw -90 east (+X); pitch -90 is straight up."""
    yaw, pitch = math.radians(yaw_deg), math.radians(pitch_deg)
    return (-math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch))


def aim_vector(yaw_deg, pitch_deg, eye_height=1.62, distance=20.0):
    """Feet -> aim point, the way the mod computes it."""
    x, y, z = look_vector(yaw_deg, pitch_deg)
    return (x * distance, eye_height + y * distance, z * distance)


def yaw_of_quat(q):
    """The turn about the vertical axis (degrees) of a rotation (x, y, z, w), the way a ground heading is read from it."""
    x, y, z, w = q
    return math.degrees(math.atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z)))


class Scenario:
    MODES = ("idle", "held", "aim", "fire", "sweep", "sweepfire", "spin", "pitch")

    def __init__(self, mode, weapon, distance=20.0, pitch_amp=40.0, spin_rate=90.0, speed=0.0, sprint=False):
        if mode not in self.MODES:
            raise ValueError(f"unknown mode {mode!r}")
        self.mode = mode
        self.weapon = weapon
        self.distance = distance
        self.pitch_amp = pitch_amp
        self.spin_rate = spin_rate
        self.speed = speed
        self.sprint = sprint

    def state_at(self, t, seq):
        yaw, pitch = 0.0, 0.0
        weapon, intents, aim = 0, 0, (0.0, 0.0, 0.0)
        if self.mode != "idle":
            weapon = self.weapon
        if self.mode in ("sweep", "sweepfire"):
            yaw = 60.0 * math.sin(t * 0.8)
            pitch = self.pitch_amp * math.sin(t * 1.3)
        if self.mode == "pitch":
            pitch = self.pitch_amp * math.sin(t * 1.0)
        if self.mode == "spin":
            yaw = (t * self.spin_rate + 180.0) % 360.0 - 180.0  # turns all the way round, wrapped like Minecraft's yaw
        if self.mode in ("aim", "fire", "sweep", "sweepfire", "spin", "pitch"):
            intents |= cjproto.INTENT_AIM
            aim = aim_vector(yaw, pitch, distance=self.distance)
        if self.mode in ("fire", "sweepfire"):
            intents |= cjproto.INTENT_FIRE
        return cjproto.State(seq=seq, ts_ms=int(t * 1000) & 0x7FFFFFFF, pos=(0.0, 64.0, 0.0), vel=(0.0, 0.0, self.speed),
                             yaw=yaw, pitch=pitch, flags=cjproto.FLAG_ON_GROUND | (cjproto.FLAG_SPRINT if self.sprint else 0), intents=intents, weapon=weapon, aim=aim)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--mode", choices=Scenario.MODES, default="aim")
    ap.add_argument("--weapon", type=int, default=22, help="GTA weapon id: 22 Colt 45, 24 Desert Eagle, 25 shotgun, 28 Uzi, 30 AK-47, 31 M4")
    ap.add_argument("--seconds", type=float, default=8.0)
    ap.add_argument("--bones", default="", help="comma-separated nodeIDs whose turn about the vertical axis is printed with each report")
    ap.add_argument("--report", type=float, default=1.0, help="seconds between reports")
    ap.add_argument("--distance", type=float, default=20.0, help="how far away the aim point is, in blocks (the mod uses 2 to 50)")
    ap.add_argument("--speed", type=float, default=0.0, help="horizontal speed of the player, blocks per second")
    ap.add_argument("--sprint", action="store_true", help="set the sprint flag")
    ap.add_argument("--spin-rate", type=float, default=90.0, help="degrees per second of the spin mode")
    ap.add_argument("--pitch-amp", type=float, default=40.0, help="amplitude of the pitch sweep, in degrees")
    args = ap.parse_args(argv)

    rx = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    rx.bind(MC_ADDR)
    rx.settimeout(0.0)
    tx = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    scenario = Scenario(args.mode, args.weapon, args.distance, args.pitch_amp, args.spin_rate, args.speed, args.sprint)

    start = time.monotonic()
    seq, next_send = 0, start
    held, last_report = -1, 0.0
    shots_seen, last_shot_seq = 0, 0
    hand = None
    bones_now = {}
    wanted = [int(b) for b in args.bones.split(",") if b]
    while True:
        now = time.monotonic()
        t = now - start
        if t > args.seconds:
            break
        if now >= next_send:
            tx.sendto(cjproto.pack_state(scenario.state_at(t, seq)), GTA_ADDR)
            seq += 1
            next_send += 1 / 60
        try:
            while True:
                data = rx.recv(2048)
                try:
                    echo = cjproto.unpack_echo(data)
                    if echo.weapon_held != held:
                        held = echo.weapon_held
                        print(f"[{t:5.2f}s] CJ holds weapon {held}")
                    continue
                except ValueError:
                    pass
                try:
                    shot = cjproto.unpack_shot(data)
                    if shot.shot_seq != last_shot_seq:
                        shots_seen += shot.rounds
                        last_shot_seq = shot.shot_seq
                        print(f"[{t:5.2f}s] SHOT weapon={shot.weapon} rounds={shot.rounds} total={shot.shot_seq}")
                    continue
                except ValueError:
                    pass
                try:
                    pose = cjproto.unpack_pose(data)
                    for bone in pose.bones:
                        bones_now[bone.node_id] = bone
                        if bone.node_id == HAND_NODE:
                            hand = bone.pos
                except ValueError:
                    pass
        except BlockingIOError:
            pass
        if hand and t - last_report >= args.report:
            last_report = t
            turns = "".join(f" node{n} yaw={yaw_of_quat(bones_now[n].quat):+.1f}" for n in wanted if n in bones_now)
            print(f"[{t:5.2f}s] pitch={scenario.state_at(t, 0).pitch:+.1f} right hand (model space, X fwd / Y left / Z up): "
                  f"{hand[0]:+.2f} {hand[1]:+.2f} {hand[2]:+.2f}{turns}")
        time.sleep(0.002)
    print(f"done: {seq} states sent, {shots_seen} rounds reported")
    return 0


if __name__ == "__main__":
    sys.exit(main())
