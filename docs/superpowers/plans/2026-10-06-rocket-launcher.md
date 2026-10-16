# Lanzacohetes con explosiones: plan de implementación

> **Para agentes:** SUB-HABILIDAD OBLIGATORIA: usar superpowers:executing-plans (el usuario elige siempre la ejecución nativa) o superpowers:subagent-driven-development para ejecutar este plan tarea a tarea. Los pasos usan casillas (`- [ ]`).

**Objetivo:** que CJ lleve y dispare el lanzacohetes RPG de GTA SA (arma 35) con su modelo real, y que cada disparo lance un cohete real de Minecraft que vuela, choca y explota (potencia 6) por las reglas del servidor.

**Arquitectura:** el RPG entra como octava arma por el camino de la minigun (catálogo, puente, conversor, ítem). El catálogo gana dos campos (`explosionPower`, `projectileSpeed`); `ShotResolver` ramifica: si el arma tiene `projectileSpeed > 0` crea una entidad `RocketEntity` (hereda de `ProjectileEntity`) en vez de lanzar rayos. La entidad avanza por tick con una lógica pura (`RocketFlight`), choca con lo más cercano y llama a `createExplosion` con el jugador como atacante. El cliente la registra con un renderizador vacío; la estela son partículas. El cohete propio de GTA se investiga en vivo (Tarea 2) y, si estorba, el puente lo quita cada fotograma con `CProjectileInfo::RemoveAllProjectiles` (Tarea 3, condicional).

**Tecnologías:** Java 21 / Fabric 1.21.1 con Yarn (JUnit 5), C++ del puente (pruebas nativas), Python 3 (conversor), GTA SA 1.0 US bajo Wine.

**Spec:** `docs/superpowers/specs/2026-10-06-rocket-launcher-design.md`. Contexto: `docs/m3-results.md`, `docs/minigun-results.md`.

## Global Constraints

- Protocolo v2 sin cambios.
- El paquete `dev.cjmc.combat` no usa clases de cliente (el renderizador del cohete vive en `dev.cjmc`).
- Un arma sin modelo conocido en `WeaponModelId` no se da nunca; toda arma con modelo tiene hueco en `WeaponSlot` (invariante nativa de la minigun). El RPG: modelo **359**, hueco **7** (armas pesadas).
- Conversor: `rocketla.dff` de `gta3.img` es de un solo marco y una sola textura (`gun_rocket_launcher`), 554 vértices y 496 triángulos, 1,43 m de largo: lo admite el conversor sin cambios; solo se añade `"rocketla"` a `WEAPON_MODELS`. (`missile.dff`, 98 vértices, existe para un modelo futuro del cohete; fuera de este hito.)
- Las otras siete armas no cambian de comportamiento: `explosionPower` 0 y `projectileSpeed` 0.
- Validación del catálogo: `explosionPower` en [0, 20]; `projectileSpeed` en [0, 200]; `projectileSpeed > 0` exige `explosionPower > 0`; `damage` ≥ 0 y **solo** puede ser 0 si `explosionPower > 0` (el daño del RPG lo hace la explosión).
- Firmas de Yarn 1.21.1 comprobadas con `javap` (jar `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.1-net.fabricmc.yarn.1_21_1.1.21.1+build.3-v2/minecraft-merged-…jar`): `World.createExplosion(Entity, DamageSource, ExplosionBehavior, double, double, double, float, boolean, World.ExplosionSourceType)`; `World.ExplosionSourceType {NONE, BLOCK, MOB, TNT, TRIGGER}`; `DamageSources.explosion(Entity source, Entity attacker)`; `EntityType.Builder.create(EntityFactory, SpawnGroup)`, `.dimensions(float, float)`, `.maxTrackingRange(int)`, `.trackingTickInterval(int)`, `.disableSummon()`, `.disableSaving()`, `.build(String)`; `ProjectileEntity(EntityType, World)`, `setOwner(Entity)`, `getOwner()`, `tick()`, `onCollision(HitResult)`, `canHit(Entity)` (protegido), `initDataTracker(DataTracker.Builder)` (abstracto en `Entity`); `Entity.setNoGravity(boolean)`, `setPosition(double,double,double)`, `discard()`; `ServerWorld.getEntitiesByType(TypeFilter<Entity,T>, Predicate<? super T>)`, `spawnEntity(Entity)`; `EntityRenderer<T>(EntityRendererFactory.Context)`, `render(T, float, float, MatrixStack, VertexConsumerProvider, int)`, `getTexture(T)` abstracto.
- Convenciones del repo: TDD con el rojo observado, mutaciones comprobadas por el arnés (el fallo debe ser de la prueba, no de compilación), commits con `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`, comentarios en inglés en el código, documentos en español.
- Comandos: Python `cd tools && python3 -B -m unittest discover -s tests`; nativas `bash tools/run_native_tests.sh`; Java `cd cj-fabric && ./gradlew test --console=plain`. Arnés de mutaciones Java `/tmp/claude-1000/mutj.py <json>` con entradas `[fichero relativo a cj-fabric/src/main/java/dev/cjmc/, original, mutante]` (si no existe, recrearlo: escribe el mutante, ejecuta `./gradlew test`, restaura y exige fallo de prueba, no de compilación).
- Cerrar solo el proceso Java de Minecraft por PID (leer `/proc/<pid>/cmdline` buscando `KnotClient`); nunca `pkill -f` ni `pgrep -f` con una cadena que contenga el propio comando.

