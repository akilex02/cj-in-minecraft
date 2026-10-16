# Reacciones del cuerpo (dolor, caída libre, suelo, muerte): plan de implementación

> **Para agentes:** SUB-HABILIDAD OBLIGATORIA: usar superpowers:executing-plans (el usuario elige siempre la ejecución nativa) o superpowers:subagent-driven-development para ejecutar este plan tarea a tarea. Los pasos usan casillas (`- [ ]`).

**Objetivo:** que CJ reaccione a lo que le pasa al jugador de Minecraft con las animaciones y la voz de GTA: frase de dolor al recibir daño, animación de caída libre, caer al suelo y levantarse tras un aterrizaje duro o una explosión (con el jugador inmovilizado), y quedarse tumbado al morir hasta reaparecer. Sin matar, herir ni inmovilizar nunca al jugador real de GTA.

**Arquitectura:** el cliente de Minecraft calcula el estado del cuerpo (cayendo, derribado, muerto, contador de daños) con reglas puras y lo manda en bits nuevos de las banderas del `State` (sin cambiar el protocolo de tamaño ni de versión). El servidor integrado decide los derribos (caída fuerte, explosión, golpe fuerte) y se lo dice al cliente por una cola en memoria. El puente lee las banderas con una máquina de estados pura (`BodyReactions`) que decide qué animación de GTA toca y cuándo, y la ejecuta con `CAnimManager::BlendAnimation` sobre el clump del ped (solo animación, nunca tareas que maten o hieran), más la voz de dolor con `CPed::Say`. Un mixin inmoviliza al jugador mientras dura el derribo.

**Tecnologías:** Java 21 / Fabric 1.21.1 con Yarn (JUnit 5), C++ del puente (pruebas nativas), Python 3 (protocolo), GTA SA 1.0 US bajo Wine.

**Spec:** `docs/superpowers/specs/2026-10-07-body-reactions-design.md`. Contexto: `docs/m4-effects-results.md`, `docs/melee-results.md`, `docs/melee-spike.md`.

## Global Constraints

- **Protocolo v2 sin cambiar de tamaño ni de versión.** Bits nuevos de `State.flags` (u32, hoy usados 0 a 4): `FLAG_FALLING` = 1<<5, `FLAG_KNOCKED` = 1<<6, `FLAG_DEAD` = 1<<7 y el **contador de daños recibidos** en los bits 8 a 11 (`HURT_SHIFT` = 8, máscara de 4 bits, vuelve a 0 tras 15). Un cambio del contador es el evento «me han dañado». Los ficheros dorados no cambian (las banderas son datos).
- **El jugador real de GTA no puede sufrir nada:** nada de `CTaskComplexDie`, `CTaskComplexFallAndGetUp` ni `CTaskComplexInAirAndLand` (activan muerte, hospital, estados del jugador). Solo **animaciones** (`BlendAnimation`) y voz. La vida y las banderas del ped de GTA no se tocan. Al soltar la marioneta o perder el estado, las animaciones puestas se quitan.
- Números (spec §4, todos constantes con nombre y ajustables): caída libre con `fallDistance` ≥ 2,5 bloques o velocidad vertical ≤ −0,6 bloques/tick; derribo por daño de caída ≥ 3, por explosión o por un golpe de ≥ 8 de daño; en el suelo 2,5 s (`DOWN_MS` 2500) y 1,5 s para levantarse (`GETUP_MS` 1500, el jugador sigue inmovilizado esos 1,5 s: 4 s en total); enfriamiento de la voz de dolor 1,5 s (`PAIN_COOLDOWN_MS` 1500).
- **Cuándo se aplica:** solo con CJ dibujado (`CjClient.bridgeLive()` y `CjRenderer.INSTANCE.hasFreshPose()`); sin GTA no se manda nada, no se inmoviliza y el jugador se comporta como siempre. Nunca reacciones de suelo montando, nadando, en una escalera o volando (esos casos son de otras piezas o ya van como Minecraft).
- **Inmovilización con vigilante:** el jugador queda inmovilizado como mucho 4 s por derribo (por el temporizador, no por una bandera que pudiera quedarse puesta); si GTA deja de responder se libera en el acto. Muerto: sin control (ya lo da la pantalla de muerte).
- El paquete `dev.cjmc.body` (reglas puras de Minecraft) no usa clases de Minecraft; `dev.cjmc.combat`/`dev.cjmc` común no usa clases de cliente.
- Convenciones del repo: TDD con el rojo observado, mutaciones comprobadas por el arnés (el fallo debe ser de la prueba, no de compilación; Java con `python3 tools/mutj.py <json>`, nativas escribiendo el mutante y ejecutando `bash tools/run_native_tests.sh`), commits con `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`, comentarios en inglés en el código, documentos en español.
- Comandos: Python `cd tools && python3 -B -m unittest discover -s tests` y `cd protocol && python3 -B -m unittest test_cjproto`; nativas `bash tools/run_native_tests.sh`; Java `cd cj-fabric && ./gradlew test --console=plain`.
- Abrir y cerrar los juegos con `tools/start_games.sh` (`--restart` cierra y abre ambos; GTA primero; reiniciar GTA para cargar un puente nuevo). La herramienta de comandos se queda esperando si se encadena el script con una tubería (`| tail`): redirigir su salida a un fichero. Cerrar procesos por PID leyendo `ps -eo pid,args` o `/proc/<pid>/cmdline`; nunca `pkill -f` ni `pgrep -f` con una cadena que contenga el propio comando.
- Hechos de GTA (gta-reversed, **hipótesis a verificar en vivo en la Tarea 4**): `CAnimManager::BlendAnimation(RpClump*, AssocGroupId, AnimationId, float blendDelta)` cdecl en 0x4D4610 (devuelve `CAnimBlendAssociation*`); el clump del ped es `*(RpClump**)(ped + 0x18)`; grupo `ANIM_GROUP_DEFAULT` = 0; ids de `AnimationEnums.h`: `ANIM_ID_JUMP_LAUNCH` 116, `JUMP_GLIDE` 118, `JUMP_LAND` 119, `FALL_FALL` 120 (en bucle), `FALL_GLIDE` 121, `FALL_LAND` 122, `FALL_COLLAPSE` 123, `FALL_BACK` 124, `FALL_FRONT` 125, `GETUP_0/1/2` 112 a 114, `GETUP_FRONT` 115, `KO_SKID_FRONT` 24, `KO_SKID_BACK` 26, `HIT_FRONT/L/BACK/R` 32 a 35. `CAnimBlendAssociation` (con tabla virtual, medido en la investigación de la pelea): peso de mezcla en +0x18, ritmo de mezcla en +0x1C, tiempo actual en +0x20, velocidad +0x24, paso de tiempo +0x28, id de animación (i16) en +0x2C y banderas (u16) en +0x2E. `CPed::Say(eGlobalSpeechContext, uint32 startDelay, float probability, bool overrideSilence, bool forceAudible, bool frontEnd)` thiscall en 0x5EFFE0; contextos de dolor: `CTX_GLOBAL_PAIN_HIGH` 344, `PAIN_LOW` 345, `PAIN_CJ_GRUNT` 350, `PAIN_CJ_HIGH_FALL` 358, `PAIN_DEATH_HIGH` 342, `PAIN_DEATH_LOW` 343.

