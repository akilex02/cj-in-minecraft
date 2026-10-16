# Munición y recetas Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Armas con munición en survival (3 ítems de balas), recetas para todas las armas, el bate, el jetpack y la munición, y un sonido de arma vacía.

**Architecture:** El disparo lo hace GTA a través del botón de fuego (`INTENT_FIRE`): el cliente lo quita antes de mandarlo si no hay balas. El servidor (que es la autoridad del inventario) gasta 1 bala por ronda en `ShotResolver`. Las reglas viven en una clase pura `AmmoRules`. Las recetas son JSON de datos.

**Tech Stack:** Java 21, Fabric (Minecraft 1.21.1, yarn), JUnit 5, Gradle (`cd cj-fabric && ./gradlew test`).

**Spec:** `docs/superpowers/specs/2026-10-10-ammo-and-recipes-design.md`

## Global Constraints

- Nombres en juego: `ammo1` = "Light Ammo", `ammo2` = "Heavy Ammo", `ammo3` = "Rocket Ammo".
- Munición: Colt 45 (`colt45`), Desert Eagle (`desert_eagle`), Micro SMG (`micro_uzi`) → `ammo1`; M4 (`m4`), AK-47 (`ak47`), escopeta (`chromegun`), minigun (`minigun`) → `ammo2`; lanzacohetes (`rocket_launcher`) → `ammo3`. Puños y bate: ninguna.
- Crafteo da 32 (`ammo1`), 16 (`ammo2`), 4 (`ammo3`).
- 1 bala por ronda en todas las armas (la escopeta 1 por disparo, no por perdigón).
- Creativo: dispara sin balas y sin gastar.
- Las texturas salen de `weapon-icons/ammo1.png`, `ammo2.png`, `ammo3.png`.
- Tablones de la escopeta: cualquiera (`#minecraft:planks`).
- Todo el código nuevo sigue el estilo del repo: ids y nombres en inglés, comentarios breves de una línea, sin dependencias nuevas.

## Review Focus

- Una ronda que GTA ya disparó cuando se acabó la última bala (latencia): no debe hacer daño ni gastar (lo cubre `AmmoInventory.take` devolviendo falso; se comprueba en el Task 7).
- Dos rondas en el mismo tick con 1 bala: solo una pasa, la otra no (el servidor decide ronda a ronda en `ShotResolver`; se comprueba en el Task 7).
- El jugador en espectador o muerto no consume (ya lo filtra `ShotResolver` antes de llegar al gasto).
- Pulsar el botón sin balas debe sonar una vez por pulsación, no en cada frame mientras se mantiene (`AmmoRulesTest.theDryClickSoundsOncePerPressNotWhileHeld`).
- Una pila de balas de varias casillas se gasta de forma continua (primero una pila, luego la siguiente).

---

### Task 1: `AmmoRules` (reglas puras)

**Files:**
- Create: `cj-fabric/src/main/java/dev/cjmc/AmmoRules.java`
- Test: `cj-fabric/src/test/java/dev/cjmc/AmmoRulesTest.java`

**Interfaces:**
- Produces:
  - `AmmoRules.LIGHT = "ammo1"`, `HEAVY = "ammo2"`, `ROCKET = "ammo3"`, `List<String> ALL`.
  - `boolean AmmoRules.isKnown(String ammo)` (verdadero para `""` y los tres ids).
  - `boolean AmmoRules.usesAmmo(String ammo)` (verdadero si no es `""`).
  - `boolean AmmoRules.canFire(String ammo, int available, boolean freeAmmo)`.
  - `int AmmoRules.cost(String ammo, boolean freeAmmo)` (0 o 1 por ronda).
  - `boolean AmmoRules.dryClick(boolean wasDown, boolean down, String ammo, int available, boolean freeAmmo)` (verdadero solo en el flanco de pulsación sin balas).

- [ ] **Step 1: Write the failing test**

