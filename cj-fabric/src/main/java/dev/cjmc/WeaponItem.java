package dev.cjmc;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * A weapon item. It does nothing by itself: holding it makes CJ take the weapon in GTA; the clicks are read by the client. With durability it
 * wears out (the server takes one per round or blow), mends at the anvil with its repair item, and takes enchantments (Mending, Unbreaking: the
 * data tag enchantable/durability lists it).
 */
public final class WeaponItem extends Item {
    private static final int ENCHANTABILITY = 10; // as iron

    private final WeaponCatalog.WeaponDef def;

    public WeaponItem(WeaponCatalog.WeaponDef def, Settings settings) {
        super(settings);
        this.def = def;
    }

    public WeaponCatalog.WeaponDef def() {
        return def;
    }

    @Override
    public int getEnchantability() {
        return def.durability() > 0 ? ENCHANTABILITY : 0;
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return !def.repair().isEmpty() && ingredient.isOf(Registries.ITEM.get(Identifier.of(def.repair())));
    }
}
