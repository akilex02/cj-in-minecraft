# Nadar — Investigación de animaciones en GTA

Fecha: 2026-10-08. Mediciones en GTA SA 1.0 US real, con Minecraft abierto (el CJ dibujado en F5 muestra el resultado), un experimento temporal del puente (ya retirado) que ejecutaba órdenes de un fichero sobre el ped y una sonda (`Pose` de las posiciones del esqueleto con Minecraft cerrado): solo animaciones, sin tareas del juego, sin tocar la posición, la vida ni el estado.

## El grupo de natación no está cargado: hay que pedirlo al juego
- Las animaciones de natación son del grupo **71 (`swim`)** y viven en un **bloque de animaciones aparte, `swim`**, que el juego solo carga cuando hace falta (índice **131**, id de streaming 25575 + 131). En una partida normal el grupo 71 está sin crear (`numAnims` 0, `groupID` −1).
- **`AddAnimation` sobre un grupo sin crear tumba el juego** (la búsqueda `anims[id − idOffset]` no comprueba nada). Fue lo que cerró el juego la primera vez (grupo 71, id 311).
- **Cómo cargarlo:** `CAnimManager::GetAnimationBlockIndex("swim")` (0x4D3990, cdecl) da el índice; `CStreaming::RequestModel(25575 + índice, GAME_REQUIRED | KEEP_IN_MEMORY)` (0x4087E0) y `CStreaming::LoadAllRequestedModels(false)` (0x40EA10) lo cargan al momento, y el propio juego crea el grupo (`CreateAnimAssocGroups`); `CAnimManager::AddAnimBlockRef(índice)` (0x4D3FB0) deja una referencia para que no se descargue. Después el grupo 71 tiene 6 animaciones y `idOffset` 311. Los grupos están en un array en `*(0xB4EA34)`, de 0x14 bytes cada uno (bloque, animaciones, número, `idOffset`, id de grupo).
- El grupo 0 (peds) y el 70 (jetpack) sí están siempre creados.

## Las animaciones (grupo 71, salvo flotar), todas con aspecto correcto según el usuario
| Estado | Grupo e id | Banderas | Duración y comportamiento |
|---|---|---|---|
| Flotar | 0, 14 (`SWIM_TREAD`) | 0x4017 | bucle de ~1,4 s; flota «como debe y en su altura» |
| Braza | 71, 311 | 0x4013 | bucle de ~1,4 s; tumba a CJ en horizontal con brazos de braza |
| Crol | 71, 312 | 0x4013 | bucle; en horizontal |
| Zambullida | 71, 313 | 0x4019 | una pasada de **1,07 s** y se borra sola (`FINISH_AUTO_REMOVE`); «se veía como bucear» |
| Bajo el agua | 71, 314 | 0x4011 | una brazada de ~1,5 s que **se queda en el último fotograma** (sin bucle); «parecía nadar sumergido» |
| Deslizar | 71, 315 | 0x4013 | postura casi estática; se ve bien |
| Salir del agua | 71, 316 | 0x4019 | una pasada de **0,33 s** y se borra sola; «se veía como subirse a un borde» |

- **Las animaciones de movimiento traen banderas que el juego gestiona:** `SINCRONIZADA` (0x20) y `PUEDE EXTRAER VELOCIDAD` (0x40), propias de las animaciones de andar y nadar. Con ellas, la braza llegaba a peso 1 pero **el juego la borraba en menos de un segundo**. Hay que quitarlas (y la de autoeliminar, 0x4, que la quita el puente cuando toca) justo al crearla: `flags &= ~0x64`; así la animación dura todo lo que se quiera. Después, como siempre: `flags |= 0x11` (parcial y reproduciendo), peso a 0 y ritmo +8; se quita con ritmo −4 y bandera 0x4.
- **Se crean con `AddAnimation`** (0x4D3AA0), como la del jetpack; nunca con `BlendAnimation`.

## La postura y el movimiento propio del esqueleto (sonda)
- **No hace falta inclinar el cuerpo dibujado:** las animaciones de nado ya tumban a CJ en horizontal. El cuerpo tumbado queda centrado en el origen del esqueleto (que está a ~1,0 bloque sobre los pies): de −0,37 a +0,24 en vertical; de pie va de −1,0 a +0,7.
- **Trae su propio avance, que se reinicia en cada bucle:** la braza avanza hasta ~2,8 bloques por ciclo (de 0,8 a 2,8 y vuelta a empezar), el crol hasta ~2,4, la de bajo el agua ~2,5 (y se queda ahí), flotar ±0,25, deslizar ~0,15. Lo vertical se mueve poco (±0,1 de media). Se quita como en el trepado: el renderer resta al dibujar el desplazamiento medio hacia delante del esqueleto respecto a la postura inicial (el lateral, de ±0,2 como mucho, no se quita).
- **Altura:** con los pies del jugador a 1,45 bloques bajo la superficie (valor inicial del plan), el cuerpo tumbado quedaría ~0,2 bloques bajo el agua. Para que la espalda quede a ras de la superficie, **`FLOAT_DEPTH` pasa a ≈ 1,15** (el centro del cuerpo ~0,15 bajo el agua, los ojos ~0,47 por encima).

## Seguridad
Tras cada prueba, al quitar la animación con ritmo −4 y autoeliminar, CJ volvió a su postura normal y el juego siguió vivo; con la animación de movimiento sin quitar las banderas, el juego la borraba solo (sin daño). El único cierre del juego fue el de `AddAnimation` sobre el grupo sin crear (resuelto cargando el bloque antes).

## Constantes para las tareas siguientes
Bloque `swim`: índice por `GetAnimationBlockIndex` (0x4D3990), carga con `RequestModel` + `LoadAllRequestedModels`, referencia con `AddAnimBlockRef` (0x4D3FB0) al empezar a controlar a CJ (para que no haya un tirón al entrar al agua); animaciones: flotar (0,14), braza (71,311), crol (71,312), zambullida (71,313), bajo el agua (71,314), salir (71,316); `kClearFlags` 0x64; `kDiveMs` 1070; `kExitMs` 330; `FLOAT_DEPTH` 1,15; la animación de bajo el agua se queda en el último fotograma: se le pone la bandera de bucle (0x2) para que repita la brazada, comprobado en vivo en la Tarea 8.