## Review Focus

1. **Cohete que nace dentro de un bloque o contra el propio jugador:** no explota en la cara del jugador al salir; ignora al dueño hasta salir de su caja y no atraviesa un muro pegado. → Tarea 4 (`ProjectileEntity.canHit` + prueba de `RocketFlight`) y Tarea 5 (en vivo, disparar pegado a una pared).
2. **Alcance agotado en el aire:** el cohete explota en el último punto sin pasarse del alcance (último paso recortado). → Tarea 4 (`RocketFlight`).
3. **`weapons.json` con valores absurdos de explosión o proyectil** (potencia 0 con velocidad, potencia enorme, velocidad negativa o infinita, daño 0 sin explosión): se rechazan al cargar. → Tarea 1.
4. **Mundo o jugador que desaparecen con cohetes en vuelo; avalancha de cohetes:** no explotan ni quedan entidades colgadas; un tope de 8 por jugador. → Tarea 4.
5. **Regla de explosiones del mundo (`mobGriefing`):** con la regla apagada el cohete explota sin romper bloques y sigue dañando a los mobs. → Tarea 5 (en vivo).

---

### Tarea 1: El RPG como octava arma (catálogo, puente, conversor, ítem)

**Archivos:**
- Modificar: `cj-fabric/src/main/java/dev/cjmc/WeaponCatalog.java`, `cj-fabric/src/main/resources/weapons.json`, `cj-fabric/src/main/resources/assets/cj_fabric/lang/en_us.json`
- Crear: `cj-fabric/src/main/resources/assets/cj_fabric/models/item/rocket_launcher.json`
- Modificar: `cj-fabric/src/test/java/dev/cjmc/WeaponCatalogTest.java`, `cj-fabric/src/test/java/dev/cjmc/MovementRulesTest.java`
- Modificar: `gta-bridge/src/weapon_driver.h`, `gta-bridge/tests/test_weapon_driver.cpp`
- Modificar: `tools/cjconvert/weapons.py`, `tools/tests/test_weapons.py`, `tools/tests/test_real_weapons.py`

**Interfaces:**
- Consume: `WeaponCatalog.WeaponDef` (último componente `canJump`), `WeaponModelId`, `WeaponSlot`, `WEAPON_MODELS`.
- Produce: `WeaponDef` gana `float explosionPower, float projectileSpeed` (últimos componentes, tras `canJump`); `weapons.json` gana ambos campos en las ocho armas y la entrada `rocket_launcher`; `WeaponModelId(35) == 359`, `WeaponSlot(35) == 7`; `WEAPON_MODELS` gana `"rocketla"`.

- [ ] **Paso 1: Pruebas en rojo**

  1. `WeaponCatalogTest.java`:
     - En las constantes `WEAPON` y `OTHER`, añadir al final del objeto JSON `,\"explosionPower\":0,\"projectileSpeed\":0` (tras `\"canJump\":true`).
     - Renombrar `theShippedCatalogHasTheSevenWeapons` a `theShippedCatalogHasTheEightWeapons`, cambiar `assertEquals(7, c.all().size());` por `8`, y añadir `"rocket_launcher"` a las listas de `sprintHeld/sprintAiming` falsos (`chromegun, ak47, m4, minigun`), de `aimSpeed < 0.4f` y de `moveWhileFiring` falso. Añadir antes del último `assertEquals(22, ...)`:

```java
        // the rocket launcher: a heavy weapon whose explosion does the damage
        WeaponCatalog.WeaponDef rocket = c.byId("rocket_launcher");
        assertEquals(35, rocket.gta());
        assertEquals("rocketla", rocket.model());
        assertEquals(6f, rocket.explosionPower());
        assertTrue(rocket.projectileSpeed() > 0f);
        assertEquals(0f, rocket.damage());
        assertTrue(rocket.canJump());
        for (WeaponCatalog.WeaponDef d : c.all()) {
            if (!d.id().equals("rocket_launcher")) {
                assertEquals(0f, d.explosionPower(), d.id());
                assertEquals(0f, d.projectileSpeed(), d.id());
            }
        }
```
     - Añadir las pruebas (dentro de la clase):

