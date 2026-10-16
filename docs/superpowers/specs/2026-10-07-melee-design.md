# Cuerpo a cuerpo: puño y bate (diseño)

Fecha: 2026-10-07. Continúa el diseño principal (`2026-10-05-cj-in-minecraft-design.md`, sección 6: «el evento de golpe de GTA provoca un ataque de Minecraft con alcance de ~3 bloques»), M2 (`2026-10-05-m2-combat-design.md`), M3 (`2026-10-06-m3-damage-design.md`), la minigun y el lanzacohetes. Tercer hito de la hoja de ruta de la minigun, sección 0.

## 1. Objetivo y criterio de éxito

Que CJ pueda pelear con el **puño** y con el **bate de béisbol**: que el golpe de GTA (animación, combos y sonido) provoque un ataque real en Minecraft contra mobs, con daño y empuje por las reglas del servidor, como las armas de fuego.

**El hito está cumplido cuando**, con GTA corriendo y un mundo de un jugador:
1. Existe el ítem del bate; llevarlo en la mano pone el bate en la mano de CJ, con su modelo real bien agarrado, y cambiarlo por otro ítem lo retira sin dejar a CJ con las manos como si lo sostuviera.
2. Con la mano **vacía**, el clic izquierdo hace que CJ pegue puñetazos con su animación y sus combos de GTA. Con el bate, que lo balancee. Con cualquier **otro ítem** de Minecraft (espada, pico…) no cambia nada: CJ va desarmado y el ataque vanilla funciona con ese ítem.
3. Un mob de 20 de vida cae en **6 golpes de puño** o **4 de bate** (criterio del usuario, como en GTA).
4. El golpe llega en el instante del impacto de la animación de GTA: el servidor resuelve entonces un ataque desde los ojos del jugador en un cono corto de unos 3 bloques; lo más cercano a la mira recibe el daño y el empuje del arma, con tu jugador como atacante (drops, XP, enfado).
5. Mientras llevas puño o bate, el ataque vanilla contra entidades se anula, de modo que no hay golpe doble. Minar bloques con la mano vacía sigue funcionando como siempre.
6. El movimiento mientras se pega (velocidad, esprintar, saltar) copia lo que GTA permite a CJ con cada arma.
7. Sin GTA, o si algo falla al resolver un golpe, el juego no se cae y el resto del mod sigue.

**Decisiones del usuario (2026-10-07):** puño y bate; el puño solo con la mano vacía (cualquier otro ítem queda como hoy); el bate es un ítem.

**Fuera de este hito:** romper bloques con el bate, golpes críticos, bloqueos y esquivas, y el resto de armas cuerpo a cuerpo (cuchillo, katana, motosierra…). Quedan M4 y el resto de la hoja de ruta.

## 2. Arquitectura

Mismo camino que los disparos: GTA manda un evento, el cliente lo deduplica y lo entrega al servidor integrado, que lo resuelve y devuelve el resultado.

- **Intención:** el cliente manda `INTENT_MELEE` (ya reservado en el protocolo v2) en `State` mientras se pulsa el clic izquierdo con puño o bate. Con puño o bate no se manda `INTENT_FIRE` ni `INTENT_AIM`.
- **Puente:** con `INTENT_MELEE`, el puente hace que GTA ejecute su tarea de pelea (`CTaskSimpleFight`) en la dirección de la mirada del jugador, igual que ya hace con la tarea de apuntar, y detecta el instante de cada golpe (la investigación decide cómo: un estado de la tarea, un campo del ped o la fase de la animación).
- **Evento de golpe:** el puente manda un mensaje `Hit` (del mismo estilo que `Shot`: contador acumulado, enviado dos veces para no perder golpes). Si la investigación demuestra que no hace falta un mensaje nuevo, se documenta y se reutiliza `Shot`; si hace falta, el protocolo pasa a v3 con ficheros dorados nuevos compartidos por Python, C++ y Java.
- **Catálogo (`weapons.json`):** dos entradas nuevas: `fists` (`gta: 0`, sin ítem, se usa con la mano vacía) y `baseball_bat` (`gta: 5`, ítem y modelo propios). Los campos de siempre más `reach` (alcance del golpe en bloques) y `coneHalfAngle` (semiángulo del cono en grados). Para ellos `explosionPower` y `projectileSpeed` son 0.
- **Resolución (servidor, `dev.cjmc.combat`):** un `MeleeResolver` con el mismo patrón que `ShotResolver`: busca el jugador, comprueba que está vivo, y elige el objetivo con una lógica pura (`MeleeTarget`): entre las entidades vivas dentro de `reach` y del cono de la mirada, gana la de menor ángulo a la mira (el desempate, la más cercana). Aplica `damage` con la fuente de jugador y el empuje del arma (`takeKnockback`), con `timeUntilRegen = 0` como en M3 para que cada golpe de un combo cuente.
- **Ataque vanilla:** un mixin sobre `MinecraftClient.doAttack` lo anula cuando el jugador lleva puño (mano vacía) o bate; no toca `handleBlockBreaking`, así que minar sigue igual.