## Review Focus

1. **La partida de GTA tras soltar la marioneta:** ningún cambio de vida, de estado (muerto, derribado, hospital) ni animación pegada en CJ; si el puente se suelta (cierre de Minecraft, 1 s sin estado) con una animación puesta, se quita. → Tareas 4 y 5 (en vivo).
2. **Falsos disparadores:** caer al agua, montar, nadar, escalera, volar en creativo, elytra y espectador no activan caída libre ni derribo; un derribo no se encadena con otro. → Tarea 2 (puro) y Tarea 3.
3. **Inmovilización que no se suelta:** con el derribo vencido, GTA sin responder, el jugador muerto que reaparece o un cambio de mundo, el control vuelve en menos de 4 s. → Tarea 2 (temporizador) y Tarea 3.
4. **Voz de dolor:** sin daño no suena; con daños seguidos no se repite más de una vez por enfriamiento; el contador de 4 bits que da la vuelta no se confunde con «sin cambio». → Tarea 2 y Tarea 5.
5. **Muerte y reaparición:** al morir CJ cae y queda; al reaparecer se limpia y vuelve a su postura; reaparecer no cuenta como daño. → Tarea 2 y Tarea 5.

---

### Tarea 1: Las banderas nuevas en el protocolo (Python, C++ y Java)

**Archivos:**
- Modificar: `protocol/cjproto.py`, `protocol/test_cjproto.py`
- Modificar: `gta-bridge/src/protocol.h`, `gta-bridge/tests/test_protocol.cpp`
- Modificar: `cj-fabric/src/main/java/dev/cjmc/Protocol.java`, `cj-fabric/src/test/java/dev/cjmc/ProtocolTest.java`

**Interfaces:**
- Consume: `State.flags` (u32), `FLAG_*` existentes (bits 0 a 4).
- Produce: en los tres lados `FLAG_FALLING` = 1<<5, `FLAG_KNOCKED` = 1<<6, `FLAG_DEAD` = 1<<7, `HURT_SHIFT` = 8, `HURT_MASK` = 0xF (ya desplazada: máscara del campo en `flags`: 0xF00), y ayudas de empaquetado: Python `body_flags(falling, knocked, dead, hurt) -> int` y `hurt_of(flags) -> int`; C++ `HurtCounter(uint32_t flags) -> uint8_t`; Java `Protocol.hurtOf(int flags)` y `Protocol.bodyFlags(boolean falling, boolean knocked, boolean dead, int hurt)`.

- [ ] **Paso 1: Pruebas en rojo**
  1. `protocol/test_cjproto.py`: una prueba `test_body_flags_do_not_overlap_and_round_trip`: los bits `FLAG_FALLING`, `FLAG_KNOCKED`, `FLAG_DEAD` y el campo del contador (0xF00) no se solapan entre sí ni con los de `FLAG_ON_GROUND`..`FLAG_CLIMBING`; `body_flags(True, False, True, 5)` da `FLAG_FALLING | FLAG_DEAD | (5 << 8)`; `hurt_of(...)` devuelve 5; `body_flags(False, False, False, 16)` envuelve a 0 (`hurt` se toma módulo 16); un `State` con esas banderas sobrevive a `pack_state`/`unpack_state`.
  2. `gta-bridge/tests/test_protocol.cpp`: los mismos valores con `CHECK` (los bits no se solapan, `HurtCounter` extrae el campo y respeta el módulo 16).
  3. `cj-fabric/src/test/java/dev/cjmc/ProtocolTest.java`: los mismos casos con `Protocol.bodyFlags` y `Protocol.hurtOf`, y el viaje de un `State` con esas banderas por `encodeState` y una decodificación.

