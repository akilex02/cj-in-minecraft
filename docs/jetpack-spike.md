# Jetpack — Investigación de animación, sonido y modelo en GTA

Fecha: 2026-10-07. Mediciones en GTA SA 1.0 US real, con Minecraft abierto (el CJ dibujado en F5 muestra el resultado) y un experimento temporal del puente (ya retirado) que ejecutaba órdenes de un fichero sobre el ped: solo animaciones y audio, sin tareas del juego, sin tocar la posición, la vida ni el estado.

## La animación: la postura del jetpack existe y se ve exactamente como en GTA
- Es el grupo de animaciones **70 (`playerjetpack`), id 3 (`JETPACK_IDLE`)**: una postura casi estática en bucle (banderas 0x4003; el tiempo de la animación oscila en centésimas de segundo). El usuario confirmó que es «tal cual la animación que CJ lleva con el jetpack».
- **No hay una animación de empuje distinta:** en el juego la inclinación al moverse y el giro de las toberas (`jetball1`, `jetball2`) los hace el código de la tarea (`ApplyRollAndPitch`, `ProcessJetBall`), no una animación. El empuje se distinguirá en el renderer (inclinación del cuerpo y de las llamas), como preveía el diseño.

### Cómo ponerla y quitarla sin romper nada (lo que costó descubrir)
- **`BlendAnimation(clump, 70, 3, delta)` NO sirve.** Esa función desvanece y marca para borrar todas las demás animaciones del mismo tipo (parcial o no parcial) que la nueva (`CAnimManager::BlendAnimation`). La pose del jetpack no es parcial, así que borra las animaciones base (quieto, andar). Al quitarla, CJ queda **tumbado en horizontal en los dos juegos** hasta que el jugador camina; y borrar la asociación de esa forma tumbó el juego unos 7 segundos después (`page fault` en el código de actualización del esqueleto con extracción de velocidad: el ped se quedó sin animaciones). `CPlayerPed::SetRealMoveAnim` (0x60A9C0) no lo arregla.
- **Lo que funciona:** `CAnimManager::AddAnimation(clump, 70, 3)` (cdecl, 0x4D3AA0), que añade la asociación sin tocar las demás, y a mano: `flags |= 0x11` (**parcial** y reproduciendo), peso (+0x18) a 0 y ritmo (+0x1C) a +8 (llega a peso 1 en unos 0,13 s). Con la pose parcial las animaciones base siguen vivas debajo.
- **Quitarla:** ritmo −4 y bandera 0x4 (autoeliminar), como las demás: GTA la borra al desvanecerse y CJ vuelve a su postura normal. Verificado con el usuario: pose visible, vuelta a la normal sin bugs, el juego vivo más de 12 s, movimiento normal.
- **Cuidado con el puntero:** una asociación parcial se borra cuando su peso llega a 0 aunque no tenga la bandera de autoeliminar. No hay que escribir en ella después: hay que comprobar que sigue en la lista del clump antes (`HeldIsLive`, ya existente). En el experimento, una escritura en una asociación borrada corrompió memoria y tumbó el juego.

## El sonido: el audio del propio GTA funciona (plan A)
- La entidad de audio del ped está en **`ped + 0x138`** (`CPed::m_pedAudio`), comprobado en vivo: la entidad de voz en `ped + 0x294` tiene la tabla `0x85F310` (`CAEPedSpeechAudioEntity`) y el puntero al ped dentro de la entidad (`audio + 0x94`) es el propio ped.
- Llamadas (thiscall sobre esa entidad): `CAEPedAudioEntity::TurnOnJetPack` (0x4E28A0), `UpdateJetPack(thrustFwd, thrustAngle)` (0x4E0EE0; hay que llamarla cada fotograma: cada llamada mueve los volúmenes un paso), `TurnOffJetPack` (0x4E2A70) y `StopJetPackSound` (0x4E1120).
- El usuario oyó un **motor de jetpack grave**; con `thrustFwd` 1,0 y 0,0 (3 s cada uno) los sonidos eran muy parecidos (la diferencia entre reposo y empuje del juego es sutil), y al apagar con `TurnOffJetPack` + `StopJetPackSound` **se cortó limpiamente**. El juego siguió vivo en todo el ciclo y no se acumularon sonidos.
- Decisión: **plan A** (el audio de GTA). La Tarea 8 (sonido propio) no aplica.

## El modelo
- `jetpack.dff` (49 152 bytes) y `jetpack.txd` (10 240 bytes) están en `gta3.img` (modelo 370). El cuerpo tiene **832 vértices** con una sola textura (`Jetpac8bit128`; el `.txd` también trae `jetpackicon`, que no hace falta); con los marcos hijos (las toberas `jetball1`/`jetball2`) son **974 vértices**, así que se ensambla con los hijos (`ASSEMBLED`, como la minigun). Caja del cuerpo: x −0,43 a 0,43; y −0,38 a 0,52; z −0,37 a 0,41 (metros).
- **Dónde lo engancha el juego** (`RenderJetPack`): la matriz del índice 3 de la jerarquía del ped (`Spine1`, nodeID 3, el torso), con una traslación (0,1; 0,08; 0) y un giro de 90° sobre Y en el espacio de ese nodo. Son los valores iniciales de la Tarea 7; la colocación fina se ajusta en vivo.

## Seguridad
Tras todas las pruebas con el método final, CJ estaba normal en los dos juegos y el CJ de GTA seguía en su sitio y manejable. Solo se usaron asociaciones de animación (que se quitan solas) y el audio de su entidad de sonido.

## Constantes para las tareas siguientes
`kJetpackGroup` 70, `kJetpackIdleId` 3, banderas al crear 0x11, ritmo de subida +8 y de bajada −4 con autoeliminar; `kAddAnimation` 0x4D3AA0; entidad de audio en `ped + 0x138`; `kJetpackOn` 0x4E28A0, `kJetpackUpdate` 0x4E0EE0, `kJetpackOff` 0x4E2A70, `kJetpackStop` 0x4E1120; modelo `jetpack` con hijos, nodo 3, desplazamiento (0,1; 0,08; 0) y giro (0, 90, 0).
