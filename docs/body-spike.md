# Reacciones del cuerpo — Investigación de animaciones y voz en GTA

Fecha: 2026-10-07. Mediciones en GTA SA 1.0 US real, con Minecraft abierto (el CJ dibujado en F5 muestra el resultado) y un experimento temporal del puente (ya retirado) que ejecutaba órdenes de un fichero sobre el ped: solo animaciones y voz, sin tareas del juego, sin tocar la vida ni el estado.

## Reproducir una animación sobre el CJ marioneta: funciona
- `CAnimManager::BlendAnimation(clump, grupo, id, retardo)` (cdecl, 0x4D4610), con el clump `*(RpClump**)(ped + 0x18)` y el grupo 0 (el de las animaciones de `ped`), crea la asociación y devuelve su puntero. El peso de mezcla (+0x18) sube de 0 a 1 a razón del retardo (con 4 en unos 0,3 s, con 8 en unos 0,15 s). La animación **se refleja en el esqueleto que leemos y el usuario la ve** en CJ.
- **El mando no la pisa:** con la caída libre puesta y el jugador andando, CJ mantiene la pose (probado 10 s). No hace falta pausar el mando ni las tareas del juego.
- **Quitarla:** poner el ritmo de mezcla (+0x1C) a −4 y la bandera `ANIMATION_IS_BLEND_AUTO_REMOVE` (0x4) en las banderas (u16 en +0x2E): el peso baja a 0 en un cuarto de segundo y la asociación se elimina sola. CJ vuelve a su postura normal y a andar con normalidad. ✔
- Campos medidos de la asociación (con tabla virtual): peso +0x18, ritmo +0x1C, tiempo +0x20, velocidad +0x24, paso de tiempo +0x28, id (i16) +0x2C, banderas (u16) +0x2E.

## Animaciones elegidas (grupo 0) y su comportamiento
| Situación | id | Notas medidas |
|---|---|---|
| Caída libre | 120 `FALL_FALL` | banderas 0x4017: en bucle, parcial, se elimina al desvanecerse; el usuario ve la pose de caída libre de GTA |
| Caer al suelo y quedarse tumbado | 124 `FALL_BACK` | dura 0,73 s y luego **se queda congelada en el último fotograma** con el peso a 1 hasta que se quita (banderas 0x4015, sin autoeliminación al terminar); CJ queda tumbado boca arriba |
| Levantarse | 112 `GETUP_0` | dura 1,37 s, banderas 0x4059: al terminar se desvanece sola; el usuario ve cómo se levanta del suelo y queda de pie |
| Muerte | 124 `FALL_BACK` | la misma que quedarse tumbado, mantenida hasta que se quite |
Descartada: 123 `FALL_COLLAPSE` (cae de rodillas y se levanta de golpe, 1,0 s). Sin probar y sin necesidad: 125 `FALL_FRONT`, 113 a 115, 26 `KO_SKID_BACK`.
- **Encadenar tumbado y levantarse:** quitar la de tumbado (ritmo −4 y bandera 0x4) y poner `GETUP_0` con retardo 8 casi a la vez (150 ms): se enlazan sin saltos.
- **Tiempos:** 0,73 s de caída + unos 1,8 s tumbado = los 2,5 s de `DOWN_MS`; `GETUP_0` (1,37 s) cabe en los 1,5 s de `GETUP_MS`.

## La voz de dolor: funciona
`CPed::Say(ped, contexto, 0, 1.0f, true, true, false)` (thiscall, 0x5EFFE0) devolvió un identificador de sonido válido con los seis contextos probados, y **el usuario los oyó todos, en orden y bien**: 345 `PAIN_LOW`, 344 `PAIN_HIGH`, 350 `PAIN_CJ_GRUNT`, 358 `PAIN_CJ_HIGH_FALL`, 343 `PAIN_DEATH_LOW`, 342 `PAIN_DEATH_HIGH`.
**Elección:** dolor normal 345; dolor cuando el golpe derriba (la bandera de derribo sube con el daño) 344; muerte 343.

## Seguridad
Nada de lo medido usa tareas, ni toca la vida o las banderas del ped: solo asociaciones de animación (que se eliminan solas o se quitan al soltar) y la voz. La comprobación de que la partida de GTA queda intacta tras soltar la marioneta se repite con el código final en la Tarea 6.

## Corrección tras ver los clips de GTA del usuario (2026-10-07)
Las animaciones elegidas al principio (`FALL_BACK` 124 y `FALL_FRONT` 125) son variantes de la **caída en el aire**: 124 deja a CJ tumbado boca arriba y 125 queda en una pose de «salto de ángel», no pegado al suelo como en GTA. Con el CJ dibujado en Minecraft se probaron las de derrape:
- **26 `KO_SKID_BACK`** (banderas 0x4811, se queda congelada en el último fotograma): CJ cae **pegado al suelo boca abajo**, exactamente como en el clip del usuario. → caídas, golpes fuertes y **muerte**.
- **24 `KO_SKID_FRONT`** (banderas 0x4011, congelada): cae **boca arriba**; el usuario propuso usarla para una **explosión cercana**.
- Levantarse: **115 `GETUP_FRONT`** (desde boca abajo, 1,37 s, banderas 0x4859, se retira sola) —confirmado con el clip— y **112 `GETUP_0`** (desde boca arriba, 1,37 s).
