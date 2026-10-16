# Pack de skate distribuible — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Packs opcionales de skate (Windows y Linux) con el motor de Skate 3 parcheado, y los comandos `skate` / `play-skate` del instalador que preparan y abren el skate con los archivos del usuario.

**Architecture:** `tools/cj_setup.py` gana `skate` (en Linux convierte con `tools/skate_convert.py`, un envoltorio de la conversión del motor sin el paso del audio; en los dos busca `skater.glb` y escribe el esqueleto y la tabla con `make_skate_rig`) y `play-skate` (abre el motor en modo anfitrión). `tools/make_skate_pack.sh` (Linux) y `tools/make_skate_pack.ps1` (Windows) compilan el motor parcheado y arman el pack con `skate.sh`/`skate.bat`, `LEEME-skate.md`, `FUENTE.md`, el parche y las licencias; `tools/skate_pack_audit.py` revisa que el zip no traiga nada de Skate 3. El mod cambia su mensaje cuando el motor no responde y pasa a 0.2.0.

**Tech Stack:** Python 3.10+ (biblioteca estándar en nuestro código; numpy y Pillow solo para la conversión del motor en Linux), unittest, Java 21/JUnit 5, bash, PowerShell 5+, Rust (cargo) del motor.

**Spec:** `docs/superpowers/specs/2026-10-12-distribution-skate-design.md`

## Global Constraints

- Motor: `https://github.com/SK8-ENGINE/skate-3-rust-engine`, commit `2e16697797c8358d16f747a8259354ff2921041a`, parche `skate-host/skate-engine-host.patch`.
- Linux: `cargo build --release -p skate-game --bin skate3rust --no-default-features`; hasta 3 intentos limpiando `skate-game`.
- Windows: `scripts/Build-Release.ps1` del motor (Rust MSVC, LLVM, Python 3.13).
- El motor se abre con `--assets <recursos> --test-world --host 127.0.0.1:34600 --mute`.
- Conversión en Linux: `tools/setup.py --base <motor>/data --game-exe <default.xex>` del motor, sin el paso del audio; necesita numpy y Pillow.
- `skater.glb` se busca como `**/assets/private/skater.glb` bajo `<pack>/motor/`.
- Revisión del pack: falla con `.xex`, `.iso`, `.glb`, `installations/`, `data/` con recursos convertidos, o `skate-spike-assets/`.
- Mensaje del mod: "Skate: the Skate engine is not running. Skating needs the optional skate pack (see LEEME-skate.md); start it with skate.bat / skate.sh before getting on the board."
- Versión del mod y del zip base: 0.2.0.
- Tests: `cd tools && python3 -B -m unittest discover -s tests`; `cd cj-fabric && ./gradlew test --offline --no-daemon`.

## Review Focus

- `skate` en Windows antes de abrir `skate3rust.exe` (sin `skater.glb`): error que diga que abra el ejecutable y complete su asistente (Tarea 2: test).
- Dos instalaciones convertidas dentro de `motor/data` (una vieja y una nueva): se usa la más nueva (Tarea 2: test).
- `play-skate` sin haber hecho `skate`: error claro, no un traceback (Tarea 2: test).
- Rutas con espacios en la carpeta del pack y de Skate 3 (Tarea 2: tests con espacios).
- Un pack hecho a partir de una carpeta de trabajo donde alguien ya corrió el asistente (`motor/data/installations/...`): la revisión lo rechaza (Tarea 3: test).

---

### Task 1: El mensaje del mod sin el motor y la versión 0.2.0

**Files:**
- Modify: `cj-fabric/src/main/java/dev/cjmc/skate/SkateRules.java`, `cj-fabric/src/main/java/dev/cjmc/skate/SkateRide.java` (the `engineSilent` stop message), `cj-fabric/gradle.properties` (`mod_version=0.2.0`)
- Test: `cj-fabric/src/test/java/dev/cjmc/skate/SkateRulesTest.java`

**Interfaces:**
- Produces: `public static final String SkateRules.ENGINE_MISSING`.

- [ ] **Step 1: Write the failing test** (add to `SkateRulesTest`)

