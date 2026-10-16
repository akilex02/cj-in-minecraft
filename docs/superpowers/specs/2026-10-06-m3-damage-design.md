# M3 — Daño a mobs y rotura de bloques a tiros (diseño)

Fecha: 2026-10-06. Continúa el diseño principal (`2026-10-05-cj-in-minecraft-design.md`, sección 6) y M2 (`2026-10-05-m2-combat-design.md`, `docs/m2-results.md`).

## 1. Objetivo y criterio de éxito

Que los disparos de CJ **hagan daño** a los mobs y **rompan bloques**, resueltos por las reglas del servidor de Minecraft, con las seis armas de fuego de M2 (Colt 45, Desert Eagle, escopeta, Micro SMG, AK-47, M4).

**El hito está cumplido cuando**, en un mundo de un jugador con GTA corriendo:
1. Disparar a un mob le quita vida, con tu jugador como fuente: suelta sus drops, da XP, se enfada y recibe un empuje. Cada arma mata a un zombi en un número de disparos coherente con su `damage`.
2. La escopeta lanza varios perdigones abiertos en abanico; cada uno puede dar a un blanco distinto.
3. Disparar a un bloque lo va agrietando (las grietas de Minecraft) y, al agotarse su vida, lo rompe y suelta sus ítems, **aunque el bloque necesite herramienta**. Si dejas de disparar unos segundos, el bloque se regenera. El bedrock y los bloques de dureza negativa no se rompen.
4. Las partículas de traza e impacto de M2 siguen saliendo, ahora donde caen de verdad los perdigones (con la dispersión).
5. Sin GTA, o si algo falla al resolver un disparo, el juego no se cae y el resto del mod sigue.

**Alcance acordado (2026-10-06):** solo armas de fuego. Cuerpo a cuerpo (puño y bate, que necesita un evento de golpe nuevo de GTA), minigun y lanzacohetes (armas nuevas con modelo y explosiones) quedan para hitos posteriores.

**Fuera de M3:** daño crítico por cabeza, cambio de armas explosivas, reacciones de CJ al daño y muerte (M4), disparos de mobs hacia el jugador, multijugador y servidores dedicados.

## 2. Arquitectura

El disparo se resuelve en el **servidor integrado**, que en un solo jugador vive en el mismo proceso que el cliente. No hay paquetes de red propios: el cliente encola el trabajo en el hilo del servidor y el servidor devuelve el resultado por una cola en memoria (enfoque 1 de la propuesta; los paquetes quedan para un futuro soporte de LAN).

Flujo de un disparo:
1. GTA manda el evento `Shot` (sin cambios). `ShotEffects` (cliente) lo deduplica como en M2.
2. Por cada disparo nuevo, el cliente captura **origen** (ojos del jugador), **dirección** (su mirada), el arma sostenida y una semilla, y los entrega al servidor con `server.execute(...)`. Si no hay servidor integrado (cliente conectado a otro servidor), no se resuelve nada y se registra una vez.
3. `ShotResolver` (servidor) busca al `ServerPlayerEntity` del jugador local y, por cada perdigón del arma, aplica la dispersión, lanza el rayo hasta `range` bloques y resuelve el impacto sobre entidad o bloque.
4. El servidor devuelve a la cola `ShotOutcomes` un `ShotOutcome` con el punto final y el tipo de cada perdigón (fallo, bloque o entidad). El cliente, en su tick, dibuja la traza y el impacto con ese resultado.

## 3. Componentes

Todo el código de resolución va en el paquete `dev.cjmc.combat`, sin referencias a clases de cliente (el mod se carga también en el lado servidor, `environment: "*"`).

- **`Spread`** (puro): `perturb(dir, spread, random) -> dir'`. Desvía una dirección unitaria dentro de un cono; `spread` es la tangente del semiángulo (el valor de `weapons.json`: 0.01 pistola, 0.08 escopeta). Con `spread = 0` devuelve la misma dirección; una dirección no finita o nula no se acepta.
- **`BlockHealthModel`** (puro): vida de bloques por posición y mundo.
  - Vida máxima = `max(dureza, minHardness) × healthPerHardness`; bloques de dureza negativa son inmunes.
  - `hit(pos, hardness, power, tick)` resta `power` y devuelve el estado de grietas (0 a 9, o «roto» al llegar a cero).
  - `tick(now)` devuelve las posiciones cuya vida se regenera tras `regenDelayTicks` sin impactos, a `regenPerTick` por tick, hasta quitar las grietas y olvidar el bloque.
  - Sin dependencias de Minecraft: recibe dureza y poder como números.
