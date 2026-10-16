package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MovementRulesTest {
    private static final WeaponCatalog CATALOG = WeaponCatalog.load();

    private static MovementRules.Limits of(String id, boolean aiming) {
        return MovementRules.of(id == null ? null : CATALOG.byId(id), aiming, false);
    }

    private static MovementRules.Limits firing(String id, boolean aiming) {
        return MovementRules.of(id == null ? null : CATALOG.byId(id), aiming, true);
    }

    @Test
    void withoutAWeaponNothingIsLimited() {
        MovementRules.Limits l = of(null, false);
        assertEquals(1.0f, l.speedFactor());
        assertTrue(l.canSprint());
        assertEquals(1.0f, of(null, true).speedFactor()); // aiming with nothing in hand is not a thing
        assertTrue(of(null, true).canSprint());
    }

    @Test
    void theColtKeepsItsSpeedAimingOrNot() {
        for (boolean aiming : new boolean[] {false, true}) {
            assertEquals(1.0f, of("colt45", aiming).speedFactor());
            assertTrue(of("colt45", aiming).canSprint());
        }
    }

    @Test
    void theMicroSmgCannotSprintWhileAiming() {
        assertTrue(of("micro_uzi", false).canSprint());
        assertFalse(of("micro_uzi", true).canSprint());
        assertEquals(1.0f, of("micro_uzi", true).speedFactor());
    }

    @Test
    void riflesAndTheShotgunNeverSprintAndCrawlWhileAiming() {
        for (String id : new String[] {"chromegun", "ak47", "m4"}) {
            assertFalse(of(id, false).canSprint(), id);
            assertEquals(1.0f, of(id, false).speedFactor(), id + " is at full pace when not aiming");
            assertFalse(of(id, true).canSprint(), id);
            assertTrue(of(id, true).speedFactor() < 0.4f, id);
        }
    }

    @Test
    void theDesertEagleSprintsButCrawlsWhileAiming() {
        assertTrue(of("desert_eagle", false).canSprint());
        assertEquals(1.0f, of("desert_eagle", false).speedFactor());
        assertFalse(of("desert_eagle", true).canSprint());
        assertTrue(of("desert_eagle", true).speedFactor() < 0.4f);
    }

    @Test
    void theColtAndTheMicroSmgCanBeFiredOnTheMove() {
        for (String id : new String[] {"colt45", "micro_uzi"}) {
            for (boolean aiming : new boolean[] {false, true}) {
                assertEquals(of(id, aiming), firing(id, aiming), id + " aiming=" + aiming);
            }
        }
    }

    @Test
    void theHeavyWeaponsStandStillWhileFiring() {
        for (String id : new String[] {"desert_eagle", "chromegun", "ak47", "m4", "rocket_launcher"}) {
            for (boolean aiming : new boolean[] {false, true}) {
                MovementRules.Limits l = firing(id, aiming);
                assertEquals(0.0f, l.speedFactor(), id + " aiming=" + aiming);
                assertFalse(l.canSprint(), id);
            }
            // and move again the moment the trigger is let go
            assertTrue(of(id, true).speedFactor() > 0f, id);
        }
    }

    @Test
    void firingWithNothingInHandIsNotALimit() {
        assertEquals(1.0f, firing(null, false).speedFactor());
        assertTrue(firing(null, false).canSprint());
    }

    @Test
    void theMinigunAndTheRocketLauncherCannotJumpInAnyState() {
        // GTA does not let CJ jump with them
        for (String id : new String[] {"minigun", "rocket_launcher"}) {
            for (boolean aiming : new boolean[] {false, true}) {
                assertFalse(of(id, aiming).canJump(), id + " aiming=" + aiming);
                assertFalse(firing(id, aiming).canJump(), id + " firing, aiming=" + aiming);
            }
        }
    }

    @Test
    void everyOtherWeaponAndNothingInHandCanJump() {
        for (String id : new String[] {"colt45", "desert_eagle", "chromegun", "micro_uzi", "ak47", "m4", "fists", "baseball_bat"}) {
            for (boolean aiming : new boolean[] {false, true}) {
                assertTrue(of(id, aiming).canJump(), id);
                assertTrue(firing(id, aiming).canJump(), id + " firing");
            }
        }
        assertTrue(of(null, false).canJump());
        assertTrue(firing(null, true).canJump());
    }

    @Test
    void theFistsAndTheBatWalkSlowedLikeSlownessFourWhileStriking() {
        // the pace of GTA's blows: Slowness IV (-60 % of the speed) while he strikes, with no sneak and no field of view change (the input is scaled)
        for (String id : new String[] {"fists", "baseball_bat"}) {
            for (boolean aiming : new boolean[] {false, true}) {
                MovementRules.Limits l = firing(id, aiming);
                assertEquals(0.4f, l.speedFactor(), 1e-6f, id);
                assertFalse(l.canSprint(), id);
                assertTrue(l.canJump(), id);
                assertFalse(l.forceSneak(), id);
            }
            // not striking: free movement and no forced sneak
            assertEquals(1.0f, of(id, false).speedFactor(), id);
            assertTrue(of(id, false).canSprint(), id);
            assertFalse(of(id, false).forceSneak(), id);
        }
    }

    @Test
    void nothingElseForcesTheSneak() {
        for (String id : new String[] {"colt45", "desert_eagle", "chromegun", "micro_uzi", "ak47", "m4", "minigun", "rocket_launcher"}) {
            for (boolean aiming : new boolean[] {false, true}) {
                assertFalse(of(id, aiming).forceSneak(), id);
                assertFalse(firing(id, aiming).forceSneak(), id);
            }
        }
        assertFalse(of(null, false).forceSneak());
        assertFalse(firing(null, true).forceSneak());
    }

    private static MovementRules.Limits crouched(String id, boolean aiming, boolean firing) {
        return MovementRules.of(id == null ? null : CATALOG.byId(id), aiming, firing, true);
    }

    @Test
    void crouchingWalksAtSlownessThreeAndSneaks() {
        MovementRules.Limits l = crouched(null, false, false);
        assertEquals(CrouchRules.SPEED, l.speedFactor(), 1e-6f);
        assertTrue(l.forceSneak());
        assertTrue(l.crouch());
        assertTrue(l.canJump());
    }

    @Test
    void crouchingWithAGunTakesTheSlowerOfTheTwoPaces() {
        assertEquals(CrouchRules.SPEED, crouched("colt45", true, false).speedFactor(), 1e-6f);  // aiming with the Colt is 1.0
        assertEquals(0.32f, crouched("desert_eagle", true, false).speedFactor(), 1e-6f);        // the Desert Eagle's aim is slower
        assertEquals(0.29f, crouched("ak47", true, false).speedFactor(), 1e-6f);
    }

    @Test
    void crouchingAndFiringAFirearmStandsStill() {
        for (String id : new String[] {"colt45", "micro_uzi", "desert_eagle", "chromegun", "ak47", "m4"}) {
            MovementRules.Limits l = crouched(id, true, true);
            assertEquals(0f, l.speedFactor(), id);
            assertTrue(l.crouch(), id);
        }
    }

    @Test
    void crouchingAndStrikingWithTheFistsKeepsTheSlowerPace() {
        assertEquals(MovementRules.STRIKE_SPEED, crouched("fists", false, true).speedFactor(), 1e-6f);
    }

    @Test
    void theWeaponsThatDoNotFireCrouchedAreNotMovedAsCrouched() { // the client stands him up as he fires them: this very tick he moves as standing
        for (String id : new String[] {"baseball_bat", "minigun", "rocket_launcher"}) {
            MovementRules.Limits l = crouched(id, true, true);
            assertFalse(l.crouch(), id);
            assertFalse(l.forceSneak(), id);
            assertEquals(firing(id, true).speedFactor(), l.speedFactor(), id);
        }
    }

    @Test
    void notCrouchingIsAsBefore() {
        assertEquals(of("colt45", true), MovementRules.of(CATALOG.byId("colt45"), true, false, false));
        assertFalse(of("colt45", true).crouch());
    }
}
