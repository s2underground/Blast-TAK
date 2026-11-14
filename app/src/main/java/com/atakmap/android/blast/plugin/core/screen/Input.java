package com.atakmap.android.blast.plugin.core.screen;

/** A labelled text field. The host reports its text under {@link #id}. */
public final class Input extends Item {

    public final String id;
    /** Shown above the field. */
    public final String label;
    public final String value;
    /** Digits keyboard. */
    public final boolean numeric;
    public final int maxLength;

    public Input(String id, String label, String value, boolean numeric,
            int maxLength) {
        super(null);
        this.id = id;
        this.label = label;
        this.value = value == null ? "" : value;
        this.numeric = numeric;
        this.maxLength = maxLength;
    }
}