```java
    @Test
    void rejectsAbsurdExplosionAndProjectileValues() {
        rejects(one().replace(",\"explosionPower\":0", ""));
        rejects(one().replace(",\"projectileSpeed\":0", ""));
        rejects(one().replace("\"explosionPower\":0", "\"explosionPower\":-1"));
        rejects(one().replace("\"explosionPower\":0", "\"explosionPower\":25"));
        rejects(one().replace("\"projectileSpeed\":0", "\"projectileSpeed\":-5"));
        rejects(one().replace("\"projectileSpeed\":0", "\"projectileSpeed\":500"));
        rejects(one().replace("\"projectileSpeed\":0", "\"projectileSpeed\":40")); // a projectile with no explosion does nothing
    }

    @Test
    void aWeaponWithAnExplosionMayHaveNoDamageButAnyOtherMust() {
        String rocket = one().replace("\"damage\":6", "\"damage\":0").replace("\"explosionPower\":0", "\"explosionPower\":6")
                .replace("\"projectileSpeed\":0", "\"projectileSpeed\":40");
        WeaponCatalog.WeaponDef d = WeaponCatalog.parse(rocket).byId("colt45");
        assertEquals(0f, d.damage());
        assertEquals(6f, d.explosionPower());
        assertEquals(40f, d.projectileSpeed());
        rejects(one().replace("\"damage\":6", "\"damage\":0")); // no explosion, no damage: nothing would happen
    }
```
  2. `MovementRulesTest.java`: añadir `"rocket_launcher"` a la lista de seis de `everyOtherWeaponAndNothingInHandCanJump`, y a la de cuatro de `theOtherFourStandStillWhileFiring` (que pasa a cinco armas; renombrarla `theHeavyWeaponsStandStillWhileFiring`).
  3. `gta-bridge/tests/test_weapon_driver.cpp`: tras `CHECK(WeaponModelId(38) == 362);  // minigun` añadir `CHECK(WeaponModelId(35) == 359);  // rocket launcher`; y en la lista de `WeaponSlot` tras la línea del hueco 7 de la minigun añadir `CHECK(WeaponSlot(35) == 7);` (la invariante «toda arma con modelo tiene hueco» ya cubre el resto).
  4. `tools/tests/test_weapons.py`: la prueba `test_the_weapon_list_is_the_six_of_m2_and_the_minigun` pasa a `test_the_weapon_list_is_the_six_of_m2_the_minigun_and_the_rocket_launcher` con `("colt45", "desert_eagle", "chromegun", "micro_uzi", "ak47", "m4", "minigun", "rocketla")`.
  5. `tools/tests/test_real_weapons.py`: añadir

```python
    def test_the_rocket_launcher_is_one_mesh(self):
        model = assemble.load_weapon(GTA, "rocketla")
        self.assertEqual(len(model.parts), 1)
        self.assertEqual((len(model.parts[0].positions), len(model.parts[0].triangles)), (554, 496))
        self.assertEqual(list(model.textures), ["rocketla:gun_rocket_launcher"])
```

- [ ] **Paso 2: Verlas fallar**

Run: `cd cj-fabric && ./gradlew test --console=plain 2>&1 | grep -E "error:|FAILED|BUILD" | head -4`; `bash tools/run_native_tests.sh 2>&1 | tail -4`; `cd tools && python3 -B -m unittest discover -s tests 2>&1 | tail -6`
Expected: Java no compila (`explosionPower()` y `projectileSpeed()` no existen); nativas fallan en `WeaponModelId(35)`; Python falla en la lista de `WEAPON_MODELS`.

- [ ] **Paso 3: Implementar**

  1. `WeaponCatalog.java`: `WeaponDef` añade al final `, float explosionPower, float projectileSpeed`; en `parse`, tras `bool(o, "canJump")` añadir `, number(o, "explosionPower"), number(o, "projectileSpeed")`; sustituir la comprobación de `damage` por:

```java
                boolean explosive = d.explosionPower() > 0f;
                if (!Float.isFinite(d.damage()) || d.damage() < 0f || (!explosive && !(d.damage() > 0f))) {
                    throw new IllegalArgumentException("weapon " + d.id() + " has the damage " + d.damage()
                            + ", which must be above 0 (0 only for a weapon whose explosion does the damage)");
                }
                if (!(d.explosionPower() >= 0f) || d.explosionPower() > 20f) {
                    throw new IllegalArgumentException("weapon " + d.id() + " has the explosionPower " + d.explosionPower() + ", which must be in [0, 20]");
                }
                if (!(d.projectileSpeed() >= 0f) || d.projectileSpeed() > 200f) {
                    throw new IllegalArgumentException("weapon " + d.id() + " has the projectileSpeed " + d.projectileSpeed() + ", which must be in [0, 200]");
                }
                if (d.projectileSpeed() > 0f && !explosive) {
                    throw new IllegalArgumentException("weapon " + d.id() + " fires a projectile but has no explosionPower, so it would do nothing");
                }
```
     (se sustituye el bloque `if (!(d.damage() > 0f) || !Float.isFinite(d.damage())) {…}` existente) y actualizar el docstring de la clase: `explosionPower` (potencia de la explosión de su proyectil; 0 = sin explosión) y `projectileSpeed` (bloques por segundo del proyectil; 0 = bala instantánea).
  2. `weapons.json`: añadir `, "explosionPower": 0, "projectileSpeed": 0` al final de cada una de las siete armas y la entrada nueva (con coma en la anterior):

```json
    {"id": "rocket_launcher", "name": "Rocket Launcher", "gta": 35, "model": "rocketla", "holdOffset": [0, 0, 0], "holdRotation": [0, 0, 0], "damage": 0, "range": 120, "pellets": 1, "spread": 0.01, "blockPower": 0, "aimSpeed": 0.29, "sprintHeld": false, "sprintAiming": false, "moveWhileFiring": false, "knockback": 0, "canJump": true, "explosionPower": 6, "projectileSpeed": 40}
```
  3. `lang/en_us.json`: `"item.cj_fabric.rocket_launcher": "Rocket Launcher"`; `models/item/rocket_launcher.json`: `{"parent": "minecraft:item/handheld", "textures": {"layer0": "cj_fabric:item/weapon_icon"}}`.
  4. `weapon_driver.h`: en `WeaponModelId` tras `case 33` añadir `    case 35: return 359; // Rocket launcher`; en `WeaponSlot` tras `case 38: return 7;` añadir `    case 35: return 7; // heavy weapons: the rocket launcher`.
  5. `tools/cjconvert/weapons.py`: `WEAPON_MODELS = ("colt45", "desert_eagle", "chromegun", "micro_uzi", "ak47", "m4", "minigun", "rocketla")`.

