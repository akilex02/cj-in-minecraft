package dev.cjmc.skate;

/**
 * The view while skating: from first person the board puts the camera behind the skater (one cannot skate seeing nothing of him), and when the ride ends the first
 * person comes back, unless the player chose another view by hand meanwhile. Generic over the game's perspective, so it is pure.
 */
final class ViewSwitch<T> {
    private final T firstPerson;
    private final T behind;
    private T restore;

    ViewSwitch(T firstPerson, T behind) {
        this.firstPerson = firstPerson;
        this.behind = behind;
    }

    /** The view to set when the ride starts, or null to leave it. */
    T onStart(T current) {
        restore = current == firstPerson ? current : null;
        return restore != null ? behind : null;
    }

    /** The view to set when the ride ends, or null to leave it (it was not switched, or the player changed it by hand). */
    T onStop(T current) {
        T back = restore != null && current == behind ? restore : null;
        restore = null;
        return back;
    }
}
