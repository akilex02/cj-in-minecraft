# Source of the Skate engine in this pack

This pack carries the **Skate 3 Rust engine** (https://github.com/SK8-ENGINE/skate-3-rust-engine), licensed under the **GPL-3.0** (see
`motor/licenses/`), built with one change: the patch `skate-engine-host.patch`, which adds the **host mode** (`--host`): the Minecraft mod drives the
skater over UDP on your own computer (127.0.0.1) and gets back its pose.

- Original repository: https://github.com/SK8-ENGINE/skate-3-rust-engine
- Commit: `2e16697797c8358d16f747a8259354ff2921041a`
- Change: `skate-engine-host.patch` (in this folder)

## Rebuilding the executable

```
git clone https://github.com/SK8-ENGINE/skate-3-rust-engine
cd skate-3-rust-engine
git checkout 2e16697797c8358d16f747a8259354ff2921041a
git apply ../skate-engine-host.patch
```

- **Linux**: `cargo build --release -p skate-game --bin skate3rust --no-default-features` (it ends up in `target/release/skate3rust`).
- **Windows**: `scripts\Build-Release.ps1` (Rust with MSVC, LLVM and Python 3.13; it leaves the portable package in
  `target\skate3rust-windows-x64.zip`). Build it in a short folder: Windows does not allow paths over 260 characters.

**Nothing of Skate 3 is included.** The game and its converted files are yours and stay on your computer.
