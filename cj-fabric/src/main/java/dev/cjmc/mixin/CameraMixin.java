package dev.cjmc.mixin;

import dev.cjmc.CameraShoulder;
import dev.cjmc.CarCamera;
import dev.cjmc.VehicleClient;
import dev.cjmc.CjItems;
import dev.cjmc.CjRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While CJ aims in third person the camera slides over his right shoulder so he does not cover the crosshair. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPos(Vec3d pos);

    @Shadow
    public abstract Vec3d getPos();

    @Shadow
    public abstract float getYaw();

    @Unique
    private final CameraShoulder cj$shoulder = new CameraShoulder();
    @Unique
    private final CarCamera cj$car = new CarCamera();
    @Unique
    private long cj$lastNs;

    @Inject(method = "update", at = @At("TAIL"))
    private void cj$overTheShoulder(BlockView area, Entity focused, boolean thirdPerson, boolean inverse, float tickDelta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!thirdPerson && mc.player != null && focused == mc.player && VehicleClient.driving()) {
            Vec3d eyes = CjRenderer.INSTANCE.carEyes(mc.player, tickDelta); // first person at the wheel: from CJ's eyes in his seat
            if (eyes != null) {
                setPos(eyes);
            }
        }
        long now = System.nanoTime();
        double dt = cj$lastNs == 0 ? 0 : (now - cj$lastNs) / 1e9;
        cj$lastNs = now;
        MinecraftClient client = MinecraftClient.getInstance();
        boolean active = thirdPerson && !inverse && client.player != null && focused == client.player
                && CjItems.weaponOf(client.player.getMainHandStack()) != null && CjItems.weaponOf(client.player.getMainHandStack()).reach() == 0f
                && CjRenderer.INSTANCE.isAiming();
        boolean driving = thirdPerson && !inverse && client.player != null && focused == client.player && VehicleClient.driving();
        double behind = cj$car.step(driving, dt);
        if (behind > 1e-4 && thirdPerson && !inverse && focused != null) {
            // farther from the car, on the line from the eyes to where the game put the camera; the blocks still stop it
            Vec3d eye = focused.getCameraPosVec(tickDelta);
            Vec3d back = getPos().subtract(eye);
            double length = back.length();
            if (length > 1e-4) {
                Vec3d direction = back.multiply(1.0 / length);
                double wanted = CarCamera.distanceFor(length, behind);
                Vec3d end = eye.add(direction.multiply(wanted + 0.1));
                HitResult hit = area.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, focused));
                double clear = hit.getType() == HitResult.Type.MISS ? wanted : Math.max(0.0, hit.getPos().distanceTo(eye) - 0.1);
                setPos(eye.add(direction.multiply(Math.max(length, Math.min(wanted, clear)))));
            }
        }
        double shift = cj$shoulder.step(active, dt);
        if (cj$shoulder.amount() > 1e-4 && thirdPerson && !inverse && focused != null) {
            // pull the camera in along the line from the eyes to where the game put it (clipping included), toward the shoulder
            Vec3d eye = focused.getCameraPosVec(tickDelta);
            Vec3d back = getPos().subtract(eye);
            double length = back.length();
            if (length > 1e-4) {
                setPos(eye.add(back.multiply(CameraShoulder.distanceFor(length, cj$shoulder.amount()) / length)));
            }
        }
        if (shift > 1e-4 && thirdPerson && !inverse) {
            double yaw = Math.toRadians(getYaw());
            // the camera's right: Minecraft's forward is (-sin yaw, 0, cos yaw)
            setPos(getPos().add(-Math.cos(yaw) * shift, 0, -Math.sin(yaw) * shift));
        }
    }
}
