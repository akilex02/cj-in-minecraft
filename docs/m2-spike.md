# M2 — Puerta con GTA real (prueba del apuntado y el disparo)

Fecha: 2026-10-05. Herramientas: `tools/fake_mc.py` (cliente falso de Minecraft) contra GTA SA real bajo Wine, con el puente de M2. Resultado: **puerta superada**, pero no con la variante que preveía la spec.

## Qué se probó y qué se vio

| Variante | Resultado |
|---|---|
| Base: arma en la mano sin apuntar | El puente da la Colt 45 (`CJ holds weapon 22`). Mano derecha en reposo: (+0.02, −0.25, −0.07) en el espacio del modelo. |
| **Mando que aprieta «apuntar» + nada más** | La animación de apuntar se reproduce (mano a +0.54 / −0.16 / +0.51: sube 0.58 m y avanza 0.5 m), pero **el brazo queda fijo hacia delante** (visto en pantalla): la tarea de apuntar que crea el propio juego lleva `m_TargetPos = (0,0,0)` y cae en la rama «apuntar al frente». |
| Solo tarea nuestra (sin mando) | La tarea desaparecía cada frame: el flujo del jugador (`PlayerPassiveControlGun`) la cierra al no ver el mando apretado. Además el primer intento falló porque el offset de `CPed::m_pIntelligence` era 0x440 y es **0x47C** (hallado escaneando un ped vivo). |
| Mando + escribir el frente de la cámara | El frente se mantiene entre frames (se leyó igual al siguiente), pero sin ratón libre el juego no usa la cámara para apuntar: seguía fijo. |
| **Mando + escribir `m_TargetPos` en la tarea del juego y poner a cero `m_IsArmIKInUse` / `m_IsLookIKInUse` cada frame** | **Funciona.** `GetTaskType` devuelve 1017 (`TASK_SIMPLE_USE_GUN`); la mano recorre (−0.36…+0.27, −0.74…−0.49, 0.14…0.82) siguiendo el barrido de mira y **en pantalla CJ gira y mueve todo el tronco siguiendo la mira mientras dispara**. |

La causa: en `CTaskSimpleUseGun::AimGun` la IK del brazo solo apunta a `m_TargetPos` mientras el brazo no tiene IK en uso; con los dos indicadores a cero cada frame vuelve a apuntar al objetivo.

## Disparo

- **Colt 45:** ~2.8 tiros/s, 17 por cargador, pausa de recarga de ~1.7 s, contador `shot_seq` creciente (24 en 10 s).
- **AK-47:** ~8 tiros/s, ráfagas continuas (118 en 20 s con barrido).
- **Escopeta:** 1 tiro cada ~1.12 s (el bombeo).
- La detección por el cargador (`AmmoInClip` del arma activa) funciona con las tres: no hizo falta enganchar `CWeapon::Fire`.

## Problemas encontrados

1. **Offset de `m_pIntelligence`:** 0x47C, no 0x440. Se detectó porque el puente comprueba que `CPedIntelligence::m_pPed` apunta de vuelta al ped y se negó a tocar nada.
2. **GTA se cae al dar un arma cuyo modelo no está cargado** (fallo de página en `0x4D464E` dentro de `CPed::AddWeaponModel`, que crea una instancia del modelo). La Colt iba bien porque su modelo ya estaba en memoria. Solución: pedir el modelo con `CStreaming::RequestModel(id, GAME_REQUIRED)` + `LoadAllRequestedModels(false)` antes de dar el arma, y no dar nunca un arma sin modelo conocido (`WeaponModelId`, con prueba nativa).
3. Una vez, la Colt se quitó y se volvió a dar a los 3.9 s de un pase (`CJ holds weapon 0` → `22`). No se repitió en el resto de las pruebas; se vigila.

## Decisión

Variante final: **`kGunPad = true`** (el mando simulado aprieta apuntar y disparar para que el juego cree y mantenga su propia tarea), y el puente **reapunta la tarea del juego** cada frame. El código que crea una tarea propia (`gungame::Control`) queda sin uso en este modo; se conserva por si hiciera falta. La escritura del frente de la cámara no es necesaria para el apuntado con arma de brazo, pero se deja de momento (inocua).

## Apuntar en todas las direcciones (hallado después, con GTA y Minecraft reales)

Con el apuntado funcionando, el usuario observó que en algunos rumbos CJ giraba todo el brazo o la retícula de GTA desaparecía. Resolución, paso a paso (todo medido con un registro por fotograma del puente y el cliente falso con un giro lento de 360°):

1. **Primer fallo, mío:** la pose va en el espacio del propio CJ, relativa a su rumbo en GTA, y yo dibujaba el cuerpo con el yaw de la mira. Se arregló llevando el **rumbo del ped** en la pose (`Pose.heading`, u16) y dibujando con él mientras se apunta.
2. **GTA no gira a CJ por sí mismo** al apuntar (con el rumbo sin escribir, el de CJ solo recorre 60°–180°). Se hizo que el puente lo gire (`SetPedHeading`: matriz del ped y, comprobados en vivo, `m_fCurrentRotation`/`m_fAimingRotation` en `0x558`/`0x55C`) **después** de leer la pose de cada fotograma.
3. **La causa de que `skipAim` saltara:** el juego (`CTaskSimpleUseGun`, que pone `m_SkipAim` a 1 y quita la retícula) solo acepta objetivos dentro de unos +115° / −130° del rumbo de **su cámara**, que se queda fija en el rumbo inicial de CJ. En la medición, `skipAim` era 1 exactamente en un cono de ±60° justo detrás de ese rumbo (centro = rumbo inicial + 180°), en el callejón y en la plaza, e igual con objetivos a 2, 5 o 50 m (no era línea de visión). No dependía del ángulo entre CJ y el objetivo (siempre ≈ 0°), ni de recrear la tarea, ni de la matriz final de la cámara (se giró y no cambió nada).
4. **La corrección** (idea del usuario: que la cámara de GTA siga a la de Minecraft): escribir el vector frontal de la cámara activa (`m_vecFront`) cada fotograma hacia el objetivo. Mi offset inicial (`0x190` dentro de `CCam`) estaba mal contado en `0x40`; el correcto es **`0x150`** (hallado escaneando una cámara viva: un vector unitario con el rumbo de CJ en `TheCamera+0x304`). Con eso, **0 de 1499 fotogramas con `skipAim`** en un giro completo de 360° (antes, entre 30 % y 70 %).

Código final del apuntado: `PadDriver` (aprieta apuntar), `WeaponDriver` (decide, también el rumbo), `gungame::AimCamera` + `RetargetGameTask` (camara y objetivo) y `SetPedHeading`. Los experimentos temporales (banderas de depuración, escaneos, enganche de `ProcessPed`, volcados) se retiraron.

## Veredicto

Puerta superada. El plan sigue con las Tareas 5 a 10.
