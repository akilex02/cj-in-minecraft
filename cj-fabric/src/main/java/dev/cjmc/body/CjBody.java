package dev.cjmc.body;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server side of the body reactions: decides which damage knocks a player down and tells the client through a queue in memory. */
public final class CjBody {
    private static final Map<UUID, Integer> KNOCKDOWNS = new ConcurrentHashMap<>(); // the kind of the knockdown the server decided

    private CjBody() {}

    public static void init() {
        // a player wearing the jetpack takes no fall damage (the server works the fall out by itself, so it is cancelled here)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayerEntity player && dev.cjmc.jetpack.JetpackRules.cancelsDamage(
                        dev.cjmc.CjItems.isWearingJetpack(player), source.isOf(DamageTypes.FALL))));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (entity instanceof ServerPlayerEntity player && !blocked) {
                int kind = KnockdownRules.kind(source.isOf(DamageTypes.FALL), source.isIn(DamageTypeTags.IS_EXPLOSION), damageTaken);
                if (kind != KnockdownRules.NONE) {
                    KNOCKDOWNS.merge(player.getUuid(), kind, Math::max); // an explosion in the same tick as a blow stays an explosion
                }
            }
        });
    }

    /** The kind (KnockdownRules.NORMAL or BLAST) of the knockdown the server decided for this player since the last call, or NONE. */
    public static int takeKnockdown(UUID player) {
        Integer kind = KNOCKDOWNS.remove(player);
        return kind == null ? KnockdownRules.NONE : kind;
    }
}
