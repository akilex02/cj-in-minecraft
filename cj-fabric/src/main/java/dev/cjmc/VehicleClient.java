package dev.cjmc;

import dev.cjmc.vehicle.ExhaustSmoke;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import dev.cjmc.vehicle.VehicleRules;
import dev.cjmc.vehicle.VehicleSession;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.particle.ParticleTypes;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Driving the car of GTA from Minecraft (the adapter of VehicleSession): the key to get in and out, the controls sent to the bridge every tick,
 * and the player carried along with the car. The rules, the collisions and the numbers are in dev.cjmc.vehicle (tested); this reads the keys and
 * the world and sends the packets. Only while GTA answers.
 */
public final class VehicleClient {
    private static final VehicleSession SESSION = new VehicleSession();
    private static KeyBinding enterKey;
    private static KeyBinding radioPreviousKey, radioNextKey, lightsKey;
    private static boolean lightsOn;
    private static final RadioDisplay RADIO = new RadioDisplay();
    private static volatile Protocol.VehicleState lastState;
    private static volatile long lastStateMs;
    private static int seq = dev.cjmc.vehicle.VehicleRules.firstCommandSeq(System.currentTimeMillis()); // ahead of the last Minecraft's, so the bridge does not take them for old ones
    private static Object trackedPlayer, trackedWorld; // the ride is given up if either changes (a new world, a respawn)

    private static final FuelTank TANK = FuelTank.car();
    private static long lastFuelMs;
    private static Object fuelPlayer;

    private VehicleClient() {}

    /** The fuel of the car: 40 seconds with the pedals down, recharged in 60. */
    public static FuelTank tank() {
        return TANK;
    }

    public static void init() {
        enterKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cj_fabric.vehicle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.cj_fabric"));
        radioPreviousKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cj_fabric.radio_previous", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, "key.categories.cj_fabric"));
        radioNextKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cj_fabric.radio_next", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, "key.categories.cj_fabric"));
        lightsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cj_fabric.headlights", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, "key.categories.cj_fabric"));
        HudRenderCallback.EVENT.register(VehicleClient::drawRadioName);
    }

