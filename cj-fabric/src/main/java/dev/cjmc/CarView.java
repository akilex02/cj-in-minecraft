package dev.cjmc;

import dev.cjmc.vehicle.VehicleSession;

/**
 * Driving in first person: the camera sits in CJ's head in his seat (the car and CJ are drawn around it, as the entity renderer would in third person), and the
 * view turns as the car turns, as it does in a boat. Pure.
 */
public final class CarView {
    private CarView() {}

    /**
     * Where CJ's head is from the player's spot, in the world's axes: the transform of the car (VehicleRig.transform: its yaw, up to its origin, its pitch and roll),
     * then the seat (shifted in the car, turned by its heading), then the head in the ped's model space (Minecraft's axes, his origin at the seat).
     */
    public static float[] headOffset(VehicleSession.Pose pose, float[] seat, float seatHeadingRad, float[] headModel) {
        float[] m = FirstPersonPlacement.rotY((float) -pose.yaw());
        m = FirstPersonPlacement.mul(m, translate(0f, (float) VehicleSession.ORIGIN_ABOVE_GROUND, 0f));
        m = FirstPersonPlacement.mul(m, FirstPersonPlacement.rotX((float) Math.toDegrees(-pose.pitch())));
        m = FirstPersonPlacement.mul(m, FirstPersonPlacement.rotZ((float) Math.toDegrees(-pose.roll())));
        m = FirstPersonPlacement.mul(m, translate(seat[0], seat[1], seat[2]));
        m = FirstPersonPlacement.mul(m, FirstPersonPlacement.rotY((float) Math.toDegrees(seatHeadingRad)));
        return FirstPersonPlacement.apply(m, headModel);
    }

    /** How far the car turned between two yaws (degrees), the short way round: what the view turns by to keep looking the same way in the car. */
    public static float yawDelta(float before, float after) {
        return ((after - before) % 360f + 540f) % 360f - 180f;
    }

    private static float[] translate(float x, float y, float z) {
        float[] t = new float[16];
        Mat4.identity(t, 0);
        t[12] = x;
        t[13] = y;
        t[14] = z;
        return t;
    }
}
