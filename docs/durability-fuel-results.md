# Durabilidad de armas y combustible (resultados)

Fecha: 2026-10-11. Verificado en juego por el usuario (durabilidad, reparación, encantamientos, loot, combustible del jetpack y del auto); la barra se movió después a la esquina superior izquierda.

## Durabilidad de armas
- Cada ronda disparada o golpe del bate gasta 1 (en el servidor, junto al gasto de balas, `ShotResolver.wearOut`; en creativo nada; si cambió de ítem entre medio, nada). Al llegar a 0 el arma se rompe como cualquier herramienta.
- Durabilidades (`weapons.json`, campo `durability`): bate 200, Colt 45 250, Desert Eagle 300, Micro SMG 400, escopeta 200, AK-47 600, M4 600, minigun 1500, lanzacohetes 40. Puños y jetpack no.
- Reparación: dos iguales en el yunque, o lingotes de hierro (bate, Colt, Desert Eagle, Micro SMG, escopeta) o diamantes (AK-47, M4, minigun, lanzacohetes) (`WeaponItem.canRepair`, campo `repair`).
- Encantamientos: Mending y Unbreaking I a III (el tag `data/minecraft/tags/item/enchantable/durability.json` lista las armas con durabilidad; `WeaponItem.getEnchantability` = 10).
- Loot: los comunes se encuentran nuevos o con 25 %, 50 % o 75 % de uso, con la misma probabilidad cada estado; la escopeta, el AK-47 y el M4 como máximo con 50 % de uso; minigun, lanzacohetes, munición y jetpack siempre nuevos (`ChestLoot.Drop.wear`).

## Combustible (clase pura `FuelTank`, tests `FuelTankTest`)
- **Jetpack:** 12 s de empuje (solo mientras se mantiene espacio; planear no gasta), recarga de vacío a lleno en 20 s siempre que no empuje; vacío no empuja hasta recuperar el 25 % (5 s). Creativo no gasta ni muestra barra.
- **Auto:** 40 s con acelerador o reversa pulsados, recarga de vacío a lleno en 60 s mientras no hay pedal; vacío, el motor se apaga (acelerador y freno a 0, freno de mano puesto, no sale del auto, la radio sigue) hasta recuperar el 25 % (15 s). Creativo no gasta.
- **Barra:** `FuelHud`, 81 x 5 px en la esquina superior izquierda, a 4 px del borde superior e izquierdo (sobre la radio estorbaba y 12 px quedaba muy separada); naranja, roja mientras espera el 25 %. Se muestra con el jetpack puesto o al manejar, y no en creativo.

## Íconos de los efectos de CJ
El Speed I y el Jump Boost I que se ponen al ser CJ ya no muestran sus íconos arriba a la derecha (`CjEffects.SHOW_ICON = false`, sin partículas como antes). Los efectos de una versión anterior que ya estaban puestos con ícono se reemplazan solos al sincronizar. Una poción o un faro del jugador no se tocan.
