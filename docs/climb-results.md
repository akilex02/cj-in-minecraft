# Trepar muros de 2 y 3 bloques — Resultados

Fecha: 2026-10-07. Spec: `docs/superpowers/specs/2026-10-07-climbing-design.md`; plan: `docs/superpowers/plans/2026-10-07-climbing.md`; investigación: `docs/climb-spike.md`.

## Qué hace
Saltando contra un muro de 2 o 3 bloques, CJ trepa con las animaciones de GTA (`CLIMB_JUMP` 128, `CLIMB_PULL` 130, `CLIMB_STAND` 131, encadenadas a los 0, 0,5 y 1,3 s; en total 2,1 s) y acaba de pie sobre el borde.
- **Desde el suelo:** a hasta 0,8 bloques del muro (centro del jugador a la cara), mirando hacia él y empujando hacia delante, al pulsar espacio. Se acerca al muro durante el salto inicial.
- **En el aire:** subiendo en un salto (velocidad vertical ≥ −0,1 bloques por tick) y al alcance del muro, se agarra del borde sin pulsar nada más. Cayendo no se agarra.
- **No trepa:** muros de 1 o de 4 bloques, sin hueco de 2 bloques encima o sin superficie completa donde pisar, con sneak, montado, nadando, en escalera, volando, planeando, derribado o muerto.
- **Mientras trepa** el jugador queda inmovilizado (`BodyClient.frozen`), no dispara ni pega por el canal de GTA, y un derribo del servidor espera a que acabe.

## Cómo está hecho
- Reglas puras con pruebas y mutaciones: `ClimbRules` (altura, hueco, `canGrab`, alcance), `ClimbPath` (tiempos y tramos: colgarse, tirón, subir, avance) y `ClimbSession`.
- `ClimbController` (cliente) lee el mundo, mueve al jugador cada tick comprobando que la caja cabe (si no cabe se cancela), con un bloqueo de 0,6 s tras cancelar. La bandera `FLAG_WALL_CLIMB` (1<<13) del `State` dura lo que la animación.
- El renderer **quita el movimiento propio de la animación** (el esqueleto sube y avanza por sí solo y lo devuelve al borrarse la animación): la animación se dibuja en el sitio y el jugador hace todo el movimiento físico.
- El puente (`BodyReactions` + `WinIo::KeepBodyAnim`) encadena las tres animaciones y, mientras dura el trepado, mantiene la nuestra encima: la tarea de salto de GTA mezcla sus animaciones (116 a 123) y desvanece las nuestras, así que se desvanecen las suyas y se vuelve a pedir la nuestra si se borró.

## Lo que se midió y se ajustó con el usuario
- El esqueleto de la animación sube ~1 bloque en el tirón y 0,94 más al levantarse, y avanza ~0,6; todo desaparece al borrar la animación (en el espacio del modelo: x adelante, z arriba). Primero se intentó compensar al jugador por esa diferencia, pero lo metía dentro del bloque; el enfoque definitivo es dibujar la animación en el sitio.
- Constantes finales (`ClimbPath`): salto 500 ms, tirón hasta 1300 ms, pies sobre el borde a 1700 ms, total 2100 ms, asentamiento 450 ms, `HANG_BLOCKS` 2,2, `PULL_BELOW` 0,3. `ClimbRules`: `REACH` 0,8, `HUG` 0,3, `SKIN` 0,01, `MIN_RISING_SPEED` −0,1.
- Verificado en vivo por el usuario: muros de 2 y 3 (desde el suelo y saltando), 1 y 4 (salto normal), techo bajo (no trepa), sneak (no trepa), quitar el bloque a mitad (se cancela), mantener espacio (un solo trepado), y que la partida de GTA queda intacta.
- Último detalle: un corte mínimo de la animación en el instante del salto, que el usuario da por aceptable.

## Revisión final
Revisión independiente (sin críticos). Arreglado en una pasada, con prueba en rojo antes cuando hay forma de probarlo:
- Derribado no puede trepar (antes llegaba `frozen = false` fijo a `ClimbRules.eligible`): `ClimbRules.permitted`, `BodyClient.knockedDown()`.
- Un muro de 4 se trepaba saltando contra él (la altura se medía desde los pies en el aire): el muro se cuenta desde el suelo del salto (`ClimbRules.referenceY`, `groundRow`).
- Un teletransporte o un cambio de mundo ya no arrastra al jugador de vuelta: el trepado se cancela si cambia el jugador o el mundo, si se desvía más de 0,5 bloques de donde lo puso el controlador (`ClimbRules.strayed`) o al desconectarse.
- El puente ya no lee ni escribe una asociación de animación que GTA ya borró (`HeldIsLive`: se recorre la lista del clump antes).
- Con un arma que prohíbe saltar o con el sneak forzado al golpear tampoco se trepa (`permitted`).

Pendientes menores (aplazados): agarrarse al caminar por un borde (cualquier subida con velocidad vertical ≥ −0,1); un pequeño salto visual si el trepado se cancela a mitad (la animación de GTA se desvanece sin que el renderer siga quitándole su movimiento propio); el reloj hacia atrás en `ClimbController` (no puede ocurrir con `nanoTime`); `ClimbController` sin pruebas propias (su lógica de decisión está en `ClimbRules`).

## Decisiones y límites
- Sin vallas ni plantas, sin colgarse de bordes más altos, sin quedarse colgado esperando una segunda pulsación de espacio.
- Un derribo durante el trepado se aplaza hasta acabar; la muerte lo cancela.
- El corte mínimo restante viene del salto de GTA, que empieza al pulsar espacio.
