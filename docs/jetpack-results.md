# Jetpack — Resultados

Fecha: 2026-10-07. Spec: `docs/superpowers/specs/2026-10-07-jetpack-design.md`; plan: `docs/superpowers/plans/2026-10-07-jetpack.md`; investigación: `docs/jetpack-spike.md`.

## Qué hace
Un ítem de pechera, el **jetpack** (`/give @s cj_fabric:jetpack`; clic derecho lo equipa, o se arrastra al slot de la pechera). Con él puesto, **mantener espacio** eleva al jugador como un cohete y **soltarlo** lo deja caer despacio; W, A, S y D lo mueven por el aire. Combustible infinito, como en GTA. Con el jetpack puesto no hay daño de caída.
- **En GTA:** CJ lleva el modelo del jetpack de GTA en la espalda (siempre que lo lleve puesto) y, en el aire o al empujar, la pose de GTA del jetpack. El cuerpo se inclina hacia donde se mueve, y salen llamas y humo detrás mientras empuja.
- **Sonido:** el motor del propio jetpack de GTA, disparado desde el puente con la entidad de audio del ped; se actualiza cada fotograma y se corta al aterrizar, al quitar el jetpack, al morir o al soltar la marioneta.
- **No vuela:** montado, en agua o lava, planeando con elytra, en escalera, con vuelo creativo, derribado, muerto o trepando.

## Cómo está hecho
- Reglas puras con pruebas y mutaciones: `JetpackRules` (cuándo funciona, subida con aceleración y máximo, caída terminal, control horizontal, inclinación, daño de caída).
- `JetpackClient` (cliente) aplica las reglas cada tick, resetea la distancia de caída y manda al puente `FLAG_JETPACK` (1<<14: en el aire o empujando con el jetpack puesto) y `FLAG_JETPACK_THRUST` (1<<15: espacio pulsado). `JetpackItem` implementa `Equipment` (pechera); el servidor cancela el daño de caída con `ALLOW_DAMAGE`.
- **La pose se crea con `AddAnimation` y se marca parcial** (flags 0x11, peso a mano), nunca con `BlendAnimation`: esta borra las animaciones base de CJ (se quedaba tumbado al quitarla y el juego se cerraba unos segundos después). Se quita con ritmo −4 y autoeliminar. Ver `docs/jetpack-spike.md`.
- Mientras vuela, `PadDriver` no anda, corre ni salta a CJ (la caminata empezaba bajo la pose y la cortaba, y un salto habría levantado de verdad la marioneta en GTA); el cliente no manda `INTENT_JUMP` con el jetpack puesto.
- El modelo (`jetpack.dff` con sus toberas, nodo 3 del esqueleto, como en el juego) se convierte con el mismo conversor que las armas (`ATTACH_NODES`) y se dibuja con `WeaponRig.drawModel`; la inclinación es una rotación del cuerpo alrededor de los pies.

## Lo medido y ajustado con el usuario
- La pose es `JETPACK_IDLE` (grupo 70, id 3): una postura casi estática; no hay animación de empuje distinta (el juego inclina el cuerpo por código), así que el empuje se distingue con la inclinación, las llamas y el sonido.
- El audio de GTA suena grave y se corta limpiamente; entre «reposo» y «empuje» el cambio es sutil.
- Constantes finales: `MAX_RISE` 0,4 bloques por tick, `RISE_ACCEL` 0,16, **`TERMINAL_FALL` −0,3** (el valor inicial de −0,15 se sentía lento), `HORIZONTAL_SPEED` 0,35, `AIR_CONTROL` 0,2, inclinación máxima 25°. Colocación del modelo: la del juego, (0,1; 0,08; 0) y giro de 90° sobre Y, que el usuario dio por buena viéndola.
- Verificado en vivo: equipar y quitar, modelo bien colocado, subir y soltar, moverse en el aire, aterrizar sin daño, sonido, pose sin cortes al avanzar en cualquier dirección, inclinación hacia donde se va, caminar con normalidad en el suelo.

## Revisión final
Revisión independiente (sin críticos). Arreglado en una pasada, con prueba en rojo antes cuando hay forma de probarlo:
- **Daño de caída al quitárselo en el aire:** el servidor acumula la distancia de caída en cada bajada y solo la borra al tocar el suelo, así que quitar el jetpack a poca altura tras un vuelo largo podía hacer un daño enorme; ahora el servidor la olvida cada tick mientras se lleva puesto (`JetpackRules.resetsFallDistance`).
- **Inclinación hacia donde se va:** se calculaba con la dirección de la cámara y no con la del cuerpo dibujado, y solo era correcta yendo hacia delante (`JetpackRules.localLean` con el giro del cuerpo).
- **Pose y sonido a trompicones:** bajar escalones con el jetpack puesto lo encendía y apagaba en cada peldaño; ahora solo se vuela tras un empuje y hasta aterrizar (`JetpackRules.flyingNext`).
- **Trepar con el jetpack puesto:** el trepado le quitaba el vuelo y junto a un muro bajo el espacio trepaba en vez de despegar; con el jetpack puesto ya no se trepa (`ClimbRules.permitted`).
- **Memoria del puente:** la comprobación de la asociación guardada también compara el grupo de animación (la pose del jetpack tiene el id 3 de la animación quieta del ped, en otro grupo), con un aviso en el registro si no coincide.
- **En pausa:** el motor y las llamas se cortaban a medias; ahora no se vuela ni empuja mientras el juego está en pausa.
- Documentación al día (significado de `FLAG_JETPACK`, una sola pose, el sonido en reposo y en empuje).

Pendientes menores (aplazados): más de 32 asociaciones en la lista del clump; un sonido que GTA suelte a mitad de vuelo (se recupera al aterrizar); las banderas del vuelo no se reinician al desconectarse (un fotograma); el comentario sobre qué parámetro de `UpdateJetPack` es reposo y cuál empuje.

## Decisiones y límites
- Sin combustible ni modo de flotar quieto, sin apuntar ni disparar con el jetpack al estilo de GTA, sin sonido propio (el de GTA funciona).
- En un servidor externo con varios jugadores, el servidor puede considerar el vuelo ilegal y expulsar al jugador; el hito se probó en un mundo de un jugador.
