# Reacciones del cuerpo (dolor, caída libre, suelo, muerte) — Resultados

Fecha de la prueba: 2026-10-07. Spec: `docs/superpowers/specs/2026-10-07-body-reactions-design.md`; plan: `docs/superpowers/plans/2026-10-07-body-reactions.md`; investigación de GTA: `docs/body-spike.md`; la primera pieza de M4 (efectos de F5) en `docs/m4-effects-results.md`.

## Qué hace ahora el juego (CJ dibujado en F5, con GTA respondiendo)
- **Dolor:** al recibir daño CJ suelta una frase de dolor de GTA (`CPed::Say`, contexto 345; 344 si el golpe además lo derriba; 343 al morir), como mucho una cada 1,5 s. Sin daño no suena.
- **Caída libre:** al caer más de 2,5 bloques (o con velocidad vertical ≤ −0,6 bloques/tick) pone la animación de caída libre de GTA (`FALL_FALL`, 120). Saltos normales, escalones, escaleras y agua no la activan.
- **Derribo:** una caída con 3 o más de daño, un golpe de 8 o más o una explosión derriba a CJ. Por caída o golpe cae **pegado al suelo boca abajo** (`KO_SKID_BACK`, 26) y se levanta empujando con los brazos (`GETUP_FRONT`, 115); por una **explosión cercana** sale despedido **boca arriba** (`KO_SKID_FRONT`, 24) y se levanta desde ahí (`GETUP_0`, 112). Tiempo abajo **1,2 s** (a petición del usuario; antes 2,5 s) y **1,5 s** levantándose: el jugador de Minecraft está inmovilizado esos 2,7 s (sin andar, saltar, atacar, usar ni minar) y recupera el control al quedar de pie.
- **Muerte:** CJ cae boca abajo y se queda tumbado hasta reaparecer; al reaparecer vuelve a su postura.
- **Seguridad de la partida de GTA:** solo se reproducen animaciones y voces (nunca las tareas de morir o caer del juego, que activarían *wasted* u hospital). Al soltar la marioneta se quitan las animaciones puestas. Comprobado en vivo: la vida y la postura de CJ en GTA quedan intactas.

## Cambios pedidos por el usuario durante las pruebas
- **Boca abajo como en GTA:** las primeras animaciones elegidas (`FALL_BACK` 124 y `FALL_FRONT` 125) eran variantes de la caída en el aire; con dos clips del propio GTA y pruebas en Minecraft se eligieron las de derrape (ver `docs/body-spike.md`).
- **Explosión cercana boca arriba:** propuesta del usuario; añade `FLAG_BLASTED` al protocolo y un tipo de derribo (`KnockdownRules.kind`: ninguno, normal, explosión).
- **Tiempo de derribo 1,2 s** en vez de 2,5 s.
- **Efectos de F5 (`CjEffects`):** se quitó el Salto II y se dejó solo la Velocidad I; un Salto II de esa forma que ya tuviera el jugador se retira solo en cuanto el servidor sincroniza.
- **El salto en F5:** la tecla de saltar llega a Minecraft con un retraso (`JumpDelay.TICKS`, ajustado por el usuario a **0,10 s**, 2 ticks, tras probar 0,5, 0,2 y 0,15 s) y, a la vez, el estado manda `INTENT_JUMP` en el instante de pulsar para que la animación de saltar de GTA arranque ya (`JumpIntent`, `PadDriver` con una guarda de 0,4 s contra el doble salto). El retraso solo se aplica con CJ dibujado en tercera persona; en primera persona el salto es inmediato.

## Pruebas automáticas
- **Protocolo:** `FLAG_FALLING`/`FLAG_KNOCKED`/`FLAG_DEAD`/`FLAG_BLASTED`, el contador de daños (bits 8 a 11) y `INTENT_JUMP` en Python, C++ y Java, sin cambiar tamaño ni versión ni los ficheros dorados; 14 mutantes muertos.
- **Reglas puras de Minecraft** (`dev.cjmc.body`): `FallRules`, `HurtCounter` (no cuenta el golpe mortal ni reaparecer), `KnockdownRules` (incluye el tipo), `KnockdownTimer` (sin encadenar, reloj que retrocede); `CjEffects`, `SyncPacer`, `DelayedSignal`, `JumpDelay` y `JumpIntent`; todas con mutaciones muertas (se simplificaron comprobaciones redundantes que dejaban mutantes equivalentes).
- **Nativas del puente:** `BodyReactions` (una animación por flanco, voz con enfriamiento y contador que da la vuelta, muerte directa, tipo de derribo visto al empezar, reloj que retrocede), `RunBody`/`Release`/cambio de ped del núcleo y el mando con el salto anunciado; direcciones del exe `BlendAnimation` (0x4D4610) y `CPed::Say` (0x5EFFE0) verificadas.
- Python y Java en verde.

## Verificación en vivo (GTA SA real + Minecraft real, con el usuario)
- Dolor (suena al recibir daño y solo entonces), caída libre (también sin falsas activaciones), derribo por caída y por explosión con las posturas elegidas, inmovilización de 2,7 s y recuperación al estar de pie, muerte tumbado boca abajo hasta reaparecer, y la partida de GTA intacta. ✔
- Salto sincronizado en F5 con 0,10 s de retraso. ✔

## Decisiones y límites
- Sin ragdoll con física, sin trepar ni nadar (piezas siguientes), sin ropa; sin derribos ni caída libre montando, nadando, en escalera, volando ni planeando con elytra (reglas puras `KnockdownRules.eligible` y `FallRules`).
- El retraso del salto y los umbrales de caída y derribo, los tiempos y los contextos de voz son constantes con nombre, fáciles de ajustar.
- Con una conexión a otro servidor no hay derribos por daño (la cola es del servidor integrado); sí caída, dolor y muerte.

## Revisión final
Revisión independiente (sin críticos). Arreglado en una pasada, cada punto con prueba en rojo antes:
- Derribado no se puede apuntar, disparar ni pegar por el canal de GTA (`ControlsMapper.intents` con `frozen`).
- Elytra: no activa la caída libre; ningún derribo montando, en fluido, en escalera, volando o planeando.
- Los derribos ya no encadenan: 3 s de inmunidad tras levantarse (`KnockdownTimer.IMMUNE_MS`).
- Al perder el ped (pantalla de carga) el puente olvida la animación sostenida sin escribir en su memoria.

Pendientes menores (aplazados): umbral mínimo de daño para explosiones lejanas; validar la asociación contra la lista de animaciones del clump en lugar de vtable+id; y los menores de estilo anotados por el revisor.
