package dev.cjmc.vehicle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Running things over with the car: how much it hurts at a speed, how hard it throws them, whether an entity touches the car, and the pause before the
 * same one is hit again. Pure. The car is the box of VehicleCollision (yaw in Minecraft degrees, 0 faces south).
 */
public final class RunOver {
    public static final double MIN_SPEED = 4.0;       // m/s (about 14 km/h): slower than this the car only pushes
    public static final double DAMAGE_PER_MPS = 1.2;  // health points for each m/s above the minimum
    public static final double MAX_DAMAGE = 40.0;
    public static final double KNOCK_PER_MPS = 0.03;  // blocks per tick of speed given to what is hit, for each m/s
    public static final double MAX_KNOCKBACK = 1.6;
    public static final int COOLDOWN_TICKS = 10;      // the same entity is not hit again before this

    private RunOver() {}

    /** Health points taken by a hit at this speed (m/s, either direction). */
    public static double damage(double speed) {
        double v = Math.abs(speed);
        if (!Double.isFinite(v) || v <= MIN_SPEED) {
            return 0.0;
        }
        return Math.min(MAX_DAMAGE, (v - MIN_SPEED) * DAMAGE_PER_MPS);
    }

    /** How hard the entity is thrown, blocks per tick along the way of the car. */
    public static double knockback(double speed) {
        double v = Math.abs(speed);
        if (!Double.isFinite(v) || v <= MIN_SPEED) {
            return 0.0;
        }
        return Math.min(MAX_KNOCKBACK, (v - MIN_SPEED) * KNOCK_PER_MPS * 2);
    }

    /**
     * Whether the entity (a round footprint of `radius` around (ex, ez), from yMin to yMax) touches the car whose centre is (cx, cz) with its underside at cy.
     */
    public static boolean hits(double yawDegrees, double cx, double cy, double cz, double ex, double ez, double radius, double yMin, double yMax) {
        if (!Double.isFinite(cx) || !Double.isFinite(cy) || !Double.isFinite(cz) || !Double.isFinite(ex) || !Double.isFinite(ez)
                || !Double.isFinite(radius) || !Double.isFinite(yMin) || !Double.isFinite(yMax) || !Double.isFinite(yawDegrees)) {
            return false;
        }
        if (yMax <= cy || yMin >= cy + VehicleCollision.HEIGHT) {
            return false;
        }
        double[] forward = VehicleCollision.forward(yawDegrees);
        double[] right = VehicleCollision.right(yawDegrees);
        double dx = ex - cx;
        double dz = ez - cz;
        double along = dx * forward[0] + dz * forward[1];
        double across = dx * right[0] + dz * right[1];
        return Math.abs(along) < VehicleCollision.LENGTH / 2 + radius && Math.abs(across) < VehicleCollision.WIDTH / 2 + radius;
    }

    /** The way the car faces on the (x, z) plane, a unit vector. */
    public static double[] forward(double yawDegrees) {
        return VehicleCollision.forward(yawDegrees);
    }

    /** When each entity was last hit (a tick count of the world), so a car resting on one does not hit it every tick. */
    public static final class Cooldown {
        private final Map<UUID, Long> last = new HashMap<>();

        /** True, and the time is noted, when the entity may be hit now. */
        public boolean ready(UUID id, long tick) {
            Long before = last.get(id);
            if (before != null && tick - before < COOLDOWN_TICKS) {
                return false;
            }
            last.put(id, tick);
            return true;
        }

        /** Forgets those whose pause is over. */
        public void prune(long tick) {
            last.values().removeIf(t -> tick - t >= COOLDOWN_TICKS);
        }

        public int size() {
            return last.size();
        }
    }
}
