# M1 — CJ visible en Minecraft — Diseño

Fecha: 2026-10-05. Refina las secciones 3, 4, 5 y 7 (M1) de `2026-10-05-cj-in-minecraft-design.md` a la vista de lo aprendido en M0 (`docs/m0-results.md`) y de una investigación en `gta-reversed` y en el exe del usuario.

## 1. Objetivo y criterio de éxito

Al jugar en Minecraft se ve a CJ, con su modelo de GTA SA, haciendo la animación real de GTA que corresponde a lo que hace el jugador: parado, caminando, corriendo, sprintando y saltando. La posición, la dirección y la altura salen de Minecraft; solo los huesos salen de GTA.

**M1 está terminado cuando** el usuario, en Minecraft real y en tercera persona, ve a CJ moviéndose por el mundo con sus animaciones, orientado hacia donde avanza, y el marcador de ejes confirma que no está girado ni espejado.

## 2. Cambio respecto al spec original (para tu revisión)

El spec decía que CJ iba sin gravedad ni colisión y se teletransportaba cada frame a la posición equivalente de Minecraft. **Esto se abandona para M1** por tres motivos comprobados:

1. Con la colisión desactivada GTA no marca a CJ como "pisando suelo" (`bIsStanding`), así que sus tareas lo ponen en animación de caída en vez de caminar o correr. Es el riesgo que el revisor de M0 señaló como "no evaluado".
2. La posición de CJ en GTA ya no sirve de nada: Minecraft dibuja el cuerpo, solo hacen falta los huesos.
3. Un CJ que pisa suelo real hace gratis lo que queremos: idle, caminar, correr, sprint y salto reales.

**Nuevo reparto:** CJ **se queda de pie en un punto fijo del suelo de GTA** (el "escenario") con colisión y gravedad normales. Minecraft solo le dice *cómo moverse* (su velocidad y sus acciones) y GTA reproduce la animación; cada frame se devuelve a CJ al escenario para que no se aleje caminando. Los huesos se exportan relativos al propio CJ, sin su posición ni su giro, y Minecraft les pone la posición y el giro del jugador.

**Escenario:** el sitio donde esté CJ cuando se engancha el puente (como en M0). La opción de un escenario en el mar, que el usuario había elegido, **no es viable** por el motivo 1: el mar no es suelo. A cambio se pone la densidad de peatones y de tráfico a 0 mientras dura el enganche (`CPopulation::PedDensityMultiplier` `0x8D2530` y `CCarCtrl::CarDensityMultiplier` `0x8A5B20`), con lo que no aparecen peatones ni coches nuevos. Los que ya estén en pantalla se van quitando solos. Si la zona resulta peligrosa, el usuario carga la partida en un sitio tranquilo.

## 3. Tres planes, en este orden

| Plan | Qué entrega | Cómo se verifica sin las otras piezas |
|---|---|---|
| **M1a — pose** | El plugin de GTA mueve a CJ con una animación real según la velocidad de Minecraft y manda sus huesos (paquete `Pose`) | Cliente falso que imprime los huesos; el usuario ve a CJ animarse en la ventana de GTA |
| **M1b — conversor** | `tools/convert_assets.py`: `player.img` → `cj.glb` (malla, pesos, esqueleto) + texturas PNG | Vista previa PNG y un visor glTF, sin abrir ningún juego |
| **M1c — render** | El mod de Minecraft dibuja a CJ con skinning en CPU | "GTA falso" que reproduce poses grabadas |

M1a va primero porque es donde está la incertidumbre (cómo responde la animación a un mando simulado). M1b es independiente y puede hacerse en paralelo, y M1c necesita los dos.

## 4. M1a — pose

### 4.1 Hooks
- El hook ya existente de `CGame::Process` (`0x53E981`) sigue ejecutando `Tick` después de `Process`. Se generaliza a un componente reutilizable `CallHook` (sitio, reemplazo, reenganche con cadena y guarda de recursión, como ya se probó en M0).
- Se añade un segundo `CallHook` sobre la **llamada a `CPad::UpdatePads` dentro de `CGame::Process` (`0x53BEE6` → `0x541DD0`)**, verificada en el exe. `UpdatePads` rellena el estado del mando antes de que el ped procese sus controles; nuestro reemplazo llama al original y después sobrescribe el estado. Escribir el mando desde `Tick` no valdría: llegaría tarde y el siguiente `UpdatePads` lo borraría.