- [ ] **Paso 4: Verlas pasar**

Run: `cd cj-fabric && ./gradlew test --console=plain 2>&1 | grep -E "FAILED|BUILD"`; `bash tools/run_native_tests.sh 2>&1 | tail -8`; `cd tools && python3 -B -m unittest discover -s tests 2>&1 | tail -3`
Expected: `BUILD SUCCESSFUL`, 7 suites `OK`, Python `OK` (las pruebas de la instalación real se ejecutan, no se saltan).

- [ ] **Paso 5: Mutaciones** (una a una; cada una debe hacer fallar una prueba por aserción): en `WeaponCatalog.java` quitar la comprobación de `explosive` en `damage` (`(!explosive && !(d.damage() > 0f))` por `false`); cambiar `d.explosionPower() > 20f` por `> 200f`; cambiar `d.projectileSpeed() > 200f` por `> 2000f`; quitar `if (d.projectileSpeed() > 0f && !explosive)` (poner `false`); cambiar `d.damage() < 0f` por `false`; en `weapon_driver.h` cambiar `case 35: return 359;` por `360` y `case 35: return 7;` por `6`; en `weapons.json` cambiar `"explosionPower": 6` por `0` en el RPG. Restaurar cada una.

- [ ] **Paso 6: Commit**

```bash
git add -A && git commit -m "Rocket launcher: the eighth weapon (GTA 35, model 359) with explosionPower and projectileSpeed in the catalog

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Tarea 2: Investigación en vivo (agarre, apuntado, cohete de GTA, recarga)

**Archivos:**
- Crear: `docs/rocket-launcher-results.md` (se rellena en las Tareas 2 a 5)
- Posiblemente modificar: `cj-fabric/src/main/resources/weapons.json` (`holdOffset`/`holdRotation`, movimiento)

**Interfaces:** consume Tarea 1 y `tools/gun_probe.py` (ya existe: `python3 tools/gun_probe.py <arma> <segundos>` mide rondas por segundo y necesita Minecraft cerrado). Produce las conclusiones del spike que deciden la Tarea 3.

- [ ] **Paso 1: Todo en verde y modelo convertido**

```bash
cd . && (cd tools && python3 -B -m unittest discover -s tests 2>&1 | tail -2) && bash tools/run_native_tests.sh 2>&1 | tail -7 && python3 tools/convert_assets.py --gta "GTA San Andreas" --out cj-fabric/run/config/cj_fabric --weapons-only
```
Expected: `wrote .../weapons/rocketla.glb (N bytes)` y los otros siete sin error. Comprobar que los `.glb` de las demás armas no cambiaron (copiar la carpeta antes y comparar con `cmp`).

- [ ] **Paso 2: Instalar el puente y arrancar los dos juegos** — `bash tools/install_bridge.sh`; si GTA ya corre, cerrarlo por PID (`ps aux | grep "[g]ta_sa.exe"`) y abrirlo de nuevo (`wine gta_sa.exe` desde `GTA San Andreas/`, en segundo plano) para cargar el puente; `cd cj-fabric && ./gradlew runClient` (en segundo plano). Pedir al usuario que cargue la partida y entre a un mundo creativo y se dé `/give @s cj_fabric:rocket_launcher`.

- [ ] **Paso 3: Comprobaciones en vivo** (el usuario mira y responde; anotar cada una en `docs/rocket-launcher-results.md`):
  1. **Se da el arma y GTA no se cae:** CJ la tiene en la mano; `GTA San Andreas/cj_bridge.log` sin `has no known model`.
  2. **Agarre y aspecto:** bien sujeto con `holdOffset`/`holdRotation` a cero y con su textura; si no, calibrar con `/cj hold x y z rx ry rz` y anotar los seis valores en `weapons.json`.
  3. **Apuntar y disparar** (clic derecho apunta, izquierdo dispara): **comprobación clave del spike**: si GTA trata el RPG con otra tarea el apuntado fallará; anotar qué pasa y, si no apunta o no dispara, parar el plan y decidir el ajuste con el usuario.
  4. **El cohete propio de GTA:** el usuario mira la ventana de GTA al disparar: ¿sale un cohete?, ¿a dónde va?, ¿explota?, ¿le pasa algo al CJ marioneta (muere, sale despedido, se cae, se bloquea el apuntado) o hay un sonido de explosión? Anotar. **Decisión:** si el CJ marioneta sufre o la partida de GTA se estropea, hacer la Tarea 3; si solo hay un sonido de explosión de GTA sin consecuencias, ledger un `Ruling` «se deja el cohete de GTA» y saltar la Tarea 3.
  5. **Recarga y cadencia:** con Minecraft cerrado y GTA con partida: `python3 tools/gun_probe.py 35 12`. Anotar rondas por segundo, el evento de recarga y que la recarga no cuenta como disparo (el total de rondas coincide con los clics).
  6. **Cambio de arma y retirada:** pasar a un ítem de Minecraft o a la mano vacía: CJ suelta el arma y vuelve a su postura normal.

- [ ] **Paso 4: Documentar y commit** — empezar `docs/rocket-launcher-results.md` (en español) con lo observado (modelo, agarre, tarea de apuntado, cohete de GTA, recarga y cadencia). Commit:

```bash
git add -A && git commit -m "Rocket launcher: first live check (model in hand, aiming, GTA's own rocket, reload)

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Tarea 3 (condicional): Anular el cohete propio de GTA

