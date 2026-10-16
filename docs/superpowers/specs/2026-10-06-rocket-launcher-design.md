# Lanzacohetes con explosiones (diseño)

Fecha: 2026-10-06. Continúa M3 (`2026-10-06-m3-damage-design.md`, `docs/m3-results.md`) y la minigun (`2026-10-06-minigun-design.md`, `docs/minigun-results.md`). Segundo hito de la hoja de ruta de la minigun, sección 0.

## 1. Objetivo y criterio de éxito

Que CJ pueda llevar y disparar el **lanzacohetes RPG** de GTA SA (arma 35), con su modelo real en la mano, y que el cohete sea un **proyectil real de Minecraft** que vuela por el mundo y **explota**, rompiendo bloques y dañando mobs por las reglas del servidor.

**El hito está cumplido cuando**, con GTA corriendo y un mundo de un jugador:
1. Existe el ítem del lanzacohetes; llevarlo en la mano pone el arma en GTA, con su modelo real bien agarrado, y cambiar de ítem o arma lo retira sin dejar nada colgado ni a CJ con las manos como si lo sostuviera.
2. Clic derecho apunta y clic izquierdo dispara, con la animación, el sonido y la recarga de GTA (cargador de un cohete).
3. Cada disparo lanza una entidad cohete desde los ojos del jugador que vuela recta, deja estela de humo y llamas, y explota al chocar con un bloque o un mob, o al agotar su alcance.
4. La explosión es de **potencia 6** (decisión del usuario: «más fuerte»), atribuida al jugador: rompe bloques con los drops normales, daña en área a mobs y al propio jugador si está cerca, con empuje, y usa el sonido y las partículas de Minecraft. Respeta las reglas del mundo sobre explosiones.
5. El cohete propio que GTA dispara en su mundo no estorba: no mata ni lanza al CJ de marioneta ni rompe nada que se vea en Minecraft.
6. Sin GTA, o si algo falla al crear el cohete o la explosión, el juego no se cae y el resto del mod sigue.

**Decisiones del usuario (2026-10-06):** el cohete es un proyectil real con viaje (no un impacto instantáneo) y la explosión es la más fuerte de las propuestas (potencia 6).

**Fuera de este hito:** el RPG con búsqueda de calor (36), el lanzallamas (37), las granadas, el modelo 3D del cohete (va invisible con partículas), el fuego, y los cohetes de mobs hacia el jugador. Quedan el cuerpo a cuerpo y M4.

## 2. Arquitectura

Mismo camino que las demás armas (GTA manda el evento `Shot`, el cliente lo deduplica y lo entrega al servidor integrado), con una rama nueva en el servidor para las armas con proyectil.

- **Catálogo (`weapons.json`):** el RPG entra con `gta: 35`, modelo `rocketla`/el nombre real que se compruebe en `gta3.img`, y dos campos nuevos obligatorios en todas las armas: `explosionPower` (0 = sin explosión) y `projectileSpeed` (bloques por segundo; 0 = bala instantánea, como hasta ahora). Se validan al cargar (rangos razonables, nada de NaN ni valores absurdos).
- **Resolución (`ShotResolver`):** si el arma tiene `projectileSpeed > 0`, en vez de lanzar rayos crea una entidad `RocketEntity` en el mundo del servidor, en el origen y con la dirección de los ojos más una dispersión mínima, a esa velocidad y sin gravedad. Las demás armas no cambian.
- **`RocketEntity` (servidor):** avanza por tick; en cada tick comprueba el choque con bloques (sin fluidos) y con entidades en el tramo recorrido, con la misma lógica de «lo más cercano gana» de `PelletRay` y la longitud máxima del alcance (unos 120 bloques). Al chocar o agotar el alcance, explota y desaparece. Si el mundo o el jugador dejan de existir, desaparece sin explotar.
- **Explosión:** `world.createExplosion` con el jugador como fuente, potencia 6, sin fuego, con el tipo que respeta la regla del mundo (se fija con `javap` y en vivo en el plan).
- **Cliente:** la entidad se registra en el inicializador común (el servidor integrado la necesita) y en el cliente con un renderizador vacío; las partículas de la estela (humo y llama) salen del tick de la entidad en el cliente.
- **Puente (`weapon_driver.h`):** `WeaponModelId(35)`, `WeaponSlot(35) = 7` y, si la investigación lo exige, anular el cohete propio de GTA.

