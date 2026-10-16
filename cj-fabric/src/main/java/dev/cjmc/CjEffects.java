package dev.cjmc;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * While the player is drawn as CJ in third person he gets Speed I and Jump Boost II for as long as it lasts, with no particles: that
 * is the pace and the jump of CJ in GTA as the user tuned them (the jump boost was taken off on 2026-10-07 and given back on 2026-10-10 at the user's request). The client says when (CjEffectsClient), the server does it, and only
 * effects of exactly this shape are ever taken away, so a potion or a beacon of the player's own is left alone.
 */
public final class CjEffects {
    public static final int SPEED_AMPLIFIER = 0;  // Speed I
    public static final int JUMP_AMPLIFIER = 0;   // Jump Boost I (it was II until 2026-10-10)
    public static final int OLD_JUMP_AMPLIFIER = 1; // the Jump Boost II of that version: found on a player, it is taken off and the right one put on

    /** The corner icons of the effects are hidden: the player gets the pace and the jump without a sign that they are there. */
    public static final boolean SHOW_ICON = false;

    /** What to do to the effects this round. */
    public record Plan(boolean addSpeed, boolean addJump, boolean removeSpeed, boolean removeJump) {}

    private CjEffects() {}

    /**
     * `speedStale` and `jumpStale`: the effect found is ours in shape but of an earlier version (the Jump Boost II, or one that still shows its icon):
     * it goes, and the right one comes.
     */
    public static Plan plan(boolean wanted, boolean speedPresent, boolean speedOurs, boolean speedStale, boolean jumpPresent, boolean jumpOurs, boolean jumpStale) {
        if (wanted) {
            return new Plan(!speedPresent || speedStale, !jumpPresent || jumpStale, speedStale, jumpStale);
        }
        return new Plan(false, false, (speedPresent && speedOurs) || speedStale, (jumpPresent && jumpOurs) || jumpStale);
    }

    /** An effect is ours when it is infinite, has our level and shows no particles. */
    public static boolean isOurs(boolean infinite, int amplifier, boolean showParticles, int expectedAmplifier) {
        return infinite && amplifier == expectedAmplifier && !showParticles;
    }

    /** Server thread: brings the player's effects to what is wanted. Safe to call every second. */
    public static void sync(ServerPlayerEntity player, boolean wanted) {
        StatusEffectInstance speed = player.getStatusEffect(StatusEffects.SPEED);
        StatusEffectInstance jump = player.getStatusEffect(StatusEffects.JUMP_BOOST);
        boolean speedOurs = speed != null && isOurs(speed.isInfinite(), speed.getAmplifier(), speed.shouldShowParticles(), SPEED_AMPLIFIER);
        boolean jumpOurs = jump != null && isOurs(jump.isInfinite(), jump.getAmplifier(), jump.shouldShowParticles(), JUMP_AMPLIFIER);
        Plan plan = plan(wanted, speed != null, speedOurs, speedOurs && speed.shouldShowIcon() != SHOW_ICON,
                jump != null, jumpOurs,
                (jumpOurs && jump.shouldShowIcon() != SHOW_ICON) || (jump != null && isOurs(jump.isInfinite(), jump.getAmplifier(), jump.shouldShowParticles(), OLD_JUMP_AMPLIFIER)));
        if (plan.removeSpeed()) { // what goes is taken off first: a stale one would keep the new one from taking its place
            player.removeStatusEffect(StatusEffects.SPEED);
        }
        if (plan.removeJump()) {
            player.removeStatusEffect(StatusEffects.JUMP_BOOST);
        }
        if (plan.addSpeed()) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, StatusEffectInstance.INFINITE, SPEED_AMPLIFIER, false, false, SHOW_ICON));
        }
        if (plan.addJump()) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, StatusEffectInstance.INFINITE, JUMP_AMPLIFIER, false, false, SHOW_ICON));
        }
    }
}
