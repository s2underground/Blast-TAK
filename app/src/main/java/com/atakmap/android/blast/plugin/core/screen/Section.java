package com.atakmap.android.blast.plugin.core.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A named block of items that can be shown or hidden as a whole. A
 * {@link #live} section's items change in number from one update to the
 * next, so hosts rebuild it instead of restyling it in place.
 */
public final class Section extends Item {

    public final String id;
    public final List<Item> items;
    public final boolean visible;
    public final boolean live;

    public Section(String id, Screen content) {
        this(id, content.items(), true, false);
    }

    private Section(String id, List<Item> items, boolean visible,
            boolean live) {
        super(null);
        this.id = id;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.visible = visible;
        this.live = live;
    }

    public Section shown(boolean on) {
        return new Section(id, items, on, live);
    }

    public Section rebuilt() {
        return new Section(id, items, visible, true);
    }
}
