package dev.cjmc.vehicle;

/**
 * The correspondence between the GTA world (x east, y north, heading counter-clockwise from north) and the Minecraft world
 * (x east, z south, yaw 0 facing south, increasing clockwise), fixed when the player gets into the car: the car stands at
 * the player's place facing the way the player faces, and from then on a displacement of the car in GTA is the same
 * displacement, rotated to the Minecraft yaw, in Minecraft. Pure: no Minecraft classes.
 */
public final class VehicleFrame {
    private final double mcX;
    private final double mcZ;
    private final double mcYaw;
    private final double gtaX;
    private final double gtaY;
    private final double gtaHeading;

    private VehicleFrame(double mcX, double mcZ, double mcYaw, double gtaX, double gtaY, double gtaHeading) {
        this.mcX = mcX;
        this.mcZ = mcZ;
        this.mcYaw = mcYaw;
        this.gtaX = gtaX;
        this.gtaY = gtaY;
        this.gtaHeading = gtaHeading;
    }

    /** The car is at (gtaX, gtaY) heading gtaHeading (radians) in GTA exactly where the player is, at (mcX, mcZ) with the yaw mcYawDegrees. */
    public static VehicleFrame anchor(double mcX, double mcZ, double mcYawDegrees, double gtaX, double gtaY, double gtaHeading) {
        return new VehicleFrame(mcX, mcZ, mcYawDegrees, gtaX, gtaY, gtaHeading);
    }

    /** Where the car is in Minecraft, {x, z}, when it is at (x, y) in GTA. A broken number gives the anchor. */
    public double[] mcPosition(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return new double[] {mcX, mcZ};
        }
        double dx = x - gtaX;
        double dy = y - gtaY;
        double right = dx * Math.cos(gtaHeading) + dy * Math.sin(gtaHeading);
        double forward = -dx * Math.sin(gtaHeading) + dy * Math.cos(gtaHeading);
        double yaw = Math.toRadians(mcYaw);
        return new double[] {
            mcX + forward * -Math.sin(yaw) + right * -Math.cos(yaw),
            mcZ + forward * Math.cos(yaw) + right * -Math.sin(yaw)
        };
    }

    /** The yaw of the car in Minecraft, in degrees in (-180, 180], when its GTA heading is gtaHeading. A broken number gives the anchor's. */
    public double mcYawDegrees(double heading) {
        if (!Double.isFinite(heading)) {
            return mcYaw;
        }
        double yaw = (mcYaw - Math.toDegrees(heading - gtaHeading)) % 360;
        if (yaw <= -180) {
            yaw += 360;
        } else if (yaw > 180) {
            yaw -= 360;
        }
        return yaw;
    }

    /** A displacement of the Minecraft world (x, z) in the axes of GTA, {dx, dy}: what to add to the car's position there. A broken number gives zero. */
    public double[] gtaCorrection(double mcDeltaX, double mcDeltaZ) {
        if (!Double.isFinite(mcDeltaX) || !Double.isFinite(mcDeltaZ)) {
            return new double[] {0, 0};
        }
        double yaw = Math.toRadians(mcYaw);
        double forward = mcDeltaX * -Math.sin(yaw) + mcDeltaZ * Math.cos(yaw);
        double right = mcDeltaX * -Math.cos(yaw) + mcDeltaZ * -Math.sin(yaw);
        return new double[] {
            right * Math.cos(gtaHeading) - forward * Math.sin(gtaHeading),
            right * Math.sin(gtaHeading) + forward * Math.cos(gtaHeading)
        };
    }
}
