package dev.cjmc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityPose;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws CJ in place of the local player. The network thread only calls onPose; everything that touches OpenGL
 * (texture upload, vertex submission) runs on the render thread.
 */
public final class CjRenderer {
    public static final CjRenderer INSTANCE = new CjRenderer();
    private static final Logger LOG = LoggerFactory.getLogger("cj_fabric");

    private volatile GlbModel model;
    private volatile PoseBuffer poses;
    private volatile String problem;
    private volatile boolean axes;
    private volatile boolean aiming;
    private volatile WeaponRig weapons;
    private volatile VehicleRig vehicle;
    private volatile dev.cjmc.skate.SkateView skateView;
    private volatile List<GlbModel.Part> armParts = List.of(); // CJ's arms only, for first person
    private volatile List<GlbModel.Part> headlessParts = List.of(); // CJ without his head, seen from his eyes in the car
    private volatile int headJoint = -1;
    private static final int HEAD_NODE = 5; // nodeID of CJ's head: his eyes in first person
    private final FirstPersonHolds holds = new FirstPersonHolds();
    private long lastFirstPersonNs;
    private volatile int handJoint = -1;
    private volatile int leftHandJoint = -1;
    private volatile int torsoJoint = -1;
    private static final int HAND_NODE = 24; // nodeID of CJ's right hand
    private static final int LEFT_HAND_NODE = 34; // nodeID of his left hand, where the second gun of the twin pistols goes
    private static final int TORSO_NODE = 3;  // nodeID of CJ's upper torso (Spine1), where the game puts the jetpack
    // how the game places the jetpack on that bone (RenderJetPack): shifted (0.1, 0.08, 0) and turned 90 degrees about Y; /cjhold calibrates it
    private static final float[] JETPACK_OFFSET = {0.1f, 0.08f, 0.0f};
    private static final float[] JETPACK_ROTATION = {0.0f, 90.0f, 0.0f};

    // render-thread state
    private Skinner skinner;
    private final List<Identifier> textures = new ArrayList<>();
    private boolean texturesReady;
    private final BodyFacing facing = new BodyFacing();
    private long lastFrameNs;
    private boolean announced;

    private CjRenderer() {}

    public void init(Path glb, Path weaponsDir, Path vehicleFile) {
        weapons = new WeaponRig(weaponsDir);
        vehicle = new VehicleRig(vehicleFile);
        if (!Files.isRegularFile(glb)) {
            problem = "cj.glb not found - run tools/convert_assets.py";
            LOG.warn("{} not found; run tools/convert_assets.py. The vanilla player will be drawn.", glb);
            return;
        }
        try {
            GlbModel m = GlbModel.read(Files.readAllBytes(glb));
            Map<Integer, Integer> jointOfNode = new HashMap<>();
            for (int j = 0; j < m.jointNodeIds.length; j++) {
                jointOfNode.put(m.jointNodeIds[j], j);
            }
            handJoint = jointOfNode.getOrDefault(HAND_NODE, -1);
            leftHandJoint = jointOfNode.getOrDefault(LEFT_HAND_NODE, -1);
            torsoJoint = jointOfNode.getOrDefault(TORSO_NODE, -1);
            headJoint = jointOfNode.getOrDefault(HEAD_NODE, -1);
            armParts = FirstPersonArms.armParts(m);
            headlessParts = FirstPersonArms.headlessParts(m);
            if (!FirstPersonArms.any(armParts)) {
                LOG.warn("cj.glb has no arm triangles: first person keeps Minecraft's hand");
            }
            poses = new PoseBuffer(jointOfNode, m.jointNodeIds.length);
            skateView = dev.cjmc.skate.SkateView.load(glb.resolveSibling("skate_rig.json"), m);
            model = m;
            LOG.info("loaded {}: {} parts, {} joints, {} textures", glb, m.parts.size(), m.jointNodeIds.length, m.images.size());
        } catch (IOException | RuntimeException e) {
            problem = "cj.glb is unreadable: " + e.getMessage();
            LOG.error("could not read {}", glb, e);
        }
    }

    public boolean hasModel() {
        return model != null;
    }

    /** A short reason why CJ is not being drawn, or null. */
    public String problem() {
        return problem;
    }

    /** Called from the network thread. */
    public void onPose(Protocol.Pose pose, long nowNs) {
        PoseBuffer buffer = poses;
        if (buffer != null) {
            buffer.push(pose, nowNs);
        }
    }