- **`PelletRay`** (puro): reparto de los perdigones de un disparo (`pellets`, semillas derivadas de la del disparo) y la longitud del rayo (`min(range, 100)`).
- **`ShotRequest` / `ShotOutcome`** (registros): lo que viaja del cliente al servidor y del servidor al cliente.
- **`ShotResolver`** (servidor, usa Minecraft): lanza los rayos contra bloques (sin fluidos) y entidades (sin espectadores ni el propio jugador), elige el impacto más cercano y lo aplica:
  - *Entidad viva:* `damage` con `DamageSource` de jugador, y empuje (`knockback` del arma) en la dirección del disparo.
  - *Bloque:* `BlockHealthModel.hit`; actualiza las grietas con `setBlockBreakingInfo` y, si se rompe, lo rompe soltando ítems con una herramienta que cumple cualquier requisito (configurable con `dropsWithoutTool`).
  - Cualquier excepción se captura, se registra una vez y no tumba el servidor.
- **`BlockHealthTicker`**: en cada tick del servidor llama a `BlockHealthModel.tick` y refleja la regeneración en las grietas; olvida el estado al descargar un mundo.
- **Cliente:** `ShotEffects` deja de lanzar rayos propios; consume `ShotOutcomes` en el tick y dibuja la traza (hasta el punto final de cada perdigón) y el impacto (partículas de bloque o críticas).

## 4. Datos (`weapons.json`)

Cada arma ya trae `damage`, `range`, `pellets`, `spread` y `blockPower`; se añade `knockback`. El fichero gana una sección `blocks` con las constantes:

```json
"blocks": {"healthPerHardness": 4.0, "minHardness": 0.25, "regenDelayTicks": 60,
           "regenPerTick": 0.05, "dropsWithoutTool": true}
```

Valores por defecto de las armas (puntos de vida; 20 = una vida completa):

| Arma | damage | pellets | spread | blockPower | knockback |
|---|---|---|---|---|---|
| Colt 45 | 6 | 1 | 0.01 | 0.5 | 0.3 |
| Desert Eagle | 14 | 1 | 0.01 | 1.0 | 0.6 |
| Escopeta | 3 | 8 | 0.08 | 0.5 | 0.1 |
| Micro SMG | 3 | 1 | 0.04 | 0.3 | 0.1 |
| AK-47 | 7 | 1 | 0.03 | 0.6 | 0.3 |
| M4 | 6 | 1 | 0.02 | 0.6 | 0.25 |

Con esos valores y la constante 4: un zombi (20) cae en 4 tiros de Colt 45, 2 de Desert Eagle o 3 de AK-47; la tierra (dureza 0.5, vida 2) en 4 tiros de Colt 45 o 4 de AK-47; la piedra (1.5, vida 6) en 12 de Colt 45 o 10 de AK-47; la obsidiana (50, vida 200) en unos 40 s de ráfaga de AK-47. Todo es ajustable jugando.

## 5. Casos límite

- **Sin servidor integrado o sin jugador del lado servidor:** no se resuelve el disparo, con un único aviso en el registro.
- **Jugador muerto, en espectador o con una pantalla abierta:** M2 ya no manda intenciones con pantalla abierta; el resolutor además descarta disparos de un jugador muerto o en espectador.
- **Disparos apilados** (ráfaga de varias rondas en un tick): cada ronda es una petición con su semilla; la cola del servidor se vacía en orden.
- **Rango o dispersión absurdos** en `weapons.json`: se rechazan al cargar (como el resto de campos).
- **Bloque que cambia mientras se agrieta** (otro jugador o el propio mundo): si la dureza cambia o el bloque desaparece, se reinicia su vida.
- **Bloques con entidad** (cofres, hornos): se rompen como cualquier otro y sueltan su contenido; no hay lista de excepciones (límite conocido).
- **Mundo descargado o dimensión cambiada:** el estado de vida de los bloques de ese mundo se descarta.

## 6. Pruebas

- **Automáticas** (con mutaciones, como en M1 y M2): `Spread` (cono, cero, dirección inválida, media), `BlockHealthModel` (daño acumulado, tramos de grietas, rotura, regeneración tras el retraso, olvido, inmunidad, cambio de dureza), `PelletRay` (número de perdigones, rango máximo, semillas distintas), el análisis de `blocks` y `knockback` en `weapons.json` (campos obligatorios, rangos).
- **En vivo con GTA real** (con el usuario): daño a zombi, cerdo y creeper con cada arma; escopeta a corta y larga distancia; romper tierra, piedra, madera, cristal y bedrock; regeneración; que las partículas caigan donde caen los perdigones; disparar sin GTA; que nada se rompa al abrir el inventario.
- **Revisión final independiente** y una pasada de arreglos, como en M2.

## 7. Riesgos

- **Nombres de Yarn** (`damage`, `takeKnockback`, `breakBlock`, `setBlockBreakingInfo`, `ProjectileUtil.raycast`) y su comportamiento exacto en 1.21.1: se comprueban al compilar y en vivo.
- **Soltar ítems de bloques que necesitan herramienta:** la herramienta que se usa para decidir los drops debe cumplir cualquier requisito sin dar encantamientos; se verifica en vivo.
- **El servidor integrado en pausa** (menú de pausa en un solo jugador): las peticiones esperan; no hay disparos nuevos con el juego en pausa, así que no es un problema real.
- **Diferencia entre la mira de Minecraft y el cañón de GTA:** como en M2, no influye; el disparo sale de los ojos del jugador.