**Solo si la Tarea 2, comprobación 4, mostró que el cohete de GTA estorba.** Si no, ledger `Task 3: skipped (GTA's rocket does no harm: <lo observado>)` y seguir con la Tarea 4.

**Archivos:**
- Modificar: `gta-bridge/src/ped_layout.h`, `gta-bridge/src/weapon_driver.h`, `gta-bridge/src/bridge_core.h`, `gta-bridge/src/bridge.cpp`
- Modificar: `gta-bridge/tests/test_bridge_core.cpp`, `gta-bridge/tests/test_weapon_driver.cpp`, `tools/verify_exe_addresses.py`

**Interfaces:**
- Consume: `BridgeIo`, `WeaponDriver`, `BridgeCore::Tick`.
- Produce: `gta::kRemoveAllProjectiles = 0x7399B0` (`CProjectileInfo::RemoveAllProjectiles`, estática, sin argumentos); `FiresProjectile(uint16_t weapon)` (verdadero para 35 y 36); `BridgeIo::RemoveProjectiles()`; el núcleo lo llama en cada `Tick` mientras el arma sostenida lanza proyectiles.

- [ ] **Paso 1: Pruebas en rojo**
  1. `test_weapon_driver.cpp`: `CHECK(FiresProjectile(35) && FiresProjectile(36)); CHECK(!FiresProjectile(22) && !FiresProjectile(38) && !FiresProjectile(0));`.
  2. `test_bridge_core.cpp`: añadir a `FakeIo` el contador `int removedProjectiles = 0;` y `void RemoveProjectiles() override { removedProjectiles++; }`; una prueba nueva con el patrón de las pruebas del puente existentes (mismo montaje que las de armas: un estado con arma 35 y puppet activo): tras varios `Tick` con `HeldWeapon == 35` el contador sube; con `HeldWeapon == 22` no sube. Copiar el montaje de la prueba de armas más cercana del fichero.
  3. `tools/verify_exe_addresses.py`: tras la comprobación de `CPed::ClearWeapon(type)` añadir `("CProjectileInfo::RemoveAllProjectiles()", 0x7399B0, "53555657e9649fccffbfc891"),` (los bytes están medidos en el exe de la instalación; seguir el formato de las entradas vecinas).

- [ ] **Paso 2: Verlas fallar** — `bash tools/run_native_tests.sh 2>&1 | tail -6`. Expected: no compila (`FiresProjectile`, `RemoveProjectiles` no existen).

- [ ] **Paso 3: Implementar**
  1. `ped_layout.h`: `constexpr uintptr_t kRemoveAllProjectiles = 0x7399B0; // CProjectileInfo::RemoveAllProjectiles()  cdecl, no arguments`.
  2. `weapon_driver.h`: `inline bool FiresProjectile(uint16_t weapon) { return weapon == 35 || weapon == 36; } // rocket launchers: the mod makes their rocket, GTA's own is removed`.
  3. `bridge_core.h`: añadir `virtual void RemoveProjectiles() = 0;  // remove every projectile GTA has in flight` a `BridgeIo` y, en `Tick`, donde ya se lee el arma sostenida y se cuentan los disparos, justo después de contar rondas: `if (FiresProjectile(held)) { io.RemoveProjectiles(); }` (usar el nombre de variable local real de esa función).
  4. `bridge.cpp`: `void RemoveProjectiles() override { reinterpret_cast<void(__cdecl*)()>(gta::kRemoveAllProjectiles)(); }` junto a los demás métodos de `WinIo`.

- [ ] **Paso 4: Verlas pasar** — `bash tools/run_native_tests.sh 2>&1 | tail -8` (7 suites OK) y `python3 tools/verify_exe_addresses.py` (todas `OK`).

- [ ] **Paso 5: Mutaciones**: en `bridge_core.h` quitar la llamada (`if (false)`), cambiar `FiresProjectile(held)` por `true`; en `weapon_driver.h` cambiar `weapon == 35 || weapon == 36` por `weapon == 35`. Cada una debe fallar una prueba. Restaurar.

- [ ] **Paso 6: Comprobación en vivo** — `bash tools/install_bridge.sh`, reiniciar GTA, y repetir con el usuario la comprobación 4 de la Tarea 2: el cohete de GTA ya no sale ni explota y el CJ marioneta no sufre; la recarga y el cambio de arma siguen igual.

- [ ] **Paso 7: Commit**

