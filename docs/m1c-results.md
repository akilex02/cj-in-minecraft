# M1c — Resultados

Fecha de la prueba: 2026-10-05

## Pruebas automáticas
- **Java** (`cd cj-fabric && ./gradlew test`): `PoseDecodeTest` (4), `GlbModelTest` (7), `PoseBufferTest` (10), `Mat4Test` (5), `AxesTest` (3), `SkinnerTest` (8), `BodyFacingTest` (11), más las de M0 y M1a (`ProtocolTest` 3, `SendSchedulerTest` 5). Todas en verde.
- **Python** (`cd tools && python3 -B -m unittest discover -s tests`): 70 pruebas en verde (las de M1b más las 7 de `fake_gta` y las 2 de `mini.glb`).
- **C++** (6 suites del puente de GTA): en verde, con las expectativas de `test_pose` y `test_bridge_core` actualizadas a ejes de CJ (ver más abajo).
- Cada módulo se comprobó con mutaciones que reintroducen un fallo plausible (signo del peso, orden de multiplicación de matrices, signo de la orientación, desplazamiento al suelo, traslación de articulación, camino corto del cuaternión, caducidad, rangos de articulaciones e índices, retardo de giro en reposo, signo y eje de la conversión a ejes de modelo); cada una hace fallar su prueba.

## Verificación en Minecraft con el puente falso (`tools/fake_gta.py`)
- **Sin poses:** se ve el jugador normal de Minecraft y la barra de acción dice `CJ bridge: GTA not responding`. ✔
- **Pose de reposo:** CJ aparece en lugar del jugador, con la cara delante, los pies sobre el suelo, las texturas correctas (piel, ropa interior, pies descalzos con la textura bien colocada) y sin triángulos que falten ni caras del revés. ✔ El registro confirma `drawing CJ in place of the local player` (el mixin funciona).
- **Marcador de ejes (`/cj axes`):** la flecha roja (adelante) y la verde (izquierda) salían bien; la azul (arriba) quedaba oculta dentro del cuerpo, porque medía 0.7 m desde la cintura. Se alargó a 1.3 m y en la segunda comprobación se ve por encima de la cabeza. ✔
- **Giro del cuerpo entero (`--mode spin`):** gira suave y continuo, el cuerpo entero, alrededor de su eje vertical y con los pies en el suelo. ✔
- **Subida y bajada (`--mode bob`):** se mueve suave y fluido. ✔
- **Corte de poses:** al parar el puente falso, vuelve el jugador normal en medio segundo y la barra dice otra vez `GTA not responding`. ✔
- **Primera persona:** no se ve el cuerpo de CJ; solo la mano del jugador de Minecraft. ✔

## Verificación con GTA real
- Con GTA cargado, el mod dibuja a CJ y la barra de acción muestra las coordenadas reales de GTA (`GTA (x, y, z) ack=N`). El registro de Minecraft no tiene errores ni excepciones del mod.
- Quieto, caminar, correr, sprint y salto: CJ hace la animación que corresponde. ✔ Se ve fluido y las zancadas encajan con lo que avanza.
- **Agacharse (`shift`):** CJ empieza a caminar. Es la limitación conocida de M1a (el sigilo se traduce a `m_bPedWalk`; el agachado real de GTA es otro botón, `ShockButtonL`, que actúa como interruptor). Queda para M4.
- **Velocidad del sprint:** caminar y trotar encajan bien con el jugador de Minecraft, pero al correr (sprint) la animación se siente algo rápida para lo que avanza el jugador. Es el desajuste medido en M1a (la animación de sprint de GTA corresponde a unos 12 m/s y el sprint de Minecraft va a unos 5.6 bloques/s). Pendiente de ajustar (por ejemplo, escalando la velocidad de reproducción o la velocidad que se le pide a GTA); el usuario lo dejó para más adelante.

