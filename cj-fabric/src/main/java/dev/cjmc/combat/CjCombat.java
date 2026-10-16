package dev.cjmc.combat;

import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;

/** The shared state of the combat: the weapons, the health of the shot blocks, and the queue of results for the client. */
public final class CjCombat {
    /** Results of resolved shots, drained by the client every tick (single player: the same process). */
    public static final ConcurrentLinkedQueue<ShotOutcome> OUTCOMES = new ConcurrentLinkedQueue<>();

    private static volatile dev.cjmc.WeaponCatalog catalog;
    private static volatile BlockHealthTracker tracker;

    private CjCombat() {}

    /** Called once from the common entrypoint. */
    public static void init(dev.cjmc.WeaponCatalog weapons) {
        CjEntities.register();
        catalog = weapons;
        dev.cjmc.WeaponCatalog.BlockRules b = weapons.blocks();
        BlockHealthTracker t = new BlockHealthTracker(
                new BlockHealthModel.Rules(b.healthPerHardness(), b.minHardness(), b.regenDelayTicks(), b.regenPerTick()));
        tracker = t;
        ServerTickEvents.END_WORLD_TICK.register(t::tick);
        ServerWorldEvents.UNLOAD.register((server, world) -> t.forget(world));
    }

    public static dev.cjmc.WeaponCatalog catalog() {
        return catalog;
    }

    public static BlockHealthTracker tracker() {
        return tracker;
    }
}
