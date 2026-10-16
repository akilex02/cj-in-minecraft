# M1b — Resultados

Fecha de la prueba: 2026-10-05

## Informe del conversor sobre la instalación real
`python3 tools/convert_assets.py --preview` (carpeta `GTA San Andreas/`):

```
CJ assembled from 5 parts on a skeleton of 32 bones:
  torso   405 vertices  646 triangles  texture player_torso:torso
  head    581 vertices  756 triangles  texture player_face:face
  hands   258 vertices  300 triangles  texture player_torso:torso
  legs    287 vertices  388 triangles  texture player_legs:legs
  feet    214 vertices  276 triangles  texture player_feet:foot
  height 1.81 m; 4 textures; cj.glb is 191476 bytes
```

- El GLB tiene 31 accesores, 35 vistas de búfer, 32 articulaciones y 4 imágenes PNG incrustadas (`player_torso:torso`, `player_face:face`, `player_legs:legs`, `player_feet:foot`).
- Orden de los `joints` (con su `nodeId`): `0, 1, 2, 3, 4, 5, 8, 6, 7, 31, 32, 33, 34, 35, 36, 21, 22, 23, 24, 25, 26, 302, 301, 201, 41, 42, 43, 44, 51, 52, 53, 54`.
- Salida en `cj-fabric/run/config/cj_fabric/` (`cj.glb` y `cj_preview.png`), ignorada por `.gitignore`. Ningún archivo derivado del juego está rastreado por git.

## Pruebas
58 pruebas en `tools/tests` (`cd tools && python3 -m unittest discover -s tests`), todas en verde, incluidas las cinco de `test_real_install` ejecutadas sobre la instalación del usuario: el esqueleto de 32 nodos en su orden, los tamaños de las cinco piezas, pesos que suman 1 y índices dentro del esqueleto, la altura de CJ entre 1.5 y 2.1 m con la cabeza sobre los pies y los dedos del pie por delante (+X), y un GLB que el lector propio acepta. Cada módulo se comprobó además con una mutación que reintroduce un fallo plausible (canales de color cambiados, triángulos del revés, huesos sin remapear por `nodeID`, nodos de articulación fuera de la pose de enlace, vista lateral configurada como la frontal) y cada una hace fallar su prueba.

## Vista previa (`cj_preview.png`), mirada vista por vista
- **De frente:** la cara de CJ reconocible, el torso con las manos al final de los brazos abiertos (postura en A), la ropa interior blanca de `player_legs:legs`, los pies descalzos. Mira a la cámara.
- **De perfil:** la cara hacia la izquierda de la imagen, el cuerpo erguido y los pies descalzos con la textura bien envuelta.
- **De espaldas:** el dorso y la parte trasera de la ropa interior y las piernas.
- No hay caras invertidas, ni texturas cambiadas entre piezas, ni el cuerpo tumbado. No hizo falta tocar la tabla `OUTFIT`.

## Decisiones y hallazgos al ejecutar
- **Las UVs de los pies no caen en `[0,1]`:** van de `v = -0.997` a `0.001` (212 de sus 214 vértices), porque la textura se direcciona con repetición. El plan suponía `[0,1]` tras medir solo el torso, la cabeza y las piernas. El conversor conserva las UVs tal cual y el GLB declara el muestreo en modo repetición; la prueba con datos reales se relajó a `|uv| ≤ 4`, y la vista previa envuelve las coordenadas con módulo 1.
- **Tres errores del propio plan, detectados al ejecutar:** un sello de versión "antiguo" de ejemplo que en realidad era del estilo nuevo, una prueba de GLB que esperaba huesos remapeados donde el modelo de prueba ya trae los índices finales, y una prueba de la vista previa que exigía a la vez vistas no vacías y una vista lateral vacía. En los tres el código estaba bien y se corrigieron las pruebas y el plan.
- **Mutaciones con bytecode obsoleto:** las primeras mutaciones de Python "pasaban" porque `cp -r` copiaba el `__pycache__` y, al conservarse el tamaño del archivo, se reutilizaba el `.pyc`. Se añadió la limpieza de caché y `python -B` a todas las mutaciones del plan.
- `cj.glb` se escribe con permisos `0600` (por `mkstemp`); lo lee el mismo usuario.

