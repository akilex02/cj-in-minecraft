# Lanzacohetes — Resultados

Fecha de la prueba: 2026-10-07. Spec: `docs/superpowers/specs/2026-10-06-rocket-launcher-design.md`; plan: `docs/superpowers/plans/2026-10-06-rocket-launcher.md`.

## Investigación en vivo (GTA SA real, con el usuario)
- **Arma en la mano:** el RPG (`rocketla`, modelo 359) se da sin que GTA se cierre y queda bien agarrado con `holdOffset`/`holdRotation` a cero y con su textura. ✔
- **Apuntar y disparar:** clic derecho apunta y clic izquierdo dispara con la animación de GTA; GTA usa la misma tarea de apuntar que las otras armas. ✔
- **Cohete propio de GTA:** sí sale, vuela en la dirección del disparo de Minecraft y explota con sonido de GTA; disparando hacia abajo **le quita vida al CJ marioneta** (le deja con poca). Estorba, así que se anula (Tarea 3: `CProjectileInfo::RemoveAllProjectiles` cada fotograma mientras se lleva el RPG).
- **Recarga y cadencia:** no hay recarga como tal; solo un pequeño retardo antes de volver a disparar. Medido con `tools/gun_probe.py 35 12`: **1,0 cohete por segundo** (11 en 10,56 s de GTA; los eventos salen duplicados por diseño). ✔
- **Cambio de arma:** al cambiar de ítem o quedar con la mano libre, CJ suelta el arma y vuelve a su postura normal. ✔

## Pruebas automáticas
- **Java** (`cd cj-fabric && ./gradlew test`): en verde, con el catálogo de ocho armas, `explosionPower`/`projectileSpeed` y su validación (límites exactos 20 y 200, `projectileSpeed > 0` exige explosión, `damage` 0 solo con explosión), `RocketFlight` y las reglas de movimiento con el RPG.
- **Nativas del puente** (`bash tools/run_native_tests.sh`): 7 suites, con `WeaponModelId(35) == 359`, `WeaponSlot(35) == 7` (y la invariante «toda arma con modelo tiene hueco»), `FiresProjectile`, la retirada del cohete de GTA cada fotograma y el escudo `ProofShield`.
- **Python** (`cd tools && python3 -B -m unittest discover -s tests`): el RPG real convierte a una malla (554 vértices, 496 triángulos); los `.glb` de las otras siete armas salen idénticos byte a byte.
- **Direcciones del exe** (`tools/verify_exe_addresses.py`): `CProjectileInfo::RemoveAllProjectiles` en `0x7399B0` verificado.
- **Mutaciones:** catálogo, vuelo, escudo y puente con mutantes muertos. Quedan vivos, a propósito, la guarda `step <= 0` de `RocketFlight` (defensiva contra un resto de redondeo negativo; su mutante es equivalente).

## Verificación en vivo (GTA SA real + Minecraft real, con el usuario)
- **El cohete:** sale con estela de humo y llamas, vuela recto y explota contra suelo, pared, aire y mob, con cráter, sonido y partículas. ✔
- **La explosión:** suelta los drops de los mobs, los daña, y daña al jugador cerca. ✔ (los drops de los bloques rotos, ver la pasada de arreglos)
- **Pegado a una pared:** explota sin atravesarla. ✔
- **Regla del mundo (`mobGriefing false`):** explota y daña sin romper bloques. ✔
- **Cohetes en vuelo al cambiar de arma o cerrar el mundo:** no quedan cohetes colgados. ✔
- **Las otras siete armas** conservan su comportamiento. ✔
- **El cohete propio de GTA:** ya no vuela hacia los lados ni suena; apuntando hacia abajo aún se ve el cohete y la explosión de GTA en su ventana, pero el CJ marioneta no recibe daño, ni fuego, ni cae. ✔

## Valores finales de la entrada `rocket_launcher`
`gta` 35, modelo `rocketla`, `damage` 0, `range` 120, `pellets` 1, `spread` 0.01, `blockPower` 0, `knockback` 0, `explosionPower` 6, `projectileSpeed` 40, `aimSpeed` 0.29, `sprintHeld` false, `sprintAiming` false, `moveWhileFiring` false, `canJump` true, `holdOffset`/`holdRotation` a cero. Las explosiones usan el tipo `TNT` si la regla `mobGriefing` está activa (rompen bloques con sus drops completos) y `NONE` si está apagada (dañan sin romper nada).

