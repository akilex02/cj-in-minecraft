package dev.cjmc.skate;

import dev.cjmc.BodyClient;
import dev.cjmc.CjItems;
import dev.cjmc.VehicleClient;
import java.io.IOException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The skate mode, on the client. Using the skateboard puts the player on the board where he stands: the blocks around him go to the Skate engine as its collision
 * world, the gamepad goes to it as the controller, and each tick the player is carried to where the engine says the board is (as the car does). Using the
 * skateboard again, or taking it out of the hand, ends it. The engine runs apart in host mode (skate-host/README.md); this only talks UDP to it.
 */
public final class SkateRide {
    private static final Logger LOG = LoggerFactory.getLogger("cj_fabric");
    /**
     * The player is carried this much under the deck's centre. The deck rolls 0.087 over the ground (as measured: 2.087 over a floor at 2.0), so 0.08 keeps his
     * feet 7 mm over it: a player whose box reaches into a block falls through it when he gets off, and is saved buried if the world closes while he skates.
     */
    static final double DECK_ABOVE_GROUND = 0.08;
    private static final double START_ABOVE_GROUND = 0.3;
    private static final long SEND_INTERVAL_NS = 16_000_000L;
    private static final int TICK_BUDGET = 4; // sections meshed per client tick while riding

    private static SkateLink link;
    private static final SkateWorld WORLD = new SkateWorld();
    private static volatile boolean active;
    private static Object ridingPlayer;
    private static long startNs;
    private static boolean answered;
    private static int seq;
    private static long lastSendNs;
    private static SkateProtocol.Pose framePose;
    private static final SkateRules.Edge COMBO = new SkateRules.Edge();
    private static final ViewSwitch<net.minecraft.client.option.Perspective> VIEW =
            new ViewSwitch<>(net.minecraft.client.option.Perspective.FIRST_PERSON, net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
    private static double lastRootX, lastRootZ;
    private static long lastRootNs;

    private SkateRide() {}

    /** The latest pose of the skater, or null (no mode, or the engine has not answered yet). */
    public static SkateProtocol.Pose pose() {
        return active && link != null ? framePose : null;
    }

    /** The mode is on: the controls of the player are the board's. */
    public static boolean active() {
        return active;
    }

    public static void install() {
        SkateboardItem.onUse = SkateRide::toggle;
    }

    private static void toggle(PlayerEntity player) {
        if (!(player instanceof ClientPlayerEntity self)) {
            return;
        }
        if (active) {
            stop(self, "Skate: you got off the board");
        } else {
            start(self);
        }
    }

    private static void say(ClientPlayerEntity player, String text) {
        player.sendMessage(Text.literal(text), true);
    }

    private static void start(ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null || !SkateRules.canRide(player.hasVehicle() || VehicleClient.driving(), player.getAbilities().flying, player.isSpectator(),
                !player.isAlive(), player.isTouchingWater() || player.isInLava())) {
            say(player, "Skate: you cannot ride from here");
            return;
        }
        if (SkateGamepad.poll() == null) {
            say(player, "Skate: connect a gamepad first");
            return;
        }
        try {
            if (link == null) {
                link = new SkateLink(SkateRules.engineAddress(System.getenv("CJ_SKATE_ENGINE")));
            }
        } catch (IOException e) {
            LOG.warn("skate: the link could not be opened", e);
            say(player, "Skate: cannot open the link to the engine");
            return;
        }
        link.forget();
        framePose = null;
        lastRootNs = 0;
        answered = false;
        startNs = System.nanoTime();
        ridingPlayer = player;
        WORLD.update(world, player.getPos(), link, 400); // the whole neighbourhood at once, so the board has ground under it
        link.send(SkateProtocol.encodeTeleport((float) player.getX(), (float) (player.getY() + START_ABOVE_GROUND), (float) player.getZ(),
                SkateRules.headingOf(player.getYaw()), 0f, 0f, 0f));
        active = true;
        net.minecraft.client.option.Perspective view = VIEW.onStart(client.options.getPerspective());
        if (view != null) {
            client.options.setPerspective(view);
        }
        say(player, "Skate: on the board (use the skateboard again to get off)");
    }

    /** Ends the mode: the engine forgets the world and the player stays where the board is. */
    public static void stop(ClientPlayerEntity player, String message) {
        if (!active) {
            return;
        }
        active = false;
        ridingPlayer = null;
        net.minecraft.client.option.Perspective view = VIEW.onStop(MinecraftClient.getInstance().options.getPerspective());
        if (view != null) {
            MinecraftClient.getInstance().options.setPerspective(view);
        }
        if (link != null) {
            WORLD.clearAll(link);
            link.send(SkateProtocol.encodeInput(++seq, new float[SkateProtocol.ACTIONS])); // let go of everything
        }
        if (player != null) {
            player.setVelocity(0, 0, 0);
            if (message != null && !message.isEmpty()) {
                say(player, message);
            }
        }
    }

