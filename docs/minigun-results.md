# Minigun — Resultados

Fecha de la prueba: 2026-10-06. Spec: `docs/superpowers/specs/2026-10-06-minigun-design.md`; plan: `docs/superpowers/plans/2026-10-06-minigun.md`.

## Pruebas automáticas
- **Python** (`cd tools && python3 -B -m unittest discover -s tests`): 115 pruebas, incluidas las de la instalación real. Las seis armas de M2 salen idénticas (mismos vértices y triángulos, una sola malla; sus `.glb` son idénticos byte a byte a los anteriores) y la minigun sale con 2 mallas: `bulletbelt` (16 triángulos) y `minigun_2` (774), que suman los 582 del cuerpo y los 208 de los cañones.
- **Nativas del puente** (`bash tools/run_native_tests.sh`): 7 suites, con `WeaponModelId(38) == 362`, `WeaponSlot(38) == 7` y la invariante «toda arma con modelo tiene hueco».
- **Java** (`cd cj-fabric && ./gradlew test`): todas en verde, con la minigun en el catálogo, `canJump` por arma y las reglas de movimiento.
- **Mutaciones:** cada módulo nuevo se comprobó cambiando signos, límites y guardas. Quedan vivos, a propósito, cuatro mutantes sin efecto en ningún modelo real: la subcadena `"muzzl"` en lugar de `"muzzle"` (equivalente), `children=True` siempre (equivalente: los hijos de las seis armas reales son solo el fogonazo; la lista `ASSEMBLED` se queda como seguro) y las dos condiciones de `_frame_world` que solo importan para marcos raros (`frames[parent][0] != -1`, `parent < 0`). Las pruebas del conversor se reforzaron tras la revisión: la compactación por malla, las normales y las UV de las piezas hijas se comprueban por contenido.

## El modelo real (medido en la instalación)
`minigun.dff` tiene tres atómicos: el cuerpo en el marco raíz con dos materiales (`bulletbelt`: 16 triángulos, `minigun_2`: 566), el fogonazo en un marco hijo, y el bloque de cañones (208 triángulos de `minigun_2`) en otro marco hijo. El conversor admitía una sola textura y descartaba todo marco hijo; ahora:
- un modelo estático con varias texturas da **una malla por textura** (material por triángulo);
- para las armas de `ASSEMBLED` (solo `minigun`) se **ensamblan las piezas hijas** (movidas por su marco; las que usan una textura `muzzle*` se dejan fuera).
Modelo fijo, sin giro de cañones (decisión del usuario).

## Verificación en vivo (GTA SA real + Minecraft real, con el usuario)
- **Arma en la mano:** bien agarrada con `holdOffset`/`holdRotation` a cero, con sus texturas y formas. ✔
- **Apuntar y disparar:** clic derecho apunta y clic izquierdo dispara en ráfaga con el giro y el sonido de GTA (GTA usa la misma tarea de apuntar que las otras armas). ✔
- **Retirada:** con la minigun en la mano, pasar a un ítem de Minecraft o a la mano libre devolvió a CJ a su postura normal (tras el arreglo de abajo). ✔
- **Salto:** no se puede saltar con la minigun en tierra; con las demás armas sí; en el agua se puede subir. ✔ (la primera versión del bloqueo también quitaba el vuelo creativo, los élitros y el salto de las monturas; la revisión lo detectó y ahora solo bloquea el salto desde el suelo, sin vuelo, montura, fluido ni escalera; esa segunda versión se comprueba en vivo en la pasada de arreglos)
- **Cadencia, daño, bloques, ráfagas largas, movimiento, cambios de arma y las otras seis armas:** según lo probado por el usuario, todo bien y sin errores. ✔

## Valores finales de la entrada `minigun`
`damage` 1.9, `range` 80, `pellets` 1, `spread` 0.05, `blockPower` 0.16, `knockback` 0.05, `aimSpeed` 0.29, `sprintHeld` false, `sprintAiming` false, `moveWhileFiring` false, `canJump` false, `holdOffset`/`holdRotation` a cero.