    /** True while the player aims or fires one of our weapons: the body then faces the look direction at once. */
    public boolean isAiming() {
        return aiming;
    }

    public void setAiming(boolean aiming) {
        this.aiming = aiming;
    }

    /** Replaces the hold (offset xyz, rotation xyz in degrees) of a weapon until the game closes: for calibrating by eye. */
    public void setHold(String weaponId, float[] six) {
        WeaponRig rig = weapons;
        if (rig != null) {
            rig.setHold(weaponId, six);
        }
    }

    public boolean toggleAxes() {
        axes = !axes;
        return axes;
    }

    /** Returns true when CJ was drawn and the vanilla player must not be. */
    public boolean render(AbstractClientPlayerEntity player, float tickDelta, MatrixStack matrices,
                          VertexConsumerProvider vcp, int light) {
        GlbModel m = model;
        PoseBuffer buffer = poses;
        if (m == null || buffer == null) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (player != client.player) {
            return false; // only the local player becomes CJ
        }
        if (preview) {
            return renderPreview(player, matrices, vcp, light, m, buffer, client);
        }
        dev.cjmc.skate.SkateProtocol.Pose skating = dev.cjmc.skate.SkateRide.pose();
        if (skating != null && skateView != null) {
            return renderSkating(player, skating, matrices, vcp, light, m, client);
        }
        VehicleRig car = vehicle;
        if (VehicleClient.driving() && car != null && car.ready()) {
            return renderInCar(car, tickDelta, matrices, vcp, light, m, buffer, client, m.parts, true);
        }
        if (!isPlainlyStanding(player)) {
            return false; // special poses (sleeping, riding, swimming...) stay vanilla
        }
        long now = System.nanoTime();
        JointFrame frame = buffer.sample(now);
        if (frame == null) {
            return false; // no fresh pose (GTA closed or not loaded): the vanilla player shows
        }
        boolean pushed = false;
        try {
            if (!texturesReady) {
                uploadTextures(client, m);
            }
            if (skinner == null) {
                skinner = new Skinner(m);
            }
            skinner.skin(frame);

            Vec3d v = player.getVelocity(); // blocks per tick
            // While aiming, GTA twists CJ's torso relative to the way the GTA ped faces, and the pose is relative to that
            // heading, so the body is drawn facing the ped's heading (not the look yaw) for the aim to land where the player looks.
            boolean aimingNow = aiming;
            float facingYaw = aimingNow ? Headings.mcYawFromGta(frame.heading) : player.getYaw(tickDelta);
            float yaw = facing.update((float) (v.x * 20.0), (float) (v.z * 20.0), facingYaw,
                    lastFrameNs == 0 ? 0 : now - lastFrameNs, aimingNow);
            lastFrameNs = now;

            matrices.push();
            pushed = true;
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
            if (JetpackClient.flying() && JetpackClient.tiltDegrees() > 0.01f) { // the body leans toward where the jetpack carries him, about his feet
                // in the axes of the body that is drawn (`yaw`, which turns toward where he goes), not of the camera
                double[] lean = dev.cjmc.jetpack.JetpackRules.localLean(JetpackClient.tiltDirX(), JetpackClient.tiltDirZ(), yaw);
                matrices.multiply(new org.joml.Quaternionf().rotationAxis((float) Math.toRadians(JetpackClient.tiltDegrees()),
                        (float) lean[1], 0.0f, (float) -lean[0]));
            }
            float[] rootMotion = ClimbController.rootMotion(); // a climb plays in place: the player makes the movement (model x is forward, z up)
            if (rootMotion != null) {
                matrices.translate(0.0f, -rootMotion[1], -rootMotion[0]);
            }
            float swimForward = SwimClient.rootForward(); // the swimming animations move him ahead on their own: he swims in place
            if (!Float.isNaN(swimForward)) {
                matrices.translate(0.0f, 0.0f, -swimForward);
            }
            MatrixStack.Entry entry = matrices.peek();
            for (int p = 0; p < m.parts.size(); p++) {
                MeshEmitter.emit(entry, vcp, m.parts.get(p), skinner.positions[p], skinner.normals[p], light, textures.get(m.parts.get(p).image()));
            }
            WeaponCatalog.WeaponDef held = CjItems.weaponOf(player.getMainHandStack());
            WeaponRig rig = weapons;
            if (held != null && rig != null && handJoint >= 0) {
                rig.draw(held, frame, handJoint, m.groundOffset, entry, vcp, light);
                if (leftHandJoint >= 0 && CjClient.dualWielding(held.gta())) { // the twin pistols: a second gun in the other hand
                    rig.drawLeft(held, frame, leftHandJoint, m.groundOffset, entry, vcp, light);
                }
            }
            if (CjItems.isWearingJetpack(player) && rig != null && torsoJoint >= 0) { // the jetpack of GTA, on his back
                float[] hold = rig.overrideOf("jetpack");
                float[] offset = hold != null ? new float[] {hold[0], hold[1], hold[2]} : JETPACK_OFFSET;
                float[] rotation = hold != null ? new float[] {hold[3], hold[4], hold[5]} : JETPACK_ROTATION;
                rig.drawModel("jetpack", frame, torsoJoint, offset, rotation, m.groundOffset, entry, vcp, light);
            }
            if (axes) {
                drawAxes(entry, vcp.getBuffer(RenderLayer.getLines()), m.groundOffset);
            }
            matrices.pop();
            pushed = false;
            if (!announced) {
                announced = true;
                LOG.info("drawing CJ in place of the local player");
            }
            return true;
        } catch (RuntimeException e) {
            if (pushed) {
                matrices.pop();
            }
            problem = "CJ renderer disabled: " + e;
            model = null; // never fail the same way every frame
            LOG.error("drawing CJ failed; going back to the vanilla player", e);
            return false;
        }
    }

