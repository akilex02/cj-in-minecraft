#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cmake -S "$root/gta-bridge" -B "$root/build/bridge" -G Ninja -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$root/gta-bridge/cmake/mingw-i686.cmake"
cmake --build "$root/build/bridge"
cp "$root/build/bridge/cj_bridge.asi" "$root/GTA San Andreas/cj_bridge.asi"
echo "installed: $root/GTA San Andreas/cj_bridge.asi"
