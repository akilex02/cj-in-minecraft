# CJ en Minecraft — M1b: conversor de assets — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un conversor, `tools/convert_assets.py`, que lee la instalación de GTA SA del usuario y escribe `cj.glb`: el cuerpo base de CJ (cabeza, torso, manos, piernas y pies) con malla, normales, UVs, skinning, esqueleto de 32 huesos y texturas, más una vista previa PNG para verificarlo sin abrir ningún juego.

**Architecture:** Un paquete de Python de solo biblioteca estándar (`tools/cjconvert/`) con un módulo por formato: trozos RenderWare, archivo IMG, diccionario de texturas (TXD), modelo (DFF), ensamblado de piezas y escritor GLB. El conversor ensambla CJ a partir del esqueleto de `player.dff` (en `gta3.img`) y de cinco piezas de `player.img`, remapeando los huesos de cada pieza al esqueleto por `nodeID`. Las pruebas usan archivos sintéticos construidos en el propio test; los datos reales solo se tocan en una prueba opcional y en la ejecución del usuario.

**Tech Stack:** Python 3 (`unittest`, solo biblioteca estándar en el conversor); `numpy` y `Pillow` solo para la vista previa.

**Spec:** `docs/superpowers/specs/2026-10-05-m1-cj-visible-design.md` (sección 5), corregido en la Task 1 con lo que se descubrió al abrir los archivos reales.

**Alcance de este plan:** solo M1b. M1c (el render en Minecraft) tendrá su propio plan, escrito con el `cj.glb` real ya delante.

## Lo que se descubrió al abrir los archivos reales (corrige el diseño)

Medido sobre la instalación del usuario (`GTA San Andreas/models/`):
- `player.img` **no contiene** `player.dff`/`player.txd`: son las piezas del sistema de ropa. El esqueleto, `player.dff` (8192 bytes) y `player.txd`, está en **`gta3.img`**.
- `player.dff` es **solo el esqueleto**: 33 marcos, una jerarquía de 32 nodos con `nodeID` `[0,1,2,3,4,5,8,6,7,31,32,33,34,35,36,21,22,23,24,25,26,302,301,201,41,42,43,44,51,52,53,54]` (en ese orden de índice), y una geometría de relleno de 6 vértices cuyo plugin *Skin* trae las 32 matrices inversas de enlace.
- El cuerpo lo ensambla el juego (`CClothesBuilder::CreateSkinnedClump`) con cinco piezas de **`player.img`**: `torso.dff` (405 vértices, 646 triángulos), `head.dff` (581, 756), `hands.dff` (258, 300), `legs.dff` (287, 388) y `feet.dff` (214, 276). Cada una: un solo *morph target*, formato `0x36` (posiciones, UVs, normales; sin tira de triángulos), plugin *Skin* con hasta 3 huesos por vértice y **su propia jerarquía de 37 nodos** (los mismos 32 más los `nodeID` 303 a 307, que ningún vértice usa).
- **Remapeo de huesos:** el índice de hueso de una pieza → su `nodeID` (por la jerarquía de la propia pieza) → el índice del mismo `nodeID` en el esqueleto base. Todos los vértices con peso apuntan a `nodeID` que el esqueleto tiene.
- **Plugin *Skin*:** `u8 numBones, u8 numUsed, u8 maxWeights, u8 pad`; `numUsed` bytes de huesos usados; 4 bytes de índices por vértice; 4 floats de pesos por vértice; `numBones` matrices de 64 bytes (formato `RwMatrix`: right, up, at, pos con un relleno de 4 bytes tras cada fila); 12 bytes finales. El tamaño cuadra exacto (torso: 4 + 16 + 405×20 + 37×64 + 12 = 10500).
- **Triángulos:** cada uno son 4 `u16` en el orden `vertex2, vertex1, material, vertex3`; el orden `(vertex1, vertex2, vertex3)` sale con la cara hacia fuera (coincide con las normales en el 100 % de los triángulos del torso y las piernas y en el 99.7 % de la cabeza), sin invertir. Las UVs tienen el origen arriba a la izquierda, como en glTF; las del torso, la cabeza, las manos y las piernas caen en `[0,1]`, pero **las de los pies van de `v = -0.997` a `0.001`**: la textura se direcciona con repetición (`-0.997` equivale a `0.003`). El GLB conserva las UVs tal cual y declara el muestreo en modo repetición; la vista previa y M1c deben envolver las coordenadas.
- **Texturas** (todas D3D9, 32 bits sin compresión, `rasterFormat` `0x600` con `d3dFormat` 22 = X8R8G8B8, o `0x500`/21 con alfa): `player_torso.txd:torso` (256×256; el atlas incluye las manos, por eso `hands` usa la textura del torso), `player_face.txd:face` (128×128), `player_legs.txd:legs` (128×256, con ropa interior) y `player_feet.txd:foot` (64×64, descalzo).
- **Espacio del modelo:** medido en las matrices inversas de enlace, **X es adelante, Y es izquierda (+) / derecha (−) y Z es arriba**: los pies están en y = ±0.17, las manos en y = ±0.47, la punta del pie 0.15 por delante en X y la cabeza 0.59 por encima de la pelvis en Z. Es el espacio en el que salen los huesos del paquete `Pose` (la posición relativa al ped coincide con las coordenadas del clump).

## Global Constraints

- Los assets del juego y todo lo derivado (`cj.glb`, PNG, texturas) **no entran al repositorio**: el conversor escribe por defecto en `cj-fabric/run/config/cj_fabric/`, que ya está en `.gitignore` junto con `*.glb`. Las pruebas usan solo archivos sintéticos.
- El conversor usa solo la biblioteca estándar de Python. `numpy` y `Pillow` se usan únicamente en la vista previa, que es opcional.
- El `cj.glb` se escribe en el **espacio del modelo de GTA** (X adelante, Y izquierda, Z arriba), no en el Y-arriba de glTF; la conversión a Minecraft se hace una sola vez en M1c.
- Los `joints` del glTF van en el orden del esqueleto de GTA (índice de nodo de la jerarquía) y cada uno lleva su `nodeID` en `extras`, para que un `nodeId` del paquete `Pose` se asocie directamente.
- Solo se leen archivos del usuario; nunca se modifica nada en su instalación.
- La salida es determinista: la misma entrada da los mismos bytes.
- Un formato que el conversor no soporta (texturas comprimidas o con paleta, geometría en tiras de triángulos, versiones antiguas de RenderWare) falla con un error claro; nunca produce un resultado a medias.
- Hay git: un commit por tarea, con la línea `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## Review Focus

Entradas y condiciones que las pruebas de cada tarea no cubrirían por sí solas. Cada una tiene su prueba en la tarea indicada.

1. Archivos IMG, DFF o TXD truncados o corruptos (tamaños que se salen del padre, un recuento de entradas enorme): error claro y sin bucle infinito — Task 1, 2 y 3.
2. Falta un archivo dentro del IMG o una textura dentro del TXD (otra versión del juego): error que nombra el archivo y lo que sí hay — Task 4.
3. Texturas comprimidas, con paleta o de 16 bits, y geometría en tiras de triángulos: error explícito — Task 2 y 3.
4. Un vértice con peso sobre un hueso cuyo `nodeID` no existe en el esqueleto, o con pesos que no suman 1: error, nunca un hueso adivinado — Task 4.
5. Un `cj.glb` que un lector estricto de glTF rechazaría (alineación de 4 bytes, índices fuera de rango, `min`/`max` ausentes en `POSITION`): se comprueba con un lector propio — Task 5.
6. La carpeta de salida no existe, o ya hay un `cj.glb`: se crea la carpeta, se reemplaza el archivo de forma atómica y no se deja basura si falla — Task 6.
7. Los datos reales del usuario: las cifras de arriba (vértices, 32 huesos, pesos que suman 1, altura de CJ entre 1.5 y 2.1 m) y una vista previa que se ve como CJ — Task 6.

## Estructura de archivos

| Archivo | Responsabilidad |
|---|---|
| `tools/cjconvert/__init__.py` | Marca el paquete |
| `tools/cjconvert/rwchunk.py` | Lector de trozos RenderWare, errores de formato y versión de la biblioteca |
| `tools/cjconvert/img.py` | Lector del archivo IMG v2 |
| `tools/cjconvert/txd.py` | Diccionario de texturas (D3D9 de 32 bits) y codificador PNG |
| `tools/cjconvert/dff.py` | Esqueleto de `player.dff` y piezas del cuerpo |
| `tools/cjconvert/assemble.py` | Equipo por defecto, remapeo de huesos y modelo ensamblado |
| `tools/cjconvert/glb.py` | Escritor y lector de GLB (el lector es para pruebas y vista previa) |
| `tools/convert_assets.py` | Línea de comandos |
| `tools/preview_glb.py` | Vista previa por software con `numpy` y `Pillow` |
| `tools/tests/rwbuild.py` | Constructores de IMG/DFF/TXD sintéticos para las pruebas |
| `tools/tests/test_*.py` | Pruebas |
| `docs/m1b-results.md` | Resultados medidos |

Ejecutar todas las pruebas del conversor (desde la raíz del proyecto):

```
cd tools && python3 -m unittest discover -s tests 2>&1 | tail -4
```

Ejecutar una (por ejemplo `test_img`): `cd tools && python3 -m unittest tests.test_img -v 2>&1 | tail -15`

---

### Task 1: Corregir el diseño y el protocolo, y el lector de trozos RenderWare, del IMG y los constructores de prueba

**Files:**
- Modify: `docs/superpowers/specs/2026-10-05-m1-cj-visible-design.md` (secciones 5 y 6), `protocol/PROTOCOL.md`, `gta-bridge/src/pose.h` (solo comentarios)
- Create: `tools/cjconvert/__init__.py`, `tools/cjconvert/rwchunk.py`, `tools/cjconvert/img.py`
- Create: `tools/tests/rwbuild.py`, `tools/tests/test_rwchunk.py`, `tools/tests/test_img.py`

**Interfaces:**
- Produces (`cjconvert.rwchunk`): constantes `STRUCT=0x01, STRING=0x02, EXTENSION=0x03, TEXTURE_NATIVE=0x15, TEXTURE_DICTIONARY=0x16, FRAME_LIST=0x0E, GEOMETRY=0x0F, CLUMP=0x10, GEOMETRY_LIST=0x1A, SKIN=0x116, HANIM=0x11E`; `class FormatError(ValueError)`; `@dataclass(frozen=True) class Chunk(type, size, version, start)` con propiedad `end`; `root(buf) -> Chunk` (pseudo-padre que abarca todo el buffer); `iter_chunks(buf, start, end)`; `all_of(buf, parent, type_id) -> list[Chunk]`; `first(buf, parent, type_id) -> Chunk` (lanza `FormatError`); `library_version(raw) -> int`.
- Produces (`cjconvert.img`): `class ImgArchive(path)` con `names() -> list[str]`, `__contains__(name)`, `read(name) -> bytes` (lanza `KeyError` si falta, `FormatError` si el archivo está roto).
- Produces (`tests/rwbuild.py`): `chunk(type_id, payload=b"")`, `struct_chunk`, `extension(*children)`, `hanim(node_id, nodes=())`, `frame_list(node_ids)`, `geometry(positions, normals, uvs, triangles, flags=0x36, skin=None, prelit=False)`, `skin(bone_count, used, bone_indices, weights, matrices)`, `dff(node_ids, geometry_chunk)`, `texture_native(...)`, `txd(*natives)`, `img(files)`.

- [ ] **Step 1: Corregir el diseño de M1 y el protocolo**

En `docs/superpowers/specs/2026-10-05-m1-cj-visible-design.md`, sustituir el contenido completo de la sección `## 5. M1b — conversor (`tools/convert_assets.py`)` (hasta justo antes de `## 6.`) por:

```markdown
## 5. M1b — conversor (`tools/convert_assets.py`)

Se ejecuta sobre la instalación del usuario y escribe en la carpeta de configuración de Minecraft; nada de esto entra al repositorio. Los detalles de formato están en `docs/superpowers/plans/2026-10-05-m1b-converter.md` (sección "Lo que se descubrió al abrir los archivos reales"), medidos sobre los archivos reales.

1. **Esqueleto:** `player.dff` está en `models/gta3.img` (no en `player.img`) y es solo el esqueleto: 32 nodos con sus `nodeID` y las 32 matrices inversas de enlace en el plugin *Skin* de una geometría de relleno.
2. **Cuerpo:** el juego lo ensambla con cinco piezas de `models/player.img`: `torso.dff`, `head.dff`, `hands.dff`, `legs.dff` y `feet.dff`. Cada una trae malla, normales, UVs y skinning de hasta 3 huesos sobre su propia jerarquía de 37 nodos. El conversor remapea los huesos de cada pieza al esqueleto por `nodeID`.
3. **Texturas del CJ base:** `player_torso.txd:torso` (el atlas del torso incluye las manos), `player_face.txd:face`, `player_legs.txd:legs` y `player_feet.txd:foot`. Son D3D9 de 32 bits sin compresión.
4. **Salida:** `cj.glb` (glTF binario con `skin`, en el espacio del modelo de GTA: X adelante, Y izquierda, Z arriba; los `joints` en el orden del esqueleto de GTA, con su `nodeID` en `extras`) y una vista previa PNG.
5. No se convierten animaciones `.ifp`: GTA ya anima el esqueleto.
6. La ropa de verdad (el sistema de ropa completo, las texturas compuestas y la gordura/músculo) queda para M4.
```

En la misma especificación, en la sección 6 sustituir el párrafo que empieza por `- **Ejes:** el modelo de GTA tiene X derecha, Y adelante, Z arriba;` por:

