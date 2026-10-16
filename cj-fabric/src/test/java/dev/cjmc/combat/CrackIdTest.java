package dev.cjmc.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CrackIdTest {
    private static long pos(int x, int y, int z) {
        return (((long) x & 0x3FFFFFF) << 38) | (((long) z & 0x3FFFFFF) << 12) | ((long) y & 0xFFF);
    }

    @Test
    void everyBlockHasItsOwnIdSoSeveralCracksCanShowAtOnce() {
        Set<Integer> ids = new HashSet<>();
        for (int x = 0; x < 20; x++) {
            for (int y = 60; y < 70; y++) {
                for (int z = 0; z < 20; z++) {
                    assertTrue(ids.add(CrackId.of(pos(x, y, z))), x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void idsAreNegativeSoTheyNeverCollideWithAnEntityId() {
        for (long p : new long[] {0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, pos(-30000000, -64, 30000000)}) {
            assertTrue(CrackId.of(p) < 0, "pos " + p);
        }
    }

    @Test
    void theSamePositionAlwaysGivesTheSameId() {
        assertEquals(CrackId.of(pos(5, 64, -9)), CrackId.of(pos(5, 64, -9)));
    }
}