```java
    @Test
    void withoutTheEngineThePlayerIsToldAboutTheOptionalPack() {
        assertTrue(SkateRules.ENGINE_MISSING.contains("optional skate pack"));
        assertTrue(SkateRules.ENGINE_MISSING.contains("LEEME-skate.md"));
        assertTrue(SkateRules.ENGINE_MISSING.contains("skate.bat / skate.sh"));
    }
```

- [ ] **Step 2: Run it** — `cd cj-fabric && ./gradlew test --offline --no-daemon --tests '*SkateRulesTest*'` → FAIL (`ENGINE_MISSING` missing).

- [ ] **Step 3: Implement**

In `SkateRules`:

```java
    /** What the player is told when the engine does not answer: skating needs the optional skate pack, started before getting on the board. */
    public static final String ENGINE_MISSING = "Skate: the Skate engine is not running. Skating needs the optional skate pack (see LEEME-skate.md); "
            + "start it with skate.bat / skate.sh before getting on the board.";
```

In `SkateRide.tick`, replace `stop(player, "Skate: the engine does not answer (is it running with --host?)");` with `stop(player, SkateRules.ENGINE_MISSING);`. In `cj-fabric/gradle.properties`, `mod_version=0.2.0`.

- [ ] **Step 4: Run** — `cd cj-fabric && ./gradlew test --offline --no-daemon` → PASS.

- [ ] **Step 5: Commit** — `git add -A cj-fabric && git commit -m "Skate: without the engine the player is told about the optional skate pack; mod 0.2.0"`

---

### Task 2: `cj_setup.py skate` y `play-skate`, y la conversión de Linux sin audio

**Files:**
- Create: `tools/skate_convert.py`
- Modify: `tools/cj_setup.py`, `tools/make_release.py` (`SETUP_FILES` gains `make_skate_rig.py` and `skate_convert.py`)
- Test: `tools/tests/test_cj_setup.py` (class `SkateTest`), `tools/tests/test_skate_convert.py`

**Interfaces:**
- Consumes: `cj_setup.main`, `load_config`, `save_config`-style config (now with more keys), `minecraft_folder`, `SetupError`; `make_skate_rig.rig(js)`, `make_skate_rig.board(js, binary)`, `make_skate_rig.read_glb(path) -> (js, binary)`.
- Produces: `find_skater_glb(motor: Path) -> Path`; `assets_of(skater_glb: Path) -> Path` (the `assets` folder that holds it); `engine_executable(motor: Path, system: str) -> Path`; `convert_command(python: str, pack: Path, skate3: Path, system: str) -> list[str]`; `engine_command(executable: Path, assets: Path) -> list[str]`; `check_python_modules(names) -> list[str]` (the missing ones); `write_skate_files(skater_glb: Path, out: Path)`; `skate_convert.main(argv) -> int`; `skate_convert.without_audio(asset_exports_module)`.

- [ ] **Step 1: Write the failing tests**

`tools/tests/test_skate_convert.py`:

```python
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import skate_convert  # noqa: E402


class SkateConvertTest(unittest.TestCase):
    def test_the_engines_conversion_runs_without_its_audio_step(self):
        # a stand-in of the engine's tools/asset_pipeline: install() calls asset_exports.audio, which fails on Linux
        with tempfile.TemporaryDirectory() as d:
            motor = Path(d) / "mi pack" / "motor"
            pipeline = motor / "tools" / "asset_pipeline"
            pipeline.mkdir(parents=True)
            (motor / "tools" / "__init__.py").write_text("")
            (pipeline / "__init__.py").write_text("")
            (pipeline / "asset_exports.py").write_text("def audio(*a, **k):\n    raise RuntimeError('downloads a Windows exe')\n")
            (pipeline / "customiser_setup.py").write_text(textwrap.dedent("""
                from pathlib import Path
                def install(iso, base, game_exe, report, refresh=False):
                    from tools.asset_pipeline import asset_exports
                    asset_exports.audio()
                    Path(base).mkdir(parents=True, exist_ok=True)
                    (Path(base) / "done").write_text(str(iso) + "|" + str(game_exe))
            """))
            xex = Path(d) / "Skate 3" / "default.xex"
            xex.parent.mkdir()
            xex.write_bytes(b"x")
            code = skate_convert.main(["--motor", str(motor), "--xex", str(xex), "--game-exe", str(motor / "skate3rust")])
            self.assertEqual(code, 0)
            self.assertEqual((motor / "data" / "done").read_text(), f"{xex}|{motor / 'skate3rust'}")
```

