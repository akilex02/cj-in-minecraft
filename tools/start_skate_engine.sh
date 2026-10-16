#!/usr/bin/env bash
# Starts the Skate 3 engine in host mode (skate-host/README.md) so the mod can skate. Needs the engine built with the host patch and the contents converted
# from your own copy of Skate 3 (see skate-host/README.md). Set:
#   SKATE3_ENGINE   the skate3rust executable (target/release/skate3rust of the engine's build)
#   SKATE3_ASSETS   the converted assets folder (installations/<id>/assets)
#   SKATE3_ADDRESS  where it listens (default 127.0.0.1:34600; the mod reads CJ_SKATE_ENGINE for another one)
# Usage: SKATE3_ENGINE=... SKATE3_ASSETS=... tools/start_skate_engine.sh
set -euo pipefail

engine="${SKATE3_ENGINE:?set SKATE3_ENGINE to the skate3rust executable}"
assets="${SKATE3_ASSETS:?set SKATE3_ASSETS to the converted assets folder}"
address="${SKATE3_ADDRESS:-127.0.0.1:34600}"
[[ -x "$engine" ]] || { echo "not an executable: $engine" >&2; exit 1; }
[[ -d "$assets" ]] || { echo "not a folder: $assets" >&2; exit 1; }

# Bevy is linked dynamically in the engine's default build: its library sits next to the executable in deps/
lib="$(dirname "$engine")/deps"
if command -v rustc >/dev/null 2>&1; then lib="$lib:$(rustc --print sysroot)/lib"; fi
export LD_LIBRARY_PATH="$lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

echo "starting the Skate engine on $address"
cd "$(dirname "$engine")"
exec "$engine" --assets "$assets" --test-world --host "$address" --mute
