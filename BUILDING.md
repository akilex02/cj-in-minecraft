# Building from source

## Layout

- `cj-fabric/` — the Minecraft mod (Fabric 1.21.1, Java 21).
- `gta-bridge/` — the GTA San Andreas side: an ASI plugin (C++20, 32-bit) that talks to the mod over UDP on 127.0.0.1.
- `protocol/` — the datagrams between the mod and the bridge, with golden test files.
- `tools/` — the converter of CJ, the weapons and the car from a GTA folder (`convert_assets.py`), the installer (`cj_setup.py`) and the packaging.
- `skate-host/` — the host-mode patch for the Skate 3 Rust engine.
- `docs/` — the design documents and test notes of each part (in Spanish).

## The mod

```
cd cj-fabric
./gradlew build            # JDK 21; the jar is in build/libs/
./gradlew runClient        # a development Minecraft
```

The models are read from `config/cj_fabric/` of the Minecraft folder (`cj-fabric/run/` in development). Convert them from a GTA folder:

```
python3 tools/convert_assets.py --gta "<GTA folder>" --weapons --vehicles
```

## The bridge

Needs CMake, Ninja and the mingw-w64 i686 cross compiler (`i686-w64-mingw32-g++`).

```
bash tools/install_bridge.sh    # builds cj_bridge.asi and copies it into "GTA San Andreas/" of the repository folder
```

## Tests

```
cd tools && python3 -B -m unittest discover -s tests
cd cj-fabric && ./gradlew test
cd protocol && python3 -B -m unittest test_cjproto
g++ -std=c++20 -Igta-bridge/src gta-bridge/tests/test_<name>.cpp -o build/test_<name> && ./build/test_<name>
```

## Packages

- The mod's package: `python3 tools/make_release.py <version>` → `build/release/cj-mod-<version>.zip` (built with the generic item icons; it fails
  if anything of GTA or the community's icons would go in).
- The Linux skate pack: `bash tools/make_skate_pack.sh <version>` (git, cargo, python3) → `build/release/cj-skate-<version>-linux.zip`.
- The Windows skate pack is built on Windows: `python3 tools/make_skate_builder.py <version>` makes a small zip; on Windows, unzip it and run
  `powershell -ExecutionPolicy Bypass -File tools\make_skate_pack.ps1 -Version <version>` (git, Rust with MSVC, LLVM, CMake and Python 3.13).
