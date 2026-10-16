package dev.cjmc.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cjmc.Protocol;
import org.junit.jupiter.api.Test;

class VehicleSessionTest {
    private static final double GROUND = 64;
    private static final double HEADING = Math.PI; // GTA's heading of a car that faces Minecraft's yaw 0 (south)
    private static final VehicleSession.Conditions OK = new VehicleSession.Conditions(true, false, false, false, false, false, false, false, true);
    private static final VehicleSession.Keys NONE = new VehicleSession.Keys(false, false, false, false, false, false);
    private static final VehicleSession.Keys ENTER = new VehicleSession.Keys(true, false, false, false, false, false);

    private static VehicleSession.Keys keys(boolean forward, boolean back, boolean left, boolean right, boolean handbrake) {
        return new VehicleSession.Keys(false, forward, back, left, right, handbrake);
    }

    private static Protocol.VehicleState state(int flags, double py, double heading, double speed, double steer) {
        return new Protocol.VehicleState(1, 1, flags, 492, 1500f, (float) py, 13f, (float) heading, 0f, 0f, (float) speed, (float) steer, -0.463f, 0.14f, -0.095f, 0.25f);
    }

    private static Protocol.VehicleState sideways(float dx) {
        return new Protocol.VehicleState(1, 1, Protocol.VEHICLE_FLAG_DRIVER_IN, 492, 1500f + dx, -2500f, 13f, (float) HEADING, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
    }

    private static Protocol.VehicleState inCar(double py) {
        return state(Protocol.VEHICLE_FLAG_DRIVER_IN, py, HEADING, 0, 0);
    }

    private static VehicleSession.Conditions with(boolean live, boolean riding, boolean water, boolean jetpack, boolean climbing, boolean knocked, boolean dead,
                                                  boolean screen, boolean connected) {
        return new VehicleSession.Conditions(live, riding, water, jetpack, climbing, knocked, dead, screen, connected);
    }

    private static VehicleSession.Output tick(VehicleSession s, long now, VehicleSession.Conditions c, VehicleSession.Keys k, Terrain world, Protocol.VehicleState st, long received) {
        return s.tick(now, c, k, 10.5, GROUND, 20.5, 0, world, st, received);
    }

    /** A session that is driving: entered at t = 1000, the car answered at t = 1100, standing at (10.5, 64, 20.5) facing yaw 0. */
    private static VehicleSession ride(Terrain world) {
        VehicleSession s = new VehicleSession();
        tick(s, 1000, OK, ENTER, world, null, 0);
        tick(s, 1100, OK, NONE, world, inCar(-2500), 1100);
        assertEquals(VehicleSession.Mode.DRIVING, s.mode());
        return s;
    }

    @Test
    void withoutTheKeyNothingHappens() {
        VehicleSession s = new VehicleSession();
        VehicleSession.Output out = tick(s, 0, OK, NONE, new FakeTerrain(GROUND), null, 0);
        assertEquals(VehicleSession.NONE, out.command());
        assertFalse(out.input());
        assertEquals(VehicleSession.Mode.IDLE, s.mode());
        assertFalse(s.riding());
    }

    @Test
    void theKeyAsksForTheCarWhereThePlayerStandsFacingHisWay() {
        VehicleSession s = new VehicleSession();
        VehicleSession.Output out = s.tick(500, OK, ENTER, 10.5, GROUND, 20.5, 90, new FakeTerrain(GROUND), null, 0);
        assertEquals(VehicleSession.ENTER, out.command());
        assertEquals(90f, out.yaw());
        assertEquals(VehicleSession.Mode.ENTERING, s.mode());
        assertFalse(s.riding());
        assertEquals(10.5, s.pose().x());
        assertEquals(GROUND, s.pose().y());
        assertEquals(20.5, s.pose().z());
        assertEquals(90.0, s.pose().yaw());
        assertEquals(492, VehicleSession.MODEL);
    }

    @Test
    void eachConditionThatForbidsGettingInStopsIt() {
        FakeTerrain world = new FakeTerrain(GROUND);
        VehicleSession.Conditions[] bad = {
            with(false, false, false, false, false, false, false, false, true), with(true, true, false, false, false, false, false, false, true),
            with(true, false, true, false, false, false, false, false, true), with(true, false, false, true, false, false, false, false, true),
            with(true, false, false, false, true, false, false, false, true), with(true, false, false, false, false, true, false, false, true),
            with(true, false, false, false, false, false, true, false, true), with(true, false, false, false, false, false, false, true, true)};
        for (VehicleSession.Conditions c : bad) {
            VehicleSession s = new VehicleSession();
            assertEquals(VehicleSession.NONE, tick(s, 0, c, ENTER, world, null, 0).command(), c.toString());
            assertEquals(VehicleSession.Mode.IDLE, s.mode());
        }
    }

    @Test
    void ifTheCarDoesNotFitThereHeDoesNotGetIn() {
        FakeTerrain wall = new FakeTerrain(GROUND).solid(10, 10, 20, 20, GROUND, GROUND + 3); // a block where he stands
        VehicleSession s = new VehicleSession();
        assertEquals(VehicleSession.NONE, tick(s, 0, OK, ENTER, wall, null, 0).command());
        assertEquals(VehicleSession.Mode.IDLE, s.mode());
        FakeTerrain narrow = new FakeTerrain(GROUND).solid(9, 9, 20, 20, GROUND, GROUND + 3); // a block beside him, inside the car's width
        assertEquals(VehicleSession.NONE, tick(new VehicleSession(), 0, OK, ENTER, narrow, null, 0).command());
    }

    @Test
    void theRequestIsRepeatedUntilTheBridgeAnswersAndThenGivenUp() {
        FakeTerrain world = new FakeTerrain(GROUND);
        VehicleSession s = new VehicleSession();
        s.tick(1000, OK, ENTER, 10.5, GROUND, 20.5, 90, world, null, 0);
        assertEquals(VehicleSession.NONE, tick(s, 1100, OK, NONE, world, null, 0).command());
        assertEquals(VehicleSession.NONE, tick(s, 1249, OK, NONE, world, null, 0).command());
        VehicleSession.Output again = tick(s, 1250, OK, NONE, world, null, 0);
        assertEquals(VehicleSession.ENTER, again.command());
        assertEquals(90f, again.yaw()); // the way he faced when he asked, not the way he faces now
        assertEquals(VehicleSession.NONE, tick(s, 1400, OK, NONE, world, null, 0).command());
        assertEquals(VehicleSession.ENTER, tick(s, 1500, OK, NONE, world, null, 0).command());
        assertEquals(VehicleSession.Mode.ENTERING, s.mode());
        tick(s, 3000, OK, NONE, world, null, 0);
        assertEquals(VehicleSession.Mode.ENTERING, s.mode()); // still waiting at exactly 2000 ms
        VehicleSession.Output failed = tick(s, 3001, OK, NONE, world, null, 0);
        assertEquals(VehicleSession.EXIT, failed.command()); // if the car comes late it must not stay
        assertTrue(failed.enterFailed());
        assertEquals(VehicleSession.Mode.IDLE, s.mode());
    }

    @Test
    void aStateWithoutTheDriverOrFromBeforeTheRequestDoesNotStartTheRide() {
        FakeTerrain world = new FakeTerrain(GROUND);
        VehicleSession s = new VehicleSession();
        tick(s, 1000, OK, ENTER, world, null, 0);
        tick(s, 1050, OK, NONE, world, state(0, -2500, HEADING, 0, 0), 1050);
        assertEquals(VehicleSession.Mode.ENTERING, s.mode());
        tick(s, 1060, OK, NONE, world, inCar(-2500), 999); // a state of an earlier ride
        assertEquals(VehicleSession.Mode.ENTERING, s.mode());
        tick(s, 1070, OK, NONE, world, inCar(-2500), 1000); // received at the very moment of the request: it counts
        assertEquals(VehicleSession.Mode.DRIVING, s.mode());
    }

    @Test
    void theCarIsWhereThePlayerStoodWhenHeAskedNotWhereHeIsWhenItAnswers() {
        FakeTerrain world = new FakeTerrain(GROUND);
        VehicleSession s = new VehicleSession();
        tick(s, 1000, OK, ENTER, world, null, 0);
        s.tick(1100, OK, NONE, 14.5, GROUND, 25.5, 45, world, inCar(-2500), 1100); // he has moved and turned meanwhile
        assertEquals(VehicleSession.Mode.DRIVING, s.mode());
        tick(s, 1150, OK, NONE, world, inCar(-2501), 1140);
        assertEquals(10.5, s.pose().x(), 1e-6);
        assertEquals(21.5, s.pose().z(), 1e-6);
        assertEquals(0.0, s.pose().yaw(), 1e-6);
    }

    @Test
    void whenTheCarAnswersTheRideStartsWhereThePlayerWas() {
        VehicleSession s = ride(new FakeTerrain(GROUND));
        assertTrue(s.riding());
        assertEquals(10.5, s.pose().x(), 1e-9);
        assertEquals(20.5, s.pose().z(), 1e-9);
        assertEquals(GROUND, s.pose().y(), 1e-9);
        assertEquals(0, s.pose().yaw(), 1e-9);
    }

    @Test
    void thePedalsAndTheHandbrakeAreWhatTheKeysSay() {
        FakeTerrain world = new FakeTerrain(GROUND);
        VehicleSession s = ride(world);
        VehicleSession.Output idle = tick(s, 1200, OK, NONE, world, inCar(-2500), 1100);
        assertTrue(idle.input());
        assertEquals(0, idle.throttle());
        assertEquals(0, idle.brake());
        assertEquals(0, idle.steer());
        assertEquals(0, idle.handbrake());
        VehicleSession.Output gas = tick(s, 1250, OK, keys(true, false, false, false, false), world, inCar(-2500), 1100);
        assertEquals(255, gas.throttle());
        assertEquals(0, gas.brake());
        VehicleSession.Output brake = tick(s, 1300, OK, keys(false, true, false, false, true), world, inCar(-2500), 1100);
        assertEquals(0, brake.throttle());
        assertEquals(255, brake.brake());
        assertEquals(255, brake.handbrake());
    }

    @Test
    void theWheelTurnsGraduallyAndComesBack() {
        FakeTerrain world = new FakeTerrain(GROUND);
        VehicleSession s = ride(world);
        int[] right = new int[3];
        for (int i = 0; i < 3; i++) {
            right[i] = tick(s, 1200 + 50 * i, OK, keys(false, false, false, true, false), world, inCar(-2500), 1100).steer();
        }
        assertEquals(25, right[0]); // 0.2 of the way each tick: 4 per second
        assertEquals(51, right[1]);
        assertEquals(76, right[2]);
        assertTrue(tick(s, 1400, OK, keys(false, false, true, false, false), world, inCar(-2500), 1100).steer() < 76 - 30); // going the other way, back faster
        VehicleSession both = ride(world);
        assertEquals(0, tick(both, 1200, OK, keys(false, false, true, true, false), world, inCar(-2500), 1100).steer());
        VehicleSession left = ride(world);
        assertEquals(-25, tick(left, 1200, OK, keys(false, false, true, false, false), world, inCar(-2500), 1100).steer());
    }

    @Test
    void theCarMovesWithWhatGtaSaysAndOnlyOncePerState() {
        FakeTerrain world = new FakeTerrain(GROUND);
        VehicleSession s = ride(world);
        tick(s, 1150, OK, NONE, world, inCar(-2510), 1140); // 10 metres ahead (GTA's heading is south)
        assertEquals(30.5, s.pose().z(), 1e-6);
        assertEquals(10.5, s.pose().x(), 1e-6);
        VehicleSession.Output again = tick(s, 1200, OK, NONE, world, inCar(-2510), 1140); // the same state again
        assertEquals(30.5, s.pose().z(), 1e-6);
        assertTrue(again.input()); // the controls still go
        VehicleSession.Output free = tick(s, 1250, OK, NONE, world, inCar(-2515), 1240);
        assertEquals(35.5, s.pose().z(), 1e-6);
        assertEquals(30.5, s.previousPose().z(), 1e-6); // the pose of the tick before, to draw between the two
        assertFalse(free.correction()); // nothing stopped it: nothing to correct
        assertEquals(0.0, free.corrX());
        assertEquals(0.0, free.corrY());
    }

    @Test
    void theYawFollowsTheTurnOfTheCar() {
        FakeTerrain world = new FakeTerrain(GROUND);
        VehicleSession s = ride(world);
        tick(s, 1150, OK, NONE, world, state(Protocol.VEHICLE_FLAG_DRIVER_IN, -2500, HEADING - Math.PI / 2, 0, 0), 1140); // a right turn of 90 degrees
        assertEquals(90, s.pose().yaw(), 1e-4);
    }

    @Test
    void theWheelsSpinWithTheSpeedAndTheFrontOnesFollowTheSteering() {
        FakeTerrain world = new FakeTerrain(GROUND);
        VehicleSession s = ride(world);
        tick(s, 1150, OK, NONE, world, state(Protocol.VEHICLE_FLAG_DRIVER_IN, -2500, HEADING, 7, -0.3), 1140);
        assertEquals(1.0, s.pose().wheelAngle(), 1e-6); // 7 m/s for a tick is 0.35 m: one radian
        assertEquals(0.3, s.pose().frontWheelAngle(), 1e-6); // GTA's negative is to the right; the wheel is positive to the right
        tick(s, 1200, OK, NONE, world, state(Protocol.VEHICLE_FLAG_DRIVER_IN, -2500, HEADING, 7, -0.3), 1190);
        assertEquals(2.0, s.pose().wheelAngle(), 1e-6);
    }

    @Test
    void theSeatOfCjComesFromTheState() {
        FakeTerrain world = new FakeTerrain(GROUND);
        VehicleSession s = ride(world);
        tick(s, 1150, OK, NONE, world, inCar(-2500), 1140);
        assertEquals(-0.463, s.pose().seatRight(), 1e-6);
        assertEquals(0.14, s.pose().seatForward(), 1e-6);
        assertEquals(-0.095, s.pose().seatUp(), 1e-6);
        assertEquals(0.25, s.pose().seatHeading(), 1e-6);
    }

    @Test
    void aWallStopsTheCarAndTheBridgeIsToldHowFarBackItIs() {
        FakeTerrain wall = new FakeTerrain(GROUND).solid(-30, 30, 30, 30, GROUND, GROUND + 3);
        VehicleSession s = ride(wall);
        VehicleSession.Output out = tick(s, 1150, OK, NONE, wall, inCar(-2515), 1140); // GTA drove 15 m: z would be 35.5
        assertEquals(27.55, s.pose().z(), 0.01);
        assertTrue(out.correction());
        assertEquals(0.0, out.corrX(), 1e-4);
        assertEquals(7.95, out.corrY(), 0.01); // GTA's y went down by 15 and has to come back up by the part the wall took
        VehicleSession.Output next = tick(s, 1200, OK, NONE, wall, inCar(-2515), 1140);
        assertFalse(next.correction()); // told once
        assertEquals(0.0, next.corrX());
        assertEquals(0.0, next.corrY());
    }

    @Test
    void aSideWallIsCorrectedSideways() {
        FakeTerrain wall = new FakeTerrain(GROUND).solid(8, 8, -50, 50, GROUND, GROUND + 3);
        VehicleSession s = ride(wall);
        // the car faces south in both worlds, so its right hand is west in Minecraft and GTA's -x: GTA drove 3 m to the right, toward the wall at x = 8..9
        VehicleSession.Output out = tick(s, 1150, OK, NONE, wall, sideways(-3f), 1140);
        assertTrue(out.correction());
        assertEquals(9.95, s.pose().x(), 0.01); // the left edge of the car (0.95 from its centre) touches the face of the wall
        assertEquals(2.45, out.corrX(), 0.01);  // and GTA's car is brought back east by what the wall took
        assertEquals(0.0, out.corrY(), 1e-3);
    }

    @Test
    void aLowStepIsClimbedAndAnEdgeMakesTheCarFall() {
        FakeTerrain step = new FakeTerrain(GROUND).solid(-30, 30, 25, 40, GROUND, GROUND + 0.5);
        VehicleSession s = ride(step);
        tick(s, 1150, OK, NONE, step, inCar(-2510), 1140);
        assertEquals(GROUND + 0.5, s.pose().y(), 1e-9);
        FakeTerrain pit = new FakeTerrain(GROUND).hole(-30, 30, 22, 80);
        VehicleSession f = ride(pit);
        tick(f, 1150, OK, NONE, pit, inCar(-2510), 1140); // over the edge: no ground under it
        double y1 = f.pose().y();
        assertTrue(y1 < GROUND);
        tick(f, 1200, OK, NONE, pit, inCar(-2510), 1140); // the same state: no new move, so no new fall either
        assertEquals(y1, f.pose().y(), 1e-12);
        tick(f, 1250, OK, NONE, pit, inCar(-2512), 1240);
        double drop1 = GROUND - y1;
        double drop2 = y1 - f.pose().y();
        assertTrue(drop2 > drop1, "it keeps falling, faster: " + drop1 + " then " + drop2);
    }

    @Test
    void theCarTiltsOnARampOfBlocks() {
        FakeTerrain ramp = new FakeTerrain(GROUND).solid(-30, 30, 22, 40, GROUND, GROUND + 0.5);
        VehicleSession s = ride(ramp);
        tick(s, 1150, OK, NONE, ramp, inCar(-2502.5), 1140); // the box is over the slab, the front wheels are on it and the rear ones are not
        assertTrue(s.pose().pitch() > 0, "nose up");
        assertEquals(0.0, s.pose().roll(), 1e-9);
    }

    @Test
    void aTurnThatWouldPutTheCarInsideAWallDoesNotStopItMovingButTheCarIsDrawnTurned() {
        FakeTerrain wall = new FakeTerrain(GROUND).solid(8, 8, -50, 50, GROUND, GROUND + 3);
        VehicleSession s = ride(wall);
        // a right turn of 90 degrees: facing west the 4.9 m of the car would reach into the wall at x = 8..9, so the old direction serves for the collision
        tick(s, 1150, OK, NONE, wall, state(Protocol.VEHICLE_FLAG_DRIVER_IN, -2505, HEADING - Math.PI / 2, 0, 0), 1140);
        assertEquals(25.5, s.pose().z(), 1e-6);
        assertEquals(10.5, s.pose().x(), 1e-6);
        assertEquals(90, s.pose().yaw(), 1e-4);
    }

    @Test
    void afterATurnThatDidNotFitTheCarIsNotPinnedButKeepsMovingWithTheDirectionThatFitted() {
        FakeTerrain wall = new FakeTerrain(GROUND).solid(8, 8, -50, 50, GROUND, GROUND + 3);
        VehicleSession s = ride(wall);
        double turned = HEADING - Math.PI / 2; // facing west the car would reach into the wall
        tick(s, 1150, OK, NONE, wall, state(Protocol.VEHICLE_FLAG_DRIVER_IN, -2505, turned, 0, 0), 1140);
        assertEquals(25.5, s.pose().z(), 1e-6);
        // GTA goes on 1 m along the old direction: the box (still the one that fitted) moves, nothing pushes back
        VehicleSession.Output next = tick(s, 1200, OK, NONE, wall, state(Protocol.VEHICLE_FLAG_DRIVER_IN, -2506, turned, 0, 0), 1190);
        assertEquals(26.5, s.pose().z(), 1e-6);
        assertEquals(10.5, s.pose().x(), 1e-6);
        VehicleSession.Output after = tick(s, 1250, OK, NONE, wall, state(Protocol.VEHICLE_FLAG_DRIVER_IN, -2507, turned, 0, 0), 1240);
        assertFalse(next.correction() || after.correction());
        assertEquals(27.5, s.pose().z(), 1e-6);
    }

    @Test
    void theTiltIsMeasuredWithTheDirectionTheCarFacesNow() {
        FakeTerrain edge = new FakeTerrain(GROUND).solid(-30, 30, 23, 40, GROUND, GROUND + 0.5);
        VehicleSession s = ride(edge);
        // turned 90 degrees to face west, the line of its wheels from front to back is along x: both axles are on the same side of the edge at z = 23, and the two sides are not
        tick(s, 1150, OK, NONE, edge, state(Protocol.VEHICLE_FLAG_DRIVER_IN, -2502.5, HEADING - Math.PI / 2, 0, 0), 1140);
        assertEquals(0.0, s.pose().pitch(), 1e-9);
        assertTrue(Math.abs(s.pose().roll()) > 0.01);
    }

    @Test
    void afterAJumpTheYawContinuesFromTheNewDirectionOfTheCar() {
        FakeTerrain world = new FakeTerrain(GROUND);
        VehicleSession s = ride(world);
        tick(s, 1150, OK, NONE, world, state(Protocol.VEHICLE_FLAG_DRIVER_IN, -2600, HEADING - Math.PI / 2, 0, 0), 1140); // GTA moved it and turned it
        assertEquals(90, s.pose().yaw(), 1e-4);
        tick(s, 1200, OK, NONE, world, state(Protocol.VEHICLE_FLAG_DRIVER_IN, -2601, HEADING - Math.PI / 2, 0, 0), 1190); // and it goes on that way
        assertEquals(90, s.pose().yaw(), 1e-4);
    }

    @Test
    void aJumpOfTheCarLargerThanThirtyBlocksIsGtaMovingItAndTheFrameStartsAgainThere() {
        FakeTerrain world = new FakeTerrain(GROUND);
        VehicleSession s = ride(world);
        tick(s, 1150, OK, NONE, world, inCar(-2600), 1140); // 100 m in a tick
        assertEquals(20.5, s.pose().z(), 1e-6);
        tick(s, 1200, OK, NONE, world, inCar(-2601), 1190); // and from there on, a metre ahead is a metre ahead
        assertEquals(21.5, s.pose().z(), 1e-6);
        tick(s, 1250, OK, NONE, world, inCar(-2601 - 29.9), 1240);
        assertEquals(51.4, s.pose().z(), 1e-3); // 29.9 is no jump
    }

    @Test
    void theKeyAgainGetsHimOutAndTheRideEndsAfterTheRepeats() {
        FakeTerrain world = new FakeTerrain(GROUND);
        VehicleSession s = ride(world);
        VehicleSession.Output out = tick(s, 2000, OK, ENTER, world, inCar(-2500), 1100);
        assertEquals(VehicleSession.EXIT, out.command());
        assertEquals(VehicleSession.Mode.LEAVING, s.mode());
        assertTrue(s.riding());
        assertFalse(out.input());
        assertEquals(VehicleSession.EXIT, tick(s, 2050, OK, NONE, world, null, 0).command());
        assertEquals(VehicleSession.EXIT, tick(s, 2149, OK, NONE, world, null, 0).command());
        assertTrue(s.riding());
        VehicleSession.Output done = tick(s, 2150, OK, NONE, world, null, 0);
        assertEquals(VehicleSession.NONE, done.command());
        assertEquals(VehicleSession.Mode.IDLE, s.mode());
        assertFalse(s.riding());
    }

    @Test
    void youGetOutWhenYouMust() {
        FakeTerrain world = new FakeTerrain(GROUND);
        VehicleSession dead = ride(world);
        assertEquals(VehicleSession.EXIT, tick(dead, 1200, with(true, false, false, false, false, false, true, false, true), NONE, world, inCar(-2500), 1190).command());
        VehicleSession gone = ride(world);
        assertEquals(VehicleSession.EXIT, tick(gone, 1200, with(false, false, false, false, false, false, false, false, true), NONE, world, inCar(-2500), 1190).command());
        VehicleSession offline = ride(world);
        assertEquals(VehicleSession.EXIT, tick(offline, 1200, with(true, false, false, false, false, false, false, false, false), NONE, world, inCar(-2500), 1190).command());
    }

    @Test
    void withoutStatesForASecondTheRideEnds() {
        FakeTerrain world = new FakeTerrain(GROUND);
        VehicleSession s = ride(world);
        assertEquals(VehicleSession.NONE, tick(s, 2100, OK, NONE, world, inCar(-2500), 1100).command()); // exactly a second old
        assertEquals(VehicleSession.Mode.DRIVING, s.mode());
        assertEquals(VehicleSession.EXIT, tick(s, 2101, OK, NONE, world, inCar(-2500), 1100).command());
        assertEquals(VehicleSession.Mode.LEAVING, s.mode());
        VehicleSession none = ride(world);
        assertEquals(VehicleSession.EXIT, tick(none, 1200, OK, NONE, world, null, 0).command()); // no state at all
        VehicleSession nothing = ride(world);
        assertEquals(VehicleSession.EXIT, tick(nothing, 1200, OK, NONE, world, null, 1190).command()); // a time but no state
    }

    @Test
    void cancelGivesTheRideUpAndSaysWhetherTheBridgeMustBeTold() {
        FakeTerrain world = new FakeTerrain(GROUND);
        VehicleSession s = ride(world);
        assertTrue(s.cancel());
        assertEquals(VehicleSession.Mode.IDLE, s.mode());
        assertFalse(s.riding());
        assertFalse(s.cancel());
        VehicleSession entering = new VehicleSession();
        tick(entering, 0, OK, ENTER, world, null, 0);
        assertTrue(entering.cancel());
    }

    @Test
    void aNewRideDoesNotInheritTheFallOfTheLastOne() {
        FakeTerrain pit = new FakeTerrain(GROUND).hole(-30, 30, 22, 80);
        VehicleSession s = ride(pit);
        for (int i = 0; i < 4; i++) {
            tick(s, 1150 + 50 * i, OK, NONE, pit, inCar(-2510 - 2 * i), 1140 + 50 * i); // falling, faster and faster
        }
        tick(s, 2000, OK, ENTER, pit, inCar(-2518), 1340);
        tick(s, 2200, OK, NONE, pit, null, 0);
        FakeTerrain flat = new FakeTerrain(GROUND);
        s.tick(3000, OK, ENTER, 10.5, GROUND + 5, 20.5, 0, flat, null, 0); // he asks in mid-air above flat ground
        s.tick(3100, OK, NONE, 10.5, GROUND + 5, 20.5, 0, flat, inCar(-2500), 3050);
        s.tick(3150, OK, NONE, 10.5, GROUND + 5, 20.5, 0, flat, inCar(-2501), 3140);
        assertEquals(GROUND + 5 - 0.0784, s.pose().y(), 1e-9); // the first tick of a fall from rest, not of the one before
    }

    @Test
    void afterARideANewOneStartsClean() {
        FakeTerrain world = new FakeTerrain(GROUND);
        VehicleSession s = ride(world);
        tick(s, 1150, OK, NONE, world, inCar(-2510), 1140);
        for (int i = 0; i < 4; i++) {
            tick(s, 1160 + 10 * i, OK, keys(false, false, false, true, false), world, inCar(-2510), 1140); // the wheel is turned to the right
        }
        tick(s, 2000, OK, ENTER, world, inCar(-2510), 1140);
        tick(s, 2200, OK, NONE, world, null, 0);
        assertEquals(VehicleSession.Mode.IDLE, s.mode());
        tick(s, 3000, OK, ENTER, world, inCar(-2510), 1140); // the old state is still the latest the adapter has
        assertEquals(VehicleSession.Mode.ENTERING, s.mode());
        tick(s, 3100, OK, NONE, world, inCar(-2510), 1140); // received before this request: not this ride's
        assertEquals(VehicleSession.Mode.ENTERING, s.mode());
        tick(s, 3200, OK, NONE, world, inCar(-2400), 3150);
        assertEquals(VehicleSession.Mode.DRIVING, s.mode());
        assertEquals(20.5, s.pose().z(), 1e-6);
        assertEquals(0.0, tick(s, 3250, OK, NONE, world, inCar(-2400), 3150).steer() * 1.0);
    }

    @Test
    void thePoseBetweenTwoTicksIsTheirBlendTheYawTakingTheShortWay() {
        VehicleSession.Pose a = new VehicleSession.Pose(0, 64, 0, 170, 0.1, -0.2, 1.0, 0.2, 0, 0, 0, 0);
        VehicleSession.Pose b = new VehicleSession.Pose(10, 66, -20, -170, 0.3, 0.2, 2.0, 0.4, 0, 0, 0, 0);
        VehicleSession.Pose half = VehicleSession.blend(a, b, 0.5);
        assertEquals(5, half.x(), 1e-9);
        assertEquals(65, half.y(), 1e-9);
        assertEquals(-10, half.z(), 1e-9);
        assertEquals(180, half.yaw(), 1e-9); // 170 and -170 are 20 degrees apart through 180, not 340 through 0; the range includes +180, not -180
        VehicleSession.Pose back = new VehicleSession.Pose(0, 0, 0, -170, 0, 0, 0, 0, 0, 0, 0, 0);
        VehicleSession.Pose front = new VehicleSession.Pose(0, 0, 0, 170, 0, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(180, VehicleSession.blend(back, front, 0.5).yaw(), 1e-9); // the same way round
        assertEquals(0.2, half.pitch(), 1e-9);
        assertEquals(0.0, half.roll(), 1e-9);
        assertEquals(1.5, half.wheelAngle(), 1e-9);
        assertEquals(0.3, half.frontWheelAngle(), 1e-9);
        assertEquals(a.x(), VehicleSession.blend(a, b, 0.0).x(), 1e-9);
        assertEquals(b.x(), VehicleSession.blend(a, b, 1.0).x(), 1e-9);
        assertEquals(175, VehicleSession.blend(a, b, 0.25).yaw(), 1e-9);
        assertEquals(-175, VehicleSession.blend(a, b, 0.75).yaw(), 1e-9);
        VehicleSession.Pose c = new VehicleSession.Pose(0, 0, 0, 10, 0, 0, 3.0, 0, 0.1, 0.2, 0.3, 0.4);
        VehicleSession.Pose d = new VehicleSession.Pose(0, 0, 0, 30, 0, 0, -3.0, 0, 0.5, 0.6, 0.7, 0.8);
        VehicleSession.Pose mid = VehicleSession.blend(c, d, 0.5);
        assertEquals(20, mid.yaw(), 1e-9);
        assertEquals(0.5, mid.seatRight(), 1e-9); // CJ's seat is not blended: it is the latest
        assertEquals(0.6, mid.seatForward(), 1e-9);
        assertEquals(0.7, mid.seatUp(), 1e-9);
        assertEquals(0.8, mid.seatHeading(), 1e-9);
        assertEquals(Math.PI, Math.abs(mid.wheelAngle()), 1e-9); // 3 and -3 radians are 0.28 apart through pi, not 6 through 0
    }
}