## Problemas encontrados y cómo se resolvieron
1. **El cohete de GTA volaba y explotaba en su mundo, quitándole vida al CJ marioneta:** el puente lo quita cada fotograma mientras se lleva el RPG (`RemoveAllProjectiles`).
2. **Apuntando hacia abajo, ese cohete explota el mismo fotograma en que nace, antes de poder quitarlo, y le hacía daño:** `ProofShield` hace al CJ marioneta invulnerable, a prueba de explosiones y de fuego mientras lleva el RPG, y le devuelve sus banderas de antes al soltarlo, cambiar de ped o liberarlo. Primero se descubrió el daño directo y después el fuego de las llamas; cada uno en una prueba en vivo.
3. **El cohete de Minecraft atravesaba el suelo sin explotar:** `ProjectileEntity.tick()` no detecta choques (lo hace cada subclase, como `ThrownEntity`); el cohete ahora hace `ProjectileUtil.getCollision` + `hitOrDeflect` en cada tick. Comprobado con el bytecode y en vivo (código de Minecraft, sin prueba unitaria).

## Límites y para después
- El cohete va invisible con estela de partículas (`missile.dff`, 98 vértices, queda para su modelo 3D); sin RPG con búsqueda de calor (36), lanzallamas (37), granadas ni fuego.
- El cohete y la explosión de GTA siguen viéndose en su ventana al disparar hacia abajo; solo el CJ marioneta está a salvo.
- Un máximo de 8 cohetes por jugador en vuelo (protección contra un contador descontrolado).

## Revisión final independiente (opus, solo lectura) y su pasada de arreglos
Sin hallazgos críticos; el ciclo de vida de la entidad sin doble explosión, el registro común y de cliente, la validación del catálogo, los bits del `ProofShield` (19, 22 y 23 con desplazamiento 0x40) y su restauración en todos los caminos de `Release`, y los valores del puente salieron correctos. Corregido (verificado en vivo con el usuario):
- **El cohete sobrevivía a su dueño:** si el jugador moría, se desconectaba o cambiaba de dimensión, seguía volando y explotaba a su nombre. Ahora desaparece sin explotar (`RocketRules.ownerGone`, con prueba en rojo primero y mutaciones muertas).
- **Nacía un bloque por delante de los ojos:** se saltaba lo que hubiera en ese primer bloque (un zombi pegado, un cristal). Ahora nace en los ojos; el dueño se ignora hasta que el cohete sale de su caja.
- **Los bloques rotos soltaban solo 1 de cada 6 drops** (el tipo de explosión `MOB` aplica `mobExplosionDropDecay`): ahora `TNT` con `mobGriefing` activo y `NONE` con la regla apagada.
Los tres últimos arreglos tocan código de Minecraft sin prueba unitaria posible; su verificación fue en vivo.

Aplazados (menores):
- La explosión ocurre al inicio del paso del tick, no en el punto exacto de impacto (hasta 2 bloques antes; 10 con `projectileSpeed` 200).
- El último paso puede pasarse del alcance y el alcance real son unos 121-122 bloques.
- Con una distancia de simulación baja, un cohete en un chunk cargado pero sin ticks queda quieto y cuenta para el tope de 8 hasta que se descarga.
- El cliente predice un paso por delante (el rastro se mete unos 2 bloques en la pared al final; solo visual).
- El catálogo admite `explosionPower > 0` con `projectileSpeed` 0 (la ruta de bala lo ignora).
- Caso raro del `ProofShield` si el pool de GTA reutiliza la misma dirección de ped sin que `Ped()` devuelva nulo entre medias.
- El escudo pisa los cambios de «proofs» de un script de misión mientras se lleva el RPG, y `RemoveAllProjectiles` quita todo proyectil de GTA (cohetes de enemigos, granadas, cargas) mientras el puente está enganchado y el RPG en la mano.
- La spec prometía pruebas puras de choque y de dueño ignorado en `RocketFlight`; solo el avance y el alcance se prueban en puro (el choque es código de Minecraft, comprobado en vivo).
- `FiresProjectile(36)` cubre también el RPG con búsqueda de calor, que está fuera de este hito y nunca se entrega.
