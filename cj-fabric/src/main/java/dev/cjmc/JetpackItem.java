package dev.cjmc;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Equipment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * The jetpack: a chest item (right click equips it, as a chestplate). It does nothing by itself; wearing it lets the client fly with the
 * jump key (JetpackClient) and the server cancels the fall damage. It has no armour model: CJ replaces the player's model, and the
 * jetpack is drawn on his back by the mod.
 */
public final class JetpackItem extends Item implements Equipment {
    public JetpackItem(Settings settings) {
        super(settings);
    }

    @Override
    public EquipmentSlot getSlotType() {
        return EquipmentSlot.CHEST;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return equipAndSwap(this, world, user, hand);
    }
}