### 4.2 Mando simulado
Sobre `CPad::Pads[0].NewState` (`Pads` en `0xB73458`, `NewState` es el primer miembro), mientras haya enganche:
- avance: `LeftStickY` en el eje de avance, con magnitud según la velocidad horizontal de Minecraft; parado = 0;
- agachado → `m_bPedWalk` (CJ camina en vez de correr);
- sprint → `ButtonCross`; salto → pulso en `ButtonSquare` cuando Minecraft sale del suelo.
Si no llega estado en 1 s, se deja de sobrescribir y el usuario recupera el mando. Los offsets exactos de `CControllerState` se leen de `gta-reversed` y se comprueban contra el exe en la primera tarea del plan.

### 4.3 Escenario y cámara
- Al engancharse se guarda la posición X, Y del escenario. Tras `Process`, cada frame se vuelve a escribir X, Y (la Z queda libre para que la gravedad lo mantenga en el suelo). Nunca se llama a `CPed::Teleport`, porque cancela las tareas y con ellas la animación.
- Se ponen a 0 las dos densidades y se restauran al soltar.
- La cámara de GTA sigue a CJ: las matrices de los huesos solo se actualizan cuando se dibuja al ped.

### 4.4 Exportación de huesos
- Cada frame, tras `Process`: `clump = *(RpClump**)(ped + 0x18)`; `hier = GetAnimHierarchyFromSkinClump(clump)` (`0x734A40`); `numNodes` en `hier+4`, `pMatrixArray` en `hier+8` (matrices de 64 bytes, **en espacio de mundo**), `pNodeInfo` en `hier+0x10` (16 bytes por nodo, `nodeID` primero).
- Cada hueso se convierte a espacio del ped: `R_rel = R_pedᵀ · R_hueso`, `p_rel = R_pedᵀ · (p_hueso − p_ped)`. Así desaparecen la posición y el giro de CJ en GTA.
- La rotación pasa a cuaternión. Un paquete `Pose` lleva todos los nodos.

### 4.5 Protocolo (se amplía; State y Echo no cambian de formato)
**Pose (tipo 3, GTA → Minecraft, ~1 KB):** cabecera de 8 bytes; `u32 ackSeq`; `u32 gtaTimeMs`; `u32 flags` (bit 0: hay animación activa); `u16 boneCount`; `u16 reservado`; y `boneCount` registros de 32 bytes: `i32 nodeId`, `f32 pos[3]`, `f32 quat[4]` (x,y,z,w). Máximo 48 huesos.
- **Echo** sigue existiendo para diagnóstico, pero su posición ahora es la posición real de CJ en GTA (que es el escenario). El oráculo de M0 que comparaba el recorrido deja de aplicar y se sustituye por comprobaciones de pose.

### 4.6 Verificación de M1a
- Pruebas nativas (con memoria de ped y de mando falsas) de: conversión de huesos a espacio del ped, cuaternión, formato del paquete `Pose` contra un binario golden (Python y C++), mapeo de velocidad → mando, restauración al soltar.
- En el juego real, con el cliente falso: la velocidad pedida → `LeftStickY`; los huesos cambian con el tiempo al caminar (por ejemplo, el hueso del pie oscila) y se quedan casi quietos en idle; con el usuario mirando la ventana de GTA.

## 5. M1b — conversor (`tools/convert_assets.py`)

Se ejecuta sobre la instalación del usuario y escribe en la carpeta de configuración de Minecraft; nada de esto entra al repositorio. Los detalles de formato están en `docs/superpowers/plans/2026-10-05-m1b-converter.md` (sección "Lo que se descubrió al abrir los archivos reales"), medidos sobre los archivos reales.

