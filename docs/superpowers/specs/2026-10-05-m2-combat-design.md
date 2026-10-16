# M2 — Armas, apuntado y disparo (diseño)

Fecha: 2026-10-05. Continúa el diseño principal (`2026-10-05-cj-in-minecraft-design.md`, secciones 4 y 6) y M1.

## 1. Objetivo y criterio de éxito

Que CJ sostenga un arma real de GTA SA y dispare como en el juego, con el control de GTA PC: **clic derecho mantenido = apuntar, clic izquierdo = disparar**. Minecraft decide dónde pega el disparo, y en M2 solo lo **marca**; todavía no hace daño (eso es M3).

**El hito está cumplido cuando**, en un mundo de Minecraft con GTA corriendo:
1. Con un arma en la mano, CJ la lleva en la mano con su **modelo 3D real** de GTA. Con cualquier otro ítem va desarmado.
2. Al mantener el clic derecho, CJ levanta el arma y la apunta hacia donde mira el jugador, con la animación de apuntar de GTA, y aparece una mira en pantalla (el cursor de Minecraft no vale, porque CJ apunta con puntería libre).
3. Al pulsar el clic izquierdo, CJ dispara con la cadencia, el cargador y la recarga de GTA, y el sonido de GTA.
4. Cada disparo llega a Minecraft como evento. Minecraft lanza un rayo desde la mira del jugador y dibuja una traza y una marca de impacto en el bloque o la entidad que toca. No hay daño.
5. Sin GTA, o con GTA caído, el arma es solo un ítem inerte y no pasa nada raro.

**Decisiones del usuario** (2026-10-05): modelo real del arma en la mano desde M2; puntería libre sin enfoque automático (la ayuda de enfoque de GTA queda fuera); controles como GTA PC.

**Fuera de M2:** daño a mobs, rotura de bloques, explosiones y armas arrojadizas (M3); armas cuerpo a cuerpo (M3, usan el evento de golpe); trepar, agacharse real y reacciones (M4); lanzallamas, minigun, francotirador con visor, RPG y granadas.

## 2. Enfoque técnico: tarea de GTA con punto objetivo

GTA dispara y apunta a los NPC con `CTaskSimpleUseGun`, que recibe una posición objetivo y una orden (`AIM`, `FIRE_BURST`, ...) y se encarga de animación, orientación de los brazos, cadencia y cargador. Solo cuando el ped es el jugador **y** la cámara de GTA está en `MODE_AIMWEAPON` toma el objetivo de la cámara; aquí la cámara no está en ese modo, así que la tarea usa el punto que le demos (`GetAimTargetPosition`, `0x61DE60` es el constructor, `CWeapon::Fire` es `0x742300`).

- El puente asigna a CJ una `CTaskSimpleUseGun` como **tarea secundaria de ataque** y refresca cada frame el punto objetivo: un punto a ~20 m delante, en la dirección de la mira de Minecraft, dentro del escenario de GTA.
- Si la tarea no se deja usar así, el plan B es el que figura en el diseño principal: GTA reproduce una animación de apuntar y el mod inclina torso y brazos según el pitch, con pérdida de cadencia y cargador reales.
- **Primer paso del plan: una prueba corta (spike) con GTA real** que valide esto antes de construir nada encima. Es la única decisión de la que depende el resto; si falla, el plan B reordena M2.

## 3. Protocolo v2

Pasa de la versión 1 a la 2. El mod y el puente rechazan paquetes de otra versión; los ficheros dorados y `cjproto.py` se regeneran y `PROTOCOL.md` se actualiza.

- **Estado (MC→GTA):** se añaden el punto de mira (`aimX`, `aimY`, `aimZ`, 3 floats, en coordenadas del mundo de Minecraft ya convertidas a GTA relativas a CJ) y el estado de entrada ya presente en `intents` (apuntar = 1, disparar = 2, golpear = 4). `weapon` (u16) pasa a ser el **id de arma de GTA** del ítem sostenido (0 = desarmado).
- **Evento de disparo (GTA→MC, tipo 4, 28 bytes):** `ackSeq`, `gtaTimeMs`, `weapon`, `shotSeq` (contador que sube con cada disparo para descartar duplicados) y, de reserva, la dirección del cañón de GTA (3 floats; el rayo de Minecraft no la usa).
- **Eco** (tipo 2): sin cambios de significado; se añade `weaponHeld` (u16) para que el mod vea qué arma tiene realmente CJ y pueda mostrar un aviso si no coincide.
- Los eventos se mandan **dos veces** (el mismo `shotSeq`) para tolerar la pérdida de un datagrama en localhost; el mod descarta el repetido.