- [ ] **Paso 2: Verlas fallar** — `cd protocol && python3 -B -m unittest test_cjproto 2>&1 | tail -4`; `bash tools/run_native_tests.sh 2>&1 | tail -3`; `cd cj-fabric && ./gradlew test --console=plain 2>&1 | grep -E "error:|FAILED|BUILD" | head -3`. Expected: fallos o errores de compilación por símbolos que no existen.

- [ ] **Paso 3: Implementar**
  1. `cjproto.py`: tras `FLAG_CLIMBING`: `FLAG_FALLING = 1 << 5`, `FLAG_KNOCKED = 1 << 6`, `FLAG_DEAD = 1 << 7`, `HURT_SHIFT = 8`, `HURT_MASK = 0xF << 8`; funciones `body_flags(falling, knocked, dead, hurt)` (`(FLAG_FALLING if falling else 0) | ... | ((hurt & 0xF) << HURT_SHIFT)`) y `hurt_of(flags)` (`(flags & HURT_MASK) >> HURT_SHIFT`).
  2. `protocol.h`: constantes `kFlagFalling`, `kFlagKnocked`, `kFlagDead`, `kHurtShift = 8`, `kHurtMask = 0xFu << 8` y `inline uint8_t HurtCounter(uint32_t flags) { return static_cast<uint8_t>((flags & kHurtMask) >> kHurtShift); }`.
  3. `Protocol.java`: `FLAG_FALLING = 32`, `FLAG_KNOCKED = 64`, `FLAG_DEAD = 128`, `HURT_SHIFT = 8`, `HURT_MASK = 0xF00`, `static int bodyFlags(boolean falling, boolean knocked, boolean dead, int hurt)` y `static int hurtOf(int flags)`.

- [ ] **Paso 4: Verlas pasar** — los tres comandos del paso 2. Expected: todo `OK` / `BUILD SUCCESSFUL`. Comprobar además que los ficheros dorados siguen igual (`git status` no los muestra).

- [ ] **Paso 5: Mutaciones** (a mano, restaurando cada una): cambiar `<< 8` por `<< 7` en una de las implementaciones; quitar `& 0xF` de `body_flags`; cambiar `FLAG_DEAD` a `1 << 6`; cambiar la máscara a `0x7 << 8`. Cada una debe hacer fallar una prueba.

- [ ] **Paso 6: Commit**

```bash
git add -A && git commit -m "Body reactions: the falling, knocked and dead flags and the hurt counter in the State flags (Python, C++, Java)

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Tarea 2: Reglas puras del lado de Minecraft (caída, daños, derribo, inmovilización)

**Archivos:**
- Crear: `cj-fabric/src/main/java/dev/cjmc/body/FallRules.java`, `HurtCounter.java`, `KnockdownRules.java`, `KnockdownTimer.java`
- Crear: `cj-fabric/src/test/java/dev/cjmc/body/FallRulesTest.java`, `HurtCounterTest.java`, `KnockdownRulesTest.java`, `KnockdownTimerTest.java`

**Interfaces:**
- Consume (Tarea 1): `Protocol.bodyFlags`/`hurtOf` (solo para la Tarea 3).
- Produce: `FallRules.falling(boolean onGround, boolean inFluid, boolean climbing, boolean flying, boolean riding, double fallDistance, double verticalSpeedPerTick) -> boolean`; `HurtCounter.update(float health, boolean alive) -> int` (0 a 15; sube en uno cuando la vida baja estando vivo; reaparecer no cuenta); `KnockdownRules.causes(boolean fall, boolean explosion, float damage) -> boolean`; `KnockdownTimer` con `start(long nowMs) -> boolean` (falso si ya está en curso), `knocked(long nowMs)` (true durante `DOWN_MS`), `frozen(long nowMs)` (true durante `DOWN_MS + GETUP_MS`) y `clear()`; constantes `FallRules.FALL_DISTANCE` = 2.5, `FallRules.FALL_SPEED` = −0.6, `KnockdownRules.FALL_DAMAGE` = 3, `KnockdownRules.STRONG_HIT` = 8, `KnockdownTimer.DOWN_MS` = 2500, `KnockdownTimer.GETUP_MS` = 1500.

- [ ] **Paso 1: Pruebas en rojo** (cada prueba en su fichero, en el paquete `dev.cjmc.body`):
  1. `FallRulesTest`: en el aire con `fallDistance` 3 es caída libre; con 2,4 y velocidad −0,3 no; con 2,4 y velocidad −0,6 sí (exactamente el límite) y con −0,59 no; en el suelo nunca; cada estado excluyente (en fluido, escalera, volando, montado) lo anula aunque la caída y la velocidad sean enormes; con `fallDistance` exactamente 2,5 sí y con 2,49 no.
  2. `HurtCounterTest`: empieza en 0; una bajada de vida sube el contador a 1; dos bajadas seguidas llegan a 2; vida igual o mayor no lo cambia; sube de 15 a 0 (vuelta); con `alive` falso la vida 0 no cuenta como daño nuevo dos veces y al reaparecer (vida sube de golpe) no cuenta; una curación pequeña seguida de un daño sí cuenta; el primer `update` nunca cuenta (no hay vida anterior).
  3. `KnockdownRulesTest`: caída con daño 3 sí, 2,9 no; sin caída, el daño 3 no; explosión con cualquier daño positivo sí, con daño 0 no; golpe de 8 sí, 7,9 no; daño 0 o negativo o NaN nunca.
  4. `KnockdownTimerTest`: sin `start`, `knocked`/`frozen` falsos; tras `start(0)`: `knocked(0)` y `knocked(2499)` verdaderos, `knocked(2500)` falso; `frozen(3999)` verdadero y `frozen(4000)` falso; `start(1000)` durante el derribo devuelve falso y no reinicia; tras `frozen` vencido, `start` vuelve a funcionar; `clear()` lo apaga todo; un reloj que retrocede no deja el temporizador activo para siempre (`frozen(-1)` con un inicio en 0 es falso).

- [ ] **Paso 2: Verlas fallar** — `cd cj-fabric && ./gradlew test --console=plain 2>&1 | grep -E "error:|FAILED|BUILD" | head -3`. Expected: error de compilación (las clases no existen).

- [ ] **Paso 3: Implementar**

```java
package dev.cjmc.body;

