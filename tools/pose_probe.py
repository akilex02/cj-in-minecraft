#!/usr/bin/env python3
"""Stand-in for the Minecraft mod: plays scripted movement phases to the GTA bridge and checks the Pose packets."""
import math
import socket
import statistics
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "protocol"))
import cjproto as p  # noqa: E402

GTA_ADDR = ("127.0.0.1", 34560)
ECHO_ADDR = ("127.0.0.1", 34561)
RATE_HZ = 60.0
START = (100.0, 64.0, 100.0)

# name, seconds, horizontal speed in blocks/s, flags
PHASES = [
    ("idle", 4.0, 0.0, p.FLAG_ON_GROUND),
    ("walk", 5.0, 1.3, p.FLAG_ON_GROUND | p.FLAG_SNEAK),
    ("run", 5.0, 4.3, p.FLAG_ON_GROUND),
    ("sprint", 5.0, 5.6, p.FLAG_ON_GROUND | p.FLAG_SPRINT),
]


def drain(sock):
    packets = []
    while True:
        try:
            data, _ = sock.recvfrom(4096)
        except BlockingIOError:
            return packets
        packets.append(data)


def energy(poses):
    """Mean over every bone and axis of the standard deviation of its position during the phase."""
    by_id = [{b.node_id: b for b in po.bones} for po in poses]
    ids = set.intersection(*(set(d) for d in by_id))
    stds = []
    for node in ids:
        for axis in range(3):
            stds.append(statistics.pstdev([d[node].pos[axis] for d in by_id]))
    return statistics.fmean(stds) if stds else 0.0


def main():
    rx = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    rx.bind(ECHO_ADDR)
    rx.setblocking(False)
    tx = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    seq = 0
    pos = list(START)

    def send(speed, flags, vy=0.0):
        nonlocal seq
        state = p.State(seq=seq, ts_ms=int(time.monotonic() * 1000) & 0xFFFFFFFF, pos=tuple(pos),
                        vel=(speed, vy, 0.0), yaw=0.0, pitch=0.0, flags=flags)
        tx.sendto(p.pack_state(state), GTA_ADDR)
        seq += 1

    # Phase 0: let a previous session expire (1 s without state), or its stale packets would be mistaken for ours.
    quiet_since = time.monotonic()
    deadline = time.monotonic() + 6.0
    while time.monotonic() < deadline and time.monotonic() - quiet_since < 1.3:
        time.sleep(0.05)
        if drain(rx):
            quiet_since = time.monotonic()

    # Phase 1: handshake, until a Pose acknowledges one of our packets.
    handshake = set()
    engaged = False
    print("waiting for GTA to answer (load a game in GTA first; stand somewhere quiet) ...")
    deadline = time.monotonic() + 30.0
    while not engaged and time.monotonic() < deadline:
        handshake.add(seq)
        send(0.0, p.FLAG_ON_GROUND)
        time.sleep(0.05)
        for data in drain(rx):
            try:
                if p.unpack_pose(data).ack_seq in handshake:
                    engaged = True
            except ValueError:
                pass
    if not engaged:
        print("FAIL: no Pose from GTA within 30 s")
        return 2
    print("GTA answered with a Pose; running the phases (watch the GTA window)")

    # Phase 2: scripted movement.
    results = {}
    bad_quat = 0.0
    farthest = 0.0
    bone_sets = set()
    for name, seconds, speed, flags in PHASES:
        poses = []
        start = time.monotonic()
        next_send = start
        while time.monotonic() - start < seconds:
            pos[0] += speed / RATE_HZ
            send(speed, flags)
            for data in drain(rx):
                try:
                    pose = p.unpack_pose(data)
                except ValueError:
                    continue
                poses.append(pose)
                bone_sets.add(tuple(sorted(b.node_id for b in pose.bones)))
                for b in pose.bones:
                    bad_quat = max(bad_quat, abs(math.sqrt(sum(c * c for c in b.quat)) - 1.0))
                    farthest = max(farthest, math.sqrt(sum(c * c for c in b.pos)))
            next_send += 1.0 / RATE_HZ
            time.sleep(max(0.0, next_send - time.monotonic()))
        results[name] = poses

    # Informative: one jump (leaves the ground going up, then lands)
    start = time.monotonic()
    jump_poses = []
    next_send = start
    while time.monotonic() - start < 2.0:
        t = time.monotonic() - start
        airborne = 0.1 < t < 0.7
        send(0.0, 0 if airborne else p.FLAG_ON_GROUND, vy=8.0 if 0.1 < t < 0.2 else 0.0)
        for data in drain(rx):
            try:
                jump_poses.append(p.unpack_pose(data))
            except ValueError:
                pass
        next_send += 1.0 / RATE_HZ
        time.sleep(max(0.0, next_send - time.monotonic()))

    e = {name: energy(po) if len(po) > 2 else 0.0 for name, po in results.items()}
    print(f"{'phase':8} {'poses':>6} {'bones':>6} {'energy':>8}")
    for name, _, _, _ in PHASES:
        po = results[name]
        print(f"{name:8} {len(po):6d} {len(po[0].bones) if po else 0:6d} {e[name]:8.4f}")
    if len(jump_poses) > 2:
        print(f"{'jump':8} {len(jump_poses):6d} {len(jump_poses[0].bones):6d} {energy(jump_poses):8.4f}   (informative)")
    print(f"bone sets seen: {len(bone_sets)}; worst |quat|-1: {bad_quat:.5f}; farthest bone from root: {farthest:.2f} m")
    if bone_sets:
        print("node ids:", sorted(next(iter(bone_sets))))

    failures = []
    for name, _, _, _ in PHASES:
        if len(results[name]) < 40:
            failures.append(f"{name}: only {len(results[name])} poses")
    if len(bone_sets) != 1:
        failures.append(f"the set of bones changed ({len(bone_sets)} different sets)")
    elif not 20 <= len(next(iter(bone_sets))) <= 48:
        failures.append(f"unexpected bone count {len(next(iter(bone_sets)))}")
    if bad_quat > 0.01:
        failures.append(f"a quaternion is not unit length (off by {bad_quat:.4f})")
    if farthest > 3.0:
        failures.append(f"a bone is {farthest:.2f} m from the root: not in CJ's own frame")
    if not e["walk"] > 2 * e["idle"] + 0.002:
        failures.append("walk does not move the skeleton more than idle")
    if not e["run"] > 3 * e["idle"] + 0.004:
        failures.append("run does not move the skeleton more than idle")
    if not e["sprint"] >= 0.8 * e["run"]:
        failures.append("sprint moves the skeleton less than run")

    for f in failures:
        print("FAIL:", f)
    print("PASS" if not failures else "FAIL")
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main())
