package dev.cjmc.combat;

import dev.cjmc.combat.BlockHealthModel.Change;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** One BlockHealthModel per world, healed by the server tick; it also puts the cracks on the blocks. */
public final class BlockHealthTracker {
    private final BlockHealthModel.Rules rules;
    private final Map<RegistryKey<World>, BlockHealthModel> models = new HashMap<>();

    public BlockHealthTracker(BlockHealthModel.Rules rules) {
        this.rules = rules;
    }

    public BlockHealthModel of(ServerWorld world) {
        return models.computeIfAbsent(world.getRegistryKey(), key -> new BlockHealthModel(rules));
    }

    /** Server tick of one world: heal the blocks left alone and update their cracks. */
    public void tick(ServerWorld world) {
        BlockHealthModel model = models.get(world.getRegistryKey());
        if (model == null) {
            return;
        }
        for (Change change : model.tick(world.getTime())) {
            world.setBlockBreakingInfo(CrackId.of(change.pos()), BlockPos.fromLong(change.pos()), change.stage());
        }
    }

    /** The world was unloaded: its blocks are forgotten. */
    public void forget(ServerWorld world) {
        models.remove(world.getRegistryKey());
    }
}
