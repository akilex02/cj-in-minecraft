# Mod base distribuible — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un paquete (zip) con el mod, el puente y un instalador en Python que deja el mod funcionando en Windows o Linux con los archivos de GTA del usuario.

**Architecture:** `tools/cj_setup.py` (biblioteca estándar) con funciones puras probadas (versión de GTA, cargador de ASI, carpeta de Minecraft, copia del `.jar`, *resource pack* de íconos, configuración guardada) y cuatro comandos (`install`, `dress`, `icons`, `play`) que reutilizan `convert_assets.main` e `install_item_icons.ICONS`. `tools/make_release.py` compila el mod con íconos genéricos, compila el puente, arma `build/release/cj-mod-<versión>/` con el instalador, `jugar.*` y `LEEME.md`, hace el zip y lo revisa (sin archivos de GTA, Skate 3 ni íconos de la comunidad).

**Tech Stack:** Python 3.10+ (solo biblioteca estándar), unittest; Gradle (Fabric 1.21.1) y CMake/mingw ya existentes.

**Spec:** `docs/superpowers/specs/2026-10-12-distribution-base-design.md`

## Global Constraints

- Solo biblioteca estándar de Python, compatible con 3.10.
- `gta_sa.exe` 1.0 US: 14 383 616 bytes; `--force` salta la comprobación.
- Cargadores de ASI reconocidos: `vorbisHooked.dll`, `dinput8.dll`, `winmm.dll`.
- Minecraft por defecto: `%APPDATA%\.minecraft` (Windows), `~/.minecraft` (Linux).
- Modelos en `<minecraft>/config/cj_fabric/` (`cj.glb`, `weapons/`, `vehicles/`).
- *Resource pack*: `<minecraft>/resourcepacks/cj-icons/`, `pack_format` 34, `assets/cj_fabric/textures/item/gta/<id>.png`.
- Configuración guardada: `cj_setup.json` junto a `cj_setup.py`.
- El zip no puede contener `.img`, `.dff`, `.txd`, `.xex`, `.glb`, nada de `weapon-icons/` ni `GTA San Andreas/`, ni PNG de `textures/item/gta/` distintos de los genéricos (también dentro del `.jar`).
- Mensajes al usuario en español; comentarios del código en inglés, en el estilo de `tools/`.
- Tests: `cd tools && python3 -B -m unittest discover -s tests`.

## Review Focus

- Una carpeta de GTA con espacios y acentos (`C:\Juegos\GTA San Andreas`, `<GTA folder>`): todo debe funcionar (Tarea 1: tests con rutas con espacios).
- `--minecraft` apuntando a una instancia de Prism (`.../instances/X/minecraft`): se usa tal cual, sin buscar `.minecraft` (Tarea 1: test).
- `install` repetido: debe reemplazar el `.jar` anterior y los modelos sin dejar dos versiones del mod (Tarea 1: test de la copia del `.jar` con una versión vieja).
- Un PNG que no es PNG en la carpeta de íconos: se ignora, no rompe el *resource pack* (Tarea 2: test).
- `cj_setup.py play` sin haber hecho `install`: error claro, no un traceback (Tarea 2: test).

---

### Task 1: Comprobaciones del instalador

**Files:**
- Create: `tools/cj_setup.py`
- Test: `tools/tests/test_cj_setup.py`

**Interfaces:**
- Produces: `GTA_10_US_SIZE = 14383616`; `ASI_LOADERS = ("vorbisHooked.dll", "dinput8.dll", "winmm.dll")`; `class SetupError(Exception)`;
  `check_gta(folder: Path, force: bool = False) -> list[str]` (avisos; lanza `SetupError` si no se puede seguir);
  `asi_loader(folder: Path) -> str | None`; `default_minecraft_dir(system: str, environ: dict, home: Path) -> Path`;
  `install_jar(jar: Path, mods: Path) -> bool` (devuelve si hay un `fabric-api-*.jar` en `mods`).

- [ ] **Step 1: Write the failing test**

