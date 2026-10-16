package dev.cjmc;

import dev.cjmc.combat.RocketEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** The rocket draws nothing itself: its look is the trail of particles its tick adds. */
final class RocketRenderer extends EntityRenderer<RocketEntity> {
    RocketRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(RocketEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {}

    @Override
    public Identifier getTexture(RocketEntity entity) {
        return Identifier.of("minecraft", "textures/entity/arrow.png"); // never drawn
    }
}
