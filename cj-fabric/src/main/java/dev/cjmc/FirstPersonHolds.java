package dev.cjmc;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * When CJ does not aim, GTA lets his arms hang and the weapon can fall out of the view from his eyes: each kind of weapon then gets a shift and a turn in the
 * camera's space (x right, y up, z toward the viewer) that brings it in. Aiming or firing takes it away in BLEND_SECONDS, leaving GTA's own pose, which points at
 * the middle of the screen. The defaults are a start; `/cj fp` calibrates them by eye until the game closes.
 */
public final class FirstPersonHolds {
    public enum Kind { PISTOL, RIFLE, HEAVY, BAT, FISTS }

    static final double BLEND_SECONDS = 0.15;

    private static final Map<Kind, float[]> DEFAULTS = new EnumMap<>(Kind.class);
    static {
        // calibrated in game with /cj fp by the user (lower and nearer than the first guess, which crowded the middle of the screen)
        DEFAULTS.put(Kind.PISTOL, new float[] {0.0f, 0.15f, 0.0f, 0f, 0f, 0f});
        DEFAULTS.put(Kind.RIFLE, new float[] {0.0f, 0.10f, -0.05f, 10f, 0f, 0f});
        DEFAULTS.put(Kind.HEAVY, new float[] {0.0f, 0.0f, 0.05f, 10f, 0f, 0f});
        DEFAULTS.put(Kind.BAT, new float[] {0.0f, 0.18f, -0.10f, 10f, 0f, 0f});
        DEFAULTS.put(Kind.FISTS, new float[] {0.0f, 0.20f, -0.15f, 10f, 0f, 0f});
    }

    private final Map<Kind, float[]> holds = new EnumMap<>(DEFAULTS);
    private float amount = 1f;

    /** The kind of a weapon of weapons.json by its id; the empty hand (null) is the fists; null for anything else. */
    public static Kind kindOf(String weaponId) {
        if (weaponId == null) {
            return Kind.FISTS;
        }
        return switch (weaponId) {
            case "colt45", "desert_eagle", "micro_uzi" -> Kind.PISTOL;
            case "chromegun", "ak47", "m4" -> Kind.RIFLE;
            case "minigun", "rocket_launcher" -> Kind.HEAVY;
            case "baseball_bat" -> Kind.BAT;
            default -> null;
        };
    }

    public static Kind parseKind(String name) {
        for (Kind k : Kind.values()) {
            if (k.name().equals(name.toUpperCase(Locale.ROOT))) {
                return k;
            }
        }
        return null;
    }

    public float[] hold(Kind kind) {
        return holds.get(kind).clone();
    }

    public void setHold(Kind kind, float[] six) {
        holds.put(kind, six.clone());
    }

    /** How much of the hold shows after `seconds` more: toward 0 while aiming, toward 1 when not, at 1 / BLEND_SECONDS per second. */
    public float blend(boolean aiming, double seconds) {
        float step = (float) (Math.max(0, seconds) / BLEND_SECONDS);
        amount = aiming ? Math.max(0f, amount - step) : Math.min(1f, amount + step);
        return amount;
    }

    /** The hold `six` (x, y, z, pitch, yaw, roll in degrees) taken `amount` of the way, as a 4x4 (column-major) applied in the camera's space. */
    public static float[] holdMatrix(float[] six, float amount) {
        float[] m = new float[16];
        Mat4.identity(m, 0);
        m[12] = six[0] * amount;
        m[13] = six[1] * amount;
        m[14] = six[2] * amount;
        float[] turn = FirstPersonPlacement.mul(FirstPersonPlacement.rotY(six[4] * amount),
                FirstPersonPlacement.mul(FirstPersonPlacement.rotX(six[3] * amount), FirstPersonPlacement.rotZ(six[5] * amount)));
        return FirstPersonPlacement.mul(m, turn);
    }
}
