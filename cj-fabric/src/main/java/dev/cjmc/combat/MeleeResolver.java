package dev.cjmc.combat;

import dev.cjmc.WeaponCatalog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Resolves one melee blow on the server thread: the living entity nearest to where the player looks, in reach and cone, takes the hit. */
final class MeleeResolver {
    private MeleeResolver() {}

    static void strike(ServerWorld world, ServerPlayerEntity player, WeaponCatalog.WeaponDef def, ShotRequest request) {
        double[] eye = request.origin();
        double[] look = request.direction();
        Vec3d from = new Vec3d(eye[0], eye[1], eye[2]);
        Box area = new Box(from, from).expand(def.reach() + 2.0);
        List<LivingEntity> open = new ArrayList<>();
        List<double[]> centers = new ArrayList<>();
        List<Double> radii = new ArrayList<>();
        for (Entity e : world.getOtherEntities(player, area, x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator() && x.canHit())) {
            Box box = e.getBoundingBox();
            Vec3d c = box.getCenter();
            double radius = 0.5 * Math.max(box.getLengthX(), Math.max(box.getLengthY(), box.getLengthZ()));
            // a wall between the eye and the target stops the blow: it must be no nearer than where the line to the centre enters the target's box
            // (not the centre minus a radius: a door or a pane right in front of a tall mob would let the blow through)
            Vec3d entry = box.raycast(from, c).orElse(from); // the eye inside the box: nothing is in between
            BlockHitResult wall = world.raycast(new RaycastContext(from, c, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, player));
            if (wall.getType() == HitResult.Type.MISS || wall.getPos().distanceTo(from) >= from.distanceTo(entry) - 1e-3) {
                open.add((LivingEntity) e);
                centers.add(new double[] {c.x, c.y, c.z});
                radii.add(radius);
            }
        }
        double[] r = new double[radii.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = radii.get(i);
        }
        int hit = MeleeTarget.pick(eye, look, def.reach(), def.coneHalfAngle(), centers.toArray(new double[0][]), r);
        if (hit >= 0) {
            ShotResolver.hitEntity(world, player, def, open.get(hit), look);
        } else if (def.blockPower() > 0f) {
            strikeBlock(world, player, def, from, look);
        }
    }

    /** With nothing alive to hit, a weapon with a blockPower (the bat) wears the block in front of the player down, as a bullet does. */
    private static void strikeBlock(ServerWorld world, ServerPlayerEntity player, WeaponCatalog.WeaponDef def, Vec3d from, double[] look) {
        if (!player.getAbilities().allowModifyWorld) {
            return; // adventure mode: nothing is broken
        }
        Vec3d to = from.add(look[0] * def.reach(), look[1] * def.reach(), look[2] * def.reach());
        BlockHitResult block = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, player));
        if (block.getType() == HitResult.Type.MISS) {
            return;
        }
        BlockPos pos = block.getBlockPos();
        if (player.isCreative()) {
            // creative: instant, and with no drops, as when mining by hand
            if (!world.getBlockState(pos).isAir()) {
                world.setBlockBreakingInfo(CrackId.of(pos.asLong()), pos, -1);
                world.breakBlock(pos, false, player, 512);
            }
            return;
        }
        ShotResolver.hitBlock(world, player, def, CjCombat.catalog().blocks(), CjCombat.tracker(), pos);
    }
}
