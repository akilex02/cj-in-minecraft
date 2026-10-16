package dev.cjmc.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PelletRayTest {
    @Test
    void theRayLengthIsTheRangeLimitedToTheServersReach() {
        assertEquals(50.0, PelletRay.length(50f));
        assertEquals(PelletRay.MAX_RANGE, PelletRay.length(500f));
        assertEquals(100.0, PelletRay.MAX_RANGE);
    }

    @Test
    void aNonsensicalRangeStillGivesAUsableLength() {
        assertEquals(1.0, PelletRay.length(0f));
        assertEquals(1.0, PelletRay.length(-5f));
        assertEquals(1.0, PelletRay.length(Float.NaN));
    }

    @Test
    void everyPelletOfAShotHasItsOwnSeedAndTheSeedIsReproducible() {
        Set<Long> seeds = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            assertTrue(seeds.add(PelletRay.seed(12345L, i)), "pellet " + i + " repeats a seed");
            assertEquals(PelletRay.seed(12345L, i), PelletRay.seed(12345L, i));
        }
        assertNotEquals(PelletRay.seed(1L, 0), PelletRay.seed(2L, 0));
    }

    @Test
    void theNearestThingWithinReachIsWhatTheShotHits() {
        assertEquals(PelletRay.Hit.BLOCK, PelletRay.classify(10, Double.POSITIVE_INFINITY, 50));
        assertEquals(PelletRay.Hit.ENTITY, PelletRay.classify(Double.POSITIVE_INFINITY, 10, 50));
        assertEquals(PelletRay.Hit.ENTITY, PelletRay.classify(20, 10, 50)); // a mob in front of a wall
        assertEquals(PelletRay.Hit.BLOCK, PelletRay.classify(10, 20, 50));  // a wall in front of a mob
    }

    @Test
    void aTieGoesToTheBlockSoNothingIsHitThroughAWall() {
        assertEquals(PelletRay.Hit.BLOCK, PelletRay.classify(10, 10, 50));
    }

    @Test
    void nothingWithinReachIsAMiss() {
        double inf = Double.POSITIVE_INFINITY;
        assertEquals(PelletRay.Hit.MISS, PelletRay.classify(inf, inf, 50));
        assertEquals(PelletRay.Hit.MISS, PelletRay.classify(60, 70, 50));   // both beyond the range
        assertEquals(PelletRay.Hit.MISS, PelletRay.classify(Double.NaN, Double.NaN, 50));
        assertEquals(PelletRay.Hit.BLOCK, PelletRay.classify(50, inf, 50)); // exactly at the end still counts
    }
}
