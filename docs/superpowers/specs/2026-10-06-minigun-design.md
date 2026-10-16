# Minigun (diseño)

Fecha: 2026-10-06. Continúa M2 (`2026-10-05-m2-combat-design.md`, `docs/m2-results.md`) y M3 (`2026-10-06-m3-damage-design.md`, `docs/m3-results.md`).

## 0. Contexto: hoja de ruta

Tras M3 el usuario pidió cuerpo a cuerpo, minigun, lanzacohetes con explosiones y el resto de M4 (agacharse, trepar, nadar, ropa, reacciones al daño, ragdoll). Es demasiado para una sola spec, así que cada pieza lleva su propio ciclo de spec, plan y verificación en vivo, en este orden:

1. **Minigun** (esta spec).
2. Lanzacohetes con explosiones.
3. Cuerpo a cuerpo (puño y bate; exige encontrar el evento de golpe de GTA).
4. M4, en sub-hitos: agacharse y daño/ragdoll, luego trepar y nadar, y por último ropa.

## 1. Objetivo y criterio de éxito

Que CJ pueda llevar y disparar la **minigun** de GTA SA, con su modelo real en la mano, y que sus disparos dañen mobs y bloques por las reglas del servidor, como las otras seis armas de fuego.

**El hito está cumplido cuando**, con GTA corriendo y un mundo de un jugador:
1. Existe el ítem de la minigun; llevarla en la mano pone el arma en GTA, con su modelo real bien agarrado, y cambiar a otro ítem o arma la retira sin dejar nada colgado.
2. Mantener el clic izquierdo dispara en ráfaga continua con la cadencia, el giro de cañones (en la animación de GTA) y el sonido de GTA; el clic derecho apunta, como con las otras armas.
3. Cada ronda hace daño a mobs y bloques por el camino de M3 (`ShotResolver`), con dispersión propia, y el daño por segundo queda en torno al doble del de la AK-47.
4. El movimiento copia a GTA (velocidad apuntando, si se puede esprintar o disparar en movimiento), medido en vivo.
5. Una ráfaga larga no pierde rondas, no satura las partículas ni la cola del cliente, y GTA no se cierra ni al dar ni al quitar el arma.

**Decisión del usuario (2026-10-06):** el modelo es **fijo**, sin animar el giro de los cañones (una sola articulación, como las demás armas). El giro se puede añadir en un hito posterior.

**Fuera de este hito:** giro visible de los cañones en el modelo, lanzacohetes, explosiones, cuerpo a cuerpo, y el resto de M4.

## 2. Arquitectura

No hay arquitectura nueva: la minigun entra por el mismo camino que las armas de M2 y M3.

- **Catálogo (`weapons.json`):** una entrada nueva `minigun` con `gta: 38`, modelo `minigun` y los campos de siempre (`damage`, `range`, `pellets`, `spread`, `blockPower`, `knockback`, `aimSpeed`, `sprintHeld`, `sprintAiming`, `moveWhileFiring`, `holdOffset`, `holdRotation`). `CjItems` registra el ítem desde el catálogo.
- **Puente (`weapon_driver.h`):** `WeaponModelId(38)` devuelve 362, para que el modelo se cargue por *streaming* antes de dar el arma (sin modelo cargado GTA se cae).
- **Conversor:** `cjconvert/weapons.py` añade `minigun` (modelo 362) a `WEAPON_MODELS`; el `.glb` sale en `weapons/minigun.glb`.
- **Disparo y daño:** sin cambios. Cada ronda que reporta GTA (`Shot`) es un rayo resuelto en el servidor con su dispersión, `timeUntilRegen = 0` y el empuje del arma.

## 3. Investigación previa (spike, parte del plan)

Antes de fijar números hay que comprobar en GTA real, igual que en M2:
- **La tarea de apuntar:** que la minigun use la misma `CTaskSimpleUseGun` (tipo 1017) que las demás armas. Si usa otra, se documenta y se decide el ajuste mínimo del puente; si no cabe, se para y se informa.
- **Cadencia real** (rondas por segundo) y **tamaño del cargador**, desde el contador de disparos y la munición.
- **Retraso de arranque** del giro de cañones: los eventos `Shot` llegan más tarde que el clic y eso debe verse bien en el juego.
- **Movimiento:** velocidad apuntando y disparando, si permite esprintar y si dispara en movimiento (se mide la velocidad de la animación en m/s, como en `docs/m2-spike.md`).
- **Modelo 362:** número de marcos y de texturas; el conversor admite hoy una malla con un solo marco raíz identidad y una textura, así que puede hacer falta fundir los cañones en una sola malla.

## 4. Números iniciales

Se fijan tras medir la cadencia: `damage` por ronda tal que el daño por segundo sea ~2× el de la AK-47 (7 de daño × su cadencia); `spread` media (en torno a 0.05); `blockPower` baja (en torno a 0.15) para que una ráfaga larga rompa piedra en unos segundos pero la tierra rápido; `knockback` baja por ronda (en torno a 0.05); `range` 80. Todo ajustable jugando, y queda anotado en el documento de resultados.

## 5. Casos límite

- **Cadencia mayor que el tope del cliente:** hoy el cliente procesa hasta 8 rondas por tick y encola hasta 64. Si la cadencia medida lo supera, el plan sube el tope con una prueba; no se tiran rondas en silencio.
- **Partículas:** las ráfagas largas dibujan menos partículas por perdigón (como ya hace la escopeta), para no saturar el cliente.
- **Munición:** el puente da la munición con la que GTA ya da el resto de armas; si se acaba, GTA deja de disparar y no llegan más eventos.
- **Cambio de arma, inventario y pantallas:** sin cambios respecto a M2 (el puente quita solo lo suyo y devuelve el arma del jugador).
- **Sin GTA:** el ítem existe pero no pasa nada, como con las otras armas.

## 6. Pruebas

- **Automáticas** (con mutaciones): catálogo con la entrada nueva (valores razonables, id de GTA único), `WeaponModelId(38)` en las pruebas nativas del puente, conversión del modelo (prueba con el modelo real de la instalación, que se salta si no hay), y el tope de rondas del cliente si cambia.
- **En vivo con GTA real** (con el usuario): minigun en la mano bien agarrada; clic derecho apunta y clic izquierdo dispara en ráfaga continua; daño a zombi, cerdo y creeper con el tiempo que tarda en matar; rotura de tierra y piedra; velocidad y movimiento como en GTA; cambiar de arma durante una ráfaga; que GTA no se cierra.
- **Revisión final independiente** y una pasada de arreglos, como en M2 y M3.

## 7. Riesgos

- **Tarea distinta para la minigun:** puede requerir tocar el puente más de lo previsto; la investigación lo decide antes de planear.
- **Modelo con varios marcos:** puede exigir ampliar el conversor (fundir marcos en una malla).
- **Cadencia muy alta:** la cola de rondas, el tope por tick y el coste de dibujar partículas se comprueban midiendo.
- **Equilibrio:** una cadencia alta con daño mal puesto mata todo al instante; se ajusta con el usuario jugando.
