package com.atakmap.android.blast.plugin.core.screen;

/**
 * A colour: either a palette role ({@link Tone}, which each host maps) or
 * an exact ARGB value, for keys that must match what is drawn on the map.
 */
public final class Ink {

    /** The palette role, or null when {@link #argb} is used. */
    public final Tone tone;
    public final int argb;

    private Ink(Tone tone, int argb) {
        this.tone = tone;
        this.argb = argb;
    }

    public static Ink of(Tone t) {
        return new Ink(t, 0);
    }

    public static Ink argb(int argb) {
        return new Ink(null, argb);
    }

    @Override
    public String toString() {
        return tone != null ? tone.name()
                : String.format(java.util.Locale.US, "#%08X", argb);
    }
}
