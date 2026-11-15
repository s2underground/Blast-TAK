package com.atakmap.android.blast.plugin.core.screen;

/** A button. The host runs whatever its {@link #action} id means. */
public final class Button {

    public enum Kind {
        /** The main action (ATAK: blue). */
        PRIMARY,
        /** Any other action. */
        NORMAL,
        /** The chosen option of a set (ATAK: purple, nuclear). */
        SELECTED,
        /** The chosen option in a UXO form (ATAK: dark orange). */
        SELECTED_UXO,
        /** The chosen option for a bomb threat (ATAK: blue grey). */
        SELECTED_STANDOFF
    }

    public final String action;
    public final String label;
    public final Kind kind;
    public final float weight;
    public final boolean bold;
    /** False: shown dimmed and does nothing. */
    public final boolean enabled;
    /** False: takes no space (it may appear after a later choice). */
    public final boolean visible;
    /** Threat row id whose icon sits before the label, or null. */
    public final String icon;
    /** Label at the start (left) instead of centred. */
    public final boolean alignStart;
    /** Destructive (ATAK: red label). */
    public final boolean danger;

    public Button(String action, String label, Kind kind, float weight,
            boolean bold) {
        this(action, label, kind, weight, bold, true, true, null, false,
                false);
    }

    private Button(String action, String label, Kind kind, float weight,
            boolean bold, boolean enabled, boolean visible, String icon,
            boolean alignStart, boolean danger) {
        this.action = action;
        this.label = label;
        this.kind = kind == null ? Kind.NORMAL : kind;
        this.weight = weight;
        this.bold = bold;
        this.enabled = enabled;
        this.visible = visible;
        this.icon = icon;
        this.alignStart = alignStart;
        this.danger = danger;
    }

    public static Button of(String action, String label) {
        return new Button(action, label, Kind.NORMAL, 1f, false);
    }

    /** NORMAL, or {@code selectedKind} when {@code on}. */
    public static Button pick(String action, String label, boolean on,
            Kind selectedKind) {
        return new Button(action, label, on ? selectedKind : Kind.NORMAL, 1f,
                false);
    }

    public Button enabled(boolean on) {
        return new Button(action, label, kind, weight, bold, on, visible,
                icon, alignStart, danger);
    }

    public Button shown(boolean on) {
        return new Button(action, label, kind, weight, bold, enabled, on,
                icon, alignStart, danger);
    }

    public Button withIcon(String iconId) {
        return new Button(action, label, kind, weight, bold, enabled,
                visible, iconId, alignStart, danger);
    }

    public Button asDanger() {
        return new Button(action, label, kind, weight, bold, enabled,
                visible, icon, alignStart, true);
    }

    public Button alignedStart() {
        return new Button(action, label, kind, weight, bold, enabled,
                visible, icon, true, danger);
    }
}
