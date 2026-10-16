package dev.cjmc;

import dev.cjmc.combat.FistMining;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Mining with the bare hand at twice the speed: the block-break-speed attribute gets a +100 % modifier while the main hand is empty. It
 * is the server's own attribute (the client gets it synced), so the server and the client agree on how long a block takes.
 */
final class FistMiningServer {
    private static final Identifier ID = Identifier.of(CjItems.MOD_ID, "fist_mining");

    private FistMiningServer() {}

    static void update(ServerPlayerEntity player) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.PLAYER_BLOCK_BREAK_SPEED);
        if (speed == null) {
            return;
        }
        boolean wanted = FistMining.boosted(player.getMainHandStack().isEmpty());
        boolean has = speed.hasModifier(ID);
        if (wanted && !has) {
            speed.addTemporaryModifier(new EntityAttributeModifier(ID, FistMining.extra(), EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!wanted && has) {
            speed.removeModifier(ID);
        }
    }
}