## 4. Puente de GTA (`gta-bridge`)

- **`weapon_driver.h`:** dado el arma pedida por Minecraft y el estado de apuntar/disparar, decide qué hacer: dar el arma (`CPed::GiveWeapon`, `0x5E6080`, con munición grande), seleccionarla (`SetCurrentWeapon`, `0x5E6280`), quitarla (`ClearWeapons`, `0x5E6320`) y qué orden dar a la tarea (`AIM`, `FIRE_BURST`, `CEASE`). Es lógica pura, probada con pruebas nativas como el resto.
- **Asignación de la tarea:** una sola función aislada llama al constructor del juego y fija la tarea en la ranura de ataque; el resto es lógica probada. Cada llamada nativa lleva su guarda (ped inexistente, en vehículo, arma no válida) y su registro.
- **Gancho en `CWeapon::Fire` (`0x742300`):** solo para el arma de CJ, empaqueta un evento de disparo y lo manda; la función original se ejecuta siempre y sin cambios, igual que se hizo con `CGame::Process`.
- **Cargador:** lo gestiona GTA. La munición del ítem es infinita; al agotar el cargador, GTA recarga con su animación.
- **Salida de seguridad:** si deja de llegar estado, CJ guarda el arma y la tarea se cancela (igual que la liberación de la marioneta, ya existente).

## 5. Mod de Minecraft (`cj-fabric`)

- **Ítems de arma** (`weapons.json` + `WeaponItem`): pistola (Colt 45), Desert Eagle, escopeta (Chromegun), Uzi, AK-47 y M4 en M2; los demás llegarán con sus mecánicas. El fichero define por arma: ítem, id de GTA, modelo, y ya deja los campos que usará M3 (daño, alcance, perdigones, dispersión, poder contra bloques) con los valores de GTA, sin que M2 los use. Se obtienen con `/give` y desde la pestaña creativa.
- **Entrada** (`AimInput`): con un arma de CJ en la mano, el clic derecho mantenido pone `intents` apuntar y el izquierdo, disparar. Mientras sostienes un arma el clic izquierdo no ataca ni rompe bloques con la mano, y el derecho no usa el ítem. Con cualquier otro ítem, Minecraft funciona como siempre. Un solo punto de decisión (`isCjWeapon(stack)`).
- **Punto de mira** (`AimRay`): cada tick, el rayo de la cámara del jugador (con la parte de render para que sea suave) fija el punto objetivo y se manda en el estado.
- **Mira en pantalla:** un cursor propio en el centro mientras se apunta; sin él, nada.
- **Resolución del disparo** (`ShotResolver`): al recibir un evento de disparo, lanza un rayo desde la mira del jugador (no desde el cañón de GTA) en el hilo del servidor integrado, resuelve el impacto sobre bloque o entidad y manda de vuelta al cliente la marca. En M2 el efecto es una **traza breve y una marca de impacto**, sin daño. M3 sustituye la marca por daño y rotura.
- **Modelo del arma en la mano:** `GlbModel` ya lee mallas con piel; las armas son mallas estáticas, que se cargan como una parte sin huesos y se dibujan acopladas al hueso de la mano derecha con la pose recibida (la mano del `Pose` ya está en el espacio del modelo). La posición y la rotación dentro de la mano salen de las constantes de GTA para esa arma; si no cuadran, se ajustan por arma en `weapons.json` (`holdOffset`, `holdRotation`) tras mirarlas en el juego.
- **Si falta el modelo del arma**, se dibuja a CJ sin ella y se avisa una vez en el registro; nunca se rompe el render.

## 6. Conversor (`tools/convert_assets.py`)

