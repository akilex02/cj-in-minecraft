# M2 — Resultados

Fecha de la prueba: 2026-10-05 y 2026-10-06. Detalle de la puerta de apuntado y de las mediciones: `docs/m2-spike.md`.

## Pruebas automáticas
- **Protocolo** (`cd protocol && python3 -B -m unittest test_cjproto`): 15 pruebas (v2: `State` 72 bytes con punto de mira, `Echo` 32 con arma, `Shot` 24, rumbo del ped en `Pose`) y ficheros dorados `*_v2.bin` compartidos por Python, C++ y Java.
- **Nativas del puente** (`bash tools/run_native_tests.sh`): 7 suites (`protocol`, `puppet`, `pose`, `pad_driver`, `call_hook`, `weapon_driver`, `bridge_core`).
- **Python** (`cd tools && python3 -B -m unittest discover -s tests`): 105 pruebas, con el decodificador DXT, las mallas estáticas con tiras, la conversión de armas, el cliente falso (`fake_mc`) y una prueba con las seis armas reales de la instalación (se salta si no hay instalación).
- **Java** (`cd cj-fabric && ./gradlew test`): 107 pruebas, 0 fallos.
- **Direcciones del exe** (`tools/verify_exe_addresses.py`): 13 comprobaciones (4 de M0 y 9 de M2, entre ellas las funciones de armas y del *streaming*).
- Cada módulo nuevo se comprobó con mutaciones (cambiar un signo, quitar una guarda, intercambiar un orden); las que sobrevivieron eran equivalentes (se simplificó el código) o revelaron pruebas que faltaban (se añadieron). Una pasada de mutaciones propia salió «falsa» porque la prueba nueva había caído dentro de la clase auxiliar y no compilaba; se detectó y se repitió bien.

## Verificación en vivo (GTA SA real + Minecraft real, con el usuario)
- **Armas en la mano:** las seis (Colt 45, Desert Eagle, escopeta, Micro SMG, AK-47, M4) con su modelo real de GTA, bien agarradas con la posición por defecto (`holdOffset` y `holdRotation` a cero en `weapons.json`; no hizo falta calibrarlas). ✔
- **Apuntar y disparar:** clic derecho apunta, clic izquierdo dispara, con la animación, la cadencia, el cargador y el sonido de GTA, hacia cualquier rumbo y cualquier cabeceo. ✔
- **Marcas del impacto:** traza de partículas e impacto en bloques cercanos y lejanos, en el aire y en mobs, siempre, también tras cambiar de arma. ✔
- **Cambio de ítem:** CJ cambia de arma y se queda desarmado con un ítem normal. ✔
- **Inventario:** abrirlo con un arma en la mano no dispara ni deja a CJ apuntando. ✔
- **Cierre con calma:** dejar de apuntar y dejar GTA sin tocar no lo cierra. ✔
- **Velocidad:** con la Colt 45 y la Micro SMG se mueve a velocidad normal apuntando y se puede disparar en movimiento; con la Desert Eagle, la escopeta, la AK-47 y la M4 apuntando va muy despacio y disparando se queda quieto, y con la escopeta y los fusiles no se puede esprintar, como en GTA. ✔

## Problemas encontrados y cómo se resolvieron
1. **Offset de `CPed::m_pIntelligence`:** 0x47C, no 0x440 (hallado escaneando un ped vivo).
2. **GTA se cae al dar un arma cuyo modelo no está cargado** (`CPed::AddWeaponModel`): se pide el modelo al *streaming* antes y no se da nunca un arma sin modelo conocido (`WeaponModelId`).
3. **El flujo del jugador cierra una tarea de apuntar propia cada fotograma.** Se usa la del propio juego, que se mantiene con el mando simulado, y se le escribe el objetivo y se reinician las banderas de IK cada fotograma.
4. **GTA se cerraba solo al soltar la marioneta:** `ClearWeapons` con la tarea de apuntar viva. Ahora se termina la tarea y el arma se quita cuando ya no existe (con tope de 60 fotogramas); la revisión final reforzó esto (ver más abajo).
5. **La pose va relativa al rumbo de CJ en GTA:** se envía el rumbo (`Pose.heading`, 16 bits) y el cuerpo se dibuja con él mientras se apunta. Además GTA no gira a CJ al apuntar, así que el puente lo gira (`SetPedHeading`) después de leer la pose de cada fotograma.
6. **`skipAim` (sin apuntar, sin retícula) en un cono de ±60° detrás del rumbo inicial:** la tarea solo acepta objetivos dentro de unos +115°/−130° del rumbo de **su cámara**. La causa estaba en un offset mal contado (0x190 en vez de 0x150 para el vector frontal de `CCam`). Con el offset correcto: 0 de 1499 fotogramas con `skipAim` en un giro completo (antes 30–70 %). Idea del usuario: que la cámara de GTA siga a la de Minecraft.
7. **Torso torcido con los fusiles y la escopeta:** para esas armas el juego devuelve al ped, dentro del fotograma, al rumbo derivado del **ángulo horizontal de la cámara** (`CCam+0x7C` = rumbo − 90°). Escribiéndolo, la postura del torso queda constante en un giro completo y en un barrido de cabeceo. La Desert Eagle resultó apuntar con el brazo, como la Colt.
8. **Marcas que desaparecían:** el puente reiniciaba el contador de disparos al cambiar de arma y el mod ignoraba un contador menor. Ahora el contador nunca retrocede (`ShotCounter::Rebase`) y el mod toma un contador que retrocede como un reinicio de GTA.
9. **El jugador de Minecraft corría más que CJ con las armas pesadas:** límites por arma en `weapons.json` (`aimSpeed`, `sprintHeld`, `sprintAiming`, `moveWhileFiring`), con valores medidos en GTA y aplicados con dos mixins. Con la Desert Eagle, la escopeta, la AK-47 y la M4 el jugador se queda inmóvil mientras mantiene el clic izquierdo (en GTA se disparan quietas); con la Colt 45 y la Micro SMG se dispara en movimiento.

