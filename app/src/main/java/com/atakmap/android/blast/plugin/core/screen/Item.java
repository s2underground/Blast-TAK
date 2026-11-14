package com.atakmap.android.blast.plugin.core.screen;

/** One piece of a {@link Screen}. */
public abstract class Item {

    /**
     * Name of a live slot the host keeps a handle on, so it can replace the
     * item later without rebuilding the screen (e.g. "you", "team"); null
     * for static items.
     */
    public final String slot;

    Item(String slot) {
        this.slot = slot;
    }
}
