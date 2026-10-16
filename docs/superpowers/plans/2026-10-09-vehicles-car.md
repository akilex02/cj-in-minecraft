# Coche: conducir el Greenwood con la física de GTA: plan de implementación

> **Para agentes:** SUB-HABILIDAD OBLIGATORIA: usar superpowers:executing-plans (el usuario elige siempre la ejecución nativa) o superpowers:subagent-driven-development para ejecutar este plan tarea a tarea. Los pasos usan casillas (`- [ ]`).

**Objetivo:** que el jugador suba con una tecla a un Greenwood de GTA, lo conduzca con la física de GTA sobre el mundo de Minecraft (los bloques lo frenan y el terreno lo sostiene), con el modelo de GTA dibujado, CJ sentado con su pose de GTA, y que al bajar el coche se borre. Sin dejar nunca la partida de GTA en un estado raro.

**Arquitectura:** el puente tiene un modo vehículo (máquina de estados `VehicleDriver`) que crea el coche, sienta a CJ, aplica al mando las entradas que manda Minecraft y devuelve cada fotograma el estado del coche; Minecraft traslada el movimiento del coche de GTA a su mundo (reglas puras: traslado de coordenadas, colisión de una caja con forma de coche contra los bloques, altura e inclinación del terreno), mueve al jugador con el coche y devuelve la corrección cuando choca; un conversor de vehículos y un `VehicleRig` dibujan el coche. Tres paquetes nuevos del protocolo v2 (`VehicleCommand`, `VehicleInput`, `VehicleState`) con ficheros dorados en los tres lenguajes.

**Tecnologías:** Java 21 / Fabric 1.21.1 con Yarn (JUnit 5), C++ del puente (pruebas nativas), Python 3 (protocolo y conversor), GTA SA 1.0 US bajo Wine.

**Spec:** `docs/superpowers/specs/2026-10-09-vehicles-car-design.md`. Contexto: `docs/vehicles-spike.md` (todo lo que se comprobó en GTA real: crear, sentar, conducir, acotar, bajar y borrar), `docs/jetpack-results.md` y `docs/swim-results.md` (el patrón de reglas puras + banderas + `BodyReactions`).

## Global Constraints

- **Protocolo v2 sin cambiar de versión; los paquetes antiguos no cambian.** Tipos nuevos (`TYPE_VEHICLE_COMMAND` = 5, `TYPE_VEHICLE_INPUT` = 6, `TYPE_VEHICLE_STATE` = 7), todo little-endian con la cabecera de siempre (`magic`, `version`, `type`, 8 bytes). Campos (tamaños con la cabecera):
  - `VehicleCommand` (cliente → puente, 24 bytes): `seq` u32, `ts_ms` u32, `command` u16 (0 nada, 1 subir, 2 bajar), `model` u16 (id de modelo de GTA; 492 = Greenwood), `yaw` f32 (guiñada del jugador en grados de Minecraft: el coche mira hacia allí).
  - `VehicleInput` (cliente → puente, 36 bytes, cada fotograma en modo vehículo): `seq` u32, `ts_ms` u32, `throttle` u16, `brake` u16 (0 a 255), `steer` i16 (−127 a 127, positivo a la derecha), `handbrake` u16 (0 o 255), `corr_flags` u16 (bit 0: hay corrección), `pad` u16, `corr_x` f32, `corr_y` f32 (desplazamiento a sumar a la posición del coche en el eje de GTA, en metros).
  - `VehicleState` (puente → cliente, 68 bytes, cada fotograma en modo vehículo): `ack_seq` u32, `gta_time_ms` u32, `flags` u16 (bit 0: CJ está dentro del coche), `model` u16, `pos` 3×f32 (posición del coche en el mundo de GTA), `heading` f32 (radianes, antihorario desde el norte, rumbo del coche), `pitch` f32, `roll` f32 (radianes), `speed` f32 (metros por segundo hacia delante), `steer` f32 (ángulo del volante en radianes, positivo a la derecha), `seat` 3×f32 (posición de CJ en los ejes del coche: derecha, delante, arriba) y `seat_heading` f32 (el rumbo de CJ respecto al del coche, radianes). Los números definitivos de los campos de CJ los fija la Tarea 1.
- **El jugador real de GTA no puede sufrir nada.** El coche se crea indestructible y CJ inmune mientras conduce; al bajar, la vida y las banderas de CJ son las de antes, se borra el coche (`CWorld::Remove` + destructor con bandera 1) y no queda ningún coche creado; si el puente se suelta o GTA no responde, se baja y se borra. Se usan siempre los desplazamientos verificados del ped (`m_pIntelligence` 0x47C con la comprobación del puntero a atrás; `m_pVehicle` 0x58C; `m_pDriver` del coche 0x460); nunca desplazamientos adivinados (un desplazamiento adivinado tumbó el juego en la investigación).
- **Hechos de GTA verificados** (`docs/vehicles-spike.md`): `CCarCtrl::CreateCarForScript(modelo, x, y, z, false)` (0x431F80, cdecl; con `z` muy baja usa el suelo del mapa); `CTaskSimpleCarSetPedInAsDriver` (`CTask::operator new(0x1C)` 0x61A5A0, constructor 0x6470E0 con el coche y sin utilidad, byte 0x18 = entrar de golpe, `ProcessPed` 0x64B950); salir con `CPedIntelligence::FlushImmediately(true)` (0x601640) y `CPed::Teleport` (0x5E4110, virtual, por valor: x, y, z, bool); borrar con `CWorld::Remove` (0x563280) y el destructor virtual `[0](coche, 1)`; mando: `LeftStickX` gira, `ButtonCross` acelera, `ButtonSquare` frena y marcha atrás, `RightShoulder1` freno de mano; el coche en GTA se mueve sobre el suelo plano del mapa (el aeropuerto de Los Santos, `z = 13,6`, donde está CJ). El puente suelta a CJ al subir al coche si no se le dice lo contrario; el modo vehículo lo impide.
- **Zona abierta:** el modo vehículo solo funciona con CJ en una zona abierta del mapa de GTA; se documenta como límite.
- **Constantes iniciales** (spec §4; en constantes con nombre, ajustables jugando y anotadas en `docs/vehicle-results.md`; la Tarea 1 sustituye lo que mida): caja del coche 4,9 × 1,9 × 1,5 bloques; escalón máximo `STEP_HEIGHT` 0,6; volante: sube a ±1 en 0,25 s (`STEER_RISE_PER_S` 4,0) y vuelve a 0 en 0,15 s (`STEER_RETURN_PER_S` 6,67); modelo `greenwood` (id 492); tecla `G`; tiempo máximo sin estado de Minecraft antes de bajar, 1000 ms.
- **Cuándo se puede subir:** GTA responde (`CjClient.bridgeLive()`), el jugador no está montado, ni en agua, ni volando con el jetpack, ni trepando, ni derribado ni muerto, no hay pantalla abierta, y la caja del coche cabe en el sitio (sin bloques dentro, con suelo debajo).
- Los paquetes `dev.cjmc.vehicle` (reglas puras) no usan clases de Minecraft; el movimiento, la lectura de bloques, el dibujo y la tecla están en `dev.cjmc` (cliente).
- Convenciones del repo: TDD con el rojo observado, mutaciones comprobadas por el arnés (el fallo debe ser de la prueba, no de compilación; Java con `python3 tools/mutj.py <json>`, nativas escribiendo el mutante y ejecutando `bash tools/run_native_tests.sh`), commits con `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` y **con rutas explícitas (nunca `git add -A`: la carpeta `weapon-icons/` del usuario está sin versionar e ignorada)**, comentarios en inglés en el código, documentos en español.
- Comandos: Python `cd tools && python3 -B -m unittest discover -s tests` y `cd protocol && python3 -B -m unittest test_cjproto`; nativas `bash tools/run_native_tests.sh`; Java `cd cj-fabric && ./gradlew test --console=plain`; regenerar los ficheros dorados con `cd protocol && python3 make_golden.py`.
- Abrir y cerrar los juegos con `tools/start_games.sh` (`--restart` cierra y abre ambos; GTA primero). Antes de reiniciar GTA con código nuevo del puente: `bash tools/install_bridge.sh`. La herramienta de comandos se queda esperando si se encadena el script con una tubería: redirigir su salida (`tools/start_games.sh --restart > /tmp/cj-start.log 2>&1 < /dev/null`). Cerrar procesos por PID leyendo `ps -eo pid,args` o `/proc/<pid>/cmdline`; nunca `pkill -f` ni `pgrep -f` con una cadena que contenga el propio comando. El registro del puente está en `GTA San Andreas/cj_bridge.log`.
- El experimento temporal de la investigación (crear, sentar, conducir, pared, bajar, borrar) está guardado fuera del repositorio en el directorio de scratchpad de la sesión anterior (`bridge_with_car_experiment.cpp`); sirve de modelo para el código de `WinIo`, pero el código definitivo se escribe con pruebas.

