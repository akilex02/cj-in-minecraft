package dev.cjmc.mixin;

import dev.cjmc.CjRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The preview of the player in the inventory (survival and creative) is drawn with CJ: this marks the moments the screen draws it. */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @Inject(method = "drawEntity(Lnet/minecraft/client/gui/DrawContext;FFFLorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/entity/LivingEntity;)V",
            at = @At("HEAD"))
    private static void cj$previewStart(DrawContext context, float x, float y, float size, Vector3f offset, Quaternionf rotation, Quaternionf overrideCameraAngle,
                                        LivingEntity entity, CallbackInfo ci) {
        CjRenderer.INSTANCE.setPreview(true);
    }

    @Inject(method = "drawEntity(Lnet/minecraft/client/gui/DrawContext;FFFLorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/entity/LivingEntity;)V",
            at = @At("RETURN"))
    private static void cj$previewEnd(DrawContext context, float x, float y, float size, Vector3f offset, Quaternionf rotation, Quaternionf overrideCameraAngle,
                                      LivingEntity entity, CallbackInfo ci) {
        CjRenderer.INSTANCE.setPreview(false);
    }
}