    /** On the skateboard: CJ in the pose of the skater of the engine, and the board under him. */
    private boolean renderSkating(AbstractClientPlayerEntity player, dev.cjmc.skate.SkateProtocol.Pose pose, MatrixStack matrices, VertexConsumerProvider vcp, int light, GlbModel m,
                                  MinecraftClient client) {
        boolean pushed = false;
        try {
            if (!texturesReady) {
                uploadTextures(client, m);
            }
            if (skinner == null) {
                skinner = new Skinner(m);
            }
            skinner.skin(skateView.frameFor(pose), 0.0f); // the origin of the pose is the centre of the board, not CJ's feet
            matrices.push();
            pushed = true;
            dev.cjmc.skate.SkateView.toSkater(matrices, pose, player.getX(), player.getY(), player.getZ());
            MatrixStack.Entry entry = matrices.peek();
            for (int p = 0; p < m.parts.size(); p++) {
                MeshEmitter.emit(entry, vcp, m.parts.get(p), skinner.positions[p], skinner.normals[p], light, textures.get(m.parts.get(p).image()));
            }
            matrices.pop();
            pushed = false;
            skateView.drawBoard(matrices, pose, player.getX(), player.getY(), player.getZ(), vcp, light);
            return true;
        } catch (RuntimeException e) {
            if (pushed) {
                matrices.pop();
            }
            problem = "CJ renderer disabled: " + e;
            model = null;
            LOG.error("drawing CJ on the skateboard failed; going back to the vanilla player", e);
            return false;
        }
    }

    /** First person shows CJ's arms: a fresh pose of GTA, his arms in the model, not skating nor driving, and a weapon of the mod (gun or bat) or an empty main hand. */
    public boolean firstPersonWanted(AbstractClientPlayerEntity player) {
        if (model == null || headJoint < 0 || !FirstPersonArms.any(armParts) || !CjEffectsClient.cjActive()
                || dev.cjmc.skate.SkateRide.active() || VehicleClient.driving()) {
            return false;
        }
        WeaponCatalog.WeaponDef held = CjItems.weaponOf(player.getMainHandStack());
        return held != null ? FirstPersonHolds.kindOf(held.id()) != null : player.getMainHandStack().isEmpty();
    }

