# Cuerpo a cuerpo — Investigación de la tarea de pelea de GTA

Fecha: 2026-10-07. Mediciones en GTA SA 1.0 US real con la sonda `tools/melee_probe.py` (que simula el clic izquierdo del mod) y un volcado temporal (ya retirado) de las ranuras de tareas del ped y de la tarea de pelea, fotograma a fotograma.

## Qué se midió
- **Dónde está la tarea:** `CTaskSimpleFight` es el tipo **1016** (el de las armas de fuego es el 1017) y vive en la ranura secundaria de ataque (la misma que usan las armas de fuego). Aparece al empezar a pegar y se queda un tiempo en reposo; no hace falta crearla: la crea el propio juego al recibir el botón de ataque (Circle) sin el de apuntar.
- **Un botón mantenido da un solo puñetazo:** con Circle fijo en 1, GTA lanza un golpe (animación de 0,43 s), la tarea queda en reposo unos 2 s y termina. Hay que **pulsar y soltar** el botón (golpeteo, un fotograma sí y otro no) para que encadene golpes: `PadDriver` ahora lo hace mientras dura `INTENT_MELEE`.
- **Combos:** con el botón golpeteando, CJ encadena sin parar el combo de tres golpes: `m_nCurrentMove` pasa por 0, 1, 2 y vuelve a 0. Cada golpe tiene su animación (`m_pAnim`, puntero que se reutiliza entre unas pocas asociaciones), y su tiempo de animación avanza 1:1 en segundos.
  - **Puño** (`m_nComboSet` 4): golpes de 0,33 / 0,56 / 0,86 s de duración; tiempos de impacto de la tabla (`CTaskSimpleFight::m_aComboData` en 0xC170D0, 0x88 bytes por entrada, `m_fHit[move]` en +8): 0,367 / 0,467 / 0,433 s. El primer golpe (0,33 s) lo corta el segundo antes de llegar a 0,367 s. Cadencia: unos 3 golpes cada 1,75 s.
  - **Bate** (`m_nComboSet` 8): golpes de 0,73 / 0,93 / 1,20 s; impactos 0,467 / 0,467 / 0,433 s. Cadencia: unos 3 golpes cada 2,85 s. El bate (arma 5, modelo 336) se da y se retira sin problemas.
- **Campos útiles** (verificados): tarea `+0x1C` = `m_pAnim`, `+0x24` = `m_nComboSet`, `+0x25` = `m_nCurrentMove` (0 a 2 son golpes; 3 bloqueo; 4 reposo); animación `+0x20` = tiempo actual en segundos (y `+0x18` el peso de mezcla, `+0x1C` su ritmo, `+0x24` la velocidad, `+0x28` el paso de tiempo).

## Decisión del método de detección: cruce del instante del golpe, con respaldo
Se cuenta **un golpe** cada vez que el tiempo de la animación del golpe en curso cruza `m_fHit[move]` de su combo (el instante de impacto de GTA), una sola vez por golpe. Si el golpe siguiente empieza antes de llegar a ese instante (el primero del puño, cortado por el encadenado) o la tarea desaparece a mitad de golpe, se cuenta igualmente al cerrarse el golpe: un golpe que empezó es un golpe. Un golpe nuevo se reconoce porque cambia el movimiento o la animación, o el tiempo de animación retrocede. Reposo, bloqueo y fotogramas sin animación no cuentan.
Por qué no el cambio de estado puro (método A): contaría el inicio de cada animación, 0,3 a 0,45 s antes del impacto visible. Por qué sí (B): coincide con el instante de impacto de GTA y funciona igual con el puño y con el bate.

## Verificación del método en vivo
Con el puente definitivo (sin volcados) y `tools/melee_probe.py` (8 s de golpeteo del botón): el puente reportó **15 golpes de puño** y el usuario contó 15 a ojo; con el bate reportó **10** (todos con `weapon 5`) y el usuario contó 10. Los golpes viajan como `Shot` (`weapon` 0 puño, 5 bate) en el mismo contador acumulado que las rondas de las armas de fuego.
