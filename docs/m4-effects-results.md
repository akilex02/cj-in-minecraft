# M4 — Efectos en tercera persona (Velocidad I y Salto II) — Resultados

Fecha de la prueba: 2026-10-07. Primera pieza de M4 acordada con el usuario (el resto: animaciones de GTA con daño, caída y muerte; trepar; nadar al final).

## Qué hace
Mientras el jugador se dibuja como CJ (GTA responde y hay pose fresca) y la cámara no está en primera persona, el servidor integrado le da **Velocidad I** y **Salto II** de duración infinita y **sin partículas** (el icono del efecto sí se ve). Al volver a primera persona, o si GTA deja de responder, se quitan en un segundo. Se reaplican solos si algo los borra (morir, leche, `/effect clear`). Solo se quitan efectos exactamente de esa forma (infinitos, con ese nivel y sin partículas), de modo que una poción o una baliza del jugador no se pisa ni se quita.

## Piezas
- `CjEffects` (común): `plan(...)` decide qué añadir o quitar; `isOurs(...)` reconoce los efectos propios; `sync(jugador, deseado)` lo aplica en el servidor.
- `SyncPacer`: cuándo avisar al servidor (al cambiar lo deseado y cada 20 ticks mientras lo está).
- `CjEffectsClient`: lo deseado es «tercera persona + puente vivo + pose fresca»; avisa al servidor integrado.

## Pruebas
- **Automáticas** (Java en verde): `CjEffectsTest` (decisión de añadir y quitar, efectos ajenos intactos, niveles) y `SyncPacerTest` (cambios, periodo, parpadeo, reinicio del periodo en cada cambio); mutaciones: 9 y 5 mutantes muertos.
- **En vivo** (GTA SA real + Minecraft real, con el usuario): los efectos funcionan y no hay problemas. ✔

## Límites
- Con una conexión a otro servidor (sin servidor integrado) no se aplican: son cosa del servidor.
- Los efectos se miden también al andar agachado al golpear (la velocidad de esa marcha se multiplica por el efecto), tal y como el usuario lo probó.