## Lo que M1c debe saber
- **Espacio del modelo:** el `cj.glb` está en el espacio de GTA (X adelante, Y izquierda, Z arriba), no en el Y-arriba de glTF; un visor genérico lo mostrará girado. La conversión a Minecraft es `(x, y, z)GTA → (y, z, x)MC`.
- **Articulaciones:** los `joints` van en el orden del esqueleto de GTA y cada nodo lleva su `nodeID` en `extras.nodeId`; el `nodeId` de cada hueso del paquete `Pose` se asocia directamente. Cada nodo de articulación está en su pose de enlace (`matrix` es la inversa de la matriz de enlace inversa).
- **Texturas:** incrustadas como PNG en el propio GLB; las UVs tienen el origen arriba a la izquierda y **hay que muestrear en modo repetición** (los pies usan `v` negativas).
- **Aspecto:** el CJ base va en ropa interior y descalzo; la ropa y la gordura/músculo son de M4.
- **Verificación de orientación:** la orientación de los huesos del paquete `Pose` sigue sin comprobarse contra el juego real (heredado de M1a); el marcador de ejes de M1c debe hacerlo.

## Revisión final y arreglos (2026-10-05)
Un revisor independiente leyó todo el código de M1b, lo comprobó con el validador oficial de glTF de Khronos y lo sometió a 9000 copias corruptas de `torso.dff`, `player.dff` y `player_face.txd` (ninguna produjo otra excepción que `FormatError` o `ConversionError`, ni tardó más de 1 s). Dos hallazgos se arreglaron con pruebas que fallaban primero; el resto quedó con decisión razonada o diferido.

**Arreglado**
- **El validador de Khronos rechazaba el `cj.glb` real** con `SKIN_NO_COMMON_ROOT`: las 32 articulaciones colgaban sueltas de la escena, sin raíz común. Mis pruebas usaban solo mi propio lector, que no podía verlo. Ahora hay un nodo `skeleton` (identidad) del que cuelgan todas las articulaciones y `skins[0].skeleton` lo señala; como es la identidad, las matrices siguen estando en el espacio del modelo. Con el validador: **0 errores, 0 avisos** y un solo aviso informativo (`NODE_MATRIX_DEFAULT`: el hueso raíz lleva una matriz identidad).
- **La vista previa, con las texturas del juego, podía acabar commiteada.** Solo estaba ignorada por git dentro de `cj-fabric/run/`, y las rutas por defecto dependían del directorio actual: ejecutar el conversor desde `tools/` la dejaba en `tools/cj-fabric/...` sin ignorar. Ahora `--out` y `--gta` por defecto se resuelven desde la raíz del proyecto y `.gitignore` ignora `cj_preview.png` y `.cj-*.tmp` donde sea.

**Decisiones**
- Los nodos de articulación siguen llevando su matriz de pose de enlace, aunque la inversa de la matriz de enlace inversa hace que sean redundantes para M1c; los visores genéricos los muestran bien.
- No se añadió el validador de Khronos como prueba del repositorio: necesita `npm` y la red. Se ejecutó a mano y su resultado queda aquí.
- Instalaciones modificadas (otras versiones del juego, IMG adicionales, `modloader`): no se tocan. En la instalación de este usuario no hay nombres duplicados en `gta3.img` (16316 entradas) ni en `player.img` (542), ni ningún reemplazo del cuerpo en `modloader/`.

**Menores diferidos**
- El soporte de Direct3D 8 de `txd.py` es código muerto con un mensaje de error contradictorio (en D3D8 esos campos significan otra cosa).
- Una pieza sin UVs genera un GLB inválido y otra sin normales se rechaza con un mensaje poco claro; no afecta a las cinco piezas del juego (formato `0x10036`).
- Una geometría en tiras de triángulos se rechaza aunque su lista completa de triángulos se podría leer.
- Un `NaN` en pesos o posiciones de un archivo corrupto no da error: escribe un `NaN` que no es JSON válido (basta `allow_nan=False`).
- `write_atomically` y la apertura del IMG quedan fuera del `try` del CLI, así que un `--out` imposible acaba en traza (código 1); los errores van a stdout y falta un `fsync`.
- Una sola textura no soportada hace fallar todo su diccionario, aunque no sea la que usa el equipo.
- `models/gta3.img` y `player.img` se abren con mayúsculas exactas (importa en Linux) y los nombres duplicados del IMG se resuelven al último.
- `test_real_install` se salta solo si falta `gta3.img`; si falta `player.img` da error en vez de saltarse. `test_preview` no detectaría vistas frontal y trasera espejadas, y `test_joint_nodes_are_at_their_bind_pose` es tautológica (la fija otra prueba, `MatrixTests`).
