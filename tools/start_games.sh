#!/usr/bin/env bash
# Opens GTA San Andreas (with the bridge) and then Minecraft (the mod), each only once.
#   tools/start_games.sh            open whichever of the two is not running
#   tools/start_games.sh --restart  close both first, then open both (use it after reinstalling the bridge)
# GTA has to start first: the bridge is loaded when GTA starts. Load your save in GTA once it is up.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
gta_dir="$root/GTA San Andreas"
logdir="${TMPDIR:-/tmp}/cj-games"
mkdir -p "$logdir"

# Process ids are matched by their real command lines (never `pgrep -f` with a name that this script's own command line contains).
gta_pids() { ps -eo pid,args | awk '$2 ~ /^Z:/ && /gta_sa\.exe/ {print $1}'; }
mc_pids() {
    for p in $(pgrep java || true); do
        if tr '\0' ' ' < "/proc/$p/cmdline" 2>/dev/null | grep -q 'KnotClient'; then echo "$p"; fi
    done
}

if [[ "${1:-}" == "--restart" ]]; then
    echo "closing the games..."
    for p in $(mc_pids); do kill "$p"; done
    for p in $(gta_pids); do kill "$p"; done
    sleep 8
fi

if [[ ! -f "$gta_dir/cj_bridge.asi" ]]; then
    echo "the bridge is not installed: run  bash tools/install_bridge.sh  first" >&2
    exit 1
fi

if [[ -n "$(gta_pids)" ]]; then
    echo "GTA is already running (pid $(gta_pids | tr '\n' ' '))"
else
    echo "opening GTA San Andreas..."
    (cd "$gta_dir" && nohup wine gta_sa.exe < /dev/null > "$logdir/gta.log" 2>&1 &)
    sleep 10
fi

if [[ -n "$(mc_pids)" ]]; then
    echo "Minecraft is already running (pid $(mc_pids | tr '\n' ' '))"
else
    echo "opening Minecraft..."
    python3 "$root/tools/install_item_icons.py" || echo "(the item icons could not be installed; the generic ones are used)" >&2
    (cd "$root/cj-fabric" && nohup ./gradlew runClient --console=plain < /dev/null > "$logdir/minecraft.log" 2>&1 &)
fi

echo "logs: $logdir/gta.log and $logdir/minecraft.log"
echo "load your save in GTA; in Minecraft enter a world and use /give @s cj_fabric:<weapon> (colt45, desert_eagle, chromegun, micro_uzi, ak47, m4, minigun, rocket_launcher)"
