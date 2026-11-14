package com.atakmap.android.blast.plugin.core.screen;

/**
 * A place for a host-built control the screen model doesn't describe yet
 * (e.g. the wind picker or the EDM entry). The host inserts its own widget
 * for {@link #id}. To be replaced by Input/Choice items.
 */
public final class Widget extends Item {

    public final String id;

    public Widget(String id) {
        super(null);
        this.id = id;
    }
}