```python
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import cj_setup  # noqa: E402


def fake_gta(root, size=cj_setup.GTA_10_US_SIZE, loader="vorbisHooked.dll"):
    gta = Path(root) / "Juegos" / "GTA San Andreas (1.0 US)"
    gta.mkdir(parents=True)
    with open(gta / "gta_sa.exe", "wb") as f:
        f.truncate(size)
    if loader:
        (gta / loader).write_bytes(b"x")
    return gta


class ChecksTest(unittest.TestCase):
    def test_a_10_us_exe_with_a_loader_passes(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(cj_setup.check_gta(fake_gta(d)), [])

    def test_a_folder_without_the_exe_is_refused(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(cj_setup.SetupError) as e:
                cj_setup.check_gta(Path(d))
            self.assertIn("gta_sa.exe", str(e.exception))

    def test_another_version_is_refused_unless_forced(self):
        with tempfile.TemporaryDirectory() as d:
            gta = fake_gta(d, size=14405632)
            with self.assertRaises(cj_setup.SetupError) as e:
                cj_setup.check_gta(gta)
            self.assertIn("1.0 US", str(e.exception))
            self.assertEqual(len(cj_setup.check_gta(gta, force=True)), 1)  # a warning, not an error

    def test_no_asi_loader_is_refused(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(cj_setup.SetupError) as e:
                cj_setup.check_gta(fake_gta(d, loader=None))
            self.assertIn("ASI", str(e.exception))

    def test_each_known_loader_is_recognised(self):
        for name in cj_setup.ASI_LOADERS:
            with tempfile.TemporaryDirectory() as d:
                self.assertEqual(cj_setup.asi_loader(fake_gta(d, loader=name)), name)

    def test_the_default_minecraft_folder_of_each_system(self):
        self.assertEqual(cj_setup.default_minecraft_dir("Windows", {"APPDATA": r"C:\Users\Ana\AppData\Roaming"}, Path("/x")),
                         Path(r"C:\Users\Ana\AppData\Roaming") / ".minecraft")
        self.assertEqual(cj_setup.default_minecraft_dir("Linux", {}, Path("/home/ana")), Path("/home/ana/.minecraft"))

    def test_the_jar_replaces_an_older_one_and_fabric_api_is_looked_for(self):
        with tempfile.TemporaryDirectory() as d:
            mods = Path(d) / "instances" / "Mi instancia" / "minecraft" / "mods"
            mods.mkdir(parents=True)
            (mods / "cj-fabric-0.0.9.jar").write_bytes(b"old")
            jar = Path(d) / "cj-fabric-0.1.0.jar"
            jar.write_bytes(b"new")
            self.assertFalse(cj_setup.install_jar(jar, mods))
            self.assertEqual(sorted(p.name for p in mods.iterdir()), ["cj-fabric-0.1.0.jar"])
            (mods / "fabric-api-0.116.17+1.21.1.jar").write_bytes(b"api")
            self.assertTrue(cj_setup.install_jar(jar, mods))


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd tools && python3 -B -m unittest tests.test_cj_setup`
Expected: FAIL (`ModuleNotFoundError: No module named 'cj_setup'`)

- [ ] **Step 3: Write minimal implementation**

