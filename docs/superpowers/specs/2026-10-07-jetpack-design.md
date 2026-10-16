# Jetpack (diseño)

Fecha: 2026-10-07. Continúa M4 (`docs/m4-effects-results.md`, `docs/body-results.md` y `docs/climb-results.md` cierran las piezas anteriores) y reutiliza la base de animaciones de `2026-10-07-body-reactions-design.md` y el patrón del trepado (`2026-10-07-climbing-design.md`). Es la primera de dos piezas pedidas por el usuario; la segunda, **coches y motos**, tendrá su propia investigación de viabilidad y su propio diseño después de esta.

## 1. Objetivo y criterio de éxito

Que CJ pueda volar con el **jetpack de GTA** en Minecraft: un ítem que se equipa en el slot de la pechera de armadura y, mientras se mantiene espacio, eleva al jugador como un cohete.

**Decisiones del usuario (2026-10-07):**
- Es un ítem de **pechera** (el slot de la armadura del pecho).
- Combustible **infinito**, como en GTA.
- Control **como un cohete**: mantener espacio sube; soltarlo deja caer despacio; sin modo de flotar quieto.
- **Con sonidos de jetpack**, si se puede.

**El hito está cumplido cuando**, con GTA corriendo, un mundo de un jugador y el jugador dibujado como CJ:
1. Existe el ítem `jetpack`; ponérselo en el slot de la pechera pone el jetpack en la espalda de CJ, bien colocado, y quitárselo lo retira.
2. Con el jetpack puesto y en el aire (o en el suelo), mantener espacio eleva al jugador hasta una velocidad máxima de subida; soltarlo lo deja caer despacio hasta una velocidad terminal baja. W, A, S y D lo mueven por el aire.
3. CJ muestra la pose de GTA del jetpack (la investigación halló una sola postura, `JETPACK_IDLE`: el empuje se distingue con la inclinación del cuerpo, las llamas y el sonido).
4. Se ven llamas y humo detrás de CJ mientras empuja, y se oye el sonido del jetpack mientras dura el empuje (ver §5).
5. Con el jetpack puesto no se recibe daño de caída.
6. Sin jetpack, o sin GTA, el juego se comporta como siempre; ni los demás ítems ni las demás armaduras cambian.
7. Seguridad de la partida de GTA: solo animación y modelo; nada de esto mueve, hiere ni cambia el estado real del CJ de GTA tras soltar la marioneta.

**Fuera de este hito:** combustible o durabilidad, apuntar y disparar con el jetpack al estilo de GTA, un modo de flotar quieto, y los coches y motos.

## 2. Arquitectura

Mismo reparto de siempre: Minecraft decide y mueve, GTA anima y dibuja a CJ. **Solo animación y modelo, nunca la tarea de jetpack real de GTA** (`CTaskSimpleJetPack` movería al CJ de GTA por su mundo y tomaría el mando).

- **Ítem (`dev.cjmc`):** `JetpackItem`, una pechera registrada como los demás ítems del mod (modelo de ítem y textura propios, generados como los iconos de las armas). Detectar que está puesto es una consulta del slot de la pechera.
- **Física (cliente, reglas puras `dev.cjmc.jetpack.JetpackRules`):** dadas la velocidad vertical, si se mantiene espacio y si está permitido, devuelve la nueva velocidad vertical (sube hasta `MAX_RISE`; al soltar, cae hasta `TERMINAL_FALL`). Permitido: jetpack puesto, no montado, no en fluido, no planeando con elytra, no en escalera, no en vuelo creativo, no derribado ni muerto, no trepando. El movimiento horizontal en el aire usa un factor de control más alto que el de Minecraft (reglas puras también). Un mixin sobre la entrada aplica el resultado al jugador.
- **Sin daño de caída (servidor):** un evento `ALLOW_DAMAGE` de Fabric cancela el daño de caída mientras se lleve el jetpack puesto (el servidor calcula su propia caída; no basta con tocar el cliente).
- **Estado hacia el puente:** dos bits nuevos en las banderas del `State`, sin cambiar tamaño ni versión del protocolo: `FLAG_JETPACK` = 1<<14 (vuela con el jetpack: lo lleva puesto, está permitido y empuja, o sigue en el aire tras un empuje; caminar o bajar un escalón no cuenta) y `FLAG_JETPACK_THRUST` = 1<<15 (está empujando ahora). Ficheros dorados sin cambios (son datos).
- **Puente:** `BodyReactions` gana la animación `JetpackIdle`/`JetpackThrust` según las banderas, reproducida con `BlendAnimation` (grupo y ids según la investigación) y mantenida mientras dura la bandera, retirada al bajar. Como en el trepado, la tarea de salto o caída de GTA puede pelear con ella: se mantiene encima y se desvanecen las animaciones de GTA que compitan (`KeepBodyAnim`).
- **Modelo:** el jetpack de GTA (modelo de `gta3.img`, si está) se convierte con el conversor actual y se dibuja en la espalda de CJ, enganchado a un hueso del esqueleto como el arma a la mano (`WeaponRig`/`WeaponAttach`).
- **Efectos:** partículas de llama y humo de Minecraft detrás de CJ (en el punto de las toberas) mientras hay empuje.
- **Sonido:** ver §5.
- **Inmovilización y combate:** el jetpack no inmoviliza. Las armas y los puños se comportan como siempre (apuntar con el jetpack no es parte de este hito).