```bash
git add -A && git commit -m "Rocket launcher: the bridge removes GTA's own rocket (CProjectileInfo::RemoveAllProjectiles) while a rocket launcher is held

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Tarea 4: El cohete de Minecraft (vuelo puro, entidad, resolución y cliente)

**Archivos:**
- Crear: `cj-fabric/src/main/java/dev/cjmc/combat/RocketFlight.java`, `RocketEntity.java`, `CjEntities.java`
- Crear: `cj-fabric/src/main/java/dev/cjmc/RocketRenderer.java`
- Crear: `cj-fabric/src/test/java/dev/cjmc/combat/RocketFlightTest.java`
- Modificar: `cj-fabric/src/main/java/dev/cjmc/combat/ShotResolver.java`, `CjCombat.java`, `cj-fabric/src/main/java/dev/cjmc/CjClient.java`

**Interfaces:**
- Consume (Tarea 1): `WeaponDef.{explosionPower, projectileSpeed, range, spread}`; (M3) `Spread.perturb`, `PelletRay.seed`, `ShotRequest`, `ShotResolver.resolveUnchecked`.
- Produce: `RocketFlight(double speedBlocksPerSecond, double maxRange)` con `double advance()` (distancia a avanzar este tick, recortada al alcance restante) y `boolean expired()`; `CjEntities.ROCKET` (`EntityType<RocketEntity>`) y `CjEntities.register()`; `RocketEntity.launch(ServerPlayerEntity owner, Vec3d from, Vec3d direction, double speed, double range, float power)`.

- [ ] **Paso 1: Prueba pura en rojo** — `RocketFlightTest.java`:

```java
package dev.cjmc.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RocketFlightTest {
    @Test
    void movesSpeedOverTwentyPerTickUntilTheRangeIsUsedUp() {
        RocketFlight f = new RocketFlight(40.0, 120.0); // 2 blocks per tick, 60 ticks
        double total = 0;
        int ticks = 0;
        while (!f.expired()) {
            double d = f.advance();
            assertEquals(2.0, d, 1e-9);
            total += d;
            ticks++;
            assertTrue(ticks <= 60);
        }
        assertEquals(60, ticks);
        assertEquals(120.0, total, 1e-9);
    }

    @Test
    void theLastStepIsTrimmedSoItNeverGoesPastTheRange() {
        RocketFlight f = new RocketFlight(40.0, 5.0); // 2, 2 and then 1
        assertEquals(2.0, f.advance(), 1e-9);
        assertFalse(f.expired());
        assertEquals(2.0, f.advance(), 1e-9);
        assertFalse(f.expired());
        assertEquals(1.0, f.advance(), 1e-9);
        assertTrue(f.expired());
    }

    @Test
    void anExpiredFlightDoesNotMoveAnyMore() {
        RocketFlight f = new RocketFlight(20.0, 1.0);
        assertEquals(1.0, f.advance(), 1e-9);
        assertTrue(f.expired());
        assertEquals(0.0, f.advance());
        assertTrue(f.expired());
    }

    @Test
    void unusableNumbersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RocketFlight(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new RocketFlight(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> new RocketFlight(Double.NaN, 10));
        assertThrows(IllegalArgumentException.class, () -> new RocketFlight(Double.POSITIVE_INFINITY, 10));
        assertThrows(IllegalArgumentException.class, () -> new RocketFlight(40, 0));
        assertThrows(IllegalArgumentException.class, () -> new RocketFlight(40, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new RocketFlight(40, -3));
    }
}
```

- [ ] **Paso 2: Verla fallar** — `cd cj-fabric && ./gradlew test --console=plain 2>&1 | grep -E "error:|FAILED|BUILD" | head -3`. Expected: error de compilación (`RocketFlight` no existe).

- [ ] **Paso 3: `RocketFlight.java`**

```java
package dev.cjmc.combat;

/** How far a rocket has flown: it moves speed/20 blocks per tick (the server runs 20 ticks a second) until its range is used up. */
public final class RocketFlight {
    private static final double EPS = 1e-9;

    private final double stepLength;
    private final double maxRange;
    private double travelled;

    public RocketFlight(double speedBlocksPerSecond, double maxRange) {
        if (!(speedBlocksPerSecond > 0) || Double.isInfinite(speedBlocksPerSecond) || !(maxRange > 0) || Double.isInfinite(maxRange)) {
            throw new IllegalArgumentException("a rocket needs a finite speed and range above 0, got " + speedBlocksPerSecond + " and " + maxRange);
        }
        this.stepLength = speedBlocksPerSecond / 20.0;
        this.maxRange = maxRange;
    }

    /** The distance to fly this tick: a whole step, or what is left of the range. Zero once the range is used up. */
    public double advance() {
        double step = Math.min(stepLength, maxRange - travelled);
        if (step <= EPS) {
            travelled = maxRange;
            return 0.0;
        }
        travelled += step;
        return step;
    }

    public boolean expired() {
        return travelled >= maxRange - EPS;
    }
}
```

- [ ] **Paso 4: Verla pasar y mutar** — `./gradlew test --console=plain 2>&1 | grep -E "FAILED|BUILD"` (`BUILD SUCCESSFUL`). Mutaciones con el arnés (`combat/RocketFlight.java`): `speedBlocksPerSecond / 20.0` → `/ 10.0`; `Math.min(stepLength, maxRange - travelled)` → `stepLength`; `step <= EPS` → `step < 0`; `travelled >= maxRange - EPS` → `travelled > maxRange`; `!(maxRange > 0)` → `false`; `!(speedBlocksPerSecond > 0)` → `false`; `Double.isInfinite(speedBlocksPerSecond)` → `false`. Todas deben morir (si alguna sobrevive por ser equivalente, simplificar el código o anotarla).

- [ ] **Paso 5: `CjEntities.java`**

```java
package dev.cjmc.combat;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** The entity types of the mod (registered on both sides, since the integrated server needs them too). */
public final class CjEntities {
    public static final String MOD_ID = "cj_fabric";
    public static EntityType<RocketEntity> ROCKET;

    private CjEntities() {}

    public static void register() {
        Identifier id = Identifier.of(MOD_ID, "rocket");
        ROCKET = Registry.register(Registries.ENTITY_TYPE, id,
                EntityType.Builder.<RocketEntity>create(RocketEntity::new, SpawnGroup.MISC)
                        .dimensions(0.3f, 0.3f).maxTrackingRange(16).trackingTickInterval(1)
                        .disableSummon().disableSaving().build(id.toString()));
    }
}
```

- [ ] **Paso 6: `RocketEntity.java`**

```java
package dev.cjmc.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A rocket in flight: it moves straight with no gravity, trails smoke and flames, and explodes where it hits a block or an entity
 * (the shooter is ignored until the rocket has left their box) or where its range runs out. Invisible: the trail is its look.
 */
public final class RocketEntity extends ProjectileEntity {
    private RocketFlight flight; // server only
    private float power;

    public RocketEntity(EntityType<? extends RocketEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    public void launch(ServerPlayerEntity owner, Vec3d from, Vec3d direction, double speed, double range, float power) {
        setOwner(owner);
        setPosition(from.x, from.y, from.z);
        setVelocity(direction.normalize().multiply(speed / 20.0));
        this.flight = new RocketFlight(speed, range);
        this.power = power;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {}

    @Override
    public void tick() {
        World world = getWorld();
        if (world.isClient) {
            setPosition(getPos().add(getVelocity())); // the server's position updates correct it
            world.addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
            world.addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
            return;
        }
        if (flight == null) {
            discard(); // not launched by a shot: nothing to fly
            return;
        }
        super.tick(); // the collision check of this tick: onCollision explodes and discards
        if (isRemoved()) {
            return;
        }
        double step = flight.advance();
        Vec3d velocity = getVelocity();
        if (step > 0 && velocity.lengthSquared() > 0) {
            setPosition(getPos().add(velocity.normalize().multiply(step)));
        }
        if (flight.expired()) {
            explode();
        }
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (getWorld() instanceof ServerWorld) {
            explode();
        }
    }

    private void explode() {
        if (isRemoved()) {
            return;
        }
        World world = getWorld();
        Entity owner = getOwner();
        // MOB: the source type whose block damage follows the world's mobGriefing rule (switched off, the rocket still hurts but breaks nothing)
        world.createExplosion(this, world.getDamageSources().explosion(this, owner), null, getX(), getY(), getZ(), power, false,
                World.ExplosionSourceType.MOB);
        discard();
    }
}
```

- [ ] **Paso 7: `ShotResolver`** — en `resolveUnchecked`, justo después de `ServerWorld world = player.getServerWorld();` (y antes de `double length = ...`) añadir:

```java
        if (def.projectileSpeed() > 0f) {
            launchRocket(world, player, def, request);
            return;
        }
```
  y añadir el método y la constante (imports: `net.minecraft.util.TypeFilter`):

```java
    private static final int MAX_ROCKETS_PER_PLAYER = 8;

    private static void launchRocket(ServerWorld world, ServerPlayerEntity player, WeaponCatalog.WeaponDef def, ShotRequest request) {
        if (CjEntities.ROCKET == null) {
            return;
        }
        long inFlight = world.getEntitiesByType(TypeFilter.instanceOf(RocketEntity.class), r -> r.getOwner() == player).size();
        if (inFlight >= MAX_ROCKETS_PER_PLAYER) {
            return; // a runaway shot counter does not flood the world with rockets
        }
        double[] dir = Spread.perturb(request.direction(), def.spread(), new Random(PelletRay.seed(request.seed(), 0)));
        Vec3d from = new Vec3d(request.origin()[0] + dir[0], request.origin()[1] + dir[1], request.origin()[2] + dir[2]); // a block ahead of the eyes
        RocketEntity rocket = new RocketEntity(CjEntities.ROCKET, world);
        rocket.launch(player, from, new Vec3d(dir[0], dir[1], dir[2]), def.projectileSpeed(), def.range(), def.explosionPower());
        world.spawnEntity(rocket);
    }
```
  (El alcance del cohete es el del catálogo, 120 bloques para el RPG, como dice la spec: el tope de 100 de `PelletRay` es solo de las balas instantáneas.)

- [ ] **Paso 8: Registro en los dos lados**
  1. `CjCombat.init`: como primera línea de `init(...)`, `CjEntities.register();`.
  2. `RocketRenderer.java` (en `dev.cjmc`, cliente):

```java
package dev.cjmc;

import dev.cjmc.combat.RocketEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** The rocket draws nothing itself: its look is the trail of particles its tick adds. */
final class RocketRenderer extends EntityRenderer<RocketEntity> {
    RocketRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(RocketEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {}

    @Override
    public Identifier getTexture(RocketEntity entity) {
        return Identifier.of("minecraft", "textures/entity/arrow.png"); // never drawn
    }
}
```
  3. `CjClient.onInitializeClient`: al principio, `net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(dev.cjmc.combat.CjEntities.ROCKET, RocketRenderer::new);` (si `EntityRendererRegistry.register` no existe con esa firma en la versión de Fabric API del proyecto, usar `net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry` con `javap` sobre el jar de Fabric API y ajustar sin cambiar el comportamiento).

- [ ] **Paso 9: Compilar y probar**

Run: `cd cj-fabric && ./gradlew build --console=plain 2>&1 | grep -E "error:|warning:|FAILED|BUILD"`
Expected: `BUILD SUCCESSFUL`. Si algún nombre de Yarn no existe tal como está escrito, ajustarlo con `javap` sin cambiar el comportamiento (las firmas de las Restricciones globales están comprobadas).

- [ ] **Paso 10: Comprobar el arranque** — `./gradlew runClient` (en segundo plano), esperar a `bridge ready` en `cj-fabric/run/logs/latest.log` y comprobar que no hay `InvalidInjection`, errores de `weapons.json` ni de registro de entidad (`Registry`, `EntityType`). Cerrar el cliente por PID.

- [ ] **Paso 11: Commit**

```bash
git add -A && git commit -m "Rocket launcher: the rocket is a real entity that flies, trails smoke and explodes with the shooter as attacker

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Tarea 5: Verificación en vivo, resultados y revisión final

**Archivos:**
- Modificar: `docs/rocket-launcher-results.md` (completo); posiblemente `weapons.json`.

- [ ] **Paso 1: Todo en verde antes de probar**

```bash
cd . && (cd protocol && python3 -B -m unittest test_cjproto 2>&1 | tail -1) && bash tools/run_native_tests.sh 2>&1 | tail -7 && (cd tools && rm -rf tests/__pycache__ cjconvert/__pycache__ && python3 -B -m unittest discover -s tests 2>&1 | tail -3) && (cd cj-fabric && ./gradlew test --rerun-tasks --console=plain 2>&1 | grep -E "FAILED|BUILD")
```
Expected: todo `OK` / `BUILD SUCCESSFUL`.

- [ ] **Paso 2: Arrancar los dos juegos** como en la Tarea 2 (un solo cliente de Minecraft; reinstalar el puente si cambió y reiniciar GTA). Avisar al usuario de que **pruebe lejos de construcciones**: la potencia 6 abre un cráter grande.

- [ ] **Paso 3: Comprobaciones en vivo** (el usuario mira y confirma; anotar cada una):
  1. **El cohete:** clic derecho apunta, izquierdo dispara: sale un cohete con estela de humo y llamas, vuela recto y explota contra suelo, pared, aire (al final del alcance) y mob; la recarga es la de GTA.
  2. **La explosión:** cráter, drops, daño a mobs (zombi, cerdo, creeper), empuje; daño propio estando cerca; sonido y partículas de Minecraft.
  3. **Disparar pegado a una pared o al suelo** (Review Focus 1): explota contra la pared sin atravesarla y el jugador solo se daña de la explosión, no por el nacimiento del cohete.
  4. **Regla del mundo** (Review Focus 5): `/gamerule mobGriefing false`: el cohete explota y daña mobs pero no rompe bloques; restaurar `true` después.
  5. **Cohetes en vuelo al cambiar de arma, cerrar el mundo o morir:** no quedan cohetes colgados ni explosiones fantasma; GTA no se cierra.
  6. **El cohete de GTA** (según la Tarea 2/3): no estorba.
  7. **Las otras siete armas** conservan su comportamiento (balas, daño, grietas).
  Ajustar `explosionPower`, `projectileSpeed` o el movimiento en `weapons.json` si el usuario lo pide y anotar los valores.

- [ ] **Paso 4: `docs/rocket-launcher-results.md`** — en español, como los anteriores: pruebas automáticas (recuentos), el spike (tarea de apuntado, cohete de GTA y si se anuló, cadencia y recarga), cada comprobación del Paso 3 con lo que se vio, valores finales de la entrada `rocket_launcher`, problemas encontrados y cómo se resolvieron, y los límites para después (el cohete no tiene modelo 3D —`missile.dff` queda para eso—, sin RPG con búsqueda de calor, lanzallamas ni granadas, sin fuego). Commit:

```bash
git add -A && git commit -m "Rocket launcher: results

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

- [ ] **Paso 5: Revisión final independiente** — lanzar un subagente revisor (opus, solo lectura) sobre el rango de commits del lanzacohetes (`git log --oneline` desde el commit de la spec del lanzacohetes hasta `HEAD`), con el *Review Focus* de este plan. Pedir: bugs de corrección (ciclo de vida de `RocketEntity` en servidor y cliente, doble explosión, `ProjectileEntity.tick`/`onCollision`, dueño ignorado, descarga de mundo, tope por jugador, registro de la entidad, renderizador, sincronización), pruebas tautológicas, discrepancias entre spec, plan y código, restos de experimentos (volcados `hs_err_pid*.log`, ficheros temporales) y efectos sobre las otras siete armas. Informe por gravedad con `archivo:línea`, escenario concreto y confianza.

- [ ] **Paso 6: Una pasada de arreglos** — clasificar por efecto real, arreglar lo importante con prueba en rojo primero (y commit), anotar en `docs/rocket-launcher-results.md` lo aplazado, pedir al usuario que repita en vivo lo que se haya tocado, y repetir el Paso 1.
