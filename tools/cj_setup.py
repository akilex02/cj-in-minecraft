#!/usr/bin/env python3
"""Installs the CJ mod with the user's own GTA San Andreas: checks GTA (version 1.0 US and an ASI loader), copies the bridge, converts CJ, the weapons and
the car from the user's files into Minecraft's config folder, and optionally puts the mod in Minecraft's mods folder. Standard library only (Python 3.10+).
Commands: install, dress, icons, play (see `cj_setup.py <command> --help`)."""
import argparse
import importlib.util
import json
import os
import platform
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import convert_assets  # noqa: E402 (next to this file: in tools/, or in the package's setup/)
import make_platform_script  # noqa: E402
import make_skate_rig  # noqa: E402
import install_item_icons  # noqa: E402

GTA_10_US_SIZE = 14383616  # bytes of gta_sa.exe 1.0 US: the bridge's addresses are those of this executable
ASI_LOADERS = ("vorbisHooked.dll", "dinput8.dll", "winmm.dll")  # Silent's (the Essentials Pack moves the original vorbisFile.dll there), Ultimate ASI Loader


class SetupError(Exception):
    """Something the user has to fix; the message says what and how."""


def asi_loader(folder):
    """The file that shows an ASI loader in the GTA folder, or None."""
    for name in ASI_LOADERS:
        if (Path(folder) / name).is_file():
            return name
    return None


def check_gta(folder, force=False):
    """Warnings about the GTA folder; SetupError when the mod cannot work with it."""
    folder = Path(folder)
    exe = folder / "gta_sa.exe"
    if not exe.is_file():
        raise SetupError(f"gta_sa.exe is not in {folder}: give the GTA San Andreas folder (the one with gta_sa.exe)")
    warnings = []
    size = exe.stat().st_size
    if size != GTA_10_US_SIZE:
        message = (f"gta_sa.exe is {size} bytes, not version 1.0 US ({GTA_10_US_SIZE} bytes), the only one the bridge works with. "
                   "Downgrade GTA San Andreas to 1.0 US with a downgrader")
        if not force:
            raise SetupError(message + " (or --force to go on anyway, at your own risk)")
        warnings.append(message)
    if asi_loader(folder) is None:
        raise SetupError("there is no ASI loader in the GTA folder: install the Essentials Pack, Silent's ASI Loader or Ultimate ASI Loader")
    return warnings


def has_file(folder, name):
    """A file of the GTA folder, whatever the case of its name (Windows does not care, Linux does)."""
    return any(p.name.lower() == name.lower() for p in Path(folder).iterdir())


def install_platform(gta):
    """The CLEO script that builds the floor the car is made on (the bridge makes no car without it). With modloader it goes in its own folder of
    modloader, else in CLEO's. None without CLEO: then there is no car."""
    gta = Path(gta)
    if not has_file(gta, "CLEO.asi"):
        return None
    if has_file(gta, "modloader.asi"):
        target = gta / "modloader" / "CjPlatform" / "cleo" / "cj_platform.cs"
    else:
        target = gta / "CLEO" / "cj_platform.cs"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(make_platform_script.build())
    return target


def default_minecraft_dir(system, environ, home):
    """Where the official launcher keeps Minecraft on this system."""
    if system == "Windows":
        return Path(environ.get("APPDATA", str(Path(home) / "AppData" / "Roaming"))) / ".minecraft"
    return Path(home) / ".minecraft"


def install_jar(jar, mods):
    """Puts the mod's jar in the mods folder, taking out any other version of it; True when Fabric API is there too."""
    mods = Path(mods)
    mods.mkdir(parents=True, exist_ok=True)
    for old in mods.glob("cj-fabric-*.jar"):
        old.unlink()
    shutil.copyfile(jar, mods / Path(jar).name)
    return any(mods.glob("fabric-api-*.jar"))


PACK_FORMAT = 34  # resource packs of Minecraft 1.21.1
CONFIG = HERE / "cj_setup.json"  # the folders install was given, for dress and play
BRIDGE = HERE.parent / "cj_bridge.asi"  # in the package the bridge sits next to the setup folder


def models_dir(minecraft):
    return Path(minecraft) / "config" / "cj_fabric"


def save_config(path, gta, minecraft):
    saved = json.loads(Path(path).read_text()) if Path(path).is_file() else {}
    saved.update({"gta": str(gta), "minecraft": str(minecraft)})
    Path(path).write_text(json.dumps(saved))


def load_config(path):
    if not Path(path).is_file():
        raise SetupError("install first: python cj_setup.py install --gta <GTA folder>")
    return json.loads(Path(path).read_text())


