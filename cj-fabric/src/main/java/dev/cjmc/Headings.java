package dev.cjmc;

/** Conversions between GTA ped headings and Minecraft yaws. */
public final class Headings {
    private Headings() {}

    /**
     * GTA heading (radians, counter-clockwise from north) to the Minecraft yaw that faces the same way, in degrees in [-180, 180):
     * yaw 0 faces south, 90 west, -90 east, 180 north.
     */
    public static float mcYawFromGta(float headingRad) {
        double yaw = Math.toDegrees(Math.PI - headingRad) % 360.0;
        if (yaw >= 180.0) {
            yaw -= 360.0;
        } else if (yaw < -180.0) {
            yaw += 360.0;
        }
        return (float) yaw;
    }
}