```markdown
- **Ejes:** el espacio del modelo de CJ (el de `cj.glb` y el de los huesos del paquete `Pose`) es X adelante, Y izquierda, Z arriba. Un jugador de Minecraft mirando al sur tiene adelante = +Z, izquierda = +X y arriba = +Y, así que la conversión es `(x, y, z)GTA → (y, z, x)MC` (una permutación cíclica, determinante +1: gira, no espeja). Se verifica con un marcador de ejes (tres flechas) antes de mirar el modelo.
```

En `protocol/PROTOCOL.md`, cambiar la frase `**en el espacio del propio CJ** (X derecha, Y adelante, Z arriba; sin su posición ni su giro en el mundo de GTA)` por `**en el espacio del modelo de CJ** (X adelante, Y izquierda, Z arriba; sin su posición ni su giro en el mundo de GTA)`.

En `gta-bridge/src/pose.h`, cambiar el comentario `// Converts every bone from world space to the ped's own frame (X right, Y forward, Z up) and fills the packet.` por `// Converts every bone from world space to the ped's model space (the clump's own space: X forward, Y left, Z up) and fills the packet.` y el comentario del campo de la estructura `float up[3]; // the entity's forward direction` por `float up[3]; // the entity's forward direction (RwMatrix "up")`.

Run: `cd . && grep -c "X adelante" docs/superpowers/specs/2026-10-05-m1-cj-visible-design.md protocol/PROTOCOL.md && clang++ -std=c++20 -Wall -Wextra -Werror -Igta-bridge/src gta-bridge/tests/test_pose.cpp -o build/test_pose && build/test_pose`
Expected: dos conteos distintos de cero y `test_pose OK` (el cambio en `pose.h` es solo de comentarios).

- [ ] **Step 2: Escribir los constructores de prueba**

`tools/tests/rwbuild.py`:

```python
"""Builders for tiny synthetic RenderWare/IMG files, so the converter is tested without any game data."""
import struct

VERSION = 0x1803FFFF  # the library stamp GTA San Andreas files carry
IDENTITY3 = (1, 0, 0, 0, 1, 0, 0, 0, 1)


def chunk(type_id, payload=b""):
    return struct.pack("<III", type_id, len(payload), VERSION) + payload


def struct_chunk(payload):
    return chunk(0x01, payload)


def extension(*children):
    return chunk(0x03, b"".join(children))


def hanim(node_id, nodes=()):
    payload = struct.pack("<III", 0x100, node_id, len(nodes))
    if nodes:
        payload += struct.pack("<II", 0, 0)
        payload += b"".join(struct.pack("<iII", nid, idx, 0) for idx, nid in enumerate(nodes))
    return chunk(0x11E, payload)


def frame_list(node_ids):
    """Frame 0 is the clump root; frame 1 carries the skeleton hierarchy; there is one frame per node."""
    count = 1 + len(node_ids)
    body = struct.pack("<I", count)
    for i in range(count):
        body += struct.pack("<12f", *IDENTITY3, 0, 0, 0) + struct.pack("<iI", -1 if i == 0 else 0, 0x20003)
    exts = [extension()]
    for i in range(len(node_ids)):
        exts.append(extension(hanim(node_ids[0], node_ids) if i == 0 else hanim(node_ids[i])))
    return chunk(0x0E, struct_chunk(body) + b"".join(exts))


def geometry(positions, normals, uvs, triangles, flags=0x36, skin=None, prelit=False):
    """`triangles` are (v1, v2, v3); the file stores them as vertex2, vertex1, material, vertex3."""
    n = len(positions)
    if prelit:
        flags |= 0x08
    st = struct.pack("<IIII", flags | (1 << 16), len(triangles), n, 1)
    if prelit:
        st += b"\xff" * (n * 4)
    st += b"".join(struct.pack("<2f", *uv) for uv in uvs)
    st += b"".join(struct.pack("<4H", v2, v1, 0, v3) for (v1, v2, v3) in triangles)
    st += struct.pack("<4f", 0, 0, 0, 1) + struct.pack("<II", 1, 1)
    st += b"".join(struct.pack("<3f", *p) for p in positions)
    st += b"".join(struct.pack("<3f", *p) for p in normals)
    ext = extension(skin) if skin else extension()
    return chunk(0x0F, struct_chunk(st) + ext)


def skin(bone_count, used, bone_indices, weights, matrices):
    payload = struct.pack("<BBBB", bone_count, len(used), 3, 0) + bytes(used)
    payload += b"".join(bytes(i) for i in bone_indices)
    payload += b"".join(struct.pack("<4f", *w) for w in weights)
    payload += b"".join(struct.pack("<16f", *m) for m in matrices)
    payload += b"\0" * 12
    return chunk(0x116, payload)


def dff(node_ids, geometry_chunk):
    clump_struct = struct_chunk(struct.pack("<III", 1, 0, 0))
    geometry_list = chunk(0x1A, struct_chunk(struct.pack("<I", 1)) + geometry_chunk)
    return chunk(0x10, clump_struct + frame_list(node_ids) + geometry_list + extension()) + b"\0" * 24  # real files end in zero padding


def texture_native(name, width, height, bgra, alpha=False, raster=0x600, d3d=22, depth=32, platform=9, flags=None):
    if flags is None:
        flags = 1 if alpha else 0
    st = struct.pack("<II", platform, 0x1101) + name.encode().ljust(32, b"\0") + b"".ljust(32, b"\0")
    st += struct.pack("<II", raster, d3d) + struct.pack("<HH", width, height)
    st += struct.pack("<BBBB", depth, 1, 4, flags) + struct.pack("<I", len(bgra)) + bgra
    return chunk(0x15, struct_chunk(st) + extension())


def txd(*natives):
    return chunk(0x16, struct_chunk(struct.pack("<HH", len(natives), 9)) + b"".join(natives) + extension())


def img(files):
    """A VER2 archive: a 32-byte directory entry per file and the data in 2048-byte sectors."""
    names = list(files)
    header = 8 + 32 * len(names)
    first_sector = -(-header // 2048)
    entries = b""
    blobs = b""
    sector = first_sector
    for n in names:
        data = files[n]
        sectors = -(-len(data) // 2048)
        entries += struct.pack("<IHH24s", sector, sectors, 0, n.encode())
        blobs += data.ljust(sectors * 2048, b"\0")
        sector += sectors
    return b"VER2" + struct.pack("<I", len(names)) + entries + b"\0" * (first_sector * 2048 - header) + blobs
```

- [ ] **Step 3: Escribir las pruebas del lector de trozos y del IMG (deben fallar)**

`tools/tests/test_rwchunk.py`:

```python
import struct
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import rwbuild as b  # noqa: E402
from cjconvert import rwchunk as rw  # noqa: E402


class ChunkTests(unittest.TestCase):
    def test_walks_nested_chunks(self):
        data = b.chunk(rw.CLUMP, b.struct_chunk(b"abcd") + b.extension(b.chunk(0x11E, b"xy")))
        clump = rw.first(data, rw.root(data), rw.CLUMP)
        self.assertEqual([c.type for c in rw.iter_chunks(data, clump.start, clump.end)], [rw.STRUCT, rw.EXTENSION])
        ext = rw.first(data, clump, rw.EXTENSION)
        self.assertEqual(rw.first(data, ext, rw.HANIM).size, 2)
        self.assertEqual(len(rw.all_of(data, clump, rw.STRUCT)), 1)

    def test_zero_padding_after_the_last_chunk_is_harmless(self):
        data = b.chunk(rw.CLUMP, b"") + b"\0" * 2048
        self.assertEqual(rw.first(data, rw.root(data), rw.CLUMP).size, 0)

    def test_missing_chunk_is_a_format_error(self):
        data = b.chunk(rw.CLUMP, b"")
        with self.assertRaises(rw.FormatError):
            rw.first(data, rw.root(data), rw.TEXTURE_DICTIONARY)

    def test_a_chunk_that_runs_past_its_parent_is_rejected(self):
        # Review Focus #1: a size that does not fit must not be followed or looped over
        good = b.chunk(rw.CLUMP, b.chunk(rw.STRUCT, b"abcd"))
        corrupt = bytearray(good)
        struct.pack_into("<I", corrupt, 12 + 4, 9999)  # the inner chunk claims 9999 bytes
        with self.assertRaises(rw.FormatError):
            clump = rw.first(bytes(corrupt), rw.root(bytes(corrupt)), rw.CLUMP)
            list(rw.iter_chunks(bytes(corrupt), clump.start, clump.end))

    def test_a_truncated_file_is_rejected(self):
        data = b.chunk(rw.CLUMP, b"0123456789")
        with self.assertRaises(rw.FormatError):
            rw.first(data[:-4], rw.root(data[:-4]), rw.CLUMP)

    def test_library_version_decodes_the_gta_stamp(self):
        self.assertEqual(rw.library_version(0x1803FFFF), 0x36003)
        self.assertGreaterEqual(rw.library_version(0x1803FFFF), 0x34000)
        self.assertEqual(rw.library_version(0x0310), 0x31000)  # old-style stamps (upper 16 bits zero) hold the version directly


if __name__ == "__main__":
    unittest.main()
```

`tools/tests/test_img.py`:

```python
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import rwbuild as b  # noqa: E402
from cjconvert.img import ImgArchive  # noqa: E402
from cjconvert.rwchunk import FormatError  # noqa: E402


def write(tmp, name, data):
    path = Path(tmp) / name
    path.write_bytes(data)
    return path


class ImgTests(unittest.TestCase):
    def test_reads_files_by_case_insensitive_name(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = write(tmp, "a.img", b.img({"Player.DFF": b"hello", "head.txd": b"x" * 5000}))
            img = ImgArchive(path)
            self.assertEqual(img.names(), ["head.txd", "player.dff"])
            self.assertIn("PLAYER.dff", img)
            self.assertTrue(img.read("player.dff").startswith(b"hello"))
            self.assertEqual(len(img.read("head.txd")), 3 * 2048)  # whole sectors, as the game stores them

    def test_missing_file_names_the_archive(self):
        with tempfile.TemporaryDirectory() as tmp:
            img = ImgArchive(write(tmp, "a.img", b.img({"a.dff": b"1"})))
            with self.assertRaises(KeyError) as ctx:
                img.read("nope.dff")
            self.assertIn("nope.dff", str(ctx.exception))
            self.assertIn("a.img", str(ctx.exception))

    def test_rejects_other_formats_and_corruption(self):
        # Review Focus #1
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(FormatError):
                ImgArchive(write(tmp, "v1.img", b"VER1" + b"\0" * 100))
            with self.assertRaises(FormatError):
                ImgArchive(write(tmp, "short.img", b"VER2"))
            huge = b"VER2" + struct.pack("<I", 0x7FFFFFFF)
            with self.assertRaises(FormatError):
                ImgArchive(write(tmp, "huge.img", huge))
            truncated = b.img({"a.dff": b"1", "b.dff": b"2"})[:8 + 32 + 10]  # the directory is cut short
            with self.assertRaises(FormatError):
                ImgArchive(write(tmp, "cut.img", truncated))

    def test_a_file_whose_data_is_missing_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            data = b.img({"a.dff": b"x" * 100})
            img = ImgArchive(write(tmp, "t.img", data[:2048]))  # the sector with the data was cut off
            with self.assertRaises(FormatError):
                img.read("a.dff")


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 4: Ejecutar y comprobar que fallan**

Run: `cd ./tools && python3 -m unittest discover -s tests 2>&1 | tail -6`
Expected: errores `ModuleNotFoundError: No module named 'cjconvert'`.

- [ ] **Step 5: Implementar `rwchunk.py` y `img.py`**

`tools/cjconvert/__init__.py`: archivo vacío.

`tools/cjconvert/rwchunk.py`:

```python
"""Minimal reader for RenderWare chunk streams (the container of DFF and TXD files)."""
import struct
from dataclasses import dataclass

STRUCT = 0x01
STRING = 0x02
EXTENSION = 0x03
TEXTURE_NATIVE = 0x15
TEXTURE_DICTIONARY = 0x16
FRAME_LIST = 0x0E
GEOMETRY = 0x0F
CLUMP = 0x10
GEOMETRY_LIST = 0x1A
SKIN = 0x116
HANIM = 0x11E


class FormatError(ValueError):
    """A file does not have the layout the converter expects."""


@dataclass(frozen=True)
class Chunk:
    type: int
    size: int
    version: int  # the raw library stamp; see library_version()
    start: int  # offset of the payload (the 12-byte header sits before it)

    @property
    def end(self):
        return self.start + self.size


def root(buf):
    """A pseudo-chunk that spans the whole buffer, to search the top level with first()/all_of()."""
    return Chunk(0, len(buf), 0, 0)


def iter_chunks(buf, start, end):
    pos = start
    while pos + 12 <= end:
        type_id, size, version = struct.unpack_from("<III", buf, pos)
        if pos + 12 + size > end:
            raise FormatError(f"chunk {type_id:#x} at offset {pos} claims {size} bytes but only {end - pos - 12} are left")
        yield Chunk(type_id, size, version, pos + 12)
        pos += 12 + size


def all_of(buf, parent, type_id):
    return [c for c in iter_chunks(buf, parent.start, parent.end) if c.type == type_id]


def first(buf, parent, type_id):
    for c in iter_chunks(buf, parent.start, parent.end):
        if c.type == type_id:
            return c
    raise FormatError(f"no chunk {type_id:#x} inside the chunk at offset {max(parent.start - 12, 0)}")


def library_version(raw):
    """Decode a chunk's library stamp (0x1803FFFF is RenderWare 3.6.0.3, which is 0x36003)."""
    if raw & 0xFFFF0000:
        return ((raw >> 14) & 0x3FF00) + 0x30000 | ((raw >> 16) & 0x3F)
    return raw << 8
```

`tools/cjconvert/img.py`:

```python
"""Reader for GTA San Andreas IMG archives (version 2)."""
import struct
from pathlib import Path