## Review Focus

1. **La partida de GTA tras salir del modo vehículo:** ningún coche huérfano, CJ con su vida y su posición, el puente que vuelve a controlar a CJ, y qué pasa si Minecraft se cierra, GTA se queda sin respuesta, el jugador muere o se carga otra partida con el coche creado. → Tareas 1, 6, 7 y 11.
2. **Acople con los bloques:** el coche no atraviesa bloques, no queda atrapado dentro de uno, no «vibra» contra una pared, sube escalones de 0,6 y no de más, cae si falta suelo, y la corrección al puente no desestabiliza la física (como en la pared invisible de la investigación). → Tareas 3, 4, 9 y 11.
3. **El estado compartido entre dos procesos:** la correspondencia GTA↔Minecraft que se fija al subir no se pierde ni se desfasa (rumbos, ida y vuelta de las correcciones, reloj hacia atrás, paquetes perdidos o repetidos), y los paquetes viejos de otro modo no se confunden con los nuevos. → Tareas 2, 3 y 6.
4. **Falsos disparadores:** subir en agua, montado, volando con el jetpack, trepando, derribado, con una pantalla abierta o sin GTA no debe hacer nada; la tecla `G` no se usa por otra cosa; los controles a pie no se mueven mientras se conduce. → Tareas 5 y 9.
5. **CJ sentado y el coche dibujados bien:** el sitio de CJ respecto al coche, su pose, las ruedas (giro y volante), la orientación y la inclinación, y que el jugador de Minecraft no se vea como Steve ni dos veces. → Tareas 1, 8 y 10.

---

### Tarea 1: Medidas en GTA y en los ficheros del juego

**Archivos:**
- Modificar (temporal, se quita al terminar): `gta-bridge/src/bridge.cpp`
- Crear: `docs/vehicle-measurements.md`

**Interfaces:**
- Consume: la investigación (`docs/vehicles-spike.md`), el experimento guardado fuera del repositorio, `tools/cjconvert/dff.py` y `rwchunk.py` para leer los modelos.
- Produce: en `docs/vehicle-measurements.md` (en español) las constantes que usarán las Tareas 2, 8 y 10: la posición y el rumbo de CJ sentado respecto al coche (en los ejes del coche: derecha, delante, arriba; y el rumbo relativo), el **radio de la rueda** del Greenwood, el **sentido del giro del volante** (qué signo tiene el ángulo del volante cuando se gira a la derecha) y el campo o la forma de leerlo, **el color de pintura** del Greenwood según `data/carcols.dat` (los colores por defecto), los **colores «mágicos»** de pintura que el modelo usa en sus materiales, y si la pose del esqueleto de CJ sentado se lee con el lector actual (qué espacio queda y qué compensación hace falta).

- [ ] **Paso 1: Medidas fuera de GTA** — con la instalación real (`GTA San Andreas/models/gta3.img` y `models/generic/vehicle.txd`, `data/carcols.dat`, `data/vehicles.ide`): un script de una línea que lea `greenwoo.dff` y calcule el radio de la malla de la rueda (el mayor radio de su caja en el plano del eje) y los colores de los materiales de cada geometría (RGBA), anotando cuáles son los colores «mágicos» de pintura de GTA (60, 255, 0 / 255, 0, 175 / 0, 255, 255 / 255, 255, 0 sustituibles por el color del coche); y que lea de `carcols.dat` los pares de colores por defecto del `greenwood` en `data/vehicles.ide` y los traduzca a RGB con la tabla de `carcols.dat`.
- [ ] **Paso 2: Medidas en GTA** — con el experimento del scratchpad como base, ampliarlo para que tras sentar a CJ registre cada 200 ms: la posición y el rumbo del coche, los de CJ (`PedPosition`, matriz), la posición de CJ en los ejes del coche (producto escalar con los ejes de la matriz del coche) y el rumbo relativo; y el ángulo del volante: leyendo el campo de dirección del coche (buscarlo en `gta-reversed-master/source/game_sa/Entity/Vehicle/Vehicle.h`, `m_fSteerAngle`; **verificar el desplazamiento escribiendo el valor mientras se gira con el mando a la derecha y comprobando que cambia de signo coherente**, nunca fiarse de un número sin comprobar) o, si no se encuentra, derivándolo de la velocidad de giro.
- [ ] **Paso 3: La pose de CJ sentado** — con el lector de esqueleto actual (`gta-bridge/src/pose.h`), comprobar en vivo, con el usuario mirando en F5, que la pose de CJ en el coche (sin soltar la marioneta: parche temporal para que el puente no suelte a CJ en vehículo y siga mandando pose) llega a Minecraft y se ve bien sentado, o qué espacio y desplazamiento muestra; anotar cuánto hay que compensar.
- [ ] **Paso 4: Seguridad** — tras bajar y borrar el coche con el experimento, comprobar con el usuario que CJ está normal en los dos juegos y el juego sigue vivo más de 12 s.
- [ ] **Paso 5: Documentar y commit** — escribir `docs/vehicle-measurements.md` con las medidas y las constantes elegidas, quitar el experimento (`git checkout gta-bridge/src/bridge.cpp`) y comprometer: `git add docs/vehicle-measurements.md && git commit -m "Vehicles: measurements (seat, wheel radius, steering, paint colours)"` con la coautoría.

