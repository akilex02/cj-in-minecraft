# Munición y recetas (diseño)

Fecha: 2026-10-10. Continúa el combate (`2026-10-05-m2-combat-design.md`, `2026-10-06-rocket-launcher-design.md`, `2026-10-06-minigun-design.md`).

## 1. Objetivo y criterio de éxito

Que las armas necesiten munición en survival, y que todas las armas, la munición y el jetpack se puedan craftear.

**Decisiones del usuario (2026-10-10):**
- Tres ítems de munición: `ammo1` "Light Ammo", `ammo2` "Heavy Ammo", `ammo3` "Rocket Ammo".
  - Light: Colt 45, Micro SMG, Desert Eagle. Heavy: M4, AK-47, escopeta, minigun. Rocket: lanzacohetes.
- Crafteo: Light da 32 balas, Heavy 16, Rocket 4.
- Survival: sin balas no se dispara. Creativo: se dispara sin tener ni gastar balas.
- Hay receta para cada arma (menos los puños), el bate y el jetpack, tal como en las imágenes de `recipes/`.
- Las texturas de la munición están en `weapon-icons/ammo1.png`, `ammo2.png`, `ammo3.png`.
- La madera de la receta de la escopeta acepta cualquier tablón.
- Sin balas en survival se oye el sonido de GTA de arma sin munición (el clic en seco) al pulsar el botón de fuego.
- Confirmado: 1 bala por disparo en todas las armas, creativo sin gasto, minigun como está, recetas de armas correctas.

**El hito está cumplido cuando:**
1. Existen los tres ítems con su nombre, textura y lugar en la pestaña de combate.
2. Las recetas de munición dan 32, 16 y 4, y las de armas, bate y jetpack dan un ítem.
3. En survival, con un arma de fuego en la mano y sin la munición correcta en el inventario, el botón izquierdo no dispara (sin animación, sonido ni daño); apuntar con el derecho sigue funcionando. Al pulsar el botón se oye el sonido de GTA de arma vacía.
4. Con munición, cada ronda disparada gasta 1 bala del tipo del arma, tomada del inventario.
5. En creativo (y en cualquier modo con `instabuild`) se dispara siempre y no se gasta nada.
6. Puños y bate no usan munición.

**Fuera de este hito:** HUD de balas restantes, recarga, balas distintas por efecto, drops de munición, durabilidad.

## 2. Arquitectura

El disparo lo ejecuta GTA, no Minecraft: el botón de fuego llega al puente como `INTENT_FIRE` desde `ControlsMapper`. Por eso el bloqueo se hace **antes** de mandar el intent; ignorar el evento de disparo después dejaría a CJ disparando con animación y sonido sin daño.

- **Datos:** `weapons.json` gana el campo `"ammo"` por arma: `"ammo1"`, `"ammo2"`, `"ammo3"` o `""` (puños, bate). `WeaponCatalog.WeaponDef` gana `ammo` y la validación rechaza un valor desconocido, y un arma de reach > 0 con munición.
- **Ítems (`CjItems`):** se registran `ammo1`, `ammo2`, `ammo3` (ítems simples, stack 64) en la pestaña de combate. Modelo `models/item/ammoN.json` (`item/generated`) y textura `textures/item/gta/ammoN.png`, copiada de `weapon-icons/` (con el mismo tratamiento de tamaño que los iconos de armas ya presentes). Nombres en `en_us.json`.
- **Reglas puras (`dev.cjmc.AmmoRules`):**
  - `canFire(def, available, freeAmmo)`: verdadero si el arma no usa munición, `freeAmmo`, o `available > 0`.
  - `roundsToSpend(def, rounds, freeAmmo)`: 0 si no usa munición o `freeAmmo`, si no `rounds`.
  - Sin dependencias de Minecraft, para poder probarlas.
