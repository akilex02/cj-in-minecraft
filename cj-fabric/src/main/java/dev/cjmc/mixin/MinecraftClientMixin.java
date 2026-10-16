package dev.cjmc.mixin;

import dev.cjmc.CjItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** With one of our weapons in hand the mouse buttons are the gun's (read in CjClient): they neither attack, break nor use. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow public ClientPlayerEntity player;

    /** Knocked down nothing is attacked, used or broken. */
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void cj$noAttackKnockedDown(CallbackInfoReturnable<Boolean> cir) {
        if (dev.cjmc.BodyClient.frozen() || dev.cjmc.VehicleClient.driving() || dev.cjmc.skate.SkateRide.active()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void cj$noUseKnockedDown(CallbackInfo ci) {
        // on the board only the skateboard itself may be used (it is how he gets off)
        if (dev.cjmc.BodyClient.frozen() || dev.cjmc.VehicleClient.driving() || (dev.cjmc.skate.SkateRide.active() && !dev.cjmc.CjItems.isHoldingSkateboard(player))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void cj$noBreakKnockedDown(boolean breaking, CallbackInfo ci) {
        if (dev.cjmc.BodyClient.frozen() || dev.cjmc.VehicleClient.driving() || dev.cjmc.skate.SkateRide.active()) {
            ci.cancel();
        }
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void cj$noAttack(CallbackInfoReturnable<Boolean> cir) {
        if (CjItems.takesOverTheButtons(player)) {
            cir.setReturnValue(false);
        }
    }

    @Shadow public net.minecraft.util.hit.HitResult crosshairTarget;

    /** With the fists in hand and the bridge alive, GTA's punch is the attack: the vanilla blow on an entity would hit a second time. Mining is left alone. */
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void cj$noBareHandedAttackOnEntities(CallbackInfoReturnable<Boolean> cir) {
        if (player != null && CjItems.isHoldingFists(player) && dev.cjmc.CjClient.bridgeLive()
                && !player.isSpectator() && crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit
                && hit.getEntity() instanceof net.minecraft.entity.LivingEntity) { // boats, item frames and the like are still hit the vanilla way
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void cj$noUse(CallbackInfo ci) {
        if (CjItems.takesOverTheButtons(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void cj$noBreak(boolean breaking, CallbackInfo ci) {
        if (CjItems.takesOverTheButtons(player)) {
            ci.cancel();
        }
    }
}
