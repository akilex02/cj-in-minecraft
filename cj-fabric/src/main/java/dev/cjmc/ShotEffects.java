package dev.cjmc;

import dev.cjmc.combat.CjCombat;
import dev.cjmc.combat.PelletRay;
import dev.cjmc.combat.ShotOutcome;
import dev.cjmc.combat.ShotRequest;
import dev.cjmc.combat.ShotResolver;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns the shot events of GTA into shots in the world. The network thread queues the rounds; the client tick hands each one to
 * the integrated server (origin and direction of the player's eyes, the weapon), which damages mobs and wears down blocks
 * (dev.cjmc.combat), and draws what the server reports: a tracer and an impact for every pellet.
 */
public final class ShotEffects {
    public static final ShotEffects INSTANCE = new ShotEffects();
    private static final Logger LOG = LoggerFactory.getLogger("cj_fabric");
    private static final int MAX_ROUNDS_PER_TICK = 8;
    private static final int MAX_OUTCOMES_PER_TICK = 64;
    private static final int CROWDED_FROM = 2; // outcomes already drawn this tick from which the next ones are drawn with fewer particles

    private final ShotDedup dedup = new ShotDedup();
    private final ConcurrentLinkedQueue<Integer> pending = new ConcurrentLinkedQueue<>(); // the GTA weapon id of each round
    private boolean warnedNoServer;

    private ShotEffects() {}

    /** Called from the network thread. */
    public void onShot(Protocol.Shot shot) {
        int rounds = dedup.newRounds(shot.shotSeq(), shot.rounds());
        for (int i = 0; i < rounds && pending.size() < 64; i++) {
            pending.add(shot.weapon());
        }
    }

    /** Called from the client thread every tick. */
    public void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            pending.clear();
            CjCombat.OUTCOMES.clear();
            return;
        }
        Integer weapon;
        for (int i = 0; i < MAX_ROUNDS_PER_TICK && (weapon = pending.poll()) != null; i++) {
            submit(client, player, weapon);
        }
        ShotOutcome outcome;
        int drawn = 0;
        for (int i = 0; i < MAX_OUTCOMES_PER_TICK && (outcome = CjCombat.OUTCOMES.poll()) != null; i++) {
            draw(world, outcome, drawn >= CROWDED_FROM); // the minigun fires about 2 rounds a tick: its bursts are drawn lighter, like a volley
            drawn++;
        }
    }

    private void submit(MinecraftClient client, ClientPlayerEntity player, int gtaWeapon) {
        IntegratedServer server = client.getServer();
        if (server == null) {
            if (!warnedNoServer) {
                warnedNoServer = true;
                LOG.warn("shots need the integrated server (single player); nothing is damaged on this connection");
            }
            return;
        }
        WeaponCatalog catalog = CjCombat.catalog();
        WeaponCatalog.WeaponDef def = catalog == null ? null : catalog.byGta(gtaWeapon);
        if (def == null) {
            return;
        }
        Vec3d eye = player.getCameraPosVec(1.0f);
        Vec3d look = player.getRotationVec(1.0f);
        ShotRequest request = new ShotRequest(player.getUuid(), new double[] {eye.x, eye.y, eye.z},
                new double[] {look.x, look.y, look.z}, def.id(), ThreadLocalRandom.current().nextLong(), CrouchClient.crouching());
        server.execute(() -> ShotResolver.resolve(server, request));
    }

    private void draw(ClientWorld world, ShotOutcome outcome, boolean crowded) {
        double[] o = outcome.origin();
        boolean volley = outcome.pellets().size() > 1 || crowded;
        for (ShotOutcome.Pellet p : outcome.pellets()) {
            Vec3d end = new Vec3d(p.x(), p.y(), p.z());
            Vec3d start = new Vec3d(o[0], o[1] - 0.25, o[2]);
            Vec3d along = end.subtract(start);
            if (along.length() > 1.0) { // the muzzle sits a little ahead of the eyes
                Vec3d muzzle = start.add(along.normalize().multiply(0.6));
                Vec3d rest = end.subtract(muzzle);
                double[][] points = ShotGeometry.tracerPoints(new double[] {muzzle.x, muzzle.y, muzzle.z},
                        new double[] {rest.x, rest.y, rest.z}, rest.length(), volley ? 4 : 12);
                for (double[] point : points) {
                    world.addParticle(ParticleTypes.END_ROD, point[0], point[1], point[2], 0, 0, 0);
                }
            }
            if (p.kind() == PelletRay.Hit.ENTITY) {
                for (int i = 0; i < (volley ? 2 : 6); i++) {
                    world.addParticle(ParticleTypes.CRIT, end.x, end.y, end.z, spread(world), world.random.nextDouble() * 0.3, spread(world));
                }
            } else if (p.kind() == PelletRay.Hit.BLOCK) {
                BlockState state = world.getBlockState(new BlockPos(p.blockX(), p.blockY(), p.blockZ()));
                if (!state.isAir()) { // the pellet may have just broken it
                    for (int i = 0; i < (volley ? 2 : 8); i++) {
                        world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), end.x, end.y, end.z,
                                spread(world), world.random.nextDouble() * 0.3, spread(world));
                    }
                }
            }
        }
    }

    private static double spread(ClientWorld world) {
        return (world.random.nextDouble() - 0.5) * 0.4;
    }
}
