# Cuerpo a cuerpo (puño y bate) — Resultados

Fecha de la prueba: 2026-10-07. Spec: `docs/superpowers/specs/2026-10-07-melee-design.md`; plan: `docs/superpowers/plans/2026-10-07-melee.md`; investigación de GTA: `docs/melee-spike.md`.

## Pruebas automáticas
- **Java** (`cd cj-fabric && ./gradlew test`): en verde. Nuevas: catálogo con `reach`, `coneHalfAngle` e `item` y su validación (límites exactos, cono sin alcance, alcance sin daño, mezcla con proyectil o explosión); los golpes para matar (un mob de 20 de vida cae en **6 golpes de puño** o **4 de bate**, con margen estricto, no en el borde de lo que da una suma de `float`); los golpes del bate para romper bloques; `MeleeTarget` (objetivo más cercano a la mira dentro del alcance y del cono, esfera enorme detrás, alcance 0, radio negativo); `ControlsMapper` con la firma de golpes; reglas de movimiento (agacharse al golpear).
- **Nativas del puente** (`bash tools/run_native_tests.sh`): 7 suites, con `WeaponModelId(5) == 336`, `WeaponSlot(5) == 1`, el mando que golpetea el botón de ataque, el rumbo de la mirada con `INTENT_MELEE`, `StrikeDetector` (un golpe por animación, cortes y cambios de movimiento), `ShotCounter::Credit` y el envío de golpes como `Shot` por el mismo contador que las armas de fuego.
- **Python** (`cd tools && python3 -B -m unittest discover -s tests`): el bate real convierte a una malla (91 vértices, 128 triángulos); los `.glb` de las otras ocho armas salen idénticos byte a byte.
- **Mutaciones:** cada módulo nuevo con mutantes muertos (catálogo, `MeleeTarget`, `ControlsMapper`, mando, rumbo, detector de golpes, contador y envío, reglas de movimiento). Quedan vivos, a propósito: el `continue` de `CjItems.register` para el puño (necesita los registros de Minecraft; comprobado en vivo que el puño no aparece en la pestaña de combate). Se guardó el arnés de mutaciones de Java en `tools/mutj.py`.

## Investigación (`docs/melee-spike.md`)
La tarea de pelea es el tipo 1016 en la ranura de ataque. Un botón mantenido da un solo puñetazo; pulsando y soltando encadena el combo de tres golpes. El instante de impacto sale de la tabla de combos del juego (`m_fHit`) y el tiempo de la animación; un golpe se cuenta al cruzarlo (o al cerrarse). Medido en vivo: 15 golpes de puño y 10 de bate en 8 s, y el usuario contó los mismos.

## Verificación en vivo (GTA SA real + Minecraft real, con el usuario)
- **Puño y bate:** pegan con la animación y los combos de GTA; un mob de 20 de vida cae en 6 golpes de puño y en 4 de bate; el bate se ve en la mano y se retira bien al cambiar de ítem. ✔
- **Un golpe por golpe de GTA** (sin golpe doble con el vanilla), **alcance y cono**, **minar con la mano vacía**, **otros ítems sin cambios**, **sin GTA el ataque vanilla funciona**, **cambio de ítem a media pelea**: según lo probado por el usuario, todo bien, sin comportamientos raros. ✔
- **Agacharse al golpear** (petición del usuario): mientras se golpea con el puño o el bate, el jugador va agachado y su velocidad coincide con la de GTA. También se agacha al minar con la mano vacía (ese clic cuenta como golpe); el usuario lo da por bueno. ✔ (la primera versión también agachaba montado, volando, nadando, en escaleras, en espectador y sin GTA; la revisión lo detectó y `SneakRules` lo limita a un jugador que anda con GTA respondiendo; esa segunda versión se comprueba en vivo en la pasada de arreglos)
- **El bate rompe bloques** (petición del usuario): en supervivencia con el clic izquierdo mantenido (tierra, hierba y arena 1 golpe; piedra, troncos, tablones y adoquines 3; mineral de hierro 5; la obsidiana es larga), con grietas, drops y regeneración como con los disparos; en creativo cualquier bloque se rompe de un golpe y sin drops; si hay un mob en la mira el golpe va al mob. ✔
- **Las otras ocho armas** conservan su comportamiento. ✔

## Valores finales
`fists`: `damage` 3.4, `reach` 3, `coneHalfAngle` 35, `knockback` 0.3, `blockPower` 0, `item` false, `moveWhileFiring` true, `canJump` true. `baseball_bat`: `damage` 6, `reach` 3.2, `coneHalfAngle` 45, `knockback` 0.8, `blockPower` 2.7, `item` true, `moveWhileFiring` true, `canJump` true; modelo 336, hueco 1.