## Medidas (`tools/gun_probe.py`)
- Minigun: **42,1 rondas/s** (237 rondas en 5,63 s de tiempo de GTA); AK-47: **5,7 rondas/s**.
- Daño por ronda = 2 × 7 × 5,7 / 42,1 = 1,9 (el doble de daño por segundo que la AK-47: unos 80 contra 40); poder contra bloques 0,16 por la misma regla.
- 42 rondas/s son unas 2,1 por tick del cliente: los topes de `ShotEffects` (8 por tick, cola de 64) bastan sin cambios; las ráfagas se dibujan con menos partículas a partir de la tercera ronda de un tick.

## Problemas encontrados y cómo se resolvieron
1. **Dos texturas y cañones en un marco hijo** (el modelo real no cabía en el conversor): ver arriba; con pruebas en rojo primero.
2. **CJ se quedaba con las manos como si sostuviera la minigun al retirársela:** `WeaponSlot(38)` devolvía −1, así que el arsenal del puente no anotaba el arma y no la quitaba nunca (el registro mostraba «he holds 38» tras darle otra arma). Hueco 7 (armas pesadas) y una invariante nativa para que no vuelva a pasar con otra arma nueva.
3. **Salto** (petición del usuario): `canJump` por arma, aplicado en el mixin de la entrada solo desde el suelo (`JumpBlock`: no en el aire, volando, montado, en agua o lava ni en escaleras, para no impedir salir a la superficie, abrir los élitros ni volar en creativo).

## Qué no se midió
La spec pedía también el tamaño del cargador, el retraso de arranque del giro de cañones y los tiempos para matar a un zombi, un cerdo y un creeper. No se midieron con números: el usuario probó todo jugando y lo dio por bueno.

## Desviaciones de la spec y límites
- La velocidad de movimiento no se midió en GTA: se copiaron los valores de las armas pesadas (apuntar lento, sin esprintar, quieta al disparar) y el usuario los dio por buenos jugando.
- Los cañones no giran en el modelo.
- `ShotEffects` aligera las partículas con un contador de resultados por tick (código de Minecraft, comprobado en vivo, sin prueba unitaria).

## Revisión final independiente (opus, solo lectura) y su pasada de arreglos
Sin hallazgos críticos; las seis armas de M2 salen idénticas byte a byte, el índice de vértices y triángulos tras fusionar y compactar, el giro de los cañones (unos 29°), las cuentas de daño y la invariante del hueco de arma salieron correctos. Corregido (cada arreglo con prueba en rojo primero):
- **El bloqueo del salto era más amplio que «en tierra»:** quitaba el vuelo creativo, los élitros y el salto de las monturas con la minigun en la mano. Ahora `JumpBlock.applies` (con prueba y mutaciones muertas) solo bloquea desde el suelo.
- **Pruebas débiles del conversor:** tres mutantes sobrevivían (compactar por malla sin ver el contenido, normales de piezas hijas movidas por la posición del marco, UV de las hijas descartadas); las pruebas comprueban ahora el contenido.
- **Documento:** se corrigieron el reparto de triángulos y la afirmación de mutaciones, y se añadió lo que no se midió.

Aplazados (menores):
- Con la opción de salto automático activada el jugador sigue subiendo bloques de uno en uno (`tickMovement` activa `jumping` después de la entrada); en agua poco profunda se puede saltar.
- Con un parón del cliente de más de ~1,5 s a 42 rondas/s la cola de rondas (64) descarta rondas sin avisar.
- Un marco hijo con escala o espejo no renormaliza las normales ni invierte los triángulos; una pieza hija sin triángulos da un error engañoso; dos atómicos en el marco raíz dan «no cuelga de una raíz identidad». Ningún modelo real lo tiene.
- `canJump` no está en la spec ni en el plan (se añadió a petición del usuario).
