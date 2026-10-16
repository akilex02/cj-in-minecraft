package dev.cjmc;

/**
 * Whether the player is made to sneak while he strikes: GTA's walking pace is the Minecraft sneaking pace. Only for a player who just
 * walks, with GTA answering: the sneak key also dismounts, flies down in creative or spectator, sinks and goes down ladders.
 */
public final class SneakRules {
    private SneakRules() {}

    public static boolean shouldForce(boolean forceSneak, boolean bridgeLive, boolean flying, boolean riding, boolean inFluid, boolean climbing,
                                      boolean spectator) {
        return forceSneak && bridgeLive && !flying && !riding && !inFluid && !climbing && !spectator;
    }
}
