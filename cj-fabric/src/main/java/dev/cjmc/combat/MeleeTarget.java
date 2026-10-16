package dev.cjmc.combat;

/** Picks what a melee blow hits: the candidate (a sphere) closest to where the player looks, inside the weapon's reach and cone. */
public final class MeleeTarget {
    private MeleeTarget() {}

    /**
     * @return the index of the best candidate, or -1. A candidate counts when its surface is within `reach` of the eye, it is not behind
     *         the look direction and the angle between the look direction and its centre, less the angle its radius covers, is within
     *         the cone. The smallest such angle wins; a tie goes to the nearer one.
     */
    public static int pick(double[] eye, double[] look, double reach, double coneHalfAngleDegrees, double[][] centers, double[] radii) {
        double ll = Math.sqrt(look[0] * look[0] + look[1] * look[1] + look[2] * look[2]);
        if (!(ll > 1e-9) || !Double.isFinite(ll) || !(reach > 0) || !Double.isFinite(reach) || !(coneHalfAngleDegrees > 0)
                || !Double.isFinite(coneHalfAngleDegrees) || radii.length < centers.length) {
            return -1;
        }
        double cone = Math.toRadians(coneHalfAngleDegrees);
        int best = -1;
        double bestAngle = Double.POSITIVE_INFINITY;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < centers.length; i++) {
            double dx = centers[i][0] - eye[0];
            double dy = centers[i][1] - eye[1];
            double dz = centers[i][2] - eye[2];
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double radius = radii[i];
            if (!Double.isFinite(distance) || !(radius >= 0) || !Double.isFinite(radius) || distance - radius > reach) {
                continue;
            }
            double angle = 0.0; // a centre at the eye is as good as dead centre
            if (distance > 1e-9) {
                double cos = (dx * look[0] + dy * look[1] + dz * look[2]) / (distance * ll);
                if (cos < 0.0) {
                    continue; // behind the player
                }
                angle = Math.acos(Math.min(1.0, cos));
                angle = Math.max(0.0, angle - Math.asin(Math.min(1.0, radius / distance)));
            }
            if (angle > cone) {
                continue;
            }
            if (angle < bestAngle - 1e-9 || (Math.abs(angle - bestAngle) <= 1e-9 && distance < bestDistance)) {
                best = i;
                bestAngle = angle;
                bestDistance = distance;
            }
        }
        return best;
    }
}
