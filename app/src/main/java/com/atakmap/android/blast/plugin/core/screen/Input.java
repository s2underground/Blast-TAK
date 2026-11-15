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
    /**
     * No label above: the label is the hint inside the box, and the
     * keyboard allows decimals ({@link #hinted}).
     */
    public final boolean hinted;

    public Input(String id, String label, String value, boolean numeric,
            int maxLength) {
        super(null);
        this.id = id;
        this.label = label;
        this.value = value == null ? "" : value;
        this.numeric = numeric;
        this.maxLength = maxLength;
        this.hinted = false;
    }

    private Input(String id, String hint) {
        super(null);
        this.id = id;
        this.label = hint;
        this.value = "";
        this.numeric = true;
        this.maxLength = 0;
        this.hinted = true;
    }

    /** A number box (decimals allowed) whose label is its hint. */
    public static Input hinted(String id, String hint) {
        return new Input(id, hint);
    }
}
