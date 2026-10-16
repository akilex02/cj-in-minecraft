package dev.cjmc.mixin;

import dev.cjmc.BodyClient;
import dev.cjmc.CjClient;
import dev.cjmc.JumpBlock;
import dev.cjmc.MovementPolicy;
import dev.cjmc.MovementRules;
import dev.cjmc.SneakRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Limits the input to what GTA lets CJ do with the weapon in hand (a slow crawl while aiming with a rifle, no jumping with the minigun). */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void cj$limitInput(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        jumping = dev.cjmc.JumpDelay.apply(jumping, dev.cjmc.CjEffectsClient.cjActive()); // CJ jumps a little after the key, as in GTA
        MovementRules.Limits limits = MovementPolicy.current();
        ClientPlayerEntity self = MinecraftClient.getInstance().player;
        if (self != null && dev.cjmc.ClimbController.onInput(self, dev.cjmc.JumpDelay.rawPressed(), movementForward > 0, sneaking,
                dev.cjmc.climb.ClimbRules.permitted(BodyClient.knockedDown(), limits.canJump(), cj$forcesSneak(limits.forceSneak()),
                        dev.cjmc.CjItems.isWearingJetpack(self)),
                System.nanoTime() / 1_000_000L)) {
            jumping = false; // a climb begins (or is under way): the vanilla jump is not made
        }
        if (limits.crouch() && cj$forcesSneak(limits.forceSneak())) {
            // crouched: Slowness III, over the factor the sneak pose took itself (it may even raise the input: the sneak took more than the wanted share)
            float scale = dev.cjmc.CrouchRules.inputScale(slowDown, slowDownFactor, limits.speedFactor());
            movementForward *= scale;
            movementSideways *= scale;
        } else if (limits.speedFactor() < 1.0f) {
            movementForward *= limits.speedFactor();
            movementSideways *= limits.speedFactor();
        }
        if (cj$forcesSneak(limits.forceSneak())) {
            sneaking = true; // crouched: the sneak pose
        }
        if (cj$blocksJump(limits.canJump())) {
            jumping = false;
        }
        if (BodyClient.frozen() || dev.cjmc.VehicleClient.driving() || dev.cjmc.skate.SkateRide.active()) { // CJ is down, or at the wheel (the keys are the car's, read apart) (falling to the ground, lying, getting up): the player cannot move or jump, as in GTA
            movementForward = 0;
            movementSideways = 0;
            jumping = false;
            sneaking = false;
        }
    }

    private static boolean cj$forcesSneak(boolean forceSneak) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player != null && SneakRules.shouldForce(forceSneak, CjClient.bridgeLive(), player.getAbilities().flying, player.hasVehicle() || dev.cjmc.VehicleClient.driving(),
                player.isTouchingWater() || player.isInLava(), player.isClimbing(), player.isSpectator());
    }

    private static boolean cj$blocksJump(boolean canJump) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player != null && JumpBlock.applies(canJump, player.isOnGround(), player.getAbilities().flying, player.hasVehicle(),
                player.isTouchingWater() || player.isInLava(), player.isClimbing());
    }
}
