package com.atakmap.android.blast.plugin.core.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A short number box with its buttons on the same row (e.g. "Exact speed
 * [mph] [Set]"). The box starts empty, shows {@link #hint} until typed in,
 * and is reported under {@link #id}.
 */
public final class EntryRow extends Item {

    public final String id;
    public final String hint;
    public final int maxLength;
    /** Whole digits only (no decimal point). */
    public final boolean digitsOnly;
    /** The box's share of the row; each button takes its own weight. */
    public final float weight;
    public final List<Button> buttons;

    public EntryRow(String id, String hint, int maxLength, boolean digitsOnly,
            float weight, Button... bs) {
        super(null);
        this.id = id;
        this.hint = hint;
        this.maxLength = maxLength;
        this.digitsOnly = digitsOnly;
        this.weight = weight;
        List<Button> l = new ArrayList<>();
        Collections.addAll(l, bs);
        this.buttons = Collections.unmodifiableList(l);
    }
}