/** Whether the player is in free fall: in the air, and either far enough above where he started falling or falling fast. */
public final class FallRules {
    public static final double FALL_DISTANCE = 2.5; // blocks
    public static final double FALL_SPEED = -0.6;   // blocks per tick (negative is down)

    private FallRules() {}

    public static boolean falling(boolean onGround, boolean inFluid, boolean climbing, boolean flying, boolean riding,
                                  double fallDistance, double verticalSpeedPerTick) {
        if (onGround || inFluid || climbing || flying || riding) {
            return false;
        }
        return fallDistance >= FALL_DISTANCE || verticalSpeedPerTick <= FALL_SPEED;
    }
}
```

```java
package dev.cjmc.body;

/** Counts the times the player's health went down (0 to 15, wrapping), which the bridge reads as "he was hurt". Coming back to life is not a hurt. */
public final class HurtCounter {
    private float last = Float.NaN;
    private int count;

    public int update(float health, boolean alive) {
        if (!Float.isNaN(last) && alive && last > 0f && health < last) {
            count = (count + 1) & 0xF;
        }
        last = health;
        return count;
    }
}
```
  (la condición `last > 0f` evita contar la vida 0 de un muerto y el salto de reaparición; `alive` falso con vida a 0 solo actualiza `last`).

```java
package dev.cjmc.body;

/** What knocks the player down: a hard fall, an explosion or a heavy blow. */
public final class KnockdownRules {
    public static final float FALL_DAMAGE = 3f;
    public static final float STRONG_HIT = 8f;

    private KnockdownRules() {}

    public static boolean causes(boolean fall, boolean explosion, float damage) {
        if (!(damage > 0f)) {
            return false;
        }
        return (fall && damage >= FALL_DAMAGE) || explosion || damage >= STRONG_HIT;
    }
}
```

```java
package dev.cjmc.body;

/** The time CJ is down: knocked for DOWN_MS, then getting up for GETUP_MS more, frozen throughout. One knockdown does not chain into another. */
public final class KnockdownTimer {
    public static final long DOWN_MS = 2500;
    public static final long GETUP_MS = 1500;

    private long startMs;
    private boolean started;

    /** Starts a knockdown; false (and nothing changes) when one is already under way. */
    public boolean start(long nowMs) {
        if (frozen(nowMs)) {
            return false;
        }
        startMs = nowMs;
        started = true;
        return true;
    }

    public boolean knocked(long nowMs) {
        return started && nowMs >= startMs && nowMs - startMs < DOWN_MS;
    }

    public boolean frozen(long nowMs) {
        return started && nowMs >= startMs && nowMs - startMs < DOWN_MS + GETUP_MS;
    }

    public void clear() {
        started = false;
    }
}
```

- [ ] **Paso 4: Verlas pasar y mutar** — `./gradlew test --console=plain 2>&1 | grep -E "FAILED|BUILD"`. Mutaciones con `tools/mutj.py` (rutas `body/...`): en `FallRules` cambiar `fallDistance >= FALL_DISTANCE` por `>`, `verticalSpeedPerTick <= FALL_SPEED` por `<`, y quitar cada uno de los cuatro estados excluyentes; en `HurtCounter` cambiar `& 0xF` por `& 0x7`, `health < last` por `health <= last`, quitar `alive &&` y `last > 0f &&`; en `KnockdownRules` cambiar `damage >= FALL_DAMAGE` por `>`, `damage >= STRONG_HIT` por `>`, quitar `fall &&` y `explosion ||`; en `KnockdownTimer` cambiar `< DOWN_MS` por `<=`, quitar `nowMs >= startMs`, quitar `if (frozen(nowMs))`, sumar solo `DOWN_MS` en `frozen`. Todas deben morir.

- [ ] **Paso 5: Commit**

```bash
git add -A && git commit -m "Body reactions: pure rules for free fall, the hurt counter, what knocks the player down and the knockdown timer

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Tarea 3: Minecraft: el estado del cuerpo llega a GTA y el jugador se inmoviliza

**Archivos:**
- Crear: `cj-fabric/src/main/java/dev/cjmc/body/CjBody.java` (servidor: derribos), `cj-fabric/src/main/java/dev/cjmc/BodyClient.java` (cliente)
- Modificar: `cj-fabric/src/main/java/dev/cjmc/CjMain.java`, `CjClient.java`, `mixin/KeyboardInputMixin.java`, `mixin/MinecraftClientMixin.java`

