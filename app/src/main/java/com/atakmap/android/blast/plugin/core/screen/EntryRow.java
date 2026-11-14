package com.atakmap.android.blast.plugin.core.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A short number box with its buttons on the same row (e.g. "Exact speed
 * [mph] [Set]"). It shows {@link #hint} until typed in and is reported
 * under {@link #id}.
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
    /**
     * The text the box should hold, or null to leave whatever is typed.
     * Hosts set it without running {@link #changeAction}.
     */
    public final String value;
    /** Action the host runs after each edit by the user, or null. */
    public final String changeAction;
    /** Box and buttons centred vertically on the row. */
    public final boolean centered;

    public EntryRow(String id, String hint, int maxLength, boolean digitsOnly,
            float weight, Button... bs) {
        this(id, hint, maxLength, digitsOnly, weight, list(bs), null, null,
                true);
    }

    private EntryRow(String id, String hint, int maxLength,
            boolean digitsOnly, float weight, List<Button> buttons,
            String value, String changeAction, boolean centered) {
        super(null);
        this.id = id;
        this.hint = hint;
        this.maxLength = maxLength;
        this.digitsOnly = digitsOnly;
        this.weight = weight;
        this.buttons = buttons;
        this.value = value;
        this.changeAction = changeAction;
        this.centered = centered;
    }

    private static List<Button> list(Button... bs) {
        List<Button> l = new ArrayList<>();
        Collections.addAll(l, bs);
        return Collections.unmodifiableList(l);
    }

    public EntryRow withValue(String v) {
        return new EntryRow(id, hint, maxLength, digitsOnly, weight, buttons,
                v, changeAction, centered);
    }

    public EntryRow onChange(String action) {
        return new EntryRow(id, hint, maxLength, digitsOnly, weight, buttons,
                value, action, centered);
    }

    /** Box and buttons top-aligned (as the custom quantity row in 1.1). */
    public EntryRow topAligned() {
        return new EntryRow(id, hint, maxLength, digitsOnly, weight, buttons,
                value, changeAction, false);
    }
}
