# Protocolo CJ-Minecraft v2

UDP en `127.0.0.1`, binario, little-endian. Puertos: GTA escucha en **34560** (estado de Minecraft); Minecraft escucha en **34561** (eco de GTA). Solo un proceso puede ocupar el 34561 a la vez (mod o cliente falso).

Todos los paquetes empiezan con una cabecera de 8 bytes: `u32 magic = 0x434A4D43`, `u16 version = 2`, `u16 type`. Un paquete con tamaño, magic, versión o tipo incorrectos se ignora.

## State (type 1, Minecraft → GTA, 72 bytes, a 60 Hz)

| Offset | Tipo | Campo |
|---|---|---|
| 8 | u32 | seq (contador creciente; la comparación es con signo para tolerar el desbordamiento) |
| 12 | u32 | ts_ms (ms desde un origen arbitrario de Minecraft) |
| 16 | f32×3 | pos (coordenadas de Minecraft, x este / y arriba / z sur, en bloques) |
| 28 | f32×3 | vel (bloques/s) |
| 40 | f32 | yaw (grados de Minecraft) |
| 44 | f32 | pitch (grados de Minecraft) |
| 48 | u32 | flags: 1 en suelo, 2 sprint, 4 agachado, 8 nadando, 16 trepando |
| 52 | u32 | intents: 1 apuntar (clic derecho mantenido), 2 disparar (clic izquierdo), 4 golpear, 8 saltar, 32 agachado (la tecla C: el puente mantiene el agachado de GTA con pulsaciones del botón de agacharse), 16 disparo en seco (el botón de disparo está pulsado pero el arma no tiene balas: el puente hace sonar el clic de GTA una vez por pulsación) |
| 56 | u16 | weapon: id de arma de GTA del ítem sostenido (22 Colt 45, 24 Desert Eagle, 25 escopeta, 28 Uzi, 30 AK-47, 31 M4; 0 = desarmado) |
| 58 | u16 | reservado (0) |
| 60 | f32×3 | aim: vector desde los pies del jugador hasta el punto de mira, en ejes de Minecraft (x este, y arriba, z sur), en bloques; (0,0,0) = sin mira |

## Echo (type 2, GTA → Minecraft, 32 bytes, un paquete por frame de GTA mientras haya marioneta)

| Offset | Tipo | Campo |
|---|---|---|
| 8 | u32 | ack_seq (último `seq` aceptado) |
| 12 | u32 | gta_time_ms (`GetTickCount` de GTA) |
| 16 | f32×3 | pos real de CJ en GTA (x este / y norte / z arriba) |
| 28 | u16 | weapon_held (arma que CJ sostiene de verdad, 0 = ninguna) |
| 30 | u16 | flags: bit 0 = CJ sostiene dos armas (las pistolas gemelas del juego, con la habilidad al máximo) |

## Ejes y anclaje

- GTA es Z arriba, Y norte; Minecraft es Y arriba, Z sur. Desplazamiento: `GTA = (dx, -dz, dy)`. Escala 1:1.
- Heading de GTA (rad) = `(π − yaw_mc_rad) mod 2π` (se usa desde M1).
- **Anclaje:** el primer `State` aceptado fija `mc_anchor = pos` y `gta_anchor = posición actual de CJ`. Desde entonces `target = gta_anchor + (pos − mc_anchor)` mapeado. El objetivo se recorta a x,y ∈ [−2900, 2900] y z ∈ [−100, 1500].
- Un `State` es válido si todos sus floats son finitos, |coordenada| ≤ 30 000 000 y su `seq` es posterior (con signo) al último aceptado.
- Si pasan más de 1000 ms sin `State` aceptado, la marioneta expira: se restauran las densidades de peatones y coches, se deja de escribir el mando (desde M1 CJ conserva su gravedad y colisión) y se olvida el anclaje y la secuencia.

## Pose (type 3, GTA → Minecraft, 24 + 32 × N bytes, una por frame de GTA mientras haya enganche)

| Offset | Tipo | Campo |
|---|---|---|
| 8 | u32 | ack_seq (último `seq` aceptado) |
| 12 | u32 | gta_time_ms |
| 16 | u32 | flags (reservado, 0) |
| 20 | u16 | bone_count (0..48) |
| 22 | u16 | heading: hacia dónde mira el ped de GTA (el «up» de su matriz) sobre el suelo, en sentido antihorario desde el norte (+Y); 0..65535 = 0..2π |
| 24 | N × 32 bytes | huesos |

Cada hueso: `i32 node_id` (el `nodeID` del RpHAnimHierarchy de GTA), `f32 pos[3]` y `f32 quat[4]` (x, y, z, w), **en el espacio del modelo de CJ** (X adelante, Y izquierda, Z arriba; sin su posición ni su giro en el mundo de GTA). Un paquete con `bone_count` > 48 o con tamaño distinto de `24 + 32 × bone_count` es inválido.

