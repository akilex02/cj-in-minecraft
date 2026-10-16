# Skate dentro de Minecraft: prueba de viabilidad en Linux (resultados)

Fecha: 2026-10-11. Es un extra opcional del mod: quien lo quiera aporta sus propios archivos de Skate 3 (Xbox 360); el mod base (GTA + Minecraft) no depende de esto. Objetivo: que funcione en Windows y en Linux.

## Qué proyecto sirve
- El mashup `chasmlol/2010-rust-rewrite-mashup` no usa el recomp: usa el motor `SK8-ENGINE/skate-3-rust-engine` (Rust + Bevy, GPL-3.0), que corre aparte como "worker", recibe la colisión del mundo anfitrión y devuelve la pose del patinador (sus huesos se reasignan al esqueleto del anfitrión).
- `mchughalex/skate3recomp` es el juego recompilado entero: no ofrece API ni ganchos para otros programas y no declara licencia. No sirve para esto.
- Windows es lo que el motor documenta. Esta prueba comprobó Linux.

## Resultado en Linux (CachyOS, Rust 1.99, tus archivos de `Skate3Recomp-Linux/game/default.xex`)
| Paso | Resultado |
|---|---|
| `skate-core` (lógica de Skate: física, riding, animación; 93 mil líneas, "independiente del motor", sin dependencias) | compila en 10 s |
| `skate-game` completo (Bevy 0.18, Vulkan; SDL3 desde fuente) | compila y enlaza en 4 min 43 s (hay que apuntar `LD_LIBRARY_PATH` a `target/release/deps`, porque Bevy se enlaza dinámicamente en este perfil) |
| Conversión de `default.xex` con el instalador (Python, `tools/asset_pipeline`, sin ventana) | termina: 3.1 GB, patinador, esqueleto, animaciones, cielo, HUD y 9 de 11 mapas |
| `--check-assets` | pasa: mapa cargado, física con colisión (triángulos y rieles de grind), patinador, controles |
| Ejecución con ventana (`--map StartPark.skate`, 25 s) | arranca con Vulkan sin fallos: física en `PhysicsGround`, personaje listo con 35 huesos (no había mando conectado) |

Fallos encontrados (ninguno bloquea la idea):
- **Audio:** el instalador descarga `vgmstream-win64.zip` y ejecuta un `.exe`; en Linux falla con un error de sistema que el instalador no trata como "contenido no disponible". Para la prueba se omitió desde un script propio. Para el mod no hace falta audio.
- **Mapas `University` e `Industrial`:** "corrupt deflate stream" al validarlos en Linux (el resto sí). No hace falta ninguno de los dos, pero conviene saber por qué (posible diferencia de plataforma).
- El instalador llama al ejecutable del juego para extraer algunas tablas (océano, menús): necesita el ejecutable construido y `LD_LIBRARY_PATH`.

## Segunda prueba: cómo programa el motor cada frame (resultado: viable)
Se leyó el código de `skate-game` y `skate-core` (sin modificarlo). Lo que importa para un "modo anfitrión":

**El motor ya tiene los puntos de entrada que necesitamos**
- **Colisión externa:** `BoardWorld::set_external_queries(Arc<dyn ExternalQueries>)`, con dos funciones: `line(start, end, radius)` (trazado de una línea) y `nearby(center, radius) -> Vec<[Vector3; 3]>` (triángulos cercanos). Es el gancho para darle los bloques de Minecraft sin mapa.
- **Entrada:** la física consume `PublishedTickInput`, que son 18 valores de acciones (`GameplayActions::from_values`) más si hay mando. Hay que sustituir el muestreo del mando por nuestro socket.
- **Avance de un tick:** `physics::frame::advance(&mut GamePhysics, &mut SkaterRuntime, &mut PlayerControls, &StockGraphs, &mut dyn ActionMap, input_available, &mut CameraRuntime)` es Rust normal. El sistema de Bevy `physics::advance` solo lo envuelve.
- **Salida:** cada tick fijo se captura un `Snapshot { root: Transform, bones: Vec<Mat4>, camera, fov }` (35 matrices de hueso, esqueleto tipo Mixamo con huesos de la tabla) y `physics.board.part_transforms()` da la tabla. Eso es lo que se enviaría a Minecraft.
- **Teleport al empezar:** `physics.board.set_transform(...)` (lo usa el multijugador), y `--test-world` ofrece un mundo plano.
- **Esqueleto de Skate (35):** HIPS, SPINE, SPINE1-3, NECK, NECK1, HEAD, RIGHT/LEFT SHOULDER, ARM, FOREARM, HAND, UPLEG, LEG, FOOT, TOEBASE, más SKATEBOARD_ROOT, TRUCK_FRONT/BACK y las 4 ruedas, y los HAND/TOEBASE "reparentados". El reasignado a CJ es por nombre y por rotación respecto a la pose de reposo; las proporciones difieren.

**Forma del parche (código nuestro dentro de un proyecto GPL-3.0)**
Un plugin de Bevy `HostPlugin` activado con un flag (por ejemplo `--host 127.0.0.1:PUERTO`):
1. Antes de la física: lee del UDP la entrada y la escribe en `PublishedTickInput`; cuando llegan bloques, actualiza el almacén de triángulos que consulta `ExternalQueries`.
2. Después de la física: manda por UDP la pose (`root`, `bones`) y las partes de la tabla.
3. Ventana oculta o minimizada y modo `--test-world` al principio. Más adelante se puede quitar el renderizado.

**Qué sigue siendo incierto**
- El tamaño real del parche (el motor tiene 120 mil líneas en `skate-game` y no se probó modificarlo).
- Cuántos triángulos por tick puede consultar `nearby` sin bajar de los 60 Hz con un mundo de bloques.
- Los controles: el motor espera mando; el mapeo teclado y ratón hay que decidirlo y probarlo.
- El reasignado de huesos a CJ y el dibujo del tablero (el modelo del tablero sale del `skater.glb` convertido).

**Hecho (2026-10-11):** los pasos 1 y 2 del plan están probados en Linux: el modo anfitrión (`skate-host/`) y la colisión desde cuadriláteros (el patinador rueda sobre un suelo de bloques, se detiene en una pared y cae por el borde). Un detalle que cambió el diseño: `ExternalQueries` solo lo usan sondas puntuales; el contacto del tablero sale de `BoardWorld`, así que el parche **reemplaza el mundo entero** con los cuadriláteros que manda el anfitrión (reconstruir 1714 cuadriláteros tarda 0.5 ms).

**Plan de prototipo (en este orden, cada paso verificable)**
1. Parche mínimo: el plugin con UDP y un cliente falso en Python (como `fake_mc`) que manda entrada y recibe pose en mundo plano.
2. Colisión desde bloques: triángulos de los bloques de un tablero de prueba, con `ExternalQueries`.
3. Cliente de Minecraft: tecla para entrar y salir del modo, mover al jugador con la posición del patinador y dibujar a CJ con los huesos reasignados.
4. Empaquetado para Windows y Linux: el parche, un script que construye y convierte, y un README con los archivos que aporta cada usuario.

## Archivos de la prueba (fuera del proyecto)
- Motor clonado y compilado: `$TMP/.../skate-engine/src` (git) y `~/.cache/skate-spike-target` (artefactos).
- Contenido convertido: `~/Descargas/skate-spike-assets` (3.1 GB).
