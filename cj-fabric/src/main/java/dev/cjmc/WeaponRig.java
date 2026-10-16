package dev.cjmc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Draws the weapon in CJ's hand. Render thread only. A weapon whose model cannot be loaded is drawn as nothing, with one log line. */
final class WeaponRig {
    private static final Logger LOG = LoggerFactory.getLogger("cj_fabric");

    private record Loaded(GlbModel model, Skinner skinner, List<Identifier> textures) {}

    private final Path dir;
    private final Map<String, Loaded> loaded = new HashMap<>();
    private final Set<String> failed = new HashSet<>();
    private final JointFrame frame = new JointFrame(1);
    private volatile Map<String, float[]> overrides = Map.of();

    WeaponRig(Path dir) {
        this.dir = dir;
    }

    /** Replaces the hold (offset xyz, rotation xyz in degrees) of one weapon until the game closes: for calibrating by eye. */
    void setHold(String weaponId, float[] six) {
        Map<String, float[]> copy = new HashMap<>(overrides);
        copy.put(weaponId, six.clone());
        overrides = copy;
    }

    /** The hold set by hand for `id` (offset xyz, rotation xyz), or null. */
    float[] overrideOf(String id) {
        return overrides.get(id);
    }

    void draw(WeaponCatalog.WeaponDef def, JointFrame cj, int handJoint, float groundOffset, MatrixStack.Entry entry,
              VertexConsumerProvider vcp, int light) {
        float[] hold = overrides.get(def.id());
        float[] offset = hold != null ? new float[] {hold[0], hold[1], hold[2]} : def.holdOffset();
        float[] rotation = hold != null ? new float[] {hold[3], hold[4], hold[5]} : def.holdRotation();
        drawModel(def.model(), cj, handJoint, offset, rotation, false, groundOffset, entry, vcp, light);
    }

    /** The second gun of the twin pistols (the top skill with the Colt 45 or the Micro SMG): the same model in the left hand, as the game puts it. */
    void drawLeft(WeaponCatalog.WeaponDef def, JointFrame cj, int leftHandJoint, float groundOffset, MatrixStack.Entry entry,
                  VertexConsumerProvider vcp, int light) {
        float[] hold = overrides.get(def.id());
        float[] offset = hold != null ? new float[] {hold[0], hold[1], hold[2]} : def.holdOffset();
        float[] rotation = hold != null ? new float[] {hold[3], hold[4], hold[5]} : def.holdRotation();
        drawModel(def.model(), cj, leftHandJoint, offset, rotation, true, groundOffset, entry, vcp, light);
    }

    /** Draws the model `model` (a one-joint file of the weapons folder) on the joint `joint` of CJ's pose, shifted and turned in that joint's frame. */
    void drawModel(String model, JointFrame cj, int joint, float[] offset, float[] rotation, float groundOffset, MatrixStack.Entry entry,
                   VertexConsumerProvider vcp, int light) {
        drawModel(model, cj, joint, offset, rotation, false, groundOffset, entry, vcp, light);
    }

    private void drawModel(String model, JointFrame cj, int joint, float[] offset, float[] rotation, boolean leftHand, float groundOffset,
                           MatrixStack.Entry entry, VertexConsumerProvider vcp, int light) {
        Loaded l = get(model);
        if (l == null) {
            return;
        }
        if (leftHand) {
            WeaponAttach.leftHandFrame(cj, joint, offset, rotation, frame);
        } else {
            WeaponAttach.handFrame(cj, joint, offset, rotation, frame);
        }
        if (!frame.has[0]) {
            return;
        }
        l.skinner().skin(frame, groundOffset);
        for (int p = 0; p < l.model().parts.size(); p++) {
            GlbModel.Part part = l.model().parts.get(p);
            MeshEmitter.emit(entry, vcp, part, l.skinner().positions[p], l.skinner().normals[p], light, l.textures().get(part.image()));
        }
    }

    private Loaded get(String model) {
        Loaded l = loaded.get(model);
        if (l != null || failed.contains(model)) {
            return l;
        }
        WeaponModelLoader.Result r = WeaponModelLoader.read(dir.resolve(model + ".glb"));
        if (r.model() == null) {
            failed.add(model);
            LOG.warn("weapon model not drawn: {}", r.problem());
            return null;
        }
        try {
            List<Identifier> textures = new ArrayList<>();
            MinecraftClient client = MinecraftClient.getInstance();
            for (int i = 0; i < r.model().images.size(); i++) {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(r.model().images.get(i)));
                Identifier id = Identifier.of("cj_fabric", "weapon/" + model + "/" + i);
                client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
                textures.add(id);
            }
            l = new Loaded(r.model(), new Skinner(r.model()), textures);
            loaded.put(model, l);
            LOG.info("loaded weapon model {}", model);
            return l;
        } catch (IOException | RuntimeException e) {
            failed.add(model);
            LOG.warn("weapon model {} not drawn: its texture is unreadable: {}", model, e.getMessage());
            return null;
        }
    }
}