    /**
     * CJ's arms and weapon seen from his eyes, in the camera's space the hand renderer gives: GTA's pose, the body turned as in third person, the head at the eye,
     * and, while he does not aim, the hold of the kind of weapon. False (Minecraft draws its hand) when there is nothing to draw.
     */
    public boolean renderFirstPerson(AbstractClientPlayerEntity player, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        GlbModel m = model;
        PoseBuffer buffer = poses;
        if (m == null || buffer == null) {
            return false;
        }
        long now = System.nanoTime();
        JointFrame frame = buffer.sample(now);
        if (frame == null) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        boolean pushed = false;
        try {
            if (!texturesReady) {
                uploadTextures(client, m);
            }
            if (skinner == null) {
                skinner = new Skinner(m);
            }
            skinner.skin(frame);
            // the body turns as in third person (the player model is not drawn in first person, so its facing is updated here)
            Vec3d v = player.getVelocity();
            boolean aimingNow = aiming;
            float facingYaw = aimingNow ? Headings.mcYawFromGta(frame.heading) : player.getYaw(tickDelta);
            float bodyYaw = facing.update((float) (v.x * 20.0), (float) (v.z * 20.0), facingYaw, lastFrameNs == 0 ? 0 : now - lastFrameNs, aimingNow);
            lastFrameNs = now;
            WeaponCatalog.WeaponDef held = CjItems.weaponOf(player.getMainHandStack());
            FirstPersonHolds.Kind kind = FirstPersonHolds.kindOf(held == null ? null : held.id());
            double seconds = lastFirstPersonNs == 0 ? 0 : Math.min(0.1, (now - lastFirstPersonNs) / 1e9);
            lastFirstPersonNs = now;
            float amount = holds.blend(aimingNow, seconds);
            float[] head = FirstPersonPlacement.headInModel(frame, headJoint, m.groundOffset);
            float[] place = FirstPersonPlacement.modelToCamera(player.getYaw(tickDelta), player.getPitch(tickDelta), bodyYaw, head);
            float[] hold = FirstPersonHolds.holdMatrix(holds.hold(kind), amount);

            matrices.push();
            pushed = true;
            matrices.multiplyPositionMatrix(new org.joml.Matrix4f().set(hold));
            matrices.multiplyPositionMatrix(new org.joml.Matrix4f().set(place));
            MatrixStack.Entry entry = matrices.peek();
            List<GlbModel.Part> arms = armParts;
            for (int p = 0; p < arms.size(); p++) {
                if (arms.get(p).indices().length > 0) {
                    MeshEmitter.emit(entry, vcp, arms.get(p), skinner.positions[p], skinner.normals[p], light, textures.get(arms.get(p).image()));
                }
            }
            WeaponRig rig = weapons;
            if (held != null && rig != null && handJoint >= 0) {
                rig.draw(held, frame, handJoint, m.groundOffset, entry, vcp, light);
                if (leftHandJoint >= 0 && CjClient.dualWielding(held.gta())) {
                    rig.drawLeft(held, frame, leftHandJoint, m.groundOffset, entry, vcp, light);
                }
            }
            matrices.pop();
            pushed = false;
            return true;
        } catch (RuntimeException e) {
            if (pushed) {
                matrices.pop();
            }
            problem = "CJ renderer disabled: " + e;
            model = null;
            LOG.error("drawing CJ's arms in first person failed; going back to Minecraft's hand", e);
            return false;
        }
    }

    /** `/cj fp`: the hold of a kind (x, y, z, pitch, yaw, roll) until the game closes. */
    public void setFirstPersonHold(FirstPersonHolds.Kind kind, float[] six) {
        holds.setHold(kind, six);
    }

    public float[] firstPersonHold(FirstPersonHolds.Kind kind) {
        return holds.hold(kind);
    }

    /**
     * At the wheel in first person (the player is not drawn then, so neither is the car): the car and CJ without his head, at the player's spot, in the frame the
     * world is drawn in (camera at the origin). True when drawn.
     */
    public boolean renderCarFromInside(AbstractClientPlayerEntity player, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, Vec3d camera, int light) {
        GlbModel m = model;
        PoseBuffer buffer = poses;
        VehicleRig car = vehicle;
        if (m == null || buffer == null || car == null || !car.ready() || !VehicleClient.driving()) {
            return false;
        }
        Vec3d at = player.getLerpedPos(tickDelta);
        matrices.push();
        try {
            matrices.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
            return renderInCar(car, tickDelta, matrices, vcp, light, m, buffer, MinecraftClient.getInstance(), headlessParts, false); // from inside, the glass only blurs the road
        } finally {
            matrices.pop();
        }
    }

