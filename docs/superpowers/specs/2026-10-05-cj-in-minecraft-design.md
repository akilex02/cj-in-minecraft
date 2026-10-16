# CJ dentro de Minecraft (passthrough) — Diseño

Fecha: 2026-10-05

## 1. Objetivo

Jugar en Minecraft como CJ (GTA San Andreas): moverse por el mundo de Minecraft, trepar bloques, atacar mobs y romper bloques a tiros, con el modelo, las animaciones y las armas reales de GTA SA.

**Criterio de éxito:** se entra a un mundo de Minecraft como CJ; se puede caminar, correr, saltar, trepar una pared de hasta 2 bloques, disparar a un mob y hacerle daño, y romper un bloque a tiros, viendo el modelo de CJ con sus animaciones de GTA.

**Alcance y restricciones**
- Un solo jugador y sin conexión. No se toca ningún servicio online de ninguno de los juegos.
- Los assets de CJ salen **de la instalación de GTA SA del usuario** y se convierten al instalar. No se copian al repositorio ni se publican.
- Minecraft es el anfitrión: sus reglas de mundo, bloques y mobs mandan.
- Plataforma: Linux (CachyOS). GTA SA corre con Wine; Minecraft nativo con Prism Launcher.

## 2. Entorno verificado

- GTA SA: `GTA San Andreas/` en este proyecto. `gta_sa.exe` de 14,383,616 bytes (no es el exe *Compact* de 5,189,632 bytes que exige el ASI de `gta-reversed`). CLEO, modloader y varios `.asi` ya cargan (`cleo.log`).
- Datos disponibles: `models/player.img`, `models/gta3.img`, `anim/ped.ifp`.
- Minecraft: Prism Launcher con Fabric loader 0.19.x, Java 21. **Versión objetivo: 1.21.1 (Fabric)**, en una instancia nueva de Prism que no toca los mundos existentes.
- `gta-reversed-master/` se usa **solo como mapa de direcciones y estructuras** del exe 1.0 US (p. ej. `CPed`, `CPed::GetBonePosition 0x5E4280`, `CWeapon::Fire`). El plugin propio no sustituye funciones del juego.
- Suposición a validar en el hito M0: las direcciones de `gta-reversed` valen para este exe de 14 MB. Si no valen, se pedirá al usuario el exe Compact.

## 3. Arquitectura

Dos procesos en `127.0.0.1`:

| Pieza | Descripción |
|---|---|
| `gta-bridge` | Plugin `.asi` de 32 bits que corre dentro de GTA SA (Wine). Convierte a CJ en una marioneta: le pone posición cada frame y le simula el mando. Lee el esqueleto animado y los disparos. |
| `cj-fabric` | Mod cliente de Minecraft 1.21.1 (Fabric). Manda el estado del jugador, recibe la pose y dibuja a CJ. Resuelve golpes, bloques y mobs con las reglas de Minecraft. |
| `tools/convert_assets.py` | Conversor que se ejecuta una vez sobre la instalación del usuario. |

Estructura del repositorio del proyecto: `protocol/` (especificación y cabeceras compartidas), `gta-bridge/`, `cj-fabric/`, `tools/`.

### Autoridad
- **Posición y colisión:** Minecraft. En GTA, CJ no tiene gravedad ni colisión y se teletransporta cada frame a una esquina vacía del mapa, equivalente a la posición de Minecraft.
- **Animación y apuntado:** GTA. Recibe entrada de mando simulada (velocidad de carrera, salto, apuntar) y sus tareas eligen la animación real de CJ.
- **Impacto de disparos, daño y bloques:** Minecraft. El mundo de GTA no participa.

## 4. Protocolo

UDP en 127.0.0.1, binario, little-endian, con `magic` y `versión` en cada paquete.

**Minecraft → GTA, a 60 Hz** (desde el bucle de render, interpolado): número de secuencia; timestamp; posición, velocidad, yaw y pitch; banderas (en suelo, sprint, agachado, nadando, trepando y fase del trepado); arma asignada; intenciones (apuntar, disparar, golpear).

**GTA → Minecraft, a la frecuencia de frame de GTA:** último número de secuencia recibido; pose de unos 30 huesos (posición 3×float + cuaternión 4×float, 28 bytes por hueso, en espacio del modelo); animación actual; estado (apuntando, disparando, recargando); eventos puntuales (disparo con arma e instante, golpe cuerpo a cuerpo).

**Latido y vigilancia:** latido en ambos sentidos. Si Minecraft no recibe pose de GTA en 500 ms, vuelve al render vanilla del jugador y muestra un aviso en pantalla. Si GTA deja de recibir estado, CJ queda en reposo.

**Ejes y escala:** GTA es Z arriba y Minecraft Y arriba. La conversión se define en un único módulo compartido y se prueba en M1 con un marcador de ejes. Escala 1:1 (1 unidad de GTA = 1 bloque). La hitbox sigue siendo la de Minecraft.

## 5. Assets y renderizado de CJ

### Conversor (`tools/convert_assets.py`)
1. Lee el directorio de `player.img` y extrae `player.dff` y `player.txd`. Siempre se usa la versión vanilla, ignorando overrides de modloader.
2. Parsea el DFF (RenderWare): jerarquía de huesos y matrices en reposo, vértices, normales, UVs, triángulos, materiales y el plugin *Skin* (hasta 4 huesos por vértice, pesos, matrices inversas de enlace).
3. Decodifica el TXD (DXT1, DXT3, paleta de 8 bits) a PNG.
4. Escribe `cj.glb` (glTF binario con skin) y los PNG en la carpeta de configuración de Minecraft.
5. Para armas, convierte modelos estáticos de `gta3.img` con el mismo conversor.
6. No se convierten animaciones `.ifp`: GTA ya anima el esqueleto.
7. Genera una vista previa PNG del modelo para verificación.