---

### Tarea 2: Los paquetes nuevos en el protocolo (Python, C++ y Java)

**Archivos:**
- Modificar: `protocol/cjproto.py`, `protocol/make_golden.py`, `protocol/test_cjproto.py`, `protocol/PROTOCOL.md`
- Crear: `protocol/golden/vehicle_command_v2.bin`, `vehicle_input_v2.bin`, `vehicle_state_v2.bin` (los genera `make_golden.py`)
- Modificar: `gta-bridge/src/protocol.h`, `gta-bridge/tests/test_protocol.cpp`
- Modificar: `cj-fabric/src/main/java/dev/cjmc/Protocol.java`, `cj-fabric/src/test/java/dev/cjmc/ProtocolTest.java`

**Interfaces:**
- Consume: los tamaños y campos de las Global Constraints; el patrón de `State`/`Echo`/`Shot`/`Pose` (cabecera, `pack_*`/`unpack_*`, `Parse*`, validación de magia, versión, tipo y tamaño exacto).
- Produce: en los tres lados `TYPE_VEHICLE_COMMAND` = 5, `TYPE_VEHICLE_INPUT` = 6, `TYPE_VEHICLE_STATE` = 7; Python `VehicleCommand`, `VehicleInput`, `VehicleState` con `pack_*`/`unpack_*` (`ValueError` si el tamaño, la magia, la versión o el tipo no cuadran); C++ `VehicleCommandPacket`, `VehicleInputPacket`, `VehicleStatePacket` con `ParseVehicleCommand`, `ParseVehicleInput` y `ParseVehicleState` (devuelven false si no cuadran) y `EncodeVehicleState`; Java `Protocol.VehicleCommand`/`VehicleInput`/`VehicleState` con `encodeVehicleCommand`, `encodeVehicleInput` y `decodeVehicleState`; y una función común de **tipo de paquete** (`packet_type(data)` / `PacketTypeOf(buffer, size)` / `Protocol.typeOf(bytes)`) que lee solo la cabecera, para que el receptor despache por tipo sin que los paquetes de otro tipo cuenten como mal formados.

- [ ] **Paso 1: Pruebas en rojo** — en cada lado, con paquetes fijos (los mismos en los tres: `fixed_vehicle_command()`, `fixed_vehicle_input()`, `fixed_vehicle_state()` con valores distintos en cada campo para que un campo cambiado de sitio falle): el tamaño exacto (24, 36 y 68), el viaje de ida y vuelta, que un tamaño distinto, una magia, una versión o un tipo equivocados se rechazan, que el tipo se lee de la cabecera, el fichero dorado byte a byte, y un paquete de estado de vehículo no se acepta como `State` ni al revés. En C++, `ParseState` sigue rechazando los paquetes nuevos y el despachador por tipo los distingue.
- [ ] **Paso 2: Verlas fallar** — `cd protocol && python3 -B -m unittest test_cjproto 2>&1 | tail -4`; `bash tools/run_native_tests.sh 2>&1 | tail -3`; `cd cj-fabric && ./gradlew test --console=plain 2>&1 | grep -E "error:|FAILED|BUILD" | head -3`. Expected: errores por símbolos que no existen.
- [ ] **Paso 3: Implementar** — `cjproto.py` (`struct` de cada paquete, `*_SIZE`, `pack_*`, `unpack_*`, `packet_type`), `make_golden.py` (los tres ficheros nuevos), `protocol.h` y `Protocol.java` en el mismo estilo, y la sección nueva de `PROTOCOL.md` con las tablas de campos.
- [ ] **Paso 4: Verlas pasar** — `cd protocol && python3 make_golden.py` y los tres comandos del paso 2. Expected: todo `OK` / `BUILD SUCCESSFUL`; los ficheros dorados antiguos siguen igual (`git status` solo muestra los tres nuevos).
- [ ] **Paso 5: Mutaciones** — mutantes de cada campo y su orden, de los tamaños y del despachador por tipo (Java con `mutj.py`; nativos escribiendo el mutante); todos matados por fallo de prueba.
- [ ] **Paso 6: Commit** — con rutas explícitas (los seis ficheros fuente y de prueba, `PROTOCOL.md` y los tres `.bin`) y la coautoría: `Vehicles: the three vehicle packets in the three protocol sides`.

---

### Tarea 3: Traslado de coordenadas GTA ↔ Minecraft (`VehicleFrame`)

**Archivos:**
- Crear: `cj-fabric/src/main/java/dev/cjmc/vehicle/VehicleFrame.java`
- Test: `cj-fabric/src/test/java/dev/cjmc/vehicle/VehicleFrameTest.java`

**Interfaces:**
- Consume: nada (puro); el rumbo de GTA es antihorario desde el norte (adelante = (−sen H, cos H) con x al este e y al norte) y la guiñada de Minecraft es 0° hacia el sur (+z), 90° hacia el oeste (−x), con adelante = (−sen yaw, cos yaw) con x al este y z al sur.
- Produce: `VehicleFrame.anchor(double mcX, double mcZ, double mcYawDegrees, double gtaX, double gtaY, double gtaHeading)` (fija la correspondencia al subir) y, sobre ella: `mcPosition(double gtaX, double gtaY)` → `{x, z}`; `mcYawDegrees(double gtaHeading)`; `gtaCorrection(double mcDeltaX, double mcDeltaZ)` → `{dx, dy}` (un desplazamiento del mundo de Minecraft expresado en los ejes de GTA); `seatToMinecraft(...)` si hace falta (la colocación de CJ respecto al coche se hace en el cliente con el rumbo del coche).

