# Trepar — Investigación de animaciones en GTA

Fecha: 2026-10-07. Mediciones en GTA SA 1.0 US real, con Minecraft abierto (el CJ dibujado en F5 muestra el resultado) y un experimento temporal del puente (ya retirado) que ejecutaba `BlendAnimation` con el id que se le pedía en un fichero de órdenes: solo animaciones, sin tareas del juego, sin tocar la posición, la vida ni el estado del ped.

## La secuencia de trepar existe y se ve bien
Grupo 0, retardo de mezcla 8. Todas con banderas 0x4015 (se mantienen en el último fotograma, 0x4014 al terminar, y se desvanecen al quitarlas):

| Orden | id | Nombre | Duración | Lo que ve el usuario |
|---|---|---|---|---|
| 1 | 128 | `CLIMB_JUMP` | 0,57 s | salta y se queda colgado del borde |
| 2 | 130 | `CLIMB_PULL` | 0,87 s | continuación: trepa la pared |
| 3 | 131 | `CLIMB_STAND` | 0,80 s | se levanta y queda de pie |

- **Encadenado:** pedir la siguiente un poco antes de que acabe la anterior, con la anterior quitándose a la vez (ritmo −4 y bandera 0x4): 128 en 0 s, 130 en 0,5 s y 131 en 1,3 s. Enlazan sin saltos y el usuario ve «la animación de trepar un muro». Duración total ≈ 2,1 s.
- **Una sola secuencia** para muros de 2 y de 3 bloques: la animación es la misma; el arco de Minecraft se ajusta (subida hasta que empieza 131, avance durante 131). Se afina en vivo; si no se ve bien con 3 bloques, se probará a cambiar la velocidad de la asociación (+0x24) o se añadirá `FLAG_WALL_CLIMB_HIGH`.
- Sin probar y sin necesidad: 129 `CLIMB_IDLE`, 132 `CLIMB_STAND_FINISH`, 133 `CLIMB_JUMP_B`, 134 `CLIMB_JUMP2FALL`.

## Constantes elegidas (primera versión; las definitivas están en `docs/climb-results.md`)
`RISE_MS` 1300 (hasta que empieza 131), `FORWARD_MS` 800 (lo que dura 131), `FORWARD_BLOCKS` 0,6: esas constantes se sustituyeron al ver que el esqueleto trae su propio movimiento (ver abajo). El puente pide 128 al subir la bandera, 130 a los 500 ms y 131 a los 1300 ms, y limpia a los 2100 ms o cuando la bandera baja.

## Seguridad
Tras la prueba y el desvanecimiento, el usuario comprobó que CJ está en su postura normal en Minecraft y que el CJ de GTA sigue en su posición y con su estado. Solo se usaron asociaciones de animación, que se quitan solas o se quitan al soltar.

## El salto de GTA pelea contra la animación de trepar (medido con el CJ real)
Con un salto justo antes del trepado (el `INTENT_JUMP` al pulsar espacio, o el agarre en el aire), la tarea de salto propia de GTA mezcla sus animaciones encima de las nuestras: `116 JUMP_LAUNCH` desvanece a `128` (que se queda en peso 0,04), luego entra `118 JUMP_GLIDE` y `130` solo llega a peso 0,34; hacia 0,6 s después GTA vuelve a imponer `118` y `130` se desvanece y se borra; entra `122 FALL_LAND` (aterrizaje) y la nuestra desaparece del todo hasta que llega `131`. Es el corte que ve el usuario. Solución: mientras la bandera de trepar está puesta, cada fotograma el puente sube de nuevo la asociación mantenida (ritmo +8 y sin autoeliminación), desvanece las asociaciones de GTA con ids 116 a 123 como lo hace el propio juego al abortarlas (ritmo negativo y autoeliminación), y vuelve a pedir la animación si la nuestra se borró. Las asociaciones se recorren con `RpAnimBlendClumpGetFirstAssociation` (0x4D15E0) y el enlace `*(asoc + 4) - 4`.
