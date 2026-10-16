package dev.cjmc;

/** When CJ punches: with an empty hand or any item that does not hit on its own. Swords, axes and the like keep their vanilla blow. Pure logic. */
public final class PunchRules {
    private PunchRules() {}

    /**
     * @param holdsWeapon the main hand holds one of the weapons of the mod
     * @param hitsOnItsOwn the item in hand is a tool or hits on its own (a sword, an axe, a pickaxe, a hoe, a trident, shears...)
     */
    public static boolean punches(boolean holdsWeapon, boolean hitsOnItsOwn) {
        return !holdsWeapon && !hitsOnItsOwn;
    }
}