```python
#!/usr/bin/env python3
"""Installs the CJ mod with the user's own GTA San Andreas: checks GTA (version 1.0 US and an ASI loader), copies the bridge, converts CJ, the weapons and
the car from the user's files into Minecraft's config folder, and optionally puts the mod in Minecraft's mods folder. Standard library only (Python 3.10+).
Commands: install, dress, icons, play (see `cj_setup.py <command> --help`)."""
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

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
        raise SetupError(f"no está gta_sa.exe en {folder}: indica la carpeta de GTA San Andreas (la que tiene gta_sa.exe)")
    warnings = []
    size = exe.stat().st_size
    if size != GTA_10_US_SIZE:
        message = (f"gta_sa.exe mide {size} bytes y no es la versión 1.0 US ({GTA_10_US_SIZE} bytes), la única con la que funciona el puente. "
                   "Usa un downgrader de GTA San Andreas a la 1.0 US")
        if not force:
            raise SetupError(message + " (o --force para seguir igual, bajo tu responsabilidad)")
        warnings.append(message)
    if asi_loader(folder) is None:
        raise SetupError("no hay un cargador de ASI en la carpeta de GTA: instala el Essentials Pack, Silent's ASI Loader o Ultimate ASI Loader")
    return warnings


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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd tools && python3 -B -m unittest tests.test_cj_setup`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add tools/cj_setup.py tools/tests/test_cj_setup.py
git commit -m "Setup: the checks of GTA and Minecraft, and the copy of the mod"
```

---

### Task 2: Los comandos `install`, `dress`, `icons` y `play`

**Files:**
- Modify: `tools/cj_setup.py`
- Test: `tools/tests/test_cj_setup.py` (new test class)

**Interfaces:**
- Consumes: Task 1 (`check_gta`, `default_minecraft_dir`, `install_jar`, `SetupError`, `HERE`); `convert_assets.main(argv) -> int`; `install_item_icons.ICONS` (id → file name) and `install_item_icons.is_png(path) -> bool`.
- Produces: `PACK_FORMAT = 34`; `make_icon_pack(src: Path, minecraft: Path) -> list[str]` (ids copied); `save_config(path: Path, gta: Path, minecraft: Path)`; `load_config(path: Path) -> dict` (`SetupError` if missing); `play_command(system: str, gta: Path) -> list[str]`; `main(argv, convert=convert_assets.main, system=platform.system()) -> int`.

- [ ] **Step 1: Write the failing test**

Add to `tools/tests/test_cj_setup.py`:

```python
import json


