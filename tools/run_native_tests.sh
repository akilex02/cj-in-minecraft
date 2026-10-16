#!/usr/bin/env bash
# Compiles and runs every native test suite of the bridge with the host compiler (clang++).
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
for t in protocol puppet pose pad_driver call_hook weapon_driver body_reactions vehicle_driver vehicle_math radio eat_driver bridge_core; do
  src="$root/gta-bridge/tests/test_$t.cpp"
  [ -f "$src" ] || continue # weapon_driver exists from task 3 on
  ${CXX:-clang++} -std=c++20 -Wall -I"$root/gta-bridge/src" "$src" -o "$out/t_$t"
  if [ "$t" = protocol ]; then "$out/t_$t" "$root/protocol/golden"; else "$out/t_$t"; fi
done
