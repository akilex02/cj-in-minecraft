package dev.cjmc;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Locale;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CjClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("cj_fabric");

    private static final int GTA_PORT = 34560;
    private static final int ECHO_PORT = 34561;
    private static final long HUD_INTERVAL_NS = 500_000_000L;
    private static final long ECHO_STALE_NS = 1_000_000_000L;

    private static CjClient instance;
    private DatagramSocket socket;
    private InetAddress loopback;
    private int seq;
    private final SendScheduler scheduler = new SendScheduler(1_000_000_000L / 60);
    private long nextHudNs;
    private volatile Protocol.Echo lastEcho;
    private volatile long lastEchoNs;
    private static volatile long lastEchoStaticNs;
    private static volatile Protocol.Echo lastEchoStatic;

    @Override
    public void onInitializeClient() {
        instance = this;
        VehicleClient.init();
        CrouchClient.init();
        dev.cjmc.skate.SkateRide.install();
        infoKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(new net.minecraft.client.option.KeyBinding("key.cj_fabric.info",
                net.minecraft.client.util.InputUtil.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_F7, "key.categories.cj_fabric"));
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(dev.cjmc.combat.CjEntities.ROCKET, RocketRenderer::new);
        java.nio.file.Path configDir = FabricLoader.getInstance().getConfigDir().resolve("cj_fabric");
        CjRenderer.INSTANCE.init(configDir.resolve("cj.glb"), configDir.resolve("weapons"), configDir.resolve("vehicles").resolve("greenwood.glb"));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("cj")
                        .then(ClientCommandManager.literal("axes").executes(context -> {
                            boolean on = CjRenderer.INSTANCE.toggleAxes();
                            context.getSource().sendFeedback(Text.literal("CJ axes " + (on ? "on" : "off")
                                    + " (red = forward, green = left, blue = up)"));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("hold")
                                .then(ClientCommandManager.argument("x", FloatArgumentType.floatArg())
                                .then(ClientCommandManager.argument("y", FloatArgumentType.floatArg())
                                .then(ClientCommandManager.argument("z", FloatArgumentType.floatArg())
                                .then(ClientCommandManager.argument("rx", FloatArgumentType.floatArg())
                                .then(ClientCommandManager.argument("ry", FloatArgumentType.floatArg())
                                .then(ClientCommandManager.argument("rz", FloatArgumentType.floatArg())
                                        .executes(context -> {
                                            MinecraftClient mc = MinecraftClient.getInstance();
                                            WeaponCatalog.WeaponDef held = mc.player == null ? null : CjItems.weaponOf(mc.player.getMainHandStack());
                                            String id = held != null ? held.id() : CjItems.isWearingJetpack(mc.player) ? "jetpack" : null;
                                            if (id == null) {
                                                context.getSource().sendFeedback(Text.literal("Hold one of the CJ weapons (or wear the jetpack) first"));
                                                return 0;
                                            }
                                            float[] six = {FloatArgumentType.getFloat(context, "x"), FloatArgumentType.getFloat(context, "y"),
                                                    FloatArgumentType.getFloat(context, "z"), FloatArgumentType.getFloat(context, "rx"),
                                                    FloatArgumentType.getFloat(context, "ry"), FloatArgumentType.getFloat(context, "rz")};
                                            CjRenderer.INSTANCE.setHold(id, six);
                                            context.getSource().sendFeedback(Text.literal(String.format(Locale.ROOT,
                                                    "%s hold set. In weapons.json: \"holdOffset\": [%s, %s, %s], \"holdRotation\": [%s, %s, %s]",
                                                    id, six[0], six[1], six[2], six[3], six[4], six[5])));
                                            return 1;
                                        }))))))))
                        .then(ClientCommandManager.literal("fp")
                                .then(ClientCommandManager.argument("kind", StringArgumentType.word())
                                        .executes(context -> {
                                            FirstPersonHolds.Kind kind = FirstPersonHolds.parseKind(StringArgumentType.getString(context, "kind"));
                                            if (kind == null) {
                                                context.getSource().sendFeedback(Text.literal("Kinds: pistol, rifle, heavy, bat, fists"));
                                                return 0;
                                            }
                                            float[] h = CjRenderer.INSTANCE.firstPersonHold(kind);
                                            context.getSource().sendFeedback(Text.literal(String.format(Locale.ROOT, "%s first-person hold: %s %s %s %s %s %s",
                                                    kind.name().toLowerCase(Locale.ROOT), h[0], h[1], h[2], h[3], h[4], h[5])));
                                            return 1;
                                        })
                                        .then(ClientCommandManager.argument("x", FloatArgumentType.floatArg())
                                        .then(ClientCommandManager.argument("y", FloatArgumentType.floatArg())
                                        .then(ClientCommandManager.argument("z", FloatArgumentType.floatArg())
                                        .then(ClientCommandManager.argument("pitch", FloatArgumentType.floatArg())
                                        .then(ClientCommandManager.argument("yaw", FloatArgumentType.floatArg())
                                        .then(ClientCommandManager.argument("roll", FloatArgumentType.floatArg())
                                                .executes(context -> {
                                                    FirstPersonHolds.Kind kind = FirstPersonHolds.parseKind(StringArgumentType.getString(context, "kind"));
                                                    if (kind == null) {
                                                        context.getSource().sendFeedback(Text.literal("Kinds: pistol, rifle, heavy, bat, fists"));
                                                        return 0;
                                                    }
                                                    float[] six = {FloatArgumentType.getFloat(context, "x"), FloatArgumentType.getFloat(context, "y"),
                                                            FloatArgumentType.getFloat(context, "z"), FloatArgumentType.getFloat(context, "pitch"),
                                                            FloatArgumentType.getFloat(context, "yaw"), FloatArgumentType.getFloat(context, "roll")};
                                                    CjRenderer.INSTANCE.setFirstPersonHold(kind, six);
                                                    context.getSource().sendFeedback(Text.literal(String.format(Locale.ROOT,
                                                            "%s first-person hold set (in FirstPersonHolds.DEFAULTS: {%sf, %sf, %sf, %sf, %sf, %sf})",
                                                            kind.name().toLowerCase(Locale.ROOT), six[0], six[1], six[2], six[3], six[4], six[5])));
                                                    return 1;
                                                })))))))))));
        try {
            loopback = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
            socket = new DatagramSocket(new InetSocketAddress(loopback, ECHO_PORT));
        } catch (IOException e) {
            LOG.error("cannot open the bridge socket on 127.0.0.1:{} (is the fake client running?)", ECHO_PORT, e);
            return;
        }
        Thread receiver = new Thread(this::receiveLoop, "cj-bridge-rx");
        receiver.setDaemon(true);
        receiver.start();
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(context -> dev.cjmc.skate.SkateRide.beforeFrame());
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(context -> turnViewWithCar());
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && mc.world != null && mc.options.getPerspective().isFirstPerson() && VehicleClient.driving()) {
                float delta = context.tickCounter().getTickDelta(false);
                Vec3d eyes = mc.player.getLerpedPos(delta).add(0, 1.0, 0);
                int light = net.minecraft.client.render.WorldRenderer.getLightmapCoordinates(mc.world, net.minecraft.util.math.BlockPos.ofFloored(eyes));
                CjRenderer.INSTANCE.renderCarFromInside(mc.player, delta, context.matrixStack(), context.consumers(), context.camera().getPos(), light);
            }
        });
        WorldRenderEvents.END.register(context -> { onFrame(); dev.cjmc.skate.SkateRide.onFrame(); });
        ClientTickEvents.END_CLIENT_TICK.register(ShotEffects.INSTANCE::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                ClimbController.tick(client.player, System.nanoTime() / 1_000_000L);
                CrouchClient.tick(client.player);
                dev.cjmc.skate.SkateRide.tick(client.player);
                JetpackClient.tick(client.player);
                SwimClient.tick(client.player);
                VehicleClient.tick(client.player, System.nanoTime() / 1_000_000L);
            }
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClimbController.cancel();
            VehicleClient.cancel();
            dev.cjmc.skate.SkateRide.cancel();
        });
        CjEffectsClient.register();
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(CjCrosshair::draw);
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(FuelHud::draw);
        LOG.info("bridge ready: state -> 127.0.0.1:{}, echo and pose <- 127.0.0.1:{}", GTA_PORT, ECHO_PORT);
    }

    private static float lastCarYaw = Float.NaN;

    /** In first person at the wheel the view turns as the car turns (as in a boat), so the road stays ahead without the mouse. */
    private static void turnViewWithCar() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !VehicleClient.driving() || !mc.options.getPerspective().isFirstPerson()) {
            lastCarYaw = Float.NaN;
            return;
        }
        float carYaw = (float) VehicleClient.pose(mc.getRenderTickCounter().getTickDelta(false)).yaw();
        if (!Float.isNaN(lastCarYaw)) {
            float turn = CarView.yawDelta(lastCarYaw, carYaw);
            mc.player.setYaw(mc.player.getYaw() + turn);
            mc.player.prevYaw += turn;
        }
        lastCarYaw = carYaw;
    }

    /** Sends a datagram to the bridge (the controls of the car, the requests to get in and out). */
    static void sendToGta(byte[] data) {
        CjClient client = instance;
        if (client == null || client.socket == null) {
            return;
        }
        try {
            client.socket.send(new DatagramPacket(data, data.length, client.loopback, GTA_PORT));
        } catch (IOException e) {
            LOG.warn("send failed", e);
        }
    }

    /**
     * CJ holds two guns of weapon `gtaWeapon` (the game's twin pistols, at the top skill): GTA says so in its echo, and only while it answers and holds
     * that very weapon (the item in hand may be a moment ahead of the game's).
     */
    public static boolean dualWielding(int gtaWeapon) {
        Protocol.Echo echo = lastEchoStatic;
        return echo != null && bridgeLive() && echo.dualWield() && echo.weaponHeld() == gtaWeapon;
    }

    /** True while GTA is answering: only then does a bare-handed punch replace the vanilla attack (without GTA nothing would hit). */
    public static boolean bridgeLive() {
        long last = lastEchoStaticNs;
        return last != 0 && System.nanoTime() - last < ECHO_STALE_NS;
    }

    private void receiveLoop() {
        byte[] buffer = new byte[Protocol.MAX_DATAGRAM];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        while (!socket.isClosed()) {
            try {
                packet.setLength(buffer.length);
                socket.receive(packet);
                long now = System.nanoTime();
                int len = packet.getLength();
                Protocol.Pose pose = Protocol.decodePose(buffer, len);
                if (pose != null) {
                    CjRenderer.INSTANCE.onPose(pose, now);
                    continue;
                }
                Protocol.Echo echo = Protocol.decodeEcho(buffer, len);
                if (echo != null) {
                    lastEcho = echo;
                    lastEchoNs = now;
                    lastEchoStaticNs = now;
                    lastEchoStatic = echo;
                    continue;
                }
                Protocol.Shot shot = Protocol.decodeShot(buffer, len);
                if (shot != null) {
                    ShotEffects.INSTANCE.onShot(shot);
                    continue;
                }
                Protocol.VehicleState vehicle = Protocol.decodeVehicleState(buffer, len);
                if (vehicle != null) {
                    VehicleClient.onState(vehicle, now);
                }
            } catch (IOException e) {
                if (socket.isClosed()) {
                    return;
                }
                LOG.warn("receive failed", e);
            }
        }
    }

    /** The dry-fire intent: the button held on a weapon with no bullets (GTA plays its own click, once per press). */
    private static int dryIntent(MinecraftClient client, WeaponCatalog.WeaponDef weapon, boolean melee, boolean attackDown,
                                 String ammo, int bullets, boolean freeAmmo) {
        boolean dry = weapon != null && !melee && client.currentScreen == null && !BodyClient.frozen()
                && AmmoRules.dryFire(attackDown, ammo, bullets, freeAmmo);
        return dry ? Protocol.INTENT_DRY : 0;
    }

    private void onFrame() {
        long now = System.nanoTime();
        if (!scheduler.shouldSend(now)) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }

        float delta = client.getRenderTickCounter().getTickDelta(false);
        Vec3d pos = player.getLerpedPos(delta);
        Vec3d vel = player.getVelocity().multiply(20.0); // blocks/tick -> blocks/s
        int flags = 0;
        if (player.isOnGround()) flags |= Protocol.FLAG_ON_GROUND;
        if (player.isSprinting()) flags |= Protocol.FLAG_SPRINT;
        if (player.isSneaking()) flags |= Protocol.FLAG_SNEAK;
        if (player.isSwimming() || SwimClient.crawling()) flags |= Protocol.FLAG_SWIM; // the crawl: Minecraft's fast swimming pose, or the run key in the water
        if (player.isClimbing()) flags |= Protocol.FLAG_CLIMBING;

        WeaponCatalog.WeaponDef weapon = CjItems.activeWeapon(player);
        boolean melee = weapon != null && weapon.reach() > 0f;
        boolean attackDown = client.options.attackKey.isPressed();
        String ammo = weapon == null ? "" : weapon.ammo();
        int bullets = AmmoRules.usesAmmo(ammo) ? AmmoInventory.count(player, ammo) : 0;
        boolean freeAmmo = AmmoInventory.isFree(player);
        int intents = ControlsMapper.intents(weapon != null && !melee, melee, client.currentScreen != null,
                attackDown && AmmoRules.canFire(ammo, bullets, freeAmmo), client.options.useKey.isPressed(), BodyClient.frozen());
        float[] aim = {0f, 0f, 0f};
        if (weapon != null && !melee && intents != 0) { // a blow needs no aim vector
            Vec3d eye = player.getCameraPosVec(delta);
            Vec3d look = player.getRotationVec(delta);
            HitResult hit = player.raycast(AimRay.MAX_DISTANCE, delta, false);
            double hitDistance = hit.getType() == HitResult.Type.MISS ? Double.NaN : hit.getPos().distanceTo(eye);
            aim = AimRay.aimVector(new double[] {pos.x, pos.y, pos.z}, new double[] {eye.x, eye.y, eye.z},
                    new double[] {look.x, look.y, look.z}, hitDistance);
        }
        CjRenderer.INSTANCE.setAiming(weapon != null && intents != 0);

        flags |= BodyClient.flags(player, now / 1_000_000L); // falling, knocked, dead and the hurt counter
        if (player.isUsingItem() && player.getActiveItem().getUseAction() == net.minecraft.util.UseAction.EAT) { // food in use: CJ eats with the animation of GTA
            flags |= EatKinds.flags(EatKinds.kindOf(net.minecraft.registry.Registries.ITEM.getId(player.getActiveItem().getItem()).getPath()));
        }

        Protocol.State state = new Protocol.State(seq++, (int) (now / 1_000_000L),
                (float) pos.x, (float) pos.y, (float) pos.z,
                (float) vel.x, (float) vel.y, (float) vel.z,
                player.getYaw(delta), player.getPitch(delta), flags, intents | jumpIntent(player) | crouchIntent() | dryIntent(client, weapon, melee, attackDown, ammo, bullets, freeAmmo),
                (short) (weapon == null ? 0 : weapon.gta()), aim[0], aim[1], aim[2]);
        byte[] data = Protocol.encodeState(state);
        try {
            socket.send(new DatagramPacket(data, data.length, loopback, GTA_PORT));
        } catch (IOException e) {
            LOG.warn("send failed", e);
        }

        if (now >= nextHudNs) {
            nextHudNs = now + HUD_INTERVAL_NS;
            showHud(player, now);
        }
    }

    /** The crouch intent: the player is crouched, and GTA's own crouch follows it (the bridge presses the duck button). */
    private static int crouchIntent() {
        return CrouchClient.crouching() ? Protocol.INTENT_CROUCH : 0;
    }

    /** The jump intent of this frame (see JumpIntent): CJ starts jumping when the key goes down, Minecraft a moment later (JumpDelay). */
    private static int jumpIntent(ClientPlayerEntity player) {
        MovementRules.Limits limits = MovementPolicy.current();
        boolean wanted = JumpIntent.wanted(JumpDelay.rawPressed(), CjEffectsClient.cjActive(), player.isOnGround(), limits.canJump(),
                BodyClient.frozen(), player.getAbilities().flying, player.hasVehicle() || VehicleClient.driving(), player.isTouchingWater() || player.isInLava(),
                player.isClimbing() || JetpackClient.flying()); // with the jetpack on the key is not a jump
        return wanted ? Protocol.INTENT_JUMP : 0;
    }

    private static net.minecraft.client.option.KeyBinding infoKey;
    private final InfoSwitch info = new InfoSwitch();

    private void showHud(ClientPlayerEntity player, long now) {
        int presses = 0;
        while (infoKey != null && infoKey.wasPressed()) {
            presses++;
        }
        info.press(presses);
        if (info.takeClear()) {
            player.sendMessage(Text.empty(), true); // the line that was on the screen goes
        }
        if (!info.visible()) {
            return;
        }
        Protocol.Echo echo = lastEcho;
        String problem = CjRenderer.INSTANCE.problem();
        String text;
        if (problem != null) {
            text = "CJ bridge: " + problem;
        } else if (echo == null || now - lastEchoNs > ECHO_STALE_NS) {
            text = "CJ bridge: GTA not responding";
        } else if (!CjRenderer.INSTANCE.hasFreshPose()) {
            text = "CJ bridge: GTA answers but sends no pose";
        } else {
            text = String.format(Locale.ROOT, "CJ bridge: GTA (%.1f, %.1f, %.1f) ack=%d",
                    echo.x(), echo.y(), echo.z(), echo.ackSeq());
        }
        player.sendMessage(Text.literal(text), true);
    }
}
