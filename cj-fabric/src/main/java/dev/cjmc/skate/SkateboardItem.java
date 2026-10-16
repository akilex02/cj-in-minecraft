package dev.cjmc.skate;

import java.util.function.Consumer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** The skateboard: using it on the client starts or ends the skate mode (SkateRide). The item itself does nothing on the server. */
public final class SkateboardItem extends Item {
    /** Set by the client at start; called on the client when the item is used. */
    public static volatile Consumer<PlayerEntity> onUse = player -> {};

    public SkateboardItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient) {
            onUse.accept(player);
        }
        return TypedActionResult.success(player.getStackInHand(hand), world.isClient);
    }
}
