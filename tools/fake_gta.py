#!/usr/bin/env python3
"""A stand-in for the GTA bridge: sends Pose packets built from cj.glb to the Minecraft mod (127.0.0.1:34561).

Modes: `rest` (CJ in his bind pose), `spin` (the whole skeleton turns about the up axis) and `bob` (it rises and falls).
Do not run it while the real GTA bridge is also sending poses.
"""
import argparse
import math
import socket
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "protocol"))

import cjproto  # noqa: E402
from cjconvert import glb  # noqa: E402

DEFAULT_GLB = HERE.parent / "cj-fabric" / "run" / "config" / "cj_fabric" / "cj.glb"
MC_ADDR = ("127.0.0.1", 34561)


def matrix_to_quat(m):
    """Unit quaternion (x, y, z, w) of the rotation in a column-major 4x4 (its first three columns)."""
    m00, m10, m20 = m[0], m[1], m[2]
    m01, m11, m21 = m[4], m[5], m[6]
    m02, m12, m22 = m[8], m[9], m[10]
    trace = m00 + m11 + m22
    if trace > 0:
        s = math.sqrt(trace + 1.0) * 2
        q = ((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25 * s)
    elif m00 > m11 and m00 > m22:
        s = math.sqrt(1.0 + m00 - m11 - m22) * 2
        q = (0.25 * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s)
    elif m11 > m22:
        s = math.sqrt(1.0 + m11 - m00 - m22) * 2
        q = ((m01 + m10) / s, 0.25 * s, (m12 + m21) / s, (m02 - m20) / s)
    else:
        s = math.sqrt(1.0 + m22 - m00 - m11) * 2
        q = ((m02 + m20) / s, (m12 + m21) / s, 0.25 * s, (m10 - m01) / s)
    length = math.sqrt(sum(c * c for c in q))
    return tuple(c / length for c in q)


def rest_pose(gltf, binary):
    """Per joint, in the skeleton's order: (node id, position, quaternion) of its bind pose, the inverse of its inverse bind."""
    inverse_bind = glb.read_accessor(gltf, binary, gltf["skins"][0]["inverseBindMatrices"])
    bones = []
    for joint, ib in zip(gltf["skins"][0]["joints"], inverse_bind):
        bind = glb.mat4_inverse(list(ib))
        node_id = gltf["nodes"][joint]["extras"]["nodeId"]
        bones.append((node_id, (bind[12], bind[13], bind[14]), matrix_to_quat(bind)))
    return bones


def spin(bones, angle):
    """Turn every bone about the Z (up) axis of the model: positions rotate and so do the bones' own axes."""
    c, s = math.cos(angle), math.sin(angle)
    hz, hw = math.sin(angle / 2), math.cos(angle / 2)
    out = []
    for node_id, (x, y, z), (qx, qy, qz, qw) in bones:
        # rotation by (0, 0, hz, hw) applied after the bone's own: q' = qz_rot * q
        nx = hw * qx - hz * qy
        ny = hw * qy + hz * qx
        nz = hw * qz + hz * qw
        nw = hw * qw - hz * qz
        out.append((node_id, (c * x - s * y, s * x + c * y, z), (nx, ny, nz, nw)))
    return out


def bob(bones, dz):
    return [(node_id, (x, y, z + dz), quat) for node_id, (x, y, z), quat in bones]


def to_pose(seq, bones):
    return cjproto.Pose(ack_seq=seq, gta_time_ms=int(time.monotonic() * 1000) & 0xFFFFFFFF, flags=0,
                        bones=tuple(cjproto.Bone(node_id, pos, quat) for node_id, pos, quat in bones))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--glb", default=str(DEFAULT_GLB), help="the cj.glb written by tools/convert_assets.py")
    ap.add_argument("--mode", choices=("rest", "spin", "bob"), default="rest")
    ap.add_argument("--seconds", type=float, default=0.0, help="stop after this long (0 = until Ctrl-C)")
    ap.add_argument("--rate", type=float, default=50.0, help="poses per second")
    args = ap.parse_args()
    try:
        gltf, binary = glb.read_glb(Path(args.glb).read_bytes())
    except (OSError, ValueError) as e:
        print(f"error: cannot read {args.glb}: {e}")
        return 1
    bones = rest_pose(gltf, binary)
    print(f"sending {args.mode} poses of {len(bones)} bones to {MC_ADDR[0]}:{MC_ADDR[1]} at {args.rate:g} Hz (Ctrl-C to stop)")
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    start = time.monotonic()
    seq = 0
    try:
        while args.seconds <= 0 or time.monotonic() - start < args.seconds:
            t = time.monotonic() - start
            if args.mode == "spin":
                frame = spin(bones, t * math.pi)  # half a turn per second
            elif args.mode == "bob":
                frame = bob(bones, 0.15 * math.sin(t * 2 * math.pi))
            else:
                frame = bones
            sock.sendto(cjproto.pack_pose(to_pose(seq, frame)), MC_ADDR)
            seq += 1
            time.sleep(1.0 / args.rate)
    except KeyboardInterrupt:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
