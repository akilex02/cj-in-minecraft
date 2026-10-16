package dev.cjmc;

import java.util.List;

/** Who needs which bullets: the three kinds of ammo, and the rules of firing with and without them. Pure logic, no Minecraft. */
public final class AmmoRules {
    public static final String LIGHT = "ammo1";  // Colt 45, Desert Eagle, Micro SMG
    public static final String HEAVY = "ammo2";  // M4, AK-47, shotgun, minigun
    public static final String ROCKET = "ammo3"; // rocket launcher
    public static final List<String> ALL = List.of(LIGHT, HEAVY, ROCKET);

    private AmmoRules() {}

    /** The empty string means the weapon takes no ammo (fists, bat). */
    public static boolean isKnown(String ammo) {
        return ammo.isEmpty() || ALL.contains(ammo);
    }

    public static boolean usesAmmo(String ammo) {
        return !ammo.isEmpty();
    }

    /** Whether the weapon may fire: it takes no ammo, the player is in creative, or at least one bullet is in the inventory. */
    public static boolean canFire(String ammo, int available, boolean freeAmmo) {
        return !usesAmmo(ammo) || freeAmmo || available > 0;
    }

    /** The bullets a round spends: one, except in creative or for a weapon without ammo. */
    public static int cost(String ammo, boolean freeAmmo) {
        return usesAmmo(ammo) && !freeAmmo ? 1 : 0;
    }

    /** True while the fire button is held on an empty weapon: the dry-fire intent goes to GTA, which clicks once per press. */
    public static boolean dryFire(boolean down, String ammo, int available, boolean freeAmmo) {
        return down && !canFire(ammo, available, freeAmmo);
    }
}