```java
package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AmmoRulesTest {
    @Test
    void aWeaponWithoutAmmoAlwaysFires() {
        assertTrue(AmmoRules.canFire("", 0, false));
        assertEquals(0, AmmoRules.cost("", false));
    }

    @Test
    void survivalNeedsABullet() {
        assertFalse(AmmoRules.canFire(AmmoRules.LIGHT, 0, false));
        assertTrue(AmmoRules.canFire(AmmoRules.LIGHT, 1, false));
        assertEquals(1, AmmoRules.cost(AmmoRules.HEAVY, false));
    }

    @Test
    void creativeFiresForFreeAndSpendsNothing() {
        assertTrue(AmmoRules.canFire(AmmoRules.ROCKET, 0, true));
        assertEquals(0, AmmoRules.cost(AmmoRules.ROCKET, true));
    }

    @Test
    void knownIdsAreTheThreeAmmosAndTheEmptyString() {
        assertTrue(AmmoRules.isKnown(""));
        assertTrue(AmmoRules.isKnown("ammo1") && AmmoRules.isKnown("ammo2") && AmmoRules.isKnown("ammo3"));
        assertFalse(AmmoRules.isKnown("ammo4"));
        assertFalse(AmmoRules.usesAmmo(""));
        assertTrue(AmmoRules.usesAmmo("ammo2"));
    }

    @Test
    void theDryClickSoundsOncePerPressNotWhileHeld() {
        assertTrue(AmmoRules.dryClick(false, true, AmmoRules.LIGHT, 0, false));
        assertFalse(AmmoRules.dryClick(true, true, AmmoRules.LIGHT, 0, false)); // held
        assertFalse(AmmoRules.dryClick(false, false, AmmoRules.LIGHT, 0, false)); // not pressed
        assertFalse(AmmoRules.dryClick(false, true, AmmoRules.LIGHT, 5, false)); // has bullets
        assertFalse(AmmoRules.dryClick(false, true, AmmoRules.LIGHT, 0, true)); // creative
        assertFalse(AmmoRules.dryClick(false, true, "", 0, false)); // melee or fists
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd cj-fabric && ./gradlew test --tests dev.cjmc.AmmoRulesTest`
Expected: FAIL (compilation error: `AmmoRules` does not exist).

- [ ] **Step 3: Write minimal implementation**