def make_icon_pack(src, minecraft):
    """A resource pack with the weapon icons the user has (the community's: they are not shipped), as the mod's items' textures. Returns the ids copied."""
    pack = Path(minecraft) / "resourcepacks" / "cj-icons"
    textures = pack / "assets" / "cj_fabric" / "textures" / "item" / "gta"
    textures.mkdir(parents=True, exist_ok=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": PACK_FORMAT, "description": "GTA weapon icons for the CJ mod"}}))
    copied = []
    for item_id, name in install_item_icons.ICONS.items():
        source = Path(src) / name
        if install_item_icons.is_png(source):
            shutil.copyfile(source, textures / f"{item_id}.png")
            copied.append(item_id)
    return copied


def play_command(system, gta):
    """The command that opens GTA: the executable on Windows, Wine elsewhere (run from the GTA folder)."""
    if system == "Windows":
        return [str(Path(gta) / "gta_sa.exe")]
    return ["wine", "gta_sa.exe"]


def minecraft_folder(given, system, config):
    """The Minecraft folder of a command that needs no GTA: the one given, else the one install saved, else the launcher's default."""
    saved = load_config(config) if not given and Path(config).is_file() else {}
    minecraft = Path(given or saved.get("minecraft") or default_minecraft_dir(system, os.environ, Path.home())).resolve()
    if not minecraft.is_dir():
        raise SetupError(f"the Minecraft folder {minecraft} does not exist: open Minecraft once or give --minecraft <folder>")
    return minecraft


def folders(args, system, config):
    """The GTA and Minecraft folders of a command: the ones given, else the ones install saved, else the default Minecraft one."""
    saved = {}
    if (not args.gta or not args.minecraft) and Path(config).is_file():
        saved = load_config(config)
    gta = Path(args.gta or saved.get("gta") or "").resolve()  # saved for play, which runs from elsewhere
    if not args.gta and not saved.get("gta"):
        raise SetupError("missing --gta <GTA San Andreas folder>")
    minecraft = Path(args.minecraft or saved.get("minecraft") or default_minecraft_dir(system, os.environ, Path.home())).resolve()
    if not minecraft.is_dir():
        raise SetupError(f"the Minecraft folder {minecraft} does not exist: open Minecraft once or give --minecraft <folder>")
    return gta, minecraft


ENGINE_HOST = "127.0.0.1:34600"


def find_skater_glb(motor):
    """The skater the engine converted (the newest when there are several conversions)."""
    found = sorted(Path(motor).glob("**/assets/private/skater.glb"), key=lambda p: p.stat().st_mtime)
    if not found:
        raise SetupError(f"there are no converted Skate 3 files in {motor}: open skate3rust once and finish its setup (Windows), "
                         "or use --skate3 <folder with default.xex> (Linux)")
    return found[-1]


def assets_of(skater_glb):
    return Path(skater_glb).parent.parent


def engine_executable(motor, system):
    return Path(motor) / ("skate3rust.exe" if system == "Windows" else "skate3rust")


def convert_command(python, pack, skate3, system):
    motor = Path(pack) / "motor"
    return [python, str(HERE / "skate_convert.py"), "--motor", str(motor), "--xex", str(Path(skate3) / "default.xex"),
            "--game-exe", str(engine_executable(motor, system))]


def engine_command(executable, assets):
    return [str(executable), "--assets", str(assets), "--test-world", "--host", ENGINE_HOST, "--mute"]


def check_python_modules(names):
    return [n for n in names if importlib.util.find_spec(n) is None]


def write_skate_files(skater_glb, out):
    """The skater's rest and Skate's own board, from the user's converted skater, for the mod (as tools/make_skate_rig.py does)."""
    js, binary = make_skate_rig.read_glb(skater_glb)
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    (out / "skate_rig.json").write_text(json.dumps(make_skate_rig.rig(js)))
    board, images = make_skate_rig.board(js, binary)
    (out / "skate_board.json").write_text(json.dumps(board))
    for name, data in images.items():
        (out / name).write_bytes(data)


def main(argv=None, convert=convert_assets.main, system=None, bridge=BRIDGE, config=CONFIG, run=lambda cmd: subprocess.call(cmd),
         write_skate=None, modules=check_python_modules):
    write_skate = write_skate or write_skate_files
    system = system or platform.system()
    ap = argparse.ArgumentParser(prog="cj_setup.py", description=__doc__)
    sub = ap.add_subparsers(dest="command", required=True)
    for name, text in (("install", "checks GTA, copies the bridge and converts the models"), ("dress", "dresses CJ as in the save loaded in GTA")):
        p = sub.add_parser(name, help=text)
        p.add_argument("--gta", help="the GTA San Andreas folder (the one with gta_sa.exe)")
        p.add_argument("--minecraft", help="the Minecraft folder or the instance folder (default: the official launcher's)")
        if name == "install":
            p.add_argument("--mods", action="store_true", help="also copy the mod into Minecraft's mods folder")
            p.add_argument("--force", action="store_true", help="go on even if gta_sa.exe is not 1.0 US")
    p = sub.add_parser("icons", help="makes the resource pack with your weapon icons")
    p.add_argument("folder", help="the folder with the icon PNGs (Pistol.png, AK47.png...)")
    p.add_argument("--minecraft", help="the Minecraft folder or the instance folder")
    p.add_argument("--gta", help=argparse.SUPPRESS)
    sub.add_parser("play", help="opens GTA San Andreas (then open Minecraft with the Fabric profile)")
    p = sub.add_parser("skate", help="sets up skating with your Skate 3 (needs the skate pack)")
    p.add_argument("--engine", required=True, help="the skate pack folder (the one with motor/)")
    p.add_argument("--skate3", help="the Skate 3 folder with default.xex (Linux: to convert your files)")
    p.add_argument("--minecraft", help="the Minecraft folder or the instance folder")
    sub.add_parser("play-skate", help="opens the Skate engine in host mode (after GTA, before Minecraft)")
    args = ap.parse_args(argv)
    try:
        if args.command == "play":
            saved = load_config(config)
            command = play_command(system, Path(saved["gta"]))
            print("opening GTA San Andreas... load your save, then open Minecraft with the Fabric profile")
            subprocess.Popen(command, cwd=saved["gta"])
            return 0
        if args.command == "play-skate":
            saved = load_config(config)
            if "engine" not in saved:
                raise SetupError("set up skating first: python cj_setup.py skate --engine <skate pack folder>")
            print("opening the Skate engine... leave it open and go into Minecraft")
            subprocess.Popen(engine_command(Path(saved["engine"]), Path(saved["engine_assets"])), cwd=str(Path(saved["engine"]).parent))
            return 0
        if args.command == "skate":
            pack = Path(args.engine).resolve()  # saved for skate.sh, which runs from elsewhere
            motor = pack / "motor"
            if not motor.is_dir():
                raise SetupError(f"there is no motor folder in {pack}: give the unzipped skate pack folder")
            converted = any(motor.glob("**/assets/private/skater.glb"))
            if system != "Windows" and (args.skate3 or not converted):  # once converted, --skate3 is only needed to convert again
                if not args.skate3:
                    raise SetupError("on Linux also give --skate3 <Skate 3 folder with default.xex>")
                missing = modules(["numpy", "PIL"])
                if missing:
                    raise SetupError("numpy and Pillow are needed to convert Skate 3: pip install numpy pillow")
                print("converting your Skate 3 files (the first time takes a while)...")
                if run(convert_command(sys.executable, pack, Path(args.skate3), system)) != 0:
                    raise SetupError("the Skate 3 conversion failed (see the messages above)")
            glb = find_skater_glb(motor)
            minecraft = minecraft_folder(args.minecraft, system, config)
            write_skate(glb, models_dir(minecraft))
            saved = load_config(config) if Path(config).is_file() else {}
            saved.update({"minecraft": saved.get("minecraft", str(minecraft)), "engine": str(engine_executable(motor, system)),
                          "engine_assets": str(assets_of(glb))})
            Path(config).write_text(json.dumps(saved))
            print("skate ready. To play: GTA, then skate.bat / skate.sh, then Minecraft")
            return 0
        if args.command == "icons":
            minecraft = minecraft_folder(args.minecraft, system, config)
            copied = make_icon_pack(Path(args.folder), minecraft)
            print(f"resource pack cj-icons with {len(copied)} icons: turn it on in Options > Resource Packs")
            return 0
        gta, minecraft = folders(args, system, config)
        if args.command == "dress":
            outfit = gta / "cj_outfit.json"
            if not outfit.is_file():
                raise SetupError("cj_outfit.json is missing: open GTA, load your save (the bridge writes it) and run this again")
            return convert(["--gta", str(gta), "--out", str(models_dir(minecraft)), "--outfit", str(outfit)])
        for warning in check_gta(gta, force=args.force):
            print(f"aviso: {warning}")
        try:
            shutil.copyfile(bridge, gta / "cj_bridge.asi")
        except PermissionError:
            raise SetupError("cj_bridge.asi could not be copied: close GTA and run this again")
        print(f"bridge copied to {gta / 'cj_bridge.asi'}")
        platform_script = install_platform(gta)
        if platform_script is None:
            print("warning: there is no CLEO in the GTA folder: without CLEO there is no car (G key). Install CLEO 4 or 5 and run install again")
        else:
            print(f"floor of the car: {platform_script}")
        code = convert(["--gta", str(gta), "--out", str(models_dir(minecraft)), "--weapons", "--vehicles"])
        if code != 0:
            return code
        save_config(config, gta, minecraft)
        if args.mods:
            jars = sorted(HERE.parent.glob("cj-fabric-*.jar"))
            if not jars:
                raise SetupError("cj-fabric-*.jar is not next to the setup folder")
            if not install_jar(jars[-1], minecraft / "mods"):
                print("warning: Fabric API is missing from the mods folder: download it for Minecraft 1.21.1")
            print(f"mod copied to {minecraft / 'mods'}")
        print("done. To play: open GTA (python cj_setup.py play), load your save, then Minecraft with the Fabric profile")
        return 0
    except SetupError as e:
        print(f"error: {e}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
