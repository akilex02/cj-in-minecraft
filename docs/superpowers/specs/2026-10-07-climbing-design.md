# Trepar muros de 2 y 3 bloques (diseño)

Fecha: 2026-10-07. Tercera pieza de M4 (`docs/m4-effects-results.md` y `docs/body-results.md` cierran las anteriores; el orden acordado con el usuario es: efectos de F5, reacciones del cuerpo, **esta pieza**, y nadar al final). Reutiliza la base de animaciones de `2026-10-07-body-reactions-design.md` (`BodyReactions`, `BlendAnimation`, banderas del `State`).

## 1. Objetivo y criterio de éxito

Que CJ trepe muros de **2 y 3 bloques** de altura con las animaciones de GTA: salta, se agarra al borde y se sube, como en San Andreas.

**Decisión del usuario (2026-10-07):** el trepado es **automático**: se activa saltando contra el muro (espacio mientras se empuja contra él), sin tecla propia.

**El hito está cumplido cuando**, con GTA corriendo, un mundo de un jugador y el jugador dibujado como CJ:
1. Al saltar contra un muro de 2 o de 3 bloques con espacio libre encima, CJ trepa con su animación de GTA y acaba de pie sobre el borde.
2. Un muro de 1 bloque sigue siendo el salto normal de Minecraft; uno de 4 o más no se trepa.
3. Si encima del borde no caben los 2 bloques del cuerpo, o no hay superficie donde pisar, no trepa.
4. No trepa montando, nadando, en una escalera, volando, planeando con elytra, derribado ni muerto.
5. Mientras trepa el jugador queda inmovilizado (como al estar derribado) y no puede apuntar, disparar ni pegar por el canal de GTA.
6. Si algo bloquea la subida a mitad, el trepado se cancela y el jugador queda donde esté, sin atravesar bloques ni quedar atrapado.
7. Seguridad de la partida de GTA: solo animación; nada de esto cambia el estado real de CJ en GTA tras soltar la marioneta.
8. Sin GTA, o si algo falla, el juego no se cae y el jugador se comporta como siempre (sin trepado automático si CJ no se dibuja).

**Fuera de este hito:** nadar (al final), vallas y plantas, colgarse de bordes más altos, trepar escaleras con animación propia, ropa y ragdoll.

## 2. Arquitectura

Mismo reparto de siempre: Minecraft decide y mueve, GTA anima y dibuja a CJ.

- **Detección (cliente, lógica pura `dev.cjmc.climb.ClimbRules`):** dados la altura del primer hueco libre sobre el suelo en el sentido de la mirada, la altura libre por encima y la superficie del borde, decide si hay trepado y de qué altura (2 o 3). Las lecturas del mundo (colisiones de bloques) las hace una capa fina de Minecraft; las reglas son puras y se prueban con mutaciones.
- **Disparo:** la señal de salto ya pasa por `JumpDelay`/`JumpIntent`. Si en el momento del salto hay empuje hacia un muro trepable, el salto vanilla se sustituye por el trepado (un mixin en la entrada, como el del salto retrasado).
- **Movimiento (cliente, `ClimbPath`, puro):** un arco con duración fija por altura: primero sube verticalmente hasta el borde y luego avanza hasta quedar de pie sobre él. Cada tick se calcula la posición y se mueve al jugador solo si la caja de colisión cabe; si no cabe, se cancela. Las duraciones salen de la animación de GTA elegida en la investigación.
- **Estado hacia el puente:** una bandera nueva `CLIMBING_WALL` (1<<13) en el `State`, sin cambiar tamaño ni versión del protocolo; ficheros dorados de Python, C++ y Java actualizados. La bandera `CLIMBING` existente (escaleras) no cambia.
- **Puente:** `BodyReactions` gana la animación `Climb2`/`Climb3` (o una sola con la altura ajustada, según la investigación) con `BlendAnimation`, mantenida mientras dura la bandera y retirada al terminar, igual que la caída libre.
- **Inmovilización:** `BodyClient.frozen()` incluye el trepado, de modo que los mixins de entrada y de ataque ya existentes lo respetan.

## 3. Investigación previa (spike, parte del plan)

Como en los hitos anteriores, antes de fijar nada hay que comprobar en GTA real:
- Qué animaciones de trepar hay (hipótesis: `CLIMB_JUMP`, `CLIMB_PULL`, `CLIMB_STAND` y variantes del grupo de peds), sus ids, duraciones y si se ven bien sobre la marioneta anclada.
- Cuál sirve para 2 y cuál para 3 bloques, o si una sola vale ajustando la altura del jugador de Minecraft.
- Que se logra solo con animación, sin lanzar la tarea de trepar del juego (que movería a CJ en el mundo de GTA).
- Que la animación se mezcla bien con la de caída libre al saltar y con la del salto de GTA (`INTENT_JUMP`).

## 4. Números iniciales

Altura trepable: 2 o 3 bloques (más de 1,25, hasta 3,0); hueco libre encima: 2 bloques; alcance del muro: 0,8 bloques desde el centro del jugador a la cara del muro (0,6 en el diseño inicial; ampliado al permitir agarrarse sin estar pegado). Duraciones del arco: las de la animación elegida. Todo en constantes con nombre, ajustable jugando y anotado en `docs/climb-results.md`.

## 5. Casos límite

- **Sin GTA o pose sin refrescar:** nada cambia; sin trepado automático.
- **Salto contra un muro de 1 bloque:** salto normal. De 4 o más: salto normal, sin trepar.
- **Techo bajo o bloque encima del borde:** no trepa.
- **Bloque que cambia a mitad (se rompe o se pone):** se vuelve a comprobar cada tick; si la caja ya no cabe, se cancela.
- **Daño durante el trepado:** el dolor suena; un derribo no interrumpe el trepado ya iniciado y se aplaza hasta acabarlo (decisión provisional, a confirmar en vivo).
- **Muerte a mitad:** la animación de muerte gana y el trepado se limpia.
- **Esprintar o sneak:** el trepado se activa igual; sneak lo impide para no trepar al borde de un precipicio sin querer.

## 6. Pruebas

- **Automáticas** (con mutaciones): `ClimbRules` (alturas 1, 2, 3 y 4, espacio libre encima, superficie, estado del jugador), `ClimbPath` (trayectoria, duración, cancelación), la bandera en Python, C++ y Java con ficheros dorados, `BodyReactions` con la animación nueva, y la regla de inmovilización.
- **En vivo con GTA real** (con el usuario): muros de 2 y 3 bloques, uno de 1 y otro de 4, techo bajo, cancelación a mitad, trepar con y sin arma, con sneak, después de caer; que la partida de GTA queda intacta tras soltar la marioneta; sin GTA.
- **Revisión final independiente** y una pasada de arreglos.

## 7. Riesgos

- **Movimiento del jugador de Minecraft:** mover una caja de colisión por un arco sin atravesar bloques es delicado; por eso el arco se valida cada tick y se cancela si no cabe.
- **Sincronía con la animación:** el arco debe coincidir con la animación de GTA, que no es mía; la investigación fija las duraciones y se ajusta jugando.
- **Conflicto con el salto retrasado:** el trepado debe decidirse con el salto de `JumpDelay` (0,10 s); se prueba que no haya salto doble ni trepado fallido.
- **Detección falsa:** trepar sin querer es molesto; las alturas y el empuje contra el muro son estrictos y ajustables.
