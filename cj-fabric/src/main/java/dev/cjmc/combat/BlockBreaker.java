package dev.cjmc.combat;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Breaks a shot block. With dropsWithoutTool the drops are those of a block mined with a netherite pickaxe, so stone drops cobblestone. */
final class BlockBreaker {
    private static ItemStack tool;

    private BlockBreaker() {}

    private static ItemStack tool() {
        if (tool == null) {
            tool = new ItemStack(Items.NETHERITE_PICKAXE); // built on first use: items are not registered when this class loads
        }
        return tool;
    }

    static void breakBlock(ServerWorld world, BlockPos pos, BlockState state, ServerPlayerEntity player, boolean dropsWithoutTool) {
        if (!dropsWithoutTool) {
            world.breakBlock(pos, true, player, 512);
            return;
        }
        BlockEntity entity = world.getBlockEntity(pos);
        if (world.breakBlock(pos, false, player, 512)) {
            Block.dropStacks(state, world, pos, entity, player, tool());
        }
    }
}
