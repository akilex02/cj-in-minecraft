package dev.cjmc;

import dev.cjmc.vehicle.RunOver;
import dev.cjmc.vehicle.VehicleCollision;
import dev.cjmc.vehicle.VehicleSession;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

/**
 * Running things over with the car (the adapter of dev.cjmc.vehicle.RunOver): every tick at the wheel it looks for living things the car touches and, when it
 * goes fast enough, the integrated server hurts them as the player and throws them along the way of the car. Single player only, like the shots.
 */
final class RunOverClient {
    private static final RunOver.Cooldown COOLDOWN = new RunOver.Cooldown();
    private static final double REACH = 6.0; // blocks from the player the server accepts a hit at (the car is 4.9 long)

    private RunOverClient() {}

    static void tick(MinecraftClient client, ClientPlayerEntity player, VehicleSession.Pose pose, double speed) {
        IntegratedServer server = client.getServer();
        double damage = RunOver.damage(speed);
        if (server == null || damage <= 0 || client.world == null) {
            return;
        }
        double half = Math.max(VehicleCollision.LENGTH, VehicleCollision.WIDTH) / 2 + 2.0;
        Box area = new Box(pose.x() - half, pose.y() - 0.5, pose.z() - half, pose.x() + half, pose.y() + VehicleCollision.HEIGHT + 0.5, pose.z() + half);
        List<LivingEntity> near = client.world.getEntitiesByClass(LivingEntity.class, area, e -> e.isAlive() && e != player && !e.hasPassenger(player));
        long tick = client.world.getTime();
        COOLDOWN.prune(tick);
        double[] forward = RunOver.forward(pose.yaw());
        double sign = speed >= 0 ? 1 : -1;
        double knock = RunOver.knockback(speed);
        for (LivingEntity e : near) {
            if (!RunOver.hits(pose.yaw(), pose.x(), pose.y(), pose.z(), e.getX(), e.getZ(), e.getWidth() / 2.0, e.getY(), e.getY() + e.getHeight())) {
                continue;
            }
            if (!COOLDOWN.ready(e.getUuid(), tick)) {
                continue;
            }
            UUID target = e.getUuid();
            UUID who = player.getUuid();
            double dx = forward[0] * sign;
            double dz = forward[1] * sign;
            server.execute(() -> apply(server, who, target, damage, dx, dz, knock));
        }
    }

    /** On the server thread. Everything the client sent is checked again: who, how near, how much. */
    private static void apply(IntegratedServer server, UUID who, UUID target, double damage, double dx, double dz, double knock) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(who);
        if (player == null || !(damage > 0) || damage > RunOver.MAX_DAMAGE || !Double.isFinite(dx) || !Double.isFinite(dz) || !(knock >= 0) || knock > RunOver.MAX_KNOCKBACK) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        Entity entity = world.getEntity(target);
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living == player || living.squaredDistanceTo(player) > REACH * REACH) {
            return;
        }
        DamageSource source = world.getDamageSources().playerAttack(player);
        living.damage(source, (float) damage);
        if (knock > 0) {
            living.addVelocity(dx * knock, 0.25 + knock * 0.1, dz * knock);
            living.velocityModified = true;
        }
    }
}