1. **Esqueleto:** `player.dff` está en `models/gta3.img` (no en `player.img`) y es solo el esqueleto: 32 nodos con sus `nodeID` y las 32 matrices inversas de enlace en el plugin *Skin* de una geometría de relleno.
2. **Cuerpo:** el juego lo ensambla con cinco piezas de `models/player.img`: `torso.dff`, `head.dff`, `hands.dff`, `legs.dff` y `feet.dff`. Cada una trae malla, normales, UVs y skinning de hasta 3 huesos sobre su propia jerarquía de 37 nodos. El conversor remapea los huesos de cada pieza al esqueleto por `nodeID`.
3. **Texturas del CJ base:** `player_torso.txd:torso` (el atlas del torso incluye las manos), `player_face.txd:face`, `player_legs.txd:legs` y `player_feet.txd:foot`. Son D3D9 de 32 bits sin compresión.
4. **Salida:** `cj.glb` (glTF binario con `skin`, en el espacio del modelo de GTA: X adelante, Y izquierda, Z arriba; los `joints` en el orden del esqueleto de GTA, con su `nodeID` en `extras`) y una vista previa PNG.
5. No se convierten animaciones `.ifp`: GTA ya anima el esqueleto.
6. La ropa de verdad (el sistema de ropa completo, las texturas compuestas y la gordura/músculo) queda para M4.

## 6. M1c — render en Minecraft

- Mixin sobre el render de entidades vivas que, para el jugador local, cancela el dibujo vanilla y dibuja a CJ en el mismo pase, con un `RenderLayer` estándar de entidad, la luz del mundo y la textura de CJ (compatible con shaders).
- Cargador mínimo propio de `.glb`; skinning en CPU cada frame: `matrizHueso = poseHueso · inversaDeEnlace`, 4 influencias por vértice.
- El `Pose` más reciente se interpola entre dos recibidos para ir suave a la tasa de render de Minecraft.
- **Ejes:** el espacio del modelo de CJ (el de `cj.glb` y el de los huesos del paquete `Pose`) es X adelante, Y izquierda, Z arriba. Un jugador de Minecraft mirando al sur tiene adelante = +Z, izquierda = +X y arriba = +Y, así que la conversión es `(x, y, z)GTA → (y, z, x)MC` (una permutación cíclica, determinante +1: gira, no espeja). Se verifica con un marcador de ejes (tres flechas) antes de mirar el modelo.
- **Orientación:** en movimiento el cuerpo se gira hacia la dirección en que avanza el jugador (estilo GTA); tras unos 0.3 s quieto gira hacia donde mira el jugador, para que la cámara (que sigue la mirada) lo vea de espaldas. Primera persona: se oculta el cuerpo.
- Si el `Pose` falta más de 500 ms, se vuelve al render vanilla y se avisa en pantalla (restricción del spec, sección 4).

## 7. Riesgos

- **El mando simulado puede no producir la animación esperada** (por ejemplo, la cámara condiciona hacia dónde "avanza" el stick). Primera tarea de M1a: un experimento que lo comprueba antes de construir nada encima.
- **Las dos llamadas enganchadas (`0x53E981`, `0x53BEE6`) pueden ser disputadas** por otros plugins del usuario, como pasó en M0. Se reutiliza el reenganche con cadena.
- **CJ en medio de una calle:** con densidad 0 no aparecen nuevos peatones ni coches, pero los que ya estén pueden golpearlo o hacerlo reaccionar. Mitigación inicial: cargar la partida en un sitio tranquilo.
- **Los huesos solo se actualizan al dibujar al ped:** si la cámara de GTA no lo ve, la pose se queda vieja. Mitigación: la cámara de GTA lo sigue; se detecta pose estancada y se registra.
- **El modelo base de `player.dff` puede salir sin ropa o con texturas distintas** a las del CJ de la partida. Aceptado en M1.
- **El `BinMesh` y el plugin *Skin* de los DFF de SA** tienen detalles de versión (tiras de triángulos, matrices con relleno). Mitigación: el conversor se verifica con la vista previa antes de tocar Minecraft.

## 8. Fuera de alcance de M1

Armas, disparos, mobs y bloques (M2 y M3); trepar, nadar y reacciones a daño (M4); ropa; primera persona con brazos; mover el escenario a otro sitio del mapa.