**Interfaces:**
- Consume (Tareas 1 y 2): `Protocol.bodyFlags`, `FallRules`, `HurtCounter`, `KnockdownRules`, `KnockdownTimer`, `CjClient.bridgeLive()`, `CjRenderer.INSTANCE.hasFreshPose()`.
- Produce: `CjBody.init()` (registra el oyente de daños del servidor) y `CjBody.takeKnockdown(UUID) -> boolean` (consume un derribo pendiente del jugador); `BodyClient.flags(ClientPlayerEntity, long nowMs) -> int` (las banderas del cuerpo para el `State`) y `BodyClient.frozen() -> boolean`.

- [ ] **Paso 1: Servidor — `CjBody.java`**

```java
package dev.cjmc.body;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server side of the body reactions: decides which damage knocks a player down and tells the client through a queue in memory. */
public final class CjBody {
    private static final Set<UUID> KNOCKDOWNS = ConcurrentHashMap.newKeySet();

    private CjBody() {}

    public static void init() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (entity instanceof ServerPlayerEntity player && !blocked
                    && KnockdownRules.causes(source.isOf(DamageTypes.FALL), source.isIn(DamageTypeTags.IS_EXPLOSION), damageTaken)) {
                KNOCKDOWNS.add(player.getUuid());
            }
        });
    }

    /** True once for each knockdown the server decided for this player since the last call. */
    public static boolean takeKnockdown(UUID player) {
        return KNOCKDOWNS.remove(player);
    }
}
```
  (verificar con `javap` que `ServerLivingEntityEvents.AFTER_DAMAGE` tiene esa firma en la versión de Fabric API del proyecto —`(LivingEntity, DamageSource, float, float, boolean)`— y que `DamageTypeTags.IS_EXPLOSION` y `DamageSource.isOf/isIn` existen; ajustar sin cambiar el comportamiento).

- [ ] **Paso 2: Cliente — `BodyClient.java`** (en `dev.cjmc`, solo cliente):

```java
package dev.cjmc;

import dev.cjmc.body.CjBody;
import dev.cjmc.body.FallRules;
import dev.cjmc.body.HurtCounter;
import dev.cjmc.body.KnockdownTimer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/** The body state the bridge needs (falling, knocked, dead, hurt counter) and the freeze while CJ is down. Only while CJ is drawn. */
public final class BodyClient {
    private static final HurtCounter HURT = new HurtCounter();
    private static final KnockdownTimer TIMER = new KnockdownTimer();
    private static volatile boolean frozen;

    private BodyClient() {}

    public static boolean frozen() {
        return frozen;
    }

    /** Called once per frame by CjClient with the player the State is about; returns the body bits of the flags. */
    static int flags(ClientPlayerEntity player, long nowMs) {
        boolean drawn = CjClient.bridgeLive() && CjRenderer.INSTANCE.hasFreshPose();
        if (!drawn) {
            TIMER.clear();
            frozen = false;
            return 0;
        }
        boolean dead = !player.isAlive() || player.getHealth() <= 0f;
        int hurt = HURT.update(player.getHealth(), player.isAlive());
        if (dead) {
            TIMER.clear(); // the death overrides a knockdown under way; the death screen holds the player
        } else if (CjBody.takeKnockdown(player.getUuid())) {
            TIMER.start(nowMs);
        }
        boolean knocked = !dead && TIMER.knocked(nowMs);
        frozen = !dead && TIMER.frozen(nowMs);
        boolean falling = !dead && !frozen && FallRules.falling(player.isOnGround(),
                player.isTouchingWater() || player.isInLava(), player.isClimbing(), player.getAbilities().flying, player.hasVehicle(),
                player.fallDistance, player.getVelocity().y);
        return Protocol.bodyFlags(falling, knocked, dead, hurt);
    }
}
```
  (`Protocol` está en `dev.cjmc`; `player.fallDistance` es público en `Entity`.)

- [ ] **Paso 3: Conexión**
  1. `CjMain.onInitialize`: llamar a `dev.cjmc.body.CjBody.init();` tras `CjCombat.init(catalog)`.
  2. `CjClient.onFrame`: al construir `flags`, añadir `flags |= BodyClient.flags(player, now / 1_000_000L);` justo antes de crear el `State`; el cliente de un servidor remoto no recibe derribos (la cola es del servidor integrado), pero sigue mandando caída, daños y muerte.
  3. `KeyboardInputMixin.cj$limitInput` (tras el resto): `if (BodyClient.frozen()) { movementForward = 0; movementSideways = 0; jumping = false; sneaking = false; }`. (`BodyClient` y su `frozen()` son públicos porque los mixins viven en otro paquete.)
  4. `MinecraftClientMixin`: añadir al inicio de `doAttack` y `doItemUse` la anulación `if (dev.cjmc.BodyClient.frozen()) { cancel }` (derribado no se ataca ni se usa nada).

- [ ] **Paso 4: Compilar** — `cd cj-fabric && ./gradlew build --console=plain 2>&1 | grep -E "error:|warning:|FAILED|BUILD"`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Paso 5: Comprobar el arranque** — `tools/start_games.sh --restart > /tmp/cj-start.log 2>&1`, esperar a `bridge ready` en `cj-fabric/run/logs/latest.log` y que no haya `InvalidInjection`, errores de registro de eventos ni de `weapons.json`.