from .rwchunk import FormatError

SECTOR = 2048
MAX_ENTRIES = 100_000


class ImgArchive:
    def __init__(self, path):
        self.path = Path(path)
        with open(self.path, "rb") as f:
            head = f.read(8)
            if len(head) < 8 or head[:4] != b"VER2":
                raise FormatError(f"{self.path.name} is not a VER2 IMG archive")
            count = struct.unpack_from("<I", head, 4)[0]
            if count > MAX_ENTRIES:
                raise FormatError(f"{self.path.name} claims {count} entries")
            raw = f.read(count * 32)
        if len(raw) != count * 32:
            raise FormatError(f"{self.path.name} has a truncated directory")
        self._entries = {}
        for i in range(count):
            offset, streaming, archive, name = struct.unpack_from("<IHH24s", raw, i * 32)
            key = name.split(b"\0")[0].decode("ascii", "replace").lower()
            self._entries[key] = (offset * SECTOR, (streaming or archive) * SECTOR)

    def names(self):
        return sorted(self._entries)

    def __contains__(self, name):
        return name.lower() in self._entries

    def read(self, name):
        try:
            offset, size = self._entries[name.lower()]
        except KeyError:
            raise KeyError(f"{name} is not in {self.path.name}") from None
        with open(self.path, "rb") as f:
            f.seek(offset)
            data = f.read(size)
        if len(data) != size:
            raise FormatError(f"{name} is cut off inside {self.path.name}")
        return data
```

- [ ] **Step 6: Ejecutar y comprobar que pasan**

Run: `cd ./tools && python3 -m unittest discover -s tests 2>&1 | tail -4`
Expected: `Ran 10 tests ... OK`.

- [ ] **Step 7: Commit**

Run: `cd . && git add -A && git commit -q -m "M1b: correct the M1 design with what the real files show; RenderWare chunk reader, IMG reader and synthetic test builders

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>" && git log --oneline | head -1`

---

### Task 2: Diccionarios de texturas (TXD) y codificador PNG (`txd.py`)

**Files:**
- Create: `tools/cjconvert/txd.py`
- Test: `tools/tests/test_txd.py`

**Interfaces:**
- Consumes: `rwchunk` (Task 1) y `tests/rwbuild.py` (`txd`, `texture_native`).
- Produces (`cjconvert.txd`): `class UnsupportedTexture(FormatError)`; `@dataclass(frozen=True) class Texture(name, width, height, rgba)` (`rgba` son `width*height*4` bytes, fila superior primero); `parse_txd(data) -> dict[str, Texture]`; `encode_png(width, height, rgba) -> bytes`.

- [ ] **Step 1: Escribir la prueba que falla**

`tools/tests/test_txd.py`:

```python
import struct
import sys
import unittest
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import rwbuild as b  # noqa: E402
from cjconvert import txd  # noqa: E402
from cjconvert.rwchunk import FormatError  # noqa: E402

# a 2x1 image: one red pixel and one green pixel, stored as BGRA like Direct3D does
BGRA = bytes([0, 0, 255, 77, 0, 255, 0, 88])


def decode_png(png):
    """Tiny PNG decoder for the filter-0 images encode_png writes."""
    assert png[:8] == b"\x89PNG\r\n\x1a\n"
    pos, idat, size = 8, b"", None
    while pos < len(png):
        length, kind = struct.unpack_from(">I4s", png, pos)
        body = png[pos + 8:pos + 8 + length]
        assert struct.unpack_from(">I", png, pos + 8 + length)[0] == zlib.crc32(kind + body) & 0xFFFFFFFF
        if kind == b"IHDR":
            size = struct.unpack(">IIBBBBB", body)
        elif kind == b"IDAT":
            idat += body
        pos += 12 + length
    w, h = size[0], size[1]
    raw = zlib.decompress(idat)
    rows = [raw[y * (w * 4 + 1) + 1:(y + 1) * (w * 4 + 1)] for y in range(h)]
    assert all(raw[y * (w * 4 + 1)] == 0 for y in range(h))
    return w, h, b"".join(rows)


class TxdTests(unittest.TestCase):
    def test_converts_bgra_to_rgba(self):
        data = b.txd(b.texture_native("skin", 2, 1, BGRA, alpha=True))
        t = txd.parse_txd(data)["skin"]
        self.assertEqual((t.name, t.width, t.height), ("skin", 2, 1))
        self.assertEqual(t.rgba, bytes([255, 0, 0, 77, 0, 255, 0, 88]))

    def test_without_the_alpha_flag_the_alpha_is_opaque(self):
        data = b.txd(b.texture_native("skin", 2, 1, BGRA, alpha=False))
        self.assertEqual(txd.parse_txd(data)["skin"].rgba, bytes([255, 0, 0, 255, 0, 255, 0, 255]))

    def test_reads_every_texture_of_the_dictionary(self):
        data = b.txd(b.texture_native("a", 2, 1, BGRA), b.texture_native("b", 1, 1, BGRA[:4]))
        self.assertEqual(sorted(txd.parse_txd(data)), ["a", "b"])

    def test_unsupported_formats_fail_clearly(self):
        # Review Focus #3: compressed, paletted and 16-bit textures are not guessed at
        cases = {
            "compressed": b.texture_native("t", 2, 1, BGRA, flags=0x08),
            "paletted": b.texture_native("t", 2, 1, BGRA, raster=0x2600),
            "16 bit": b.texture_native("t", 2, 1, BGRA, depth=16),
            "wrong d3d format": b.texture_native("t", 2, 1, BGRA, d3d=827611204),  # 'DXT1'
            "other platform": b.texture_native("t", 2, 1, BGRA, platform=6),
        }
        for label, native in cases.items():
            with self.subTest(label):
                with self.assertRaises(txd.UnsupportedTexture) as ctx:
                    txd.parse_txd(b.txd(native))
                self.assertIn("t", str(ctx.exception))

    def test_corrupt_sizes_are_rejected(self):
        # Review Focus #1
        with self.assertRaises(FormatError):
            txd.parse_txd(b.txd(b.texture_native("t", 2, 1, BGRA[:6])))  # the pixel data is shorter than 2x1x4
        with self.assertRaises(FormatError):
            txd.parse_txd(b.txd(b.texture_native("t", 0, 1, b"")))
        with self.assertRaises(FormatError):
            txd.parse_txd(b"\0" * 64)  # no dictionary at all

    def test_png_roundtrip(self):
        rgba = bytes(range(16))  # a 2x2 image
        png = txd.encode_png(2, 2, rgba)
        self.assertEqual(decode_png(png), (2, 2, rgba))
        self.assertEqual(txd.encode_png(2, 2, rgba), png)  # deterministic


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Ejecutar y comprobar que falla**

Run: `cd ./tools && python3 -m unittest tests.test_txd 2>&1 | tail -4`
Expected: `ModuleNotFoundError` o `ImportError` por `cjconvert.txd`.

- [ ] **Step 3: Implementar `txd.py`**

`tools/cjconvert/txd.py`:

```python
"""Texture dictionaries (TXD): the 32-bit Direct3D 9 textures GTA San Andreas uses for CJ, and a PNG encoder."""
import struct
import zlib
from dataclasses import dataclass

from .rwchunk import STRUCT, TEXTURE_DICTIONARY, TEXTURE_NATIVE, FormatError, all_of, first, root

D3DFMT_A8R8G8B8 = 21
D3DFMT_X8R8G8B8 = 22
PLATFORM_D3D8 = 8
PLATFORM_D3D9 = 9
MAX_SIZE = 4096


class UnsupportedTexture(FormatError):
    """A texture is in a format the converter does not decode (compressed, paletted, 16 bit...)."""


@dataclass(frozen=True)
class Texture:
    name: str
    width: int
    height: int
    rgba: bytes  # width * height * 4 bytes, top row first


def parse_txd(data):
    dictionary = first(data, root(data), TEXTURE_DICTIONARY)
    textures = {}
    for native in all_of(data, dictionary, TEXTURE_NATIVE):
        st = first(data, native, STRUCT)
        s = st.start
        if st.size < 92:
            raise FormatError("a texture is too short to hold its header")
        platform = struct.unpack_from("<I", data, s)[0]
        name = data[s + 8:s + 40].split(b"\0")[0].decode("ascii", "replace")
        raster, d3d = struct.unpack_from("<II", data, s + 72)
        width, height = struct.unpack_from("<HH", data, s + 80)
        depth, _mips, _raster_type, flags = struct.unpack_from("<BBBB", data, s + 84)
        if not (1 <= width <= MAX_SIZE and 1 <= height <= MAX_SIZE):
            raise FormatError(f"texture {name!r} has an impossible size {width}x{height}")
        if platform not in (PLATFORM_D3D8, PLATFORM_D3D9):
            raise UnsupportedTexture(f"texture {name!r} is for platform {platform}, not Direct3D")
        if flags & 0x08:
            raise UnsupportedTexture(f"texture {name!r} is compressed (DXT); only 32-bit textures are supported")
        if raster & 0x6000:
            raise UnsupportedTexture(f"texture {name!r} uses a palette; only 32-bit textures are supported")
        if depth != 32 or d3d not in (D3DFMT_A8R8G8B8, D3DFMT_X8R8G8B8):
            raise UnsupportedTexture(f"texture {name!r} is {depth}-bit (d3d format {d3d}); only 32-bit textures are supported")
        size = struct.unpack_from("<I", data, s + 88)[0]
        if size != width * height * 4 or s + 92 + size > st.end:
            raise FormatError(f"texture {name!r} has {size} bytes of pixels, expected {width * height * 4}")
        pixels = data[s + 92:s + 92 + size]
        rgba = bytearray(size)
        rgba[0::4] = pixels[2::4]  # Direct3D stores BGRA
        rgba[1::4] = pixels[1::4]
        rgba[2::4] = pixels[0::4]
        rgba[3::4] = pixels[3::4] if flags & 0x01 else b"\xff" * (width * height)
        textures[name] = Texture(name, width, height, bytes(rgba))
    return textures


def encode_png(width, height, rgba):
    """An 8-bit RGBA PNG with no filtering; deterministic."""
    raw = b"".join(b"\x00" + rgba[y * width * 4:(y + 1) * width * 4] for y in range(height))

    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
```

- [ ] **Step 4: Ejecutar y comprobar que pasa**

Run: `cd ./tools && python3 -m unittest discover -s tests 2>&1 | tail -4`
Expected: `Ran 16 tests ... OK`.

- [ ] **Step 5: Comprobar con una mutación que la prueba discrimina**

Run: `cd ./tools && rm -rf /tmp/mutt && mkdir /tmp/mutt && cp -r cjconvert tests /tmp/mutt/ && find /tmp/mutt -name __pycache__ -exec rm -rf {} + && sed -i 's/rgba\[0::4\] = pixels\[2::4\]/rgba[0::4] = pixels[0::4]/' /tmp/mutt/cjconvert/txd.py && cd /tmp/mutt && python3 -B -m unittest tests.test_txd 2>&1 | tail -3`
Expected: `FAILED` (sin el intercambio de canales azul/rojo, la prueba de color falla).

- [ ] **Step 6: Commit**

Run: `cd . && git add -A && git commit -q -m "M1b: TXD reader for 32-bit Direct3D textures and a PNG encoder

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>" && git log --oneline | head -1`

---

### Task 3: Esqueleto y piezas del cuerpo (`dff.py`)

**Files:**
- Create: `tools/cjconvert/dff.py`
- Test: `tools/tests/test_dff.py`

**Interfaces:**
- Consumes: `rwchunk` (Task 1) y `tests/rwbuild.py` (`dff`, `geometry`, `skin`).
- Produces (`cjconvert.dff`): `class UnsupportedGeometry(FormatError)`; `@dataclass(frozen=True) class Skeleton(node_ids, inverse_bind)` (`node_ids` es una tupla de `int` en el orden de índice de nodo; `inverse_bind[i]` son los 16 floats de la matriz `RwMatrix` del hueso `i`); `@dataclass(frozen=True) class Part(positions, normals, uvs, triangles, bone_indices, weights, hierarchy_ids)` (`positions`/`normals` listas de 3-tuplas, `uvs` de 2-tuplas, `triangles` de `(v1, v2, v3)`, `bone_indices` y `weights` de 4-tuplas por vértice, `hierarchy_ids` el `nodeID` de cada índice de hueso de la pieza); `parse_skeleton(data) -> Skeleton`; `parse_part(data) -> Part`.

- [ ] **Step 1: Escribir la prueba que falla**

`tools/tests/test_dff.py`:

