package dev.cjmc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A crosshair for the third-person view, where the game draws none: it is a dot that sits on the screen point where the shot lands (the ray
 * from the player's eyes, not the middle of the screen, since the camera is behind CJ). In first person the vanilla one stays.
 */
final class CjCrosshair {
    private static final double RANGE = AimRay.MAX_DISTANCE;
    private static final int COLOR = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF000000;

    private CjCrosshair() {}

    /** The dot is for firearms: a bat or the fists have no aim. */
    private static boolean holdsFirearm(ClientPlayerEntity player) {
        dev.cjmc.WeaponCatalog.WeaponDef w = CjItems.weaponOf(player.getMainHandStack());
        return w != null && w.reach() == 0f;
    }

    static void draw(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.currentScreen != null || client.options.hudHidden
                || client.options.getPerspective().isFirstPerson() || !holdsFirearm(player)
                || !CjRenderer.INSTANCE.isAiming()) { // shown while aiming (right click), firing (left click) or both
            return;
        }
        float delta = tickCounter.getTickDelta(false);
        Vec3d eye = player.getCameraPosVec(delta);
        Vec3d look = player.getRotationVec(delta);
        Vec3d end = eye.add(look.multiply(RANGE));
        double distance = RANGE;
        HitResult block = player.raycast(RANGE, delta, false);
        if (block.getType() != HitResult.Type.MISS) {
            distance = block.getPos().distanceTo(eye);
        }
        EntityHitResult entity = ProjectileUtil.raycast(player, eye, end, new Box(eye, end).expand(1.0),
                e -> !e.isSpectator() && e.canHit(), distance * distance);
        if (entity != null) {
            distance = Math.min(distance, entity.getPos().distanceTo(eye));
        }
        Vec3d target = eye.add(look.multiply(distance));

        net.minecraft.client.render.Camera camera = client.gameRenderer.getCamera();
        Vec3d rel = target.subtract(camera.getPos());
        Vector3f v = new Quaternionf(camera.getRotation()).conjugate().transform(new Vector3f((float) rel.x, (float) rel.y, (float) rel.z));
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        float[] p = CrosshairMath.project(v.x, v.y, v.z, client.options.getFov().getValue(), w, h);
        if (p == null) {
            return;
        }
        int x = Math.round(p[0]);
        int y = Math.round(p[1]);
        plus(context, x + 1, y + 1, SHADOW);
        plus(context, x, y, COLOR);
    }

    private static void plus(DrawContext context, int x, int y, int color) {
        context.fill(x - 1, y - 1, x + 1, y + 1, color); // a 2x2 dot
    }
}
