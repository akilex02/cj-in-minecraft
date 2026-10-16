package dev.cjmc;

import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class CjItems {
    public static final String MOD_ID = "cj_fabric";

    public static Item JETPACK;
    public static Item SKATEBOARD;
    /** The fists of the catalog (no item), set when the items are registered. */
    public static WeaponCatalog.WeaponDef FISTS;
    public static final Map<String, Item> AMMO = new LinkedHashMap<>();

    private CjItems() {}

    /** Registers one item per weapon of the catalog and lists them in the combat tab. */
    public static void register(WeaponCatalog catalog) {
        FISTS = catalog.byId("fists");
        for (WeaponCatalog.WeaponDef def : catalog.all()) {
            if (!def.item()) {
                continue;
            }
            Item item = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, def.id()), new WeaponItem(def, settingsOf(def)));
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(item));
        }
        JETPACK = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "jetpack"), new JetpackItem(new Item.Settings().maxCount(1)));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(JETPACK));
        SKATEBOARD = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "skateboard"), new dev.cjmc.skate.SkateboardItem(new Item.Settings().maxCount(1)));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(SKATEBOARD));
        for (String id : AmmoRules.ALL) {
            Item ammo = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, id), new Item(new Item.Settings()));
            AMMO.put(id, ammo);
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(ammo));
        }
    }

    /** The item of a kind of ammo (AmmoRules.LIGHT...), or null for an unknown id. */
    public static Item ammoItem(String id) {
        return AMMO.get(id);
    }

    /** One of each weapon, never stacked; with durability it wears out (WeaponItem makes it mend and take Mending and Unbreaking). */
    private static Item.Settings settingsOf(WeaponCatalog.WeaponDef def) {
        if (def.durability() <= 0) {
            return new Item.Settings().maxCount(1);
        }
        return new Item.Settings().maxDamage(def.durability());
    }

    /** The skateboard is in the main hand. */
    public static boolean isHoldingSkateboard(PlayerEntity player) {
        return player != null && player.getMainHandStack().isOf(SKATEBOARD);
    }

    /** Whether the player wears the jetpack in the chest slot. */
    public static boolean isWearingJetpack(PlayerEntity player) {
        return player != null && player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).getItem() instanceof JetpackItem;
    }

    /** The weapon of a stack, or null when it is not one of ours. */
    public static WeaponCatalog.WeaponDef weaponOf(ItemStack stack) {
        return stack.getItem() instanceof WeaponItem w ? w.def() : null;
    }

    public static boolean isHoldingWeapon(PlayerEntity player) {
        return player != null && weaponOf(player.getMainHandStack()) != null;
    }

    /** The fists of the catalog (GTA's unarmed): no item, CJ punches with them whenever PunchRules says so. */
    public static boolean isFists(WeaponCatalog.WeaponDef def) {
        return def != null && "fists".equals(def.id());
    }

    /** Whether the player punches: an empty hand or an item that does not hit on its own (see PunchRules). */
    public static boolean isHoldingFists(PlayerEntity player) {
        return player != null && FISTS != null && PunchRules.punches(isHoldingWeapon(player), hitsOnItsOwn(player.getMainHandStack()));
    }

    /** Whether an item is a tool or hits on its own: a sword, axe, pickaxe, shovel, hoe (its attack damage is zero but it is there), trident, mace, shears. */
    public static boolean hitsOnItsOwn(ItemStack stack) {
        if (stack.contains(net.minecraft.component.DataComponentTypes.TOOL)) {
            return true;
        }
        net.minecraft.component.type.AttributeModifiersComponent mods = stack.get(net.minecraft.component.DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (mods == null) {
            return false;
        }
        for (net.minecraft.component.type.AttributeModifiersComponent.Entry e : mods.modifiers()) {
            if (e.attribute().matches(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE)
                    && e.slot().matches(net.minecraft.entity.EquipmentSlot.MAINHAND)) {
                return true; // any value: the hoes add 0
            }
        }
        return false;
    }

    /** A weapon in hand that takes over the buttons (it shoots or strikes): the fists leave them alone, they mine and use like a hand. */
    public static boolean takesOverTheButtons(PlayerEntity player) {
        return isHoldingWeapon(player) && !isHoldingFists(player);
    }

    /** The weapon the player strikes or shoots with: the one of the item in hand, else the fists (an empty hand or an item that does not hit on its own). Null for an item that hits on its own. */
    public static WeaponCatalog.WeaponDef activeWeapon(PlayerEntity player) {
        if (player == null) {
            return null;
        }
        WeaponCatalog.WeaponDef held = weaponOf(player.getMainHandStack());
        return held != null ? held : isHoldingFists(player) ? FISTS : null;
    }
}