```python
import struct
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import rwbuild as b  # noqa: E402
from cjconvert import dff  # noqa: E402
from cjconvert.rwchunk import FormatError  # noqa: E402

POS = [(0.0, 0.0, 0.0), (1.0, 0.0, 0.0), (0.0, 1.0, 0.5), (1.0, 1.0, 0.5)]
NRM = [(0.0, 0.0, 1.0)] * 4
UVS = [(0.0, 0.0), (1.0, 0.0), (0.0, 1.0), (1.0, 1.0)]
TRIS = [(0, 1, 2), (2, 1, 3)]
HIER = [0, 1, 5, 303]  # the part's own hierarchy, with a node (303) the skeleton does not have
INDICES = [(0, 0, 0, 0), (1, 2, 0, 0), (2, 0, 0, 0), (1, 0, 0, 0)]
WEIGHTS = [(1.0, 0.0, 0.0, 0.0), (0.25, 0.75, 0.0, 0.0), (1.0, 0.0, 0.0, 0.0), (1.0, 0.0, 0.0, 0.0)]
IDENT = (1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)


def part_dff(**kw):
    sk = b.skin(len(HIER), [0, 1, 2], INDICES, WEIGHTS, [IDENT] * len(HIER))
    return b.dff(HIER, b.geometry(POS, NRM, UVS, TRIS, skin=sk, **kw))


class PartTests(unittest.TestCase):
    def test_reads_geometry_skin_and_hierarchy(self):
        part = dff.parse_part(part_dff())
        self.assertEqual(part.positions, POS)
        self.assertEqual(part.normals, NRM)
        self.assertEqual(part.uvs, UVS)
        self.assertEqual(part.triangles, TRIS)  # (vertex1, vertex2, vertex3), undoing the file's stored order
        self.assertEqual(part.bone_indices, INDICES)
        self.assertEqual([tuple(round(x, 4) for x in w) for w in part.weights], WEIGHTS)
        self.assertEqual(part.hierarchy_ids, tuple(HIER))

    def test_prelit_colours_are_skipped(self):
        part = dff.parse_part(part_dff(prelit=True))
        self.assertEqual(part.positions, POS)
        self.assertEqual(part.triangles, TRIS)

    def test_tri_strip_geometry_is_unsupported(self):
        # Review Focus #3
        with self.assertRaises(dff.UnsupportedGeometry):
            dff.parse_part(part_dff(flags=0x37))

    def test_a_part_without_skin_or_hierarchy_is_rejected(self):
        no_skin = b.dff(HIER, b.geometry(POS, NRM, UVS, TRIS))
        with self.assertRaises(FormatError):
            dff.parse_part(no_skin)
        sk = b.skin(len(HIER), [0], INDICES, WEIGHTS, [IDENT] * len(HIER))
        no_hierarchy = b.dff([], b.geometry(POS, NRM, UVS, TRIS, skin=sk))
        with self.assertRaises(FormatError):
            dff.parse_part(no_hierarchy)

    def test_corrupt_or_truncated_files_are_rejected(self):
        # Review Focus #1
        data = part_dff()
        for cut in (20, 200, len(data) // 2):
            with self.subTest(cut=cut):
                with self.assertRaises(FormatError):
                    dff.parse_part(data[:cut])
        with self.assertRaises(FormatError):
            dff.parse_part(b"\0" * 100)

    def test_a_skin_that_does_not_fit_in_its_chunk_is_rejected(self):
        sk = b.skin(len(HIER), [0, 1, 2], INDICES[:2], WEIGHTS[:2], [IDENT] * len(HIER))  # two vertices too few
        with self.assertRaises(FormatError):
            dff.parse_part(b.dff(HIER, b.geometry(POS, NRM, UVS, TRIS, skin=sk)))


class SkeletonTests(unittest.TestCase):
    def test_reads_node_ids_in_index_order_and_the_inverse_bind_matrices(self):
        ids = [0, 1, 5]
        mats = [tuple(float(i + 10 * k) for k in range(16)) for i in range(3)]
        stub = b.geometry([(0, 0, 0)] * 3, [(0, 0, 1)] * 3, [(0, 0)] * 3, [(0, 1, 2)],
                          skin=b.skin(3, [0], [(0, 0, 0, 0)] * 3, [(1, 0, 0, 0)] * 3, mats))
        sk = dff.parse_skeleton(b.dff(ids, stub))
        self.assertEqual(sk.node_ids, (0, 1, 5))
        self.assertEqual(len(sk.inverse_bind), 3)
        self.assertEqual(sk.inverse_bind[2], mats[2])

    def test_the_matrix_count_must_match_the_node_count(self):
        stub = b.geometry([(0, 0, 0)] * 3, [(0, 0, 1)] * 3, [(0, 0)] * 3, [(0, 1, 2)],
                          skin=b.skin(2, [0], [(0, 0, 0, 0)] * 3, [(1, 0, 0, 0)] * 3, [IDENT] * 2))
        with self.assertRaises(FormatError):
            dff.parse_skeleton(b.dff([0, 1, 5], stub))


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Ejecutar y comprobar que falla**

Run: `cd ./tools && python3 -m unittest tests.test_dff 2>&1 | tail -4`
Expected: `ImportError` por `cjconvert.dff`.

- [ ] **Step 3: Implementar `dff.py`**

`tools/cjconvert/dff.py`:

```python
"""DFF models: the skeleton of player.dff and the body parts (head, torso, hands, legs, feet) of player.img."""
import struct
from dataclasses import dataclass

from .rwchunk import (CLUMP, EXTENSION, FRAME_LIST, GEOMETRY, GEOMETRY_LIST, HANIM, SKIN, STRUCT, FormatError, all_of,
                      first, iter_chunks, library_version, root)

MAX_VERTICES = 65535


class UnsupportedGeometry(FormatError):
    """A geometry uses a layout the converter does not read (tri-strips, old RenderWare versions)."""


@dataclass(frozen=True)
class Skeleton:
    node_ids: tuple       # nodeID of every skeleton node, in node-index order
    inverse_bind: tuple   # per node, the 16 floats of its RwMatrix (right, up, at, pos with a pad float after each row)


@dataclass(frozen=True)
class Part:
    positions: list
    normals: list
    uvs: list
    triangles: list       # (v1, v2, v3), front faces counter-clockwise
    bone_indices: list    # 4 per vertex, indices into the part's own hierarchy
    weights: list         # 4 per vertex
    hierarchy_ids: tuple  # nodeID of each of the part's hierarchy nodes


def _hierarchy_ids(data, clump):
    frame_list = first(data, clump, FRAME_LIST)
    for ext in all_of(data, frame_list, EXTENSION):
        for c in iter_chunks(data, ext.start, ext.end):
            if c.type == HANIM and c.size >= 12:
                _version, _node_id, count = struct.unpack_from("<III", data, c.start)
                if count > 0:
                    if c.size < 20 + 12 * count:
                        raise FormatError("a skeleton hierarchy is cut off")
                    return tuple(struct.unpack_from("<i", data, c.start + 20 + 12 * i)[0] for i in range(count))
    raise FormatError("the model has no skeleton hierarchy")


def _only_geometry(data, clump):
    geometries = all_of(data, first(data, clump, GEOMETRY_LIST), GEOMETRY)
    if len(geometries) != 1:
        raise FormatError(f"expected one geometry, found {len(geometries)}")
    return geometries[0]


def _read_geometry(data, geometry):
    st = first(data, geometry, STRUCT)
    if library_version(geometry.version) < 0x34000:
        raise UnsupportedGeometry("geometry from a RenderWare older than 3.4 is not supported")
    if st.size < 16:
        raise FormatError("a geometry is too short to hold its header")
    fmt, tri_count, vert_count, morph_count = struct.unpack_from("<IIII", data, st.start)
    flags = fmt & 0xFFFF
    uv_sets = (fmt >> 16) & 0xFF
    if flags & 0x01:
        raise UnsupportedGeometry("tri-strip geometry is not supported")
    if morph_count != 1:
        raise UnsupportedGeometry(f"{morph_count} morph targets; only one is supported")
    if not 0 < vert_count <= MAX_VERTICES:
        raise FormatError(f"a geometry has {vert_count} vertices")
    if uv_sets == 0:
        uv_sets = 1 if flags & 0x04 else (2 if flags & 0x80 else 0)
    need = 16 + (vert_count * 4 if flags & 0x08 else 0) + vert_count * 8 * uv_sets + tri_count * 8 + 24 + vert_count * 24
    if st.size < need:
        raise FormatError("a geometry is shorter than its header says")
    o = st.start + 16
    if flags & 0x08:
        o += vert_count * 4  # prelit vertex colours
    uvs = [struct.unpack_from("<2f", data, o + i * 8) for i in range(vert_count)] if uv_sets else []
    o += vert_count * 8 * uv_sets
    raw_tris = [struct.unpack_from("<4H", data, o + i * 8) for i in range(tri_count)]
    o += tri_count * 8
    o += 16  # bounding sphere
    has_vertices, has_normals = struct.unpack_from("<II", data, o)
    o += 8
    if not has_vertices:
        raise FormatError("a geometry has no vertices")
    positions = [struct.unpack_from("<3f", data, o + i * 12) for i in range(vert_count)]
    o += vert_count * 12
    normals = [struct.unpack_from("<3f", data, o + i * 12) for i in range(vert_count)] if has_normals else []
    # The file stores each triangle as vertex2, vertex1, material, vertex3.
    triangles = []
    for v2, v1, _material, v3 in raw_tris:
        if max(v1, v2, v3) >= vert_count:
            raise FormatError("a triangle points past the last vertex")
        triangles.append((v1, v2, v3))
    return positions, normals, uvs, triangles, vert_count


def _read_skin(data, geometry, vert_count):
    ext = first(data, geometry, EXTENSION)
    skin = first(data, ext, SKIN)
    if skin.size < 4:
        raise FormatError("a skin is too short")
    bones, used, _max_weights, _pad = struct.unpack_from("<BBBB", data, skin.start)
    o = skin.start + 4 + used
    if o + vert_count * 20 + bones * 64 > skin.end:
        raise FormatError("a skin does not fit in its chunk")
    indices = [tuple(data[o + i * 4:o + i * 4 + 4]) for i in range(vert_count)]
    o += vert_count * 4
    weights = [struct.unpack_from("<4f", data, o + i * 16) for i in range(vert_count)]
    o += vert_count * 16
    matrices = tuple(struct.unpack_from("<16f", data, o + 64 * i) for i in range(bones))
    return bones, indices, weights, matrices


def parse_skeleton(data):
    clump = first(data, root(data), CLUMP)
    ids = _hierarchy_ids(data, clump)
    geometry = _only_geometry(data, clump)
    *_, vert_count = _read_geometry(data, geometry)
    bones, _indices, _weights, matrices = _read_skin(data, geometry, vert_count)
    if bones != len(ids):
        raise FormatError(f"the skeleton has {len(ids)} nodes but its skin has {bones} matrices")
    return Skeleton(ids, matrices)


def parse_part(data):
    clump = first(data, root(data), CLUMP)
    ids = _hierarchy_ids(data, clump)
    geometry = _only_geometry(data, clump)
    positions, normals, uvs, triangles, vert_count = _read_geometry(data, geometry)
    _bones, indices, weights, _matrices = _read_skin(data, geometry, vert_count)
    return Part(positions, normals, uvs, triangles, indices, weights, ids)
```

- [ ] **Step 4: Ejecutar y comprobar que pasa**

Run: `cd ./tools && python3 -m unittest discover -s tests 2>&1 | tail -4`
Expected: `Ran 24 tests ... OK`.

- [ ] **Step 5: Comprobar con una mutación que la prueba discrimina**

Run: `cd ./tools && rm -rf /tmp/mutd && mkdir /tmp/mutd && cp -r cjconvert tests /tmp/mutd/ && find /tmp/mutd -name __pycache__ -exec rm -rf {} + && sed -i 's/triangles.append((v1, v2, v3))/triangles.append((v2, v1, v3))/' /tmp/mutd/cjconvert/dff.py && cd /tmp/mutd && python3 -B -m unittest tests.test_dff 2>&1 | tail -3`
Expected: `FAILED` (con el orden de vértices cambiado, los triángulos salen del revés).

- [ ] **Step 6: Commit**

Run: `cd . && git add -A && git commit -q -m "M1b: DFF reader for the skeleton and the body parts

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>" && git log --oneline | head -1`

---

### Task 4: Ensamblado de CJ: equipo por defecto y remapeo de huesos (`assemble.py`)

**Files:**
- Create: `tools/cjconvert/assemble.py`
- Test: `tools/tests/test_assemble.py`

**Interfaces:**
- Consumes: `ImgArchive` (Task 1), `parse_txd` y `Texture` (Task 2), `parse_skeleton`, `parse_part`, `Skeleton`, `Part` (Task 3), `tests/rwbuild.py`.
- Produces (`cjconvert.assemble`): `class ConversionError(Exception)`; `@dataclass(frozen=True) class PartSpec(name, dff, txd, texture)`; `OUTFIT` (la tupla de cinco `PartSpec` del CJ base); `@dataclass(frozen=True) class MeshPart(name, positions, normals, uvs, triangles, joints, weights, texture)` (`joints` son 4 índices del esqueleto por vértice, `texture` es la clave del diccionario `Model.textures`); `@dataclass(frozen=True) class Model(skeleton, parts, textures)` (`textures: dict[str, Texture]`); `remap_part(name, part, skeleton) -> (joints, weights)`; `load_model(gta_dir, outfit=OUTFIT) -> Model`.

- [ ] **Step 1: Escribir la prueba que falla**

`tools/tests/test_assemble.py`:

