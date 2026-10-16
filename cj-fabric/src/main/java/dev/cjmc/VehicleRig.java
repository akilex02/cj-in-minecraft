package dev.cjmc;

import dev.cjmc.vehicle.VehicleSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws the car of GTA (vehicles/greenwood.glb, made by tools/convert_assets.py --vehicles): the body, and the four wheels spun by the distance driven and
 * turned with the steering. Render thread only. A car whose model cannot be loaded is not drawn (and CJ is drawn the usual way), with one log line.
 */
final class VehicleRig {
    private static final Logger LOG = LoggerFactory.getLogger("cj_fabric");
    private static final float ORIGIN_ABOVE_GROUND = (float) VehicleSession.ORIGIN_ABOVE_GROUND;

    private final Path file;
    private VehicleModel model;
    private final List<Identifier> textures = new ArrayList<>();
    private final List<Boolean> translucent = new ArrayList<>();
    private boolean failed;

    VehicleRig(Path file) {
        this.file = file;
    }

    /** True when the model is loaded (it is read the first time this is asked, on the render thread). */
    boolean ready() {
        if (model != null) {
            return true;
        }
        if (failed) {
            return false;
        }
        try {
            if (!Files.isRegularFile(file)) {
                throw new IOException(file.getFileName() + " not found; run tools/convert_assets.py --vehicles");
            }
            VehicleModel loaded = VehicleModel.from(GlbModel.read(Files.readAllBytes(file)));
            MinecraftClient client = MinecraftClient.getInstance();
            for (int i = 0; i < loaded.source.images.size(); i++) {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(loaded.source.images.get(i)));
                boolean glass = false;
                for (int y = 0; y < image.getHeight() && !glass; y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        if ((image.getColor(x, y) >>> 24) < 250) {
                            glass = true;
                            break;
                        }
                    }
                }
                Identifier id = Identifier.of("cj_fabric", "vehicle/" + file.getFileName().toString().replace('.', '_') + "/" + i);
                client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
                textures.add(id);
                translucent.add(glass);
            }
            model = loaded;
            LOG.info("loaded the vehicle model {}", file.getFileName());
            return true;
        } catch (IOException | RuntimeException e) {
            failed = true;
            LOG.warn("the vehicle {} is not drawn: {}", file.getFileName(), e.getMessage());
            return false;
        }
    }

    /** Where CJ sits in the model space of Minecraft (x left, y up, z forward) when the pose has no seat yet. */
    float[] defaultSeat() {
        return model.driverSeat;
    }

    /** Puts the matrix at the car: turned to its yaw, raised to the centre of the car, and tilted by the ground under it. */
    static void transform(MatrixStack matrices, VehicleSession.Pose pose) {
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) -pose.yaw()));
        matrices.translate(0.0f, ORIGIN_ABOVE_GROUND, 0.0f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotation((float) -pose.pitch())); // nose up
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation((float) -pose.roll()));  // right side up
    }

    /** Draws the glass with the matrix already at the car. It goes last: the glass writes depth, so whatever is drawn after it behind it would vanish (CJ at the wheel). */
    void drawGlass(MatrixStack matrices, VertexConsumerProvider vcp, int light, boolean lightsOn) {
        MatrixStack.Entry car = matrices.peek();
        for (GlbModel.Part part : model.body) {
            if (translucent.get(part.image())) {
                emit(car, vcp, part, light, lightsOn); // the lenses of the lights are glass too
            }
        }
    }

    /** Draws the opaque body and the wheels with the matrix already at the car (see transform). */
    void drawParts(MatrixStack matrices, VehicleSession.Pose pose, VertexConsumerProvider vcp, int light, boolean lightsOn) {
        MatrixStack.Entry car = matrices.peek();
        for (GlbModel.Part part : model.body) {
            if (!translucent.get(part.image())) {
                emit(car, vcp, part, light, lightsOn);
            }
        }
        for (int corner = 0; corner < 4; corner++) {
            float[] at = model.wheelPositions[corner];
            matrices.push();
            matrices.translate(at[0], at[1], at[2]);
            if (VehicleModel.isFront(corner)) {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float) -pose.frontWheelAngle())); // positive is to the right
            }
            if (VehicleModel.isLeft(corner)) {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f)); // the tyre faces out on both sides
                matrices.multiply(RotationAxis.POSITIVE_X.rotation((float) -pose.wheelAngle()));
            } else {
                matrices.multiply(RotationAxis.POSITIVE_X.rotation((float) pose.wheelAngle()));
            }
            MatrixStack.Entry wheel = matrices.peek();
            for (GlbModel.Part part : model.wheels) {
                emit(wheel, vcp, part, light, false);
            }
            matrices.pop();
        }
    }

    private static final int FULL_BRIGHT = 0xF000F0;

    /** With the lights on, the parts that have a lit twin (the headlights and the tail lights) are drawn as it, at full brightness whatever the light around. */
    private void emit(MatrixStack.Entry entry, VertexConsumerProvider vcp, GlbModel.Part part, int light, boolean lightsOn) {
        int image = lightsOn ? model.litImage(part.image()) : part.image();
        if (image != part.image()) {
            light = FULL_BRIGHT;
        }
        Identifier texture = textures.get(image);
        RenderLayer layer = translucent.get(part.image()) ? RenderLayer.getEntityTranslucent(texture) : RenderLayer.getEntityCutoutNoCull(texture);
        MeshEmitter.emit(entry, vcp, part, part.positions(), part.normals(), light, layer);
    }
}