## Problemas encontrados y cómo se resolvieron
- **La pose de GTA salía girada ~90° sobre el eje vertical** (CJ aparecía de perfil mirando a la izquierda, también quieto, y al avanzar el cuerpo miraba hacia la izquierda de la marcha). El puente de GTA medía los huesos en los ejes del ped (X derecha, Y adelante) y el modelo está en ejes de CJ (X adelante, Y izquierda). El puente falso no lo delataba porque sus poses salen de la propia pose de reposo del modelo.
  - **Comprobación con datos reales** (una captura de la pose en vivo contra la pose de reposo): rotación de reposo a vivo de **89.6°** (dedos de los pies) y **87.5°** (manos), y **90.2°** en la orientación de la pelvis.
  - **Arreglo en el puente** (`BuildPose` en `pose.h`): cada vector del mundo `v` se convierte a `(v·adelante, −(v·derecha), v·arriba)`. Después del arreglo: **−0.4°** y **−0.9°** (los pies y las manos) y 4.1° en la pelvis (la propia animación de reposo, con los brazos caídos). En Minecraft el usuario confirma que CJ mira hacia donde avanza, de espaldas a la cámara al quedarse quieto, y que la animación se ve bien.
  - La documentación de M1b ya decía que la `Pose` estaba en ejes de modelo; con este arreglo es verdad. `PROTOCOL.md` explica ahora quién hace la conversión.
- **Orientación en reposo:** al quedarse quieto CJ conservaba la última dirección de movimiento y al entrar en F5 se veía de perfil. A elección del usuario, tras 0.3 s quieto el cuerpo gira hacia donde mira el jugador (y en movimiento sigue mirando hacia donde avanza). Es lo que hará falta para apuntar en M2.
- **Flecha azul del marcador de ejes oculta en el cuerpo:** alargada a 1.3 m.
- **Comprobación de rangos en el lector del GLB sin prueba propia:** la detectó una mutación; se añadieron pruebas que corrompen solo una articulación y solo un índice.
- **Error del propio plan:** una prueba de `PoseBuffer` que nunca habría pasado (instante de muestreo equivocado), un auxiliar de reflexión pegado aparte y una mutación de `fake_gta` que no detectaba nada; corregidos en el plan antes de ejecutar. Además, el `pkill -f` del plan se mataba a sí mismo al compartir línea de comandos con `tools/fake_gta.py`; se hizo por PID.
- **La barra de acción** se basa en el `Echo`, no en las poses: con el puente falso (que solo manda `Pose`) dice `GTA not responding` aunque CJ se dibuje. Con GTA real llegan las dos cosas y es correcta.

## Decisiones y límites para M2 y M4
- El cuerpo mira hacia donde avanza al moverse, y hacia donde mira el jugador al estar quieto (necesario para apuntar).
- Falta: agacharse de verdad, el ajuste de la animación del sprint a la velocidad de Minecraft, la ropa y las armas en la mano (el mixin cancela el dibujo del jugador entero, así que no se dibuja nada de lo que el jugador sostiene).
- El mod usa `RenderLayer.getEntityCutoutNoCull` con luz de entidad; la compatibilidad con shaders (Iris) no se ha probado.
- Interpolación de poses: se dibuja con 50 ms de retraso (antes 20 ms) para interpolar entre las dos poses más recientes aunque GTA vaya a 30 fps (una pose cada ~33 ms); la caducidad es de 500 ms.

## Revisión final independiente (opus, solo lectura)
Sin defectos críticos: matrices, esqueleto, permutación de ejes, signo del giro, nlerp y la conversión del puente salieron correctos. Hilos: `push` y `sample` están sincronizados.

Corregido en una pasada (con prueba en rojo antes, salvo lo marcado):
- **Retraso de interpolación demasiado corto** (20 ms con GTA a 30 fps dejaba ~40 % del tiempo la pose congelada): ahora 50 ms; prueba con poses cada 33 ms.
- **Poses hostiles o rotas**: los cuaterniones se normalizan al recibirlos (cero → identidad) y las posiciones se limitan a ±100 m; dos pruebas.
- **Dos articulaciones con el mismo `nodeId`** en el `.glb`: ahora se rechaza al leerlo; prueba.
- **Vista previa del inventario y poses especiales** (dormir, montar, nadar, invisible): el mixin ya no dibuja a CJ ahí y sale el jugador normal (sin prueba automática; compila, falta mirarlo en el juego).
- **Aviso en pantalla**: si GTA responde con `Echo` pero no manda poses, la barra dice `GTA answers but sends no pose` (sin prueba automática).

Aplazado (menores, bajo riesgo): vértices parciales si falla a mitad de emisión, pesos sin renormalizar, articulación ausente en un paquete (usa la pose de reposo), cerrar el socket y las texturas al salir, asignación de un `JointFrame` por fotograma, y una prueba del giro en `render` (solo comprobado a ojo).
Pendiente de medir: la tasa real de poses de GTA (si es 30 fps o más).
