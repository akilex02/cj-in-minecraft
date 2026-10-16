package dev.cjmc.combat;

import java.util.List;

/** What the server made of a shot, for the client to draw: where each pellet ended and what it hit. */
public record ShotOutcome(double[] origin, List<Pellet> pellets) {
    /** kind: what the pellet hit; (x, y, z): where it ended (the end of its range for a miss); block*: the block hit (BLOCK only). */
    public record Pellet(PelletRay.Hit kind, double x, double y, double z, int blockX, int blockY, int blockZ) {}
}