```python
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import rwbuild as b  # noqa: E402
from cjconvert import assemble  # noqa: E402
from cjconvert.dff import Part, Skeleton  # noqa: E402

IDENT = (1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)
SKELETON_IDS = [0, 5, 1]  # deliberately not in the order of the part's hierarchy
BGRA = bytes([0, 0, 255, 255, 0, 255, 0, 255])


def make_part(indices, weights, hierarchy=(0, 1, 5, 303)):
    n = len(indices)
    return Part([(0.0, 0.0, 0.0)] * n, [(0.0, 0.0, 1.0)] * n, [(0.0, 0.0)] * n, [(0, 1, 2)] * (1 if n >= 3 else 0),
                indices, weights, tuple(hierarchy))


SKELETON = Skeleton(tuple(SKELETON_IDS), (IDENT, IDENT, IDENT))


class RemapTests(unittest.TestCase):
    def test_remaps_part_bones_to_skeleton_joints_by_node_id(self):
        # part bone 2 is node 5, which is joint 1 of the skeleton; part bone 1 is node 1, joint 2
        part = make_part([(2, 0, 0, 0), (1, 2, 0, 0), (0, 0, 0, 0)],
                         [(1.0, 0.0, 0.0, 0.0), (0.25, 0.75, 0.0, 0.0), (1.0, 0.0, 0.0, 0.0)])
        joints, weights = assemble.remap_part("t", part, SKELETON)
        self.assertEqual(joints[0], (1, 0, 0, 0))
        self.assertEqual(joints[1][:2], (2, 1))
        self.assertEqual(joints[2], (0, 0, 0, 0))
        self.assertAlmostEqual(sum(weights[1]), 1.0)
        self.assertEqual(weights[1][:2], (0.25, 0.75))

    def test_zero_weight_slots_point_at_joint_zero_whatever_their_index(self):
        part = make_part([(1, 3, 3, 3)], [(1.0, 0.0, 0.0, 0.0)])  # bone 3 is node 303, which the skeleton lacks, but unused
        joints, _ = assemble.remap_part("t", part, SKELETON)
        self.assertEqual(joints[0], (2, 0, 0, 0))

    def test_a_weight_on_a_node_the_skeleton_lacks_is_an_error(self):
        # Review Focus #4: never guess a bone
        part = make_part([(3, 0, 0, 0)], [(1.0, 0.0, 0.0, 0.0)])
        with self.assertRaises(assemble.ConversionError) as ctx:
            assemble.remap_part("legs", part, SKELETON)
        self.assertIn("303", str(ctx.exception))
        self.assertIn("legs", str(ctx.exception))

    def test_a_bone_index_past_the_part_hierarchy_is_an_error(self):
        part = make_part([(9, 0, 0, 0)], [(1.0, 0.0, 0.0, 0.0)])
        with self.assertRaises(assemble.ConversionError):
            assemble.remap_part("t", part, SKELETON)

    def test_weights_that_do_not_sum_to_one_are_an_error(self):
        part = make_part([(0, 1, 0, 0)], [(0.5, 0.2, 0.0, 0.0)])
        with self.assertRaises(assemble.ConversionError):
            assemble.remap_part("t", part, SKELETON)

    def test_weights_that_are_slightly_off_are_normalised(self):
        part = make_part([(0, 1, 0, 0)], [(0.504, 0.5, 0.0, 0.0)])
        _, weights = assemble.remap_part("t", part, SKELETON)
        self.assertAlmostEqual(sum(weights[0]), 1.0, places=6)


def make_install(root, outfit_files=None):
    """models/gta3.img with the skeleton and models/player.img with parts and textures, all synthetic."""
    models = Path(root) / "models"
    models.mkdir(parents=True)
    stub = b.geometry([(0, 0, 0)] * 3, [(0, 0, 1)] * 3, [(0, 0)] * 3, [(0, 1, 2)],
                      skin=b.skin(3, [0], [(0, 0, 0, 0)] * 3, [(1, 0, 0, 0)] * 3, [IDENT] * 3))
    (models / "gta3.img").write_bytes(b.img({"player.dff": b.dff(SKELETON_IDS, stub)}))
    files = {}
    for name in ("torso", "hands"):
        sk = b.skin(4, [0, 1], [(0, 0, 0, 0), (1, 2, 0, 0), (2, 0, 0, 0)],
                    [(1.0, 0.0, 0.0, 0.0), (0.5, 0.5, 0.0, 0.0), (1.0, 0.0, 0.0, 0.0)], [IDENT] * 4)
        files[name + ".dff"] = b.dff([0, 1, 5, 303], b.geometry(
            [(0, 0, 0), (1, 0, 0), (0, 1, 0)], [(0, 0, 1)] * 3, [(0, 0), (1, 0), (0, 1)], [(0, 1, 2)], skin=sk))
    files["player_torso.txd"] = b.txd(b.texture_native("torso", 2, 1, BGRA), b.texture_native("torso_fat", 2, 1, BGRA))
    (models / "player.img").write_bytes(b.img(files))
    return Path(root)


OUTFIT = (assemble.PartSpec("torso", "torso.dff", "player_torso.txd", "torso"),
          assemble.PartSpec("hands", "hands.dff", "player_torso.txd", "torso"))


class LoadModelTests(unittest.TestCase):
    def test_assembles_the_parts_on_the_shared_skeleton(self):
        with tempfile.TemporaryDirectory() as tmp:
            model = assemble.load_model(make_install(tmp), OUTFIT)
        self.assertEqual(model.skeleton.node_ids, tuple(SKELETON_IDS))
        self.assertEqual([p.name for p in model.parts], ["torso", "hands"])
        self.assertEqual(model.parts[0].joints[1][:2], (2, 1))  # part bones 1,2 are nodes 1,5 -> joints 2,1
        self.assertEqual(model.parts[0].triangles, [(0, 1, 2)])
        self.assertEqual(list(model.textures), ["player_torso:torso"])  # torso and hands share one texture
        self.assertEqual({p.texture for p in model.parts}, {"player_torso:torso"})

    def test_the_default_outfit_is_the_five_body_parts_with_cjs_own_skin(self):
        self.assertEqual([(s.name, s.dff, s.txd, s.texture) for s in assemble.OUTFIT], [
            ("torso", "torso.dff", "player_torso.txd", "torso"),
            ("head", "head.dff", "player_face.txd", "face"),
            ("hands", "hands.dff", "player_torso.txd", "torso"),
            ("legs", "legs.dff", "player_legs.txd", "legs"),
            ("feet", "feet.dff", "player_feet.txd", "foot"),
        ])

    def test_a_missing_game_folder_says_where_to_look(self):
        # Review Focus #2
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(assemble.ConversionError) as ctx:
                assemble.load_model(Path(tmp) / "nowhere", OUTFIT)
        self.assertIn("gta3.img", str(ctx.exception))

    def test_a_missing_part_names_the_file_and_the_archive(self):
        # Review Focus #2
        with tempfile.TemporaryDirectory() as tmp:
            install = make_install(tmp)
            outfit = OUTFIT + (assemble.PartSpec("head", "head.dff", "player_torso.txd", "torso"),)
            with self.assertRaises(assemble.ConversionError) as ctx:
                assemble.load_model(install, outfit)
        self.assertIn("head.dff", str(ctx.exception))
        self.assertIn("player.img", str(ctx.exception))

    def test_a_missing_texture_lists_what_the_dictionary_has(self):
        # Review Focus #2
        with tempfile.TemporaryDirectory() as tmp:
            install = make_install(tmp)
            outfit = (assemble.PartSpec("torso", "torso.dff", "player_torso.txd", "nope"),)
            with self.assertRaises(assemble.ConversionError) as ctx:
                assemble.load_model(install, outfit)
        self.assertIn("nope", str(ctx.exception))
        self.assertIn("torso_fat", str(ctx.exception))

    def test_a_broken_file_is_reported_as_a_conversion_error_naming_it(self):
        # Review Focus #1
        with tempfile.TemporaryDirectory() as tmp:
            install = make_install(tmp)
            (install / "models" / "player.img").write_bytes(b.img({"torso.dff": b"\0" * 64, "hands.dff": b"\0" * 64,
                                                                   "player_torso.txd": b.txd(b.texture_native("torso", 2, 1, BGRA))}))
            with self.assertRaises(assemble.ConversionError) as ctx:
                assemble.load_model(install, OUTFIT)
        self.assertIn("torso.dff", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Ejecutar y comprobar que falla**

Run: `cd ./tools && python3 -m unittest tests.test_assemble 2>&1 | tail -4`
Expected: `ImportError` por `cjconvert.assemble`.

- [ ] **Step 3: Implementar `assemble.py`**

`tools/cjconvert/assemble.py`:

```python
"""Assembles CJ from the skeleton of player.dff and the body parts of player.img."""
from dataclasses import dataclass
from pathlib import Path

from .dff import Skeleton, parse_part, parse_skeleton
from .img import ImgArchive
from .rwchunk import FormatError
from .txd import Texture, parse_txd

WEIGHT_TOLERANCE = 0.01


class ConversionError(Exception):
    """Something is wrong with the user's game files or with the way the model is assembled."""


@dataclass(frozen=True)
class PartSpec:
    name: str
    dff: str
    txd: str
    texture: str


# CJ without clothes: his own face, torso (the atlas also holds the hands), legs (with underwear) and bare feet.
OUTFIT = (
    PartSpec("torso", "torso.dff", "player_torso.txd", "torso"),
    PartSpec("head", "head.dff", "player_face.txd", "face"),
    PartSpec("hands", "hands.dff", "player_torso.txd", "torso"),
    PartSpec("legs", "legs.dff", "player_legs.txd", "legs"),
    PartSpec("feet", "feet.dff", "player_feet.txd", "foot"),
)


@dataclass(frozen=True)
class MeshPart:
    name: str
    positions: list
    normals: list
    uvs: list
    triangles: list
    joints: list    # 4 skeleton joint indices per vertex
    weights: list   # 4 weights per vertex, summing to 1
    texture: str    # key into Model.textures


@dataclass(frozen=True)
class Model:
    skeleton: Skeleton
    parts: list
    textures: dict


def remap_part(name, part, skeleton):
    """Part bone index -> nodeID (through the part's own hierarchy) -> joint index of the same nodeID in the skeleton."""
    joint_of = {node_id: i for i, node_id in enumerate(skeleton.node_ids)}
    joints, weights = [], []
    for v, (bones, ws) in enumerate(zip(part.bone_indices, part.weights)):
        row_joints, row_weights = [], []
        for bone, w in zip(bones, ws):
            if w <= 0.0:
                row_joints.append(0)
                row_weights.append(0.0)
                continue
            if bone >= len(part.hierarchy_ids):
                raise ConversionError(f"{name}: vertex {v} uses bone {bone} but the part only has {len(part.hierarchy_ids)}")
            node_id = part.hierarchy_ids[bone]
            if node_id not in joint_of:
                raise ConversionError(f"{name}: vertex {v} is weighted to bone {node_id}, which the skeleton does not have")
            row_joints.append(joint_of[node_id])
            row_weights.append(w)
        total = sum(row_weights)
        if abs(total - 1.0) > WEIGHT_TOLERANCE:
            raise ConversionError(f"{name}: the weights of vertex {v} add up to {total:.3f}, not 1")
        joints.append(tuple(row_joints))
        weights.append(tuple(w / total for w in row_weights))
    return joints, weights


def load_model(gta_dir, outfit=OUTFIT):
    models = Path(gta_dir) / "models"
    skeleton_img = models / "gta3.img"
    parts_img = models / "player.img"
    for path in (skeleton_img, parts_img):
        if not path.is_file():
            raise ConversionError(f"{path} was not found; point --gta at your GTA San Andreas folder")
    try:
        gta3 = ImgArchive(skeleton_img)
        player = ImgArchive(parts_img)
    except FormatError as e:
        raise ConversionError(str(e)) from e

    def read(img, name):
        try:
            return img.read(name)
        except KeyError as e:
            raise ConversionError(e.args[0]) from e
        except FormatError as e:
            raise ConversionError(str(e)) from e

    def parse(label, fn, data):
        try:
            return fn(data)
        except FormatError as e:
            raise ConversionError(f"{label}: {e}") from e

    skeleton = parse("player.dff", parse_skeleton, read(gta3, "player.dff"))
    textures, parts = {}, []
    for spec in outfit:
        part = parse(spec.dff, parse_part, read(player, spec.dff))
        dictionary = parse(spec.txd, parse_txd, read(player, spec.txd))
        if spec.texture not in dictionary:
            raise ConversionError(f"{spec.txd} has no texture named {spec.texture!r}; it has: {', '.join(sorted(dictionary))}")
        key = f"{spec.txd.rsplit('.', 1)[0]}:{spec.texture}"
        textures[key] = dictionary[spec.texture]
        joints, weights = remap_part(spec.name, part, skeleton)
        parts.append(MeshPart(spec.name, part.positions, part.normals, part.uvs, part.triangles, joints, weights, key))
    return Model(skeleton, parts, textures)
```

- [ ] **Step 4: Ejecutar y comprobar que pasa**

Run: `cd ./tools && python3 -m unittest discover -s tests 2>&1 | tail -4`
Expected: `Ran 36 tests ... OK`.

- [ ] **Step 5: Comprobar con una mutación que la prueba discrimina**

Run: `cd ./tools && rm -rf /tmp/muta && mkdir /tmp/muta && cp -r cjconvert tests /tmp/muta/ && find /tmp/muta -name __pycache__ -exec rm -rf {} + && sed -i 's/node_id = part.hierarchy_ids\[bone\]/node_id = skeleton.node_ids[bone % len(skeleton.node_ids)]/' /tmp/muta/cjconvert/assemble.py && cd /tmp/muta && python3 -B -m unittest tests.test_assemble 2>&1 | tail -3`
Expected: `FAILED` (sin remapear por `nodeID`, la prueba de los huesos falla).

- [ ] **Step 6: Commit**

Run: `cd . && git add -A && git commit -q -m "M1b: assemble CJ from the skeleton and the body parts, remapping bones by node id

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>" && git log --oneline | head -1`

---

### Task 5: Escritor y lector de GLB (`glb.py`)

**Files:**
- Create: `tools/cjconvert/glb.py`
- Test: `tools/tests/test_glb.py`

**Interfaces:**
- Consumes: `Model`, `MeshPart` (Task 4), `Skeleton` (Task 3), `Texture`/`encode_png` (Task 2).
- Produces (`cjconvert.glb`): `mat4_inverse(m) -> list[float]` (matrices de 16 floats en orden de columnas; lanza `ValueError` si es singular); `rw_matrix_to_column_major(m16) -> list[float]` (de las 16 floats de un `RwMatrix`, filas right/up/at/pos con relleno, a una matriz de columnas con `w` = 0,0,0,1); `to_glb(model) -> bytes`; `read_glb(data) -> (gltf: dict, bin: bytes)` (lanza `ValueError` si no es un GLB válido); `read_accessor(gltf, bin, index) -> list` (lista de escalares o de tuplas, según el tipo del accesor).
- Estructura del GLB producido: nodo 0 = malla `CJ` con `skin: 0`; nodos `1..N` = una articulación por hueso (`name` `bone_<nodeID>`, `extras: {"nodeId": <id>}`, `matrix` = inversa de la matriz de enlace inversa); `skins[0].joints = [1..N]`; una malla con una primitiva por pieza (`POSITION`, `NORMAL`, `TEXCOORD_0`, `JOINTS_0` u8, `WEIGHTS_0` f32, índices u16, `material`); un material y una textura por clave de `model.textures`, con la imagen PNG incrustada.

