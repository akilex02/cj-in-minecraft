package dev.cjmc;

import dev.cjmc.jetpack.JetpackRules;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

/**
 * Flying with the jetpack, on the client: with the jetpack on, the jump key held lifts the player like a rocket and letting go lets him
 * down slowly, and the movement keys steer him in the air. The rules are JetpackRules; this applies them to the player every tick
 * and tells the renderer and the bridge what is going on.
 */
public final class JetpackClient {
    private static volatile boolean flying;
    private static volatile boolean thrusting;
    private static volatile float tiltDegrees;
    private static volatile float tiltDirX;
    private static volatile float tiltDirZ;

    private static final FuelTank TANK = FuelTank.jetpack();
    private static final double TICK_SECONDS = 0.05; // a client tick
    private static Object tankPlayer; // the tank is filled again for a new player (a new world, a respawn)

    private JetpackClient() {}

    /** The fuel: 12 seconds of thrust, recharged in 20 while he is not thrusting. */
    public static FuelTank tank() {
        return TANK;
    }

    /** In the air with the jetpack on (or lifting off): the animation and the sound of the jetpack. */
    public static boolean flying() {
        return flying;
    }

    /** The jump key is held with the jetpack on. */
    public static boolean thrusting() {
        return thrusting;
    }

    /** How far the body leans forward, in degrees, and the horizontal direction (unit, world) it leans toward. */
    public static float tiltDegrees() {
        return tiltDegrees;
    }

    public static float tiltDirX() {
        return tiltDirX;
    }

    public static float tiltDirZ() {
        return tiltDirZ;
    }

    /** Called every client tick. */
    public static void tick(ClientPlayerEntity player) {
        boolean allowed = JetpackRules.allowed(CjItems.isWearingJetpack(player), player.hasVehicle() || VehicleClient.driving(),
                player.isTouchingWater() || player.isInLava(), player.isFallFlying(), player.isClimbing(),
                player.getAbilities().flying || player.isSpectator(), BodyClient.knockedDown(), !player.isAlive(), ClimbController.active());
        boolean paused = net.minecraft.client.MinecraftClient.getInstance().isPaused(); // the input does not run: a held key would stay held
        if (tankPlayer != player) {
            tankPlayer = player;
            TANK.fill();
        }
        boolean wants = allowed && !paused && JumpDelay.rawPressed();
        boolean thrust;
        if (player.getAbilities().creativeMode) {
            TANK.fill(); // creative never runs dry
            thrust = wants;
        } else if (paused) {
            thrust = false; // the tank neither burns nor fills while the game is stopped
        } else {
            thrust = TANK.update(wants, TICK_SECONDS) && wants; // fuel is spent on thrust only, never on gliding
        }
        boolean inAir = !paused && JetpackRules.flyingNext(allowed, thrust, flying, player.isOnGround());
        thrusting = thrust;
        flying = inAir;
        if (!inAir) {
            tiltDegrees = 0f;
            return;
        }
        Vec3d v = player.getVelocity();
        // the movement keys, turned to the way the player faces (as the game does for walking)
        double yaw = Math.toRadians(player.getYaw());
        double forward = player.input.movementForward;
        double side = player.input.movementSideways;
        double wishX = side * Math.cos(yaw) - forward * Math.sin(yaw);
        double wishZ = forward * Math.cos(yaw) + side * Math.sin(yaw);
        double length = Math.sqrt(wishX * wishX + wishZ * wishZ);
        if (length > 1.0) {
            wishX /= length;
            wishZ /= length;
        }
        double vx = JetpackRules.horizontalSpeed(v.x, wishX);
        double vz = JetpackRules.horizontalSpeed(v.z, wishZ);
        double vy = JetpackRules.verticalSpeed(v.y, thrust);
        player.setVelocity(vx, vy, vz);
        player.fallDistance = 0f;

        double speed = Math.sqrt(vx * vx + vz * vz);
        float target = (float) JetpackRules.tiltDegrees(speed);
        tiltDegrees += (target - tiltDegrees) * 0.3f;
        if (speed > 1e-4) {
            tiltDirX = (float) (vx / speed);
            tiltDirZ = (float) (vz / speed);
        }
        if (thrust) {
            spawnFlames(player);
        }
    }

    /** Flames and smoke from the two nozzles, behind and below the shoulders. */
    private static void spawnFlames(ClientPlayerEntity player) {
        double yaw = Math.toRadians(player.getYaw());
        double backX = Math.sin(yaw), backZ = -Math.cos(yaw);      // behind him
        double sideX = Math.cos(yaw), sideZ = Math.sin(yaw);       // to his left
        for (int nozzle = -1; nozzle <= 1; nozzle += 2) {
            double x = player.getX() + backX * 0.35 + sideX * 0.2 * nozzle;
            double y = player.getY() + 0.75;
            double z = player.getZ() + backZ * 0.35 + sideZ * 0.2 * nozzle;
            Vec3d v = player.getVelocity();
            player.getWorld().addParticle(ParticleTypes.FLAME, x, y, z, v.x * 0.5, -0.25, v.z * 0.5);
            player.getWorld().addParticle(ParticleTypes.SMOKE, x, y - 0.1, z, v.x * 0.5, -0.12, v.z * 0.5);
        }
    }
}