```java
package dev.cjmc;

import java.util.List;

/** Who needs which bullets: the three kinds of ammo, and the rules of firing with and without them. Pure logic, no Minecraft. */
public final class AmmoRules {
    public static final String LIGHT = "ammo1";  // Colt 45, Desert Eagle, Micro SMG
    public static final String HEAVY = "ammo2";  // M4, AK-47, shotgun, minigun
    public static final String ROCKET = "ammo3"; // rocket launcher
    public static final List<String> ALL = List.of(LIGHT, HEAVY, ROCKET);

    private AmmoRules() {}

    /** The empty string means the weapon takes no ammo (fists, bat). */
    public static boolean isKnown(String ammo) {
        return ammo.isEmpty() || ALL.contains(ammo);
    }

    public static boolean usesAmmo(String ammo) {
        return !ammo.isEmpty();
    }

    /** Whether the weapon may fire: it takes no ammo, the player is in creative, or at least one bullet is in the inventory. */
    public static boolean canFire(String ammo, int available, boolean freeAmmo) {
        return !usesAmmo(ammo) || freeAmmo || available > 0;
    }

    /** The bullets a round spends: one, except in creative or for a weapon without ammo. */
    public static int cost(String ammo, boolean freeAmmo) {
        return usesAmmo(ammo) && !freeAmmo ? 1 : 0;
    }

    /** True on the tick the fire button goes down while the weapon is empty: the dry click sounds once per press. */
    public static boolean dryClick(boolean wasDown, boolean down, String ammo, int available, boolean freeAmmo) {
        return down && !wasDown && !canFire(ammo, available, freeAmmo);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd cj-fabric && ./gradlew test --tests dev.cjmc.AmmoRulesTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add cj-fabric/src/main/java/dev/cjmc/AmmoRules.java cj-fabric/src/test/java/dev/cjmc/AmmoRulesTest.java
git commit -m "Ammo: pure rules for the three kinds of bullets

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Campo `ammo` en el catálogo de armas

**Files:**
- Modify: `cj-fabric/src/main/java/dev/cjmc/WeaponCatalog.java` (record `WeaponDef`, `parse`)
- Modify: `cj-fabric/src/main/resources/weapons.json`
- Test: `cj-fabric/src/test/java/dev/cjmc/WeaponCatalogTest.java`

**Interfaces:**
- Consumes: `AmmoRules.isKnown(String)` (Task 1).
- Produces: `WeaponCatalog.WeaponDef.ammo()` → `String` (`""`, `"ammo1"`, `"ammo2"` o `"ammo3"`). El campo JSON `"ammo"` es opcional (ausente = `""`).

- [ ] **Step 1: Write the failing tests** (añadir dentro de `WeaponCatalogTest`)

```java
    @Test
    void theShippedWeaponsTakeTheAmmoOfTheSpec() {
        WeaponCatalog c = WeaponCatalog.load();
        for (String id : new String[] {"colt45", "desert_eagle", "micro_uzi"}) {
            assertEquals("ammo1", c.byId(id).ammo(), id);
        }
        for (String id : new String[] {"m4", "ak47", "chromegun", "minigun"}) {
            assertEquals("ammo2", c.byId(id).ammo(), id);
        }
        assertEquals("ammo3", c.byId("rocket_launcher").ammo());
        assertEquals("", c.byId("fists").ammo());
        assertEquals("", c.byId("baseball_bat").ammo());
    }

    @Test
    void theAmmoFieldIsOptionalAndDefaultsToNone() {
        assertEquals("", WeaponCatalog.parse(one()).byId("colt45").ammo());
    }

    @Test
    void anUnknownAmmoIsRejected() {
        rejects(json(WEAPON.replace("\"item\":true", "\"item\":true,\"ammo\":\"ammo9\"")));
    }

    @Test
    void aMeleeWeaponCannotTakeAmmo() {
        rejects(json(WEAPON.replace("\"reach\":0,\"coneHalfAngle\":0", "\"reach\":3,\"coneHalfAngle\":45")
                .replace("\"item\":true", "\"item\":true,\"ammo\":\"ammo1\"")));
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd cj-fabric && ./gradlew test --tests dev.cjmc.WeaponCatalogTest`
Expected: FAIL (compilation: `ammo()` does not exist).

- [ ] **Step 3: Implement**

En `WeaponCatalog.java`: añadir el componente `String ammo` al final del record:

```java
                            float reach, float coneHalfAngle, boolean item, String ammo) {}
```

En `parse`, al construir el `WeaponDef`, añadir como último argumento `optionalText(o, "ammo")` (después de `bool(o, "item")`), y tras la validación del `reach`, antes de `ids.add`:

```java
                if (!AmmoRules.isKnown(d.ammo())) {
                    throw new IllegalArgumentException("weapon " + d.id() + " has the ammo \"" + d.ammo() + "\", which is not one of " + AmmoRules.ALL);
                }
                if (d.reach() > 0f && AmmoRules.usesAmmo(d.ammo())) {
                    throw new IllegalArgumentException("weapon " + d.id() + " is a melee weapon and cannot take ammo");
                }
```

Y el helper junto a `text`:

```java
    /** A text that may be missing: the empty string then. */
    private static String optionalText(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null) {
            return "";
        }
        if (!e.isJsonPrimitive()) {
            throw new IllegalArgumentException("\"" + key + "\" must be a text");
        }
        return e.getAsString();
    }
