package dev.cjmc.combat;

import java.util.UUID;

/** A shot, as the client hands it to the server: where the player's eyes were, where they looked, with what, and whether he was crouched (a tighter spread). */
public record ShotRequest(UUID player, double[] origin, double[] direction, String weaponId, long seed, boolean crouching) {}
