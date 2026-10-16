#!/usr/bin/env bash
# Opens GTA San Andreas (with Wine) with the bridge; then open Minecraft with the Fabric profile.
exec python3 "$(dirname "$0")/setup/cj_setup.py" play
