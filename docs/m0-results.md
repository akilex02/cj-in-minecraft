# M0 — Resultados

Fecha de la prueba: 2026-10-05

## Direcciones y exe
- `tools/verify_exe_addresses.py` sobre `GTA San Andreas/gta_sa.exe` (14,383,616 bytes): 3 comprobaciones `OK` (prólogo de `FindPlayerPed @0x56E210`, llamada en `0x53E981` hacia `CGame::Process 0x53BEE0`, prólogo de `CGame::Process`).
- `cj_bridge.log` dentro de GTA (Wine 11.18): `hook installed: call @0x53E981 ... (original target 0x53BEE0)`, `HookedProcess: first call from the game loop`, `listening on 127.0.0.1:34560`, `puppet engaged at GTA (...)`. No aparece `ADDRESS MISMATCH`.
- **Conclusión: la suposición se cumple.** Las direcciones y offsets de `gta-reversed` valen para el exe de 14 MB: el plugin lee `Players`/`PlayerInFocus`, escribe la posición y los flags de física de CJ y el juego responde. No hace falta el exe Compact.

## Puente con el cliente falso (`tools/fake_mc_client.py`)
- Prueba normal (15 s): 1080 ecos comprobados, 100 % dentro de 0.1 bloques, error máximo 0.000. PASS.
- Prueba con basura (`--garbage`, 15 s): 1076 ecos, 100 %, error máximo 0.000. PASS. GTA siguió vivo y el log registró `rejected malformed datagram`.
- Prueba sin foco (30 s, el usuario quitó el foco a GTA): 2274 ecos (~75/s, igual que con foco: 1080 en 15 s). PASS. El hilo que fuerza `ForegroundApp` impide la pausa. Que el usuario realmente quitara el foco no se verificó de forma independiente.
- Expiración: tras parar el cliente, el log muestra `no state for 1000 ms: puppet released, gravity and collision restored`, y un cliente nuevo vuelve a engancharlo (`puppet engaged ...`) sin reiniciar GTA.
- Sin partida cargada (menú), el cliente falso sale con código 2 y GTA no se cuelga.

## Minecraft real (mod `cj-fabric` en `./gradlew runClient`, mundo creativo superplano)
La barra de acción mostró `CJ bridge: GTA (x, y, z) ack=N` con `ack` creciendo.

| Lectura | Acción en Minecraft | Eco de GTA | Resultado |
|---|---|---|---|
| A | inicio | (2488.6, -1666.9, 13.7) | referencia |
| B | `/tp @s ~10 ~ ~` (este) | (2498.6, -1666.9, 13.7) | x +10.0 |
| C | `/tp @s ~ ~ ~10` (sur) | (2498.6, -1676.9, 13.7) | y −10.0 |
| D | `/tp @s ~ ~10 ~` (arriba) | (2498.6, -1676.9, 13.7) | sin cambio: el jugador no volaba y ya había caído al capturar |
| E | tras volver a subir, en y=-47.8 mientras caía | (2497.1, -1672.5, 22.2) | x e y coinciden con el desplazamiento esperado (2497.09 y −1672.58); z subió |

- **Este→este y sur→sur: correctos, escala 1:1 exacta.**
- **Vertical: la dirección es correcta** (arriba→arriba). La lectura D falló por la caída del jugador, no por el código. La escala exacta del eje vertical no se midió con Minecraft real; se apoya en la prueba del cliente falso, cuya trayectoria incluye un vaivén vertical de ±0.5 y dio error máximo 0.000.
- El usuario vio en la ventana de GTA que CJ se movía al ejecutar los `/tp`.
- Reinicio de Minecraft: al cerrarlo, GTA liberó a CJ tras 1 s; al volver a abrir el mundo, el log muestra un nuevo `puppet engaged at GTA (2499.5, -1672.4, 13.3)` con ancla nueva, sin reiniciar GTA.
- Latencia percibida: no medida.
- Capturas de pantalla: no guardadas (una captura de pantalla completa registraría todo el escritorio del usuario).

