# Coche: conducir el Greenwood con la física de GTA (diseño)

Fecha: 2026-10-09. Primera etapa de «coches y motos» (las motos y el resto vienen después). Parte de la investigación de viabilidad `docs/vehicles-spike.md`, que comprobó en GTA real que el puente puede crear un coche, sentar a CJ de golpe, conducirlo con el mando, acotarlo con una «pared» y bajarlo y borrarlo sin romper la partida. Continúa el diseño principal (`2026-10-05-cj-in-minecraft-design.md`) y reutiliza el patrón de las piezas anteriores (banderas y paquetes del protocolo, reglas puras con pruebas, `BodyReactions`, el mando del puente).

## 1. Objetivo y criterio de éxito

Que el jugador pueda **subirse a un coche de GTA y conducirlo por el mundo de Minecraft**, con la **física del propio GTA** (motor, suspensión, derrape, frenos) y con los bloques de Minecraft haciendo de paredes y de suelo.

**Decisiones del usuario (2026-10-09):**
- **El coche de GTA es el que se mueve** (no una física propia de Minecraft).
- **Un coche primero.** Las motos vienen después.
- **Subir con una tecla, sin animación de abrir la puerta**: pulsas y CJ aparece dentro, listo para manejar.
- **El coche aparece al subir y desaparece al bajar** (no queda aparcado en el mundo).

**El hito está cumplido cuando**, con GTA corriendo, un mundo de un jugador y CJ en una zona abierta del mapa de GTA (el aeropuerto de la partida del usuario):
1. Al pulsar la tecla de subir (`G`, configurable) donde cabe un coche, aparece un **Greenwood** mirando hacia donde mira el jugador, con CJ de conductor sentado con su pose de GTA, y el jugador dentro. Si no cabe (cueva, pared), no pasa nada y el jugador recibe un aviso.
2. **W** acelera, **S** frena y da marcha atrás, **A** y **D** giran con un giro suave, **Espacio** es el freno de mano. El coche responde con la física de GTA (inercia, derrape, frenado).
3. El coche se dibuja con el **modelo de GTA** (carrocería, parachoques, cristales, ruedas), con las ruedas girando según lo que avanza y las delanteras siguiendo el volante.
4. **Los bloques lo frenan:** si el coche choca contra un bloque no lo atraviesa; se queda parado contra él sin rebotes ni descontrol.
5. **El relieve se sigue:** el coche se apoya en los bloques, sube escalones de hasta 0,6 bloques y cuestas suaves, y cae si se queda sin suelo; se inclina según el terreno.
6. Al pulsar la tecla otra vez, CJ **baja** a un lado del coche, el coche **se borra** y el jugador vuelve a ser CJ a pie, con los controles de siempre.
7. Si GTA deja de responder, el jugador muere, o se desconecta o cambia de mundo, se **sale del modo vehículo** sin dejar el coche ni a CJ en un estado raro.
8. Seguridad de la partida de GTA: el coche es indestructible y CJ inmune mientras conduce; al bajar, la vida y el estado de CJ en GTA son los de antes; no queda ningún coche creado.
9. Sin GTA, o si algo falla, el juego no se cae y no se puede subir a ningún coche (la tecla no hace nada).

**Fuera de este hito:** motos y otros modelos (el código quedará preparado para elegir otro), coches aparcados que persisten en el mundo, puertas, luces, daños, pasajeros, armas al conducir, cámara automática detrás del coche (la cámara es libre con el ratón), copiar la suspensión de GTA a las ruedas, el sonido del motor (suena el de GTA como suene) y conducir en un mapa de GTA con edificios (CJ debe estar en una zona abierta).

## 2. Arquitectura

Reparto nuevo, que es la clave de este hito: **GTA calcula la física del coche; Minecraft decide qué espacio hay y dibuja.**