    /** Called when the client leaves its world. */
    public static void cancel() {
        stop(null, null);
    }

    /** A block of the world changed: the sections around it are meshed again for the engine. */
    public static void blockChanged(BlockPos pos) {
        if (active) {
            WORLD.blockChanged(pos);
        }
    }

    /** Every rendered frame: the gamepad goes to the engine at about 60 Hz (a quick flick of a stick would be lost at the 20 Hz of the ticks). */
    public static void onFrame() {
        if (!active || link == null) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastSendNs < SEND_INTERVAL_NS) {
            return;
        }
        lastSendNs = now;
        float[] actions = SkateGamepad.poll();
        link.send(SkateProtocol.encodeInput(++seq, actions != null ? actions : new float[SkateProtocol.ACTIONS]));
    }

    /**
     * Before each frame is drawn: the player is put where the engine's latest pose says (it comes at 60 Hz), and his previous position is the same, so the camera
     * does not interpolate between ticks. Taking the board's position only at the 20 Hz of the ticks makes the steps uneven (the ticks are not in step with the
     * engine) and the whole view shakes.
     */
    public static void beforeFrame() {
        if (!active || link == null || !(ridingPlayer instanceof ClientPlayerEntity player)) {
            return;
        }
        SkateProtocol.Pose pose = link.latest();
        if (pose == null) {
            return;
        }
        framePose = pose; // the renderer draws the same pose the player was put at
        double[] at = SkateRules.playerPosition(rootOf(pose), pose.deckPosition());
        player.setPosition(at[0], at[1], at[2]);
        player.prevX = player.lastRenderX = at[0];
        player.prevY = player.lastRenderY = at[1];
        player.prevZ = player.lastRenderZ = at[2];
        followWithCamera(player, at[0], at[2]);
    }

    private static float[] rootOf(SkateProtocol.Pose pose) {
        float[] r = pose.root();
        return new float[] {r[12], r[13], r[14]};
    }

    /** The camera swings behind the skater as he goes (the pad has no hand free for the mouse): the player's yaw turns toward where the root moves. */
    private static void followWithCamera(ClientPlayerEntity player, double x, double z) {
        long now = System.nanoTime();
        if (lastRootNs != 0) {
            double seconds = (now - lastRootNs) / 1e9;
            if (seconds > 0.004) { // a frame drawn twice with the same pose says nothing about the speed
                float yaw = SkateRules.followYaw(player.getYaw(), (x - lastRootX) / seconds, (z - lastRootZ) / seconds, seconds);
                player.setYaw(yaw);
                player.prevYaw = yaw;
                lastRootX = x;
                lastRootZ = z;
                lastRootNs = now;
            }
            return;
        }
        lastRootX = x;
        lastRootZ = z;
        lastRootNs = now;
    }

    /** Every client tick: the end conditions, the world for the engine, and the player carried to the board. */
    public static void tick(ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        // L3 + R3 on the pad: on the board (holding the skateboard) or off it, as the right click with it; not while a screen is open
        if (COMBO.rose(client.currentScreen == null && SkateRules.toggleCombo(SkateGamepad.poll()))) {
            if (active) {
                stop(player, "Skate: you got off the board");
                return;
            }
            if (CjItems.isHoldingSkateboard(player)) {
                start(player);
            }
        }
        if (!active) {
            return;
        }
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null || player != ridingPlayer) {
            cancel();
            return;
        }
        if (!CjItems.isHoldingSkateboard(player)) {
            stop(player, "Skate: you put the skateboard away");
            return;
        }
        if (!SkateRules.canRide(player.hasVehicle() || VehicleClient.driving(), player.getAbilities().flying, player.isSpectator(), !player.isAlive(),
                player.isTouchingWater() || player.isInLava()) || BodyClient.knockedDown()) {
            stop(player, "Skate: you got off the board");
            return;
        }
        SkateProtocol.Pose pose = link.latest();
        answered |= pose != null;
        long silent = pose != null ? link.nanosSinceLatest() : System.nanoTime() - startNs;
        if (SkateRules.engineSilent(answered, silent)) {
            stop(player, SkateRules.ENGINE_MISSING);
            return;
        }
        WORLD.update(world, player.getPos(), link, TICK_BUDGET);
        if (pose != null) {
            float[] d = pose.deckPosition();
            if (d[1] < world.getBottomY() - 8) {
                stop(player, "Skate: the board fell out of the world");
                return;
            }
            double[] at = SkateRules.playerPosition(rootOf(pose), d);
            player.setPosition(at[0], at[1], at[2]);
            player.setVelocity(0, 0, 0);
            player.setOnGround(true);
            player.fallDistance = 0f;
        }
    }
}