- Amplía el conversor con `--weapons`: lee `gta3.img` (modelos `.dff`) y los TXD correspondientes con la misma tubería de M1b y escribe un `.glb` estático por arma en `cj-assets/weapons/` (git-ignorado, igual que `cj.glb`). Los modelos y texturas se extraen de la instalación del usuario y no entran al repositorio.
- Los modelos presentes en tu `gta3.img` (comprobado): `colt45`, `silenced`, `desert_eagle`, `chromegun`, `sawnoff`, `shotgspa`, `micro_uzi`, `mp5lng`, `ak47`, `m4`, `tec9`, `cuntgun`, `sniper`, `rocketla`, `flame`, `minigun`, `grenade`, `bat` y otros.
- Prueba: el `.glb` de cada arma pasa el validador de glTF y su vista previa PNG se compara a ojo.

## 7. Errores y casos límite

- Sin GTA o sin pose: el arma sigue siendo un ítem, sin efecto. Nada se dibuja a medias.
- Dos armas rápidas seguidas (cambio de ítem): el puente tolera un cambio por frame y descarta el anterior; el eco informa del arma que tiene.
- Recarga: mientras GTA recarga, los disparos pedidos se ignoran y no se manda evento.
- Munición: infinita (se vuelve a dar al recargar, no hay cuenta en Minecraft).
- Eventos perdidos o duplicados: `shotSeq` y el envío doble los cubren.
- Pose obsoleta (>500 ms): vuelve el jugador vanilla y se oculta la mira.
- Jugador en inventario o dormido: se ignora la entrada de armas, como el render de CJ.

## 8. Pruebas

- **Protocolo:** los ficheros dorados v2 y la prueba cruzada Python, C++ y Java, como en M0.
- **`weapon_driver`** (nativa): tabla de transiciones (sin arma → dar y seleccionar; apuntar → orden `AIM`; disparar → `FIRE_BURST`; soltar → `CEASE`; cambio de arma; recarga; sin estado → quitar).
- **`AimRay`** (Java): conversión de dirección y pitch a punto objetivo, incluidos los extremos (mirar recto arriba y abajo).
- **`ShotResolver`** (Java): rayo que toca bloque, entidad, nada, y el más cercano de los dos; deduplicación por `shotSeq`.
- **`GlbModel`:** mallas estáticas (sin `JOINTS_0`) y la parte acoplada.
- **Conversor:** `.glb` de arma con vértices, UV y textura; validador de glTF.
- **Mutaciones** en cada módulo, como en M1.
- **En vivo con GTA real:** pistola en la mano, apuntar arriba y abajo y hacia los lados, ráfagas, recarga, cambio de arma, soltar el ítem y GTA cerrado a medias. Cada comprobación con captura o registro.
- **Revisión final independiente** del conjunto y una pasada de arreglos, como en M1.

## 9. Riesgos

- **`CTaskSimpleUseGun` fuera de su flujo normal:** puede no apuntar al punto dado en un `CPlayerPed`, o chocar con `CTaskSimplePlayerOnFoot`. Se valida primero con el spike; plan B en la sección 2.
- **Dirección real del cañón de GTA frente a la mira de Minecraft:** las balas de GTA saldrían desviadas, pero M2 solo usa el rayo de Minecraft, así que no importa para el resultado. Se registra la diferencia para M3.
- **Acople del modelo a la mano:** las constantes de GTA pueden no coincidir con el esqueleto convertido; se ajusta por arma.
- **Sonido:** sale de GTA, sin posición. Si molesta, se ajusta después.
- **Hilos:** el evento llega por el hilo de red y la resolución corre en el servidor integrado; la comunicación entre ellos se hace por cola sincronizada.

## 10. Desviaciones decididas al escribir el plan (2026-10-05)

- **Disparos:** el puente los detecta viendo bajar el cargador del arma activa en vez de enganchar `CWeapon::Fire` (sección 4); evita un *detour* nuevo. Si alguna arma no baja el cargador, se vuelve al gancho.
- **Resolución del rayo:** en M2 es del cliente y solo marca el impacto con partículas; M3 la mueve al servidor integrado (sección 5).
- **Mira:** se usa la de Minecraft (el cursor del centro de la pantalla), que ya es el punto hacia donde va el rayo; no se dibuja una mira propia (sección 1, punto 2).
- **Modelos de arma:** el `.glb` de cada arma lleva una sola articulación (el hueso de la mano derecha, nodo 24) con matriz de enlace inversa identidad; así se reutilizan el cargador, el *skinner* y el buffer de poses (sección 6).
- **Protocolo v2:** `Shot` mide 24 bytes y sin dirección de cañón; `State` mide 72 (lleva `aim`); `Echo` mide 32 (lleva `weapon_held`).
