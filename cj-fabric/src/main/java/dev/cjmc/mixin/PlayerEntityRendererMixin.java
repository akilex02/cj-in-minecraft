package dev.cjmc.mixin;

import dev.cjmc.CjRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    /** Draw CJ instead of the player when there is a model and a fresh pose; otherwise leave the vanilla rendering alone. */
    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void cj$render(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices,
                           VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (CjRenderer.INSTANCE.render(player, tickDelta, matrices, vertexConsumers, light)) {
            ci.cancel();
        }
    }
}
