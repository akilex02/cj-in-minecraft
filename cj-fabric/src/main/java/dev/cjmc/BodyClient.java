package dev.cjmc;

import dev.cjmc.body.CjBody;
import dev.cjmc.body.FallRules;
import dev.cjmc.body.HurtCounter;
import dev.cjmc.body.KnockdownRules;
import dev.cjmc.body.KnockdownTimer;
import net.minecraft.client.network.ClientPlayerEntity;

/** The body state the bridge needs (falling, knocked, dead, hurt counter) and the freeze while CJ is down. Only while CJ is drawn. */
public final class BodyClient {
    private static final HurtCounter HURT = new HurtCounter();
    private static final KnockdownTimer TIMER = new KnockdownTimer();
    private static volatile boolean frozen;
    private static volatile boolean knockedDown;
    private static boolean blast; // the knockdown under way came from an explosion

    private BodyClient() {}

    public static boolean frozen() {
        return frozen;
    }

    /** Whether CJ is knocked down (falling to the ground, lying, getting up), as opposed to frozen by a climb. */
    public static boolean knockedDown() {
        return knockedDown;
    }

    private static boolean inFluid(ClientPlayerEntity player) {
        return player.isTouchingWater() || player.isInLava();
    }

    /** Called once per frame by CjClient with the player the State is about; returns the body bits of the flags. */
    static int flags(ClientPlayerEntity player, long nowMs) {
        boolean drawn = CjClient.bridgeLive() && CjRenderer.INSTANCE.hasFreshPose();
        if (!drawn) {
            ClimbController.cancel();
            TIMER.clear();
            frozen = false;
            knockedDown = false;
            return 0;
        }
        boolean dead = !player.isAlive() || player.getHealth() <= 0f;
        int hurt = HURT.update(player.getHealth(), player.isAlive());
        if (dead) {
            ClimbController.cancel();
            TIMER.clear(); // the death overrides a knockdown under way; the death screen holds the player
        } else {
            int kind = ClimbController.active() ? KnockdownRules.NONE : CjBody.takeKnockdown(player.getUuid()); // a knockdown waits for the climb to end
            boolean eligible = KnockdownRules.eligible(player.hasVehicle() || VehicleClient.driving(), inFluid(player), player.isClimbing(),
                    player.getAbilities().flying, player.isFallFlying());
            if (kind != KnockdownRules.NONE && eligible && TIMER.start(nowMs)) {
                blast = kind == KnockdownRules.BLAST;
            }
        }
        boolean knocked = !dead && TIMER.knocked(nowMs);
        boolean climbing = !dead && ClimbController.active();
        knockedDown = !dead && TIMER.frozen(nowMs);
        frozen = !dead && (knockedDown || climbing);
        boolean falling = !dead && !frozen && FallRules.falling(player.isOnGround(),
                inFluid(player), player.isClimbing(), player.getAbilities().flying, player.hasVehicle() || VehicleClient.driving(),
                player.fallDistance, player.getVelocity().y, player.isFallFlying());
        return Protocol.bodyFlags(falling, knocked, dead, hurt, knocked && blast)
                | (!dead && ClimbController.animating(nowMs) ? Protocol.FLAG_WALL_CLIMB : 0)
                | (!dead && JetpackClient.flying() ? Protocol.FLAG_JETPACK : 0)
                | (!dead && JetpackClient.thrusting() ? Protocol.FLAG_JETPACK_THRUST : 0)
                | (!dead && SwimClient.inWater() ? Protocol.FLAG_IN_WATER : 0)
                | (!dead && SwimClient.underwater() ? Protocol.FLAG_UNDERWATER : 0)
                | (!dead && SwimClient.moving() ? Protocol.FLAG_WATER_MOVING : 0);
    }
}
