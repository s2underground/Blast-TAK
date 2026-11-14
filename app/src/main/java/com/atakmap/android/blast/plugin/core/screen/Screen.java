package com.atakmap.android.blast.plugin.core.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A card or screen described as data: an ordered list of items. The core
 * builds it; each host (ATAK pane, TAK-X Swing panel, a test) renders it.
 */
public final class Screen {

    private final List<Item> items = new ArrayList<>();

    public Screen add(Item i) {
        if (i != null)
            items.add(i);
        return this;
    }

    public List<Item> items() {
        return Collections.unmodifiableList(items);
    }

    /** The item in a live slot, or null. */
    public Item slot(String name) {
        for (Item i : items)
            if (name.equals(i.slot))
                return i;
        return null;
    }
}
