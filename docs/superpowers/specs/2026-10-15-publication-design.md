# Publicación en GitHub — diseño (parte 3 de 3)

**Fecha:** 2026-10-15

## Objetivo

Publicar el mod en GitHub como `akilex02/cj-in-minecraft`: el código fuente en un repositorio público con una historia nueva, en inglés para los
usuarios, con licencia GPL-3.0, créditos y avisos claros, y un Release v0.2.0 con los paquetes ya probados en Linux y Windows. Los pasos con efecto en
GitHub (crear el repositorio, `git push`, el Release) los hace el usuario.

## Decisiones del usuario

- Código fuente con **historia nueva** (un commit inicial), sin el correo de git ni rutas personales.
- Licencia **GPL-3.0** para todo.
- Docs internos de desarrollo **incluidos, limpiados** de rutas personales (siguen en español).
- README, guías e instalador **en inglés**.
- Autor del commit: `AkiLeX <42688146+akilex02@users.noreply.github.com>`.
- Portada: `img4.png` (CJ en un bosque de Minecraft).
- Créditos de los íconos de armas: el mod "SA Weapon Icons" (https://gtaforums.com/topic/997951-sa-weapon-icons/), sin incluirlos; la guía dice
  que de ahí se consiguen.
- El pack de Windows (`build/release/cj-skate-0.2.0-windows.zip`, ya hecho y probado) se publica **tal cual, en español**.

## Diseño

### 1. El repositorio público

`tools/make_public_repo.py <carpeta destino>` arma el repositorio en una carpeta nueva (por defecto `build/public/cj-in-minecraft`), sin tocar el
repositorio de desarrollo:

1. Copia **solo los archivos que git sigue** (`git ls-files`), más los nuevos de publicación (README, LICENSE, etc.), y la portada en
   `docs/images/cover.jpg` (JPG de unos 300–500 KB hecho desde `img4.png`).
2. En los documentos internos (`docs/`), reemplaza las rutas personales: `.` → la raíz del repositorio (`.`
   o "the repository root"), `<GTA folder>` y otras rutas de la máquina → `<GTA folder>`, `<Skate 3 folder>`, etc.
3. **Revisión** (falla y no sigue): rutas personales (`/home/`, `/mnt/`, `C:\Users\`), el Gmail de git, archivos de juego (`.img`, `.dff`, `.txd`,
   `.ifp`, `.col`, `.xex`, `.iso`; los `.glb` salvo los tres sintéticos de los tests), íconos de la comunidad (PNG en `textures/item/gta/` distintos
   de los genéricos), claves (`sk-ant-`, `ghp_`, `AKIA...`, `FAL_KEY=`), archivos de más de 5 MB.
4. `git init -b main`, un solo commit firmado `AkiLeX <42688146+akilex02@users.noreply.github.com>` con el mensaje "CJ in Minecraft v0.2.0".

### 2. Licencias y avisos

- `LICENSE`: el texto de la GPL-3.0.
- En el README, una sección **Legal**: no incluye nada de GTA San Andreas (Rockstar Games) ni de Skate 3 (Electronic Arts); cada usuario usa sus
  propias copias; no está afiliado ni respaldado por Rockstar, Take-Two, EA, Mojang ni Microsoft; los nombres y marcas son de sus dueños; el motor
  de Skate 3 en Rust es de SK8-ENGINE (GPL-3.0) y el pack de skate lleva su fuente (`FUENTE.md` / `SOURCE.md` y el parche).

### 3. Documentación en inglés

- `README.md`: portada; qué es (CJ de GTA San Andreas jugando en Minecraft, con GTA corriendo al lado); qué incluye (CJ con sus animaciones y su
  ropa, armas y munición con recetas, puños y bate, agacharse, nadar, escalar, el coche Greenwood con radio y gasolina, el jetpack, primera persona,
  el skate opcional con el motor de Skate 3); requisitos; enlaces a `INSTALL.md`, `INSTALL-skate.md`, `BUILDING.md` y a Releases; créditos; legal;
  licencia.
- `INSTALL.md`: la traducción de `tools/release/LEEME.md`, con el enlace a "SA Weapon Icons" donde habla de los íconos. Va también dentro de
  `cj-mod-0.2.0.zip` (reemplaza a `LEEME.md`).
- `INSTALL-skate.md`: la traducción de `tools/skate_pack/LEEME-skate.md`; dice que el pack de Windows trae su guía en español (`LEEME-skate.md`) y
  que esta es la versión en inglés. El pack de Linux se regenera con `INSTALL-skate.md` y `SOURCE.md` (traducción de `FUENTE.md`).
- `BUILDING.md`: compilar el mod (`./gradlew build`), el puente (CMake + mingw i686), los tests, los paquetes (`make_release.py`,
  `make_skate_pack.sh`, el builder de Windows) y dónde están los docs internos.
- `CHANGELOG.md`: v0.2.0 (lo que trae) — de ahí salen las notas del Release.
- **Créditos**: Rockstar Games (GTA San Andreas), Electronic Arts / EA Black Box (Skate 3), SK8-ENGINE (Skate 3 Rust engine), FabricMC (Fabric
  Loader, Fabric API), el proyecto gta-reversed (referencia del puente), Silent (ASI Loader), CLEO y modloader (requisitos), "SA Weapon Icons"
  (https://gtaforums.com/topic/997951-sa-weapon-icons/, no incluidos), los íconos de munición (AkiLeX), y "Built with Claude Code (Anthropic)".

### 4. Mensajes en inglés

Los mensajes de `tools/cj_setup.py`, `tools/release/jugar.*` (pasan a `play.bat` / `play.sh`) y `tools/skate_pack/skate.*` pasan a inglés; los
tests que miran textos se ajustan. Los comandos (`install`, `dress`, `icons`, `play`, `skate`, `play-skate`) no cambian, así el `skate.bat` del pack
de Windows (en español) sigue funcionando.

### 5. El Release v0.2.0

- `cj-mod-0.2.0.zip`: regenerado con `INSTALL.md`, `play.*` y el instalador en inglés.
- `cj-skate-0.2.0-linux.zip`: regenerado con `INSTALL-skate.md`, `SOURCE.md` y `skate.sh` en inglés.
- `cj-skate-0.2.0-windows.zip`: el que ya está, sin cambios.
- `SHA256SUMS.txt` de los tres.
- Notas: el texto de v0.2.0 del `CHANGELOG.md` más "what to download".

### 6. La subida (la hace el usuario)

Se le dan los comandos exactos: crear `akilex02/cj-in-minecraft` vacío en GitHub (sin README ni licencia), `git remote add origin
https://github.com/akilex02/cj-in-minecraft.git`, `git push -u origin main` desde la carpeta preparada; crear el Release `v0.2.0` desde la web
con el texto preparado y subir los cuatro archivos.

### 7. Pruebas

- Unitarias: la revisión del repositorio público (rechaza una ruta personal, el Gmail, un `.dff`, un ícono de la comunidad, una clave; deja pasar los
  `.glb` sintéticos de los tests) y el reemplazo de rutas en los documentos.
- La suite completa en verde con los mensajes en inglés.
- El repositorio público armado: `git log` con un solo commit y el autor noreply; la revisión limpia; el mod compila desde esa carpeta
  (`./gradlew build`).

## Fuera de alcance

- Traducir los docs internos.
- Un video de presentación (se puede añadir después al README).
- Publicar en Modrinth / CurseForge / Nexus.