- [ ] **Paso 6: Commit**

```bash
git add -A && git commit -m "Body reactions: the client sends falling, knocked, dead and the hurt counter, the server decides the knockdowns, and the player is frozen while CJ is down

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Tarea 4: Investigación en GTA: animar a CJ por encima del mando, y la voz de dolor

**Archivos:**
- Modificar (temporal, se quita al terminar): `gta-bridge/src/bridge.cpp`
- Crear: `tools/body_probe.py`, `docs/body-spike.md`

**Interfaces:**
- Consume: la Tarea 1 (banderas), el puente actual, `docs/melee-spike.md` como ejemplo del método.
- Produce: en `docs/body-spike.md` (en español) las respuestas medidas a la investigación (§3 de la spec) y las constantes que usará la Tarea 5: ids de animación definitivos para caída libre, caer al suelo, tumbado, levantarse y muerte; con qué banderas del `CAnimBlendAssociation` se mantienen o se quitan; cómo se evita que el mando las pise; la firma y el contexto de la voz de dolor; y la lista de lo que hay que restaurar al soltar.

- [ ] **Paso 1: Sonda `tools/body_probe.py`** — copia de `tools/melee_probe.py` (mismo cuerpo de envío/recepción) cuyo argumento es una **fase** (`fall`, `down`, `dead`, `hurt`) y que manda `State` con `flags = FLAG_ON_GROUND` más las banderas de la fase (`FLAG_FALLING`; `FLAG_KNOCKED` durante 2,5 s y luego sin ella; `FLAG_DEAD`; el contador de daños sube en 1 cada 2 s durante 10 s) durante 10 s, y luego libera 3 s; imprime la hora de cada cambio. Con GTA con partida y Minecraft cerrado, el usuario mira la ventana de GTA.

- [ ] **Paso 2: Experimento temporal en el puente** — en `bridge.cpp`, un `WinIo::ExperimentBody(ped, flags)` llamado cada fotograma desde `HeldWeapon` (como en la investigación de la pelea) que, al cambiar las banderas, hace: con `FLAG_FALLING`, `CAnimManager::BlendAnimation(clump, 0, 120, 4.0f)` (`FALL_FALL`); con `FLAG_KNOCKED`, la de caída al suelo (probar 123 `FALL_COLLAPSE`, 124 `FALL_BACK`, 125 `FALL_FRONT` y 26 `KO_SKID_BACK`, una por pasada, elegida por una variable de entorno o por un número que `body_probe.py` manda en `pad` del `State`); al bajar `FLAG_KNOCKED`, una de levantarse (112 a 115); con `FLAG_DEAD`, una de las de caída o muerte; con subida del contador, `CPed::Say` con los contextos 345, 344 y 350 (uno por golpe). Registrar el puntero devuelto, el peso de mezcla y el tiempo de la animación cada 100 ms, y si la animación se mantiene o se retira sola. Si el mando (andar/quieto) la pisa o el esqueleto no la refleja, probar: poner el peso de mezcla de las demás animaciones a 0; o quitar las banderas de autoeliminación; o mezclar con un `blendDelta` mayor.
  - Cuidado (seguridad): no llamar a ninguna tarea de muerte o de caída del juego; no tocar la vida ni las banderas del ped; si algo falla, soltar con `RemoveAnimation` en cuanto se pierda el estado.

- [ ] **Paso 3: Medir y decidir** — con la sonda (una fase por pasada), anotar en `docs/body-spike.md`:
  1. ¿Se refleja la animación en el esqueleto (la pose que lee `ReadSkeleton`) y el usuario la ve en GTA?, ¿la pisa el mando?, ¿cómo se evita?
  2. Para cada situación, el id que se ve bien (caída libre, caer al suelo, tumbado, levantarse, muerte) y su duración; si la de caer al suelo termina tumbada y se mantiene (banderas y tiempo final) o hay que congelarla en el último fotograma.
  3. Cómo se quita una animación puesta (peso de mezcla y ritmo, banderas de autoeliminación, o una función del juego) y que tras quitarla CJ vuelve a andar y estar quieto con normalidad.
  4. ¿Suena la frase de dolor con `CPed::Say(ctx, 0, 1.0f, true, true, false)`? Contextos que suenan bien para el daño normal, la caída y la muerte; ¿hace falta `overrideSilence`?; ¿se solapa si se repite?
  5. **La partida de GTA queda intacta:** tras soltar la marioneta (cerrar la sonda y esperar 1 s) la vida del CJ de GTA, su estado y sus animaciones son los de antes; anotarlo.
  Si ninguna combinación mantiene la animación sobre el mando, **parar el plan y decidir con el usuario** (por ejemplo pausar el mando mientras dura).

- [ ] **Paso 4: Documentar y commit** — escribir `docs/body-spike.md` con las mediciones y las constantes elegidas, quitar el experimento de `bridge.cpp` (`git checkout gta-bridge/src/bridge.cpp`) y comprometer:

```bash
git add -A && git commit -m "Body reactions: spike notes (playing GTA animations on the puppet, pain voice) and the body probe

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Tarea 5: El puente anima a CJ y le da voz