```

En `weapons.json`, añadir `, "ammo": "<id>"` al final de cada arma (antes de `}`): `colt45`, `desert_eagle`, `micro_uzi` → `"ammo1"`; `ak47`, `m4`, `chromegun`, `minigun` → `"ammo2"`; `rocket_launcher` → `"ammo3"`; `fists` y `baseball_bat` → `""`.

- [ ] **Step 4: Run to verify it passes**

Run: `cd cj-fabric && ./gradlew test`
Expected: PASS (todo el conjunto: ningún otro test construye `WeaponDef` a mano).

- [ ] **Step 5: Commit**

```bash
git add cj-fabric/src/main/java/dev/cjmc/WeaponCatalog.java cj-fabric/src/main/resources/weapons.json cj-fabric/src/test/java/dev/cjmc/WeaponCatalogTest.java
git commit -m "Ammo: each weapon names the ammo it takes in weapons.json

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Ítems de munición (registro, modelo, textura, nombre)

**Files:**
- Modify: `cj-fabric/src/main/java/dev/cjmc/CjItems.java`
- Create: `cj-fabric/src/main/resources/assets/cj_fabric/models/item/ammo1.json`, `ammo2.json`, `ammo3.json`
- Create: `cj-fabric/src/main/resources/assets/cj_fabric/textures/item/gta/ammo1.png`, `ammo2.png`, `ammo3.png` (copia de `weapon-icons/`)
- Modify: `cj-fabric/src/main/resources/assets/cj_fabric/lang/en_us.json`
- Test: `cj-fabric/src/test/java/dev/cjmc/AmmoResourcesTest.java`

**Interfaces:**
- Consumes: `AmmoRules.ALL` (Task 1).
- Produces: `CjItems.AMMO` (`java.util.Map<String, Item>`, id → ítem registrado), `static Item CjItems.ammoItem(String id)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AmmoResourcesTest {
    private static final Map<String, String> NAMES = Map.of("ammo1", "Light Ammo", "ammo2", "Heavy Ammo", "ammo3", "Rocket Ammo");

    private static String read(String path) throws Exception {
        try (InputStream in = AmmoResourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void eachAmmoHasItsNameModelAndTexture() throws Exception {
        JsonObject lang = JsonParser.parseString(read("/assets/cj_fabric/lang/en_us.json")).getAsJsonObject();
        for (String id : AmmoRules.ALL) {
            assertEquals(NAMES.get(id), lang.get("item.cj_fabric." + id).getAsString(), id);
            JsonObject model = JsonParser.parseString(read("/assets/cj_fabric/models/item/" + id + ".json")).getAsJsonObject();
            assertEquals("cj_fabric:item/gta/" + id, model.getAsJsonObject("textures").get("layer0").getAsString(), id);
            assertNotNull(AmmoResourcesTest.class.getResourceAsStream("/assets/cj_fabric/textures/item/gta/" + id + ".png"), id);
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd cj-fabric && ./gradlew test --tests dev.cjmc.AmmoResourcesTest`
Expected: FAIL (the lang key does not exist: `NullPointerException`).

- [ ] **Step 3: Implement**

Copiar texturas y crear los modelos:

```bash
cd .
for n in 1 2 3; do
  cp weapon-icons/ammo$n.png cj-fabric/src/main/resources/assets/cj_fabric/textures/item/gta/ammo$n.png
  echo "{\"parent\": \"minecraft:item/generated\", \"textures\": {\"layer0\": \"cj_fabric:item/gta/ammo$n\"}}" \
    > cj-fabric/src/main/resources/assets/cj_fabric/models/item/ammo$n.json
done
```

En `en_us.json`, tras la línea de `item.cj_fabric.jetpack`, añadir:

```json
  "item.cj_fabric.ammo1": "Light Ammo",
  "item.cj_fabric.ammo2": "Heavy Ammo",
  "item.cj_fabric.ammo3": "Rocket Ammo",
```

En `CjItems.java`: imports `java.util.LinkedHashMap` y `java.util.Map`; campo:

```java
    public static final Map<String, Item> AMMO = new LinkedHashMap<>();
```

y al final de `register(...)`, tras el jetpack:

```java
        for (String id : AmmoRules.ALL) {
            Item ammo = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, id), new Item(new Item.Settings()));
            AMMO.put(id, ammo);
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(ammo));
        }
```

y el acceso:

