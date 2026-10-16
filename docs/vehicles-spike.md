# Coches — Investigación de viabilidad (el coche de GTA es el que se mueve)

Fecha: 2026-10-09. Mediciones en GTA SA 1.0 US real con un experimento temporal del puente (ya retirado; el código está fuera del repositorio) que ejecutaba órdenes de un fichero. Era una investigación, no un hito: nada de lo que se probó se queda en el código.

## Veredicto: es viable
La física del coche la puede calcular GTA, con el coche real del juego, y Minecraft puede acotarla con los bloques. Lo que se comprobó en vivo:

| Pregunta | Resultado |
|---|---|
| ¿Se puede crear un coche de GTA desde el puente? | **Sí.** Se pide el modelo (`RequestModel` + `LoadAllRequestedModels`, como las armas) y `CCarCtrl::CreateCarForScript(modelo, x, y, z, false)` (0x431F80, cdecl; con `z` muy baja usa el suelo del mapa) lo crea y lo añade al mundo; el Greenwood (492) apareció a 12 m de CJ, apoyado en el suelo. |
| ¿Se puede meter a CJ de conductor sin la animación de abrir la puerta? | **Sí.** Se construye `CTaskSimpleCarSetPedInAsDriver` (`CTask::operator new(0x1C)`, constructor en 0x6470E0 con el coche y sin utilidad, la bandera de «entrar de golpe» en el byte 0x18) y se llama a su `ProcessPed` (0x64B950): es lo que hace el comando de script `WARP_CHAR_INTO_CAR`. CJ queda de conductor (`m_pVehicle` del ped = el coche, `m_pDriver` del coche = CJ, desplazamiento 0x460). |
| ¿Se puede conducir con el mando? | **Sí.** Con el pad (`LeftStickX` gira, `ButtonCross` acelera, `ButtonSquare` frena y marcha atrás, `RightShoulder1` freno de mano), el coche aceleró hasta ~0,4 unidades por fotograma, giró, frenó y dio marcha atrás con la física propia del juego (suspensión, derrape, rampas del mapa). En la ventana de GTA CJ conducía con las animaciones de GTA. |
| ¿Se puede acotar el coche con un «bloque»? | **Sí.** Una pared invisible (si la posición `y` pasaba de un valor, se fijaba la posición y se anulaba la velocidad que la atravesaba) dejó el coche parado en la pared, con la velocidad a cero y sin rebotes ni descontrol; al frenar, dio marcha atrás con normalidad y siguió girando. |
| ¿Se puede sacar a CJ y borrar el coche sin romper nada? | **Sí.** Se hace como el comando de script `WARP_CHAR_FROM_CAR_TO_COORD`: `CPedIntelligence::FlushImmediately(true)` (0x601640, sobre el `m_pIntelligence` del ped en 0x47C, comprobando que su puntero a atrás apunta al ped) y `CPed::Teleport` (0x5E4110): el coche se queda sin conductor, CJ aparece a 3 m y **el puente lo vuelve a controlar solo**. Borrar el coche: `CWorld::Remove` (0x563280) y el destructor virtual con la bandera 1: el juego siguió estable más de 12 s. |
| ¿Se convierte el modelo? | **Con un conversor nuevo.** El `greenwoo.dff` (nombre de 8 letras) tiene **54 marcos con nombre** (`chassis`, `door_*_dummy`, `bump_*`, `wheel_lf_dummy`…`wheel_rb_dummy`, `ped_frontseat`, `headlights`, `taillights`…) y 24 atomics sin ninguna geometría colgada de la raíz (el conversor de armas lo rechaza). Cada pieza tiene una variante `_ok` y otra `_dam` (dañada), la rueda es una sola (`wheel`, bajo el marco de una rueda) para las cuatro posiciones, y hay piezas de mejora (`ug_*`). Texturas: las propias (`greenwoo92interior128`, `greenwoo92wheel64`) y las compartidas de `models/generic/vehicle.txd` (19). Hace falta leer los nombres de los marcos (extensión de marco `0x253F2FE`) y componer las matrices de los marcos anidados. |

## Lo que se aprendió por el camino
- **CJ está en el aeropuerto de Los Santos** (`z = 13,6`): una explanada plana, ideal. Una partida de GTA con CJ en otro sitio (edificios, calles) no sirve igual: el coche se crearía junto a él y chocaría con el mapa de GTA. Mientras CJ esté en una zona abierta, el diseño funciona.
- **El puente suelta a CJ solo al subir al coche** («puppet released: the game moved CJ») y deja de mandar su pose: por eso en Minecraft aparecía el Steve de Minecraft. En el diseño hará falta un modo vehículo que siga mandando la pose de CJ sentado.
- **Un fallo mío** de la investigación: usé un desplazamiento adivinado de `m_pIntelligence` (0x440) y tumbó el juego; el verificado es el 0x47C que ya usaba el puente para las armas (con comprobación del puntero a atrás). En el código definitivo se usa siempre el verificado.
- El suelo del coche es el del mapa de GTA, con sus objetos (una rampa pequeña a ~50 m del coche lo hizo saltar): hay que elegir la zona.

## Arquitectura que sale de esto (a diseñar en la especificación)
1. **GTA hace la física**; Minecraft manda las teclas (acelerar, frenar o marcha atrás, girar, freno de mano) y recibe la posición y el giro del coche, **respecto al punto donde se creó**.
2. **Minecraft traslada ese movimiento a su mundo:** el jugador va dentro, y su posición sigue el desplazamiento del coche; la altura y la inclinación las toma del terreno de Minecraft, y las colisiones se resuelven con una caja con forma de coche contra los bloques. Si el movimiento no cabe, Minecraft le dice al puente la posición válida y el puente la fija y anula la velocidad que atraviesa (lo probado con la pared invisible).
3. **Dibujo:** el coche de GTA convertido, con las ruedas girando y orientadas a mano según la velocidad y la dirección (la suspensión de GTA se puede espejar después); CJ sentado con la pose de GTA.
4. **Subir y bajar** con una tecla, sin animación de puertas: lo probado.
5. **Protocolo:** paquetes nuevos (el estado del vehículo hacia Minecraft, y la corrección hacia el puente) y un modo vehículo en el puente.

## Riesgos que quedan
- Que el acople por «fijar posición» se note en pendientes y escalones: GTA ve un suelo plano y Minecraft uno con relieve; hay que espejar bien la altura y decidir qué escalones se suben.
- La latencia del bucle (el puente manda, Minecraft decide, el puente corrige): si es de varios fotogramas, el coche puede entrar un poco en un bloque antes de ser frenado.
- El sonido: el motor suena en el proceso de GTA con su propio audio; no se midió cómo se oye junto a Minecraft.
- Que haya otros objetos del mapa de GTA cerca del coche (farolas, rampas) que lo desvíen sin que Minecraft los vea.
