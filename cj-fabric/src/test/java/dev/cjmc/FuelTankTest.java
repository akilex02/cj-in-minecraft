package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FuelTankTest {
    private static final double TICK = 0.05; // a client tick

    private static void run(FuelTank tank, boolean wants, double seconds) {
        for (int i = 0; i < Math.round(seconds / TICK); i++) {
            tank.update(wants, TICK);
        }
    }

    @Test
    void theJetpackBurnsTwelveSecondsOfThrustAndThenIsEmpty() {
        FuelTank tank = FuelTank.jetpack();
        assertEquals(1.0, tank.level(), 1e-9);
        run(tank, true, 11.9);
        assertTrue(tank.level() > 0.0 && !tank.locked());
        run(tank, true, 0.1); // the last two ticks
        assertEquals(0.0, tank.level(), 1e-9);
        assertTrue(tank.locked());
    }

    @Test
    void notThrustingBurnsNothing() { // gliding down with the key let go costs no fuel
        FuelTank tank = FuelTank.jetpack();
        run(tank, true, 6.0);
        double level = tank.level();
        tank.update(false, TICK);
        assertTrue(tank.level() >= level);
    }

    @Test
    void theJetpackRechargesFromEmptyToFullInTwentySeconds() {
        FuelTank tank = FuelTank.jetpack();
        run(tank, true, 12.0);
        run(tank, false, 19.9);
        assertTrue(tank.level() < 1.0);
        run(tank, false, 0.2);
        assertEquals(1.0, tank.level(), 1e-9);
    }

    @Test
    void anEmptyTankGivesNothingUntilAQuarterIsBack() {
        FuelTank tank = FuelTank.jetpack();
        run(tank, true, 12.0);
        assertTrue(tank.locked());
        assertFalse(tank.update(true, TICK)); // holding the key with the tank dry: no thrust
        run(tank, true, 4.5); // 4.5 s of recharging at 5 % a second: not yet 25 %
        assertFalse(tank.update(true, TICK));
        run(tank, true, 0.6);
        assertFalse(tank.locked());
        assertTrue(tank.update(true, TICK)); // and the thrust is back
    }

    @Test
    void whatTheTankSpendsIsReportedPerTick() {
        FuelTank tank = FuelTank.jetpack();
        assertTrue(tank.update(true, TICK));
        assertFalse(tank.update(false, TICK));
    }

    @Test
    void theCarBurnsFortySecondsRechargesInSixtyAndRunsAgainAtAQuarter() {
        FuelTank tank = FuelTank.car();
        run(tank, true, 40.0);
        assertTrue(tank.locked());
        assertEquals(0.0, tank.level(), 1e-9);
        run(tank, false, 14.8); // 25 % of 60 s is 15 s
        assertTrue(tank.locked());
        run(tank, false, 0.4);
        assertFalse(tank.locked());
        run(tank, false, 46.0);
        assertEquals(1.0, tank.level(), 1e-9);
    }

    @Test
    void fillPutsItBackToFullAndUnlocked() {
        FuelTank tank = FuelTank.jetpack();
        run(tank, true, 12.0);
        tank.fill();
        assertEquals(1.0, tank.level(), 1e-9);
        assertFalse(tank.locked());
    }

    @Test
    void theBarFillsInProportionToTheLevel() {
        assertEquals(0, FuelBar.fillPixels(0.0, 100));
        assertEquals(50, FuelBar.fillPixels(0.5, 100));
        assertEquals(100, FuelBar.fillPixels(1.0, 100));
        assertEquals(0, FuelBar.fillPixels(-0.3, 100));
        assertEquals(100, FuelBar.fillPixels(1.7, 100));
    }
}