- **Bloqueo (cliente, `CjClient`):** antes de calcular `intents`, si el arma usa munición y `!canFire(...)` (cuenta de ese ítem en el inventario; `freeAmmo` = `player.getAbilities().creativeMode`), se pasa `attackDown = false` a `ControlsMapper.intents`. Se mantiene `useDown` (apuntar).
- **Consumo (`ShotEffects`):** al procesar cada ronda de un `Shot` (`submit`), el servidor integrado quita 1 bala del inventario del jugador (`roundsToSpend`). Si ya no quedan, esa ronda no se resuelve (sin daño): cubre las rondas que GTA ya había disparado por latencia. La escopeta gasta 1 por disparo (no por perdigón) y el lanzacohetes 1 por cohete, pues el puente cuenta rondas, no perdigones.
- **Recetas:** JSON de receta con forma (`data/cj_fabric/recipe/*.json`, formato 1.21.1: `"category"`, `"key"`, `"pattern"`, `"result"` con `id` y `count`). La munición usa `count` 32/16/4.

- **Sonido de arma vacía:** la implementación del sonido es lo único sin investigar. Spike previo (parte del plan): hallar en GTA el sonido de arma vacía (`gta-reversed-master`, audio de armas del ped, `CAEWeaponAudioEntity`) y decidir entre (a) que el puente lo reproduzca al recibir un nuevo intent `DRY_FIRE`, o (b) reproducir un sonido equivalente desde Minecraft. Se elige (a) si se puede disparar el sonido real sin que GTA dispare. Se reproduce una vez por pulsación, no en bucle.

## 3. Recetas

Leyenda: N = pepita de hierro, I = lingote de hierro, B = bloque de hierro, G = bloque de oro, R = bloque de redstone, D = diamante, DB = bloque de diamante, P = cualquier tablón (`#minecraft:planks`), S = palo, g = pólvora, r = polvo de redstone, f = polvo de blaze. `.` = vacío. Las filas van de arriba abajo.

**Munición** (según el teclado numérico del usuario: 7 8 9 arriba, 4 5 6 en medio, 1 2 3 abajo):

| Ítem | Fila 1 | Fila 2 | Fila 3 | Resultado |
|---|---|---|---|---|
| ammo1 | N N N | N g N | N r N | 32 |
| ammo2 | N g N | N g N | r N r | 16 |
| ammo3 | N f N | N f N | r g r | 4 |

**Armas y jetpack** (de las imágenes de `recipes/`):

| Ítem | Fila 1 | Fila 2 | Fila 3 |
|---|---|---|---|
| colt45 | I I G | . N I | . . R |
| desert_eagle | B B G | . N B | . . R |
| micro_uzi | I B I | . N G | . . R |
| chromegun | G G B | . N N | . R P |
| ak47 | D G R | . N G | . . B |
| m4 | D B R | . N B | . . G |
| minigun | . . R | . G B | DB DB B |
| rocket_launcher | DB DB G | N G G | . . R |
| baseball_bat | . I B | I S I | S I . |
| jetpack | B DB B | B G B | f g f |

Cada una da 1 unidad.

## 4. Pruebas

- `AmmoRulesTest`: sin munición no dispara en survival; con 1 dispara; creativo dispara con 0; puños y bate siempre; gasto 0 en creativo y N en survival.
- `WeaponCatalogTest`: el campo `ammo` se lee; falla con un valor desconocido y con un arma melee con munición; el catálogo real asigna la munición pedida a cada arma.
- Prueba de recursos: cada JSON de receta parsea, su resultado es un ítem registrado (`cj_fabric:*`), y los tres de munición tienen `count` 32, 16 y 4; existen modelo, textura y nombre de cada ítem nuevo.
- Verificación manual en juego (GTA corriendo): sin balas no sale ni animación; con balas se gastan una a una; creativo no gasta; crafteo de las 13 recetas.

## 5. Riesgos

- Las imágenes de recetas se transcribieron a mano: se comparan una a una en la verificación manual.
- La minigun dispara ~30 rondas/s: 16 balas duran ~0,5 s. Se deja como pidió el usuario; si resulta incómodo, se ajusta la cuenta por crafteo o un gasto por ráfaga.
- El consumo requiere el servidor integrado (un jugador), igual que el daño de los disparos ya hoy.
