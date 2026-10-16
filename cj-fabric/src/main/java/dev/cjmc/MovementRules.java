package dev.cjmc;

/** How a weapon limits the player's movement, copying what GTA lets CJ do with it (measured, see docs/m2-spike.md). */
public final class MovementRules {
    /** `crouch`: the player is crouched (the sneak pose and GTA's crouch), and the movement input is scaled for it. */
    public record Limits(float speedFactor, boolean canSprint, boolean canJump, boolean forceSneak, boolean crouch) {
        public Limits(float speedFactor, boolean canSprint, boolean canJump, boolean forceSneak) {
            this(speedFactor, canSprint, canJump, forceSneak, false);
        }
    }

    /** The share of the walking speed while striking: what Slowness IV leaves (4 levels of -15 %), applied to the input, so it shows no icon and does not touch the field of view. */
    public static final float STRIKE_SPEED = 0.4f;

    private static final Limits FREE = new Limits(1.0f, true, true, false);

    private MovementRules() {}

    /**
     * The limits of a crouched player: the slower of the crouched pace and the weapon's, standing still while he fires a firearm. A weapon that
     * does not fire crouched is moved as standing while it is fired (the client stands him up on that very tick).
     */
    public static Limits of(WeaponCatalog.WeaponDef weapon, boolean aiming, boolean firing, boolean crouching) {
        Limits base = of(weapon, aiming, firing);
        if (!crouching || (firing && weapon != null && !weapon.crouchFire())) {
            return base;
        }
        float speed = firing && weapon != null && weapon.reach() == 0f ? 0.0f : Math.min(CrouchRules.SPEED, base.speedFactor());
        return new Limits(speed, base.canSprint(), base.canJump(), true, true);
    }

    /**
     * @param weapon the weapon in the main hand, or null for anything else
     * @param aiming the aim button is held
     * @param firing the fire button is held
     */
    public static Limits of(WeaponCatalog.WeaponDef weapon, boolean aiming, boolean firing) {
        if (weapon == null) {
            return FREE;
        }
        if (firing && weapon.reach() > 0f) {
            return new Limits(STRIKE_SPEED, false, weapon.canJump(), false); // striking with the fists or the bat: GTA's blows go at a slow pace
        }
        if (firing && !weapon.moveWhileFiring()) {
            return new Limits(0.0f, false, weapon.canJump(), false); // GTA fires these standing still
        }
        return aiming ? new Limits(weapon.aimSpeed(), weapon.sprintAiming(), weapon.canJump(), false)
                : new Limits(1.0f, weapon.sprintHeld(), weapon.canJump(), false);
    }
}