- [ ] **Paso 1: Pruebas en rojo** — `VehicleFrameTest` con casos que se puedan comprobar a mano:
  1. **Sin movimiento:** `mcPosition(gtaX, gtaY)` en el mismo punto del ancla devuelve el punto de Minecraft del ancla; `mcYawDegrees(gtaHeading)` con el rumbo del ancla devuelve el yaw del ancla.
  2. **Hacia delante:** ancla con rumbo GTA 0 (norte) y yaw 0 (sur): un coche que avanza 5 m al norte (`gtaY + 5`) queda 5 bloques al sur (`z + 5`); con yaw 90 (oeste) avanza 5 bloques al oeste (`x − 5`); con rumbo GTA 90° (π/2, oeste en GTA) y yaw 0, avanzar «hacia delante» en GTA (`gtaX − 5`) es `z + 5`.
  3. **Lateral:** un desplazamiento a la derecha del coche (con rumbo 0 y yaw 0, `gtaX + 5`) queda 5 bloques a la derecha del jugador (al oeste, `x − 5`, porque mirando al sur la derecha es el oeste).
  4. **Giro:** `mcYawDegrees(h0 − π/2)` (giro a la derecha de 90°) es `yaw0 + 90`; `mcYawDegrees(h0 + π/2)` es `yaw0 − 90`; el resultado se normaliza a (−180, 180].
  5. **Ida y vuelta:** `gtaCorrection(mcPosition(p) − mcPosition(q))` es `p − q` en el eje de GTA para varios pares de puntos y varios rumbos del ancla; la corrección de un desplazamiento nulo es cero.
  6. **Entradas rotas:** valores NaN o infinitos no producen NaN en la salida (se devuelve el ancla) y no lanzan.
- [ ] **Paso 2: Verlas fallar** — `cd cj-fabric && ./gradlew test --console=plain 2>&1 | grep -E "error:|FAILED|BUILD" | head -3`. Expected: error de compilación por `VehicleFrame` inexistente.
- [ ] **Paso 3: Implementar** — con las rotaciones de 2×2 exactas (seno y coseno de la diferencia de rumbos), sin ángulos acumulados, y los signos de los casos de arriba.
- [ ] **Paso 4: Verlas pasar** y **Paso 5: Mutaciones** — `./gradlew test`; `/tmp/mut_vframe.json` (formato de `tools/mutj.py`) con mutantes de cada signo, cada seno y coseno intercambiados, cada término sumado en vez de restado y la normalización; `python3 tools/mutj.py /tmp/mut_vframe.json`. Expected: todos matados; los equivalentes se resuelven simplificando.
- [ ] **Paso 6: Commit** — `git add cj-fabric/src/main/java/dev/cjmc/vehicle/VehicleFrame.java cj-fabric/src/test/java/dev/cjmc/vehicle/VehicleFrameTest.java && git commit -m "Vehicles: VehicleFrame (GTA to Minecraft coordinates)"` con la coautoría.

---

### Tarea 4: Colisión y terreno del coche (`VehicleCollision`, `VehicleTerrain`)

**Archivos:**
- Crear: `cj-fabric/src/main/java/dev/cjmc/vehicle/Terrain.java`, `VehicleCollision.java`, `VehicleTerrain.java`
- Test: `cj-fabric/src/test/java/dev/cjmc/vehicle/VehicleCollisionTest.java`, `VehicleTerrainTest.java`

**Interfaces:**
- Consume: nada (puro). `Terrain` es la interfaz con la que la lógica ve el mundo: `double top(int bx, int bz, double nearY)` (la altura de la superficie sólida más alta que quede por debajo de `nearY + STEP_HEIGHT`, o −∞ si no hay) y `double ceiling(int bx, int bz, double fromY)` (la altura del primer bloque sólido por encima de `fromY`, o +∞).
- Produce: `VehicleCollision.move(Terrain world, double x, double y, double z, double yawDegrees, double wantedX, double wantedZ)` → `Result{x, y, z, blocked}`: mueve la caja del coche (4,9 × 1,9 × 1,5) de `(x, z)` a `(wantedX, wantedZ)` por ejes; el suelo bajo cada una de sus 9 muestras (centro, esquinas, puntos medios de los lados, en el rumbo dado) determina si el escalón es subible (≤ `STEP_HEIGHT`) o bloquea, y el techo si cabe (altura 1,5); si choca, el movimiento por el eje que choca se anula y el otro sigue (deslizar); `blocked` dice si se anuló algo. `VehicleTerrain.height(...)`, `pitchRoll(...)`: la altura de reposo del coche, y su cabeceo y alabeo en radianes a partir de la altura del suelo bajo las cuatro ruedas (posiciones de las ruedas respecto al centro y rumbo), suavizados con un `VehicleTerrain.Smoother`; `VehicleTerrain.fall(y, vy)` la caída con la gravedad de Minecraft si no hay suelo.

- [ ] **Paso 1: Pruebas en rojo** — con terrenos de mentira (arrays de alturas por celda) y un coche de rumbo 0 y 90°:
  1. **Suelo plano:** moverse 3 bloques en un suelo plano da `blocked == false` y llega al destino con `y` igual al suelo.
  2. **Pared:** una columna de 3 bloques de alto a 4 bloques por delante: el coche avanza hasta que el borde de la caja toca la pared (`x` o `z` queda a medio largo del coche de la pared), `blocked == true` y no atraviesa; no queda dentro del bloque por pequeño que sea el paso (pasos de 0,01 y de 5 bloques dan el mismo punto final).
  3. **Escalón:** un escalón de 0,5 bloques se sube y la `y` sube 0,5; uno de 0,6 también (límite incluido); uno de 0,61 y de 1 bloque bloquea.
  4. **Deslizar:** una pared paralela a un movimiento diagonal deja pasar la componente libre y anula la otra.
  5. **Techo:** un techo a 1,4 bloques sobre el suelo no deja entrar; a 1,5 sí.
  6. **Rumbo:** el mismo muro bloquea a distinta distancia con rumbo 0 que con rumbo 90° (la caja es más larga que ancha) y con rumbos intermedios sigue sin atravesar (muestras de las esquinas).
  7. **Caer:** sin suelo debajo, `fall` baja la `y` con la gravedad de Minecraft y se detiene al llegar al suelo; con suelo a menos de 0,6 por debajo, se pega al suelo sin caer.
  8. **Inclinación:** con las cuatro ruedas a la misma altura, cabeceo y alabeo son 0; con las delanteras 1 bloque más altas (distancia entre ejes de 3,4), el cabeceo es `atan(1/3.4)` con el signo de «morro arriba»; con las de la derecha más altas, el alabeo es `atan(h/ancho de vía)` con su signo; el suavizado se acerca al objetivo sin pasarse en 1 y en 100 pasos.
  9. **Entradas rotas:** NaN o infinito no devuelven NaN ni lanzan: el resultado es la posición de partida con `blocked == true`.