**Archivos:**
- Modificar: `gta-bridge/src/weapon_driver.h` o crear `gta-bridge/src/body_reactions.h`, `gta-bridge/src/ped_layout.h`, `gta-bridge/src/bridge_core.h`, `gta-bridge/src/bridge.cpp`
- Modificar: `gta-bridge/tests/test_bridge_core.cpp`; crear `gta-bridge/tests/test_body_reactions.cpp` y registrarlo en `tools/run_native_tests.sh` y en el `CMakeLists.txt` del puente como las demás suites

**Interfaces:**
- Consume (Tareas 1 y 4): las banderas, las constantes de `docs/body-spike.md`.
- Produce: `BodyReactions` (puro) con `Update(uint32_t flags, uint32_t nowMs) -> BodyPlan`, `BodyPlan { BodyAnim anim; bool say; }` y `enum class BodyAnim { None, Fall, Down, GetUp, Dead, Clear }`; `BridgeIo::PlayBodyAnim(uint8_t* ped, BodyAnim)`, `BridgeIo::ClearBodyAnim(uint8_t* ped)` y `BridgeIo::SayPain(uint8_t* ped)`; el núcleo ejecuta el plan cada fotograma y lo limpia en `Release`.

- [ ] **Paso 1: Pruebas puras en rojo** — `test_body_reactions.cpp`, con `BodyReactions r;` y banderas de `protocol.h`:
  - sin banderas: `Update` da `None` y no dice nada, indefinidamente;
  - `kFlagFalling` sube: `Fall` una vez; con las banderas igual, `None` (no se repite); baja `kFlagFalling`: `Clear`;
  - `kFlagKnocked` sube (con o sin `Falling` antes): `Down` una vez; baja: `GetUp`, y pasada la duración de `GETUP_MS` (1500 ms de reloj) `Clear`;
  - `kFlagDead` sube: `Dead` una vez, sin importar lo demás; sigue mientras la bandera dure; baja: `Clear`;
  - muerto mientras estaba derribado o cayendo: `Dead` directo, sin `GetUp`;
  - la voz: un cambio del contador (también la vuelta de 15 a 0) da `say` una vez; sin cambio no; un segundo cambio antes de `PAIN_COOLDOWN_MS` (1500 ms) no suena y el tercero, ya pasado el enfriamiento, sí; el primer estado visto no cuenta como cambio; al pasar a `Dead` el contador que cambia a la vez no suena;
  - `Reset()` olvida todo (la siguiente bandera de `Falling` vuelve a dar `Fall`, el primer contador no suena);
  - un reloj que retrocede no deja una voz bloqueada para siempre.

- [ ] **Paso 2: Verlas fallar** — `bash tools/run_native_tests.sh 2>&1 | grep -E "error|CHECK failed" | head -3`. Expected: error de compilación (`BodyReactions` no existe).

- [ ] **Paso 3: Implementar la máquina pura** — `BodyReactions` en `body_reactions.h` con las constantes `kPainCooldownMs = 1500` y `kGetUpMs = 1500`, estados internos (`Normal`, `Falling`, `Down`, `GettingUp`, `Dead`) y la lógica de las pruebas; las banderas se leen con las constantes de `protocol.h`; solo `Update`, `Reset` y estado privado; sin llamadas al juego.

- [ ] **Paso 4: Pruebas del núcleo en rojo** — en `test_bridge_core.cpp`: `FakeIo` gana `std::vector<BodyAnim> bodyAnims; int pains = 0; int clears = 0;` con `PlayBodyAnim`, `SayPain` y `ClearBodyAnim` que los apuntan; casos: un `State` con `kFlagFalling` en `flags` produce `Fall` en la pasada del fotograma y ninguna más mientras siga; con `kFlagKnocked`, `Down`, y al bajar, `GetUp`; con `kFlagDead`, `Dead`; el contador que cambia da una voz; con `Release` por 1 s sin estado (`io.now += Puppet::kTimeoutMs + 1`) con una animación puesta, `ClearBodyAnim` se llama una vez; con el ped en vehículo (`PedInVehicle`) no se ejecuta nada; la animación nunca se pide sin estado engranado.

- [ ] **Paso 5: Núcleo y puente** — implementar en `bridge_core.h`: `BodyReactions body_;` y, cada fotograma engranado, `BodyPlan plan = body_.Update(state.flags, now)` con el último `State` recibido (guardar las banderas en el decodificado del paquete, como el resto) y `io.PlayBodyAnim/ClearBodyAnim/SayPain` según el plan; en `Release` y al cambiar de ped `ClearBodyAnim` si había una puesta, y `body_.Reset()`. En `bridge.cpp` (`WinIo`): `PlayBodyAnim` llama a `BlendAnimation` con los ids y parámetros de `docs/body-spike.md` sobre el clump `*(RpClump**)(ped + 0x18)` (con las comprobaciones de puntero nulo del resto del puente) y guarda la asociación devuelta; `ClearBodyAnim` la quita como dice el spike; `SayPain` llama a `CPed::Say(ctx, 0, 1.0f, true, true, false)` con el contexto elegido. Constantes nuevas en `ped_layout.h` con su origen (gta-reversed + medición en vivo). Añadir `0x4D4610` y `0x5EFFE0` a `tools/verify_exe_addresses.py` con sus primeros bytes medidos del exe de la instalación.

