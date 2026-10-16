package dev.cjmc.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

/**
 * A rocket in flight: it moves straight with no gravity, trails smoke and flames, and explodes where it hits a block or an entity
 * (the shooter is ignored until the rocket has left their box) or where its range runs out. Invisible: the trail is its look.
 */
public final class RocketEntity extends ProjectileEntity {
    private RocketFlight flight; // server only
    private float power;

    public RocketEntity(EntityType<? extends RocketEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    public void launch(ServerPlayerEntity owner, Vec3d from, Vec3d direction, double speed, double range, float power) {
        setOwner(owner);
        setPosition(from.x, from.y, from.z);
        setVelocity(direction.normalize().multiply(speed / 20.0));
        this.flight = new RocketFlight(speed, range);
        this.power = power;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {}

    @Override
    public void tick() {
        World world = getWorld();
        if (world.isClient) {
            setPosition(getPos().add(getVelocity())); // the server's position updates correct it
            world.addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
            world.addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
            return;
        }
        if (flight == null) {
            discard(); // not launched by a shot: nothing to fly
            return;
        }
        Entity owner = getOwner();
        if (RocketRules.ownerGone(owner instanceof ServerPlayerEntity, owner != null && owner.isAlive(), owner != null && owner.getWorld() == world)) {
            discard(); // the shooter died, left or changed dimension: no explosion in their name
            return;
        }
        super.tick(); // keeps track of the shooter leaving the rocket's way; ProjectileEntity does not look for hits itself (ThrownEntity does)
        HitResult hit = ProjectileUtil.getCollision(this, this::canHit); // from here to here + velocity: what this tick's step would run into
        if (hit.getType() != HitResult.Type.MISS) {
            hitOrDeflect(hit); // ends in onCollision, which explodes and discards
            if (isRemoved()) {
                return;
            }
        }
        double step = flight.advance();
        Vec3d velocity = getVelocity();
        if (step > 0 && velocity.lengthSquared() > 0) {
            setPosition(getPos().add(velocity.normalize().multiply(step)));
        }
        if (flight.expired()) {
            explode();
        }
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (getWorld() instanceof ServerWorld) {
            explode();
        }
    }

    private void explode() {
        if (isRemoved()) {
            return;
        }
        World world = getWorld();
        Entity owner = getOwner();
        // mobGriefing decides whether the blocks break (switched off, the rocket still hurts but breaks nothing). TNT is the type that breaks
        // them and drops their items in full (MOB would drop only about one in six: mobExplosionDropDecay); NONE breaks nothing.
        World.ExplosionSourceType type = world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)
                ? World.ExplosionSourceType.TNT : World.ExplosionSourceType.NONE;
        world.createExplosion(this, world.getDamageSources().explosion(this, owner), null, getX(), getY(), getZ(), power, false, type);
        discard();
    }
}