- [ ] **Paso 2: Verlas fallar** — `cd cj-fabric && ./gradlew test --console=plain 2>&1 | grep -E "error:|FAILED|BUILD" | head -3`. Expected: error de compilación.
- [ ] **Paso 3: Implementar** — la caja como un rectángulo girado muestreado en sus 9 puntos; el movimiento en pasos de como mucho 0,25 bloques para no saltar una pared fina; un eje cada vez; el techo medido desde la altura del suelo ya subido; constantes con nombre (`LENGTH`, `WIDTH`, `HEIGHT`, `STEP_HEIGHT`, `WHEELBASE`, `TRACK`).
- [ ] **Paso 4: Verlas pasar** y **Paso 5: Mutaciones** — `./gradlew test`; `/tmp/mut_vcol.json` con mutantes de cada constante, cada comparador (`<=`, `<`), cada eje, el tamaño del paso, las muestras (quitar una esquina) y los signos de la inclinación; todos matados.
- [ ] **Paso 6: Commit** — rutas explícitas de los tres ficheros y sus pruebas y la coautoría: `Vehicles: VehicleCollision and VehicleTerrain (pure box against blocks, steps, slopes)`.

---

### Tarea 5: Entradas, volante, ruedas y condiciones (`VehicleRules`)

**Archivos:**
- Crear: `cj-fabric/src/main/java/dev/cjmc/vehicle/VehicleRules.java`
- Test: `cj-fabric/src/test/java/dev/cjmc/vehicle/VehicleRulesTest.java`

**Interfaces:**
- Consume: nada (puro).
- Produce: `VehicleRules.canEnter(boolean bridgeLive, boolean riding, boolean inWater, boolean jetpackFlying, boolean climbing, boolean knockedDown, boolean dead, boolean screenOpen, boolean fits)` y `VehicleRules.mustExit(boolean bridgeLive, boolean dead, boolean connected, boolean inCar)`; `VehicleRules.steer(double current, double wanted, double dtSeconds)` (el volante suavizado, −1 a 1); `VehicleRules.throttle(boolean forwardKey, boolean backKey, double speed)` → `{throttle, brake}` en 0 a 255 (adelante acelera; atrás frena si se va hacia delante y da marcha atrás si está casi parado o yendo atrás; ambas a la vez = freno); `VehicleRules.steerByte(double steer)` (−127 a 127 con redondeo); `VehicleRules.wheelSpin(double previousAngle, double distanceMeters, double radius)` (el ángulo que giran las ruedas al avanzar la distancia, normalizado) y `VehicleRules.frontWheelAngle(double steerRadians)`.

- [ ] **Paso 1: Pruebas en rojo** — `VehicleRulesTest` con:
  1. `canEnter` verdadero solo con todo en orden y `fits`; cada uno de los otros ocho parámetros, por separado, lo impide.
  2. `mustExit`: GTA sin responder, muerto o desconectado fuerzan la salida cuando está dentro; si no está dentro, no hay nada que forzar.
  3. `steer`: desde 0 con `wanted` 1, sube a razón de `STEER_RISE_PER_S` y no pasa de 1; desde 1 con `wanted` 0, baja a razón de `STEER_RETURN_PER_S` y no pasa de 0; cruzar de 1 a −1 pasa por 0; un `dt` enorme o negativo o NaN no salta ni da NaN.
  4. `throttle`: solo adelante, `{255, 0}`; solo atrás parado, `{255 de marcha atrás...}` según la convención de GTA que mida la Tarea 1 (acelerar con marcha atrás se hace con freno: el puente lo traduce a `ButtonSquare`); yendo hacia delante, atrás es freno; ambas a la vez, freno; ninguna, `{0, 0}`.
  5. `steerByte`: −1 → −127, 0 → 0, 1 → 127, 0,5 → 64, fuera de rango se acota.
  6. `wheelSpin`: avanzar `2π · radio` da una vuelta completa (ángulo 0 tras normalizar), `π · radio` da media vuelta; hacia atrás gira al revés; el resultado queda en (−π, π].
  7. `frontWheelAngle` es el volante tal cual hasta un tope (≈ 0,6 rad) con su signo.
- [ ] **Paso 2: Verlas fallar**, **Paso 3: Implementar**, **Paso 4: Verlas pasar** y **Paso 5: Mutaciones** como las tareas puras anteriores (todos los mutantes matados).
- [ ] **Paso 6: Commit** — `git add cj-fabric/src/main/java/dev/cjmc/vehicle/VehicleRules.java cj-fabric/src/test/java/dev/cjmc/vehicle/VehicleRulesTest.java && git commit -m "Vehicles: VehicleRules (enter conditions, steering, wheels)"` con la coautoría.

---

### Tarea 6: El modo vehículo del puente (`VehicleDriver` y el despacho por tipo)

**Archivos:**
- Crear: `gta-bridge/src/vehicle_driver.h`
- Modificar: `gta-bridge/src/bridge_core.h`, `gta-bridge/src/pad_driver.h`, `gta-bridge/src/ped_layout.h`
- Test: `gta-bridge/tests/test_vehicle_driver.cpp` (nuevo), `gta-bridge/tests/test_bridge_core.cpp`, `gta-bridge/tests/test_pad_driver.cpp`, y su entrada en `tools/run_native_tests.sh` / `gta-bridge/CMakeLists.txt` si hace falta

**Interfaces:**
- Consume: los paquetes de la Tarea 2 (`VehicleCommandPacket`, `VehicleInputPacket`, `EncodeVehicleState`, el tipo del paquete), `BridgeIo` (`Ped`, `NowMs`, `Recv`, `Send...`).
- Produce: en `BridgeIo` las operaciones del coche (puras en las pruebas, reales en la Tarea 7): `uint8_t* CreateVehicle(int model, Vec3 near, float gtaHeading)`, `bool SeatDriver(uint8_t* ped, uint8_t* vehicle)`, `void MakeVehicleSafe(uint8_t* vehicle, uint8_t* ped)`, `bool ReadVehicle(uint8_t* vehicle, uint8_t* ped, VehicleStatePacket& out)`, `void ApplyVehicleInput(const VehicleInputPacket&)` (escribe el mando), `void CorrectVehicle(uint8_t* vehicle, float dx, float dy)` (fija la posición y anula la velocidad que atraviesa), `void ExitDriver(uint8_t* ped, uint8_t* vehicle, float sideMetres)` y `void DeleteVehicle(uint8_t* vehicle)`; `VehicleDriver` con la máquina de estados `Idle → Entering → Driving → Exiting → Idle`, `OnCommand(const VehicleCommandPacket&)`, `OnInput(const VehicleInputPacket&, now)`, `Update(BridgeIo&, ped, now)` y `Reset()`; el despacho por tipo del `Tick` (los paquetes de vehículo no cuentan como mal formados); en modo vehículo el puente **no suelta a CJ** («CJ got into a vehicle» y «the game moved CJ» no se aplican) y sigue mandando `Pose`.