## Problemas encontrados y decisiones para M1
- **Otro plugin pisa la llamada de `0x53E981`.** Alguno de los `.asi` o plugins de modloader del usuario reescribe esa misma llamada tras cargar (la deja apuntando a `0x797FE030`). El plugin se reengancha encadenando con él (hasta 5 veces) y tiene una protección contra recursión. Si M1 necesita otro punto de enganche, hay que tener en cuenta que `0x53E981` es disputado. El plugin que lo hace no se identificó.
- **Loom 1.17.21.** Loom 1.18.x exige Java 25, y Minecraft 1.21.1 corre en Java 21.
- **CJ queda anclado donde esté en GTA.** La primera posición de Minecraft se ancla en la posición actual de CJ, en medio de Grove Street, con peatones y tráfico. M1 debería decidir si lo coloca en un punto vacío del mapa.
- **No se tocó el heading ni la animación de CJ.** Con colisión y gravedad desactivadas, CJ se teletransporta pero no se verificó qué animación reproduce. M1 depende de ello.
- **Solo un proceso puede ocupar el puerto 34561** (mod o cliente falso).
- **Eje vertical con Minecraft real:** repetir la lectura D con el jugador volando si se quiere una cifra exacta.

## Revisión final y arreglos (2026-10-05)
Un revisor independiente leyó todo el código. Tres hallazgos se arreglaron con pruebas que fallaban primero; el resto quedó con decisión razonada o diferido.

**Arreglado**
- **Eco no tautológico.** El eco devolvía el objetivo recién escrito, no la posición real, así que "error 0.000" probaba aritmética y no comportamiento del juego. Ahora el eco es la posición que dejó el juego (muestreada al inicio del frame) con el ack del frame anterior. Con el código nuevo, en el juego real: 1379 ecos al 100 % (error 0.000) en la prueba normal, y 898 y 1245 ecos al 100 % en dos pruebas seguidas (la segunda con basura).
- **Vehículos y flags de física.** Al liberar a CJ se forzaban colisión y gravedad; ahora se guardan al engancharse y se restauran. Un CJ dentro de un vehículo no se marionetea (`bInVehicle` = bit 8 de `ped+0x46C`, comprobado contra el exe).
- **Envío a 60 Hz.** El mod enviaba a unos 30-48 Hz según la tasa de frames. `SendScheduler` mantiene el ritmo con tolerancia de medio intervalo. Pruebas: 144 fps, 60 fps con jitter, 30 fps, `nanoTime` negativo y una parada sin ráfaga.
- La lógica por frame vive ahora en `bridge_core.h` detrás de `BridgeIo` y tiene pruebas nativas con memoria de ped falsa. Se comprobó con mutaciones que cada prueba detecta su bug original.
- El cliente falso se confundía con una sesión anterior aún enganchada (error constante de 4.3 bloques al lanzar dos pruebas sin pausa); ahora espera a que el puente la expire.

**Decisiones**
- Forzar `ForegroundApp=1` podría hacer que el `CentreMousePointer` de GTA mueva el cursor del SO mientras está sin foco. Se mantiene: en este equipo (Linux/XWayland) una sesión completa de Minecraft con GTA sin foco no dio problemas, y el spec exige que GTA siga corriendo. Si falla en X11 o Windows, M1 debe añadir un interruptor.
- El parche de 4 bytes de la llamada desde otro hilo se mantiene: es un único almacenamiento dentro de una línea de caché.
- La animación de CJ con gravedad y colisión desactivadas, y detalles de Wine, no se evaluaron; M1 debe estudiarlos.

**Menores diferidos**
- Tras un reenganche, la guarda de recursión llama a `CGame::Process` real y un tercer plugin podría salir de la cadena.
- `WatchHook` fía de una sola lectura de la llamada y calla tras 5 reenganches.
- Los teletransportes que hace el juego (muerte, interiores) se deshacen al siguiente frame porque el ancla no se refresca.
- El mod se desactiva entero, también el envío, si el puerto 34561 está ocupado.
- El bucle de recepción no tiene tope por frame.
- La conversión en `float32` tiembla a más de ~1e6 bloques del ancla.

**No re-verificado:** tras cambiar el planificador de envío no se volvió a probar el mod con Minecraft real (sí compila y pasa sus 8 pruebas).
