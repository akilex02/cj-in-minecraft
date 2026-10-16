# Skating with CJ (optional)

> The Windows pack of v0.2.0 carries this guide in Spanish (`LEEME-skate.md`); this is the English version.

This pack adds **skating** to the CJ mod for Minecraft: CJ skates with the physics, tricks and board of **Skate 3**, with a gamepad. It uses the
Skate 3 Rust engine (see `SOURCE.md`) and **your own copy of Skate 3**: nothing of the game is included.

## What you need

- The **CJ mod already installed** (the `cj-mod` package, with its `INSTALL.md`).
- **Skate 3 for Xbox 360**: `default.xex` with its `data` folder beside it (the extracted game), or the disc's ISO (Windows only).
- **A gamepad** (Xbox, PlayStation or another compatible one).
- On **Linux**: `numpy` and `Pillow` for Python (`pip install numpy pillow`).

## Where to put the folders

Unzip the mod's package and this pack **side by side** (in the same folder): `cj-mod-<version>` and `cj-skate-<version>-<system>`. That way
`skate.bat` / `skate.sh` find the mod's installer.

## Set up (Windows)

1. Open `cj-skate-...\motor\skate3rust.exe` **once**: its setup asks for your `default.xex` (or the ISO) and converts your files. The first time
   takes a while. When it is done, close it.
2. In a terminal opened in the folder that holds both:
   ```
   python cj-mod-<version>\setup\cj_setup.py skate --engine cj-skate-<version>-windows
   ```
   It ends with **"skate ready"**.

## Set up (Linux)

```
pip install numpy pillow
python3 cj-mod-<version>/setup/cj_setup.py skate --engine cj-skate-<version>-linux --skate3 "/path/to/Skate 3"
```

The `--skate3` folder is the one with `default.xex`. The first conversion takes a while (several minutes and a few GB). The audio step is skipped:
the engine runs muted. It ends with **"skate ready"**. Later runs of `skate` without `--skate3` reuse the conversion.

## Play

1. **GTA** (with the mod's `play.bat` / `./play.sh`) and load your save.
2. **The Skate engine**: `skate.bat` / `./skate.sh` in the pack's folder. Its window opens: leave it open.
3. **Minecraft** with Fabric. With the **skateboard** in your hand (it is in the tools tab and has a recipe), **right click** or **L3 + R3** on the
   gamepad to get on and off. **Triangle** steps off and lets you walk carrying the board, and again gets you back on. The camera moves behind on
   its own; in first person, getting on switches to third person and getting off brings first person back.

## Troubleshooting

- **"Skate: the Skate engine is not running"**: open `skate.bat` / `skate.sh` before getting on the board.
- **"there are no converted Skate 3 files"**: on Windows, open `motor\skate3rust.exe` and finish its setup; on Linux, use `--skate3`.
- **"numpy and Pillow are needed"**: `pip install numpy pillow`.
- **"cannot find cj_setup.py"**: put the skate pack next to the `cj-mod` folder, or give its path with the `CJ_SETUP` variable.
- **The firewall asks about skate3rust**: allow the local network; the engine and Minecraft talk on your own computer (127.0.0.1).
