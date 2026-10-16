package dev.cjmc;

import java.util.ArrayList;
import java.util.List;

/**
 * What the chests of the game hold of the mod, as plain data (ChestLootEvents puts it into the loot tables). Every chest gets the common
 * ammo and weapons; the valuable ones (end cities, bastions, ancient cities, strongholds...) also the rare ones; the weaponsmith of the
 * village gives the common ones twice as often. Each drop is its own roll, so they come together or alone.
 */
public final class ChestLoot {
    /**
     * One item: the chance that a chest holds it, and how many (min to max, a whole stack of bullets or a single weapon). `wear` lists the shares
     * of use a weapon can be found with (0 = as new, 0.25 = a quarter used...), each equally likely; empty for what has no durability or is always as new.
     */
    public record Drop(String item, float chance, int min, int max, List<Float> wear) {}

    private static final List<Float> ANY_WEAR = List.of(0f, 0.25f, 0.5f, 0.75f);
    private static final List<Float> HALF_WEAR = List.of(0f, 0.25f, 0.5f); // the rifles and the shotgun are never found more than half used
    private static final List<Float> NEW_ONLY = List.of();

    private static final List<Drop> COMMON = List.of(
            new Drop("ammo1", 0.25f, 8, 24, NEW_ONLY), new Drop("ammo2", 0.12f, 6, 16, NEW_ONLY),
            new Drop("colt45", 0.18f, 1, 1, ANY_WEAR), new Drop("baseball_bat", 0.15f, 1, 1, ANY_WEAR), new Drop("micro_uzi", 0.14f, 1, 1, ANY_WEAR),
            new Drop("desert_eagle", 0.13f, 1, 1, ANY_WEAR), new Drop("chromegun", 0.12f, 1, 1, HALF_WEAR), new Drop("ak47", 0.12f, 1, 1, HALF_WEAR),
            new Drop("m4", 0.12f, 1, 1, HALF_WEAR));
    private static final List<Drop> RARE = List.of(
            new Drop("minigun", 0.08f, 1, 1, NEW_ONLY), new Drop("rocket_launcher", 0.08f, 1, 1, NEW_ONLY), new Drop("ammo3", 0.12f, 2, 4, NEW_ONLY),
            new Drop("jetpack", 0.08f, 1, 1, NEW_ONLY));

    /** The loot tables of the chests that hold treasure (a path starting with one of these; the village's weaponsmith is apart). */
    private static final List<String> VALUABLE = List.of("chests/end_city_treasure", "chests/bastion_", "chests/ancient_city", "chests/stronghold_",
            "chests/ruined_portal", "chests/buried_treasure", "chests/desert_pyramid", "chests/jungle_temple", "chests/woodland_mansion",
            "chests/simple_dungeon", "chests/nether_bridge", "chests/pillager_outpost", "chests/shipwreck_treasure", "chests/trial_chambers/reward");
    private static final String WEAPONSMITH = "chests/village/village_weaponsmith";

    private ChestLoot() {}

    /** The drops to add to a loot table (its path under the namespace, like "chests/simple_dungeon"): empty unless it is a chest. */
    public static List<Drop> dropsFor(String tablePath) {
        if (!tablePath.startsWith("chests/")) {
            return List.of();
        }
        List<Drop> out = new ArrayList<>();
        boolean smith = tablePath.equals(WEAPONSMITH);
        for (Drop d : COMMON) {
            out.add(smith ? new Drop(d.item(), Math.min(1f, d.chance() * 2f), d.min(), d.max(), d.wear()) : d);
        }
        if (VALUABLE.stream().anyMatch(tablePath::startsWith)) {
            out.addAll(RARE);
        }
        return out;
    }
}
