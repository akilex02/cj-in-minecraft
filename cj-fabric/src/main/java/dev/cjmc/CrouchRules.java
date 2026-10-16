package dev.cjmc;

/**
 * Crouching with the C key, as in GTA: a toggle that makes CJ crouch (the sneak pose of Minecraft with GTA's own crouch), walk at the pace of
 * Slowness III, and fire a weapon with a tighter spread but standing still. Pure logic: the client applies it.
 */
public final class CrouchRules {
    /** The share of the walking speed while crouched: what Slowness III leaves (3 levels of -15 %). */
    public static final float SPEED = 0.55f;
    private static final double SPREAD_FACTOR = 0.5; // a crouched shot has half the spread

    private CrouchRules() {}

    /** The factor of the spread of a shot: tighter crouched. */
    public static double spreadFactor(boolean crouching) {
        return crouching ? SPREAD_FACTOR : 1.0;
    }

    /**
     * Whether a crouched player stays crouched this tick. Jumping and sprinting stand him up, as they do CJ in GTA, and so do the things that take
     * the body over (flight, the car, water, a climb, a knockdown); without GTA answering there is no crouch; and a weapon that does not fire
     * crouched (`standsOnFire`: the bat, the minigun, the rocket launcher, with the attack button down) stands him up as he fires it.
     */
    public static boolean stays(boolean jump, boolean sprint, boolean flying, boolean jetpack, boolean driving, boolean inFluid, boolean climbing,
                                boolean knocked, boolean dead, boolean bridgeLive, boolean standsOnFire) {
        return bridgeLive && !(jump || sprint || flying || jetpack || driving || inFluid || climbing || knocked || dead || standsOnFire);
    }

    /**
     * The factor for the movement input at the end of the input tick. The sneak pose has already scaled it by its own factor (`vanillaFactor`, 0.3)
     * once the pose is the sneaking one (`vanillaSlowed`), so the rest is what brings it to `target` of the normal speed; on the first tick the
     * pose is not there yet and nothing has been taken.
     */
    public static float inputScale(boolean vanillaSlowed, float vanillaFactor, float target) {
        return vanillaSlowed && vanillaFactor > 0f ? target / vanillaFactor : target;
    }
}
