package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WeaponCatalogTest {
    private static final String BLOCKS = "\"blocks\":{\"healthPerHardness\":4.0,\"minHardness\":0.25,\"regenDelayTicks\":60,"
            + "\"regenPerTick\":0.05,\"dropsWithoutTool\":true}";
    private static final String WEAPON = "{\"id\":\"colt45\",\"name\":\"Colt 45\",\"gta\":22,\"model\":\"colt45\","
            + "\"holdOffset\":[0,0,0],\"holdRotation\":[0,0,0],\"damage\":6,\"range\":50,\"pellets\":1,"
            + "\"spread\":0.01,\"blockPower\":0.5,\"aimSpeed\":1.0,\"sprintHeld\":true,\"sprintAiming\":true,"
            + "\"moveWhileFiring\":true,\"knockback\":0.3,\"canJump\":true,\"explosionPower\":0,\"projectileSpeed\":0,\"reach\":0,\"coneHalfAngle\":0,\"item\":true}";
    private static final String OTHER = "{\"id\":\"other\",\"name\":\"x\",\"gta\":23,\"model\":\"m\",\"holdOffset\":[0,0,0],"
            + "\"holdRotation\":[0,0,0],\"damage\":1,\"range\":1,\"pellets\":1,\"spread\":0,\"blockPower\":0,\"aimSpeed\":1,"
            + "\"sprintHeld\":true,\"sprintAiming\":true,\"moveWhileFiring\":true,\"knockback\":0,\"canJump\":true,\"explosionPower\":0,\"projectileSpeed\":0,\"reach\":0,\"coneHalfAngle\":0,\"item\":true}";

    private static String json(String... weapons) {
        return "{\"weapons\":[" + String.join(",", weapons) + "]," + BLOCKS + "}";
    }

    private static String one() {
        return json(WEAPON);
    }

    private static void rejects(String json) {
        assertThrows(IllegalArgumentException.class, () -> WeaponCatalog.parse(json));
    }

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

    @Test
    void theShippedWeaponsHaveTheDurabilityAndRepairOfTheSpec() {
        WeaponCatalog c = WeaponCatalog.load();
        int[] durability = {200, 250, 300, 400, 200, 600, 600, 1500, 40};
        String[] ids = {"baseball_bat", "colt45", "desert_eagle", "micro_uzi", "chromegun", "ak47", "m4", "minigun", "rocket_launcher"};
        String[] repair = {"minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:iron_ingot",
                "minecraft:diamond", "minecraft:diamond", "minecraft:diamond", "minecraft:diamond"};
        for (int i = 0; i < ids.length; i++) {
            assertEquals(durability[i], c.byId(ids[i]).durability(), ids[i]);
            assertEquals(repair[i], c.byId(ids[i]).repair(), ids[i]);
        }
        assertEquals(0, c.byId("fists").durability()); // the fists never wear out
    }

    @Test
    void theDurabilityIsOptionalAndDefaultsToNone() {
        assertEquals(0, WeaponCatalog.parse(one()).byId("colt45").durability());
        assertEquals("", WeaponCatalog.parse(one()).byId("colt45").repair());
    }

    @Test
    void aNegativeDurabilityIsRejected() {
        rejects(json(WEAPON.replace("\"item\":true", "\"item\":true,\"durability\":-5")));
    }

    @Test
    void theHeavyWeaponsAndTheBatDoNotFireCrouched() {
        WeaponCatalog c = WeaponCatalog.load();
        for (String id : new String[] {"baseball_bat", "minigun", "rocket_launcher"}) {
            assertFalse(c.byId(id).crouchFire(), id);
        }
        for (String id : new String[] {"colt45", "desert_eagle", "micro_uzi", "chromegun", "ak47", "m4", "fists"}) {
            assertTrue(c.byId(id).crouchFire(), id);
        }
    }

    @Test
    void crouchFireIsOptionalAndDefaultsToTrue() {
        assertTrue(WeaponCatalog.parse(one()).byId("colt45").crouchFire());
    }

    @Test
    void theShippedCatalogHasTheTenWeapons() {
        WeaponCatalog c = WeaponCatalog.load();
        assertEquals(10, c.all().size());
        Set<String> ids = new HashSet<>();
        Set<Integer> gtaIds = new HashSet<>();
        for (WeaponCatalog.WeaponDef d : c.all()) {
            assertTrue(ids.add(d.id()), "duplicate id " + d.id());
            assertTrue(gtaIds.add(d.gta()), "duplicate gta id " + d.gta());
            assertEquals(3, d.holdOffset().length);
            assertEquals(3, d.holdRotation().length);
            assertTrue((d.damage() > 0 || d.explosionPower() > 0) && d.range() > 0 && d.pellets() >= 1, d.id());
            assertTrue(d.aimSpeed() > 0f && d.aimSpeed() <= 1f, d.id() + " aimSpeed " + d.aimSpeed());
            assertTrue(d.knockback() >= 0f, d.id());
        }
        // measured in GTA (m/s of CJ's animation, see docs/m2-spike.md): rifles and the shotgun cannot sprint, and the Desert Eagle, the
        // shotgun and the rifles crawl while aiming; the Colt 45 and the Micro SMG keep their speed while aiming
        assertEquals(1.0f, c.byId("colt45").aimSpeed());
        assertTrue(c.byId("colt45").sprintHeld() && c.byId("colt45").sprintAiming());
        assertTrue(c.byId("micro_uzi").sprintHeld() && !c.byId("micro_uzi").sprintAiming());
        assertTrue(c.byId("desert_eagle").sprintHeld() && !c.byId("desert_eagle").sprintAiming());
        for (String id : new String[] {"chromegun", "ak47", "m4", "minigun", "rocket_launcher"}) {
            assertTrue(!c.byId(id).sprintHeld() && !c.byId(id).sprintAiming(), id);
        }
        for (String id : new String[] {"desert_eagle", "chromegun", "ak47", "m4", "minigun", "rocket_launcher"}) {
            assertTrue(c.byId(id).aimSpeed() < 0.4f, id);
        }
        // measured in GTA: only the Colt 45 and the Micro SMG can be fired on the move
        assertTrue(c.byId("colt45").moveWhileFiring() && c.byId("micro_uzi").moveWhileFiring());
        for (String id : new String[] {"desert_eagle", "chromegun", "ak47", "m4", "minigun", "rocket_launcher"}) {
            assertFalse(c.byId(id).moveWhileFiring(), id);
        }
        // the minigun: a heavy weapon like the rifles (movement copied from them, to be checked against GTA; see docs/minigun-results.md)
        assertEquals(38, c.byId("minigun").gta());
        assertEquals("minigun", c.byId("minigun").model());
        // GTA does not let CJ jump with the minigun; the other six jump
        assertFalse(c.byId("minigun").canJump());
        for (String id : new String[] {"colt45", "desert_eagle", "chromegun", "micro_uzi", "ak47", "m4"}) {
            assertTrue(c.byId(id).canJump(), id);
        }
        // the rocket launcher: a heavy weapon whose explosion does the damage
        WeaponCatalog.WeaponDef rocket = c.byId("rocket_launcher");
        assertEquals(35, rocket.gta());
        assertEquals("rocketla", rocket.model());
        assertEquals(6f, rocket.explosionPower());
        assertTrue(rocket.projectileSpeed() > 0f);
        assertEquals(0f, rocket.damage());
        assertFalse(rocket.canJump()); // GTA does not let CJ jump with it, as with the minigun
        for (WeaponCatalog.WeaponDef d : c.all()) {
            if (!d.id().equals("rocket_launcher")) {
                assertEquals(0f, d.explosionPower(), d.id());
                assertEquals(0f, d.projectileSpeed(), d.id());
            }
        }
        // melee: the fists (GTA's unarmed, no item) and the baseball bat
        WeaponCatalog.WeaponDef fists = c.byId("fists");
        WeaponCatalog.WeaponDef bat = c.byId("baseball_bat");
        assertEquals(0, fists.gta());
        assertFalse(fists.item()); // the fists are no item: CJ punches with an empty hand or any item that does not hit on its own
        assertEquals(5, bat.gta());
        assertTrue(bat.item());
        assertEquals("bat", bat.model());
        assertEquals(c.byGta(0), fists);
        assertEquals(c.byGta(5), bat);
        assertEquals(3f, fists.reach());
        assertEquals(35f, fists.coneHalfAngle());
        assertEquals(3.2f, bat.reach(), 1e-6f);
        assertEquals(45f, bat.coneHalfAngle());
        assertEquals(0f, fists.explosionPower());
        assertEquals(0f, bat.projectileSpeed());
        // the user's criterion, as in GTA: a 20 HP mob falls in 6 punches or in 4 bat blows
        assertEquals(6, hitsToKill(20f, fists));
        assertEquals(4, hitsToKill(20f, bat));
        // with a margin, not on the edge of what a float sum happens to give: one blow less never kills, the right number always does
        assertTrue(fists.damage() * 5 < 20f && fists.damage() * 6 > 20f);
        assertTrue(bat.damage() * 3 < 20f && bat.damage() * 4 > 20f);
        for (WeaponCatalog.WeaponDef d : c.all()) {
            if (!d.id().equals("fists") && !d.id().equals("baseball_bat")) {
                assertEquals(0f, d.reach(), d.id());
                assertEquals(0f, d.coneHalfAngle(), d.id());
                assertTrue(d.item(), d.id());
            }
        }
        assertEquals(22, c.byId("colt45").gta());
        assertEquals("ak47", c.byGta(30).model());
        assertEquals(25, c.byId("chromegun").gta());
        assertTrue(c.byId("chromegun").pellets() > 1);
    }

    @Test
    void theShippedDamageValuesMatchTheDesign() {
        WeaponCatalog c = WeaponCatalog.load();
        // docs/superpowers/specs/2026-10-06-m3-damage-design.md, section 4
        assertEquals(6f, c.byId("colt45").damage());
        assertEquals(14f, c.byId("desert_eagle").damage());
        assertEquals(3f, c.byId("chromegun").damage());
        assertEquals(8, c.byId("chromegun").pellets());
        assertEquals(0.08f, c.byId("chromegun").spread(), 1e-6f);
        assertEquals(7f, c.byId("ak47").damage());
        assertEquals(1.2f, c.byId("m4").blockPower(), 1e-6f);
        assertEquals(0.3f, c.byId("colt45").knockback(), 1e-6f);
        assertEquals(0.6f, c.byId("desert_eagle").knockback(), 1e-6f);
        WeaponCatalog.BlockRules b = c.blocks();
        assertEquals(4.0, b.healthPerHardness());
        assertEquals(0.25, b.minHardness());
        assertEquals(60, b.regenDelayTicks());
        assertEquals(0.05, b.regenPerTick(), 1e-9);
        assertTrue(b.dropsWithoutTool());
    }

    @Test
    void parsesAWeaponAndTheBlockRules() {
        WeaponCatalog c = WeaponCatalog.parse(one());
        WeaponCatalog.WeaponDef d = c.byId("colt45");
        assertNotNull(d);
        assertEquals("Colt 45", d.name());
        assertArrayEquals(new float[] {0, 0, 0}, d.holdOffset());
        assertEquals(0.01f, d.spread(), 1e-6f);
        assertEquals(0.3f, d.knockback(), 1e-6f);
        assertNull(c.byId("nope"));
        assertNull(c.byGta(99));
        assertEquals(60, c.blocks().regenDelayTicks());
        assertTrue(c.blocks().dropsWithoutTool());
    }

    @Test
    void rejectsADuplicateIdOrGtaId() {
        rejects(json(WEAPON, OTHER.replace("\"id\":\"other\"", "\"id\":\"colt45\"")));
        rejects(json(WEAPON, OTHER.replace("\"gta\":23", "\"gta\":22")));
    }

    @Test
    void rejectsMissingOrWrongFields() {
        rejects(one().replace("\"gta\":22,", ""));
        rejects(one().replace("[0,0,0],\"holdRotation\"", "[0,0],\"holdRotation\""));
        rejects(one().replace("\"gta\":22", "\"gta\":-1"));
        assertEquals(0, WeaponCatalog.parse(one().replace("\"gta\":22", "\"gta\":0")).byId("colt45").gta());
        rejects(one().replace(",\"reach\":0", ""));
        rejects(one().replace(",\"coneHalfAngle\":0", ""));
        rejects(one().replace(",\"item\":true", ""));
        rejects(one().replace("\"item\":true", "\"item\":1"));
        rejects(one().replace(",\"aimSpeed\":1.0", ""));
        rejects(one().replace("\"sprintHeld\":true", "\"sprintHeld\":3"));
        rejects(one().replace(",\"moveWhileFiring\":true", ""));
        rejects(one().replace(",\"knockback\":0.3", ""));
        rejects(one().replace(",\"canJump\":true", ""));
        rejects(one().replace("\"canJump\":true", "\"canJump\":1"));
        rejects(one().replace("\"aimSpeed\":1.0", "\"aimSpeed\":0"));
        rejects(one().replace("\"aimSpeed\":1.0", "\"aimSpeed\":1.5"));
        rejects("{}");
        rejects("not json");
    }

    @Test
    void rejectsAbsurdCombatValues() {
        // Review Focus #2: nothing that would make a shot divide by zero, heal a mob or reach across the world
        rejects(one().replace("\"damage\":6", "\"damage\":0"));
        rejects(one().replace("\"damage\":6", "\"damage\":-3"));
        rejects(one().replace("\"range\":50", "\"range\":0"));
        rejects(one().replace("\"range\":50", "\"range\":5000"));
        rejects(one().replace("\"pellets\":1", "\"pellets\":0"));
        rejects(one().replace("\"pellets\":1", "\"pellets\":500"));
        rejects(one().replace("\"pellets\":1", "\"pellets\":33"));
        assertEquals(32, WeaponCatalog.parse(one().replace("\"pellets\":1", "\"pellets\":32")).byId("colt45").pellets());
        rejects(one().replace("\"spread\":0.01", "\"spread\":-0.1"));
        rejects(one().replace("\"spread\":0.01", "\"spread\":2"));
        rejects(one().replace("\"blockPower\":0.5", "\"blockPower\":-1"));
        rejects(one().replace("\"knockback\":0.3", "\"knockback\":-0.1"));
        rejects(one().replace("\"knockback\":0.3", "\"knockback\":50"));
    }

    @Test
    void rejectsAbsurdOrMissingBlockRules() {
        rejects(one().replace("\"healthPerHardness\":4.0", "\"healthPerHardness\":0"));
        rejects(one().replace("\"minHardness\":0.25", "\"minHardness\":0"));
        rejects(one().replace("\"regenDelayTicks\":60", "\"regenDelayTicks\":-1"));
        rejects(one().replace("\"regenPerTick\":0.05", "\"regenPerTick\":-0.05"));
        rejects(one().replace("\"dropsWithoutTool\":true", "\"dropsWithoutTool\":1"));
        rejects(one().replace(",\"regenPerTick\":0.05", ""));
        rejects("{\"weapons\":[" + WEAPON + "]}"); // no "blocks" section at all
    }

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
        rejects(rocket.replace("\"damage\":0", "\"damage\":-3")); // an explosion does not excuse a negative damage
        // the speed limit on its own: with an explosion, 200 blocks per second is the most
        assertEquals(200f, WeaponCatalog.parse(rocket.replace("\"projectileSpeed\":40", "\"projectileSpeed\":200")).byId("colt45").projectileSpeed());
        rejects(rocket.replace("\"projectileSpeed\":40", "\"projectileSpeed\":201"));
        rejects(rocket.replace("\"projectileSpeed\":40", "\"projectileSpeed\":500"));
        // and the power limit: 20 is the most
        assertEquals(20f, WeaponCatalog.parse(rocket.replace("\"explosionPower\":6", "\"explosionPower\":20")).byId("colt45").explosionPower());
        rejects(rocket.replace("\"explosionPower\":6", "\"explosionPower\":21"));
    }

    private static int hitsToKill(float health, WeaponCatalog.WeaponDef d) {
        int hits = 0;
        float dealt = 0f;
        while (dealt < health) {
            dealt += d.damage();
            hits++;
        }
        return hits;
    }

    @Test
    void rejectsAbsurdMeleeValues() {
        String melee = one().replace("\"reach\":0", "\"reach\":3").replace("\"coneHalfAngle\":0", "\"coneHalfAngle\":35");
        WeaponCatalog.WeaponDef d = WeaponCatalog.parse(melee).byId("colt45");
        assertEquals(3f, d.reach());
        assertEquals(35f, d.coneHalfAngle());
        rejects(one().replace("\"reach\":0", "\"reach\":-1"));
        rejects(melee.replace("\"reach\":3", "\"reach\":11")); // with a cone, so only the reach limit rejects it
        rejects(one().replace("\"reach\":0", "\"reach\":11"));
        assertEquals(10f, WeaponCatalog.parse(melee.replace("\"reach\":3", "\"reach\":10")).byId("colt45").reach());
        rejects(melee.replace("\"coneHalfAngle\":35", "\"coneHalfAngle\":0"));   // a reach with no cone hits nothing
        rejects(melee.replace("\"coneHalfAngle\":35", "\"coneHalfAngle\":-5"));
        rejects(melee.replace("\"coneHalfAngle\":35", "\"coneHalfAngle\":91"));
        assertEquals(90f, WeaponCatalog.parse(melee.replace("\"coneHalfAngle\":35", "\"coneHalfAngle\":90")).byId("colt45").coneHalfAngle());
        rejects(one().replace("\"coneHalfAngle\":0", "\"coneHalfAngle\":35"));   // a cone with no reach
        rejects(melee.replace("\"damage\":6", "\"damage\":0"));                   // a blow with no damage does nothing
        rejects(melee.replace("\"projectileSpeed\":0", "\"projectileSpeed\":40").replace("\"explosionPower\":0", "\"explosionPower\":6"));
        rejects(melee.replace("\"explosionPower\":0", "\"explosionPower\":6"));
    }

    private static int blowsToBreak(WeaponCatalog c, String weaponId, float hardness) {
        WeaponCatalog.BlockRules b = c.blocks();
        dev.cjmc.combat.BlockHealthModel model = new dev.cjmc.combat.BlockHealthModel(
                new dev.cjmc.combat.BlockHealthModel.Rules(b.healthPerHardness(), b.minHardness(), b.regenDelayTicks(), b.regenPerTick()));
        for (int blows = 1; blows <= 200; blows++) {
            if (model.hit(1L, hardness, c.byId(weaponId).blockPower(), 0L).broken()) {
                return blows;
            }
        }
        return Integer.MAX_VALUE;
    }

    @Test
    void everyWeaponButTheBatTheRocketAndTheFistsBreaksBlocksAtTwiceTheOldPower() {
        // the user asked for all of them at double speed (2026-10-07): the old block powers, doubled; the bat stays as it was and the rocket
        // launcher breaks blocks with its explosion, not with a power
        WeaponCatalog c = WeaponCatalog.load();
        assertEquals(1.0f, c.byId("colt45").blockPower(), 1e-6f);
        assertEquals(2.0f, c.byId("desert_eagle").blockPower(), 1e-6f);
        assertEquals(1.0f, c.byId("chromegun").blockPower(), 1e-6f);
        assertEquals(0.6f, c.byId("micro_uzi").blockPower(), 1e-6f);
        assertEquals(1.2f, c.byId("ak47").blockPower(), 1e-6f);
        assertEquals(1.2f, c.byId("m4").blockPower(), 1e-6f);
        assertEquals(0.32f, c.byId("minigun").blockPower(), 1e-6f);
        assertEquals(2.7f, c.byId("baseball_bat").blockPower(), 1e-6f);
        assertEquals(0f, c.byId("rocket_launcher").blockPower());
        assertEquals(6f, c.byId("rocket_launcher").explosionPower());
        // twice as fast: half the shots for a common block
        assertEquals(8, blowsToBreak(c, "colt45", 2.0f));   // a log or planks: 8 shots of a Colt 45 (it was 16)
        assertEquals(7, blowsToBreak(c, "ak47", 2.0f));     // 8 / 1.2 rounded up (it was 14)
    }

    @Test
    void theBatBreaksCommonBlocksInAboutThreeBlowsAndTheFistsBreakNothing() {
        WeaponCatalog c = WeaponCatalog.load();
        // the user's criterion: about three blows to break a block in survival
        assertEquals(1, blowsToBreak(c, "baseball_bat", 0.5f));  // dirt, grass, sand
        assertEquals(3, blowsToBreak(c, "baseball_bat", 1.5f));  // stone
        assertEquals(3, blowsToBreak(c, "baseball_bat", 2.0f));  // logs, planks, cobblestone
        assertEquals(5, blowsToBreak(c, "baseball_bat", 3.0f));  // iron ore
        assertTrue(blowsToBreak(c, "baseball_bat", 50.0f) > 50);  // obsidian stays a long job
        assertEquals(0f, c.byId("fists").blockPower());
        assertEquals(Integer.MAX_VALUE, blowsToBreak(c, "fists", 0.5f));
    }
}
