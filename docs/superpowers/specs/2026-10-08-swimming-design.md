# Nadar y bucear (diseño)

Fecha: 2026-10-08. Última pieza de M4 que quedaba (`docs/m4-effects-results.md`, `docs/body-results.md`, `docs/climb-results.md` y `docs/jetpack-results.md` cierran las anteriores). Reutiliza la base de animaciones del cuerpo (`BodyReactions`, las animaciones parciales con `AddAnimation` de `docs/jetpack-spike.md`) y el patrón del jetpack (reglas puras de física en el cliente, banderas nuevas en el estado).

## 1. Objetivo y criterio de éxito

Que CJ **flote, nade y bucee** con las animaciones de GTA cuando el jugador está en el agua en Minecraft, y que el agua se sienta como en San Andreas: no te hundes por quedarte quieto, flotas con la cabeza fuera, y bajas cuando quieres.

**Decisiones del usuario (2026-10-08):**
- Versión 2: **superficie y buceo** (flotar quieto, nadar despacio, nadar rápido, zambullida, nadar bajo el agua y salir del agua).
- **Flotar en lugar de hundirse:** se modifica la física de Minecraft para que, al entrar al agua, el jugador no se hunda sino que **flote desde el primer bloque de agua**, aunque sea mucho más hondo, para poder tener la animación de flotar quieto.
- **Bucear con Shift:** al mantener Shift en el agua se empieza a descender y CJ pasa a la animación de bucear.

**El hito está cumplido cuando**, con GTA corriendo, un mundo de un jugador y el jugador dibujado como CJ:
1. Al entrar al agua el jugador **no se hunde**: la flotabilidad lo lleva hasta que la cabeza queda justo fuera del agua y se queda flotando quieto, respirando; lo mismo si el agua es muy honda. En un charco de un bloque, o con el suelo cerca, sigue pisando el fondo.
2. Flotando quieto, CJ hace la animación de **flotar en el agua** de GTA.
3. Moviéndose por la superficie, CJ **nada** (braza); con el modo de correr en el agua (nadar rápido de Minecraft) hace el **crol**.
4. Mantener **Shift** en el agua hace descender al jugador a velocidad fija; CJ hace la **zambullida** y después **nada bajo el agua** mientras está sumergido. Nadar rápido mirando hacia abajo (el buceo de Minecraft) también lo mantiene sumergido y con esa animación.
5. Al soltar Shift, la flotabilidad devuelve al jugador a la superficie; CJ hace la animación de **salir del agua** al llegar arriba y pasa a flotar.
6. **Espacio** sigue siendo el de Minecraft: nadar hacia arriba y salir en una orilla.
7. El aire se gasta y el ahogo funcionan como en Minecraft (flotando con la cabeza fuera se respira; sumergido se gasta).
8. Sin GTA, o si algo falla, el jugador flota y bucea igualmente (es física de Minecraft), sin animaciones de GTA; sin jetpack, sin lava (no se nada en lava) y sin tocar nada de lo demás.
9. Seguridad de la partida de GTA: solo animación; nada de esto mueve, hiere ni cambia el estado real del CJ de GTA tras soltar la marioneta.

**Fuera de este hito:** sonidos de agua (salpicaduras; GTA los tiene y quizá se puedan disparar como el sonido del jetpack, se decide aparte), nadar en lava, el modelo de CJ mojado, armas bajo el agua y aletas o equipo de buceo.

## 2. Arquitectura

Mismo reparto de siempre: Minecraft decide y mueve, GTA anima y dibuja a CJ. **Solo animación, nunca una tarea de natación real de GTA** (movería al CJ de GTA por su mundo).

- **Física (cliente, reglas puras `dev.cjmc.swim.SwimRules`):** dados la altura de los pies, la altura de la superficie del agua en la columna del jugador, si mantiene Shift y si está permitido, devuelve la velocidad vertical: hacia la altura de flotación (pies = superficie − `HEAD_ABOVE_WATER` para dejar la cabeza fuera) con un máximo de subida y de bajada y una suavidad que no es un golpe seco; con Shift desciende a `DIVE_SPEED`. La subida natural al soltar Shift tiene su propio máximo (`SURFACE_SPEED`). Permitido: en agua (no lava), no montado, no con el jetpack puesto y volando, no planeando, no en escalera, no en vuelo creativo, no derribado, muerto ni trepando. En el suelo de un fondo bajo no hace nada especial. Espacio no cambia: lo sigue llevando Minecraft.
- **Dónde se aplica (cliente):** como el jetpack, `SwimClient.tick` en `END_CLIENT_TICK` pone la velocidad vertical resultante; el desplazamiento horizontal sigue siendo el de Minecraft.
- **Estado hacia el puente:** dos bits nuevos en las banderas del `State`, sin cambiar tamaño ni versión del protocolo: `FLAG_IN_WATER` = 1<<16 (el jugador está en el agua y la flotabilidad actúa: flotando, nadando o buceando) y `FLAG_UNDERWATER` = 1<<17 (la cabeza está bajo el agua). `FLAG_SWIM` (1<<3, ya existente: la pose de nadar rápido de Minecraft) pasa a significar el crol; hoy el puente no lo usa. Ficheros dorados sin cambios (las banderas son datos).
- **Puente:** `BodyReactions` gana la animación de natación según las banderas y el movimiento: `Tread` (flotar), `Swim` (braza), `Crawl` (crol), `Dive` (zambullida), `Under` (bajo el agua) y `Exit` (salir del agua), reproducidas con animaciones parciales hechas con `AddAnimation` (grupo e ids según la investigación) y mantenidas mientras dura la bandera. La velocidad horizontal se lee del propio `State`, como ya hace el mando para andar y correr. Como en el jetpack, el mando del puente no debe andar ni correr a CJ en el agua (la caminata empezaría bajo la pose y la cortaría).
- **Efectos:** ninguno en esta pieza (sin partículas ni sonido de agua, ver arriba).

