package dev.cjmc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The weapons the mod knows, read from weapons.json. damage, range, pellets, spread, blockPower and knockback drive the shots
 * (see ShotResolver), and the "blocks" section holds the constants of the health of shot blocks.
 * aimSpeed (the share of the normal walking speed while aiming), sprintHeld / sprintAiming (whether the player may sprint with the
 * weapon in hand, and while aiming) and moveWhileFiring (whether it can be fired on the move) copy what GTA lets CJ do, so the
 * Minecraft player does not outrun his animation; canJump says whether the player may jump with it in hand (GTA does not let CJ jump
 * with the minigun). explosionPower (the power of the explosion of its projectile; 0 = none) and projectileSpeed (blocks per second of
 * its projectile; 0 = an instant bullet) make a rocket launcher. reach (a melee blow's reach in blocks; 0 = not a melee weapon) and
 * coneHalfAngle (degrees) make the fists and the bat; item is false for the fists, which are the empty hand. durability (uses: rounds fired or
 * blows struck; 0 = it never wears out) and repair (the item that mends it at the anvil) make the weapon item wear out. crouchFire says
 * whether it can be fired crouched (the bat, the minigun and the rocket launcher cannot: GTA stands CJ up as he uses them).
 */
public final class WeaponCatalog {
    public record WeaponDef(String id, String name, int gta, String model, float[] holdOffset, float[] holdRotation,
                            float damage, float range, int pellets, float spread, float blockPower,
                            float aimSpeed, boolean sprintHeld, boolean sprintAiming, boolean moveWhileFiring, float knockback,
                            boolean canJump, float explosionPower, float projectileSpeed,
                            float reach, float coneHalfAngle, boolean item, String ammo, int durability, String repair, boolean crouchFire) {}

    /** The constants of the health of shot blocks (see BlockHealthModel) and whether a broken block drops as if mined with any tool. */
    public record BlockRules(double healthPerHardness, double minHardness, long regenDelayTicks, double regenPerTick,
                             boolean dropsWithoutTool) {}

    private final List<WeaponDef> defs;
    private final BlockRules blocks;

    private WeaponCatalog(List<WeaponDef> defs, BlockRules blocks) {
        this.defs = List.copyOf(defs);
        this.blocks = blocks;
    }

    public BlockRules blocks() {
        return blocks;
    }

    public List<WeaponDef> all() {
        return defs;
    }

    public WeaponDef byId(String id) {
        for (WeaponDef d : defs) {
            if (d.id().equals(id)) {
                return d;
            }
        }
        return null;
    }

    public WeaponDef byGta(int gta) {
        for (WeaponDef d : defs) {
            if (d.gta() == gta) {
                return d;
            }
        }
        return null;
    }

    public static WeaponCatalog load() {
        try (InputStream in = WeaponCatalog.class.getResourceAsStream("/weapons.json")) {
            if (in == null) {
                throw new IllegalStateException("weapons.json is missing from the mod");
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("weapons.json could not be read", e);
        }
    }

    public static WeaponCatalog parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("weapons");
            if (array == null) {
                throw new IllegalArgumentException("weapons.json has no \"weapons\" list");
            }
            List<WeaponDef> out = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            Set<Integer> gtaIds = new HashSet<>();
            for (JsonElement e : array) {
                JsonObject o = e.getAsJsonObject();
                WeaponDef d = new WeaponDef(text(o, "id"), text(o, "name"), (int) number(o, "gta"), text(o, "model"),
                        vec3(o, "holdOffset"), vec3(o, "holdRotation"), number(o, "damage"), number(o, "range"),
                        (int) number(o, "pellets"), number(o, "spread"), number(o, "blockPower"),
                        number(o, "aimSpeed"), bool(o, "sprintHeld"), bool(o, "sprintAiming"), bool(o, "moveWhileFiring"), number(o, "knockback"), bool(o, "canJump"),
                        number(o, "explosionPower"), number(o, "projectileSpeed"),
                        number(o, "reach"), number(o, "coneHalfAngle"), bool(o, "item"), optionalText(o, "ammo"), (int) optionalNumber(o, "durability"), optionalText(o, "repair"), optionalBool(o, "crouchFire", true));
                if (d.gta() < 0 || d.gta() > 0xFFFF) {
                    throw new IllegalArgumentException("weapon " + d.id() + " has the GTA id " + d.gta());
                }
                if (!(d.aimSpeed() > 0f && d.aimSpeed() <= 1f)) {
                    throw new IllegalArgumentException("weapon " + d.id() + " has the aimSpeed " + d.aimSpeed() + ", which must be in (0, 1]");
                }
                boolean explosive = d.explosionPower() > 0f;
                if (!Float.isFinite(d.damage()) || d.damage() < 0f || (!explosive && !(d.damage() > 0f))) {
                    throw new IllegalArgumentException("weapon " + d.id() + " has the damage " + d.damage()
                            + ", which must be above 0 (0 only for a weapon whose explosion does the damage)");
                }
                if (!(d.explosionPower() >= 0f) || d.explosionPower() > 20f) {
                    throw new IllegalArgumentException("weapon " + d.id() + " has the explosionPower " + d.explosionPower() + ", which must be in [0, 20]");
                }
                if (!(d.projectileSpeed() >= 0f) || d.projectileSpeed() > 200f) {
                    throw new IllegalArgumentException("weapon " + d.id() + " has the projectileSpeed " + d.projectileSpeed() + ", which must be in [0, 200]");
                }
                if (d.projectileSpeed() > 0f && !explosive) {
                    throw new IllegalArgumentException("weapon " + d.id() + " fires a projectile but has no explosionPower, so it would do nothing");
                }
                if (!(d.reach() >= 0f) || d.reach() > 10f) {
                    throw new IllegalArgumentException("weapon " + d.id() + " has the reach " + d.reach() + ", which must be in [0, 10]");
                }
                if (d.reach() > 0f) {
                    if (!(d.coneHalfAngle() > 0f) || d.coneHalfAngle() > 90f) {
                        throw new IllegalArgumentException("weapon " + d.id() + " has the coneHalfAngle " + d.coneHalfAngle() + ", which must be in (0, 90]");
                    }
                    if (!(d.damage() > 0f) || d.explosionPower() > 0f || d.projectileSpeed() > 0f) {
                        throw new IllegalArgumentException("weapon " + d.id() + " is a melee weapon: it needs a damage and no explosion or projectile");
                    }
                } else if (d.coneHalfAngle() != 0f) {
                    throw new IllegalArgumentException("weapon " + d.id() + " has a coneHalfAngle but no reach");
                }
                if (!(d.range() > 0f) || d.range() > 1000f) {
                    throw new IllegalArgumentException("weapon " + d.id() + " has the range " + d.range() + ", which must be in (0, 1000]");
                }
                if (d.pellets() < 1 || d.pellets() > 32) {
                    throw new IllegalArgumentException("weapon " + d.id() + " has " + d.pellets() + " pellets, which must be 1 to 32");
                }
                if (!(d.spread() >= 0f) || d.spread() > 1f) {
                    throw new IllegalArgumentException("weapon " + d.id() + " has the spread " + d.spread() + ", which must be in [0, 1]");
                }
                if (!(d.blockPower() >= 0f) || !Float.isFinite(d.blockPower())) {
                    throw new IllegalArgumentException("weapon " + d.id() + " has the blockPower " + d.blockPower() + ", which must be 0 or more");
                }
                if (!(d.knockback() >= 0f) || d.knockback() > 5f) {
                    throw new IllegalArgumentException("weapon " + d.id() + " has the knockback " + d.knockback() + ", which must be in [0, 5]");
                }
                if (!AmmoRules.isKnown(d.ammo())) {
                    throw new IllegalArgumentException("weapon " + d.id() + " has the ammo \"" + d.ammo() + "\", which is not one of " + AmmoRules.ALL);
                }
                if (d.reach() > 0f && AmmoRules.usesAmmo(d.ammo())) {
                    throw new IllegalArgumentException("weapon " + d.id() + " is a melee weapon and cannot take ammo");
                }
                if (d.durability() < 0) {
                    throw new IllegalArgumentException("weapon " + d.id() + " has the durability " + d.durability() + ", which must be 0 (it never wears out) or more");
                }
                if (!ids.add(d.id())) {
                    throw new IllegalArgumentException("duplicate weapon id " + d.id());
                }
                if (!gtaIds.add(d.gta())) {
                    throw new IllegalArgumentException("duplicate GTA weapon id " + d.gta());
                }
                out.add(d);
            }
            JsonObject b = root.getAsJsonObject("blocks");
            if (b == null) {
                throw new IllegalArgumentException("weapons.json has no \"blocks\" section");
            }
            BlockRules rules = new BlockRules(number(b, "healthPerHardness"), number(b, "minHardness"),
                    (long) number(b, "regenDelayTicks"), number(b, "regenPerTick"), bool(b, "dropsWithoutTool"));
            if (!(rules.healthPerHardness() > 0) || !(rules.minHardness() > 0) || rules.regenDelayTicks() < 0
                    || !(rules.regenPerTick() >= 0)) {
                throw new IllegalArgumentException("the \"blocks\" section has values out of range");
            }
            return new WeaponCatalog(out, rules);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) { // a wrong type, malformed JSON...
            throw new IllegalArgumentException("weapons.json is malformed (" + e + ")", e);
        }
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            throw new IllegalArgumentException("a weapon is missing \"" + key + "\"");
        }
        return e.getAsString();
    }

    /** A text that may be missing: the empty string then. */
    private static String optionalText(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null) {
            return "";
        }
        if (!e.isJsonPrimitive()) {
            throw new IllegalArgumentException("\"" + key + "\" must be a text");
        }
        return e.getAsString();
    }

    /** A true or false that may be missing: `fallback` then. */
    private static boolean optionalBool(JsonObject o, String key, boolean fallback) {
        return o.get(key) == null ? fallback : bool(o, key);
    }

    /** A number that may be missing: 0 then. */
    private static float optionalNumber(JsonObject o, String key) {
        return o.get(key) == null ? 0f : number(o, key);
    }

    private static float number(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            throw new IllegalArgumentException("a weapon is missing \"" + key + "\"");
        }
        return e.getAsFloat();
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("a weapon needs \"" + key + "\" as true or false");
        }
        return e.getAsBoolean();
    }

    private static float[] vec3(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() != 3) {
            throw new IllegalArgumentException("\"" + key + "\" needs three numbers");
        }
        JsonArray a = e.getAsJsonArray();
        return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }
}
