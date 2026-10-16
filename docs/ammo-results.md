# Munición y recetas (resultados)

Fecha: 2026-10-10. Spec: `superpowers/specs/2026-10-10-ammo-and-recipes-design.md`. Plan: `superpowers/plans/2026-10-10-ammo-and-recipes.md`.

## Hecho (con tests, `./gradlew build` en verde)
- `AmmoRules` (reglas puras), campo `ammo` en `weapons.json`/`WeaponCatalog`, ítems Light/Heavy/Rocket Ammo con icono y nombre, 13 recetas en `data/cj_fabric/recipe/`.
- Servidor: `ShotResolver` gasta 1 bala por ronda (`AmmoInventory.take`); si no queda, la ronda no hace daño. Creativo no gasta.
- Cliente: sin balas se quita `INTENT_FIRE` (apuntar sigue); un clic por pulsación.

## Sonido de arma vacía
Es el de GTA: el evento de audio del frontend `AE_FRONTEND_FIRE_FAIL_ROCKET` (42), el clic del lanzacohetes sin apuntar. Minecraft manda el bit `INTENT_DRY` (16) mientras se mantiene el botón de disparo sin balas; el bridge detecta el flanco y llama a `CAudioEngine::ReportFrontendAudioEvent` (0x506EA0, objeto 0xB6BC90), una vez por pulsación. Documentado en `protocol/PROTOCOL.md`.

## Verificado en juego (2026-10-10, por el usuario)
- El clic de GTA suena solo en survival sin balas, una vez por pulsación.
- Las armas gastan 1 bala por disparo.
- Las 13 recetas se craftean y dan lo esperado, incluidas las cantidades de balas (32, 16, 4).

## Brazos aplastados al apuntar (arreglado)
Síntoma: con la Colt 45 (y la Micro SMG) los hombros se veían hundidos y planos al apuntar, y se quedaban así después. Causa: `CPed::PreRender` llama a `CPed::ShoulderBoneRotation` (0x5DF560) para el modelo del jugador, que copia la matriz del brazo superior a los huesos de los pechos (301/302, los que mueven la piel del hombro) y reduce a la mitad su rotación en X respecto a la clavícula; el puente leía los huesos antes y mandaba 301/302 sin corregir (≈95° fuera de su pose de referencia). Arreglo: `ReadSkeleton` llama a esa función del juego antes de leer (es idempotente). Descartado antes con datos: escala o cizalla de los huesos, el skinning de la build delgada, el inverse bind del torso. Verificado en juego por el usuario.
