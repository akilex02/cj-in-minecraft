package dev.cjmc;

import dev.cjmc.vehicle.Terrain;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/** The blocks of the world as the vehicle rules see them: the collision shapes of the columns (water and the like are not ground). */
final class VehicleWorld implements Terrain {
    private static final int SEARCH_DOWN = 48;  // rows below the height asked about that are looked at for ground
    private static final int SEARCH_UP = 3;     // rows above that are looked at for a roof (the car is 1.5 high)
    private static final double EPS = 1e-9;

    private final World world;

    VehicleWorld(World world) {
        this.world = world;
    }

    private VoxelShape shape(int x, int y, int z) {
        if (y < world.getBottomY() || y >= world.getTopY()) {
            return net.minecraft.util.shape.VoxelShapes.empty();
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = world.getBlockState(pos);
        return state.getCollisionShape(world, pos);
    }

    @Override
    public double top(int bx, int bz, double maxY) {
        int start = (int) Math.floor(maxY);
        for (int y = start; y >= start - SEARCH_DOWN; y--) {
            VoxelShape shape = shape(bx, y, bz);
            if (!shape.isEmpty()) {
                double top = y + shape.getMax(Direction.Axis.Y);
                if (top <= maxY) {
                    return top;
                }
            }
        }
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    public double ceiling(int bx, int bz, double fromY) {
        int start = (int) Math.floor(fromY);
        for (int y = start; y <= start + SEARCH_UP; y++) {
            VoxelShape shape = shape(bx, y, bz);
            if (!shape.isEmpty()) {
                double bottom = y + shape.getMin(Direction.Axis.Y);
                if (bottom >= fromY - EPS) {
                    return bottom;
                }
            }
        }
        return Double.POSITIVE_INFINITY;
    }
}
