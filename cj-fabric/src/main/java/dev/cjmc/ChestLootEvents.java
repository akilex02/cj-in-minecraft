package dev.cjmc;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.item.Item;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetCountLootFunction;
import net.minecraft.loot.function.SetDamageLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Adds the weapons and ammo of the mod (see ChestLoot) to the chests of the game: one pool per item, so each one rolls on its own. */
final class ChestLootEvents {
    private ChestLootEvents() {}

    private static ItemEntry.Builder<?> entry(Item item, ChestLoot.Drop drop, Float left) {
        ItemEntry.Builder<?> entry = ItemEntry.builder(item);
        if (drop.max() > drop.min()) {
            entry.apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(drop.min(), drop.max())));
        } else if (drop.min() > 1) {
            entry.apply(SetCountLootFunction.builder(ConstantLootNumberProvider.create(drop.min())));
        }
        if (left != null && left < 1f) {
            entry.apply(SetDamageLootFunction.builder(ConstantLootNumberProvider.create(left)));
        }
        return entry;
    }

    static void register() {
        LootTableEvents.MODIFY.register((key, table, source, registries) -> {
            if (!source.isBuiltin() || !"minecraft".equals(key.getValue().getNamespace())) {
                return; // only the tables of the game itself, never another mod's or a datapack's
            }
            for (ChestLoot.Drop drop : ChestLoot.dropsFor(key.getValue().getPath())) {
                Item item = Registries.ITEM.get(Identifier.of(CjItems.MOD_ID, drop.item()));
                LootPool.Builder pool = LootPool.builder().rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(RandomChanceLootCondition.builder(drop.chance()));
                if (drop.wear().isEmpty()) {
                    pool.with(entry(item, drop, null));
                } else {
                    for (float used : drop.wear()) { // one equally likely entry per state of wear: the function sets the share of durability that is left
                        pool.with(entry(item, drop, 1f - used));
                    }
                }
                table.pool(pool);
            }
        });
    }
}