## 3. Investigación previa (spike, parte del plan)

Antes de fijar nada hay que comprobar en GTA real, como en el trepado y el jetpack:
- **Las animaciones:** según `gta-reversed` el grupo de animaciones de natación es el 71 (`swim`) con `SWIM_BREAST` 311, `SWIM_CRAWL` 312, `SWIM_DIVE_UNDER` 313, `SWIM_UNDER` 314, `SWIM_GLIDE` 315 y `SWIM_JUMPOUT` 316; flotar (`SWIM_TREAD`) es el id 14 del grupo de peds. Hay que comprobar cuáles se ven como flotar, nadar, bucear y salir, su duración, banderas y si se mantienen o son bucles.
- **La postura del cuerpo:** en GTA CJ se tumba en horizontal al nadar (la tarea gira al ped); ver si la animación sola ya lo tumba sobre el esqueleto o si hace falta inclinar el cuerpo dibujado como con el jetpack (la inclinación al nadar en horizontal), y a qué altura queda respecto a los pies del jugador de Minecraft.
- **Cómo se reproducen:** si `AddAnimation` parcial funciona con estas animaciones (como con la del jetpack) o si hay que hacer algo distinto para que no queden las animaciones base de pie debajo.
- **Seguridad:** que ninguna de las pruebas mueve al CJ de GTA ni deja una animación pegada tras soltar la marioneta.

## 4. Números iniciales

Todos constantes con nombre, ajustables jugando y anotados en `docs/swim-results.md`: `HEAD_ABOVE_WATER` ≈ 0,2 bloques por encima de la superficie con los ojos (pies a ~1,45 bloques por debajo de la superficie), `DIVE_SPEED` ≈ −0,08 bloques por tick (1,6 bloques por segundo), `SURFACE_SPEED` ≈ 0,12 bloques por tick (2,4 por segundo), suavidad de la flotabilidad ≈ 0,25 por tick.

## 5. Casos límite

- **Agua poco honda:** con el suelo por encima de la altura de flotación el jugador pisa el fondo y camina como siempre; no se le obliga a hundirse ni a flotar. Solo se corrige hacia arriba cuando está por debajo de la altura de flotación.
- **Caer al agua desde lo alto:** se hunde un poco por la inercia y la flotabilidad lo devuelve a la superficie con suavidad; sin daño de caída (como en Minecraft).
- **Salir a una orilla:** Espacio y el movimiento de Minecraft lo sacan, con la animación de salida breve; en tierra firme todo vuelve a la normalidad.
- **Cascadas, columnas de burbujas y corrientes:** las fuerzas de Minecraft siguen actuando; la flotabilidad solo pone la velocidad vertical hacia su objetivo (se aplica encima de las corrientes verticales).
- **Waterlogged (bloques con agua):** cuentan como agua.
- **Ahogarse:** si el jugador se queda sin aire mientras bucea, sigue ahogándose como en Minecraft; soltar Shift lo devuelve a la superficie.
- **Muerte, derribo, montar, escalera, vuelo creativo, trepar y jetpack:** la flotabilidad no actúa (esos casos son de otras piezas).
- **Servidor externo (varios jugadores):** el servidor puede considerar que flotar es un movimiento ilegal y expulsar al jugador; se documenta como límite (el hito se prueba en un mundo de un jugador).

## 6. Pruebas

- **Automáticas** (con mutaciones): `SwimRules` (cada condición que lo impide, la velocidad hacia la altura de flotación con sus topes, el buceo con Shift, la subida al soltarlo, el fondo bajo), las banderas nuevas en Python, C++ y Java, `BodyReactions` con las animaciones nuevas y su orden de prioridad con las demás (muerte, derribo, trepar, jetpack, caída), la regla del mando que no anda a CJ en el agua.
- **En vivo con GTA real** (con el usuario): entrar al agua y flotar sin hundirse, flotar quieto, nadar, nadar rápido, bucear con Shift y mirando hacia abajo, soltar Shift y subir, salir en una orilla, agua poco honda, caer al agua desde alto, ahogarse, sin GTA, que el trepado, el jetpack, caer, morir y los derribos siguen funcionando, y que la partida de GTA queda intacta.
- **Revisión final independiente** y una pasada de arreglos.

## 7. Riesgos

- **La postura horizontal del nado:** si la animación no tumba a CJ sola, hay que inclinar el cuerpo dibujado, con riesgo de que se meta en el suelo o se vea raro con la cámara; la investigación y la prueba en vivo lo ajustan.
- **Flotabilidad y servidor integrado:** controlar la velocidad vertical cada tick puede chocar con el servidor (el jetpack y el trepado dieron buena señal).
- **Pelea con la tarea de nado o de caída de GTA:** ya ocurrió con el trepado y el jetpack; se resuelve con el mismo patrón (mantener la animación encima, desvanecer las de GTA que compitan).
- **Muchos estados de animación** (seis) que cambian rápido con el movimiento: hace falta que las transiciones no parpadeen (como pasó con el jetpack al bajar escalones); las reglas puras de transición lo cubren con una histéresis.
- **Banderas nuevas en el protocolo:** tocan los tres lados (Python, C++, Java).