    /** Where CJ's eyes are at the wheel, in the world, or null (no pose, no car): the camera goes there in first person. */
    public Vec3d carEyes(AbstractClientPlayerEntity player, float tickDelta) {
        GlbModel m = model;
        PoseBuffer buffer = poses;
        VehicleRig car = vehicle;
        JointFrame frame = buffer == null ? null : buffer.sample(System.nanoTime());
        if (m == null || frame == null || car == null || !car.ready() || headJoint < 0 || !VehicleClient.driving()) {
            return null;
        }
        dev.cjmc.vehicle.VehicleSession.Pose pose = VehicleClient.pose(tickDelta);
        boolean seated = pose.seatRight() != 0 || pose.seatForward() != 0 || pose.seatUp() != 0;
        float[] seat = seated ? new float[] {(float) -pose.seatRight(), (float) pose.seatUp(), (float) pose.seatForward()} : car.defaultSeat();
        float[] head = FirstPersonPlacement.headInModel(frame, headJoint, 0f); // in the car the ped's origin is the seat
        head[1] += EYES_OVER_HEAD;
        float[] offset = CarView.headOffset(pose, seat, (float) pose.seatHeading(), head);
        Vec3d at = player.getLerpedPos(tickDelta);
        return new Vec3d(at.x + offset[0], at.y + offset[1], at.z + offset[2]);
    }

    /** GTA's head bone is at the base of the skull; the eyes are this much higher. */
    private static final float EYES_OVER_HEAD = 0.08f;

    private volatile boolean preview;

    /** On while the preview of the player in an inventory screen is being drawn (see InventoryScreenMixin): CJ is shown there, facing the way the screen turns the body. */
    public void setPreview(boolean on) {
        preview = on;
    }

