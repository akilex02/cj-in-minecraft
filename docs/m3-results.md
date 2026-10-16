# M3 — Resultados

Fecha de la prueba: 2026-10-06. Diseño: `docs/superpowers/specs/2026-10-06-m3-damage-design.md`; plan: `docs/superpowers/plans/2026-10-06-m3-damage.md`.

## Pruebas automáticas
- **Protocolo** (`cd protocol && python3 -B -m unittest test_cjproto`): sin cambios, OK.
- **Nativas del puente** (`bash tools/run_native_tests.sh`): 7 suites, OK (M3 no toca el puente).
- **Python** (`cd tools && python3 -B -m unittest discover -s tests`): 105 pruebas, OK.
- **Java** (`cd cj-fabric && ./gradlew test`): 148 pruebas, 0 fallos. Nuevas en M3: `Spread`, `PelletRay`, `BlockHealthModel` (27), `WeaponCatalog` con `knockback`, `blocks` y validación de todos los valores de combate, `CrosshairMath` y `CameraShoulder`.
- **Mutaciones:** cada módulo nuevo con lógica pura se comprobó cambiando signos, límites y guardas. Los supervivientes eran equivalentes (se simplificó el código: el tope redundante de `CameraShoulder`, el retorno temprano de `Spread`) o revelaron pruebas que faltaban (uniformidad e isotropía de la dispersión con direcciones donde cada componente importa; el límite exacto de 32 perdigones; el límite de 180° del campo de visión). Queda un equivalente conservado a propósito: el `Math.min(9, …)` de las grietas en `BlockHealthModel` (guarda defensiva).
- El código que usa Minecraft (`ShotResolver`, `BlockBreaker`, `BlockHealthTracker`, `ShotEffects`, `CjCrosshair`, `CameraMixin`) se comprobó compilando y en vivo.

## Verificación en vivo (GTA SA real + Minecraft real, con el usuario)
- **Daño a mobs:** con las armas probadas, los mobs pierden vida, sueltan drops, se enfadan y reciben empuje. Un zombi cae en 4 disparos de Colt 45, 2 de Desert Eagle y 3 de AK-47, como en la tabla de la spec. ✔
- **Escopeta:** a quemarropa y a 20 bloques el daño se reparte entre los perdigones. ✔
- **Bloques:** todos los probados se agrietan y se rompen; si se espera, se curan (regeneración). Bedrock, barrera, agua y lava quedan inmunes, como se pretendía. ✔
- **Partículas:** la traza y el impacto caen donde caen los perdigones, sin fallos. ✔
- **Cambio de ítem:** al cambiar de ítem no se pierde ni la marca ni el arma. ✔
- **Mira (petición del usuario durante la prueba):** como el juego no dibuja mira en tercera persona, `CjCrosshair` dibuja un punto blanco con sombra donde cae el disparo (la proyección a pantalla del punto del rayo desde los ojos, no el centro de la pantalla). Solo se ve con un arma de CJ en la mano y mientras se apunta o se dispara; en primera persona queda la mira de Minecraft. Los disparos caen justo bajo el punto a cualquier distancia. ✔
- **Cámara sobre el hombro (petición del usuario):** al apuntar o disparar, la cámara en tercera persona se desliza 0.75 bloques a la derecha en 0,2 s (`CameraMixin`, `CameraShoulder`), de modo que CJ no tapa la mira, como en San Andreas. Entra y sale suave y no se mete en los bloques en la prueba. ✔
- **No comprobado en vivo:** disparar sin GTA abierto (el código no hace nada si no llegan eventos) y el comportamiento con un cliente conectado a otro servidor (solo se registra un aviso).

## Valores finales de `weapons.json`
Sin cambios sobre la spec: colt45 6/1/0.01/0.5/0.3, desert_eagle 14/1/0.01/1.0/0.6, chromegun 3/8/0.08/0.5/0.1, micro_uzi 3/1/0.04/0.3/0.1, ak47 7/1/0.03/0.6/0.3, m4 6/1/0.02/0.6/0.25 (damage/pellets/spread/blockPower/knockback). `blocks`: `healthPerHardness` 4.0, `minHardness` 0.25, `regenDelayTicks` 60, `regenPerTick` 0.05, `dropsWithoutTool` true.

## Problemas encontrados y cómo se resolvieron
1. **Mutantes de `Spread` que sobrevivían:** la prueba de isotropía usaba una dirección (1,1,1) donde el eje auxiliar anulaba un componente; se probó con direcciones casi diagonales donde el vector auxiliar está lejos de ser unitario y con la varianza absoluta esperada (s²/4).
2. **Sin mira en tercera persona:** ver arriba.
3. **Cámara tapando la mira:** ver arriba.

## Límites y para después
- Cuerpo a cuerpo (puño y bate, necesita un evento de golpe de GTA), minigun, lanzacohetes y explosiones: hitos posteriores.
- Sin daño crítico por cabeza.
- Cofres y bloques con entidad se rompen como cualquier otro.
- La cámara sobre el hombro no comprueba colisiones con bloques (en las pruebas no hizo falta).
- Solo servidor integrado (un jugador); LAN y servidores dedicados quedan fuera.

## Revisión final independiente (opus, solo lectura) y su pasada de arreglos
Sin hallazgos críticos; dispersión, clasificación de perdigones, vida y regeneración de bloques, daño, drops, hilos y los cinco puntos del *Review Focus* salieron correctos. Importantes corregidos y verificados en vivo con el usuario:
- **Una sola grieta visible en todo el mundo:** todas compartían un id y el cliente guarda una grieta por id. Ahora cada bloque tiene el suyo (`CrackId`, negativo para no chocar con ids de entidad), con prueba en rojo primero y mutaciones muertas. En vivo: varias grietas a la vez. ✔
- **Empuje casi duplicado:** el golpe del juego (0,4) se sumaba al `knockback` del arma. Ahora se restablece la velocidad previa y solo se aplica el del arma (sin prueba automática: código de Minecraft). En vivo: la Desert Eagle empuja claramente más que la Micro SMG. ✔
- **Volcado `hs_err_pid*.log` en git:** retirado del seguimiento y añadido a `.gitignore` (sigue en el historial local; no hay remoto).

Aplazados (menores):
- Un bloque sustituido por otro de la misma dureza, o picado a mano, hereda el daño y la regeneración manda grietas a esa posición.
- Las grietas solo llegan a jugadores a menos de 32 bloques, y el alcance llega a 100.
- Romper a tiros se salta `canModifyAt`, modo aventura, protección del spawn, eventos de rotura y `onBreak`/`afterBreak` (el hielo no deja agua); en creativo suelta ítems.
- El rayo usa `OUTLINE`: hierba alta, flores y nieve fina detienen el perdigón (igual que la mira).
- La validación de `blocks` admite `Infinity` y `NaN` en algunos campos; `pellets` 1.5 se trunca; un `range` menor que 1 se sube a 1.
- Sin aviso único en el registro si falta el jugador, está muerto o en espectador; el arma sale del id de GTA y no del ítem sostenido; `ShotResolver` solo captura `RuntimeException`; nombres de la spec distintos (`BlockHealthTicker`, `ShotOutcomes`); `BlockHealthModel.clear` sin uso.
- La cámara sobre el hombro se aplica después de `clipToSpace` y puede entrar en una pared si el lado derecho la toca.
- La mira usa el FOV de las opciones, no el dinámico (esprintar, bajo el agua) ni el balanceo de la vista.
- Javadoc mal colocado sobre `CjRenderer.isAiming`.