Add to `tools/tests/test_cj_setup.py` (before `if __name__ == "__main__":`):

```python
class SkateTest(unittest.TestCase):
    def installation(self, motor, name, mtime):
        glb = motor / "data" / "installations" / name / "assets" / "private" / "skater.glb"
        glb.parent.mkdir(parents=True)
        glb.write_bytes(b"glb")
        os.utime(glb, (mtime, mtime))
        return glb

    def test_the_newest_converted_skater_is_found(self):
        with tempfile.TemporaryDirectory() as d:
            motor = Path(d) / "cj skate" / "motor"
            self.installation(motor, "old", 1000)
            new = self.installation(motor, "new", 2000)
            self.assertEqual(cj_setup.find_skater_glb(motor), new)
            self.assertEqual(cj_setup.assets_of(new), new.parent.parent)

    def test_without_a_conversion_the_user_is_told_to_run_the_engine_once(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(cj_setup.SetupError) as e:
                cj_setup.find_skater_glb(Path(d))
            self.assertIn("skate3rust", str(e.exception))

    def test_the_executable_of_each_system(self):
        motor = Path("/p/motor")
        self.assertEqual(cj_setup.engine_executable(motor, "Windows"), motor / "skate3rust.exe")
        self.assertEqual(cj_setup.engine_executable(motor, "Linux"), motor / "skate3rust")

    def test_the_linux_conversion_command(self):
        pack = Path("/juegos/cj skate")
        cmd = cj_setup.convert_command("/usr/bin/python3", pack, Path("/juegos/Skate 3"), "Linux")
        self.assertEqual(cmd, ["/usr/bin/python3", str(cj_setup.HERE / "skate_convert.py"), "--motor", str(pack / "motor"),
                               "--xex", str(Path("/juegos/Skate 3") / "default.xex"), "--game-exe", str(pack / "motor" / "skate3rust")])

    def test_the_engine_is_opened_as_a_host_without_sound(self):
        exe, assets = Path("/p/motor/skate3rust"), Path("/p/motor/data/installations/a/assets")
        self.assertEqual(cj_setup.engine_command(exe, assets),
                         [str(exe), "--assets", str(assets), "--test-world", "--host", "127.0.0.1:34600", "--mute"])

    def test_missing_python_modules_are_listed(self):
        self.assertEqual(cj_setup.check_python_modules(["json", "no_such_module_cj"]), ["no_such_module_cj"])

    def test_play_skate_without_skate_says_so(self):
        with tempfile.TemporaryDirectory() as d:
            config = Path(d) / "c.json"
            config.write_text(json.dumps({"gta": "x", "minecraft": "y"}))
            self.assertEqual(cj_setup.main(["play-skate"], config=config), 1)

    def test_skate_on_windows_writes_the_rig_and_the_board_and_remembers_the_engine(self):
        with tempfile.TemporaryDirectory() as d:
            pack = Path(d) / "cj skate"
            glb = self.installation(pack / "motor", "a", 1000)
            minecraft = Path(d) / "mc"
            minecraft.mkdir()
            config = Path(d) / "c.json"
            config.write_text(json.dumps({"gta": "g", "minecraft": str(minecraft)}))
            written = []
            code = cj_setup.main(["skate", "--engine", str(pack)], system="Windows", config=config,
                                 write_skate=lambda src, out: written.append((src, out)))
            self.assertEqual(code, 0)
            self.assertEqual(written, [(glb, minecraft / "config" / "cj_fabric")])
            saved = json.loads(config.read_text())
            self.assertEqual(saved["engine"], str(pack / "motor" / "skate3rust.exe"))
            self.assertEqual(saved["engine_assets"], str(glb.parent.parent))
            self.assertEqual(saved["gta"], "g")  # what install saved stays

    def test_skate_on_linux_converts_first_and_needs_the_skate3_folder(self):
        with tempfile.TemporaryDirectory() as d:
            pack = Path(d) / "cj skate"
            (pack / "motor").mkdir(parents=True)
            minecraft = Path(d) / "mc"
            minecraft.mkdir()
            config = Path(d) / "c.json"
            config.write_text(json.dumps({"gta": "g", "minecraft": str(minecraft)}))
            self.assertEqual(cj_setup.main(["skate", "--engine", str(pack)], system="Linux", config=config), 1)  # no --skate3
            ran = []

            def run(cmd):
                ran.append(cmd)
                self.installation(pack / "motor", "a", 1000)
                return 0
            code = cj_setup.main(["skate", "--engine", str(pack), "--skate3", str(Path(d) / "Skate 3")], system="Linux", config=config,
                                 run=run, write_skate=lambda src, out: None, modules=lambda names: [])
            self.assertEqual(code, 0)
            self.assertEqual(ran[0][1:3], [str(cj_setup.HERE / "skate_convert.py"), "--motor"])
```

