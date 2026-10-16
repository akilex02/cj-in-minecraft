package dev.cjmc.mixin;

import dev.cjmc.CjItems;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The skateboard carries the player at the speed of the Skate engine, which the server's own check of the moves (too fast, or not what the physics of a walking player
 * would give) refuses: it puts the player back every tick. While a player holds the skateboard the server takes the position the client says (in a world shared with
 * others this lets that player move freely, which is the price of the optional skate mode).
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;
    @Shadow
    private Vec3d requestedTeleportPos;
    @Shadow
    private double lastTickX, lastTickY, lastTickZ, updatedX, updatedY, updatedZ;

    @Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
    private void cj$takeTheSkateboardsMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        // the packet first arrives on the network thread, and the handler sends it on to the server's thread: only there may the player and his chunks change
        if (!player.getServer().isOnThread()) {
            return;
        }
        if (requestedTeleportPos != null || !packet.changesPosition() || !CjItems.isHoldingSkateboard(player)) {
            return;
        }
        double x = packet.getX(player.getX()), y = packet.getY(player.getY()), z = packet.getZ(player.getZ());
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x) > 3.0E7 || Math.abs(z) > 3.0E7 || Math.abs(y) > 2.0E4) {
            return; // nonsense: the usual checks deal with it
        }
        player.updatePositionAndAngles(x, y, z, packet.getYaw(player.getYaw()), packet.getPitch(player.getPitch()));
        player.setOnGround(packet.isOnGround());
        player.fallDistance = 0f;
        lastTickX = updatedX = x;
        lastTickY = updatedY = y;
        lastTickZ = updatedZ = z;
        player.getServerWorld().getChunkManager().updatePosition(player);
        ci.cancel();
    }
}