## Problemas encontrados y cómo se resolvieron
1. **Un botón mantenido daba un solo golpe:** el mando golpetea el botón de ataque mientras dura `INTENT_MELEE`.
2. **`WeaponSlot(5) == -1` usado como ejemplo de «no soportada» en una prueba antigua:** el 5 ahora es el bate; el ejemplo pasó al 6.
3. **La herramienta de lanzamiento esperaba para siempre al lanzar los juegos con una tubería:** `tools/start_games.sh` ya no deja a los juegos heredar la entrada estándar.

## Decisiones, desviaciones de la spec y límites
- **Sin protocolo nuevo:** los golpes viajan como `Shot` con `weapon` 0 o 5 (la spec dejaba abierto un mensaje `Hit`).
- **Romper bloques con el bate** estaba fuera del hito y entró a petición del usuario. El puño no rompe bloques; en modo aventura el bate no rompe nada.
- **Agacharse al golpear** también alcanza al minado con la mano vacía; se puede limitar a cuando no se apunta a un bloque si molesta.
- Sin golpes críticos, bloqueos ni esquivas, ni el resto de armas cuerpo a cuerpo (cuchillo, katana, motosierra…).

## Revisión final independiente (opus, solo lectura) y su pasada de arreglos
Sin hallazgos críticos; el contador de golpes (monótono, sin saltos al reengancharse), las lecturas de memoria de `WinIo::MeleeStrikes`, `StrikeDetector`, `MeleeTarget`, el empuje de `hitEntity`, `item: false` del puño y la ausencia de efecto en las otras ocho armas salieron correctos. Corregido (cada arreglo con prueba en rojo primero y mutaciones muertas donde hay lógica probable):
- **Golpes fantasma con el arma equivocada:** un golpe cerrado a mitad de animación al cambiar de ítem salía con el arma que CJ llevaba entonces (una bala o un cohete en Minecraft). El puente solo reporta golpes con las manos vacías o el bate (`IsMeleeWeapon`).
- **El mando golpeteaba con una pistola de la partida guardada:** si CJ recuperaba un arma suya al quedarse la mano vacía, GTA la disparaba. El botón de ataque no se golpetea si CJ no lleva las manos vacías o el bate.
- **Agachado forzado sin mirar el estado del jugador:** desmontaba, bajaba volando, hundía nadando, bajaba escaleras y en espectador, y se aplicaba sin GTA. Ahora `SneakRules` solo lo fuerza a un jugador que anda, con GTA respondiendo.
- **El ataque vanilla anulado a cualquier entidad con la mano vacía** (barcas, vagonetas, marcos, y en espectador): ahora solo se anula contra seres vivos y fuera del modo espectador.
- **Golpes a través de paredes finas** (una puerta o un cristal pegado a un mob alto): la línea de visión se compara con la entrada del rayo en la caja del objetivo y no con el centro menos un radio.
Los dos últimos arreglos tocan código de Minecraft sin prueba unitaria posible; su verificación es en vivo.

Aplazados (menores):
- En creativo el bate rompe bloques inmunes (lecho de roca, barrera) y de operador sin comprobar el permiso, ignora la protección de spawn, el borde del mundo y los eventos de rotura de Fabric, y no borra la vida del bloque del rastreador (un bloque dañado antes en supervivencia deja grietas que se regeneran).
- Montado, el golpe puede ir a la propia montura o a otros pasajeros.
- El margen de 6 golpes de puño no tiene en cuenta la armadura del mob (un zombi con casco de cuero necesita 7).
- Los soportes de armadura cuentan como seres vivos atacables.
- El catálogo acepta `gta: 0` en un arma con ítem, `item: false` en un arma de fuego, el id `"fists"` está fijo en `CjItems.activeWeapon` y `range` se exige aunque el cuerpo a cuerpo no lo use.
- Si CJ recupera de su partida un arma de fuego al quedarse la mano vacía, el puño no golpea (el mando no la dispara, pero tampoco pega); seleccionar «desarmado» exigiría llamar a una función del juego que no se ha comprobado.
- La spec §5 pedía terminar la tarea de pelea antes de quitar el bate; el puente no espera a que termine (la tarea acaba sola y el golpe que cierra se descarta si ya hay otra arma).
- Estilo: un comentario huérfano en `weapon_driver.h`, `vtable[4]` sin constante, `case 5` entre el 35 y el 38 en `WeaponModelId`, `weaponOf` llamado dos veces en `CameraMixin`, `StrikeDetector::Reset` solo usado en pruebas, y `tools/mutj.py` cuenta como error de compilación una salida de prueba que contenga «error:».
