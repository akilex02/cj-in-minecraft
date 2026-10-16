# M1a — Resultados

Fecha de la prueba: 2026-10-05

## Hooks en el juego real
Log de `cj_bridge.log` al arrancar GTA (Wine 11.18) con el plugin nuevo:
- `CGame::Process call: hook installed at 0x53E981 (original target 0x53BEE0)`
- `CPad::UpdatePads call: hook installed at 0x53BEE6 (original target 0x541DD0)`
- `CGame::Process call: another plugin changed the call, it now targets 0x797FE030; re-hooking ... (1/5)` (igual que en M0: otro plugin pisa esa llamada; la llamada a `UpdatePads` no fue pisada)
- `first call from the game loop` para los dos hooks, y `listening on 127.0.0.1:34560`.

## Sonda de movimiento (`tools/pose_probe.py`), GTA real, CJ en (2592.5, -1775.4)
Dos ejecuciones seguidas, las dos `PASS`:

| Fase | Poses | Huesos | Energía (1ª / 2ª ejecución) |
|---|---|---|---|
| idle | 198 / 197 | 32 | 0.0025 / 0.0081 |
| walk (sigilo) | 244 / 249 | 32 | 0.0435 / 0.0435 |
| run | 249 / 248 | 32 | 0.0684 / 0.0682 |
| sprint | 248 / 248 | 32 | 0.1046 / 0.1048 |
| salto (informativo) | 100 / 100 | 32 | 0.1491 / 0.1495 |

- Un solo conjunto de huesos en todas las poses, peor desviación de longitud de cuaternión 0.00000 y el hueso más lejano a 1.17 m de la raíz (espacio de CJ correcto).
- **IDs de hueso observados (32):** 0, 1, 2, 3, 4, 5, 6, 7, 8, 21, 22, 23, 24, 25, 26, 31, 32, 33, 34, 35, 36, 41, 42, 43, 44, 51, 52, 53, 54, 201, 301, 302. Coinciden con `eBoneTag`. **El conversor (M1b) debe ordenar los `joints` del glTF por el índice de nodo del hierarchy de GTA y llevar estos `nodeID`.**

## Velocidad de la animación (líneas `anim speed` del log, ventanas de 100 frames)
| Fase | Velocidad medida |
|---|---|
| idle | 0.00 m/s |
| walk | 1.53 y 1.58 m/s |
| run | 3.25 (ventana con la transición) y 5.72 y 5.69 m/s |
| sprint | 11.50 y 12.36 y 12.34 m/s |
| salto + ventanas siguientes | 8.71 y 8.94 m/s |

Con esto se puede ajustar la longitud de zancada: el cuerpo de CJ en Minecraft se mueve a la velocidad del jugador, pero la animación de GTA corresponde a esas velocidades. El sprint (unos 12 m/s) es muy rápido comparado con el sprint de Minecraft (5.6 bloques/s).

## Lo que vio el usuario en la ventana de GTA
- **Idle:** CJ se quedó quieto en el sitio. ✔
- **Walk (sigilo):** camina, **pero no se agacha**. Es el comportamiento diseñado (el sigilo de Minecraft se mapea a `m_bPedWalk`), no un fallo.
- **Run:** corre en el sitio. ✔
- **Sprint:** hace el sprint. ✔
- **Salto:** salta. ✔
- **Al terminar:** el usuario recuperó el control de CJ con el teclado. El log muestra `no state for 1000 ms: puppet released, densities restored`.

## Limitación conocida: agacharse
En GTA, agacharse es el botón `ShockButtonL` del mando (offset `0x24` del `CPad`, `GetDuck()` en `0x540700`, `DuckJustDown()` en `0x540720`) y funciona como interruptor al pulsarlo. El sigilo de Minecraft se mantiene pulsado, así que un agachado real exige pulsar `ShockButtonL` en cada flanco de sigilo y recordar el estado. Queda para M4 (pulido).

## Decisiones y notas para M1b y M1c
- **Hooks:** `0x53E981` es disputado por otro plugin; `0x53BEE6` no lo fue. El reenganche con cadena ya cubre ambos.
- **Oráculo de M0:** `tools/fake_mc_client.py` se retira (GTA ya no sigue la posición de Minecraft). `pose_probe.py` lo sustituye.
- **Salida de pose:** unas 50 poses por segundo (un frame de GTA; las tablas dan ~248 poses en 5 s y las ventanas `anim speed` cuentan 100 frames por cada 2 s), 32 huesos, 24 + 32 × 32 = 1048 bytes por paquete (~52 KB/s). M1c debe interpolar entre dos poses para ir suave a la tasa de render de Minecraft, que será mayor que 50 Hz.
- **Escenario:** el sitio donde estaba CJ al engancharse; con densidad de peatones y coches a 0 no apareció nadie nuevo durante las pruebas.
- Pendientes de M1b: modelo y esqueleto de `player.dff` con los `nodeID` de arriba, y las texturas del `player.txd`.

## Revisión final y arreglos (2026-10-05)
Un revisor independiente leyó todo el código de M1a. Tres hallazgos se arreglaron con pruebas que fallaban primero; el resto quedó con decisión razonada o diferido.

**Arreglado**
- **El juego recoloca a CJ.** Si el juego cambiaba el ped (cargar una partida), lo quitaba (pantalla de carga) o lo movía más de 5 m en un frame (hospital, misión, interior), el plugin devolvía al nuevo ped al escenario viejo con la Z nueva y CJ acababa bajo el mapa. Ahora suelta y vuelve a situarse donde esté. Las densidades se devuelven también cuando el ped desaparece.
- **Cadena de hooks.** `CallHook` guarda la lista completa de plugins por los que encadena, así que ninguno se pierde tras varios reenganches; un plugin que reaplica su propio hook ya no se ejecuta dos o tres veces por frame ni gasta el presupuesto de reenganches. El contador se publica de forma atómica para el hilo vigilante.
- **Documentación.** La expiración ya no devuelve gravedad y colisión (CJ las conserva desde M1), y el ritmo de poses es de unas 50 por segundo, no 75.

**Decisiones**
- Un test antiguo movía a CJ 14 m en un frame para probar que el escenario se mantiene; con la regla de 5 m eso es un teletransporte, así que ahora usa 2.8 m (la deriva real de la animación es de unos 0.25 m por frame).
- Sin cambios, por no poder observarse fuera del juego: desfase de un frame entre los huesos y la matriz del ped, estamina del sprint, entrada del mando en menús y cutscenes, geometría del escenario (cuestas, paredes) y pantalla dividida. La escala de los huesos tampoco se toca: la sonda real no mostró señales de huesos escalados.

**Menores diferidos**
- `QuatFromBasis` devuelve una rotación incorrecta (aunque unitaria) con matrices de hueso escaladas; bastaría normalizar los tres ejes antes de convertir.
- Huecos en las pruebas: las ramas m00 y m11 del cuaternión no se ejercitan, los criterios de cuaternión y distancia de la sonda no pueden fallar y nada comprueba la orientación de los huesos contra el juego real (el marcador de ejes de M1c debe hacerlo).
- Si el puente se engancha durante una cutscene guarda densidad 0 y las calles quedan vacías al soltar; un cambio de densidad hecho por un script durante el enganche se revierte al soltar.
- Tras cerrar Minecraft, el último mando simulado sigue moviendo a CJ hasta 1 s.
- El bucle de recepción del hilo del juego no tiene tope por frame (segunda vez que se señala).

**No re-verificado en vivo:** tras el pase de arreglos no se repitió la sonda en GTA real (las suites nativas, de Python y de Java sí pasan y el plugin recompila sin avisos).
