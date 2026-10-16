package dev.cjmc;

import dev.cjmc.climb.ClimbPath;
import dev.cjmc.climb.ClimbRules;
import dev.cjmc.climb.ClimbSession;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * Wall climbing on the Minecraft side: a jump against a wall of 2 or 3 blocks becomes a climb. It reads the world in front of the player,
 * asks ClimbRules, and then moves the player a tick at a time, checking that his box still fits. GTA's climb animations move the skeleton
 * up and ahead on their own, and take that back when they are cleared, so the renderer removes that motion (rootMotion) and the animation
 * plays in place while the player makes the whole movement.
 * Only while CJ is drawn; anything odd (a block in the way, GTA gone, death) ends the climb where it is.
 */
public final class ClimbController {
    private static final double EPS = 1e-6;
    private static final long LOCKOUT_MS = 600; // so that a climb that cannot go on does not start again at once
    private static final double EDGE_MARGIN = 0.4; // how far past the edge of the wall the centre of the player ends up

    private static final ClimbSession SESSION = new ClimbSession();
    private static volatile boolean running;
    private static boolean wasRaw;
    private static double startX, startY, startZ, dirX, dirZ, wallHeight; // wallHeight: blocks from the feet to the top of the wall
    private static double toLedge;               // how far the player moves ahead onto the top of the wall
    private static double toWall;                // and how far to the wall, to hang against it
    private static double lastGroundY = Double.NaN; // where he last stood: the wall is counted from there, also in the air
    private static Object trackedPlayer, trackedWorld; // the climb is given up if either changes (a new world, a respawn)
    private static double lastX, lastY, lastZ;          // where the controller last put him, to notice a teleport
    private static long lockoutUntilMs;          // after a climb ends or is cancelled, no new one starts for a moment
    private static double baseForward, baseUp;   // the skeleton before the animation, to measure what it adds

    private ClimbController() {}

    public static boolean active() {
        return running;
    }

    /** Whether the climb animation is playing (the flag for the bridge); the settle after it keeps the player frozen without it. */
    public static boolean animating(long nowMs) {
        return running && SESSION.animating(nowMs);
    }

    public static void cancel() {
        SESSION.cancel();
        running = false;
    }

    /**
     * What the animation moves the skeleton by on its own, ahead and up, against how it was when the climb began: the renderer takes it
     * away so the animation plays in place. Null when no climb is running.
     */
    public static float[] rootMotion() {
        if (!running) {
            return null;
        }
        float[] skeleton = CjRenderer.INSTANCE.skeletonMean();
        return skeleton == null ? null : new float[] {(float) (skeleton[0] - baseForward), (float) (skeleton[1] - baseUp)};
    }

    private static boolean drawn() {
        return CjClient.bridgeLive() && CjRenderer.INSTANCE.hasFreshPose();
    }

    private static boolean eligible(ClientPlayerEntity player) {
        return ClimbRules.eligible(player.hasVehicle() || VehicleClient.driving(), player.isTouchingWater() || player.isInLava(), player.isClimbing(),
                player.getAbilities().flying, player.isFallFlying(), false, !player.isAlive());
    }