class CommandsTest(unittest.TestCase):
    def test_install_checks_copies_the_bridge_converts_and_remembers_the_folders(self):
        with tempfile.TemporaryDirectory() as d:
            gta = fake_gta(d)
            minecraft = Path(d) / "mc"
            minecraft.mkdir()
            bridge = Path(d) / "cj_bridge.asi"
            bridge.write_bytes(b"asi")
            calls = []
            config = Path(d) / "cj_setup.json"
            code = cj_setup.main(["install", "--gta", str(gta), "--minecraft", str(minecraft)], convert=lambda argv: calls.append(argv) or 0,
                                 bridge=bridge, config=config)
            self.assertEqual(code, 0)
            self.assertEqual((gta / "cj_bridge.asi").read_bytes(), b"asi")
            self.assertEqual(calls, [["--gta", str(gta), "--out", str(minecraft / "config" / "cj_fabric"), "--weapons", "--vehicles"]])
            self.assertEqual(json.loads(config.read_text()), {"gta": str(gta), "minecraft": str(minecraft)})

    def test_install_stops_on_a_bad_gta_folder_with_the_reason(self):
        with tempfile.TemporaryDirectory() as d:
            minecraft = Path(d) / "mc"
            minecraft.mkdir()
            code = cj_setup.main(["install", "--gta", d, "--minecraft", str(minecraft)], convert=lambda argv: 0,
                                 bridge=Path(d) / "x.asi", config=Path(d) / "c.json")
            self.assertEqual(code, 1)

    def test_dress_needs_the_outfit_the_bridge_writes(self):
        with tempfile.TemporaryDirectory() as d:
            gta = fake_gta(d)
            minecraft = Path(d) / "mc"
            minecraft.mkdir()
            calls = []
            args = ["dress", "--gta", str(gta), "--minecraft", str(minecraft)]
            self.assertEqual(cj_setup.main(args, convert=lambda argv: calls.append(argv) or 0, config=Path(d) / "c.json"), 1)
            (gta / "cj_outfit.json").write_text("{}")
            self.assertEqual(cj_setup.main(args, convert=lambda argv: calls.append(argv) or 0, config=Path(d) / "c.json"), 0)
            self.assertEqual(calls, [["--gta", str(gta), "--out", str(minecraft / "config" / "cj_fabric"), "--outfit", str(gta / "cj_outfit.json")]])

    def test_the_icon_pack_takes_the_pngs_there_are_and_skips_the_rest(self):
        with tempfile.TemporaryDirectory() as d:
            src = Path(d) / "iconos"
            src.mkdir()
            (src / "AK47.png").write_bytes(b"\x89PNG\r\n\x1a\n ak")
            (src / "Pistol.png").write_bytes(b"not a png")
            minecraft = Path(d) / "mc"
            minecraft.mkdir()
            copied = cj_setup.make_icon_pack(src, minecraft)
            pack = minecraft / "resourcepacks" / "cj-icons"
            self.assertEqual(copied, ["ak47"])
            self.assertEqual((pack / "assets" / "cj_fabric" / "textures" / "item" / "gta" / "ak47.png").read_bytes(), b"\x89PNG\r\n\x1a\n ak")
            self.assertFalse((pack / "assets" / "cj_fabric" / "textures" / "item" / "gta" / "colt45.png").exists())
            self.assertEqual(json.loads((pack / "pack.mcmeta").read_text())["pack"]["pack_format"], cj_setup.PACK_FORMAT)

    def test_play_without_install_says_so(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(cj_setup.main(["play"], config=Path(d) / "missing.json"), 1)

    def test_play_opens_gta_natively_on_windows_and_with_wine_on_linux(self):
        gta = Path("/juegos/GTA San Andreas")
        self.assertEqual(cj_setup.play_command("Windows", gta), [str(gta / "gta_sa.exe")])
        self.assertEqual(cj_setup.play_command("Linux", gta), ["wine", "gta_sa.exe"])
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd tools && python3 -B -m unittest tests.test_cj_setup`
Expected: FAIL (`main` / `make_icon_pack` / `play_command` do not exist)

- [ ] **Step 3: Write minimal implementation**

Add to `tools/cj_setup.py`:

```python
import argparse
import json
import os
import platform
import subprocess

import convert_assets
import install_item_icons

PACK_FORMAT = 34  # resource packs of Minecraft 1.21.1
CONFIG = HERE / "cj_setup.json"  # the folders install was given, for dress and play
BRIDGE = HERE.parent / "cj_bridge.asi"  # in the package the bridge sits next to the setup folder


def models_dir(minecraft):
    return Path(minecraft) / "config" / "cj_fabric"


def save_config(path, gta, minecraft):
    Path(path).write_text(json.dumps({"gta": str(gta), "minecraft": str(minecraft)}))


def load_config(path):
    if not Path(path).is_file():
        raise SetupError("primero instala: python cj_setup.py install --gta <carpeta de GTA>")
    return json.loads(Path(path).read_text())


def make_icon_pack(src, minecraft):
    """A resource pack with the weapon icons the user has (the community's: they are not shipped), as the mod's items' textures. Returns the ids copied."""
    pack = Path(minecraft) / "resourcepacks" / "cj-icons"
    textures = pack / "assets" / "cj_fabric" / "textures" / "item" / "gta"
    textures.mkdir(parents=True, exist_ok=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": PACK_FORMAT, "description": "Íconos de las armas de GTA para el mod de CJ"}}))
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


def folders(args, system, config):
    """The GTA and Minecraft folders of a command: the ones given, else the ones install saved, else the default Minecraft one."""
    saved = {}
    if (not args.gta or not args.minecraft) and Path(config).is_file():
        saved = load_config(config)
    gta = Path(args.gta or saved.get("gta") or "")
    if not args.gta and not saved.get("gta"):
        raise SetupError("falta --gta <carpeta de GTA San Andreas>")
    minecraft = Path(args.minecraft or saved.get("minecraft") or default_minecraft_dir(system, os.environ, Path.home()))
    if not minecraft.is_dir():
        raise SetupError(f"no existe la carpeta de Minecraft {minecraft}: abre Minecraft una vez o indica --minecraft <carpeta>")
    return gta, minecraft


def main(argv=None, convert=convert_assets.main, system=None, bridge=BRIDGE, config=CONFIG):
    system = system or platform.system()
    ap = argparse.ArgumentParser(prog="cj_setup.py", description=__doc__)
    sub = ap.add_subparsers(dest="command", required=True)
    for name, text in (("install", "comprueba GTA, copia el puente y convierte los modelos"), ("dress", "viste a CJ como en la partida cargada en GTA")):
        p = sub.add_parser(name, help=text)
        p.add_argument("--gta", help="la carpeta de GTA San Andreas (la que tiene gta_sa.exe)")
        p.add_argument("--minecraft", help="la carpeta de Minecraft o de la instancia (por defecto la del launcher oficial)")
        if name == "install":
            p.add_argument("--mods", action="store_true", help="copiar también el mod a la carpeta mods de Minecraft")
            p.add_argument("--force", action="store_true", help="seguir aunque gta_sa.exe no sea la 1.0 US")
    p = sub.add_parser("icons", help="arma el paquete de recursos con tus íconos de las armas")
    p.add_argument("folder", help="la carpeta con los PNG de los íconos (Pistol.png, AK47.png...)")
    p.add_argument("--minecraft", help="la carpeta de Minecraft o de la instancia")
    p.add_argument("--gta", help=argparse.SUPPRESS)
    sub.add_parser("play", help="abre GTA San Andreas (después abre Minecraft con el perfil de Fabric)")
    args = ap.parse_args(argv)
    try:
        if args.command == "play":
            saved = load_config(config)
            command = play_command(system, Path(saved["gta"]))
            print("abriendo GTA San Andreas... carga tu partida y luego abre Minecraft con el perfil de Fabric")
            subprocess.Popen(command, cwd=saved["gta"])
            return 0
        if args.command == "icons":
            minecraft = Path(args.minecraft) if args.minecraft else folders(args, system, config)[1]
            copied = make_icon_pack(Path(args.folder), minecraft)
            print(f"paquete de recursos cj-icons con {len(copied)} íconos: actívalo en Opciones > Paquetes de recursos")
            return 0
        gta, minecraft = folders(args, system, config)
        if args.command == "dress":
            outfit = gta / "cj_outfit.json"
            if not outfit.is_file():
                raise SetupError("no está cj_outfit.json: abre GTA, carga tu partida (el puente lo escribe) y repite")
            return convert(["--gta", str(gta), "--out", str(models_dir(minecraft)), "--outfit", str(outfit)])
        for warning in check_gta(gta, force=args.force):
            print(f"aviso: {warning}")
        try:
            shutil.copyfile(bridge, gta / "cj_bridge.asi")
        except PermissionError:
            raise SetupError("no se pudo copiar cj_bridge.asi: cierra GTA y repite")
        print(f"puente copiado a {gta / 'cj_bridge.asi'}")
        code = convert(["--gta", str(gta), "--out", str(models_dir(minecraft)), "--weapons", "--vehicles"])
        if code != 0:
            return code
        save_config(config, gta, minecraft)
        if args.mods:
            jars = sorted(HERE.parent.glob("cj-fabric-*.jar"))
            if not jars:
                raise SetupError("no está cj-fabric-*.jar junto a la carpeta setup")
            if not install_jar(jars[-1], minecraft / "mods"):
                print("aviso: falta Fabric API en la carpeta mods: descárgala para Minecraft 1.21.1")
            print(f"mod copiado a {minecraft / 'mods'}")
        print("listo. Para jugar: abre GTA (python cj_setup.py play), carga tu partida, y luego Minecraft con el perfil de Fabric")
        return 0
    except SetupError as e:
        print(f"error: {e}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd tools && python3 -B -m unittest tests.test_cj_setup`
Expected: PASS (13 tests)

- [ ] **Step 5: Run the whole tools suite and commit**

Run: `cd tools && python3 -B -m unittest discover -s tests`
Expected: no new failures (tests that need the user's GTA files skip as before).

```bash
git add tools/cj_setup.py tools/tests/test_cj_setup.py
git commit -m "Setup: install, dress, icons and play"
```

---

### Task 3: El paquete, su revisión, `jugar.*` y `LEEME.md`

**Files:**
- Create: `tools/make_release.py`
- Create: `tools/release/jugar.bat`, `tools/release/jugar.sh`, `tools/release/LEEME.md`
- Test: `tools/tests/test_make_release.py`

**Interfaces:**
- Consumes: `install_item_icons.install(src, out, generic)`, `install_item_icons.ICONS`, `install_item_icons.TEXTURES`.
- Produces: `FORBIDDEN_EXTENSIONS`; `problems(zip_path: Path, generic_icons: list[bytes]) -> list[str]`; `main(argv) -> int`.

- [ ] **Step 1: Write the failing test**

```python
import io
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import make_release  # noqa: E402

GENERIC = b"\x89PNG generic"


def jar_with(entries):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        for name, data in entries.items():
            z.writestr(name, data)
    return buf.getvalue()


def release_zip(d, entries):
    path = Path(d) / "r.zip"
    with zipfile.ZipFile(path, "w") as z:
        for name, data in entries.items():
            z.writestr(name, data)
    return path


class AuditTest(unittest.TestCase):
    def test_a_clean_package_passes(self):
        with tempfile.TemporaryDirectory() as d:
            jar = jar_with({"assets/cj_fabric/textures/item/gta/ak47.png": GENERIC, "dev/cjmc/CjClient.class": b"x"})
            z = release_zip(d, {"cj-mod-0.1.0/cj-fabric-0.1.0.jar": jar, "cj-mod-0.1.0/cj_bridge.asi": b"x", "cj-mod-0.1.0/setup/cj_setup.py": b"x"})
            self.assertEqual(make_release.problems(z, [GENERIC]), [])

    def test_game_files_are_refused(self):
        with tempfile.TemporaryDirectory() as d:
            for name in ("cj-mod/models/gta3.img", "cj-mod/x.dff", "cj-mod/x.txd", "cj-mod/default.xex", "cj-mod/config/cj.glb"):
                self.assertTrue(make_release.problems(release_zip(d, {name: b"x"}), [GENERIC]), name)

    def test_a_community_icon_inside_the_jar_is_refused(self):
        with tempfile.TemporaryDirectory() as d:
            jar = jar_with({"assets/cj_fabric/textures/item/gta/ak47.png": b"\x89PNG the community's"})
            z = release_zip(d, {"cj-mod/cj-fabric-0.1.0.jar": jar})
            self.assertEqual(len(make_release.problems(z, [GENERIC])), 1)

    def test_the_users_folders_are_refused(self):
        with tempfile.TemporaryDirectory() as d:
            for name in ("cj-mod/weapon-icons/AK47.png", "cj-mod/GTA San Andreas/gta_sa.exe"):
                self.assertTrue(make_release.problems(release_zip(d, {name: b"x"}), [GENERIC]), name)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd tools && python3 -B -m unittest tests.test_make_release`
Expected: FAIL (`No module named 'make_release'`)

- [ ] **Step 3: Write the implementation**

`tools/make_release.py`:

```python
#!/usr/bin/env python3
"""Builds the package of the CJ mod: the mod compiled with the generic item icons (the community's are not ours to ship), the bridge, the setup (cj_setup.py
and the converter), jugar.bat/jugar.sh and LEEME.md, zipped into build/release/cj-mod-<version>.zip; then checks that the zip holds nothing of GTA, Skate 3 or
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
SETUP_FILES = ("cj_setup.py", "convert_assets.py", "install_item_icons.py")


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
    return [(install_item_icons.TEXTURES / name).read_bytes() for name in ("weapon_icon.png", "jetpack_icon.png", "skateboard.png")]


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
    for name in ("jugar.bat", "jugar.sh", "LEEME.md"):
        shutil.copyfile(ROOT / "tools" / "release" / name, out / name)
    (out / "jugar.sh").chmod(0o755)
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
```

`tools/release/jugar.bat`:

```bat
@echo off
rem Abre GTA San Andreas con el puente; luego abre Minecraft con el perfil de Fabric.
python "%~dp0setup\cj_setup.py" play
pause
```

`tools/release/jugar.sh`:

```bash
#!/usr/bin/env bash
# Abre GTA San Andreas (con Wine) con el puente; luego abre Minecraft con el perfil de Fabric.
exec python3 "$(dirname "$0")/setup/cj_setup.py" play
```

`tools/release/LEEME.md`: the guide, in Spanish, with these sections and content:

- **Qué es**: CJ de GTA San Andreas dentro de Minecraft; GTA corre al lado y mueve a CJ; no se incluye nada de GTA: se usan los archivos del usuario.
- **Qué necesitas**: the list of the spec's section 2, with links: Fabric (https://fabricmc.net/use/installer/), Fabric API (https://modrinth.com/mod/fabric-api, version for 1.21.1), Python (https://www.python.org/downloads/, "Add python.exe to PATH" on Windows), Silent's ASI Loader / Essentials Pack, a downgrader to 1.0 US.
- **Instalar (Windows)**: unzip; `python setup\cj_setup.py install --gta "C:\ruta\a\GTA San Andreas" --mods` in a terminal opened in the folder (or `--minecraft` for a launcher instance); what it prints when it works.
- **Instalar (Linux)**: the same with `python3 setup/cj_setup.py install --gta "/ruta/a/GTA San Andreas" --mods`; GTA runs with Wine.
- **Jugar**: `jugar.bat` / `./jugar.sh` opens GTA; load the save; open Minecraft with the Fabric profile; enter a world; `/give @s cj_fabric:colt45` etc. (the item list of `start_games.sh`).
- **Vestir a CJ como en tu partida**: load the save in GTA, then `cj_setup.py dress`.
- **Íconos de las armas**: `cj_setup.py icons <carpeta>` with the expected file names (the `ICONS` table), and activating `cj-icons` in Opciones > Paquetes de recursos.
- **Problemas comunes**: "no es la versión 1.0 US"; "no hay un cargador de ASI"; CJ is not drawn (GTA not open or no save loaded: the vanilla player shows); the firewall asking about Java or GTA (allow local network: they talk over 127.0.0.1).

- [ ] **Step 4: Run test to verify it passes**

Run: `cd tools && python3 -B -m unittest tests.test_make_release`
Expected: PASS (4 tests)

- [ ] **Step 5: Build a real package and check it**

Run: `python3 tools/make_release.py 0.1.0`
Expected: `paquete: .../build/release/cj-mod-0.1.0.zip`; then `ls tools/../cj-fabric/src/main/resources/assets/cj_fabric/textures/item/gta/` still shows the user's icons (they were put back), and `unzip -l build/release/cj-mod-0.1.0.zip` lists the jar, the asi, `setup/` with `cjconvert/`, `jugar.*` and `LEEME.md`.

- [ ] **Step 6: Commit**

```bash
git add tools/make_release.py tools/release tools/tests/test_make_release.py
git commit -m "Release: the package of the mod, its audit, jugar.bat/jugar.sh and LEEME.md"
```

---

### Task 4: Prueba desde cero (con el usuario)

- [ ] **Step 1 (Linux):** en una carpeta nueva, descomprimir `cj-mod-0.1.0.zip`; `python3 setup/cj_setup.py install --gta "<GTA>" --minecraft "<una instancia nueva o .minecraft>" --mods`; `./jugar.sh`; Minecraft con Fabric; comprobar CJ, armas y coche; `dress`; `icons`.
- [ ] **Step 2 (Windows):** lo mismo en Windows con su GTA 1.0 US y el launcher oficial o Prism, siguiendo solo `LEEME.md`.
- [ ] **Step 3:** corregir lo que falle (cada fallo con su test cuando sea código), y ajustar `LEEME.md`.
