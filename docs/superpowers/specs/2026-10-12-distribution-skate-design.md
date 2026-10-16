# Pack de skate distribuible — diseño (parte 2 de 3)

**Fecha:** 2026-10-12

## Objetivo

Que quien ya tiene el mod base instalado (parte 1) pueda añadir el modo skate con su propia copia de Skate 3, en **Windows** o **Linux**, con un pack
opcional que trae el motor de Skate 3 en Rust con nuestro parche de modo anfitrión. El mod es uno solo (el `.jar` ya trae el skate); el pack añade el motor.

## Decisiones del usuario

- Un solo mod con el skate dentro y un **pack de skate opcional** aparte (no dos releases del mod).
- La receta del skateboard no se oculta: sin el pack, un mensaje lo explica.
- El pack de **Windows** lo compila el usuario en su Windows con un `.ps1` (no compilación cruzada).

## Diseño

### 1. Los packs

**`cj-skate-<versión>-windows.zip`**, hecho en Windows por `tools/make_skate_pack.ps1 <versión>`:

1. Clona `https://github.com/SK8-ENGINE/skate-3-rust-engine` en una carpeta de trabajo, `git checkout 2e16697797c8358d16f747a8259354ff2921041a`,
   `git apply` de `skate-host/skate-engine-host.patch`.
2. Corre `scripts/Build-Release.ps1` del motor (Rust MSVC, LLVM y Python 3.13), que deja el motor portátil (`skate3rust.exe`, su asistente
   `support/skate3setup.exe`, `licenses/`).
3. Arma `cj-skate-<versión>-windows/` con ese contenido en `motor/`, más `skate.bat`, `LEEME-skate.md`, `FUENTE.md` y `skate-engine-host.patch`; zip.
4. Revisa el zip (punto 4).

**`cj-skate-<versión>-linux.zip`**, hecho en Linux por `tools/make_skate_pack.sh <versión>`:

1. Lo mismo hasta el parche; compila `cargo build --release -p skate-game --bin skate3rust --no-default-features` (sin `dev-dynamic`: un
   ejecutable sin las bibliotecas de Bevy aparte). Los fallos de `rustc` de esta máquina se reintentan (hasta 3 veces, limpiando `skate-game`).
2. Arma `cj-skate-<versión>-linux/` con `motor/skate3rust`, `motor/tools/` (la herramienta de conversión del motor, en Python: `tools/setup.py`,
   `tools/asset_pipeline/` y lo que importen), `motor/licenses/` (la licencia del motor `LICENSE` y las de `vendor/`), más `skate.sh`,
   `LEEME-skate.md`, `FUENTE.md` y el parche; zip.
3. Revisa el zip (punto 4).

### 2. `cj_setup.py skate --engine <carpeta del pack> [--skate3 <carpeta con default.xex>] [--minecraft <carpeta>]`

- **Windows** (`--skate3` no hace falta): el usuario ya abrió `skate3rust.exe` una vez y su asistente convirtió los archivos (en `data/` junto al
  ejecutable). El comando busca `**/assets/private/skater.glb` bajo `<engine>/motor/`; si no está, error: abrir `skate3rust.exe` y completar su
  asistente primero.
- **Linux** (`--skate3` requerido la primera vez): comprueba que estén numpy y Pillow (si no, error con `pip install numpy pillow`), y corre la
  conversión del motor (`tools/setup.py --base <engine>/motor/data --game-exe <skate3>/default.xex`) **sin el paso del audio** (en Linux descarga un
  `.exe` de Windows y falla; el mod usa el motor con `--mute`). Después busca `skater.glb` como en Windows.
- En los dos: escribe `skate_rig.json`, `skate_board.json` y sus PNG en `<minecraft>/config/cj_fabric/` (lo de `tools/make_skate_rig.py`), y guarda
  la carpeta del motor y la de recursos convertidos en `cj_setup.json` (para `skate.bat` / `skate.sh`).

### 3. Jugar

Primero GTA (con la partida), luego el motor: `skate.bat` / `skate.sh` (que llaman a `cj_setup.py play-skate`: el motor con
`--assets <recursos convertidos> --test-world --host 127.0.0.1:34600 --mute`), luego Minecraft. En Minecraft: el skateboard en la mano, clic derecho
o L3 + R3, con un mando.

### 4. Licencias y revisión del pack

- Cada pack lleva la licencia GPL-3.0 del motor, las de sus dependencias, `skate-engine-host.patch` y `FUENTE.md` (el repositorio original, el commit
  exacto, el parche y los pasos para reconstruir el ejecutable).
- La revisión falla si el zip contiene `.xex`, `.iso`, una carpeta `installations/` o `data/` con recursos convertidos, `.glb`, o algo de
  `skate-spike-assets/`.

### 5. El mod sin el pack

El mensaje del mod cuando el motor no responde pasa a ser: "Skate: the Skate engine is not running. Skating needs the optional skate pack (see
LEEME-skate.md); start it with skate.bat / skate.sh before getting on the board." Nada más cambia: el skateboard y su receta siguen.

### 6. Paquete base

`cj_setup.py` gana los comandos `skate` y `play-skate`; el zip base pasa a la versión 0.2.0 (con el mensaje nuevo del mod).

### 7. Pruebas

- Unitarias (`tools/tests/`): la búsqueda de `skater.glb` (encuentra el de la instalación, falla con un mensaje claro sin ella); el comando de la
  conversión en Linux (con `--base` y `--game-exe` y el audio fuera); el comando del motor de `play-skate`; numpy/Pillow ausentes → error claro;
  la revisión del pack (rechaza `.xex`, `installations/`, `.glb`).
- Java: el mensaje nuevo (el texto que da `SkateRules` cuando el motor no responde).
- Manuales (el usuario): desde cero en Windows (compilar el pack con el `.ps1`, instalarlo, jugar) y en Linux (instalar el pack, jugar).

## Fuera de alcance

- Compilación cruzada del `.exe` de Windows.
- El sonido del motor en Linux.
- La publicación (parte 3).
