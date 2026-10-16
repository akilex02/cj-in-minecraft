package dev.cjmc.skate;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** The board as a few boxes of vanilla textures: a deck, two trucks and four wheels, in the frame of the deck (origin at its centre, z along its length, y up). */
final class SkateBoard {
    private static final Identifier DECK = Identifier.of("minecraft", "textures/block/dark_oak_planks.png");
    private static final Identifier TRUCK = Identifier.of("minecraft", "textures/block/iron_block.png");
    private static final Identifier WHEEL = Identifier.of("minecraft", "textures/block/white_concrete.png");

    private SkateBoard() {}

    static void draw(MatrixStack.Entry entry, VertexConsumerProvider vcp, int light) {
        // one layer at a time: asking the provider for another layer ends the buffer of the last one
        box(entry, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(DECK)), light, -0.10f, -0.006f, -0.40f, 0.10f, 0.012f, 0.40f);
        VertexConsumer truck = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TRUCK));
        for (float z : new float[] {-0.259f, 0.259f}) {
            box(entry, truck, light, -0.075f, -0.040f, z - 0.018f, 0.075f, -0.010f, z + 0.018f);
        }
        VertexConsumer wheel = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(WHEEL));
        for (float z : new float[] {-0.259f, 0.259f}) {
            for (float x : new float[] {-0.097f, 0.097f}) {
                box(entry, wheel, light, x - 0.016f, -0.090f, z - 0.034f, x + 0.016f, -0.022f, z + 0.034f);
            }
        }
    }

    private static void box(MatrixStack.Entry e, VertexConsumer out, int light, float x0, float y0, float z0, float x1, float y1, float z1) {
        // each face: four corners counter-clockwise seen from outside, and its normal
        quad(e, out, light, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        quad(e, out, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(e, out, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        quad(e, out, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(e, out, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(e, out, light, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
    }

    private static void quad(MatrixStack.Entry e, VertexConsumer out, int light, float nx, float ny, float nz, float... c) {
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int i = 0; i < 4; i++) {
            out.vertex(e, c[3 * i], c[3 * i + 1], c[3 * i + 2]).color(255, 255, 255, 255).texture(uv[i][0], uv[i][1])
                    .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
        }
    }
}
