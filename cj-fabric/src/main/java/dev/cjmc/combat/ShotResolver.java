package dev.cjmc.combat;

import dev.cjmc.AmmoInventory;
import dev.cjmc.AmmoRules;
import dev.cjmc.CjItems;
import dev.cjmc.CrouchRules;
import dev.cjmc.WeaponCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Resolves a shot on the server thread: damages what the pellets hit, wears down the blocks, and reports where each pellet ended. */
public final class ShotResolver {
    private static final Logger LOG = LoggerFactory.getLogger("cj_fabric");
    private static boolean failureLogged;

    private ShotResolver() {}

    /** Called through server.execute: never throws, a failure is logged once and the shot is lost. */
    public static void resolve(MinecraftServer server, ShotRequest request) {
        try {
            resolveUnchecked(server, request);
        } catch (RuntimeException e) {
            if (!failureLogged) {
                failureLogged = true;
                LOG.error("a shot could not be resolved (further failures are not logged)", e);
            }
        }
    }

    private static void resolveUnchecked(MinecraftServer server, ShotRequest request) {
        WeaponCatalog catalog = CjCombat.catalog();
        BlockHealthTracker tracker = CjCombat.tracker();
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(request.player());
        if (catalog == null || tracker == null || player == null || !player.isAlive() || player.isSpectator()) {
            return;
        }
        WeaponCatalog.WeaponDef def = catalog.byId(request.weaponId());
        if (def == null) {
            return;
        }
        if (AmmoRules.usesAmmo(def.ammo()) && !AmmoInventory.take(player, def.ammo(), AmmoRules.cost(def.ammo(), AmmoInventory.isFree(player)))) {
            return; // the round GTA already fired when the last bullet went: it does no harm
        }
        wearOut(player, def);
        if (def.reach() > 0f) {
            MeleeResolver.strike(player.getServerWorld(), player, def, request);
            return;
        }
        ServerWorld world = player.getServerWorld();
        if (def.projectileSpeed() > 0f) {
            launchRocket(world, player, def, request);
            return;
        }
        double length = PelletRay.length(def.range());
        List<ShotOutcome.Pellet> pellets = new ArrayList<>();
        for (int i = 0; i < def.pellets(); i++) {
            double[] dir = Spread.perturb(request.direction(), def.spread() * CrouchRules.spreadFactor(request.crouching()), new Random(PelletRay.seed(request.seed(), i)));
            pellets.add(firePellet(world, player, def, catalog.blocks(), tracker, request.origin(), dir, length));
        }
        CjCombat.OUTCOMES.add(new ShotOutcome(request.origin(), pellets));
    }

    /** One round or one blow wears the weapon in hand by one (nothing in creative; nothing if the player has switched to another item meanwhile). */
    private static void wearOut(ServerPlayerEntity player, WeaponCatalog.WeaponDef def) {
        net.minecraft.item.ItemStack stack = player.getMainHandStack();
        if (def.durability() > 0 && !AmmoInventory.isFree(player) && CjItems.weaponOf(stack) == def) {
            stack.damage(1, player, net.minecraft.entity.EquipmentSlot.MAINHAND);
        }
    }