## 3. Investigación previa (spike, parte del plan)

Antes de fijar nada hay que comprobar en GTA real, como en M2, la minigun y el lanzacohetes:
- **La tarea de pelea:** cómo se lanza `CTaskSimpleFight` en el ped del jugador sin ped enemigo cerca (en el mundo de GTA no hay peds: pelea contra el aire), y si el bate usa la misma tarea (esperado) con otra familia de animaciones.
- **El instante del golpe:** si hay un estado de la tarea, un campo del ped o una marca de la animación que dé el frame del impacto; si no, la fase de la animación desde el inicio del golpe.
- **Combos y cadencia:** cuántos golpes de combo hay con puño y con bate, cuánto dura cada uno y qué los encadena, y que el contador de golpes no cuente dos veces el mismo.
- **El modelo del bate:** nombre y estructura en `gta3.img` (marcos, texturas); si el conversor actual lo admite o necesita ampliarse.
- **Movimiento:** velocidad mientras se pega, si deja esprintar y si deja saltar, por arma.
- **Efectos laterales:** que la tarea de pelea no mueva al CJ marioneta (arrastres hacia el objetivo) ni lo gire fuera de la dirección de la mirada, y que no pida nada que bloquee el puente.

## 4. Números iniciales

`fists`: `damage` 3.4, `reach` 3, `coneHalfAngle` 35°, `knockback` 0.3. `baseball_bat`: `damage` 6, `reach` 3.2, `coneHalfAngle` 45°, `knockback` 0.8. Movimiento y salto copiados de GTA. Todo ajustable jugando y anotado en el documento de resultados.

**Criterio del usuario (2026-10-07), como en GTA San Andreas:** el puño mata a un mob de 20 de vida en **6 golpes** (3.4 × 5 = 17 no basta; 3.4 × 6 = 20.4 sí) y el bate en **4 golpes** (6 × 3 = 18 no basta; 6 × 4 = 24 sí; el usuario pedía 3 o 4). Una prueba del catálogo fija estos números de golpes.

## 5. Casos límite

- **Sin objetivo en el cono:** el golpe no daña a nadie (animación y sonido de GTA siguen).
- **Golpes seguidos de un combo:** cada golpe es un evento; el contador acumulado evita contar dos veces el mismo.
- **Mano vacía y minado:** el minado vanilla continúa; si el cursor apunta a un bloque y a la vez hay un mob en el cono, el golpe puede dañar al mob y el minado seguir (como en vanilla).
- **Cambio de ítem a media pelea:** el puente termina la tarea de pelea antes de quitar el bate (como con la tarea de apuntar y la retirada diferida de M2).
- **Objetivos no atacables** (espectadores, el propio jugador, armaduras sin vida): se ignoran.
- **Cliente sin servidor integrado:** como en M3, no se resuelve nada y se registra un único aviso.
- **Sin GTA:** el bate existe como ítem pero no pasa nada; la mano vacía conserva el ataque vanilla (sin `INTENT_MELEE` válido no se anula).

## 6. Pruebas

- **Automáticas** (con mutaciones): catálogo con `fists`, `baseball_bat`, `reach` y `coneHalfAngle` (campos obligatorios, rangos), `MeleeTarget` (objetivo más cercano a la mira dentro del cono y del alcance, desempate, fuera de alcance, fuera de cono, vacío), `WeaponModelId(5)` y `WeaponSlot(5)` en las nativas del puente con la invariante «toda arma con modelo tiene hueco», el contador de golpes del puente (acumulado y nunca hacia atrás), el protocolo del mensaje `Hit` si existe (ficheros dorados), y la conversión del modelo del bate con la instalación real (se salta si no hay).
- **En vivo con GTA real** (con el usuario): bate en la mano bien agarrado; puño con la mano vacía y bate con clic izquierdo con su animación y combos; daño a zombi, cerdo y creeper con el número de golpes; empuje; alcance y cono; un solo golpe por golpe de GTA (sin doble); minar con la mano vacía; otro ítem sin cambios; cambio de arma a media pelea; que GTA no se cierra.
- **Revisión final independiente** y una pasada de arreglos, como en los hitos anteriores.

## 7. Riesgos

- **El instante del golpe** puede no estar expuesto de forma limpia en GTA; la investigación lo decide antes de planear, con la fase de la animación como plan B.
- **La tarea de pelea sin enemigos** puede comportarse raro (no mover al CJ, no cancelarse); la investigación lo comprueba.
- **Un mensaje nuevo en el protocolo** toca los tres lados (Python, C++, Java) y los ficheros dorados.
- **El puño con mano vacía convive con el minado y con el ataque vanilla;** el mixin de `doAttack` debe anular solo el ataque a entidades.
- **Equilibrio:** el daño del puño y el bate frente a los de las armas de fuego se ajusta jugando.
