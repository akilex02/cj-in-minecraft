package dev.cjmc;

import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Client side of CjEffects: wanted while the player is played as CJ, in first person as in third. */
public final class CjEffectsClient {
    private static final SyncPacer PACER = new SyncPacer(20);

    private CjEffectsClient() {}

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CjEffectsClient::tick);
    }

    /** CJ is played: the bridge is live and he has a fresh pose, in first person as in third (what the effects and the delayed jump are for). */
    public static boolean cjActive() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && client.world != null && CjClient.bridgeLive() && CjRenderer.INSTANCE.hasFreshPose();
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        boolean wanted = cjActive();
        if (!PACER.tick(wanted) || player == null) {
            return;
        }
        IntegratedServer server = client.getServer();
        if (server == null) {
            return; // a connection to another server: the effects are the server's business
        }
        UUID id = player.getUuid();
        server.execute(() -> {
            ServerPlayerEntity serverPlayer = server.getPlayerManager().getPlayer(id);
            if (serverPlayer != null) {
                CjEffects.sync(serverPlayer, wanted);
            }
        });
    }
}
