# Mod base distribuible — diseño (parte 1 de 3)

**Fecha:** 2026-10-12

## Objetivo

Que otra persona pueda instalar y jugar el mod (CJ en Minecraft con GTA San Andreas corriendo al lado) en **Windows** o **Linux**, desde un paquete,
con sus propios archivos de GTA. Es la parte 1 de compartir el mod entero; la parte 2 es el extra de skate y la parte 3 la publicación (zip de release,
créditos, licencias, guía completa).

## Decisiones del usuario

- Compartir el mod entero, dividido en tres partes; se empieza por el mod base.
- El usuario puede probar en Windows.
- Instalador **en Python** (biblioteca estándar), reutilizando el convertidor existente. Un `.exe` de Windows (PyInstaller) queda como mejora opcional.

## Diseño

### 1. El paquete

```
cj-mod-<versión>/
  cj-fabric-<versión>.jar      el mod, compilado con los íconos genéricos (ninguno de la comunidad)
  cj_bridge.asi                el puente, compilado (i686, mingw): el mismo para Windows y para Linux con Wine
  setup/cj_setup.py            el instalador
  setup/cjconvert/...          el convertidor de modelos (el de tools/, sin cambios)
  jugar.bat, jugar.sh          abren GTA y recuerdan abrir Minecraft
  LEEME.md                     instalación y uso
```

Nada de GTA, de Skate 3 ni íconos de la comunidad dentro del paquete.

### 2. Lo que el usuario necesita

- GTA San Andreas **1.0 US** (`gta_sa.exe` de 14 383 616 bytes) con un **cargador de ASI** (Essentials Pack, Silent's ASI Loader o Ultimate ASI Loader).
- Minecraft **1.21.1** con **Fabric Loader** (0.19.5 o más nuevo) y **Fabric API** (0.116.17+1.21.1 o más nuevo), desde cualquier launcher.
- **Python 3.10** o más nuevo (en Windows, de python.org, con "Add python.exe to PATH").
- Linux: Wine para GTA.

### 3. `cj_setup.py install --gta <carpeta de GTA> [--minecraft <carpeta>] [--mods]`

1. **GTA:** en la carpeta indicada debe estar `gta_sa.exe`. Su tamaño debe ser el de la 1.0 US; si no, error con la sugerencia de un downgrader
   (`--force` lo salta, bajo responsabilidad del usuario). Debe haber un cargador de ASI: `vorbisHooked.dll` (Silent's, el del Essentials Pack),
   `dinput8.dll` o `winmm.dll` (Ultimate ASI Loader); si no, error con dónde conseguirlo.
2. **El puente:** copia `cj_bridge.asi` a la carpeta de GTA (si GTA está abierto y el archivo está en uso, error claro: cerrar GTA y repetir).
3. **Minecraft:** la carpeta por defecto es `%APPDATA%\.minecraft` en Windows y `~/.minecraft` en Linux; `--minecraft` acepta otra (la de una
   instancia de Prism, por ejemplo). Si no existe, error.
4. **Los modelos:** convierte, desde los archivos de GTA del usuario, a CJ (`cj.glb`), las armas (`weapons/*.glb`) y el coche (`vehicles/greenwood.glb`)
   en `<minecraft>/config/cj_fabric/`, como hace hoy `tools/convert_assets.py`.
5. **El mod:** con `--mods`, copia el `.jar` a `<minecraft>/mods/` (reemplazando una versión anterior de `cj-fabric-*.jar`). Avisa si en `mods/` no
   hay ningún `fabric-api-*.jar`.
6. **Informe:** cada paso dice qué hizo; al final, un resumen y los pasos que faltan (instalar Fabric, Fabric API, abrir GTA primero).

### 4. `cj_setup.py dress --gta <carpeta> [--minecraft <carpeta>]`

Viste a CJ con la ropa y el cuerpo de la partida: lee `cj_outfit.json` (lo escribe el puente al cargar la partida en GTA) y vuelve a convertir
`cj.glb`, como `tools/convert_assets.py --outfit`. Sin `cj_outfit.json`, error: abrir GTA, cargar la partida y repetir.

### 5. `cj_setup.py icons <carpeta de íconos> [--minecraft <carpeta>]`

Los íconos de las armas (de la comunidad) no se distribuyen. Este comando arma un *resource pack* `cj-icons` en `<minecraft>/resourcepacks/` con los
PNG que el usuario tenga (los mismos nombres que `weapon-icons/`: `Pistol.png`, `AK47.png`, etc., mapeados como en `tools/install_item_icons.py`):
`pack.mcmeta` (pack_format 34, el de 1.21.1) y `assets/cj_fabric/textures/item/gta/<id>.png`. El usuario lo activa en Opciones → Paquetes de recursos.

### 6. Jugar

Primero GTA (y cargar la partida), luego Minecraft. `jugar.bat` abre `gta_sa.exe` desde la carpeta guardada por `install` (un `cj_setup.json` junto a
`cj_setup.py` con las carpetas); `jugar.sh` hace lo mismo con Wine. Ambos terminan recordando abrir Minecraft con el perfil de Fabric.

### 7. El paquete se genera con `tools/make_release.py <versión>`

1. Instala los íconos genéricos en el árbol de recursos (como `install_item_icons.py` con una carpeta de íconos vacía) y compila el mod
   (`./gradlew build --offline --no-daemon`); al terminar, vuelve a instalar los íconos del usuario para su propio uso.
2. Compila el puente (`tools/install_bridge.sh` sin instalar: solo `cmake --build`).
3. Copia el `.jar`, el `.asi`, `setup/` (con `cjconvert/`), `jugar.*` y `LEEME.md` a `build/release/cj-mod-<versión>/` y hace el zip.
4. **Revisa el zip:** falla si contiene archivos `.img`, `.dff`, `.txd`, `.xex`, `.glb`, PNG bajo `textures/item/gta/` distintos de los genéricos, o
   cualquier cosa de `weapon-icons/`, `GTA San Andreas/` o de Skate 3.

### 8. Errores

Cada error dice qué pasó y qué hacer; el instalador nunca deja archivos a medias (escribe en temporales y renombra, como `convert_assets.py`).

### 9. Pruebas

- Unitarias (Python, `tools/tests/`): la versión de GTA por tamaño; la detección del cargador de ASI; la carpeta de Minecraft por sistema; la copia del
  `.jar` reemplazando la versión anterior; el *resource pack* (archivos y `pack.mcmeta`); la revisión del zip (rechaza un archivo prohibido).
- Manuales (el usuario): instalación desde cero siguiendo `LEEME.md` en Windows y en Linux; jugar; `dress`; `icons`.

## Fuera de alcance (partes 2 y 3)

- El extra de skate (motor, parche, archivos de Skate 3).
- El `.exe` de Windows del instalador.
- Créditos, licencias y la publicación del zip.
