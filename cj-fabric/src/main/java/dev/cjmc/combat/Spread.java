package dev.cjmc.combat;

import java.util.Random;

/** Spreads a shot: turns an aim direction into a direction inside a cone around it. Pure maths, no Minecraft. */
public final class Spread {
    private Spread() {}

    /**
     * @param dir the aim direction (any length but not zero)
     * @param spread the tangent of the cone's half angle (the `spread` of weapons.json); 0 = no spread
     * @return a unit vector, uniformly distributed over the disc of radius `spread` seen from one unit away along `dir`
     */
    public static double[] perturb(double[] dir, double spread, Random rng) {
        double len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
        if (!Double.isFinite(len) || len < 1e-9) {
            throw new IllegalArgumentException("the direction is not usable");
        }
        if (!(spread >= 0) || !Double.isFinite(spread)) {
            throw new IllegalArgumentException("the spread must be a finite number >= 0");
        }
        double dx = dir[0] / len;
        double dy = dir[1] / len;
        double dz = dir[2] / len;
        // the axis least aligned with d gives a first perpendicular; the cross product gives the second
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        double hx = 0, hy = 0, hz = 0;
        if (ax <= ay && ax <= az) {
            hx = 1;
        } else if (ay <= az) {
            hy = 1;
        } else {
            hz = 1;
        }
        double ux = dy * hz - dz * hy;
        double uy = dz * hx - dx * hz;
        double uz = dx * hy - dy * hx;
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        double vx = dy * uz - dz * uy;
        double vy = dz * ux - dx * uz;
        double vz = dx * uy - dy * ux;
        double r = spread * Math.sqrt(rng.nextDouble());
        double a = 2 * Math.PI * rng.nextDouble();
        double c = Math.cos(a) * r;
        double s = Math.sin(a) * r;
        double px = dx + ux * c + vx * s;
        double py = dy + uy * c + vy * s;
        double pz = dz + uz * c + vz * s;
        double pl = Math.sqrt(px * px + py * py + pz * pz);
        return new double[] {px / pl, py / pl, pz / pl};
    }
}