- [ ] **Step 1: Escribir la prueba que falla**

`tools/tests/test_glb.py`:

```python
import struct
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from cjconvert import glb  # noqa: E402
from cjconvert.assemble import MeshPart, Model  # noqa: E402
from cjconvert.dff import Skeleton  # noqa: E402
from cjconvert.txd import Texture  # noqa: E402

# a rigid RwMatrix: right=(0,1,0) up=(-1,0,0) at=(0,0,1), position (1,2,3); a pad float follows each row
RW = (0.0, 1.0, 0.0, 0.0, -1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 2.0, 3.0, 1.0)
RW_IDENT = (1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)


def make_model():
    skeleton = Skeleton((0, 5, 1), (RW, RW_IDENT, RW_IDENT))
    part = MeshPart(
        "torso",
        [(0.0, 0.0, 0.0), (1.0, 0.0, 0.5), (0.0, 2.0, -1.0), (1.0, 2.0, 0.0)],
        [(0.0, 0.0, 1.0)] * 4,
        [(0.0, 0.0), (1.0, 0.0), (0.0, 1.0), (1.0, 1.0)],
        [(0, 1, 2), (2, 1, 3)],
        [(0, 0, 0, 0), (1, 2, 0, 0), (2, 0, 0, 0), (1, 0, 0, 0)],
        [(1.0, 0.0, 0.0, 0.0), (0.25, 0.75, 0.0, 0.0), (1.0, 0.0, 0.0, 0.0), (1.0, 0.0, 0.0, 0.0)],
        "tex:a")
    hands = MeshPart("hands", part.positions, part.normals, part.uvs, part.triangles, part.joints, part.weights, "tex:a")
    tex = Texture("a", 2, 1, bytes([255, 0, 0, 255, 0, 255, 0, 255]))
    return Model(skeleton, [part, hands], {"tex:a": tex})


class MatrixTests(unittest.TestCase):
    def test_rw_matrix_becomes_a_column_major_matrix(self):
        self.assertEqual(glb.rw_matrix_to_column_major(RW),
                         [0, 1, 0, 0, -1, 0, 0, 0, 0, 0, 1, 0, 1, 2, 3, 1])

    def test_inverse_times_matrix_is_the_identity(self):
        m = glb.rw_matrix_to_column_major(RW)
        inv = glb.mat4_inverse(m)
        for c in range(4):
            for r in range(4):
                total = sum(m[k * 4 + r] * inv[c * 4 + k] for k in range(4))  # (m * inv)[r][c]
                self.assertAlmostEqual(total, 1.0 if r == c else 0.0, places=5)

    def test_a_singular_matrix_is_rejected(self):
        with self.assertRaises(ValueError):
            glb.mat4_inverse([0.0] * 16)


class GlbTests(unittest.TestCase):
    def setUp(self):
        self.model = make_model()
        self.data = glb.to_glb(self.model)
        self.gltf, self.bin = glb.read_glb(self.data)

    def test_container_is_a_valid_glb(self):
        magic, version, total = struct.unpack_from("<III", self.data, 0)
        self.assertEqual((magic, version, total), (0x46546C67, 2, len(self.data)))
        json_len, json_type = struct.unpack_from("<II", self.data, 12)
        self.assertEqual(json_type, 0x4E4F534A)
        self.assertEqual(json_len % 4, 0)
        bin_len, bin_type = struct.unpack_from("<II", self.data, 20 + json_len)
        self.assertEqual(bin_type, 0x004E4942)
        self.assertEqual(bin_len % 4, 0)
        self.assertEqual(20 + json_len + 8 + bin_len, len(self.data))

    def test_output_is_deterministic(self):
        self.assertEqual(glb.to_glb(make_model()), self.data)

    def test_joints_follow_the_skeleton_order_and_carry_their_node_id(self):
        g = self.gltf
        joints = g["skins"][0]["joints"]
        self.assertEqual([g["nodes"][j]["extras"]["nodeId"] for j in joints], [0, 5, 1])
        self.assertEqual([g["nodes"][j]["name"] for j in joints], ["bone_0", "bone_5", "bone_1"])
        self.assertEqual(g["nodes"][0]["skin"], 0)
        self.assertEqual(g["scenes"][0]["nodes"], [0] + joints)

    def test_joint_nodes_are_at_their_bind_pose(self):
        g = self.gltf
        inverse_bind = glb.read_accessor(g, self.bin, g["skins"][0]["inverseBindMatrices"])
        for k, joint in enumerate(g["skins"][0]["joints"]):
            node = g["nodes"][joint]["matrix"]
            ib = inverse_bind[k]
            for c in range(4):
                for r in range(4):
                    total = sum(node[kk * 4 + r] * ib[c * 4 + kk] for kk in range(4))
                    self.assertAlmostEqual(total, 1.0 if r == c else 0.0, places=5)

    def test_every_accessor_is_well_formed(self):
        # Review Focus #5
        g = self.gltf
        for view in g["bufferViews"]:
            self.assertEqual(view["byteOffset"] % 4, 0)
            self.assertLessEqual(view["byteOffset"] + view["byteLength"], len(self.bin))
        self.assertEqual(g["buffers"][0]["byteLength"], len(self.bin))
        sizes = {5120: 1, 5121: 1, 5122: 2, 5123: 2, 5125: 4, 5126: 4}
        widths = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
        for acc in g["accessors"]:
            view = g["bufferViews"][acc["bufferView"]]
            self.assertLessEqual(acc.get("byteOffset", 0) + acc["count"] * sizes[acc["componentType"]] * widths[acc["type"]],
                                 view["byteLength"])

    def test_primitives(self):
        g = self.gltf
        self.assertEqual(len(g["meshes"][0]["primitives"]), 2)
        prim = g["meshes"][0]["primitives"][0]
        attrs = prim["attributes"]
        pos = glb.read_accessor(g, self.bin, attrs["POSITION"])
        self.assertEqual(pos, [tuple(p) for p in self.model.parts[0].positions])
        self.assertEqual(glb.read_accessor(g, self.bin, attrs["TEXCOORD_0"]), [tuple(u) for u in self.model.parts[0].uvs])
        idx = glb.read_accessor(g, self.bin, prim["indices"])
        self.assertEqual(idx, [0, 1, 2, 2, 1, 3])
        self.assertTrue(all(i < len(pos) for i in idx))
        joints = glb.read_accessor(g, self.bin, attrs["JOINTS_0"])
        weights = glb.read_accessor(g, self.bin, attrs["WEIGHTS_0"])
        self.assertEqual(joints, [tuple(j) for j in self.model.parts[0].joints])  # the joints survive the round trip
        self.assertTrue(all(max(j) < 3 for j in joints))
        self.assertTrue(all(abs(sum(w) - 1.0) < 1e-5 for w in weights))

    def test_position_min_and_max_are_present_and_right(self):
        acc = self.gltf["accessors"][self.gltf["meshes"][0]["primitives"][0]["attributes"]["POSITION"]]
        self.assertEqual(acc["min"], [0.0, 0.0, -1.0])
        self.assertEqual(acc["max"], [1.0, 2.0, 0.5])

    def test_the_texture_is_embedded_as_png_and_shared(self):
        g = self.gltf
        self.assertEqual(len(g["images"]), 1)
        view = g["bufferViews"][g["images"][0]["bufferView"]]
        png = self.bin[view["byteOffset"]:view["byteOffset"] + view["byteLength"]]
        self.assertEqual(g["images"][0]["mimeType"], "image/png")
        self.assertEqual(png[:8], b"\x89PNG\r\n\x1a\n")
        self.assertEqual(len(g["materials"]), 1)
        self.assertEqual([p["material"] for p in g["meshes"][0]["primitives"]], [0, 0])
        self.assertEqual(g["asset"]["extras"]["space"], "GTA clump space: X forward, Y left, Z up")

    def test_a_part_with_too_many_vertices_is_rejected(self):
        # Review Focus #5: indices are 16-bit
        big = MeshPart("big", [(0.0, 0.0, 0.0)] * 70000, [(0.0, 0.0, 1.0)] * 70000, [(0.0, 0.0)] * 70000, [(0, 1, 2)],
                       [(0, 0, 0, 0)] * 70000, [(1.0, 0.0, 0.0, 0.0)] * 70000, "tex:a")
        with self.assertRaises(ValueError):
            glb.to_glb(Model(self.model.skeleton, [big], self.model.textures))

    def test_read_glb_rejects_garbage(self):
        for bad in (b"", b"glTF", b"\0" * 64, self.data[:30]):
            with self.assertRaises(ValueError):
                glb.read_glb(bad)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Ejecutar y comprobar que falla**

Run: `cd ./tools && python3 -m unittest tests.test_glb 2>&1 | tail -4`
Expected: `ImportError` por `cjconvert.glb`.

- [ ] **Step 3: Implementar `glb.py`**

`tools/cjconvert/glb.py`:

```python
"""Writer (and a small reader, for tests and the preview) of glTF binary files."""
import json
import struct

from .txd import encode_png

GLB_MAGIC = 0x46546C67
CHUNK_JSON = 0x4E4F534A
CHUNK_BIN = 0x004E4942
FLOAT, UNSIGNED_BYTE, UNSIGNED_SHORT = 5126, 5121, 5123
ARRAY_BUFFER, ELEMENT_ARRAY_BUFFER = 34962, 34963
COMPONENT_SIZE = {5120: 1, 5121: 1, 5122: 2, 5123: 2, 5125: 4, 5126: 4}
COMPONENT_FORMAT = {5120: "b", 5121: "B", 5122: "h", 5123: "H", 5125: "I", 5126: "f"}
TYPE_WIDTH = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
MAX_VERTICES = 65535
SPACE = "GTA clump space: X forward, Y left, Z up"


def rw_matrix_to_column_major(m):
    """The 16 floats of an RwMatrix (right, up, at, pos, each followed by a pad float) as a column-major 4x4."""
    return [m[0], m[1], m[2], 0.0, m[4], m[5], m[6], 0.0, m[8], m[9], m[10], 0.0, m[12], m[13], m[14], 1.0]


def mat4_inverse(m):
    """Inverse of a column-major 4x4 by Gauss-Jordan elimination; ValueError if it is singular."""
    a = [[m[c * 4 + r] for c in range(4)] + [1.0 if r == k else 0.0 for k in range(4)] for r in range(4)]
    for col in range(4):
        pivot = max(range(col, 4), key=lambda r: abs(a[r][col]))
        if abs(a[pivot][col]) < 1e-12:
            raise ValueError("the matrix is singular")
        a[col], a[pivot] = a[pivot], a[col]
        scale = a[col][col]
        a[col] = [x / scale for x in a[col]]
        for r in range(4):
            if r != col:
                factor = a[r][col]
                a[r] = [x - factor * y for x, y in zip(a[r], a[col])]
    return [a[r][4 + c] for c in range(4) for r in range(4)]


class _Buffer:
    def __init__(self):
        self.data = bytearray()
        self.views = []
        self.accessors = []

    def view(self, payload, target=None):
        while len(self.data) % 4:
            self.data.append(0)
        entry = {"buffer": 0, "byteOffset": len(self.data), "byteLength": len(payload)}
        if target:
            entry["target"] = target
        self.data += payload
        self.views.append(entry)
        return len(self.views) - 1

    def accessor(self, payload, component, count, kind, target=None, bounds=None):
        entry = {"bufferView": self.view(payload, target), "componentType": component, "count": count, "type": kind}
        if bounds:
            entry["min"], entry["max"] = bounds
        self.accessors.append(entry)
        return len(self.accessors) - 1


def _pack(fmt, values):
    return b"".join(struct.pack(fmt, *v) if isinstance(v, (tuple, list)) else struct.pack(fmt, v) for v in values)


