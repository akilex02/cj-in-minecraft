package dev.cjmc;

import java.util.Set;

/** What CJ eats in the animation, by the food the player eats: meat is the chicken, baked things the pizza and the rest the burger. */
public final class EatKinds {
    private static final Set<String> MEAT = Set.of("beef", "cooked_beef", "porkchop", "cooked_porkchop", "chicken", "cooked_chicken", "mutton", "cooked_mutton",
            "rabbit", "cooked_rabbit", "rotten_flesh", "cod", "cooked_cod", "salmon", "cooked_salmon", "tropical_fish", "pufferfish", "spider_eye");
    private static final Set<String> BAKED = Set.of("bread", "cookie", "cake", "pumpkin_pie", "baked_potato");

    private EatKinds() {}

    /** The kind (Protocol.EAT_*) for an item id without its namespace; anything unknown is a burger. */
    public static int kindOf(String itemPath) {
        if (itemPath == null) {
            return Protocol.EAT_BURGER;
        }
        if (MEAT.contains(itemPath)) {
            return Protocol.EAT_CHICKEN;
        }
        return BAKED.contains(itemPath) ? Protocol.EAT_PIZZA : Protocol.EAT_BURGER;
    }

    /** The state flags of a player eating food of this kind. */
    public static int flags(int kind) {
        return Protocol.FLAG_EATING | (kind << Protocol.EAT_KIND_SHIFT);
    }
}
