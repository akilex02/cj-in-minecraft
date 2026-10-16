package dev.cjmc;

import dev.cjmc.swim.SwimRules;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Swimming on the client: in deep water the player floats with his back at the surface instead of sinking (a buoyancy that takes him to
 * the float height), Shift dives and letting go brings him back up; Space stays Minecraft's. The rules are SwimRules; this applies them
 * every tick and tells the renderer and the bridge what is going on.
 */
public final class SwimClient {
    private static volatile boolean inWater;
    private static volatile boolean underwater;
    private static volatile boolean moving;
    private static volatile boolean crawling;
    private static float baseForward;     // where the skeleton is, forward, when the water is entered: what the animations move it by is measured from there
    private static boolean wasInWater;
    private static long lastInWaterNs;    // when the water was last swum in, to know how long the pose has been fading

    private SwimClient() {}

    /** In water deep enough to swim, with the buoyancy at work: floating, swimming or diving. */
    public static boolean inWater() {
        return inWater;
    }

    /** Crawling: the run key is held while he moves in the water (Minecraft cancels the sprint of a player with his head out, so it is read here). */
    public static boolean crawling() {
        return crawling;
    }

    /** The head is under the water. */
    public static boolean underwater() {
        return underwater;
    }

    /** Moving horizontally in the water. */
    public static boolean moving() {
        return moving;
    }

    /**
     * How far ahead of where it was when the water was entered the skeleton is, because the swimming animations carry CJ forward on their
     * own (up to 2.8 blocks a stroke, back to the start with each loop): the renderer takes it away so he swims in place and the player does
     * the moving. NaN when it does not apply.
     */
    public static float rootForward() {
        if (!inWater && !SwimRules.poseFading(msSinceInWater())) {
            return Float.NaN;
        }
        float[] skeleton = CjRenderer.INSTANCE.skeletonMean();
        return skeleton == null ? Float.NaN : skeleton[0] - baseForward;
    }

    private static long msSinceInWater() {
        return lastInWaterNs == 0 ? Long.MAX_VALUE : (System.nanoTime() - lastInWaterNs) / 1_000_000L;
    }

    /** Called every client tick. */
    public static void tick(ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        double surface = player.isTouchingWater() && !player.isInLava() ? surfaceY(player) : Double.NaN;
        boolean deep = !Double.isNaN(surface) && SwimRules.deepEnough(surface - player.getY(), wasInWater, player.isOnGround());
        boolean allowed = !client.isPaused() && SwimRules.allowed(deep, player.hasVehicle() || VehicleClient.driving(), JetpackClient.flying(), player.isFallFlying(),
                player.isClimbing(), player.getAbilities().flying || player.isSpectator(), BodyClient.knockedDown(), !player.isAlive(),
                ClimbController.active());
        if (allowed && !wasInWater && SwimRules.baselineStale(msSinceInWater())) { // not in the middle of a swim whose pose is still fading
            float[] skeleton = CjRenderer.INSTANCE.skeletonMean();
            baseForward = skeleton == null ? 0f : skeleton[0];
        }
        if (allowed) {
            lastInWaterNs = System.nanoTime();
        }
        wasInWater = allowed;
        inWater = allowed;
        if (!allowed) {
            underwater = false;
            moving = false;
            crawling = false;
            return;
        }
        Vec3d v = player.getVelocity();
        // Shift is the key itself, not the sneaking the mod forces while striking or aiming
        boolean shift = client.options.sneakKey.isPressed();
        // Minecraft's own vertical pushes (bubble columns, a riptide, the hop against a step) and the dive along the look of a fast swimmer are not undone
        boolean lookDive = SwimRules.lookDiving(player.isSwimming(), player.getPitch());
        double vy = !shift && SwimRules.leavesToMinecraft(v.y, player.horizontalCollision)
                ? v.y
                : SwimRules.verticalSpeed(v.y, player.getY(), surface, moving, shift, JumpDelay.rawPressed() || lookDive);
        player.setVelocity(v.x, vy, v.z);
        player.fallDistance = 0f;
        underwater = player.isSubmergedInWater();
        boolean pushing = player.input.movementForward != 0 || player.input.movementSideways != 0;
        crawling = SwimRules.crawling(client.options.sprintKey.isPressed(), pushing);
        double vx = v.x;
        double vz = v.z;
        if (crawling && !underwater) {
            // on the surface Minecraft does not let him run, and the crawl is a faster stroke: the speed is taken to the crawl speed
            double[] wish = SwimRules.wish(player.input.movementForward, player.input.movementSideways, player.getYaw());
            vx = SwimRules.crawlStep(v.x, wish[0]);
            vz = SwimRules.crawlStep(v.z, wish[1]);
            player.setVelocity(vx, vy, vz);
        }
        moving = SwimRules.moving(Math.sqrt(vx * vx + vz * vz));
    }

    /** The height of the top of the water over the player's feet column, or NaN when there is no water there. */
    private static double surfaceY(ClientPlayerEntity player) {
        World world = player.getWorld();
        int x = player.getBlockX();
        int z = player.getBlockZ();
        int y = (int) Math.floor(player.getY());
        int first = Integer.MIN_VALUE;
        for (int dy = 0; dy <= 2 && first == Integer.MIN_VALUE; dy++) { // the box may start just over a block of water
            if (isWater(world, x, y + dy, z)) {
                first = y + dy;
            }
        }
        if (first == Integer.MIN_VALUE) {
            return Double.NaN;
        }
        int top = first;
        while (top - first < 256 && isWater(world, x, top + 1, z)) {
            top++;
        }
        BlockPos pos = new BlockPos(x, top, z);
        return top + world.getFluidState(pos).getHeight(world, pos);
    }

    /** Water to swim in: not the water that falls (a waterfall or a shaft, where he drops or is carried and does not float). */
    private static boolean isWater(World world, int x, int y, int z) {
        FluidState fluid = world.getFluidState(new BlockPos(x, y, z));
        return fluid.isIn(FluidTags.WATER) && !(fluid.contains(FlowableFluid.FALLING) && fluid.get(FlowableFluid.FALLING));
    }
}