def to_glb(model):
    buf = _Buffer()
    joint_count = len(model.skeleton.node_ids)
    nodes = [{"name": "CJ", "mesh": 0, "skin": 0}]
    inverse_bind = []
    for node_id, rw in zip(model.skeleton.node_ids, model.skeleton.inverse_bind):
        column_major = rw_matrix_to_column_major(rw)
        inverse_bind.append(column_major)
        nodes.append({"name": f"bone_{node_id}", "matrix": mat4_inverse(column_major), "extras": {"nodeId": node_id}})
    joints = list(range(1, joint_count + 1))
    ibm = buf.accessor(_pack("<16f", inverse_bind), FLOAT, joint_count, "MAT4")

    texture_keys = list(model.textures)
    images, materials, textures = [], [], []
    for key in texture_keys:
        t = model.textures[key]
        images.append({"bufferView": buf.view(encode_png(t.width, t.height, t.rgba)), "mimeType": "image/png", "name": key})
        textures.append({"sampler": 0, "source": len(images) - 1})
        materials.append({"name": key, "pbrMetallicRoughness": {
            "baseColorTexture": {"index": len(textures) - 1}, "baseColorFactor": [1, 1, 1, 1],
            "metallicFactor": 0.0, "roughnessFactor": 1.0}})

    primitives = []
    for part in model.parts:
        n = len(part.positions)
        if n > MAX_VERTICES:
            raise ValueError(f"{part.name} has {n} vertices; glTF indices are 16-bit")
        bounds = ([min(p[i] for p in part.positions) for i in range(3)], [max(p[i] for p in part.positions) for i in range(3)])
        attributes = {
            "POSITION": buf.accessor(_pack("<3f", part.positions), FLOAT, n, "VEC3", ARRAY_BUFFER, bounds),
            "NORMAL": buf.accessor(_pack("<3f", part.normals), FLOAT, n, "VEC3", ARRAY_BUFFER),
            "TEXCOORD_0": buf.accessor(_pack("<2f", part.uvs), FLOAT, n, "VEC2", ARRAY_BUFFER),
            "JOINTS_0": buf.accessor(_pack("<4B", part.joints), UNSIGNED_BYTE, n, "VEC4", ARRAY_BUFFER),
            "WEIGHTS_0": buf.accessor(_pack("<4f", part.weights), FLOAT, n, "VEC4", ARRAY_BUFFER),
        }
        flat = [i for tri in part.triangles for i in tri]
        indices = buf.accessor(_pack("<H", flat), UNSIGNED_SHORT, len(flat), "SCALAR", ELEMENT_ARRAY_BUFFER)
        primitives.append({"attributes": attributes, "indices": indices, "material": texture_keys.index(part.texture), "mode": 4})

    gltf = {
        "asset": {"version": "2.0", "generator": "cjconvert", "extras": {"space": SPACE, "parts": [p.name for p in model.parts]}},
        "scene": 0,
        "scenes": [{"nodes": [0] + joints}],
        "nodes": nodes,
        "meshes": [{"name": "CJ", "primitives": primitives}],
        "skins": [{"joints": joints, "inverseBindMatrices": ibm}],
        "materials": materials,
        "textures": textures,
        "images": images,
        "samplers": [{"magFilter": 9728, "minFilter": 9728, "wrapS": 10497, "wrapT": 10497}],
        "accessors": buf.accessors,
        "bufferViews": buf.views,
        "buffers": [{"byteLength": len(buf.data) + (-len(buf.data)) % 4}],
    }
    json_bytes = json.dumps(gltf, separators=(",", ":")).encode("utf-8")
    json_bytes += b" " * (-len(json_bytes) % 4)
    bin_bytes = bytes(buf.data) + b"\0" * (-len(buf.data) % 4)
    total = 12 + 8 + len(json_bytes) + 8 + len(bin_bytes)
    return (struct.pack("<III", GLB_MAGIC, 2, total) + struct.pack("<II", len(json_bytes), CHUNK_JSON) + json_bytes
            + struct.pack("<II", len(bin_bytes), CHUNK_BIN) + bin_bytes)


def read_glb(data):
    if len(data) < 20 or struct.unpack_from("<I", data, 0)[0] != GLB_MAGIC:
        raise ValueError("not a GLB file")
    version, total = struct.unpack_from("<II", data, 4)
    if version != 2 or total != len(data):
        raise ValueError("unsupported or truncated GLB")
    json_len, json_type = struct.unpack_from("<II", data, 12)
    if json_type != CHUNK_JSON or 20 + json_len + 8 > len(data):
        raise ValueError("the GLB has no JSON chunk")
    gltf = json.loads(data[20:20 + json_len].decode("utf-8"))
    bin_len, bin_type = struct.unpack_from("<II", data, 20 + json_len)
    if bin_type != CHUNK_BIN or 20 + json_len + 8 + bin_len > len(data):
        raise ValueError("the GLB has no BIN chunk")
    return gltf, data[28 + json_len:28 + json_len + bin_len]


def read_accessor(gltf, binary, index):
    acc = gltf["accessors"][index]
    view = gltf["bufferViews"][acc["bufferView"]]
    width = TYPE_WIDTH[acc["type"]]
    fmt = "<" + COMPONENT_FORMAT[acc["componentType"]] * width
    size = struct.calcsize(fmt)
    start = view["byteOffset"] + acc.get("byteOffset", 0)
    out = [struct.unpack_from(fmt, binary, start + i * size) for i in range(acc["count"])]
    return [v[0] for v in out] if width == 1 else out
```

- [ ] **Step 4: Ejecutar y comprobar que pasa**

Run: `cd ./tools && python3 -m unittest discover -s tests 2>&1 | tail -4`
Expected: `Ran 49 tests ... OK`.

- [ ] **Step 5: Comprobar con una mutación que la prueba discrimina**

Run: `cd ./tools && rm -rf /tmp/mutg && mkdir /tmp/mutg && cp -r cjconvert tests /tmp/mutg/ && find /tmp/mutg -name __pycache__ -exec rm -rf {} + && sed -i 's/"matrix": mat4_inverse(column_major)/"matrix": column_major/' /tmp/mutg/cjconvert/glb.py && cd /tmp/mutg && python3 -B -m unittest tests.test_glb 2>&1 | tail -3`
Expected: `FAILED` (si el nodo de la articulación no es la inversa de la matriz de enlace inversa, el modelo no se vería bien en un visor).

- [ ] **Step 6: Commit**

Run: `cd . && git add -A && git commit -q -m "M1b: GLB writer (and reader for tests and the preview)

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>" && git log --oneline | head -1`

---

### Task 6: Línea de comandos, vista previa, datos reales y resultados

**Files:**
- Create: `tools/convert_assets.py`, `tools/preview_glb.py`, `docs/m1b-results.md`
- Test: `tools/tests/test_cli.py`, `tools/tests/test_preview.py`, `tools/tests/test_real_install.py`

**Interfaces:**
- Consumes: `load_model`, `ConversionError`, `OUTFIT` (Task 4), `to_glb`, `read_glb`, `read_accessor` (Task 5).
- Produces: `tools/convert_assets.py` con `main(argv=None) -> int` (0 si todo va bien, 1 si hay un `ConversionError`); opciones `--gta DIR` (por defecto `GTA San Andreas`), `--out DIR` (por defecto `cj-fabric/run/config/cj_fabric`) y `--preview`; escribe `cj.glb` (y `cj_preview.png` con `--preview`) y imprime un informe. `tools/preview_glb.py` con `render_preview(glb_bytes, scale=330) -> PIL.Image.Image` (tres vistas ortogonales: de frente, de lado y de espaldas).

- [ ] **Step 1: Escribir las pruebas del CLI y de la vista previa (deben fallar)**

`tools/tests/test_cli.py`:

```python
import io
import os
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import rwbuild as b  # noqa: E402
import convert_assets  # noqa: E402
from cjconvert import assemble, glb  # noqa: E402

IDENT = (1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)
BGRA = bytes([0, 0, 255, 255, 0, 255, 0, 255])


def make_full_install(root):
    """A synthetic install with the five default parts and the four default textures."""
    models = Path(root) / "models"
    models.mkdir(parents=True)
    ids = [0, 1, 5]
    stub = b.geometry([(0, 0, 0)] * 3, [(0, 0, 1)] * 3, [(0, 0)] * 3, [(0, 1, 2)],
                      skin=b.skin(3, [0], [(0, 0, 0, 0)] * 3, [(1, 0, 0, 0)] * 3, [IDENT] * 3))
    (models / "gta3.img").write_bytes(b.img({"player.dff": b.dff(ids, stub)}))
    files = {}
    for spec in assemble.OUTFIT:
        sk = b.skin(4, [0, 1], [(0, 0, 0, 0), (1, 2, 0, 0), (2, 0, 0, 0)],
                    [(1.0, 0.0, 0.0, 0.0), (0.5, 0.5, 0.0, 0.0), (1.0, 0.0, 0.0, 0.0)], [IDENT] * 4)
        files[spec.dff] = b.dff([0, 1, 5, 303], b.geometry(
            [(0, 0, 0), (1, 0, 0), (0, 1, 0)], [(0, 0, 1)] * 3, [(0, 0), (1, 0), (0, 1)], [(0, 1, 2)], skin=sk))
        files.setdefault(spec.txd, [])
        files[spec.txd].append(b.texture_native(spec.texture, 2, 1, BGRA))
    files = {k: (b.txd(*v) if isinstance(v, list) else v) for k, v in files.items()}
    (models / "player.img").write_bytes(b.img(files))
    return Path(root)


class CliTests(unittest.TestCase):
    def run_cli(self, *args):
        out = io.StringIO()
        with redirect_stdout(out):
            code = convert_assets.main(list(args))
        return code, out.getvalue()

    def test_writes_cj_glb_and_reports(self):
        with tempfile.TemporaryDirectory() as tmp:
            install = make_full_install(Path(tmp) / "gta")
            out_dir = Path(tmp) / "deep" / "out"  # Review Focus #6: the folder does not exist yet
            code, text = self.run_cli("--gta", str(install), "--out", str(out_dir))
            self.assertEqual(code, 0, text)
            data = (out_dir / "cj.glb").read_bytes()
            gltf, _ = glb.read_glb(data)
            self.assertEqual(len(gltf["meshes"][0]["primitives"]), 5)
            self.assertEqual(len(gltf["skins"][0]["joints"]), 3)
            self.assertIn("torso", text)
            self.assertIn("cj.glb", text)
            self.assertEqual(os.listdir(out_dir), ["cj.glb"])  # no temporary files left behind

    def test_running_twice_replaces_the_file_with_identical_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            install = make_full_install(Path(tmp) / "gta")
            out_dir = Path(tmp) / "out"
            self.run_cli("--gta", str(install), "--out", str(out_dir))
            first = (out_dir / "cj.glb").read_bytes()
            code, _ = self.run_cli("--gta", str(install), "--out", str(out_dir))
            self.assertEqual(code, 0)
            self.assertEqual((out_dir / "cj.glb").read_bytes(), first)
            self.assertEqual(os.listdir(out_dir), ["cj.glb"])

    def test_a_failure_leaves_no_output_and_does_not_touch_an_existing_file(self):
        # Review Focus #6
        with tempfile.TemporaryDirectory() as tmp:
            out_dir = Path(tmp) / "out"
            out_dir.mkdir()
            (out_dir / "cj.glb").write_bytes(b"previous")
            code, text = self.run_cli("--gta", str(Path(tmp) / "nowhere"), "--out", str(out_dir))
            self.assertEqual(code, 1)
            self.assertIn("error:", text)
            self.assertIn("gta3.img", text)
            self.assertEqual((out_dir / "cj.glb").read_bytes(), b"previous")
            self.assertEqual(os.listdir(out_dir), ["cj.glb"])


if __name__ == "__main__":
    unittest.main()
```

`tools/tests/test_preview.py`:

```python
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

try:
    import numpy  # noqa: F401
    from PIL import Image  # noqa: F401
    HAVE_PREVIEW_DEPS = True
except ImportError:
    HAVE_PREVIEW_DEPS = False

from cjconvert import glb  # noqa: E402
from cjconvert.assemble import MeshPart, Model  # noqa: E402
from cjconvert.dff import Skeleton  # noqa: E402
from cjconvert.txd import Texture  # noqa: E402

RW_IDENT = (1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)


@unittest.skipUnless(HAVE_PREVIEW_DEPS, "numpy and Pillow are needed for the preview")
class PreviewTests(unittest.TestCase):
    def test_renders_three_views_with_the_model_in_each(self):
        import preview_glb
        # a quad in the XZ plane facing +X (forward), 1 m wide along Y and 2 m tall
        part = MeshPart("quad", [(0.0, -0.5, -1.0), (0.0, 0.5, -1.0), (0.0, -0.5, 1.0), (0.0, 0.5, 1.0)],
                        [(1.0, 0.0, 0.0)] * 4, [(0.0, 1.0), (1.0, 1.0), (0.0, 0.0), (1.0, 0.0)],
                        [(0, 2, 1), (1, 2, 3)], [(0, 0, 0, 0)] * 4, [(1.0, 0.0, 0.0, 0.0)] * 4, "t")
        tex = Texture("t", 2, 2, bytes([255, 0, 0, 255] * 4))
        data = glb.to_glb(Model(Skeleton((0,), (RW_IDENT,)), [part], {"t": tex}))
        image = preview_glb.render_preview(data, scale=100)
        self.assertEqual(image.height, 240)  # 2 * extent (1 m) * scale (100) + 40
        self.assertEqual(image.width, 3 * 240)
        pixels = numpy.array(image.convert("RGB"))
        background = pixels[0, 0]
        for view in (0, 2):  # the side view sees the quad edge-on, so it is checked below
            tile = pixels[:, view * 240:(view + 1) * 240]
            self.assertTrue((tile != background).any(axis=2).any(), f"view {view} is empty")
        # the quad faces the camera in the front view (wide) and is edge-on in the side view (nearly empty)
        painted = [(pixels[:, v * 240:(v + 1) * 240] != background).any(axis=2).sum() for v in range(3)]
        self.assertGreater(painted[0], 10 * max(painted[1], 1))
        self.assertGreater(painted[2], 10 * max(painted[1], 1))


if __name__ == "__main__":
    unittest.main()
```

`tools/tests/test_real_install.py` (opcional; se salta si no hay instalación):