- [ ] **Paso 1: Pruebas en rojo** — en `test_vehicle_driver.cpp` con un `FakeIo` de vehículo:
  1. `OnCommand(subir)` desde `Idle`: llama a `CreateVehicle` una vez con el modelo y el rumbo pedidos, a `SeatDriver` y a `MakeVehicleSafe`, y pasa a `Driving`; con el mismo comando estando ya `Driving`, no crea otro coche.
  2. Si `CreateVehicle` devuelve nulo (no se pudo crear) o `SeatDriver` falla, no se queda a medias: se borra lo creado y se vuelve a `Idle`, y se avisa en el registro.
  3. Mientras `Driving`: cada `Update` lee el vehículo y manda el `VehicleState`; cada `OnInput` aplica el mando; con `corr_flags` bit 0 llama a `CorrectVehicle` con la corrección una sola vez por paquete (no se repite si el paquete no cambia: por `seq`).
  4. `OnCommand(bajar)` desde `Driving`: llama a `ExitDriver` y a `DeleteVehicle`, en ese orden, y vuelve a `Idle`; bajar estando `Idle` no hace nada.
  5. **Vigilante:** sin `VehicleInput` ni `VehicleCommand` durante 1000 ms estando `Driving`, baja y borra el coche; un reloj que va hacia atrás no lo deja colgado (diferencia sin signo).
  6. `Reset()` estando `Driving` (el ped cambió, se cargó otra partida o se soltó el puente): borra el coche solo si el ped sigue siendo el mismo, y si es otro, solo olvida el puntero sin tocar memoria (como `ClearBodyAnim(nullptr)`).
  7. `Update` nunca llama a nada con el ped nulo; con el coche desaparecido por el juego (puntero ya no válido según `ReadVehicle`), vuelve a `Idle`.
  En `test_bridge_core.cpp`: un paquete de vehículo no incrementa el contador de mal formados; en modo vehículo, estar `inVehicle` no suelta la marioneta ni descarta los estados, y se sigue mandando `Pose` y `VehicleState`; al volver a `Idle` y salir del coche, la marioneta se vuelve a controlar. En `test_pad_driver.cpp`: en modo vehículo el mando no anda a CJ ni lo hace saltar (la regla de `kFlagInWater`/jetpack se extiende al vehículo).
- [ ] **Paso 2: Verlas fallar** — `bash tools/run_native_tests.sh 2>&1 | tail -4`. Expected: error de compilación (`VehicleDriver` y las operaciones nuevas no existen).
- [ ] **Paso 3: Implementar** — `vehicle_driver.h` (puro: solo usa `BridgeIo`), las operaciones nuevas en `BridgeIo` y su `FakeIo`, el despacho por tipo en el `Tick`, el no soltar a CJ en modo vehículo (`bridge_core.h`) y la regla del mando.
- [ ] **Paso 4: Verlas pasar** — el comando del paso 2. Expected: todas `OK`.
- [ ] **Paso 5: Mutaciones** — mutantes nativos (escribir el mutante y ejecutar `bash tools/run_native_tests.sh`; el fallo debe ser de una prueba): cada transición de estado, el orden de `ExitDriver` y `DeleteVehicle`, el vigilante (`>=` y el reloj hacia atrás), la corrección por `seq`, el borrado en `Reset` (mismo ped o no), el no soltar a CJ, el mando. Cada mutante se restaura con `git checkout`.
- [ ] **Paso 6: Commit** — rutas explícitas y la coautoría: `Vehicles: the vehicle mode of the bridge (VehicleDriver) and the dispatch by packet type`.

---

### Tarea 7: Las operaciones reales del coche en el juego (`WinIo`)

**Archivos:**
- Modificar: `gta-bridge/src/bridge.cpp`, `gta-bridge/src/ped_layout.h`, `tools/verify_exe_addresses.py`

**Interfaces:**
- Consume: la Tarea 6 (las operaciones de `BridgeIo`), la Tarea 1 (el campo del volante y el desplazamiento de CJ), el experimento del scratchpad como modelo.
- Produce: `WinIo::CreateVehicle`, `SeatDriver`, `MakeVehicleSafe`, `ReadVehicle`, `ApplyVehicleInput`, `CorrectVehicle`, `ExitDriver` y `DeleteVehicle` con las llamadas de las Global Constraints; direcciones nuevas verificadas en `verify_exe_addresses.py` (los prólogos de `CreateCarForScript`, `CTaskSimpleCarSetPedInAsDriver` ctor y `ProcessPed`, `FlushImmediately`, `CPed::Teleport` y `CWorld::Remove`).

- [ ] **Paso 1: Prólogos** — leer del `.exe` real los 10 primeros bytes de cada función nueva (como en el jetpack) y añadirlos a `verify_exe_addresses.py`; `python3 tools/verify_exe_addresses.py 2>&1 | grep -c FAIL` debe dar 0.
- [ ] **Paso 2: Implementar** — `CreateVehicle` (pide el modelo y lo carga, llama a `CreateCarForScript` con `z` muy baja cerca de CJ y gira el coche al rumbo pedido escribiendo su matriz con la rotación exacta); `SeatDriver` (la tarea de sentar con la bandera de entrar de golpe, `ProcessPed`, y liberar la tarea con `delete` virtual si el juego lo exige: comprobar con el mismo método de la investigación que no se filtra); `MakeVehicleSafe` (coche indestructible: banderas de `CPhysical` de invulnerable, a prueba de fuego y explosiones, motor encendido, estado de coche del jugador, sin cerrojo; y la protección de CJ de `ProofShield`); `ReadVehicle` (posición, rumbo, cabeceo y alabeo desde la matriz del coche, velocidad hacia delante, volante, y la posición y el rumbo de CJ en los ejes del coche); `ApplyVehicleInput` (escribe en el mando acelerador, freno, volante y freno de mano, **después** del mando normal del puente); `CorrectVehicle` (suma la corrección a la posición y quita de la velocidad la componente hacia el obstáculo); `ExitDriver` (`FlushImmediately(true)` sobre la inteligencia verificada y `Teleport` al lado del coche) y `DeleteVehicle` (`CWorld::Remove` y el destructor virtual con la bandera 1, solo si el puntero sigue en la lista de vehículos).
- [ ] **Paso 3: Compilar y comprobar** — `bash tools/install_bridge.sh`; `bash tools/run_native_tests.sh 2>&1 | grep -c OK` (las 9 suites); `python3 tools/verify_exe_addresses.py 2>&1 | grep -c FAIL` (0). Lo que el juego hace de verdad se prueba en vivo en la Tarea 11.
- [ ] **Paso 4: Commit** — rutas explícitas y la coautoría: `Vehicles: the real car operations of the bridge`.