**Rumbo (`heading`):** los huesos están en el espacio del modelo de CJ, es decir, **relativos a su rumbo en el mundo de GTA**. Al apuntar, GTA gira el torso para alcanzar el objetivo respecto a ese rumbo; para que la mira caiga donde mira el jugador, el mod dibuja el cuerpo orientado según este rumbo (yaw de Minecraft = π − heading, en grados) mientras se apunta.

**Quién convierte los ejes:** la matriz de un ped da (derecha, adelante, arriba). El puente convierte cada vector del mundo `v` a espacio del modelo como `(v·adelante, −(v·derecha), v·arriba)`; así `Pose`, las matrices de enlace inversa del esqueleto y `cj.glb` comparten ejes, y no hace falta ninguna rotación al dibujar. (Se comprobó en el juego real que sin esta conversión el cuerpo salía girado unos 90°.)

## Shot (type 4, GTA → Minecraft, 24 bytes, cuando CJ dispara)

| Offset | Tipo | Campo |
|---|---|---|
| 8 | u32 | ack_seq |
| 12 | u32 | gta_time_ms |
| 16 | u32 | shot_seq (total de disparos desde que GTA empezó, incluidos los de este evento) |
| 20 | u16 | weapon |
| 22 | u16 | rounds (disparos que trae este evento, ≥ 1) |

GTA manda cada evento **dos veces** (mismo `shot_seq`) por si se pierde un datagrama; el receptor cuenta como nuevo solo lo que sube `shot_seq`. Quién dispara lo decide GTA (cadencia, cargador y recarga); dónde pega lo decide Minecraft con un rayo desde la mira del jugador. El puente detecta los disparos viendo bajar el cargador del arma activa.

## Paquetes del coche (types 5, 6 y 7)

Mismo encabezado de 8 bytes. Protocolo sigue en v2: los paquetes antiguos no cambian. Quien recibe despacha primero por el tipo de la cabecera; un paquete de otro tipo no es «mal formado».

**VehicleCommand (type 5, Minecraft → GTA, 24 bytes):** subir al coche o bajar.

| Offset | Tipo | Campo |
|---|---|---|
| 8 | u32 | seq |
| 12 | u32 | ts_ms |
| 16 | u16 | command (0 nada, 1 subir, 2 bajar, 3 emisora siguiente, 4 emisora anterior) |
| 18 | u16 | model (id de modelo de GTA; 492 = Greenwood) |
| 20 | f32 | yaw: guiñada del jugador en grados de Minecraft; el coche mira hacia allí |

**VehicleInput (type 6, Minecraft → GTA, 36 bytes, cada fotograma mientras se conduce):**

| Offset | Tipo | Campo |
|---|---|---|
| 8 | u32 | seq |
| 12 | u32 | ts_ms |
| 16 | u16 | throttle (0..255) |
| 18 | u16 | brake (0..255) |
| 20 | i16 | steer (−127..127, positivo a la derecha) |
| 22 | u16 | handbrake (0 o 255) |
| 24 | u16 | corr_flags (bit 0: hay corrección) |
| 26 | u16 | pad |
| 28 | f32 | corr_x: desplazamiento a sumar a la posición del coche, ejes de GTA, metros |
| 32 | f32 | corr_y |

**VehicleState (type 7, GTA → Minecraft, 68 bytes, cada fotograma mientras se conduce):**

| Offset | Tipo | Campo |
|---|---|---|
| 8 | u32 | ack_seq |
| 12 | u32 | gta_time_ms |
| 16 | u16 | flags (bit 0: CJ está dentro del coche; bits 8..15: la emisora de radio como id + 1, 0 si no se sabe) |
| 18 | u16 | model |
| 20 | f32[3] | pos: posición del coche en el mundo de GTA |
| 32 | f32 | heading (radianes, antihorario desde el norte) |
| 36 | f32 | pitch |
| 40 | f32 | roll |
| 44 | f32 | speed (m/s hacia delante) |
| 48 | f32 | steer (radianes; negativo = a la derecha, `m_fSteerAngle`) |
| 52 | f32[3] | seat: CJ en los ejes del coche (derecha, delante, arriba) |
| 64 | f32 | seat_heading: rumbo de CJ respecto al del coche |

## Cambios de semántica desde M1

- **GTA ya no sigue la posición de Minecraft.** CJ se queda en un punto fijo del suelo de GTA (el sitio donde esté al engancharse). De `State` GTA usa la velocidad horizontal, `flags` (en suelo, sprint, agachado) y la transición de suelo a aire para el salto; `pos`, `yaw` y `pitch` solo se validan.
- `Echo` sigue existiendo para diagnóstico; su posición es la posición real de CJ en GTA (el escenario más lo que se haya movido en ese frame).