(Add `import os` next to the other imports of `test_cj_setup.py`.)

- [ ] **Step 2: Run them** — `cd tools && python3 -B -m unittest tests.test_cj_setup tests.test_skate_convert` → FAIL (`skate_convert` missing, `find_skater_glb` missing).

- [ ] **Step 3: Implement**

`tools/skate_convert.py`:

```python
#!/usr/bin/env python3
"""Converts the user's Skate 3 (default.xex and its data folder) with the Skate engine's own asset pipeline, as its setup window would, but without the
audio step: on Linux that step downloads a Windows program and fails, and the mod runs the engine muted. Run with the Python that has numpy and Pillow.
Usage: skate_convert.py --motor <the pack's motor folder> --xex <default.xex> --game-exe <the engine's executable>"""
import argparse
import importlib
import sys
from pathlib import Path


def without_audio(asset_exports):
    """The audio export does nothing (the engine is run with --mute)."""
    asset_exports.audio = lambda *args, **kwargs: print("(audio skipped: not needed, the engine runs muted)")


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--motor", required=True)
    ap.add_argument("--xex", required=True)
    ap.add_argument("--game-exe", required=True)
    args = ap.parse_args(argv)
    motor = Path(args.motor).resolve()
    sys.path.insert(0, str(motor))
    try:
        without_audio(importlib.import_module("tools.asset_pipeline.asset_exports"))
        install = importlib.import_module("tools.asset_pipeline.customiser_setup").install
        install(Path(args.xex), motor / "data", Path(args.game_exe), print)
    finally:
        sys.path.remove(str(motor))
        for name in [n for n in sys.modules if n == "tools" or n.startswith("tools.")]:
            del sys.modules[name]  # the next run (or a test) imports its own engine tools
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

Add to `tools/cj_setup.py` (imports: `import importlib.util` and `import make_skate_rig` next to the others):

```python
ENGINE_HOST = "127.0.0.1:34600"


def find_skater_glb(motor):
    """The skater the engine converted (the newest when there are several conversions)."""
    found = sorted(Path(motor).glob("**/assets/private/skater.glb"), key=lambda p: p.stat().st_mtime)
    if not found:
        raise SetupError(f"no hay archivos de Skate 3 convertidos en {motor}: abre skate3rust una vez y completa su asistente (Windows), "
                         "o usa --skate3 <carpeta con default.xex> (Linux)")
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
```

In `main`, add parameters `run=lambda cmd: subprocess.call(cmd)`, `write_skate=write_skate_files`, `modules=check_python_modules`; add the subcommands:

```python
    p = sub.add_parser("skate", help="prepara el skate con tu Skate 3 (necesita el pack de skate)")
    p.add_argument("--engine", required=True, help="la carpeta del pack de skate (la que tiene motor/)")
    p.add_argument("--skate3", help="la carpeta de Skate 3 con default.xex (Linux: para convertir tus archivos)")
    p.add_argument("--minecraft", help="la carpeta de Minecraft o de la instancia")
    sub.add_parser("play-skate", help="abre el motor de Skate en modo anfitrión (después de GTA, antes de Minecraft)")