    private static ShotOutcome.Pellet firePellet(ServerWorld world, ServerPlayerEntity player, WeaponCatalog.WeaponDef def,
                                                 WeaponCatalog.BlockRules rules, BlockHealthTracker tracker, double[] origin,
                                                 double[] dir, double length) {
        Vec3d from = new Vec3d(origin[0], origin[1], origin[2]);
        Vec3d to = from.add(dir[0] * length, dir[1] * length, dir[2] * length);

        BlockHitResult block = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, player));
        double blockDistance = block.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : block.getPos().distanceTo(from);
        EntityHitResult entity = ProjectileUtil.raycast(player, from, to, new Box(from, to).expand(1.0),
                e -> !e.isSpectator() && e.canHit() && e != player, length * length);
        double entityDistance = entity == null ? Double.POSITIVE_INFINITY : entity.getPos().distanceTo(from);

        PelletRay.Hit hit = PelletRay.classify(blockDistance, entityDistance, length);
        switch (hit) {
            case ENTITY -> {
                hitEntity(world, player, def, entity.getEntity(), dir);
                Vec3d at = entity.getPos();
                return new ShotOutcome.Pellet(hit, at.x, at.y, at.z, 0, 0, 0);
            }
            case BLOCK -> {
                BlockPos pos = block.getBlockPos();
                hitBlock(world, player, def, rules, tracker, pos);
                Vec3d at = block.getPos();
                return new ShotOutcome.Pellet(hit, at.x, at.y, at.z, pos.getX(), pos.getY(), pos.getZ());
            }
            default -> {
                return new ShotOutcome.Pellet(hit, to.x, to.y, to.z, 0, 0, 0);
            }
        }
    }

    private static final int MAX_ROCKETS_PER_PLAYER = 8;

    /** A weapon with a projectile makes it fly instead of casting rays: the rocket explodes where it lands (see RocketEntity). */
    private static void launchRocket(ServerWorld world, ServerPlayerEntity player, WeaponCatalog.WeaponDef def, ShotRequest request) {
        if (CjEntities.ROCKET == null) {
            return;
        }
        int inFlight = world.getEntitiesByType(TypeFilter.instanceOf(RocketEntity.class), r -> r.getOwner() == player).size();
        if (inFlight >= MAX_ROCKETS_PER_PLAYER) {
            return; // a runaway shot counter does not flood the world with rockets
        }
        double[] dir = Spread.perturb(request.direction(), def.spread() * CrouchRules.spreadFactor(request.crouching()), new Random(PelletRay.seed(request.seed(), 0)));
        Vec3d from = new Vec3d(request.origin()[0], request.origin()[1], request.origin()[2]); // the eyes: whatever is right in front is hit too
        RocketEntity rocket = new RocketEntity(CjEntities.ROCKET, world);
        rocket.launch(player, from, new Vec3d(dir[0], dir[1], dir[2]), def.projectileSpeed(), def.range(), def.explosionPower());
        world.spawnEntity(rocket);
    }

    static void hitEntity(ServerWorld world, ServerPlayerEntity player, WeaponCatalog.WeaponDef def, Entity target, double[] dir) {
        target.timeUntilRegen = 0; // every pellet and every round of a burst counts: the game would ignore damage within 10 ticks of the last
        Vec3d before = target.getVelocity();
        target.damage(world.getDamageSources().playerAttack(player), def.damage());
        if (target instanceof LivingEntity) {
            target.setVelocity(before); // the game's own hit knockback (0.4) is dropped: only the weapon's knockback applies
        }
        if (target instanceof LivingEntity living && def.knockback() > 0f) {
            double horizontal = Math.sqrt(dir[0] * dir[0] + dir[2] * dir[2]);
            if (horizontal > 1e-6) {
                living.takeKnockback(def.knockback(), -dir[0] / horizontal, -dir[2] / horizontal); // (x, z) point at the attacker
            }
        }
    }

    static void hitBlock(ServerWorld world, ServerPlayerEntity player, WeaponCatalog.WeaponDef def,
                                 WeaponCatalog.BlockRules rules, BlockHealthTracker tracker, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        BlockHealthModel.Hit result = tracker.of(world).hit(pos.asLong(), state.getHardness(world, pos), def.blockPower(), world.getTime());
        if (result.immune()) {
            return;
        }
        if (result.broken()) {
            world.setBlockBreakingInfo(CrackId.of(pos.asLong()), pos, -1);
            BlockBreaker.breakBlock(world, pos, state, player, rules.dropsWithoutTool());
        } else {
            world.setBlockBreakingInfo(CrackId.of(pos.asLong()), pos, result.stage());
        }
    }
}
