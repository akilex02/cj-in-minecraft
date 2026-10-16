package dev.cjmc;

import net.fabricmc.api.ModInitializer;

/** Runs on both sides (the integrated server needs the items too). Nothing here may touch client classes. */
public class CjMain implements ModInitializer {
    @Override
    public void onInitialize() {
        WeaponCatalog catalog = WeaponCatalog.load();
        CjItems.register(catalog);
        ChestLootEvents.register();
        dev.cjmc.combat.CjCombat.init(catalog);
        dev.cjmc.body.CjBody.init();
        // the server works the distance fallen out by itself, and it only grows on the way down: while he wears the jetpack it is forgotten
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (net.minecraft.server.network.ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (dev.cjmc.jetpack.JetpackRules.resetsFallDistance(CjItems.isWearingJetpack(player))) {
                    player.fallDistance = 0f;
                }
                if (dev.cjmc.swim.SwimRules.resetsFallDistance(player.isTouchingWater())) {
                    player.fallDistance = 0f; // the distance fallen only grows on the way down: it would add up over a long dive
                }
                FistMiningServer.update(player);
            }
        });
    }
}
