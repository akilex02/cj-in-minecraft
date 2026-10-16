package dev.cjmc;

import dev.cjmc.vehicle.Headlights;
import dev.cjmc.vehicle.VehicleSession;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;

/**
 * The light of the headlights (the adapter of dev.cjmc.vehicle.Headlights): invisible light blocks ahead of the car, put in the client's own copy of the
 * world so they light it for the player and for no one else (the server does not know them, nothing is saved, and a new world forgets them). Only air is
 * ever replaced and only our own blocks are ever taken away.
 */
final class HeadlightsClient {
    private static List<Headlights.Light> placed = new ArrayList<>();
    private static ClientWorld placedIn;

    private HeadlightsClient() {}

    /** Brings the light blocks to what the car wants: its headlights on or not, from its place. Called every tick. */
    static void update(ClientWorld world, boolean on, VehicleSession.Pose pose) {
        if (placedIn != world) {
            placed = new ArrayList<>(); // another world: the old blocks went with it
            placedIn = world;
        }
        if (world == null) {
            return;
        }
        placed.removeIf(l -> !world.getBlockState(new BlockPos(l.x(), l.y(), l.z())).isOf(Blocks.LIGHT)); // one the server's word has overwritten is no longer there
        List<Headlights.Light> wanted = on && pose != null ? Headlights.lights(pose.x(), pose.y(), pose.z(), pose.yaw()) : List.of();
        Headlights.Change change = Headlights.change(placed, wanted);
        if (change.remove().isEmpty() && change.add().isEmpty()) {
            return;
        }
        List<Headlights.Light> now = new ArrayList<>(placed);
        for (Headlights.Light l : change.remove()) {
            BlockPos pos = new BlockPos(l.x(), l.y(), l.z());
            if (world.getBlockState(pos).isOf(Blocks.LIGHT)) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
            now.remove(l);
        }
        for (Headlights.Light l : change.add()) {
            BlockPos pos = new BlockPos(l.x(), l.y(), l.z());
            if (world.getBlockState(pos).isAir()) { // a solid block, water or a plant is left alone: no light there
                BlockState light = Blocks.LIGHT.getDefaultState().with(Properties.LEVEL_15, l.level());
                world.setBlockState(pos, light, Block.NOTIFY_LISTENERS);
                now.add(l);
            }
        }
        placed = now;
    }
}
