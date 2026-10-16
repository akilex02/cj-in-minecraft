package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChestLootTest {
    private static ChestLoot.Drop drop(List<ChestLoot.Drop> drops, String item) {
        for (ChestLoot.Drop d : drops) {
            if (d.item().equals(item)) {
                return d;
            }
        }
        return null;
    }

    private static void is(List<ChestLoot.Drop> drops, String item, float chance, int min, int max) {
        ChestLoot.Drop d = drop(drops, item);
        assertNotNull(d, item + " is missing");
        assertEquals(chance, d.chance(), 1e-6f, item + " chance");
        assertEquals(min, d.min(), item + " min");
        assertEquals(max, d.max(), item + " max");
    }

    private static final List<Float> ANY_WEAR = List.of(0f, 0.25f, 0.5f, 0.75f);
    private static final List<Float> HALF_WEAR = List.of(0f, 0.25f, 0.5f);

    private static void wear(List<ChestLoot.Drop> drops, String item, List<Float> wear) {
        assertEquals(wear, drop(drops, item).wear(), item + " wear");
    }

    @Test
    void onlyChestTablesGetLoot() {
        assertTrue(ChestLoot.dropsFor("entities/zombie").isEmpty());
        assertTrue(ChestLoot.dropsFor("blocks/stone").isEmpty());
        assertTrue(ChestLoot.dropsFor("gameplay/fishing").isEmpty());
        assertFalse(ChestLoot.dropsFor("chests/abandoned_mineshaft").isEmpty());
    }

    @Test
    void everyChestHasTheCommonAmmoAndWeaponsAtTheChancesOfTheUser() {
        List<ChestLoot.Drop> d = ChestLoot.dropsFor("chests/abandoned_mineshaft");
        is(d, "ammo1", 0.25f, 8, 24);
        is(d, "ammo2", 0.12f, 6, 16);
        is(d, "colt45", 0.18f, 1, 1);
        is(d, "baseball_bat", 0.15f, 1, 1);
        is(d, "micro_uzi", 0.14f, 1, 1);
        is(d, "desert_eagle", 0.13f, 1, 1);
        is(d, "chromegun", 0.12f, 1, 1);
        is(d, "ak47", 0.12f, 1, 1);
        is(d, "m4", 0.12f, 1, 1);
        assertEquals(9, d.size());
        for (String worn : new String[] {"colt45", "baseball_bat", "micro_uzi", "desert_eagle"}) {
            wear(d, worn, ANY_WEAR); // found as new or used a quarter, a half or three quarters
        }
        for (String half : new String[] {"chromegun", "ak47", "m4"}) {
            wear(d, half, HALF_WEAR); // never more than half used
        }
        wear(d, "ammo1", List.of());
        wear(d, "ammo2", List.of());
        for (String rare : new String[] {"minigun", "rocket_launcher", "ammo3", "jetpack"}) {
            assertEquals(null, drop(d, rare), rare + " is for the valuable chests only");
        }
    }

    @Test
    void theValuableChestsAddTheRareOnes() {
        for (String table : new String[] {"chests/end_city_treasure", "chests/bastion_treasure", "chests/ancient_city", "chests/stronghold_library",
                "chests/buried_treasure", "chests/desert_pyramid", "chests/woodland_mansion", "chests/simple_dungeon", "chests/trial_chambers/reward"}) {
            List<ChestLoot.Drop> d = ChestLoot.dropsFor(table);
            is(d, "minigun", 0.08f, 1, 1);
            is(d, "rocket_launcher", 0.08f, 1, 1);
            is(d, "ammo3", 0.12f, 2, 4);
            is(d, "jetpack", 0.08f, 1, 1);
            wear(d, "minigun", List.of()); // always as new
            wear(d, "rocket_launcher", List.of());
            wear(d, "jetpack", List.of());
            wear(d, "ammo3", List.of());
            is(d, "colt45", 0.18f, 1, 1); // and the common ones as well
            assertEquals(13, d.size(), table);
        }
    }

    @Test
    void theWeaponsmithOfTheVillageGivesTheCommonOnesTwiceAsOften() {
        List<ChestLoot.Drop> d = ChestLoot.dropsFor("chests/village/village_weaponsmith");
        is(d, "ammo1", 0.50f, 8, 24);
        is(d, "colt45", 0.36f, 1, 1);
        is(d, "baseball_bat", 0.30f, 1, 1);
        assertEquals(9, d.size());
    }

    @Test
    void everyDropIsAnItemOfTheMod() {
        WeaponCatalog catalog = WeaponCatalog.load();
        Set<String> items = new HashSet<>(AmmoRules.ALL);
        items.add("jetpack");
        for (WeaponCatalog.WeaponDef def : catalog.all()) {
            if (def.item()) {
                items.add(def.id());
            }
        }
        for (String table : new String[] {"chests/abandoned_mineshaft", "chests/end_city_treasure", "chests/village/village_weaponsmith"}) {
            for (ChestLoot.Drop d : ChestLoot.dropsFor(table)) {
                assertTrue(items.contains(d.item()), d.item());
                assertTrue(d.chance() > 0f && d.chance() <= 1f, d.item() + " chance " + d.chance());
                assertTrue(d.min() >= 1 && d.min() <= d.max(), d.item() + " count");
            }
        }
    }
}