```

and, inside the `try`, before the `icons` branch:

```python
        if args.command == "play-skate":
            saved = load_config(config)
            if "engine" not in saved:
                raise SetupError("primero prepara el skate: python cj_setup.py skate --engine <carpeta del pack de skate>")
            print("abriendo el motor de Skate... déjalo abierto y entra a Minecraft")
            subprocess.Popen(engine_command(Path(saved["engine"]), Path(saved["engine_assets"])), cwd=str(Path(saved["engine"]).parent))
            return 0
        if args.command == "skate":
            pack = Path(args.engine)
            motor = pack / "motor"
            if not motor.is_dir():
                raise SetupError(f"no está la carpeta motor en {pack}: indica la carpeta del pack de skate descomprimido")
            if system != "Windows":
                if not args.skate3:
                    raise SetupError("en Linux indica también --skate3 <carpeta de Skate 3 con default.xex>")
                missing = modules(["numpy", "PIL"])
                if missing:
                    raise SetupError("faltan numpy y Pillow para convertir Skate 3: pip install numpy pillow")
                print("convirtiendo tus archivos de Skate 3 (la primera vez tarda)...")
                if run(convert_command(sys.executable, pack, Path(args.skate3), system)) != 0:
                    raise SetupError("la conversión de Skate 3 falló (mira los mensajes de arriba)")
            glb = find_skater_glb(motor)
            minecraft = minecraft_folder(args.minecraft, system, config)
            write_skate(glb, models_dir(minecraft))
            saved = load_config(config) if Path(config).is_file() else {}
            saved.update({"minecraft": saved.get("minecraft", str(minecraft)), "engine": str(engine_executable(motor, system)),
                          "engine_assets": str(assets_of(glb))})
            Path(config).write_text(json.dumps(saved))
            print("skate listo. Para jugar: GTA, luego skate.bat / skate.sh, luego Minecraft")
            return 0
```

Change `save_config` so it keeps the keys already saved (the engine's), writing `gta` and `minecraft` over them:

```python
def save_config(path, gta, minecraft):
    saved = json.loads(Path(path).read_text()) if Path(path).is_file() else {}
    saved.update({"gta": str(gta), "minecraft": str(minecraft)})
    Path(path).write_text(json.dumps(saved))
```

In `tools/make_release.py`: `SETUP_FILES = ("cj_setup.py", "convert_assets.py", "install_item_icons.py", "make_platform_script.py", "make_skate_rig.py", "skate_convert.py")`.

- [ ] **Step 4: Run** — `cd tools && python3 -B -m unittest discover -s tests` → PASS (the new 10 tests and all the old ones).

- [ ] **Step 5: Commit** — `git add tools && git commit -m "Setup: skate and play-skate, and the engine's conversion without audio for Linux"`

---

### Task 3: El pack de Linux, su revisión, `skate.sh`/`skate.bat`, `LEEME-skate.md` y `FUENTE.md`

**Files:**
- Create: `tools/skate_pack_audit.py`, `tools/make_skate_pack.sh`, `tools/skate_pack/skate.sh`, `tools/skate_pack/skate.bat`, `tools/skate_pack/LEEME-skate.md`, `tools/skate_pack/FUENTE.md`
- Test: `tools/tests/test_skate_pack_audit.py`

**Interfaces:**
- Produces: `skate_pack_audit.problems(zip_path: Path) -> list[str]`; `skate_pack_audit.main(argv) -> int` (exit 1 with the list when there are problems).

- [ ] **Step 1: Write the failing test**

```python
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import skate_pack_audit  # noqa: E402


def pack(d, names):
    path = Path(d) / "p.zip"
    with zipfile.ZipFile(path, "w") as z:
        for name in names:
            z.writestr(name, b"x")
    return path


class SkatePackAuditTest(unittest.TestCase):
    def test_a_clean_pack_passes(self):
        with tempfile.TemporaryDirectory() as d:
            names = ["cj-skate-0.2.0-linux/motor/skate3rust", "cj-skate-0.2.0-linux/motor/tools/setup.py", "cj-skate-0.2.0-linux/motor/licenses/LICENSE.txt",
                     "cj-skate-0.2.0-linux/skate.sh", "cj-skate-0.2.0-linux/FUENTE.md", "cj-skate-0.2.0-linux/skate-engine-host.patch"]
            self.assertEqual(skate_pack_audit.problems(pack(d, names)), [])

    def test_skate3_files_and_conversions_are_refused(self):
        with tempfile.TemporaryDirectory() as d:
            for name in ("p/default.xex", "p/Skate 3.iso", "p/motor/data/installations/abc/assets/private/skater.glb",
                         "p/motor/data/installation.json", "p/skate-spike-assets/x", "p/board.glb"):
                self.assertTrue(skate_pack_audit.problems(pack(d, [name])), name)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run** — `cd tools && python3 -B -m unittest tests.test_skate_pack_audit` → FAIL (module missing).

