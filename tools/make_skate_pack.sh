#!/usr/bin/env bash
# Builds the Linux skate pack: the Skate 3 engine (SK8-ENGINE/skate-3-rust-engine at the pinned commit) with the host patch, one executable without
# Bevy's libraries beside it, its conversion tools, its licenses, skate.sh, INSTALL-skate.md, SOURCE.md and the patch; zipped and audited.
# Usage: tools/make_skate_pack.sh <version>     (needs git, cargo and python3)
set -euo pipefail
version="${1:?usage: make_skate_pack.sh <version>}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
commit=2e16697797c8358d16f747a8259354ff2921041a
work="$root/build/skate-pack/src"
target="$root/build/skate-pack/target"
out="$root/build/release/cj-skate-$version-linux"

if [[ ! -d "$work/.git" ]]; then
    git clone https://github.com/SK8-ENGINE/skate-3-rust-engine "$work"
fi
git -C "$work" checkout -q --force "$commit"
git -C "$work" clean -fdq
git -C "$work" apply "$root/skate-host/skate-engine-host.patch"

# rustc crashes now and then inside LLVM on some machines (a load problem: fewer jobs help): what was built is kept, the crate is cleaned and the
# build goes on. CJ_BUILD_ATTEMPTS and CJ_BUILD_JOBS change the defaults.
attempts="${CJ_BUILD_ATTEMPTS:-6}"
jobs="${CJ_BUILD_JOBS:-4}"
for attempt in $(seq 1 "$attempts"); do
    if cargo build --manifest-path "$work/Cargo.toml" --release -p skate-game --bin skate3rust --no-default-features --target-dir "$target" -j "$jobs"; then
        break
    fi
    [[ $attempt == "$attempts" ]] && { echo "the engine did not build" >&2; exit 1; }
    cargo clean --manifest-path "$work/Cargo.toml" --release -p skate-game --target-dir "$target"
done

rm -rf "$out"
mkdir -p "$out/motor/licenses"
cp "$target/release/skate3rust" "$out/motor/"
strip "$out/motor/skate3rust"  # the release profile keeps debug info: 1 GB without this, 120 MB with it
cp -r "$work/tools" "$out/motor/tools"
find "$out/motor/tools" -name __pycache__ -prune -exec rm -rf {} +
cp "$work/LICENSE" "$out/motor/licenses/skate-3-rust-engine-GPL-3.0.txt"
for license in "$work"/vendor/*/LICENSE*; do
    cp "$license" "$out/motor/licenses/$(basename "$(dirname "$license")")-$(basename "$license").txt"
done
cp "$root/tools/skate_pack/skate.sh" "$root/tools/skate_pack/INSTALL-skate.md" "$root/tools/skate_pack/SOURCE.md" "$root/skate-host/skate-engine-host.patch" "$out/"
chmod +x "$out/skate.sh" "$out/motor/skate3rust"

rm -f "$out.zip"
python3 -c 'import shutil, sys; shutil.make_archive(sys.argv[1], "zip", sys.argv[2], sys.argv[3])' "$out" "$(dirname "$out")" "$(basename "$out")"
python3 "$root/tools/skate_pack_audit.py" "$out.zip"
echo "pack: $out.zip"