## 3. Investigación previa (spike, parte del plan)

Antes de fijar nada hay que comprobar en GTA real, como en el trepado:
- **Las animaciones del jetpack:** qué asociaciones de animación existen para el jetpack (hipótesis: un grupo propio, o ids `JETPACK_IDLE`, `JETPACK_FWD`, `JETPACK_L/R`, `JETPACK_BACK` del grupo de peds), sus ids, su duración y si se ven bien sobre el CJ anclado; si hay una de empuje hacia arriba y otra de flotar o caer, o si hay que usar las de moverse.
- **Cómo se reproducen:** si `BlendAnimation` con el grupo 0 basta o hay que pedir el grupo del jetpack (`CAnimManager::AddAnimation(clump, group, id)` o la tabla de grupos).
- **El modelo:** dónde está (id de modelo, nombre en `gta3.img`, su textura), qué estructura tiene (marcos, texturas) y si el conversor actual lo admite o necesita ampliarse; el hueso donde engancharlo y el desplazamiento que se ve bien.
- **El sonido:** cómo suena el jetpack de GTA y si se puede reproducir desde el puente (por el audio del propio juego, como la voz de dolor, que ya funciona); si no, hay que generar uno propio (§5).
- **Seguridad:** que ninguna de las pruebas mueve al CJ de GTA ni deja el jetpack puesto o una animación pegada tras soltar la marioneta.

## 4. Números iniciales

Todos constantes con nombre, ajustables jugando y anotados en `docs/jetpack-results.md`:
- Subida máxima `MAX_RISE` ≈ 0,4 bloques por tick (8 bloques por segundo), con una aceleración que no sea un golpe seco (≈ 0,08 por tick mientras se mantiene espacio).
- Caída terminal `TERMINAL_FALL` ≈ −0,3 bloques por tick (6 bloques por segundo; con 3 la caída se sentía lenta al probarlo).
- Velocidad horizontal en el aire ≈ 0,35 bloques por tick (7 bloques por segundo) con control del aire alto.
- Sin daño de caída con el jetpack puesto.

## 5. Sonido

El usuario pidió sonidos de jetpack «si se puede». Orden de preferencia, decidido tras la investigación:
1. **El audio del propio GTA**, disparado desde el puente con la maquinaria del juego, como `CPed::Say` para la voz de dolor: es lo más fiel, no copia ningún fichero y se oye mientras GTA corre.
2. **Un sonido propio generado**, si el audio de GTA no se puede disparar de forma limpia: un bucle de motor sintetizado por el proyecto (sin tomar nada de los ficheros de GTA), registrado como un evento de sonido de Minecraft y reproducido en bucle con volumen según el empuje, en el cliente.
En ambos casos: suena mientras se vuela con el jetpack, en reposo y en empuje (el motor de GTA, que no se oye muy distinto entre los dos), se detiene al quitarse el jetpack, al morir o al perder GTA, y nunca se acumulan sonidos superpuestos.

## 6. Casos límite

- **Sin GTA o con la pose sin refrescar:** el jetpack sigue existiendo como ítem y el vuelo funciona como física de Minecraft (sin animación ni modelo de GTA).
- **Quitarse el jetpack en pleno vuelo:** la bandera baja, la animación se quita y el jugador cae con la física normal (con el daño de caída normal, porque ya no lleva el jetpack).
- **Morir o ser derribado:** la animación del cuerpo gana (muerte o derribo); el vuelo se corta.
- **Trepar, montar, nadar, planear, escalera, vuelo creativo:** el jetpack no actúa (esos casos son de otras piezas o ya van como Minecraft).
- **Techo y bloques:** el movimiento sigue las colisiones normales de Minecraft; no atraviesa bloques.
- **Servidor externo (varios jugadores):** el servidor puede considerar el vuelo ilegal y expulsar al jugador; se documenta como límite (el hito se prueba en un mundo de un jugador).

## 7. Pruebas

- **Automáticas** (con mutaciones): `JetpackRules` (aceleración y límite de subida, caída terminal, cada condición que lo impide, control horizontal), las banderas nuevas en Python, C++ y Java, `BodyReactions` con las animaciones nuevas y su encadenado con las de caída y trepado, la regla de daño de caída (pura), y la conversión del modelo con la instalación real (se salta si no hay).
- **En vivo con GTA real** (con el usuario): equipar y quitar el jetpack, subir y soltar, moverse en el aire, aterrizar sin daño, animaciones y modelo bien colocados, llamas, sonido sin acumularse, que trepar, caer, morir y los derribos siguen funcionando, y que la partida de GTA queda intacta.
- **Revisión final independiente** y una pasada de arreglos.

## 8. Riesgos

- **El modelo del jetpack puede no estar donde se espera** o necesitar ampliar el conversor; la investigación lo decide antes de planear.
- **Pelea con la tarea de salto o caída de GTA:** ya ocurrió con el trepado; se resuelve con el mismo patrón (`KeepBodyAnim`).
- **El sonido:** el audio del juego puede no dejarse disparar limpio; hay un plan B propio.
- **Movimiento del jugador de Minecraft:** controlar la velocidad vertical cada tick puede chocar con el servidor integrado (rebotes o «flying» ilegal); el trepado dio buena señal, y la investigación y la prueba en vivo lo confirman.
- **Banderas nuevas en el protocolo:** tocan los tres lados (Python, C++, Java).