- [ ] **Step 3: Implement**

`tools/skate_pack_audit.py`:

```python
#!/usr/bin/env python3
"""Checks a skate pack before it is shared: nothing of the user's Skate 3 (the game, an ISO, a conversion, the converted skater or board) may be in it.
Usage: skate_pack_audit.py <pack.zip>"""
import sys
import zipfile
from pathlib import Path

FORBIDDEN_EXTENSIONS = (".xex", ".iso", ".glb")
FORBIDDEN_PARTS = ("/installations/", "/data/", "skate-spike-assets/")


def problems(zip_path):
    found = []
    with zipfile.ZipFile(zip_path) as z:
        for name in z.namelist():
            low = "/" + name.lower()
            if low.endswith(FORBIDDEN_EXTENSIONS):
                found.append(f"{name}: un archivo de Skate 3 o convertido")
            elif any(part in low for part in FORBIDDEN_PARTS):
                found.append(f"{name}: una conversión de Skate 3")
    return found


def main(argv):
    if len(argv) != 2:
        print(__doc__)
        return 2
    found = problems(Path(argv[1]))
    for line in found:
        print(line)
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
```

`tools/make_skate_pack.sh`:

```bash
#!/usr/bin/env bash
# Builds the Linux skate pack: the Skate 3 engine (SK8-ENGINE/skate-3-rust-engine at the pinned commit) with the host patch, one executable without
# Bevy's libraries beside it, its conversion tools, its licenses, skate.sh, LEEME-skate.md, FUENTE.md and the patch; zipped and audited.
# Usage: tools/make_skate_pack.sh <version>     (needs git, cargo, zip, python3)
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

# rustc crashes now and then inside LLVM on some machines: clean the crate and try again
for attempt in 1 2 3; do
    if cargo build --manifest-path "$work/Cargo.toml" --release -p skate-game --bin skate3rust --no-default-features --target-dir "$target" -j 8; then
        break
    fi
    [[ $attempt == 3 ]] && { echo "the engine did not build" >&2; exit 1; }
    cargo clean --manifest-path "$work/Cargo.toml" --release -p skate-game --target-dir "$target"
done

rm -rf "$out"
mkdir -p "$out/motor/licenses"
cp "$target/release/skate3rust" "$out/motor/"
cp -r "$work/tools" "$out/motor/tools"
find "$out/motor/tools" -name __pycache__ -prune -exec rm -rf {} +
cp "$work/LICENSE" "$out/motor/licenses/skate-3-rust-engine-GPL-3.0.txt"
for license in "$work"/vendor/*/LICENSE*; do
    cp "$license" "$out/motor/licenses/$(basename "$(dirname "$license")")-$(basename "$license").txt"
done
cp "$root/tools/skate_pack/skate.sh" "$root/tools/skate_pack/LEEME-skate.md" "$root/tools/skate_pack/FUENTE.md" "$root/skate-host/skate-engine-host.patch" "$out/"
chmod +x "$out/skate.sh" "$out/motor/skate3rust"

rm -f "$out.zip"
(cd "$(dirname "$out")" && zip -qr "$(basename "$out").zip" "$(basename "$out")")
python3 "$root/tools/skate_pack_audit.py" "$out.zip"
echo "pack: $out.zip"
```

`tools/skate_pack/skate.sh`:

```bash
#!/usr/bin/env bash
# Abre el motor de Skate en modo anfitrión (después de GTA, antes de Minecraft). Usa las carpetas que guardó "cj_setup.py skate".
here="$(cd "$(dirname "$0")" && pwd)"
setup="${CJ_SETUP:-$here/../cj-mod/setup/cj_setup.py}"
[[ -f "$setup" ]] || { echo "no encuentro cj_setup.py: indica su ruta con CJ_SETUP=/ruta/a/setup/cj_setup.py ./skate.sh" >&2; exit 1; }
exec python3 "$setup" play-skate
```