### Renderizado en Minecraft
- Un mixin cancela el render vanilla del jugador local y dibuja a CJ dentro del pase de entidades de Minecraft, con un `RenderLayer` estándar de entidad. Recibe luz y sombras del mundo, y debe ser compatible con shaders (Iris).
- Skinning en CPU cada frame (hasta 4 influencias por vértice).
- Primera persona: el cuerpo se oculta. Tercera persona: se ve a CJ completo.
- Cargador mínimo propio de `.glb`, sin dependencias externas.
- Las armas convertidas se acoplan al hueso de la mano usando la pose recibida.

### Aspecto de CJ
El `player.dff` base puede salir con un aspecto por defecto o con texturas que no coincidan, porque en SA la ropa se arma dinámicamente con piezas de `player.img`. En M1 se acepta el resultado; la ropa correcta es parte de M4.

## 6. Armas, daño y mundo

**Principio:** GTA decide cuándo y cómo dispara CJ (cadencia, cargador, recarga, animación). Minecraft decide qué golpea el disparo.

### Armas
- Las armas son ítems de Minecraft del mod (pistola, escopeta, Uzi, AK-47, francotirador, RPG, bate, etc.). Al sostener uno, el puente le da esa arma a CJ. Con cualquier otro ítem, CJ va desarmado.
- Se obtienen con `/give` o desde la pestaña creativa.
- `weapons.json` define por arma: ítem, arma de GTA, daño, alcance, perdigones, dispersión, poder contra bloques, radio de explosión.
- Munición infinita; el cargador y la recarga los controla GTA.

### Disparo
1. Minecraft manda intención de disparar y dirección de la mira. GTA orienta a CJ y dispara.
2. Un hook en `CWeapon::Fire` manda un evento a Minecraft (arma, instante).
3. Minecraft lanza el rayo desde la mira del jugador (no desde el cañón de GTA), en el hilo del servidor integrado, y resuelve el impacto sobre entidad o bloque.

### Mobs
- Daño con `DamageSource` de jugador (drops, XP, crédito de kill, aggro), más empuje y el daño de `weapons.json`.
- Melee (puño, bate): el evento de golpe de GTA provoca un ataque de Minecraft con alcance de ~3 bloques.

### Romper bloques a tiros
- Vida de un bloque = dureza × constante configurable. Cada impacto resta el poder contra bloques del arma y se muestran las grietas vanilla (`setBlockBreakingInfo`).
- Si se deja de disparar, la vida se regenera gradualmente.
- Al llegar a cero, el bloque se rompe y suelta ítems. El bedrock y los bloques con dureza negativa son inmunes.
- Los bloques que requieren herramienta sueltan ítems igualmente (configurable).
- RPG y granadas usan una explosión de Minecraft y respetan `mobGriefing`.

### Trepar
- Al correr contra una pared de hasta 2 bloques (altura máxima configurable), con espacio libre encima, y pulsar saltar, Minecraft detecta el borde y mueve al jugador por una trayectoria de ~1 s con la entrada bloqueada.
- Durante el trepado avisa a GTA, que reproduce la animación de trepar de CJ, disparada a mano y sin su sistema de tareas.

### Salud y sonido
- Al recibir daño en Minecraft, CJ hace un gesto de dolor. Muerte y ragdoll: M4.
- El sonido de los disparos sale del propio GTA, no posicional. Se ajustará más adelante y no es prioritario.
- GTA debe seguir ejecutándose sin foco, con ventana pequeña y calidad baja. Se resuelve en M0.

## 7. Hitos

1. **M0 — El puente:** el jugador de Minecraft se mueve y CJ aparece en GTA en las coordenadas equivalentes. Valida que el exe de 14 MB y las direcciones de `gta-reversed` sirven. GTA sigue ejecutándose sin foco.
2. **M1 — CJ visible:** los huesos llegan a Minecraft y CJ se dibuja con su modelo convertido. Marcador de ejes verificado.
3. **M2 — Combate:** eventos de disparo y golpe, armas ligadas a ítems, orientación de apuntado.
4. **M3 — Impacto en el mundo:** daño a mobs y rotura de bloques.
5. **M4 — Pulido:** trepado, natación, ropa de CJ, reacciones a daño, muerte.

## 8. Pruebas y verificación

- **Cliente falso de Minecraft:** script Python que manda paquetes de estado, para probar el puente sin Minecraft.
- **GTA falso:** reproduce poses grabadas, para probar el mod sin abrir GTA.
- Cada hito se verifica con captura de pantalla y logs del juego real, con la skill de automatización de juegos.
- El puente puede volcar los huesos a JSON para contrastarlos con los que calcula el mod.
- Vista previa PNG del conversor comparada con una captura de CJ en GTA y otra en Minecraft.

## 9. Riesgos conocidos

- Las direcciones de `gta-reversed` pueden no coincidir con el exe de 14 MB (se detecta en M0).
- Apuntar: GTA orienta los brazos con su cámara; hay que darle una dirección de mira sin cámara (se investiga en M2).
- La animación de trepar se dispara a mano y puede quedar menos fiel al principio.
- El aspecto de CJ depende del sistema de ropa (ver sección 5).
- Skinning de CJ en el render de Minecraft: se hace en CPU y se mide el coste en M1.

## 10. Fuera de alcance

Multijugador, servicios online, vehículos de GTA, misiones y mundo de GTA, ropa personalizable completa, publicación del mod. Si se publica algún día, se hará solo código y conversores, sin assets, tras una revisión previa.