```python
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from cjconvert import assemble, glb  # noqa: E402

GTA = Path(__file__).resolve().parents[2] / "GTA San Andreas"


@unittest.skipUnless((GTA / "models" / "gta3.img").is_file(), "no GTA San Andreas install next to the project")
class RealInstallTests(unittest.TestCase):
    """Facts about the vanilla game that the plan measured by hand. Opt-in: needs the user's own copy."""

    @classmethod
    def setUpClass(cls):
        cls.model = assemble.load_model(GTA)

    def test_the_skeleton_has_the_32_known_nodes_in_order(self):
        self.assertEqual(self.model.skeleton.node_ids,
                         (0, 1, 2, 3, 4, 5, 8, 6, 7, 31, 32, 33, 34, 35, 36, 21, 22, 23, 24, 25, 26, 302, 301, 201,
                          41, 42, 43, 44, 51, 52, 53, 54))

    def test_the_parts_have_the_measured_sizes(self):
        sizes = {p.name: (len(p.positions), len(p.triangles)) for p in self.model.parts}
        self.assertEqual(sizes, {"torso": (405, 646), "head": (581, 756), "hands": (258, 300), "legs": (287, 388), "feet": (214, 276)})

    def test_weights_uvs_and_indices_are_sane(self):
        for p in self.model.parts:
            with self.subTest(p.name):
                self.assertTrue(all(abs(sum(w) - 1.0) < 1e-4 for w in p.weights))
                self.assertTrue(all(max(j) < 32 for j in p.joints))
                self.assertTrue(all(abs(u) <= 4 and abs(v) <= 4 for u, v in p.uvs))  # the feet use v in [-1, 0]: the texture repeats
                self.assertTrue(all(max(t) < len(p.positions) for t in p.triangles))

    def test_cj_is_a_person_sized_and_standing_upright(self):
        zs = [pos[2] for p in self.model.parts for pos in p.positions]
        height = max(zs) - min(zs)
        self.assertTrue(1.5 < height < 2.1, height)
        head = next(p for p in self.model.parts if p.name == "head")
        feet = next(p for p in self.model.parts if p.name == "feet")
        self.assertGreater(min(pos[2] for pos in head.positions), max(pos[2] for pos in feet.positions))  # head above feet
        # forward is +X: the toes stick out ahead of the ankles
        self.assertGreater(max(pos[0] for pos in feet.positions), 0.1)

    def test_it_converts_to_a_glb_a_reader_accepts(self):
        gltf, binary = glb.read_glb(glb.to_glb(self.model))
        self.assertEqual(len(gltf["skins"][0]["joints"]), 32)
        for acc in range(len(gltf["accessors"])):
            glb.read_accessor(gltf, binary, acc)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Ejecutar y comprobar que fallan**

Run: `cd ./tools && python3 -m unittest tests.test_cli tests.test_preview 2>&1 | tail -4`
Expected: `ModuleNotFoundError` por `convert_assets` y `preview_glb`.

- [ ] **Step 3: Implementar la línea de comandos**

`tools/convert_assets.py`:

```python
#!/usr/bin/env python3
"""Converts CJ from the user's own GTA San Andreas files into cj.glb (skinned mesh, skeleton and textures)."""
import argparse
import os
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from cjconvert.assemble import ConversionError, load_model  # noqa: E402
from cjconvert.glb import to_glb  # noqa: E402

DEFAULT_OUT = "cj-fabric/run/config/cj_fabric"


def report(model, size):
    lines = [f"CJ assembled from {len(model.parts)} parts on a skeleton of {len(model.skeleton.node_ids)} bones:"]
    for part in model.parts:
        lines.append(f"  {part.name:6} {len(part.positions):4} vertices {len(part.triangles):4} triangles  texture {part.texture}")
    zs = [p[2] for part in model.parts for p in part.positions]
    lines.append(f"  height {max(zs) - min(zs):.2f} m; {len(model.textures)} textures; cj.glb is {size} bytes")
    return "\n".join(lines)


def write_atomically(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=path.parent, prefix=".cj-", suffix=".tmp")
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(data)
        os.replace(tmp, path)
    except BaseException:
        if os.path.exists(tmp):
            os.unlink(tmp)
        raise


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--gta", default="GTA San Andreas", help="the GTA San Andreas folder (the one with models/)")
    ap.add_argument("--out", default=DEFAULT_OUT, help="where to write cj.glb")
    ap.add_argument("--preview", action="store_true", help="also write cj_preview.png (needs numpy and Pillow)")
    args = ap.parse_args(argv)
    try:
        model = load_model(Path(args.gta))
        data = to_glb(model)
    except (ConversionError, ValueError) as e:
        print(f"error: {e}")
        return 1
    out = Path(args.out)
    write_atomically(out / "cj.glb", data)
    print(report(model, len(data)))
    print(f"wrote {out / 'cj.glb'}")
    if args.preview:
        try:
            from preview_glb import render_preview
        except ImportError:
            print("error: --preview needs numpy and Pillow (pip install numpy pillow)")
            return 1
        image = render_preview(data)
        preview = out / "cj_preview.png"
        image.save(preview)
        print(f"wrote {preview}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 4: Implementar la vista previa**

`tools/preview_glb.py`:

```python
#!/usr/bin/env python3
"""Software preview of cj.glb: three orthographic views (front, side, back) so the conversion can be checked by eye."""
import io
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))

from cjconvert.glb import read_accessor, read_glb  # noqa: E402

BACKGROUND = (60, 64, 72)
# Model space is X forward, Y left, Z up. For each view: where the camera is, the screen's right axis and the depth axis.
VIEWS = (
    ("front", np.array([0.0, 1.0, 0.0]), np.array([1.0, 0.0, 0.0])),   # camera ahead of CJ: his left is on the screen's right
    ("side", np.array([-1.0, 0.0, 0.0]), np.array([0.0, 1.0, 0.0])),   # camera on his left: he faces the screen's left
    ("back", np.array([0.0, -1.0, 0.0]), np.array([-1.0, 0.0, 0.0])),  # camera behind him
)


def _load(data):
    gltf, binary = read_glb(data)
    textures = []
    for image in gltf["images"]:
        view = gltf["bufferViews"][image["bufferView"]]
        png = binary[view["byteOffset"]:view["byteOffset"] + view["byteLength"]]
        textures.append(np.array(Image.open(io.BytesIO(png)).convert("RGBA")))
    parts = []
    for prim in gltf["meshes"][0]["primitives"]:
        attrs = prim["attributes"]
        pos = np.array(read_accessor(gltf, binary, attrs["POSITION"]), dtype=np.float64)
        uv = np.array(read_accessor(gltf, binary, attrs["TEXCOORD_0"]), dtype=np.float64)
        idx = np.array(read_accessor(gltf, binary, prim["indices"]), dtype=np.int64).reshape(-1, 3)
        texture = textures[gltf["textures"][gltf["materials"][prim["material"]]["pbrMetallicRoughness"]["baseColorTexture"]["index"]]["source"]]
        parts.append((pos, uv, idx, texture))
    return parts


def _render(parts, right, toward_camera, scale, size, centre):
    up = np.array([0.0, 0.0, 1.0])
    height = size
    image = np.zeros((height, size, 3), dtype=np.uint8)
    image[:] = BACKGROUND
    depth = np.full((height, size), -np.inf)
    for pos, uv, idx, texture in parts:
        sx = (pos - centre) @ right * scale + size / 2
        sy = height / 2 - (pos - centre) @ up * scale
        sz = pos @ toward_camera
        th, tw = texture.shape[:2]
        for tri in idx:
            xs, ys, zs = sx[tri], sy[tri], sz[tri]
            x0, x1 = max(int(np.floor(xs.min())), 0), min(int(np.ceil(xs.max())) + 1, size)
            y0, y1 = max(int(np.floor(ys.min())), 0), min(int(np.ceil(ys.max())) + 1, height)
            if x0 >= x1 or y0 >= y1:
                continue
            det = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(det) < 1e-9:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
            w0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / det
            w1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / det
            w2 = 1.0 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not inside.any():
                continue
            z = w0 * zs[0] + w1 * zs[1] + w2 * zs[2]
            region = depth[y0:y1, x0:x1]
            visible = inside & (z > region)
            if not visible.any():
                continue
            u = w0 * uv[tri[0], 0] + w1 * uv[tri[1], 0] + w2 * uv[tri[2], 0]
            v = w0 * uv[tri[0], 1] + w1 * uv[tri[1], 1] + w2 * uv[tri[2], 1]
            tx = np.clip((np.mod(u, 1.0) * tw).astype(int), 0, tw - 1)  # textures repeat: the feet use v in [-1, 0]
            ty = np.clip((np.mod(v, 1.0) * th).astype(int), 0, th - 1)
            colour = texture[ty, tx, :3]
            region[visible] = z[visible]
            image[y0:y1, x0:x1][visible] = colour[visible]
    return image


def render_preview(glb_bytes, scale=330):
    parts = _load(glb_bytes)
    everything = np.concatenate([p[0] for p in parts])
    centre = (everything.min(axis=0) + everything.max(axis=0)) / 2
    extent = float(np.abs(everything - centre).max())
    size = int(2 * extent * scale + 40)
    tiles = [_render(parts, right, toward, scale, size, centre) for _, right, toward in VIEWS]
    return Image.fromarray(np.concatenate(tiles, axis=1))


if __name__ == "__main__":
    src = Path(sys.argv[1])
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else src.with_name("cj_preview.png")
    render_preview(src.read_bytes()).save(out)
    print("wrote", out)
```

- [ ] **Step 5: Ejecutar las pruebas del CLI y de la vista previa**

Run: `cd ./tools && python3 -m unittest discover -s tests 2>&1 | tail -4`
Expected: todas pasan (`Ran 58 tests ... OK`; `test_real_install` se ejecuta si hay una instalación junto al proyecto, y en este equipo la hay; en otro caso aparece como `skipped`). Si `test_preview` falla por el tamaño del lienzo, revisa que el centro se calcule sobre el cuadro envolvente: con un cuadrado de 1 m × 2 m centrado en el origen, `extent` es 1.0 y el lado es `2 * 1.0 * 100 + 40 = 240`.

- [ ] **Step 6: Comprobar con una mutación que la prueba de la vista previa discrimina**

Run: `cd ./tools && rm -rf /tmp/mutv && mkdir /tmp/mutv && cp -r cjconvert tests preview_glb.py convert_assets.py /tmp/mutv/ && find /tmp/mutv -name __pycache__ -exec rm -rf {} + && sed -i 's/("side", np.array(\[-1.0, 0.0, 0.0\]), np.array(\[0.0, 1.0, 0.0\]))/("side", np.array([0.0, 1.0, 0.0]), np.array([1.0, 0.0, 0.0]))/' /tmp/mutv/preview_glb.py && cd /tmp/mutv && python3 -B -m unittest tests.test_preview 2>&1 | tail -3`
Expected: `FAILED` (con la vista lateral configurada como la frontal, el cuadrado ya no se ve de canto).

- [ ] **Step 7: Ejecutar el conversor sobre la instalación real y mirar la vista previa**

Run: `cd . && python3 tools/convert_assets.py --preview && ls -l cj-fabric/run/config/cj_fabric/`
Expected: el informe con las cinco piezas y los tamaños medidos (`torso 405 vertices 646 triangles`, `head 581 ... 756`, `hands 258 ... 300`, `legs 287 ... 388`, `feet 214 ... 276`), `32 bones`, una altura entre 1.5 y 2.1 m, y los archivos `cj.glb` y `cj_preview.png`.

Abre `cj-fabric/run/config/cj_fabric/cj_preview.png` con la herramienta de lectura de imágenes y comprueba a ojo: tres vistas de un cuerpo humano reconocible, con la cara de CJ y su piel, la camiseta ausente (torso desnudo y ropa interior), las manos pegadas al final de los brazos en cruz (postura en T), los pies descalzos, el frente de la vista de frente mirando a la cámara y de espaldas en la tercera. **Si algo no se ve bien** (piel con las texturas cambiadas, caras al revés, el cuerpo tumbado, la cabeza con la textura del torso), no lo des por bueno: aplica `superpowers:systematic-debugging`, cambia la tabla `OUTFIT` solo si el problema es la elección de textura (por ejemplo `head.txd:head8bit` en vez de `player_face.txd:face`), y anota cada decisión en `docs/m1b-results.md`.

- [ ] **Step 8: Comprobar que nada del juego entra al repositorio**

Run: `cd . && git status --short && git check-ignore -v cj-fabric/run/config/cj_fabric/cj.glb cj-fabric/run/config/cj_fabric/cj_preview.png | cut -c1-90`
Expected: `git status` solo lista los archivos de código y documentación nuevos (nada de `.glb` ni `.png`), y las dos rutas de salida figuran como ignoradas por `.gitignore`.

- [ ] **Step 9: Escribir `docs/m1b-results.md` con lo medido**

El documento debe tener estas secciones, rellenadas con lo observado en los pasos anteriores (no con valores esperados): el informe del conversor tal cual salió (piezas, vértices, triángulos, texturas, altura, tamaño del `cj.glb`); el resultado de `test_real_install` (qué pruebas se ejecutaron y pasaron); lo que se ve en la vista previa, vista por vista (lo que estaba bien y lo que no); las decisiones tomadas (por ejemplo, cambios en `OUTFIT`) con su motivo; los problemas encontrados; y lo que M1c debe saber (el espacio del modelo X adelante, Y izquierda, Z arriba, la conversión `(x, y, z)GTA → (y, z, x)MC`, el orden de los `joints` y sus `nodeId`, que las texturas van incrustadas como PNG y las UVs tienen el origen arriba a la izquierda).

- [ ] **Step 10: Criterio de cierre de M1b y commit**

M1b se da por terminado solo si: todas las pruebas de `tools/tests` pasan (incluida `test_real_install` sobre la instalación real); el conversor genera `cj.glb` con las cifras medidas; la vista previa muestra a CJ reconocible en las tres vistas (lo comprueba quien ejecuta el plan mirando el PNG y se lo enseña al usuario si hay dudas); no hay archivos del juego en el repositorio; y `docs/m1b-results.md` está completo. Run: `cd . && (cd tools && python3 -m unittest discover -s tests 2>&1 | tail -3) && git add -A && git commit -q -m "M1b: conversion CLI, preview renderer and results of the run on the real install

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>" && git log --oneline | head -1`
Si se cumple, se avisa al usuario y se pasa a planificar M1c (el render en Minecraft). Si el modelo no se reconoce como CJ, se detiene el trabajo y se vuelve a discutir con el usuario antes de seguir.
