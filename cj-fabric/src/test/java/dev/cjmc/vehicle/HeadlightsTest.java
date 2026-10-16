package dev.cjmc.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HeadlightsTest {
    @Test
    void theLightsAreAheadOfTheCarAlongItsWayAtTheHeightOfTheHeadlights() {
        List<Headlights.Light> lights = Headlights.lights(10.5, 64.0, 20.5, 0); // yaw 0 faces +z
        assertEquals(Headlights.DISTANCES.length, lights.size());
        for (int i = 0; i < lights.size(); i++) {
            Headlights.Light l = lights.get(i);
            assertEquals(10, l.x());
            assertEquals(64, l.y()); // 0.9 above the underside: the block that stands on the ground the car is on
            assertEquals((int) Math.floor(20.5 + Headlights.DISTANCES[i]), l.z());
        }
    }

    @Test
    void theLightsTurnWithTheCar() {
        // facing west (yaw 90) is -x
        List<Headlights.Light> lights = Headlights.lights(10.5, 64.0, 20.5, 90);
        for (int i = 0; i < lights.size(); i++) {
            assertEquals((int) Math.floor(10.5 - Headlights.DISTANCES[i]), lights.get(i).x());
            assertEquals(20, lights.get(i).z());
        }
    }

    @Test
    void theNearerTheBrighterAndNeverMoreThanTheLightBlockCanGive() {
        List<Headlights.Light> lights = Headlights.lights(0, 64, 0, 0);
        for (int i = 1; i < lights.size(); i++) {
            assertTrue(lights.get(i).level() < lights.get(i - 1).level());
        }
        for (Headlights.Light l : lights) {
            assertTrue(l.level() >= 1 && l.level() <= 15);
        }
    }

    @Test
    void aBrokenPlaceGivesNoLights() {
        assertTrue(Headlights.lights(Double.NaN, 64, 0, 0).isEmpty());
        assertTrue(Headlights.lights(0, Double.POSITIVE_INFINITY, 0, 0).isEmpty());
        assertTrue(Headlights.lights(0, 64, 0, Double.NaN).isEmpty());
    }

    @Test
    void theDifferenceBetweenTwoRoundsIsWhatToPutAndWhatToTakeAway() {
        List<Headlights.Light> before = Headlights.lights(0.5, 64, 0.5, 0);
        List<Headlights.Light> after = Headlights.lights(0.5, 64, 1.5, 0); // one block ahead
        Headlights.Change change = Headlights.change(before, after);
        assertTrue(change.remove().size() >= 1 && change.add().size() >= 1);
        for (Headlights.Light l : change.remove()) {
            assertTrue(after.stream().noneMatch(a -> a.x() == l.x() && a.y() == l.y() && a.z() == l.z() && a.level() == l.level()));
        }
        // nothing moved: nothing to do
        Headlights.Change same = Headlights.change(before, before);
        assertTrue(same.remove().isEmpty() && same.add().isEmpty());
        // all of them to go
        Headlights.Change off = Headlights.change(before, List.of());
        assertEquals(before.size(), off.remove().size());
        assertTrue(off.add().isEmpty());
    }
}