`tools/skate_pack/skate.bat`:

```bat
@echo off
rem Abre el motor de Skate en modo anfitrion (despues de GTA, antes de Minecraft). Usa las carpetas que guardo "cj_setup.py skate".
if "%CJ_SETUP%"=="" set "CJ_SETUP=%~dp0..\cj-mod\setup\cj_setup.py"
if not exist "%CJ_SETUP%" (
    echo no encuentro cj_setup.py: pon el pack de skate junto a la carpeta cj-mod, o define CJ_SETUP con su ruta
    pause
    exit /b 1
)
python "%CJ_SETUP%" play-skate
```

(`cj-mod` is the folder of the unzipped base package renamed by the guide: the guide tells the user to keep both unzipped folders side by side as `cj-mod` and `cj-skate`.)

`tools/skate_pack/FUENTE.md` — in Spanish: what this pack contains (the Skate 3 Rust engine, GPL-3.0, built with our host-mode patch); the upstream repository `https://github.com/SK8-ENGINE/skate-3-rust-engine` and commit `2e16697797c8358d16f747a8259354ff2921041a`; that `skate-engine-host.patch` is the only change; the steps to rebuild (clone, checkout, `git apply`, `cargo build --release -p skate-game --bin skate3rust --no-default-features` on Linux, `scripts/Build-Release.ps1` on Windows); the licenses folder; that nothing of Skate 3 is included.

