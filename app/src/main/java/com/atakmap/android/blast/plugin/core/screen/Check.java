package com.atakmap.android.blast.plugin.core.screen;

/** A checkbox. The host reports its state under {@link #id}. */
public final class Check extends Item {

    public final String id;
    public final String label;
    public final boolean checked;

    public Check(String id, String label, boolean checked) {
        super(null);
        this.id = id;
        this.label = label;
        this.checked = checked;
    }
}
