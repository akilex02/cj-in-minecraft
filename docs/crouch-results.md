# Agacharse con la C (resultados)

Fecha: 2026-10-11. Verificado en juego quieto, con armas y al levantarse; agacharse en movimiento se corrigió después (ver abajo).

## Cómo funciona
- La tecla C (configurable, categoría CJ) es un **interruptor**, como en GTA. Shift sigue como antes (sneak vanilla con la caminata lenta de CJ).
- Agachado: pose de sneak de Minecraft (hitbox baja, no se cae de los bordes) con la velocidad de **Slowness III** (×0.55, aplicada a la entrada: sin ícono ni cambio de FOV). El sneak vanilla ya escala la entrada por 0.3; se compensa (`CrouchRules.inputScale`) para llegar al ×0.55.
- Se levanta al saltar, esprintar, volar (jetpack o creativo), subir al auto, nadar, trepar, ser derribado, morir o si GTA no responde (`CrouchRules.stays`), y al disparar un arma que no se dispara agachado.
- Armas: se puede apuntar y disparar agachado con Colt 45, Desert Eagle, Micro SMG, escopeta, AK-47 y M4. **Bate, minigun y lanzacohetes** no (`"crouchFire": false` en `weapons.json`): al usarlos se levanta solo. Con puños se puede.
- Moverse: agachado y apuntando, la velocidad es la más lenta entre la agachada y la del arma; **agachado y disparando un arma de fuego, quieto**. Golpeando con puños agachado, el ritmo del golpe (×0.4).
- Dispersión: a la mitad al disparar agachado (`CrouchRules.spreadFactor`, 0.5; el servidor la usa con `ShotRequest.crouching`).

## Bridge
- Nuevo bit de intent `INTENT_CROUCH` (32). El bridge compara el deseo con el estado de GTA (existe la tarea secundaria de agacharse, ranura 1) y, cuando el deseo cambia, pulsa el botón de agacharse (`ShockButtonL`, 0x24 en el pad, la C de GTA lee su flanco) cada dos frames hasta que GTA coincide (máx. 30 frames: GTA lo rechaza si el ped corre, por eso mientras desea agacharse se fuerza el paso de caminata y no el sprint).
- Solo un cambio del deseo inicia las pulsaciones: si GTA levanta a CJ por su cuenta, no se le vuelve a agachar. Si Minecraft y GTA se desincronizaran, pulsar C dos veces lo arregla.
- Sin resolver: si el puente se suelta (GTA deja de recibir estado) estando CJ agachado, queda agachado en GTA hasta el siguiente estado.

## Agacharse en movimiento
Con el stick adelante y la caminata forzada, 15 pulsaciones seguidas del botón de agacharse no hicieron nada en GTA (medido con un registro temporal del bridge), mientras que quieto la primera funcionó al instante: GTA rechaza agachar a un ped que se mueve (`CanPedDuck` mira su estado de movimiento). Arreglo: mientras el bridge intenta agacharlo suelta el stick (CJ queda quieto un instante) y lo devuelve cuando GTA ya está agachado; si en 60 frames no lo logra, sigue caminando.
