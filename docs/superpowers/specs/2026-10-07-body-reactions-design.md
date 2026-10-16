# Reacciones del cuerpo: dolor, caída libre, suelo y muerte (diseño)

Fecha: 2026-10-07. Segunda pieza de M4 (`docs/m4-effects-results.md` cierra la primera; el orden acordado con el usuario es: efectos de F5, **esta pieza**, trepar, y nadar al final). Continúa el diseño principal (`2026-10-05-cj-in-minecraft-design.md`) y los hitos de combate (`docs/m3-results.md`, `docs/melee-results.md`).

## 1. Objetivo y criterio de éxito

Que el cuerpo de CJ reaccione a lo que le pasa al jugador de Minecraft con las animaciones y la voz de GTA: **frase de dolor** al recibir daño, **animación de caída libre** al caer, **caer al suelo y levantarse** tras un aterrizaje duro, un golpe fuerte o una explosión, y **quedarse tumbado hasta reaparecer** al morir.

**El hito está cumplido cuando**, con GTA corriendo y un mundo de un jugador, y el jugador dibujado como CJ:
1. **Dolor:** al recibir daño (golpe de mob, flecha, caída, fuego, explosión) CJ suelta una frase de dolor con la voz de GTA, con un enfriamiento para no repetirla en cada tick; no suena si no se ha recibido daño.
2. **Caída libre:** al caer desde más de ~2,5 bloques (o con velocidad vertical fuerte), CJ cancela lo que estuviera haciendo y pone la animación de caída de GTA hasta tocar el suelo.
3. **Suelo:** si el aterrizaje hace daño por caída, o una explosión o un golpe fuerte derriba al jugador, CJ cae al suelo con su animación de GTA, se queda unos segundos y se levanta con la animación de levantarse de GTA. Durante todo ese tiempo el jugador de Minecraft queda **inmovilizado** (decisión del usuario, como en GTA).
4. **Muerte:** al morir, CJ cae con la animación de muerte de GTA y se queda en el suelo hasta que el jugador reaparece; al reaparecer vuelve a su postura normal.
5. **Seguridad de la partida de GTA:** nada de esto mata, hiere, hospitaliza ni deja inmovilizado de verdad al jugador de GTA (CJ es el jugador real de la partida). Las animaciones son solo visuales.
6. Sin GTA, o si algo falla, el juego no se cae y el jugador de Minecraft se comporta como siempre.

**Decisión del usuario (2026-10-07):** el jugador de Minecraft se inmoviliza mientras CJ está derribado (cayendo al suelo, tumbado y levantándose).

**Fuera de este hito:** trepar (siguiente pieza, que reutiliza la base de animaciones de esta), nadar (al final), la ropa, el ragdoll con física y otras reacciones al daño (retroceder, cubrirse).

## 2. Arquitectura

Mismo reparto de siempre: Minecraft decide, GTA anima y dibuja a CJ.

- **Estado desde Minecraft (cliente → puente):** el `State` ya trae `flags`; se añaden banderas nuevas (`falling`, `knocked`, `dead`) y un contador de golpes recibidos en bits libres de las banderas (un cambio del contador es el evento «me han dañado»). Sin cambiar el tamaño ni la versión del protocolo; los ficheros dorados se actualizan.
- **Qué lo dispara (lado Minecraft):**
  - `falling`: el jugador no está en el suelo y su velocidad vertical es fuerte o ha caído más de ~2,5 bloques (`fallDistance`).
  - `knocked`: el servidor decide un derribo (daño por caída por encima de un umbral, explosión, golpe fuerte) y lo manda al cliente por la cola en memoria que ya usa M3 (el servidor integrado vive en el mismo proceso); dura unos segundos fijos con los datos del catálogo.
  - `dead`: el jugador está muerto (pantalla de muerte).
  - contador de daño: sube cuando la vida del jugador baja (comparando entre ticks).
- **Puente (`BridgeCore`, nuevo módulo de animaciones):** con esas banderas decide una **animación de cuerpo** (`caída libre`, `caer al suelo`, `tumbado`, `levantarse`, `muerto`) y la ejecuta sobre CJ reproduciendo animaciones concretas de GTA con `CAnimManager::BlendAnimation` (o la tarea del juego que la investigación muestre segura), mezclándolas por encima de lo que haga el mando y quitándolas al acabar. Una máquina de estados pura (`BodyReactions`) decide qué animación toca y cuándo; la parte que llama al juego es mínima.
- **Voz de dolor (puente):** al subir el contador de daño, el puente hace que el ped de CJ diga una frase de dolor con la voz del juego (la investigación fija cómo se pide, p. ej. `CAEPedSpeechAudioEntity` con el contexto de dolor), con enfriamiento.
- **Inmovilización (cliente):** mientras `knocked` (y la caída al suelo y el levantarse), un mixin de la entrada anula el movimiento y el salto del jugador (como el que ya limita la velocidad y el salto con el arma en la mano).
- **Cliente:** el resto del dibujo no cambia: CJ se dibuja con la pose que GTA manda, que ahora incluye esas animaciones.

