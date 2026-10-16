#!/usr/bin/env bash
# Opens the Skate engine in host mode (after GTA, before Minecraft), with the folders "cj_setup.py skate" saved.
here="$(cd "$(dirname "$0")" && pwd)"
setup="${CJ_SETUP:-}"
if [[ -z "$setup" ]]; then
    # the base package unzipped beside this one: cj-mod, or cj-mod-<version> as it comes out of the zip
    for candidate in "$here/../cj-mod/setup/cj_setup.py" "$here"/../cj-mod*/setup/cj_setup.py; do
        if [[ -f "$candidate" ]]; then setup="$candidate"; break; fi
    done
fi
[[ -n "$setup" && -f "$setup" ]] || { echo "cannot find cj_setup.py: put the skate pack next to the cj-mod folder, or give its path with CJ_SETUP=/path/to/setup/cj_setup.py ./skate.sh" >&2; exit 1; }
exec python3 "$setup" play-skate