    /** The preview of the inventory: CJ in the pose GTA gives him, turned like the body of the player (the screen follows the mouse with it), the vanilla player when there is no pose. */
    private boolean renderPreview(AbstractClientPlayerEntity player, MatrixStack matrices, VertexConsumerProvider vcp, int light, GlbModel m, PoseBuffer buffer,
                                  MinecraftClient client) {
        JointFrame frame = buffer.sample(System.nanoTime());
        if (frame == null) {
            return false;
        }
        boolean pushed = false;
        try {
            if (!texturesReady) {
                uploadTextures(client, m);
            }
            if (skinner == null) {
                skinner = new Skinner(m);
            }
            skinner.skin(frame);
            matrices.push();
            pushed = true;
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-player.bodyYaw)); // the model faces +z at 0; the screen sets the body yaw so the vanilla one faces us at 180
            MatrixStack.Entry entry = matrices.peek();
            for (int p = 0; p < m.parts.size(); p++) {
                MeshEmitter.emit(entry, vcp, m.parts.get(p), skinner.positions[p], skinner.normals[p], light, textures.get(m.parts.get(p).image()));
            }
            WeaponCatalog.WeaponDef held = CjItems.weaponOf(player.getMainHandStack());
            WeaponRig rig = weapons;
            if (held != null && rig != null && handJoint >= 0) {
                rig.draw(held, frame, handJoint, m.groundOffset, entry, vcp, light);
            }
            matrices.pop();
            pushed = false;
            return true;
        } catch (RuntimeException e) {
            if (pushed) {
                matrices.pop();
            }
            problem = "CJ renderer disabled: " + e;
            model = null;
            LOG.error("drawing CJ in the inventory failed; going back to the vanilla player", e);
            return false;
        }
    }

    /** At the wheel: the car of GTA, with CJ sitting in it where GTA seats him, in the pose GTA gives him (the car alone while there is no fresh pose). */
    private boolean renderInCar(VehicleRig car, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light, GlbModel m, PoseBuffer buffer,
                                MinecraftClient client, List<GlbModel.Part> cjParts, boolean glass) {
        dev.cjmc.vehicle.VehicleSession.Pose pose = VehicleClient.pose(tickDelta);
        JointFrame frame = buffer.sample(System.nanoTime());
        boolean pushed = false;
        try {
            matrices.push();
            pushed = true;
            VehicleRig.transform(matrices, pose);
            car.drawParts(matrices, pose, vcp, light, VehicleClient.lightsOn());
            if (frame != null) {
                if (!texturesReady) {
                    uploadTextures(client, m);
                }
                if (skinner == null) {
                    skinner = new Skinner(m);
                }
                skinner.skin(frame, 0.0f); // the origin of the ped is where he sits, not his feet on the ground
                boolean seated = pose.seatRight() != 0 || pose.seatForward() != 0 || pose.seatUp() != 0;
                float[] seat = seated ? new float[] {(float) -pose.seatRight(), (float) pose.seatUp(), (float) pose.seatForward()} : car.defaultSeat();
                matrices.push();
                matrices.translate(seat[0], seat[1], seat[2]);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) Math.toDegrees(pose.seatHeading()))); // counter-clockwise from the car's nose
                MatrixStack.Entry entry = matrices.peek();
                for (int p = 0; p < cjParts.size(); p++) {
                    if (cjParts.get(p).indices().length > 0) {
                        MeshEmitter.emit(entry, vcp, cjParts.get(p), skinner.positions[p], skinner.normals[p], light, textures.get(cjParts.get(p).image()));
                    }
                }
                matrices.pop();
            }
            if (glass) {
                car.drawGlass(matrices, vcp, light, VehicleClient.lightsOn());
            }
            matrices.pop();
            pushed = false;
            return true;
        } catch (RuntimeException e) {
            if (pushed) {
                matrices.pop();
            }
            problem = "CJ renderer disabled: " + e;
            model = null;
            LOG.error("drawing the car failed; going back to the vanilla player", e);
            return false;
        }
    }

    private static boolean isPlainlyStanding(AbstractClientPlayerEntity player) {
        EntityPose pose = player.getPose();
        // swimming fast under the water puts him in the swimming pose of Minecraft, and CJ is drawn then too (the game would draw its own player)
        return (pose == EntityPose.STANDING || pose == EntityPose.CROUCHING || (pose == EntityPose.SWIMMING && SwimClient.inWater()))
                && !player.isInvisible() && !player.hasVehicle() && !player.isSleeping();
    }

    /** The mean forward (x) and up (z) position of the joints of the latest pose, in the model space of GTA (x forward, y left, z up); null without a pose. */
    float[] skeletonMean() {
        PoseBuffer buffer = poses;
        JointFrame f = buffer == null ? null : buffer.sample(System.nanoTime());
        if (f == null) {
            return null;
        }
        float sx = 0, sz = 0;
        int n = 0;
        for (int j = 0; j < f.has.length; j++) {
            if (f.has[j]) {
                sx += f.pos[3 * j];
                sz += f.pos[3 * j + 2];
                n++;
            }
        }
        return n == 0 ? null : new float[] {sx / n, sz / n};
    }

    /** True when a recent pose is available, i.e. CJ is being drawn (or would be). */
    public boolean hasFreshPose() {
        PoseBuffer buffer = poses;
        return buffer != null && buffer.sample(System.nanoTime()) != null;
    }

    private void uploadTextures(MinecraftClient client, GlbModel m) {
        for (int i = 0; i < m.images.size(); i++) {
            try {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(m.images.get(i)));
                Identifier id = Identifier.of("cj_fabric", "skin/" + i);
                client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
                textures.add(id);
            } catch (IOException e) {
                throw new IllegalStateException("texture " + i + " is not a valid image", e);
            }
        }
        texturesReady = true;
    }

    /** Three arrows at the model's origin, drawn with the same conversion as the mesh: red forward, green left, blue up. */
    private void drawAxes(MatrixStack.Entry entry, VertexConsumer lines, float groundOffset) {
        float[][] axis = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        int[][] colour = {{255, 40, 40}, {40, 255, 40}, {60, 120, 255}};
        for (int a = 0; a < 3; a++) {
            float length = a == 2 ? 1.3f : 0.7f; // the up arrow must clear the head, or the body hides it
            float[] tip = {axis[a][0] * length, axis[a][1] * length, axis[a][2] * length + groundOffset};
            float[] origin = {0, 0, groundOffset};
            float[] dir = {axis[a][0], axis[a][1], axis[a][2]};
            Axes.toMinecraft(tip, 0);
            Axes.toMinecraft(origin, 0);
            Axes.toMinecraft(dir, 0);
            lines.vertex(entry, origin[0], origin[1], origin[2]).color(colour[a][0], colour[a][1], colour[a][2], 255)
                    .normal(entry, dir[0], dir[1], dir[2]);
            lines.vertex(entry, tip[0], tip[1], tip[2]).color(colour[a][0], colour[a][1], colour[a][2], 255)
                    .normal(entry, dir[0], dir[1], dir[2]);
        }
    }
}
