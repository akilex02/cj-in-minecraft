package dev.cjmc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;

/** The fuel bar of the jetpack (worn) or the car (driving): in the top left corner. Orange, red while it waits for a quarter to be back. */
final class FuelHud {
    private static final int BACKGROUND = 0xFF000000;
    private static final int TRACK = 0xFF3A3A3A;
    private static final int FUEL = 0xFFFF9A1F;
    private static final int WAITING = 0xFFE03030;
    private static final int PADDING = 4; // pixels from the top and left edges

    private FuelHud() {}

    static void draw(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.options.hudHidden || player.getAbilities().creativeMode) {
            return; // creative has no fuel to show
        }
        FuelTank tank = VehicleClient.driving() ? VehicleClient.tank() : CjItems.isWearingJetpack(player) ? JetpackClient.tank() : null;
        if (tank == null) {
            return;
        }
        int x = PADDING;
        int y = PADDING;
        context.fill(x - 1, y - 1, x + FuelBar.WIDTH + 1, y + FuelBar.HEIGHT + 1, BACKGROUND);
        context.fill(x, y, x + FuelBar.WIDTH, y + FuelBar.HEIGHT, TRACK);
        context.fill(x, y, x + FuelBar.fillPixels(tank.level(), FuelBar.WIDTH), y + FuelBar.HEIGHT, tank.locked() ? WAITING : FUEL);
    }
}
