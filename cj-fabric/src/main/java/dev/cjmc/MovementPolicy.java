package dev.cjmc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/** The movement limits of the weapon the local player holds right now (client thread). */
public final class MovementPolicy {
    private MovementPolicy() {}

    public static MovementRules.Limits current() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return new MovementRules.Limits(1.0f, true, true, false);
        }
        WeaponCatalog.WeaponDef weapon = CjItems.activeWeapon(player);
        boolean melee = weapon != null && weapon.reach() > 0f;
        int intents = ControlsMapper.intents(weapon != null && !melee, melee, client.currentScreen != null,
                client.options.attackKey.isPressed(), client.options.useKey.isPressed(), BodyClient.frozen());
        return MovementRules.of(weapon, (intents & Protocol.INTENT_AIM) != 0, (intents & (Protocol.INTENT_FIRE | Protocol.INTENT_MELEE)) != 0, CrouchClient.crouching());
    }
}
