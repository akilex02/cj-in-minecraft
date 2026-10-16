package dev.cjmc;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Submits one skinned part to the entity pass. Triangles go out as quads whose last vertex is repeated: the entity layers draw quads. */
final class MeshEmitter {
    private MeshEmitter() {}

    static void emit(MatrixStack.Entry entry, VertexConsumerProvider vcp, GlbModel.Part part, float[] pos, float[] nrm,
                     int light, Identifier texture) {
        emit(entry, vcp, part, pos, nrm, light, RenderLayer.getEntityCutoutNoCull(texture));
    }

    /** The same with the layer chosen by the caller (a vehicle's glass is drawn translucent). */
    static void emit(MatrixStack.Entry entry, VertexConsumerProvider vcp, GlbModel.Part part, float[] pos, float[] nrm,
                     int light, RenderLayer layer) {
        VertexConsumer out = vcp.getBuffer(layer);
        float[] uv = part.uvs();
        int[] idx = part.indices();
        for (int t = 0; t < idx.length; t += 3) {
            for (int corner = 0; corner < 4; corner++) {
                int i = idx[t + Math.min(corner, 2)];
                out.vertex(entry, pos[3 * i], pos[3 * i + 1], pos[3 * i + 2])
                        .color(255, 255, 255, 255)
                        .texture(uv[2 * i], uv[2 * i + 1])
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(light)
                        .normal(entry, nrm[3 * i], nrm[3 * i + 1], nrm[3 * i + 2]);
            }
        }
    }
}
