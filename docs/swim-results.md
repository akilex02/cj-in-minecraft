# Nadar y bucear — Resultados

Fecha: 2026-10-08. Spec: `docs/superpowers/specs/2026-10-08-swimming-design.md`; plan: `docs/superpowers/plans/2026-10-08-swimming.md`; investigación: `docs/swim-spike.md`.

## Qué hace
En agua honda (al menos un bloque sobre los pies) el jugador **no se hunde**: una flotabilidad lo lleva a una altura de flotación y se queda allí con la cabeza fuera, respirando; también en agua muy honda.
- **Flotar quieto:** CJ en vertical con la animación de flotar de GTA; el agua le llega a los hombros (`TREAD_DEPTH` 1,4).
- **Nadar:** braza al moverse; con la espalda a ras de agua (`FLOAT_DEPTH` 1,15). **Crol** con la tecla de correr (Ctrl) mientras se mueve, más rápido (`CRAWL_SPEED` 0,22 bloques por tick); Minecraft cancela el sprint con la cabeza fuera del agua, así que se lee la tecla, no el sprint.
- **Bucear:** mantener **Shift** baja a 1,6 bloques por segundo (zambullida de 1,07 s y después nadar bajo el agua); soltarlo devuelve a la superficie a 1,6 bloques por segundo, directamente a flotar o nadar (la animación de GTA de salir, con las manos en alto, se quitó porque no se veía bien). Nadar rápido mirando hacia abajo (Minecraft) también se dibuja con CJ.
- **Espacio** sigue siendo el de Minecraft (nadar hacia arriba y salir en una orilla). En un charco de un bloque se camina por el fondo; con lava no actúa.
- **No flota:** montado, con el jetpack volando, planeando con elytra, en escalera, con vuelo creativo, derribado, muerto o trepando.

## Cómo está hecho
- Reglas puras con pruebas y mutaciones: `SwimRules` (condiciones, altura de flotación según nadar o flotar quieto, velocidad vertical, buceo con Shift, el crol, el deseo de movimiento y la profundidad mínima).
- `SwimClient` (cliente) lee la superficie del agua de la columna del jugador y aplica la velocidad vertical cada tick; manda al puente `FLAG_IN_WATER` (1<<16), `FLAG_UNDERWATER` (1<<17), `FLAG_WATER_MOVING` (1<<18) y `FLAG_SWIM` (el crol). En pausa no actúa. El servidor olvida la distancia de caída mientras el jugador toca agua (Minecraft ya la reinicia en el agua: es redundante e inocuo).
- El puente (`BodyReactions`) elige la animación con histéresis (250 ms mínimos entre dos poses de superficie) y las reproduce como animaciones parciales con `AddAnimation`, quitando las banderas de movimiento (`0x64`) que hacían que el juego las borrara en menos de un segundo; la de «bajo el agua» se hace de bucle. El bloque de animaciones `swim` de GTA (grupo 71) se pide al juego y se carga **al empezar a controlar a CJ** (hasta entonces el grupo no existe y `AddAnimation` tumba el juego, por eso se comprueba que el grupo está creado antes de usarlo). El mando no anda ni corre a CJ en el agua.
- El renderer quita el avance propio de las animaciones de nado (hasta 2,8 bloques por brazada, que se reinicia en cada bucle) para que CJ nade en sitio y se mueva el jugador; las animaciones ya lo tumban en horizontal, así que no hace falta inclinar el cuerpo.

## Lo medido y ajustado con el usuario
- Animaciones (todas con el aspecto correcto): flotar (grupo 0, id 14), braza 311, crol 312, zambullida 313, bajo el agua 314, deslizar 315 y salir 316 (esta última sin usar).
- Ajustes tras probarlo: la subida a la superficie de 2,4 a 1,6 bloques por segundo; flotar quieto más abajo (el agua a los hombros, con los brazos bajo el agua); el crol más rápido (de 2,4 a 4,4); sin la animación de salir; CJ dibujado también en la pose de nado de Minecraft.
- Verificado en vivo: flotar sin hundirse, braza, crol, buceo con Shift y mirando abajo, subida, salir en una orilla, charcos, ahogarse, lava (no flota), trepar, jetpack, armas y que CJ vuelve a su postura normal al salir del agua.

## Revisión final
Revisión independiente (sin críticos). Arreglado en una pasada, con prueba en rojo antes cuando hay forma de probarlo:
- **Mantener Espacio en la superficie:** subía los pies casi hasta la superficie y la profundidad del agua sobre ellos bajaba de 1 bloque, así que CJ dejaba de nadar y «andaba sobre el agua». Ahora, ya nadando y sin tocar el fondo, sigue nadando aunque quede poca agua sobre los pies (`SwimRules.deepEnough`).
- **La referencia del avance de la animación** se recalculaba a mitad de la natación y CJ quedaba dibujado hasta 2 bloques lejos del jugador. Ahora solo se recalcula cuando la pose anterior ya se ha desvanecido (400 ms), y el dibujo sigue quitando el avance mientras se desvanece (`SwimRules.poseFading`, `baselineStale`).
- **Fuerzas verticales de Minecraft:** la flotabilidad pisaba las columnas de burbujas, el tridente con impulso y el saltito para subir un escalón desde el agua, que dejaba atascado en orillas con escalón. Ahora un empuje fuerte o un choque horizontal se dejan a Minecraft (`SwimRules.leavesToMinecraft`).
- **Cascadas y pozos de agua de 1×1:** el agua que cae ya no cuenta como agua para nadar, así que no te sube sola ni te deja flotando arriba de un pozo.
- **Bucear a crol mirando hacia abajo:** la flotabilidad lo deshacía; ahora, con la pose de nado y mirando más de 20° hacia abajo, el buceo es el de Minecraft (`SwimRules.lookDiving`).
- **`PrepareSwim`** solo pedía el bloque de animaciones una vez por proceso: si al cargar una partida se descarga, la natación dejaba de funcionar en silencio. Ahora se mira en cada engagement y se vuelve a pedir si falta.
- Documentación al día.

Pendientes menores (aplazados): la pausa a mitad de nadar (GTA quita la pose y la zambullida se repite al volver); el jugador de Minecraft que parpadea en canales de un bloque; cuevas inundadas con techo; la zambullida puede repetirse un fotograma justo antes de pasar a nadar bajo el agua; el momento exacto en que se lee la pose para quitar el avance; el crol en agua de 1 a 1,6 de fondo y al moverse hacia atrás; el reinicio de la referencia al desconectarse; `SwimClient` sin pruebas propias (su lógica de decisión está en `SwimRules`).

## Decisiones y límites
- Sin sonidos de agua (las salpicaduras de GTA existen y quizá se puedan disparar, se decide aparte), sin lava, sin modelo mojado.
- En un servidor externo con varios jugadores, el servidor puede considerar que flotar es un movimiento ilegal; el hito se probó en un mundo de un jugador.