## 3. Investigación previa (spike, parte del plan)

Antes de fijar nada hay que comprobar en GTA real, como en M2, la minigun, el lanzacohetes y el cuerpo a cuerpo:
- **Reproducir una animación sobre el CJ marioneta:** si `BlendAnimation(clump, grupo, id, retardo)` aplicada al clump del ped se refleja en el esqueleto que leemos, y cómo evitar que las tareas del propio juego (andar, estar quieto) la pisen; cuánto dura y cómo se detiene.
- **Los ids de animación** de caída libre, caída al suelo, levantarse y muerte (grupo `ped` y las de `FALL_*`, `KO_*`, `GETUP`, etc.): cuáles se ven bien y su duración.
- **Las tareas del juego** (`CTaskComplexInAirAndLand`, `CTaskComplexFallAndGetUp`, `CTaskComplexDie`): si alguna es segura sobre el jugador real (sin dañarlo ni activar wasted/busted) o hay que quedarse con la animación sola; esperado: la muerte solo como animación.
- **La voz de dolor:** cómo se pide una frase de dolor del CJ y el contexto exacto; si suena sin más con un ped parado y qué enfriamiento hace falta.
- **Qué ve el cuerpo en el aire:** el CJ marioneta está anclado en GTA; comprobar que la animación de caída se refleja en la pose aunque no caiga en el mundo de GTA.
- **Seguridad:** que ninguna de las pruebas deja a CJ muerto, inmovilizado o con vida cambiada en la partida de GTA tras soltar la marioneta.

## 4. Números iniciales

Umbral de caída libre ~2,5 bloques o velocidad vertical ≤ −0,6 bloques por tick; derribo con daño por caída ≥ 3 (1,5 corazones), explosión o golpe de empuje fuerte; tiempo en el suelo ~2,5 s antes de levantarse; enfriamiento de la voz de dolor ~1,5 s. Todo en `weapons.json` o en un fichero de configuración del mod, ajustable jugando, y anotado en el documento de resultados.

## 5. Casos límite

- **Sin GTA o con la pose sin refrescar:** nada cambia; el jugador de Minecraft se comporta y se dibuja como siempre (sin inmovilizarlo si CJ no se dibuja).
- **Muerte con la pantalla de muerte:** CJ queda tumbado; al reaparecer, el estado de muerte se limpia y CJ vuelve a su postura.
- **Daño repetido durante el derribo:** no encadena derribos; el temporizador del suelo no se reinicia más de una vez por derribo.
- **Caída corta o agua:** caer en agua o desde poca altura no activa la caída libre ni el derribo.
- **Vehículos, nadar, trepar y volar:** no se activan reacciones de suelo mientras el jugador monta, nada, está en una escalera o vuela (esos casos son de otras piezas o ya van como Minecraft).
- **Cambio de ítem, golpear o disparar mientras se levanta:** las armas no se pueden usar derribado; al levantarse se retoman.
- **El puente se suelta (GTA sin estado):** se quitan las animaciones y CJ queda como estaba.

## 6. Pruebas

- **Automáticas** (con mutaciones): `BodyReactions` (qué animación toca según las banderas, el temporizador del suelo, derribo sin encadenar, muerte que dura hasta limpiar), enfriamiento de la voz, codificación de las banderas y del contador en Python, C++ y Java con ficheros dorados, el cálculo de caída libre y de derribo en el lado Minecraft (puro: umbrales) y la regla de inmovilización.
- **En vivo con GTA real** (con el usuario): dolor al recibir daño de varias fuentes con enfriamiento; caída libre desde varias alturas; aterrizaje con y sin daño; derribo por explosión y por golpe fuerte con inmovilización y levantarse; muerte, quedarse tumbado y reaparecer; que la partida de GTA queda intacta (vida, estado) tras soltar la marioneta; sin GTA; que no hay reacciones al nadar o montar.
- **Revisión final independiente** y una pasada de arreglos.

## 7. Riesgos

- **La marioneta es el jugador real de GTA:** cualquier tarea de muerte o daño del juego puede activar wasted, busted o cambiar la vida; por eso la muerte y la caída van como animación y la investigación lo comprueba antes de planear.
- **Animar por encima del mando:** las tareas de movimiento del propio juego pueden pisar o cancelar la animación; puede hacer falta pausar el mando o la tarea mientras dura.
- **La voz:** puede no existir una forma limpia de pedir la frase de dolor; la investigación lo decide con el audio del juego como plan B (otra acción sonora de CJ).
- **Banderas nuevas en el protocolo:** tocan los tres lados (Python, C++, Java) y los ficheros dorados.
- **Pantalla de muerte:** el jugador no controla nada y la cámara no muestra a CJ igual; se comprueba en vivo cómo se ve.