### 2.1 Lo que pasa en GTA (el puente, `VehicleDriver`)
- **Máquina de estados:** libre → subiendo → conduciendo → bajando → libre.
- **Subiendo:** crea el coche en la posición de CJ en GTA con el modelo pedido (`RequestModel` + `LoadAllRequestedModels`, `CCarCtrl::CreateCarForScript`), mirando hacia el rumbo que manda Minecraft; lo deja listo para conducir (estado de coche del jugador, motor encendido, sin cerrojo) e indestructible, protege a CJ y lo sienta de conductor (`CTaskSimpleCarSetPedInAsDriver` con la bandera de entrar de golpe).
- **Conduciendo:** cada fotograma escribe en el mando lo que manda Minecraft (acelerador, freno, volante, freno de mano), lee la posición, el giro, la velocidad y el volante del coche y la posición de CJ respecto al coche, y se lo manda a Minecraft; sigue mandando la **pose de CJ** (hoy el puente suelta la marioneta al subir: en modo vehículo no la suelta). Aplica la **corrección** que mande Minecraft: fija la posición del coche en el punto válido y anula la velocidad que atraviesa el obstáculo.
- **Bajando:** saca a CJ con `FlushImmediately(true)` y `Teleport` a un lado del coche (como el comando de script que lo hace), borra el coche con `CWorld::Remove` y su destructor, y devuelve la marioneta a su control normal. Usa siempre los desplazamientos verificados del ped (p. ej. `m_pIntelligence` en 0x47C con la comprobación del puntero a atrás).
- **Vigilante:** sin estado de Minecraft durante un segundo, o con CJ fuera del coche sin que se haya pedido, se baja y se borra el coche.

### 2.2 Lo que pasa en Minecraft (el cliente, `VehicleClient` y reglas puras `dev.cjmc.vehicle`)
- **Subir y bajar:** la tecla manda la orden; antes de subir, una regla pura comprueba que la caja del coche cabe en el sitio (sin bloques dentro, con suelo debajo) y que el jugador puede (no montado, no en agua, no volando con el jetpack, no trepando, no derribado ni muerto).
- **Traslado de coordenadas:** al subir, el cliente fija la correspondencia entre el punto del jugador y su rumbo y el punto y el rumbo del coche en GTA. Cada fotograma, el desplazamiento del coche en GTA respecto a ese punto, girado al rumbo de Minecraft, da la posición deseada del coche en el mundo; el rumbo de GTA menos el inicial, más el de Minecraft, da su orientación.
- **Mover al jugador:** el jugador (dentro del coche) va a la posición del coche cada tick, como en el trepado, con sus controles normales inmovilizados.
- **Altura e inclinación:** del terreno de Minecraft, no de GTA (GTA ve un suelo plano): la altura sale del suelo bajo la caja del coche y la inclinación (cabeceo y alabeo) de la altura del suelo bajo las cuatro ruedas, suavizada; el coche cae con la gravedad de Minecraft si no hay suelo.
- **Colisiones (`VehicleCollision`, pura):** una caja con forma de coche (≈ 4,9 de largo × 1,9 de ancho × 1,5 de alto, girada al rumbo) se mueve contra los bloques por ejes; sube escalones de hasta 0,6 bloques; si el movimiento no cabe, el coche se queda en el último punto válido y el cliente manda la **corrección** al puente.
- **Entradas de conducción:** W, S, A, D y Espacio, con el volante suavizado (sube y baja hacia −1 y 1, no salta); se mandan al puente cada fotograma.
- **Dibujo:** un `VehicleRig` dibuja el coche convertido con la orientación calculada, las ruedas girando (por la distancia avanzada y el radio de la rueda) y las delanteras orientadas por el volante; `CjRenderer` dibuja a CJ sentado con la pose de GTA, en el sitio que GTA dice respecto al coche.
- **Cámara:** la tercera persona normal de Minecraft sigue al jugador (libre con el ratón).

### 2.3 El protocolo (v2 sin cambiar de versión; los paquetes antiguos no cambian)
Paquetes nuevos con ficheros dorados en Python, C++ y Java: `VehicleCommand` (cliente → puente: subir con el modelo y el rumbo, bajar), `VehicleInput` (cliente → puente, cada fotograma: acelerador, freno, volante, freno de mano, y la corrección con la posición válida y el recorte de velocidad cuando hay colisión) y `VehicleState` (puente → cliente, cada fotograma: si está dentro, posición, rumbo, cabeceo y alabeo, velocidad, volante, y la posición de CJ respecto al coche). Los tamaños y campos concretos los fija el plan.