    /** The name of the station on the screen: centred just above the status bars of the hotbar, for a few seconds after a change. */
    private static void drawRadioName(DrawContext context, RenderTickCounter tickCounter) {
        long now = System.nanoTime() / 1_000_000L;
        String name = RADIO.text(now);
        if (name == null) {
            return;
        }
        int alpha = Math.max(4, (int) Math.round(RADIO.alpha(now) * 255.0)); // a text with alpha under 4 is drawn opaque
        MinecraftClient client = MinecraftClient.getInstance();
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("\u266B " + name), context.getScaledWindowWidth() / 2, context.getScaledWindowHeight() - 58,
                (alpha << 24) | 0xFFFFFF);
    }

    /** The player is at the wheel (or has just asked to get out): the movement keys are the car's, and he neither attacks nor uses. */
    public static boolean driving() {
        return SESSION.riding();
    }

    /** The headlights are on (they go off when CJ gets out). */
    public static boolean lightsOn() {
        return lightsOn;
    }

    /** The place of the car between the last two ticks (delta 0 to 1), for drawing. */
    public static VehicleSession.Pose pose(float delta) {
        return VehicleSession.blend(SESSION.previousPose(), SESSION.pose(), delta);
    }

    /** From the thread that receives the datagrams of the bridge. */
    static void onState(Protocol.VehicleState state, long receivedNs) {
        lastState = state;
        lastStateMs = receivedNs / 1_000_000L;
    }

    /** Gives the ride up (the world went, the connection closed): the bridge is told so the car does not stay. */
    public static void cancel() {
        if (SESSION.cancel()) {
            send(Protocol.encodeVehicleCommand(new Protocol.VehicleCommand(seq++, (int) (System.nanoTime() / 1_000_000L), Protocol.VEHICLE_CMD_EXIT, VehicleSession.MODEL, 0f)));
        }
    }

    /** Called every client tick. */
    public static void tick(ClientPlayerEntity player, long nowMs) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || enterKey == null) {
            return;
        }
        if (SESSION.riding() && (player != trackedPlayer || player.getWorld() != trackedWorld)) {
            cancel(); // another world or another player: the old car belongs to the old one
        }
        boolean pressed = false;
        while (enterKey.wasPressed()) {
            pressed = true;
        }
        boolean toggleLights = false;
        while (lightsKey.wasPressed()) {
            toggleLights = !toggleLights;
        }
        int radio = 0; // presses of next (+1) and previous (-1) this tick; only at the wheel are they sent
        while (radioNextKey.wasPressed()) {
            radio++;
        }
        while (radioPreviousKey.wasPressed()) {
            radio--;
        }
        GameOptions o = client.options;
        boolean screen = client.currentScreen != null;
        // with a screen open nothing is pressed and the car is parked (the handbrake), so it does not roll on while the game is stopped
        VehicleSession.Keys keys = new VehicleSession.Keys(pressed, !screen && o.forwardKey.isPressed(), !screen && o.backKey.isPressed(),
                !screen && o.leftKey.isPressed(), !screen && o.rightKey.isPressed(), screen || o.jumpKey.isPressed());
        VehicleSession.Conditions conditions = new VehicleSession.Conditions(CjClient.bridgeLive(), player.hasVehicle(),
                player.isTouchingWater() || player.isInLava(), JetpackClient.flying(), player.isClimbing() || ClimbController.active(),
                BodyClient.knockedDown(), !player.isAlive() || player.getHealth() <= 0f, screen, client.getNetworkHandler() != null);
        boolean wasRiding = SESSION.riding();
        VehicleSession.Output out = SESSION.tick(nowMs, conditions, keys, player.getX(), player.getY(), player.getZ(), player.getYaw(),
                new VehicleWorld(player.getWorld()), lastState, lastStateMs);
        if (!wasRiding && SESSION.mode() != VehicleSession.Mode.IDLE) {
            trackedPlayer = player;
            trackedWorld = player.getWorld();
        }
        if (out.enterFailed()) {
            CjClient.LOG.warn("vehicle: GTA did not answer the request for a car");
        }
        int ms = (int) nowMs;
        if (out.command() == VehicleSession.ENTER) {
            send(Protocol.encodeVehicleCommand(new Protocol.VehicleCommand(seq++, ms, Protocol.VEHICLE_CMD_ENTER, VehicleSession.MODEL, out.yaw())));
        } else if (out.command() == VehicleSession.EXIT) {
            send(Protocol.encodeVehicleCommand(new Protocol.VehicleCommand(seq++, ms, Protocol.VEHICLE_CMD_EXIT, VehicleSession.MODEL, 0f)));
        }
        if (SESSION.mode() == VehicleSession.Mode.DRIVING && client.currentScreen == null) {
            for (int i = 0; i < Math.abs(radio) && i < 3; i++) {
                send(Protocol.encodeVehicleCommand(new Protocol.VehicleCommand(seq++, ms,
                        radio > 0 ? Protocol.VEHICLE_CMD_RADIO_NEXT : Protocol.VEHICLE_CMD_RADIO_PREVIOUS, VehicleSession.MODEL, 0f)));
            }
        }
        boolean atTheWheel = SESSION.mode() == VehicleSession.Mode.DRIVING;
        if (toggleLights && atTheWheel && client.currentScreen == null) {
            lightsOn = !lightsOn;
        }
        if (!atTheWheel) {
            lightsOn = false; // the lights go off with the car
        }
        HeadlightsClient.update(client.world, lightsOn, atTheWheel ? SESSION.pose() : null);
        Protocol.VehicleState shown = lastState;
        RADIO.observe(shown == null ? -1 : RadioStations.stationOf(shown.flags()), SESSION.mode() == VehicleSession.Mode.DRIVING, nowMs);
        updateFuel(client, player, nowMs, atTheWheel && (keys.forward() || keys.back()));
        boolean dry = atTheWheel && !player.getAbilities().creativeMode && TANK.locked();
        int[] pedals = VehicleRules.pedalsWithFuel(dry, out.throttle(), out.brake(), out.handbrake()); // the engine dies: the car stops, he stays in it and the radio plays on
        if (out.input()) {
            send(Protocol.encodeVehicleInput(new Protocol.VehicleInput(seq++, ms, pedals[0], pedals[1], (short) out.steer(), pedals[2],
                    out.correction() ? Protocol.VEHICLE_CORR_PRESENT : 0, (float) out.corrX(), (float) out.corrY())));
        }
        if (SESSION.riding()) {
            VehicleSession.Pose pose = SESSION.pose();
            player.setPosition(pose.x(), pose.y(), pose.z()); // the player goes with the car; the camera is his own
            player.setVelocity(0, 0, 0);
            player.fallDistance = 0f;
            player.setOnGround(true);
            Protocol.VehicleState state = lastState;
            if (state != null) {
                RunOverClient.tick(client, player, pose, state.speed());
                exhaust(client, pose, state.speed());
            }
        }
    }

    /** The tank burns while a pedal is down at the wheel and fills when not; creative never runs dry. Real time, not ticks, as the rest of the car. */
    private static void updateFuel(MinecraftClient client, ClientPlayerEntity player, long nowMs, boolean pedalDown) {
        double seconds = lastFuelMs == 0 ? 0.0 : Math.max(0L, Math.min(200L, nowMs - lastFuelMs)) / 1000.0;
        lastFuelMs = nowMs;
        if (fuelPlayer != player) {
            fuelPlayer = player;
            TANK.fill(); // a new player (a new world, a respawn) starts with a full tank
        }
        if (player.getAbilities().creativeMode) {
            TANK.fill();
        } else if (!client.isPaused()) {
            TANK.update(pedalDown, seconds);
        }
    }

    /** A light puff of smoke from the tailpipe, at the back right of the car. */
    private static void exhaust(MinecraftClient client, VehicleSession.Pose pose, double speed) {
        if (client.world == null || ThreadLocalRandom.current().nextDouble() >= ExhaustSmoke.chance(speed)) {
            return;
        }
        double[] at = ExhaustSmoke.position(pose.x(), pose.y(), pose.z(), pose.yaw());
        double[] v = ExhaustSmoke.velocity(pose.yaw(), speed);
        client.world.addParticle(ParticleTypes.SMOKE, at[0], at[1], at[2], v[0], v[1], v[2]);
    }

    private static void send(byte[] data) {
        CjClient.sendToGta(data);
    }
}
