package dev.cjmc.vehicle;

/** How the vehicle rules see the Minecraft world: columns of blocks with collision. The client implements it over the real world; the tests over arrays. */
public interface Terrain {
    /** The height of the highest solid surface of the column (bx, bz) that is at or below maxY, or -infinity if there is none. */
    double top(int bx, int bz, double maxY);

    /** The height of the underside of the lowest solid shape of the column that starts at or above fromY, or +infinity if there is none. */
    double ceiling(int bx, int bz, double fromY);
}
