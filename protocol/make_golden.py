#!/usr/bin/env python3
"""Regenerates protocol/golden/*.bin from the fixed packets used by every implementation's tests."""
from pathlib import Path

import cjproto as p
from test_cjproto import (fixed_echo, fixed_pose, fixed_shot, fixed_state, fixed_vehicle_command, fixed_vehicle_input,
                          fixed_vehicle_state)

out = Path(__file__).parent / "golden"
out.mkdir(exist_ok=True)
for old in out.glob("*_v1.bin"):
    old.unlink()
(out / "state_v2.bin").write_bytes(p.pack_state(fixed_state()))
(out / "echo_v2.bin").write_bytes(p.pack_echo(fixed_echo()))
(out / "pose_v2.bin").write_bytes(p.pack_pose(fixed_pose()))
(out / "shot_v2.bin").write_bytes(p.pack_shot(fixed_shot()))
(out / "vehicle_command_v2.bin").write_bytes(p.pack_vehicle_command(fixed_vehicle_command()))
(out / "vehicle_input_v2.bin").write_bytes(p.pack_vehicle_input(fixed_vehicle_input()))
(out / "vehicle_state_v2.bin").write_bytes(p.pack_vehicle_state(fixed_vehicle_state()))
print("golden files written")
