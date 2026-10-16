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
