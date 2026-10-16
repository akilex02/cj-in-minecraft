package dev.cjmc;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Crouching with the C key, as in GTA: the key toggles it, and the rules of CrouchRules say when it ends. While crouched the player has the sneak
 * pose of Minecraft at the pace of Slowness III (MovementRules and the input mixin), and the bridge keeps GTA's own crouch in step (INTENT_CROUCH).
 * Shift stays what it was: the sneak with CJ's slow walk.
 */
public final class CrouchClient {
    private static KeyBinding key;
    private static volatile boolean crouching;
    private static Object crouchPlayer; // the crouch ends for a new player (a new world, a respawn)

    private CrouchClient() {}

    public static void init() {
        key = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cj_fabric.crouch", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.cj_fabric"));
    }

    /** The player is crouched: the toggle is on and nothing has stood him up. */
    public static boolean crouching() {
        return crouching;
    }

    /** Called every client tick. */
    public static void tick(ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (key == null) {
            return;
        }
        boolean toggled = false;
        while (key.wasPressed()) {
            toggled = !toggled;
        }
        if (crouchPlayer != player) {
            crouchPlayer = player;
            crouching = false;
        }
        boolean screen = client.currentScreen != null;
        WeaponCatalog.WeaponDef held = CjItems.weaponOf(player.getMainHandStack());
        boolean standsOnFire = held != null && !held.crouchFire() && !screen && client.options.attackKey.isPressed();
        // what stands a crouched player up: also the starting conditions of a crouch (a toggle on to be stood up at once would do nothing)
        boolean stays = CrouchRules.stays(JumpDelay.rawPressed(), client.options.sprintKey.isPressed(), player.getAbilities().flying || player.isSpectator(),
                JetpackClient.flying(), player.hasVehicle() || VehicleClient.driving(), player.isTouchingWater() || player.isInLava(),
                player.isClimbing() || ClimbController.active(), BodyClient.knockedDown(), !player.isAlive(), CjClient.bridgeLive(), standsOnFire);
        if (toggled) {
            crouching = !crouching && stays;
        } else if (!stays) {
            crouching = false;
        }
    }
}