---

### Tarea 8: El conversor de vehículos

**Archivos:**
- Crear: `tools/cjconvert/vehicles.py`
- Modificar: `tools/cjconvert/dff.py` (leer los nombres de los marcos y las matrices anidadas), `tools/cjconvert/glb.py` (colores por material si hace falta), `tools/convert_assets.py`
- Test: `tools/tests/test_vehicles.py`

**Interfaces:**
- Consume: la Tarea 1 (colores de pintura, radio de la rueda), `parse_static_part`/`_frames` como base, `ImgArchive`, `parse_txd`.
- Produce: `dff.parse_frames(data)` → lista de marcos con su nombre, su padre y su matriz mundial; `vehicles.load_vehicle(gta_dir, name)` → un modelo con una malla de carrocería (piezas `_ok`, sin `_dam` ni `ug_*` ni `_vlo`, con las matrices de los marcos aplicadas) y una malla de rueda (la de `wheel`, centrada en su marco) más las cuatro posiciones de rueda (`wheel_lf_dummy`, `wheel_rf_dummy`, `wheel_lb_dummy`, `wheel_rb_dummy`) y la posición del asiento del conductor, todo en los metadatos del `.glb`; `convert_assets.py --vehicles-only` escribe `vehicles/greenwood.glb` junto a los de las armas.

- [ ] **Paso 1: Pruebas en rojo** — `test_vehicles.py` con instalaciones sintéticas (`rwbuild`, como `test_weapons.py`) y una prueba con la instalación real (se salta si no está):
  1. `parse_frames` devuelve los nombres leídos de la extensión de marco y las matrices mundiales de marcos anidados (una puerta bajo la carrocería queda desplazada por la suma de sus marcos).
  2. El conversor elige las piezas `_ok` y deja fuera las `_dam`, las `ug_*` y la carrocería de baja resolución `chassis_vlo`; un modelo sin carrocería (`chassis`) da un error con el nombre del fichero.
  3. La rueda sale como una sola malla y las cuatro posiciones salen de los marcos `wheel_*_dummy` (con el signo y el orden de la Tarea 1: izquierda y derecha, delante y detrás).
  4. Los materiales sin textura con el color «mágico» de pintura se sustituyen por el color del coche elegido y se cuecen como una textura de un píxel; los de otro color se conservan; los materiales con textura usan la de la `.txd` propia o la de `vehicle.txd` y, si no está en ninguna, un error que nombra la textura.
  5. El `.glb` sale válido (`glb.read_glb`) con los metadatos de las ruedas y del asiento.
  6. Con la instalación real: `greenwoo` se convierte, tiene al menos 3000 vértices en la carrocería, una rueda y los colores esperados.
- [ ] **Paso 2: Verlas fallar** — `cd tools && python3 -B -m unittest discover -s tests 2>&1 | tail -4`. Expected: fallos por módulo inexistente.
- [ ] **Paso 3: Implementar** — la lectura de nombres con la extensión `0x253F2FE`, la composición de matrices por la jerarquía, la elección de piezas por nombre, las ruedas, los colores y el `--vehicles-only`.
- [ ] **Paso 4: Verlas pasar** — el comando del paso 2 y `python3 tools/convert_assets.py --vehicles-only`. Expected: `OK` y `greenwood.glb` escrito.
- [ ] **Paso 5: Mutaciones** — mutantes de los filtros de nombre (`_ok`, `_dam`, `ug_`), de la composición de matrices (el orden de la multiplicación) y de la sustitución de colores; todos matados.
- [ ] **Paso 6: Commit** — rutas explícitas y la coautoría: `Vehicles: the vehicle converter (named frames, parts, wheels, paint)`.

---

### Tarea 9: Minecraft: subir, conducir, colisionar y mover al jugador (`VehicleClient`)

**Archivos:**
- Crear: `cj-fabric/src/main/java/dev/cjmc/VehicleClient.java`, `cj-fabric/src/main/java/dev/cjmc/VehicleWorld.java` (la implementación de `Terrain` sobre los bloques)
- Modificar: `cj-fabric/src/main/java/dev/cjmc/CjClient.java`, `cj-fabric/src/main/java/dev/cjmc/BodyClient.java`, `cj-fabric/src/main/java/dev/cjmc/mixin/KeyboardInputMixin.java`, `cj-fabric/src/main/java/dev/cjmc/MovementPolicy.java`

**Interfaces:**
- Consume: `VehicleFrame` (Tarea 3), `VehicleCollision` y `VehicleTerrain` (Tarea 4), `VehicleRules` (Tarea 5), los paquetes (Tarea 2), `CjClient.bridgeLive()`, `BodyClient`, `ClimbController`, `JetpackClient`, `SwimClient`.
- Produce: `VehicleClient.tick(ClientPlayerEntity player, long nowMs)` (cada tick), `VehicleClient.driving()` (volátil, leído por el dibujo y las demás piezas), `VehicleClient.onState(VehicleState)` (llamado por el receptor al llegar un paquete del puente), `VehicleClient.pose()` (posición, orientación, cabeceo, alabeo, giro de las ruedas, volante y el sitio de CJ, para el dibujo); la tecla `G` registrada en las opciones de controles; los controles de a pie y las armas inmovilizados mientras se conduce.

