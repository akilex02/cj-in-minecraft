package dev.cjmc;

/** Turns the mouse buttons into the intents of the protocol. Only while an item of ours is in hand (or the fists are out) and no screen is open and CJ is not knocked down. */
public final class ControlsMapper {
    private ControlsMapper() {}

    public static int intents(boolean holdingWeapon, boolean melee, boolean screenOpen, boolean attackDown, boolean useDown, boolean frozen) {
        if (frozen || screenOpen || (!holdingWeapon && !melee)) {
            return 0;
        }
        if (melee) {
            return attackDown ? Protocol.INTENT_MELEE : 0; // fists and bat: the left button strikes, the right one does nothing
        }
        int intents = 0;
        if (useDown) {
            intents |= Protocol.INTENT_AIM; // right mouse button: aim
        }
        if (attackDown) {
            intents |= Protocol.INTENT_FIRE; // left mouse button: fire
        }
        return intents;
    }
}
