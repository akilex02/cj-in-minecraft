#!/usr/bin/env python3
"""Builds the package of the CJ mod: the mod compiled with the generic item icons (the community's are not ours to ship), the bridge, the setup (cj_setup.py
and the converter), play.bat/play.sh and INSTALL.md, zipped into build/release/cj-mod-<version>.zip; then checks that the zip holds nothing of GTA, Skate 3 or
the community's icons. The user's own icons are put back in the source tree afterwards. Usage: make_release.py <version>"""
import io
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import install_item_icons  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
FORBIDDEN_EXTENSIONS = (".img", ".dff", ".txd", ".xex", ".glb", ".ifp", ".col")
FORBIDDEN_FOLDERS = ("weapon-icons/", "GTA San Andreas/", "skate-spike-assets/")
ICON_FOLDER = "assets/cj_fabric/textures/item/gta/"
SETUP_FILES = ("cj_setup.py", "convert_assets.py", "install_item_icons.py", "make_platform_script.py", "make_skate_rig.py", "skate_convert.py")


def problems(zip_path, generic_icons):
    """What must not be in the package: game files, the user's folders, and any icon of the mod's items that is not a generic one (inside the jar too)."""
    found = []

    def check(names_and_data, where):
        for name, read in names_and_data:
            low = name.lower()
            if low.endswith(FORBIDDEN_EXTENSIONS):
                found.append(f"{where}{name}: un archivo de juego")
            if any(folder.lower() in low for folder in FORBIDDEN_FOLDERS):
                found.append(f"{where}{name}: una carpeta del usuario")
            if ICON_FOLDER in name and name.endswith(".png") and read() not in generic_icons:
                found.append(f"{where}{name}: un ícono que no es el genérico")
            if name.endswith(".jar"):
                with zipfile.ZipFile(io.BytesIO(read())) as inner:
                    check([(n, (lambda n=n: inner.read(n))) for n in inner.namelist()], f"{where}{name}!")

    with zipfile.ZipFile(zip_path) as z:
        check([(n, (lambda n=n: z.read(n))) for n in z.namelist()], "")
    return found


def generic_icons():
    return [(install_item_icons.TEXTURES / name).read_bytes() for name in ("weapon_icon.png", "jetpack_icon.png", "skateboard.png", "ammo1_icon.png", "ammo2_icon.png", "ammo3_icon.png")]


def build_mod():
    """The mod with the generic icons in it; the user's icons go back afterwards (also when the build fails)."""
    with tempfile.TemporaryDirectory() as empty:
        install_item_icons.install(Path(empty), install_item_icons.TEXTURES / "gta", install_item_icons.TEXTURES)
    try:
        subprocess.run(["./gradlew", "build", "--offline", "--no-daemon", "-q"], cwd=ROOT / "cj-fabric", check=True)
    finally:
        install_item_icons.install(ROOT / "weapon-icons", install_item_icons.TEXTURES / "gta", install_item_icons.TEXTURES)
    jars = [j for j in (ROOT / "cj-fabric" / "build" / "libs").glob("cj-fabric-*.jar") if "sources" not in j.name]
    return max(jars, key=lambda j: j.stat().st_mtime)


def build_bridge():
    build = ROOT / "build" / "bridge"
    subprocess.run(["cmake", "-S", str(ROOT / "gta-bridge"), "-B", str(build), "-G", "Ninja", "-DCMAKE_BUILD_TYPE=Release",
                    f"-DCMAKE_TOOLCHAIN_FILE={ROOT / 'gta-bridge' / 'cmake' / 'mingw-i686.cmake'}"], check=True)
    subprocess.run(["cmake", "--build", str(build)], check=True)
    return build / "cj_bridge.asi"


def main(argv):
    if len(argv) != 2:
        print(__doc__)
        return 2
    version = argv[1]
    out = ROOT / "build" / "release" / f"cj-mod-{version}"
    if out.exists():
        shutil.rmtree(out)
    (out / "setup").mkdir(parents=True)
    jar = build_mod()
    shutil.copyfile(jar, out / f"cj-fabric-{version}.jar")
    shutil.copyfile(build_bridge(), out / "cj_bridge.asi")
    for name in SETUP_FILES:
        shutil.copyfile(ROOT / "tools" / name, out / "setup" / name)
    shutil.copytree(ROOT / "tools" / "cjconvert", out / "setup" / "cjconvert", ignore=shutil.ignore_patterns("__pycache__"))
    for name in ("play.bat", "play.sh", "INSTALL.md"):
        shutil.copyfile(ROOT / "tools" / "release" / name, out / name)
    (out / "play.sh").chmod(0o755)
    zip_path = shutil.make_archive(str(out), "zip", out.parent, out.name)
    found = problems(Path(zip_path), generic_icons())
    if found:
        Path(zip_path).unlink()
        print("el paquete tenía archivos que no se pueden publicar; no se hizo:")
        for line in found:
            print(f"  {line}")
        return 1
    print(f"paquete: {zip_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