`tools/skate_pack/LEEME-skate.md` — in Spanish, sections:
- **Qué es**: the optional skate mode of the CJ mod; needs the base mod installed (`cj-mod`), the user's Skate 3 for Xbox 360 (`default.xex` with its `data` folder, or the ISO on Windows) and a gamepad.
- **Dónde poner las carpetas**: unzip the base package as `cj-mod` and this pack as `cj-skate`, side by side.
- **Preparar (Windows)**: open `cj-skate\motor\skate3rust.exe` once, choose `default.xex` or the ISO in its assistant, wait for it, close it; then `python cj-mod\setup\cj_setup.py skate --engine cj-skate`.
- **Preparar (Linux)**: `pip install numpy pillow`; `python3 cj-mod/setup/cj_setup.py skate --engine cj-skate --skate3 "/ruta/a/Skate 3"` (the first conversion takes a while; the audio step is skipped: the engine runs muted).
- **Jugar**: GTA (and the save), then `skate.bat` / `./skate.sh` (the engine window stays open), then Minecraft; the skateboard in hand; right click or L3 + R3 to get on and off; triangle walks with the board; the camera follows.
- **Problemas comunes**: "Skate: the Skate engine is not running" (open skate.bat/skate.sh first); "no hay archivos de Skate 3 convertidos" (Windows: complete the engine's assistant; Linux: `--skate3`); "faltan numpy y Pillow"; the firewall asking about skate3rust (allow local network: 127.0.0.1).

- [ ] **Step 4: Run the audit tests** — `cd tools && python3 -B -m unittest tests.test_skate_pack_audit` → PASS (2 tests).

- [ ] **Step 5: Build the Linux pack for real** — `bash tools/make_skate_pack.sh 0.2.0` → `pack: .../build/release/cj-skate-0.2.0-linux.zip`; then unzip it to the scratchpad and run `motor/skate3rust --assets <the user's converted assets> --test-world --host 127.0.0.1:34601 --mute` with `tools/skate_host_probe.py` against port 34601 (`python3 -B tools/skate_host_probe.py --blocks 5 127.0.0.1:34601`): the deck must move several metres (the patched engine works without Bevy's libraries).

- [ ] **Step 6: Commit** — `git add tools && git commit -m "Skate pack: the Linux pack, its audit, skate.sh/skate.bat, LEEME-skate.md and FUENTE.md"`

---

### Task 4: El script del pack de Windows

**Files:**
- Create: `tools/make_skate_pack.ps1`

- [ ] **Step 1: Write the script**

```powershell
# Builds the Windows skate pack on Windows: the Skate 3 engine (SK8-ENGINE/skate-3-rust-engine at the pinned commit) with the host patch, packaged by
# the engine's own Build-Release.ps1 (with its first-run setup assistant), plus skate.bat, LEEME-skate.md, FUENTE.md and the patch; zipped and audited.
# Needs, once: git, Rust with the MSVC toolchain, LLVM (default location) and Python 3.13 (the engine's packaging uses it).
# Usage (PowerShell, in the repository): powershell -ExecutionPolicy Bypass -File tools\make_skate_pack.ps1 -Version 0.2.0
param([Parameter(Mandatory = $true)][string]$Version)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$commit = '2e16697797c8358d16f747a8259354ff2921041a'
$work = Join-Path $root 'build\skate-pack\src'
$out = Join-Path $root "build\release\cj-skate-$Version-windows"

if (-not (Test-Path (Join-Path $work '.git'))) {
    git clone https://github.com/SK8-ENGINE/skate-3-rust-engine $work
    if ($LASTEXITCODE -ne 0) { throw 'git clone failed' }
}
git -C $work checkout -q --force $commit
git -C $work clean -fdq
git -C $work apply (Join-Path $root 'skate-host\skate-engine-host.patch')
if ($LASTEXITCODE -ne 0) { throw 'the host patch did not apply' }

Push-Location $work
try {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\Build-Release.ps1
    if ($LASTEXITCODE -ne 0) { throw 'the engine did not build (Build-Release.ps1)' }
} finally { Pop-Location }

$engineZip = Join-Path $work 'target\skate3rust-windows-x64.zip'
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force (Join-Path $out 'motor') | Out-Null
$unpacked = Join-Path $root 'build\skate-pack\engine-release'
if (Test-Path $unpacked) { Remove-Item -Recurse -Force $unpacked }
Expand-Archive -LiteralPath $engineZip -DestinationPath $unpacked
$inner = Get-ChildItem $unpacked | Select-Object -First 1
Copy-Item -Recurse -Path (Join-Path $inner.FullName '*') -Destination (Join-Path $out 'motor')
foreach ($name in 'skate.bat', 'LEEME-skate.md', 'FUENTE.md') { Copy-Item (Join-Path $root "tools\skate_pack\$name") $out }
Copy-Item (Join-Path $root 'skate-host\skate-engine-host.patch') $out

$zip = "$out.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
Compress-Archive -LiteralPath $out -DestinationPath $zip
python (Join-Path $root 'tools\skate_pack_audit.py') $zip
if ($LASTEXITCODE -ne 0) { Remove-Item -Force $zip; throw 'the pack had Skate 3 files in it; it was not made' }
Write-Host "pack: $zip"
```

Note on the engine's zip layout: if `Expand-Archive` gives the files directly (no inner folder), `$inner` is a file; then copy `$unpacked\*` instead. The script handles it:

```powershell
$source = if ($inner -and $inner.PSIsContainer -and (Get-ChildItem $unpacked).Count -eq 1) { $inner.FullName } else { $unpacked }
Copy-Item -Recurse -Path (Join-Path $source '*') -Destination (Join-Path $out 'motor')
```

(use these two lines in place of the two `$inner` lines above).

- [ ] **Step 2: Check the script parses** — there is no PowerShell here; check it with the user on Windows in Task 5 (the first run is the test).

- [ ] **Step 3: Commit** — `git add tools/make_skate_pack.ps1 && git commit -m "Skate pack: the Windows pack script (built on Windows with the engine's own packaging)"`

---

### Task 5: Paquete base 0.2.0 y prueba desde cero (con el usuario)

- [ ] **Step 1:** `python3 tools/make_release.py 0.2.0` → `build/release/cj-mod-0.2.0.zip` (with `make_skate_rig.py` and `skate_convert.py` in `setup/`).
- [ ] **Step 2 (Linux, the user):** unzip `cj-mod-0.2.0.zip` as `cj-mod` and `cj-skate-0.2.0-linux.zip` as `cj-skate` side by side; `install` (base), `skate --engine cj-skate --skate3 <Skate 3>`; GTA, `./skate.sh`, Minecraft; skate.
- [ ] **Step 3 (Windows, the user):** run `tools\make_skate_pack.ps1 -Version 0.2.0` in the repository on Windows; unzip as above; open `motor\skate3rust.exe` once (its assistant); `skate --engine cj-skate`; GTA, `skate.bat`, Minecraft; skate.
- [ ] **Step 4:** fix what fails (code with its test), adjust the guides.
