package dev.cjmc.vehicle;

import java.util.ArrayList;
import java.util.List;

/**
 * The light of the headlights of the car in the world of Minecraft, which has no dynamic lights: a few invisible light blocks ahead of the car, moved with it.
 * Pure: where they go and what changes between two rounds; the client puts them in its own copy of the world.
 */
public final class Headlights {
    public static final double[] DISTANCES = {3.5, 7.5, 11.5}; // blocks ahead of the centre of the car
    public static final int[] LEVELS = {14, 13, 12};           // light level of each, the nearest the brightest
    public static final double HEIGHT = 0.9;                   // above the underside of the car

    /** One light block: the block and its light level (1 to 15). */
    public record Light(int x, int y, int z, int level) {}

    /** What to take away and what to put to go from one round of lights to another. */
    public record Change(List<Light> remove, List<Light> add) {}

    private Headlights() {}

    /** The lights for a car whose centre is at (x, z) with its underside at y, facing yaw (Minecraft degrees, 0 faces south); none for a broken place. */
    public static List<Light> lights(double x, double y, double z, double yawDegrees) {
        List<Light> lights = new ArrayList<>();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(yawDegrees)) {
            return lights;
        }
        double[] forward = RunOver.forward(yawDegrees);
        for (int i = 0; i < DISTANCES.length; i++) {
            lights.add(new Light((int) Math.floor(x + forward[0] * DISTANCES[i]), (int) Math.floor(y + HEIGHT), (int) Math.floor(z + forward[1] * DISTANCES[i]), LEVELS[i]));
        }
        return lights;
    }

    /** The lights of `before` that are not in `after` (to take away) and the ones of `after` that are not in `before` (to put). */
    public static Change change(List<Light> before, List<Light> after) {
        List<Light> remove = new ArrayList<>();
        for (Light l : before) {
            if (!after.contains(l)) {
                remove.add(l);
            }
        }
        List<Light> add = new ArrayList<>();
        for (Light l : after) {
            if (!before.contains(l)) {
                add.add(l);
            }
        }
        return new Change(remove, add);
    }
}