```java
    /** The item of a kind of ammo (AmmoRules.LIGHT...), or null for an unknown id. */
    public static Item ammoItem(String id) {
        return AMMO.get(id);
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd cj-fabric && ./gradlew test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add cj-fabric/src/main/java/dev/cjmc/CjItems.java cj-fabric/src/main/resources/assets cj-fabric/src/test/java/dev/cjmc/AmmoResourcesTest.java
git commit -m "Ammo: Light, Heavy and Rocket Ammo items with their icons and names

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Las 13 recetas

**Files:**
- Create: `cj-fabric/src/main/resources/data/cj_fabric/recipe/<id>.json` para `ammo1`, `ammo2`, `ammo3`, `colt45`, `desert_eagle`, `micro_uzi`, `chromegun`, `ak47`, `m4`, `minigun`, `rocket_launcher`, `baseball_bat`, `jetpack`
- Test: `cj-fabric/src/test/java/dev/cjmc/RecipesTest.java`

**Interfaces:**
- Consumes: ítems registrados `cj_fabric:<id>` (Task 3 y los existentes).
- Produces: nada (datos).

- [ ] **Step 1: Write the failing test**

```java
package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecipesTest {
    private static final List<String> IDS = List.of("ammo1", "ammo2", "ammo3", "colt45", "desert_eagle", "micro_uzi", "chromegun",
            "ak47", "m4", "minigun", "rocket_launcher", "baseball_bat", "jetpack");
    private static final Map<String, Integer> COUNTS = Map.of("ammo1", 32, "ammo2", 16, "ammo3", 4);

    private static JsonObject recipe(String id) throws Exception {
        try (InputStream in = RecipesTest.class.getResourceAsStream("/data/cj_fabric/recipe/" + id + ".json")) {
            assertNotNull(in, "recipe " + id + " is missing");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void everyRecipeIsAWellFormedShapedRecipeForItsItem() throws Exception {
        for (String id : IDS) {
            JsonObject r = recipe(id);
            assertEquals("minecraft:crafting_shaped", r.get("type").getAsString(), id);
            JsonObject result = r.getAsJsonObject("result");
            assertEquals("cj_fabric:" + id, result.get("id").getAsString(), id);
            assertEquals(COUNTS.getOrDefault(id, 1), result.get("count").getAsInt(), id);
            JsonArray pattern = r.getAsJsonArray("pattern");
            assertEquals(3, pattern.size(), id);
            JsonObject key = r.getAsJsonObject("key");
            for (var row : pattern) {
                assertEquals(3, row.getAsString().length(), id + " row " + row);
                for (char c : row.getAsString().toCharArray()) {
                    assertTrue(c == ' ' || key.has(String.valueOf(c)), id + " uses the undefined key '" + c + "'");
                }
            }
            for (String k : key.keySet()) {
                assertTrue(pattern.toString().contains(k), id + " defines the unused key " + k);
            }
        }
    }

    @Test
    void theAmmoRecipesFollowTheNumpadLayoutOfTheUser() throws Exception {
        assertEquals(List.of("NNN", "NgN", "NrN"), rows("ammo1"));
        assertEquals(List.of("NgN", "NgN", "rNr"), rows("ammo2"));
        assertEquals(List.of("NfN", "NfN", "rgr"), rows("ammo3"));
    }

    @Test
    void theShotgunTakesAnyPlank() throws Exception {
        assertEquals("minecraft:planks", recipe("chromegun").getAsJsonObject("key").getAsJsonObject("P").get("tag").getAsString());
    }

    private static List<String> rows(String id) throws Exception {
        return recipe(id).getAsJsonArray("pattern").asList().stream().map(e -> e.getAsString()).toList();
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd cj-fabric && ./gradlew test --tests dev.cjmc.RecipesTest`
Expected: FAIL ("recipe ammo1 is missing").

- [ ] **Step 3: Generate the recipe files** (script de un solo uso en el scratchpad; solo se commitean los JSON)

Crear `$SCRATCH/gen_recipes.py` (con `$SCRATCH` el directorio scratchpad) y ejecutarlo desde la raíz del repo:

```python
import json, os

OUT = "cj-fabric/src/main/resources/data/cj_fabric/recipe"
ITEMS = {
    "N": "minecraft:iron_nugget", "I": "minecraft:iron_ingot", "B": "minecraft:iron_block",
    "G": "minecraft:gold_block", "R": "minecraft:redstone_block", "D": "minecraft:diamond",
    "X": "minecraft:diamond_block", "S": "minecraft:stick", "g": "minecraft:gunpowder",
    "r": "minecraft:redstone", "f": "minecraft:blaze_powder",
}
# id: (rows, count, category) - X is a diamond block, D a diamond, P any plank
RECIPES = {
    "ammo1": (["NNN", "NgN", "NrN"], 32, "equipment"),
    "ammo2": (["NgN", "NgN", "rNr"], 16, "equipment"),
    "ammo3": (["NfN", "NfN", "rgr"], 4, "equipment"),
    "colt45": (["IIG", " NI", "  R"], 1, "equipment"),
    "desert_eagle": (["BBG", " NB", "  R"], 1, "equipment"),
    "micro_uzi": (["IBI", " NG", "  R"], 1, "equipment"),
    "chromegun": (["GGB", " NN", " RP"], 1, "equipment"),
    "ak47": (["DGR", " NG", "  B"], 1, "equipment"),
    "m4": (["DBR", " NB", "  G"], 1, "equipment"),
    "minigun": ["  R", " GB", "XXB"],
    "rocket_launcher": (["XXG", "NGG", "  R"], 1, "equipment"),
    "baseball_bat": ([" IB", "ISI", "SI "], 1, "equipment"),
    "jetpack": (["BXB", "BGB", "fgf"], 1, "equipment"),
}
RECIPES["minigun"] = (RECIPES["minigun"], 1, "equipment")

os.makedirs(OUT, exist_ok=True)
for rid, (rows, count, category) in RECIPES.items():
    used = sorted({c for row in rows for c in row if c != " "})
    key = {}
    for c in used:
        key[c] = {"tag": "minecraft:planks"} if c == "P" else {"item": ITEMS[c]}
    doc = {"type": "minecraft:crafting_shaped", "category": category, "pattern": rows, "key": key,
           "result": {"id": "cj_fabric:" + rid, "count": count}}
    with open(os.path.join(OUT, rid + ".json"), "w") as f:
        json.dump(doc, f, indent=2)
        f.write("\n")
```

Run: `python3 $SCRATCH/gen_recipes.py && ls cj-fabric/src/main/resources/data/cj_fabric/recipe | wc -l` → `13`.

- [ ] **Step 4: Run to verify it passes**

Run: `cd cj-fabric && ./gradlew test`
Expected: PASS

- [ ] **Step 5: Compare the table with the user's images**

Abrir cada `recipes/<id>.png` (herramienta Read) y comparar casilla a casilla con `pattern`/`key` del JSON; corregir lo que difiera y repetir el Step 4. (Las imágenes de munición ya coinciden con el teclado numérico del usuario; las de armas las confirmó el usuario.)

- [ ] **Step 6: Commit**

```bash
git add cj-fabric/src/main/resources/data cj-fabric/src/test/java/dev/cjmc/RecipesTest.java
git commit -m "Recipes: ammo, every weapon, the bat and the jetpack

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Gasto de balas en el servidor

**Files:**
- Create: `cj-fabric/src/main/java/dev/cjmc/AmmoInventory.java`
- Modify: `cj-fabric/src/main/java/dev/cjmc/combat/ShotResolver.java` (`resolveUnchecked`, tras obtener `def`)

**Interfaces:**
- Consumes: `CjItems.ammoItem(String)` (Task 3), `AmmoRules.cost/canFire` (Task 1), `WeaponDef.ammo()` (Task 2).
- Produces:
  - `static int AmmoInventory.count(PlayerEntity player, String ammo)`: balas del tipo en todo el inventario (incluye mano secundaria).
  - `static boolean AmmoInventory.take(PlayerEntity player, String ammo, int n)`: quita `n` balas pasando de una pila a la siguiente; devuelve falso (sin quitar nada) si no hay suficientes.
  - `static boolean AmmoInventory.isFree(PlayerEntity player)`: `player.getAbilities().creativeMode`.

`AmmoInventory` depende de clases de Minecraft, así que no tiene test unitario (el repo prueba la lógica pura); la lógica de decisión ya está en `AmmoRules`. Se verifica en el Task 7.

- [ ] **Step 1: Write `AmmoInventory`**

```java
package dev.cjmc;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Counts and spends the bullets in a player's inventory. */
public final class AmmoInventory {
    private AmmoInventory() {}

    /** Creative takes no bullets and spends none. */
    public static boolean isFree(PlayerEntity player) {
        return player.getAbilities().creativeMode;
    }

    public static int count(PlayerEntity player, String ammo) {
        Item item = CjItems.ammoItem(ammo);
        if (item == null) {
            return 0;
        }
        int total = 0;
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isOf(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Takes n bullets, from one stack and then the next. Takes nothing and returns false when there are fewer than n. */
    public static boolean take(PlayerEntity player, String ammo, int n) {
        if (n <= 0) {
            return true;
        }
        if (count(player, ammo) < n) {
            return false;
        }
        Item item = CjItems.ammoItem(ammo);
        PlayerInventory inv = player.getInventory();
        int left = n;
        for (int i = 0; i < inv.size() && left > 0; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isOf(item)) {
                int used = Math.min(left, stack.getCount());
                stack.decrement(used);
                left -= used;
            }
        }
        return true;
    }
}
```

- [ ] **Step 2: Wire it into `ShotResolver.resolveUnchecked`**

Justo después de `if (def == null) { return; }`, antes del `if (def.reach() > 0f)`:

```java
        if (AmmoRules.usesAmmo(def.ammo()) && !AmmoInventory.take(player, def.ammo(), AmmoRules.cost(def.ammo(), AmmoInventory.isFree(player)))) {
            return; // the round GTA already fired when the last bullet went: it does no harm
        }
```

y el import `dev.cjmc.AmmoInventory; dev.cjmc.AmmoRules;` junto a `dev.cjmc.WeaponCatalog`. (`take` con coste 0 en creativo devuelve verdadero sin tocar nada.)

- [ ] **Step 3: Build and run all tests**

Run: `cd cj-fabric && ./gradlew build`
Expected: BUILD SUCCESSFUL (compila y pasan todos los tests).

- [ ] **Step 4: Commit**

```bash
git add cj-fabric/src/main/java/dev/cjmc/AmmoInventory.java cj-fabric/src/main/java/dev/cjmc/combat/ShotResolver.java
git commit -m "Ammo: the server spends one bullet per round and drops the rounds left over

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Quitar el botón de fuego sin balas y sonar el clic en seco

**Files:**
- Modify: `cj-fabric/src/main/java/dev/cjmc/CjClient.java` (`onFrame`, alrededor de las líneas 192-196)

**Interfaces:**
- Consumes: `AmmoInventory.count/isFree` (Task 5), `AmmoRules.canFire/dryClick` (Task 1), `WeaponDef.ammo()`.
- Produces: nada.

**Sobre el sonido.** Primero un spike de 15 minutos (parte de esta tarea): buscar en `gta-reversed-master/source/game_sa` (`Weapon.cpp`, `AEWeaponAudioEntity.cpp`, `AEPedWeaponAudioEntity.cpp`) un evento de audio de GTA para "arma vacía". En lo ya revisado no aparece ninguno para pistolas, subfusiles ni fusiles (`AE_WEAPON_FIRE_MINIGUN_NO_AMMO` es solo el sonido de la minigun al girar sin disparar). Regla de decisión:
- Si existe un evento claro y se puede lanzar con el mismo patrón que el jetpack (`call` a una dirección de GTA sobre la entidad de audio del ped, ver `bridge.cpp` alrededor de `kJetpackOn`), se anota en `docs/` y se implementa en un plan aparte (cambio de protocolo y del puente, que no se prueban sin GTA).
- Si no existe (lo más probable), se usa el sonido vanilla de Minecraft `SoundEvents.BLOCK_DISPENSER_FAIL` (un clic seco, sin assets) desde el cliente, que es lo que implementa este task. Se avisa al usuario del resultado del spike.

- [ ] **Step 1: Spike** — `grep -n -i "NO_AMMO\|EMPTY\|DRY" gta-reversed-master/source/game_sa/Audio/Entities/*.cpp gta-reversed-master/source/game_sa/Weapon.cpp` y anotar en el mensaje del commit qué se encontró.

- [ ] **Step 2: Implement** — en `CjClient.onFrame`, sustituir el cálculo de `intents` por:

```java
        WeaponCatalog.WeaponDef weapon = CjItems.activeWeapon(player);
        boolean melee = weapon != null && weapon.reach() > 0f;
        boolean attackDown = client.options.attackKey.isPressed();
        String ammo = weapon == null ? "" : weapon.ammo();
        int bullets = AmmoRules.usesAmmo(ammo) ? AmmoInventory.count(player, ammo) : 0;
        boolean free = AmmoInventory.isFree(player);
        if (client.currentScreen == null && AmmoRules.dryClick(wasAttackDown, attackDown, ammo, bullets, free)) {
            player.playSound(net.minecraft.sound.SoundEvents.BLOCK_DISPENSER_FAIL, 1.0f, 1.0f); // the click of an empty weapon, once per press
        }
        wasAttackDown = attackDown;
        int intents = ControlsMapper.intents(weapon != null && !melee, melee, client.currentScreen != null,
                attackDown && AmmoRules.canFire(ammo, bullets, free), client.options.useKey.isPressed(), BodyClient.frozen());
```

(eliminando las dos líneas antiguas `WeaponCatalog.WeaponDef weapon = ...` y `boolean melee = ...` que quedan duplicadas) y declarar junto a los demás campos de `CjClient`:

```java
    private boolean wasAttackDown; // the fire button on the last frame, so the dry click sounds once per press
```

Nota: `onFrame` retorna antes si no hay jugador o mundo, así que `wasAttackDown` solo se actualiza con partida en marcha. Si `weapon` es `null` o es melee, `ammo` es `""` y `dryClick` es falso.

- [ ] **Step 3: Build and run all tests**

Run: `cd cj-fabric && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add cj-fabric/src/main/java/dev/cjmc/CjClient.java
git commit -m "Ammo: no fire button without bullets, with the dry click once per press

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Verificación en juego y documentación

**Files:**
- Create: `docs/ammo-results.md`

- [ ] **Step 1: Build the mod and launch** (con GTA corriendo, según `docs/*-results.md` anteriores y la skill `universal-modder:game-automation`): `cd cj-fabric && ./gradlew runClient`.

- [ ] **Step 2: Check each point en survival y creativo**, anotando el resultado real:
  1. Las tres balas están en la pestaña de combate, con nombre e icono.
  2. Los 13 crafteos se hacen con la cuadrícula del libro de recetas y dan 32/16/4 y 1.
  3. Survival, Colt 45 y 0 balas: el clic izquierdo no dispara (ni animación ni sonido de GTA) y suena el clic seco una vez por pulsación; apuntar con el derecho funciona.
  4. Survival con 5 balas `ammo1`: 5 disparos Colt y se acaba; con la escopeta `ammo2`: 1 bala por disparo; con el lanzacohetes: 1 `ammo3` por cohete.
  5. Minigun: gasta 1 bala por ronda; 16 balas duran unos 0,5 s.
  6. Creativo: dispara con 0 balas y el inventario no cambia.
  7. Puños y bate: sin gasto ni bloqueo.
  8. Una pila de 64 balas repartida en 2 casillas se gasta sin saltos.

- [ ] **Step 3: Write `docs/ammo-results.md`** con lo comprobado, el resultado del spike del sonido y cualquier desviación.

- [ ] **Step 4: Commit**

```bash
git add docs/ammo-results.md
git commit -m "Docs: ammo and recipes

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```