- [ ] **Paso 6: Verlas pasar y mutar** — `bash tools/run_native_tests.sh 2>&1 | tail -9` y `python3 tools/verify_exe_addresses.py 2>&1 | grep -c FAIL` (0). Mutaciones nativas: no volver a pedir `Fall` mientras siga la bandera (quitar la memoria del estado); `GetUp` sin esperar `kGetUpMs`; permitir `GetUp` desde `Dead`; que el contador no cuente la vuelta 15 a 0; quitar el enfriamiento; no resetear en `Reset`; no llamar a `ClearBodyAnim` en `Release`; ejecutar el plan con el ped en vehículo. Cada una debe fallar un `CHECK`.

- [ ] **Paso 7: Comprobación en vivo con la sonda** — `bash tools/install_bridge.sh`, reiniciar GTA y, con Minecraft cerrado: `python3 tools/body_probe.py fall`, `down`, `dead` y `hurt`; el usuario mira la ventana de GTA: caída libre con su animación, caer al suelo, tumbado, levantarse, muerto y la voz de dolor con enfriamiento; tras cada fase, CJ vuelve a su postura; y la partida de GTA intacta (vida y estado).

- [ ] **Paso 8: Commit**

```bash
git add -A && git commit -m "Body reactions: the bridge plays GTA's fall, ground and death animations on CJ and gives him a pain voice

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Tarea 6: Verificación en vivo de punta a punta, resultados y revisión final

**Archivos:**
- Crear: `docs/body-results.md`; posiblemente ajustes de las constantes.

- [ ] **Paso 1: Todo en verde antes de probar**

```bash
cd . && (cd protocol && python3 -B -m unittest test_cjproto 2>&1 | tail -1) && bash tools/run_native_tests.sh 2>&1 | tail -8 && (cd tools && rm -rf tests/__pycache__ cjconvert/__pycache__ && python3 -B -m unittest discover -s tests 2>&1 | tail -3) && (cd cj-fabric && ./gradlew test --rerun-tasks --console=plain 2>&1 | grep -E "FAILED|BUILD") && python3 tools/verify_exe_addresses.py 2>&1 | grep -c FAIL
```
Expected: todo `OK` / `BUILD SUCCESSFUL` y `0`.

- [ ] **Paso 2: Abrir los juegos** — `bash tools/install_bridge.sh` y `tools/start_games.sh --restart > /tmp/cj-start.log 2>&1`; el usuario carga su partida y entra a un mundo (creativo o supervivencia con `/gamemode survival`).

- [ ] **Paso 3: Comprobaciones en vivo** (el usuario mira y confirma; anotar cada una):
  1. **Dolor:** recibir daño de un zombi, una flecha, fuego y una caída: CJ suelta una frase de dolor; seguidas, no se repite más de una vez cada ~1,5 s; sin daño, no suena.
  2. **Caída libre:** desde unos 5 y 10 bloques, CJ pone la animación de caída de GTA hasta tocar el suelo; saltos normales y bajar escalones no la activan; caer al agua tampoco.
  3. **Suelo:** un aterrizaje que hace daño (unos 10 bloques) o una explosión (`/summon tnt` o el cohete) derriba: CJ cae al suelo, se queda unos segundos y se levanta; el jugador está inmovilizado esos ~4 s y recupera el control; golpear o disparar derribado no funciona.
  4. **Muerte:** morir (`/kill`) deja a CJ tumbado hasta reaparecer; al reaparecer vuelve a su postura normal.
  5. **Seguridad de GTA:** tras cerrar Minecraft, el CJ de la partida de GTA tiene su vida y su estado de antes y se mueve normal.
  6. **Casos raros:** montado, nadando, en una escalera y volando en creativo no hay reacciones de suelo; sin GTA, nada cambia y el jugador no queda inmovilizado; dos derribos seguidos no se encadenan.
  7. **Lo anterior intacto:** armas, puño, bate, efectos de F5.
  Ajustar los números (umbrales, tiempos, enfriamiento, contexto de la voz) si el usuario lo pide y anotarlos.

- [ ] **Paso 4: `docs/body-results.md`** — en español, como los anteriores: pruebas automáticas, el spike, cada comprobación del Paso 3 con lo que se vio, valores finales, problemas y cómo se resolvieron, decisiones y límites (sin ragdoll con física, sin trepar ni nadar, sin ropa). Commit:

```bash
git add -A && git commit -m "Body reactions: results

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

- [ ] **Paso 5: Revisión final independiente** — subagente revisor (opus, solo lectura) sobre el rango de commits (`git log --oneline` desde el commit de la spec de las reacciones del cuerpo hasta `HEAD`), con el *Review Focus* de este plan. Pedir: bugs de corrección (máquina de estados, contador que da la vuelta, temporizador y reloj, bits de las banderas, lecturas de memoria de `BlendAnimation`/`Say`, restauración al soltar), seguridad de la partida de GTA (¿alguna ruta puede dejar a CJ inmovilizado, herido o con animación pegada?), falsos disparadores, el vigilante de la inmovilización, el mixin de entrada (¿interfiere con el agachado forzado, el salto bloqueado, el vuelo?), pruebas tautológicas, discrepancias entre spec, plan y código, restos de experimentos y efectos sobre las armas, el puño y el bate. Informe por gravedad con `archivo:línea`, escenario concreto y confianza.

- [ ] **Paso 6: Una pasada de arreglos** — clasificar por efecto real, arreglar lo importante con prueba en rojo primero (y commit), anotar en `docs/body-results.md` lo aplazado, pedir al usuario que repita en vivo lo que se haya tocado, y repetir el Paso 1.
