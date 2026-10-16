package dev.cjmc.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cjmc.combat.BlockHealthModel.Change;
import dev.cjmc.combat.BlockHealthModel.Hit;
import dev.cjmc.combat.BlockHealthModel.Rules;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlockHealthModelTest {
    private static final Rules RULES = new Rules(4.0, 0.25, 60, 0.5);
    private static final long POS = 1L;

    private static BlockHealthModel model() {
        return new BlockHealthModel(RULES);
    }

    @Test
    void damageAccumulatesAndTheBlockBreaksOnTheLastHit() {
        BlockHealthModel m = model();
        // stone: hardness 1.5 -> 6 points of health; 0.5 per hit -> 12 hits
        for (int i = 1; i <= 11; i++) {
            assertFalse(m.hit(POS, 1.5, 0.5, 100).broken(), "hit " + i);
        }
        Hit last = m.hit(POS, 1.5, 0.5, 100);
        assertTrue(last.broken());
        assertEquals(-1, last.stage());
        assertEquals(0, m.tracked());
        assertFalse(m.hit(POS, 1.5, 0.5, 100).broken()); // a new block in the same place starts again
    }

    @Test
    void theCracksGrowInTenStages() {
        BlockHealthModel m = model();
        assertEquals(0, m.hit(POS, 1.5, 0.5, 100).stage());   // 1 hit: 5.5 of 6
        assertEquals(1, m.hit(POS, 1.5, 0.5, 100).stage());   // 2 hits
        for (int i = 3; i <= 5; i++) {
            m.hit(POS, 1.5, 0.5, 100);
        }
        assertEquals(5, m.hit(POS, 1.5, 0.5, 100).stage());   // 6 hits: half the health
        for (int i = 7; i <= 10; i++) {
            m.hit(POS, 1.5, 0.5, 100);
        }
        assertEquals(9, m.hit(POS, 1.5, 0.5, 100).stage());   // 11 hits: the last stage, never 10
    }

    @Test
    void bedrockAndNegativeHardnessAreImmune() {
        BlockHealthModel m = model();
        for (double hardness : new double[] {-1.0, -0.0001, Double.NaN, Double.POSITIVE_INFINITY}) {
            Hit h = m.hit(POS, hardness, 100.0, 100);
            assertTrue(h.immune(), "hardness " + hardness);
            assertFalse(h.broken());
        }
        assertEquals(0, m.tracked());
    }

    @Test
    void softBlocksUseTheMinimumHardness() {
        BlockHealthModel m = model();
        // hardness 0 (a flower, a torch): 0.25 * 4 = 1 point
        assertFalse(m.hit(POS, 0.0, 0.5, 100).broken());
        assertTrue(m.hit(POS, 0.0, 0.5, 100).broken());
        assertTrue(m.hit(2L, 0.0, 1.0, 100).broken()); // a strong hit breaks it at once
    }

    @Test
    void blocksAreTrackedIndependently() {
        BlockHealthModel m = model();
        for (int i = 0; i < 11; i++) {
            m.hit(1L, 1.5, 0.5, 100);
        }
        assertFalse(m.hit(2L, 1.5, 0.5, 100).broken());
        assertTrue(m.hit(1L, 1.5, 0.5, 100).broken());
        assertEquals(1, m.tracked());
    }

    @Test
    void aBlockThatChangesStartsOver() {
        BlockHealthModel m = model();
        for (int i = 0; i < 6; i++) {
            m.hit(POS, 1.5, 0.5, 100); // stone, half way
        }
        // somebody put dirt (hardness 0.5, health 2) where the stone was: it must not inherit the stone's damage
        Hit h = m.hit(POS, 0.5, 0.5, 100);
        assertFalse(h.broken());
        assertEquals(2, h.stage()); // 1.5 of 2 left: 25 % damage
    }

    @Test
    void regenerationWaitsForTheDelayThenHealsAndReportsTheCracks() {
        BlockHealthModel m = model();
        for (int i = 0; i < 6; i++) {
            m.hit(POS, 1.5, 0.5, 100); // 3 of 6 left, stage 5
        }
        assertTrue(m.tick(160).isEmpty()); // 60 ticks after the hit is still waiting
        assertEquals(List.of(new Change(POS, 4)), m.tick(161));
        assertEquals(List.of(new Change(POS, 3)), m.tick(162));
        assertEquals(List.of(new Change(POS, 2)), m.tick(163));
        assertEquals(List.of(new Change(POS, 1)), m.tick(164));
        assertEquals(List.of(new Change(POS, 0)), m.tick(165));
        assertEquals(List.of(new Change(POS, -1)), m.tick(166)); // fully healed: the cracks go and the block is forgotten
        assertEquals(0, m.tracked());
        assertTrue(m.tick(167).isEmpty());
    }

    @Test
    void aNewHitRestartsTheRegenerationClock() {
        BlockHealthModel m = model();
        m.hit(POS, 1.5, 0.5, 100);
        assertTrue(m.tick(150).isEmpty());
        m.hit(POS, 1.5, 0.5, 150);
        assertTrue(m.tick(210).isEmpty());
        assertFalse(m.tick(211).isEmpty());
    }

    @Test
    void withARegenerationRateOfZeroBlocksStayCracked() {
        BlockHealthModel m = new BlockHealthModel(new Rules(4.0, 0.25, 60, 0.0));
        m.hit(POS, 1.5, 0.5, 100);
        assertTrue(m.tick(100000).isEmpty());
        assertEquals(1, m.tracked());
    }

    @Test
    void tickingWithNothingToReportGivesAnEmptyList() {
        assertTrue(model().tick(5).isEmpty());
    }

    @Test
    void weakOrInvalidPowerChangesNothing() {
        BlockHealthModel m = model();
        for (double power : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            Hit h = m.hit(POS, 1.5, power, 100);
            assertFalse(h.broken(), "power " + power);
            assertFalse(h.immune());
        }
        assertEquals(0, m.tracked());
        m.hit(POS, 1.5, 0.5, 100);
        assertEquals(0, m.hit(POS, 1.5, 0.0, 100).stage()); // reports the stage it already has
    }

    @Test
    void clearForgetsEveryBlock() {
        BlockHealthModel m = model();
        m.hit(1L, 1.5, 0.5, 100);
        m.hit(2L, 1.5, 0.5, 100);
        m.clear();
        assertEquals(0, m.tracked());
        assertTrue(m.tick(1000).isEmpty());
    }
}
