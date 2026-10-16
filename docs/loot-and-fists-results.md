# Puño sin ítem y loot en cofres (resultados)

Fecha: 2026-10-11.

## Puño sin ítem
- `fists` sigue en `weapons.json` (daño, alcance, cono) con `"item": false`; el ítem `cj_fabric:fists`, su modelo, icono y nombre ya no existen.
- CJ pega con animación de puñetazo con la mano vacía o con cualquier ítem que no sume daño de ataque por sí solo (bloques, comida...). Las herramientas y espadas de Minecraft (ítems con modificador de daño de ataque en la mano principal) conservan su golpe vanilla (`PunchRules`, `CjItems.hitsOnItsOwn`).
- Minar al doble de velocidad: solo con la mano vacía (antes: con el ítem `fists`).
- Al golpear (puños o bate) el jugador va a Slowness IV (×0.4 de la velocidad, aplicado a la entrada del movimiento: sin icono, sin cambio de FOV) y no corre; ya no se fuerza el agacharse. Herramientas, azadas (daño 0), tijeras y espadas cuentan como ítems que golpean por su cuenta.

## Loot en cofres
- `ChestLoot` (datos puros, con tests) y `ChestLootEvents` (`LootTableEvents.MODIFY`, solo las tablas del juego): cada ítem es su propio pool con su probabilidad.
- Todos los cofres (`chests/*`): Light Ammo 25 % (8 a 24), Heavy Ammo 12 % (6 a 16), Colt 45 18 %, bate 15 %, Micro SMG 14 %, Desert Eagle 13 %, escopeta 12 %, AK-47 12 %, M4 12 %.
- Cofres de botín valioso (End city, bastiones, ciudad antigua, fortaleza, ruinas de portal, tesoro enterrado, pirámide del desierto, templo de la jungla, mansión, mazmorra, puente del Nether, avanzada de saqueadores, tesoro de naufragio, recompensas de Trial Chambers): además minigun 8 %, lanzacohetes 8 %, Rocket Ammo 12 % (2 a 4), jetpack 8 %.
- Herrería de armas de la aldea: los comunes con el doble de probabilidad.
- Solo afecta a cofres generados después del cambio.