## 3. Investigación previa (spike, parte del plan)

Antes de fijar nada hay que comprobar en GTA real, como en M2 y en la minigun:
- **El modelo:** nombre y estructura del RPG en `gta3.img` (marcos, texturas, partes hijas como el cohete cargado o el fogonazo); si el conversor actual lo admite o necesita ampliarse.
- **La tarea de apuntar:** que el RPG use la misma `CTaskSimpleUseGun` que las demás armas; si no, se documenta y se decide el ajuste mínimo (o se para y se informa).
- **El cohete de GTA:** a dónde va, si explota, y si eso afecta al CJ de marioneta (muerte, empuje, ruido). Si estorba, se anula quitando su proyectil o haciendo invulnerable al CJ marioneta; si solo suena en el audio de GTA, se deja.
- **Cadencia y recarga:** rondas por segundo del contador de disparos, tamaño del cargador, y la munición dada (el puente da munición abundante), sin que la recarga produzca disparos falsos en `ShotCounter`.
- **Movimiento:** velocidad apuntando, si deja esprintar y si dispara en movimiento (se copia a `weapons.json` jugando).

## 4. Números iniciales

`explosionPower` 6, `projectileSpeed` unos 40 bloques/s, alcance unos 120 bloques, `damage` 0 (el daño lo hace la explosión), dispersión mínima (0.01), `knockback` 0 (el empuje lo hace la explosión). Movimiento como las armas pesadas (apuntar lento, sin esprintar, quieto al disparar). Todo ajustable jugando y anotado en el documento de resultados.

## 5. Casos límite

- **Disparos apilados:** cada ronda es un cohete; con cargador de uno y la recarga de GTA no hay ráfagas, pero el tope de entidades vivas por jugador (p. ej. 8) evita una avalancha si el contador de GTA se descontrola.
- **Cohete que choca contra el propio jugador al salir:** el origen está en los ojos y el cohete ignora al jugador que lo dispara durante sus primeros ticks.
- **Explosión en un mundo con las explosiones desactivadas por regla:** el cohete explota sin romper bloques y sigue dañando mobs.
- **Descarga de mundo, muerte del jugador o cambio de dimensión con cohetes en vuelo:** desaparecen sin explotar, sin dejar entidades colgadas.
- **Cliente sin servidor integrado:** como en M3, no se resuelve nada y se registra un único aviso.
- **Sin GTA:** el ítem existe pero no pasa nada.

## 6. Pruebas

- **Automáticas** (con mutaciones): catálogo con los campos nuevos (obligatorios, rangos), `WeaponModelId(35)` y `WeaponSlot(35)` en las nativas del puente con la invariante «toda arma con modelo tiene hueco», conversión del modelo con la instalación real (se salta si no hay) y la lógica pura del vuelo del cohete (avance por tick, choque contra el más cercano, alcance, ignorar al dueño al salir), separada de las clases de Minecraft.
- **En vivo con GTA real** (con el usuario): arma en la mano bien agarrada; apuntar y disparar con la recarga de GTA; el cohete vuela y explota contra suelo, pared, mob y aire; potencia y cráter; daño propio cerca; humo; regla de explosiones; cambio de arma sin dejar a CJ con las manos de arma; que el cohete de GTA no estorba y que GTA no se cierra.
- **Revisión final independiente** y una pasada de arreglos, como en M3 y la minigun.

## 7. Riesgos

- **El cohete de GTA** puede afectar al CJ de marioneta o al estado del puente; la investigación lo decide antes de planear.
- **Entidad nueva** (registro en servidor y cliente, renderizador vacío, sincronización): se comprueba compilando y en vivo.
- **Potencia 6** rompe mucho: probar lejos de construcciones.
- **Modelo del RPG** con varias piezas o texturas: puede exigir ampliar el conversor (ya admite varias texturas y piezas hijas opcionales).
- **Cargador de un cohete:** la recarga no debe contarse como disparo; `ShotCounter` ya lo evita al ignorar rellenos.
