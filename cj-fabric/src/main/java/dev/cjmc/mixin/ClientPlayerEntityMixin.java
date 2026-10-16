package dev.cjmc.mixin;

import dev.cjmc.MovementPolicy;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Where GTA does not let CJ sprint (with a rifle in hand, say), the player cannot either. */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
    private void cj$noSprintStart(CallbackInfoReturnable<Boolean> cir) {
        if (!MovementPolicy.current().canSprint()) {
            cir.setReturnValue(false);
        }
    }

    /** A sprint already under way (the player starts aiming while running) stops at once. */
    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void cj$stopSprint(CallbackInfo ci) {
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        if (self.isSprinting() && !MovementPolicy.current().canSprint()) {
            self.setSprinting(false);
        }
    }
}