### 2.4 El modelo (conversor de vehículos)
`tools/cjconvert/vehicles.py` lee el `.dff` (nombres de 8 letras: `greenwoo.dff`) con los nombres de sus marcos (extensión de marco `0x253F2FE`) y compone las matrices de los marcos anidados; elige las piezas normales (`_ok`, nunca `_dam` ni `ug_*`), separa las ruedas (una sola malla, que se repite en las cuatro posiciones, con sus marcos `wheel_*_dummy`) y las pone en un `.glb` junto a los de las armas. Las **texturas** salen de la `.txd` del coche y de `models/generic/vehicle.txd`; los **colores** de pintura de GTA (materiales sin textura con colores «mágicos» que el juego sustituye por el color del coche) se aplican con un color base elegido, y el conversor los cuece en una textura de un píxel por material.

## 3. Investigación ya hecha y lo que se mide en el plan
Todo lo que sostiene este diseño se comprobó en la investigación de viabilidad (crear, sentar, conducir, acotar, bajar y borrar). Lo que queda por medir en el plan, antes de fijar números: que la **pose de CJ sentado se lee bien** (el esqueleto del ped dentro del coche, en qué espacio queda), la posición y orientación de CJ respecto al coche, el **radio de la rueda** y el **sentido de giro del volante** del Greenwood, y qué **color** de GTA es el del Greenwood.

## 4. Números iniciales
Caja del coche ≈ 4,9 × 1,9 × 1,5 bloques; escalón máximo 0,6; volante: sube a ±1 en ≈ 0,25 s y vuelve al centro en ≈ 0,15 s; modelo `greenwood` (id 492); la tecla `G`. Todo en constantes con nombre, ajustable jugando y anotado en `docs/vehicle-results.md`.

## 5. Casos límite
- **Sin sitio para el coche:** no se sube y el jugador recibe un aviso; no se crea nada en GTA.
- **Chocar de frente a mucha velocidad:** el coche se queda parado contra el bloque (sin daño ni rebote); un choque lateral a lo largo de un muro lo desliza por el eje libre.
- **Escalón alto, cuesta empinada, borde o agua:** si el escalón supera 0,6 bloques se choca contra él; sin suelo, el coche cae; el agua no se atraviesa con el coche (lo tratará una etapa posterior).
- **Morir o salir del mundo en el coche:** se sale del modo vehículo y se borra el coche.
- **GTA se cierra o deja de responder:** el cliente baja al jugador del coche y vuelve a su estado normal; el puente, al volver a oírse, no deja un coche huérfano (se borra al detectar que no hay modo vehículo).
- **Subir cerca de objetos del mapa de GTA** (farolas, rampas, edificios): GTA los ve y Minecraft no; es el límite de la zona abierta, documentado.
- **Teclado:** `G` no se usa mientras haya una pantalla abierta; los controles de conducción tienen prioridad sobre los de a pie mientras se conduce.

## 6. Pruebas
- **Automáticas** (con mutaciones): el traslado de coordenadas (ida y vuelta, rumbos), `VehicleCollision` (la caja contra bloques, escalones, deslizar, caer), la inclinación por las cuatro ruedas, el volante suavizado, el giro de las ruedas, las condiciones de subir y bajar, la máquina de estados del puente (con sus tiempos y el vigilante), los paquetes en Python, C++ y Java con ficheros dorados, y el conversor (marcos con nombre, piezas `_ok`, ruedas repetidas, colores).
- **En vivo con GTA real** (con el usuario): subir donde cabe y donde no, conducir y frenar, girar con el volante suave, el freno de mano, chocar contra paredes y a distintas velocidades, subir un escalón y una cuesta, caer de un borde, bajar, que CJ vuelve a verse como CJ, que el coche se borra, que la partida de GTA queda intacta, y que cerrar GTA o morir en el coche no deja nada raro.
- **Revisión final independiente** y una pasada de arreglos.

## 7. Riesgos
- **Acople con el relieve:** GTA ve un suelo plano y Minecraft uno con relieve y bloques; elegir bien qué escalones se suben y cómo se espeja la altura es lo más delicado.
- **Latencia del bucle de corrección:** el puente manda, Minecraft decide, el puente corrige; si son varios fotogramas, el coche puede entrar un poco en un bloque antes de frenarse.
- **La pose de CJ sentado:** si el esqueleto del ped en el coche no queda en un espacio utilizable, hará falta compensarlo (como el avance de las animaciones de nado y de trepar).
- **Objetos del mapa de GTA** junto al coche que lo desvíen sin que Minecraft los vea.
- **Mucho código nuevo de golpe:** un conversor, tres paquetes, un modo del puente, una entidad de dibujo y las reglas de colisión; el plan lo divide para poder probar cada pieza.