- [ ] **Paso 1: `VehicleWorld`** — implementa `Terrain` sobre el mundo del cliente: `top(bx, bz, nearY)` baja desde `nearY + STEP_HEIGHT` por la columna hasta el primer bloque con forma de colisión no vacía y devuelve la altura máxima de esa forma (losas y escaleras incluidas); `ceiling(bx, bz, fromY)` sube hasta el primer bloque con colisión; el agua no es suelo (el coche no la pisa).
- [ ] **Paso 2: `VehicleClient.tick`** — con la tecla pulsada (flanco): si `VehicleRules.canEnter(...)` (con `fits` calculado con `VehicleCollision` sobre `VehicleWorld`: la caja en el sitio sin movimiento no choca), manda `VehicleCommand` de subir con el yaw del jugador; al llegar el primer `VehicleState` con CJ dentro, fija `VehicleFrame.anchor` con la posición del jugador y su yaw y la posición y el rumbo del estado; mientras se conduce: lee W, S, A, D y Espacio (con `client.options`, no con las entradas ya filtradas), calcula `VehicleRules.steer/throttle`, manda `VehicleInput` cada tick; calcula la posición deseada con `VehicleFrame`, la resuelve con `VehicleCollision.move` (con la altura y la inclinación de `VehicleTerrain`), mueve al jugador con `setPosition` (velocidad a 0, como el trepado) y, si `blocked`, pone la corrección en el siguiente `VehicleInput` (con `gtaCorrection` de la diferencia entre la posición deseada y la válida); con la tecla otra vez o `VehicleRules.mustExit(...)`, manda `VehicleCommand` de bajar y, al llegar el estado con CJ fuera, coloca al jugador al lado del coche y vuelve a lo normal; muerte, desconexión, cambio de mundo o de jugador: bajada de emergencia (cancela el modo y manda bajar). Un paquete viejo (por `ack_seq`) o repetido no mueve el coche.
- [ ] **Paso 3: Integración** — registrar la tecla y el `END_CLIENT_TICK`; el receptor de paquetes del puente despacha por tipo y llama a `VehicleClient.onState`; `BodyClient.frozen()` y los mixins de entrada y de ataque respetan el modo vehículo; el jetpack, nadar y trepar no actúan mientras se conduce (su `allowed` gana la condición); `ClimbController`, `SwimClient` y `JetpackClient` ya reciben `VehicleClient.driving()` en sus condiciones; el tiempo muerto del cliente: sin estados del puente en 1 s se baja.
- [ ] **Paso 4: Compilar y ejecutar las suites** — `cd cj-fabric && ./gradlew test --console=plain 2>&1 | grep -E "error:|FAILED|BUILD"`. Expected: `BUILD SUCCESSFUL`, sin pruebas rotas. (El movimiento real se comprueba en vivo: Tarea 11.)
- [ ] **Paso 5: Commit** — rutas explícitas y la coautoría: `Vehicles: boarding, driving, collisions and moving the player (VehicleClient)`.

---

### Tarea 10: Dibujar el coche y a CJ sentado (`VehicleRig`)

**Archivos:**
- Crear: `cj-fabric/src/main/java/dev/cjmc/VehicleRig.java`
- Modificar: `cj-fabric/src/main/java/dev/cjmc/CjRenderer.java`, `cj-fabric/src/main/java/dev/cjmc/WeaponModelLoader.java` (si hace falta leer los metadatos de ruedas y asiento)

**Interfaces:**
- Consume: `greenwood.glb` (Tarea 8) con las cuatro posiciones de rueda y el asiento, `VehicleClient.pose()` (Tarea 9), `VehicleRules.wheelSpin/frontWheelAngle` (Tarea 5), el patrón de `WeaponRig` (carga, texturas, `MeshEmitter`).
- Produce: `VehicleRig.draw(...)` que dibuja la carrocería con la orientación y la inclinación del coche y las cuatro ruedas con su giro y el volante en las delanteras; `CjRenderer` dibuja a CJ **en el sitio de GTA respecto al coche** (el puente manda su desplazamiento y su rumbo relativo) en vez de en la posición del jugador, con la pose de GTA, y sin dibujar al jugador de Minecraft; la altura y el rumbo del coche siguen al jugador (que va con el coche).

- [ ] **Paso 1: Implementar** — la carga del `.glb` (si no está o falla, el coche no se dibuja y se registra una vez: sin GTA el coche no existe, pero el cliente no se cae), el dibujo del coche con matrices (guiñada, cabeceo y alabeo, y las ruedas sobre sus marcos con el giro y el volante) y el cambio de `CjRenderer` para el modo vehículo; sin tocar el dibujo normal de CJ.
- [ ] **Paso 2: Compilar y ejecutar las suites** — `./gradlew test --console=plain`. Expected: `BUILD SUCCESSFUL`. (El aspecto se ajusta en vivo: Tarea 11.)
- [ ] **Paso 3: Commit** — rutas explícitas y la coautoría: `Vehicles: drawing the car and CJ sitting in it (VehicleRig)`.

---

### Tarea 11: Verificación en vivo, resultados y revisión final

**Archivos:**
- Crear: `docs/vehicle-results.md`
- Modificar: `docs/superpowers/specs/2026-10-09-vehicles-car-design.md` (si algo cambió respecto al diseño)

**Interfaces:**
- Consume: todas las anteriores.
- Produce: el documento de resultados con lo medido y ajustado en vivo.

- [ ] **Paso 1: Suites completas** — Python (`cd protocol && python3 -B -m unittest test_cjproto`, `cd tools && python3 -B -m unittest discover -s tests`), nativas, Java y `python3 tools/verify_exe_addresses.py`. Expected: todo en verde.
- [ ] **Paso 2: Instalar y abrir** — `python3 tools/convert_assets.py --vehicles-only`, `bash tools/install_bridge.sh`, luego `tools/start_games.sh --restart > /tmp/cj-start.log 2>&1 < /dev/null`; decir al usuario cuándo cargar la partida (CJ debe estar en el aeropuerto) y entrar a un mundo con zonas abiertas, un muro, un escalón de un bloque, una cuesta y un borde.
- [ ] **Paso 3: Con el usuario, en vivo:** subir donde cabe y donde no (un espacio estrecho), conducir y frenar, el giro suave, el freno de mano, marcha atrás, chocar contra una pared de frente y de lado y a distintas velocidades (parar sin rebote y sin atravesar), subir un escalón de medio bloque y no uno de un bloque, una cuesta suave, caer de un borde, bajar (CJ a un lado y el coche borrado), que CJ vuelve a ser CJ a pie, subir y bajar varias veces seguidas, cerrar GTA o morir en el coche (sale del modo vehículo sin coche huérfano), y que la partida de GTA queda intacta (vida, posición, nada raro tras bajar). Mirar el dibujo: el color, las ruedas girando y el volante, CJ sentado bien colocado, el coche inclinado según el terreno. Ajustar la caja, el escalón, el volante, la inclinación y el sitio de CJ jugando; anotarlos.
- [ ] **Paso 4: `docs/vehicle-results.md`** — qué se hizo, constantes finales, lo medido con el usuario, decisiones y límites (zona abierta, un modelo, sin coches aparcados, sin motos, sin sonido propio, cámara libre).
- [ ] **Paso 5: Revisión final independiente y una pasada de arreglos** — con el procedimiento de executing-plans (revisor de solo lectura con el modelo más capaz, repaso de gravedad por efecto, una sola pasada de arreglos con rojo→verde, ledger con `Final:`); luego borrar el espacio de trabajo `.superpowers/sdd/2026-10-09-vehicles-car` e informar de «Rulings I made» y «Deferred minors».
- [ ] **Paso 6: Commit** — `git add docs/vehicle-results.md` (y la spec si cambió) y `git commit -m "Vehicles: results"` con la coautoría.
