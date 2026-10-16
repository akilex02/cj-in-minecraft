package dev.cjmc.mixin;

import dev.cjmc.CjRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First person with a weapon of the mod or an empty hand: CJ's arms and weapon instead of Minecraft's hand. The main hand draws them; the other hand is not drawn
 * then, or Steve's arm would float beside CJ's.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void cj$firstPersonArms(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item,
                                    float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (dev.cjmc.VehicleClient.driving()) {
            ci.cancel(); // at the wheel the car and CJ are drawn around the camera: no hand
            return;
        }
        if (!CjRenderer.INSTANCE.firstPersonWanted(player)) {
            return;
        }
        if (hand == Hand.OFF_HAND || CjRenderer.INSTANCE.renderFirstPerson(player, tickDelta, matrices, vertexConsumers, light)) {
            ci.cancel();
        }
    }
}
