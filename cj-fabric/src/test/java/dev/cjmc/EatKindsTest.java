package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EatKindsTest {
    @Test
    void theKindsAreTheOnesOfTheProtocol() {
        assertEquals(1 << 19, Protocol.FLAG_EATING);
        assertEquals(20, Protocol.EAT_KIND_SHIFT);
        assertEquals(0, Protocol.EAT_BURGER);
        assertEquals(1, Protocol.EAT_CHICKEN);
        assertEquals(2, Protocol.EAT_PIZZA);
    }

    @Test
    void meatIsChickenBakedThingsArePizzaAndTheRestIsBurger() {
        for (String meat : new String[] {"cooked_beef", "beef", "cooked_porkchop", "porkchop", "cooked_chicken", "chicken", "cooked_mutton", "mutton",
                "cooked_rabbit", "rabbit", "rotten_flesh", "cooked_cod", "cooked_salmon"}) {
            assertEquals(Protocol.EAT_CHICKEN, EatKinds.kindOf(meat), meat);
        }
        for (String baked : new String[] {"bread", "cookie", "cake", "pumpkin_pie", "baked_potato"}) {
            assertEquals(Protocol.EAT_PIZZA, EatKinds.kindOf(baked), baked);
        }
        for (String other : new String[] {"apple", "golden_apple", "carrot", "melon_slice", "mushroom_stew", "dried_kelp", "sweet_berries", "honey_bottle"}) {
            assertEquals(Protocol.EAT_BURGER, EatKinds.kindOf(other), other);
        }
    }

    @Test
    void anythingUnknownIsABurger() {
        assertEquals(Protocol.EAT_BURGER, EatKinds.kindOf(""));
        assertEquals(Protocol.EAT_BURGER, EatKinds.kindOf(null));
        assertEquals(Protocol.EAT_BURGER, EatKinds.kindOf("some_mod_food"));
    }

    @Test
    void theFlagsHaveTheEatingBitAndTheKindAndLeaveTheRestAlone() {
        int flags = EatKinds.flags(Protocol.EAT_PIZZA);
        assertTrue((flags & Protocol.FLAG_EATING) != 0);
        assertEquals(Protocol.EAT_PIZZA, (flags >> Protocol.EAT_KIND_SHIFT) & 3);
        assertEquals(Protocol.FLAG_EATING | (2 << 20), flags);
        for (int used : new int[] {Protocol.FLAG_ON_GROUND, Protocol.FLAG_SWIM, Protocol.FLAG_WATER_MOVING, Protocol.FLAG_JETPACK_THRUST, 0xF << Protocol.HURT_SHIFT}) {
            assertEquals(0, used & flags, "the bits do not overlap");
        }
        assertEquals(Protocol.FLAG_EATING, EatKinds.flags(Protocol.EAT_BURGER));
    }
}