Los experimentos temporales (banderas de depuración en un fichero, escaneos de memoria, volcados, enganche de `ProcessPed`, modo de recreación de la tarea, escritura de la matriz de la cámara) se retiraron del código final.

## Desviaciones de la spec
- Disparos detectados por el cargador del arma activa, sin gancho en `CWeapon::Fire` (funciona con todas las armas probadas).
- El rayo del disparo se resuelve en el cliente y solo marca; M3 lo pasa al servidor.
- La mira es la de Minecraft; no hay mira propia.
- Se usa la tarea de apuntar del propio juego (no una nueva), la cámara de GTA sigue a la de Minecraft y el puente gira a CJ: la spec preveía solo un punto objetivo.
- Los modelos de arma son `.glb` de una articulación (mano derecha, nodo 24).
- Se añadieron límites de movimiento por arma (la spec los dejaba para M4).

## Para M3 y M4
- El disparo sale de la mira de Minecraft y no del cañón de GTA; la diferencia entre ambos no se midió.
- `damage`, `range`, `pellets`, `spread` y `blockPower` de `weapons.json` son orientativos y todavía no se usan.
- El jugador sigue viendo la mira vanilla; el arma no se dibuja en primera persona (CJ tampoco).
- El sprint de Minecraft (5.6 bloques/s) sigue sintiéndose lento frente a la animación de sprint de GTA (12 m/s), como se anotó en M1c.
- Agacharse, trepar, nadar y las armas cuerpo a cuerpo siguen pendientes (M3 y M4).
- No se probaron armas fuera de las seis (el puente nunca da una sin modelo conocido, `WeaponModelId`).

## Revisión final independiente (opus, solo lectura) y su pasada de arreglos
Sin hallazgos críticos; ficheros dorados, llamadas nativas y convenciones, UDP en ambos sentidos, hilos, mixins, `ShotDedup`/`ShotCounter` y el decodificador DXT salieron correctos. Importantes y menores corregidos (cada uno con prueba en rojo primero y mutaciones muertas):
- **I1, GTA aún podía cerrarse al volver a armar con una retirada pendiente:** mientras un arma espera a ser retirada, el puente ya no alimenta la tarea, suelta los botones de apuntar/disparar del mando y repite cada fotograma la orden de terminar. Además, una retirada pendiente no sobrevive a un ped nulo (carga de partida).
- **I2, el mando apretaba apuntar/disparar con un vector de mira inutilizable** (cero, NaN, infinito o enorme), y entonces el juego apuntaba a su cámara y disparaba de verdad: ahora solo aprieta con un vector utilizable (`AimUsable`, compartido con `WeaponDriver`). Se quitó la constante `kGunPad`.
- **I3, quitar un arma del mod vaciaba todo el arsenal de CJ** (`ClearWeapons`), también las armas de la partida guardada: ahora el puente (`Arsenal`) recuerda qué dio y qué sustituyó en el mismo hueco y solo quita lo suyo (`CPed::ClearWeapon`) y devuelve la del jugador con su munición.
- **M1, un `.glb` de arma con más de una articulación** habría apagado el dibujo de todo CJ: el cargador lo rechaza como problema (una línea en el registro).

Aplazados (menores, sin efecto en lo que ve el jugador):
- Código sin uso de los experimentos: `gungame::Control`, `GunVtable`, `IsGunTask` y las constantes `kTaskNew`, `kUseGunCtor`, `kSetTaskSecondary`, `kUseGunSize` (la tarea ya no se crea, la crea el juego); `Describe` siempre imprime `ours=0`.
- `gungame::Describe` desreferencia la matriz del ped sin comprobar que sea nula (solo en el registro, cada 120 fotogramas apuntando).
- `GunCommand::Fire` en el plan se ignora al ejecutarlo (el juego decide disparar con el mando); las pruebas que lo comprueban fijan un comportamiento informativo.
- `ShotDedup`: un GTA reiniciado cuyo primer evento tenga exactamente el valor anterior pierde ese único evento.
- `CjClient.receiveLoop` solo captura `IOException`; hoy ninguna ruta lanza otra excepción.
- `BodyFacing` limita el giro al apuntar a 720°/s aunque su documentación diga «de inmediato».
- El conversor puede dar un `struct.error` en lugar de un `FormatError` ante un fichero de modelo corrupto con un recuento de marcos absurdo.