    /** Called with the input of every tick; true when the vanilla jump has to be swallowed (a climb begins or is under way). */
    public static boolean onInput(ClientPlayerEntity player, boolean rawJump, boolean pushing, boolean sneaking, boolean permitted,
                                 long nowMs) {
        if (player.isOnGround()) {
            lastGroundY = player.getY();
        }
        boolean edge = rawJump && !wasRaw; // one climb per press of the key
        wasRaw = rawJump;
        if (running) {
            return true;
        }
        // on the ground the key starts it; in a jump going up there is no need to press anything: he grabs the ledge
        boolean trigger = edge || !player.isOnGround();
        if (!trigger || !permitted || nowMs < lockoutUntilMs || !drawn() || player.getWorld() == null) {
            return false;
        }
        Direction dir = player.getHorizontalFacing();
        // the wall is counted from the ground of the jump, not from his feet in the air (a wall of 4 would read as 3 at the top of a jump)
        int row = ClimbRules.groundRow(ClimbRules.referenceY(lastGroundY, player.getY()));
        BlockPos base = new BlockPos((int) Math.floor(player.getX() + dir.getOffsetX() * ClimbRules.REACH), row,
                (int) Math.floor(player.getZ() + dir.getOffsetZ() * ClimbRules.REACH));
        World world = player.getWorld();
        int wall = 0;
        while (wall < 4 && !shape(world, base.up(wall)).isEmpty()) {
            wall++;
        }
        int clear = 0;
        while (wall > 0 && clear < ClimbRules.CLEAR_ABOVE && shape(world, base.up(wall + clear)).isEmpty()) {
            clear++;
        }
        boolean standable = wall > 0 && Math.abs(shape(world, base.up(wall - 1)).getMax(Direction.Axis.Y) - 1.0) < EPS;
        boolean grab = ClimbRules.canGrab(player.isOnGround(), player.getVelocity().y);
        int height = ClimbRules.heightOf(wall, clear, standable, pushing, grab, sneaking, eligible(player));
        if (height == 0) {
            return false;
        }
        startX = player.getX();
        startY = player.getY();
        startZ = player.getZ();
        dirX = dir.getOffsetX();
        dirZ = dir.getOffsetZ();
        wallHeight = base.getY() + wall - startY;
        float[] skeleton = CjRenderer.INSTANCE.skeletonMean();
        baseForward = skeleton == null ? 0 : skeleton[0];
        baseUp = skeleton == null ? 0 : skeleton[1];
        double face = dir.getOffsetX() > 0 ? base.getX() : dir.getOffsetX() < 0 ? base.getX() + 1
                : dir.getOffsetZ() > 0 ? base.getZ() : base.getZ() + 1;
        double distance = Math.abs(face - (dir.getOffsetX() != 0 ? startX : startZ));
        toWall = ClimbRules.approach(distance);
        toLedge = distance + EDGE_MARGIN;
        trackedPlayer = player;
        trackedWorld = player.getWorld();
        lastX = startX;
        lastY = startY;
        lastZ = startZ;
        SESSION.start(height, nowMs);
        running = true;
        player.setVelocity(0, 0, 0);
        player.setYaw(dir.asRotation()); // the skeleton moves ahead along the way the player faces: face the wall exactly
        player.prevYaw = player.getYaw();
        return true;
    }

    /** Called every client tick: puts the player where the path says, or ends the climb. */
    public static void tick(ClientPlayerEntity player, long nowMs) {
        if (!running) {
            return;
        }
        if (!drawn() || !eligible(player) || player != trackedPlayer || player.getWorld() != trackedWorld
                || ClimbRules.strayed(player.getX() - lastX, player.getY() - lastY, player.getZ() - lastZ)) {
            cancel(); // GTA gone, a bad state, a new world or player, or he was teleported: leave him where he is
            lockoutUntilMs = nowMs + LOCKOUT_MS;
            return;
        }
        boolean over = !SESSION.active(nowMs);
        long elapsed = SESSION.elapsed(nowMs);
        double y = startY + (over ? wallHeight : ClimbPath.lift(wallHeight, elapsed));
        double forward = over ? toLedge
                : toWall * ClimbPath.approachFraction(elapsed) + (toLedge - toWall) * ClimbPath.forwardFraction(elapsed);
        double x = startX + dirX * forward;
        double z = startZ + dirZ * forward;
        Box box = player.getBoundingBox().offset(x - player.getX(), y - player.getY(), z - player.getZ());
        if (!player.getWorld().isSpaceEmpty(player, box)) {
            cancel();
            lockoutUntilMs = nowMs + LOCKOUT_MS;
            return;
        }
        player.setPosition(x, y, z);
        lastX = x;
        lastY = y;
        lastZ = z;
        player.setVelocity(0, 0, 0);
        player.fallDistance = 0f;
        if (over) {
            cancel();
            lockoutUntilMs = nowMs + LOCKOUT_MS;
        }
    }

    private static VoxelShape shape(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getCollisionShape(world, pos);
    }
}
