package com.atakmap.android.blast.plugin.core.screen;

/** A button. The host runs whatever its {@link #action} id means. */
public final class Button {

    public enum Kind {
        /** The main action (ATAK: blue). */
        PRIMARY,
        /** Any other action. */
        NORMAL,
        /** The chosen option of a set (ATAK: purple). */
        SELECTED,
        /** The chosen option in a UXO form (ATAK: dark orange). */
        SELECTED_UXO
    }

    public final String action;
    public final String label;
    public final Kind kind;
    public final float weight;
    public final boolean bold;

    public Button(String action, String label, Kind kind, float weight,
            boolean bold) {
        this.action = action;
        this.label = label;
        this.kind = kind == null ? Kind.NORMAL : kind;
        this.weight = weight;
        this.bold = bold;
    }

    public static Button of(String action, String label) {
        return new Button(action, label, Kind.NORMAL, 1f, false);
    }
}
