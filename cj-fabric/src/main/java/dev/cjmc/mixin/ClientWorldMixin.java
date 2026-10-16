package dev.cjmc.mixin;

import dev.cjmc.skate.SkateRide;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A block of the client's world changed (placed, broken, or sent by the server): while skating, the Skate engine must see it. */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Inject(method = "updateListeners", at = @At("TAIL"))
    private void cj$skateBlockChanged(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        SkateRide.blockChanged(pos);
    }
}
