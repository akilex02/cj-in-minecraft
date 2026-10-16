# Skate en Minecraft: el cliente (diseño)

Fecha: 2026-10-11. Continúa `docs/skate-spike-results.md` y `skate-host/README.md` (el motor de Skate 3 corre aparte en "modo anfitrión" y habla UDP). Es un **extra opcional** del mod: sin el motor ni los archivos de Skate 3 del usuario, el mod base (GTA + Minecraft) sigue igual. Debe funcionar en Windows y Linux.

## 1. Objetivo y criterio de éxito
Poder patinar dentro de Minecraft con la skin de CJ, con la física, los trucos y las animaciones de Skate 3 (GTA no interviene).

**Decisiones del usuario (2026-10-11):**
- **Solo mando.** Las palancas y botones del mando van directos a las 18 acciones del motor. Sin mando no se patina.
- **Un ítem "Skateboard"** activa el modo (con el clic derecho); se craftea.
- **El usuario arranca el motor** de Skate; el mod solo se conecta por UDP (127.0.0.1:34600 por defecto) y avisa si no responde.

**Hito M1 (se juega, sin pose de Skate):** con el motor abierto, un mando y el ítem en la mano, el clic derecho pone al jugador sobre el tablero donde está; el jugador se mueve por el mundo de Minecraft controlado por el mando (empujar, girar, saltar, frenar), choca con los bloques, y el mundo de bloques cercano llega al motor y se actualiza al romper o poner bloques. El mismo gesto (o soltar el ítem) lo baja.
**Hito M2 (se ve):** CJ posado con los huesos de Skate (reasignados a su esqueleto) sobre un tablero.
**Hito M3:** integración con `start_games.sh`, documentación y empaquetado.

## 2. Arquitectura
Todo en `dev.cjmc.skate` (cliente), salvo el ítem (común).

- **`SkateProtocol`** (puro): codifica la entrada, el mundo (regiones de cuadriláteros), borrar región y teletransporte; decodifica la pose (36 huesos, raíz y tablero). Formato en `skate-host/README.md`; se prueba contra bytes dorados generados por `tools/skate_host_probe.py`.
- **`SkateInput`** (puro): mando de GLFW (el diseño de mando estándar de GLFW) → 18 acciones del motor, con zona muerta. Las palancas suben con el valor positivo (GLFW las da al revés), los gatillos pasan de [-1, 1] a [0, 1].
- **`BlockQuads`** (puro): dado "¿es sólido este bloque/caja?", produce los cuadriláteros de las caras expuestas con la orientación que exige el motor (triángulos de una sola cara; ver `README` del parche) y los parte en regiones de a lo más 1000.
- **`SkateWorld`** (cliente): cuenta las secciones (16³) alrededor del patinador (radio de 3 secciones en horizontal y 2 en vertical), arma los cuadriláteros de cada una con un presupuesto por tick, los manda por regiones, borra las que se alejan y rehace una sección cuando cambia un bloque dentro de ella. Los bloques salen de la forma de colisión del bloque (`getCollisionShape`), cada caja con sus 6 caras; se omiten las caras tapadas por un cubo completo vecino y los fluidos.
- **`SkateRide`** (cliente): el estado del modo. Al activar manda el teletransporte (posición y rumbo del jugador), cada tick manda la entrada del mando y mueve al jugador a la posición del tablero (como el auto: sin gravedad, sin salto ni ataque ni uso vanilla, el ratón mueve solo la cámara). Si el motor no responde en un segundo avisa y se sale.
- **Ítem `skateboard`** (común): ítem de herramientas con receta; el clic derecho alterna el modo; cambiar de ítem de la mano lo termina.
- **M2:** `SkateRig` (puro: reasignado de huesos por rotación mundial respecto a la pose de reposo, con jerarquía de CJ y de Skate) y el modelo del tablero; el renderizador de CJ usa esa pose en vez de la de GTA mientras dura el modo.

Coordenadas: el mundo del motor **es el de Minecraft** (1 bloque = 1 m, mismos ejes, sin transformación): los cuadriláteros y la posición del tablero se mandan y se leen tal cual.

## 3. Detalles que importan
- Una cara de bloque es un cuadrilátero con las esquinas en el orden que da la normal hacia afuera: (esquina 2 − esquina 0) × (esquina 1 − esquina 0). Un suelo es (x0,z0), (x1,z0), (x1,z1), (x0,z1) con x1 > x0 y z1 > z0.
- Id de región = sección (sx, sy, sz) empaquetada más 4 bits de parte, para repartir una sección con más de 1000 caras.
- El motor reconstruye todo el mundo al cambiar una región (0.5 ms para 1714 cuadriláteros), así que se manda por cambios, no por tick.
- Si el jugador está en creativo con vuelo, o en un vehículo, o muerto, el modo no se activa.
- El motor es GPL-3.0: este cliente no lo enlaza ni lo incluye, solo habla con él por UDP.

## 4. Pruebas
- `SkateProtocol`: bytes dorados contra el probe de Python; rechazo de datagramas rotos.
- `SkateInput`: cada botón y eje, zona muerta, inversión de palancas, gatillos.
- `BlockQuads`: un suelo, una pared, un cubo aislado (6 caras), dos cubos vecinos (las caras tapadas no salen), un escalón, partición en regiones de 1000.
- En juego (M1): activar, patinar, chocar con un muro de bloques, romper un bloque del suelo y caer, soltar el ítem.

## 5. Riesgos
- La forma de mando depende del mapeo estándar de GLFW (hay mandos no reconocidos).
- El rendimiento del mallado en secciones con mucho detalle (se limita por presupuesto de tick).
- El reasignado de huesos (M2) puede necesitar ajustes por hueso al ver el resultado.
