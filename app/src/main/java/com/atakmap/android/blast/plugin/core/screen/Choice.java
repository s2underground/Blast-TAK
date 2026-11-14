package com.atakmap.android.blast.plugin.core.screen;

/**
 * Single-choice push buttons laid out {@link #perRow} to a row. Tapping a
 * button selects its code; tapping the selected one again clears it when
 * {@link #clearable}. The host reports the code ("" for none) under
 * {@link #id}.
 */
public final class Choice extends Item {

    public final String id;
    private final String[] codes;
    private final String[] labels;
    public final int perRow;
    /** Code selected at first ("" for none). */
    public final String selected;
    public final boolean clearable;
    /** How the selected button looks (SELECTED or SELECTED_UXO). */
    public final Button.Kind selectedKind;

    public Choice(String id, String[] codes, String[] labels, int perRow,
            String selected, boolean clearable, Button.Kind selectedKind) {
        super(null);
        if (codes.length != labels.length)
            throw new IllegalArgumentException("codes and labels differ");
        this.id = id;
        this.codes = codes.clone();
        this.labels = labels.clone();
        this.perRow = perRow;
        this.selected = selected == null ? "" : selected;
        this.clearable = clearable;
        this.selectedKind = selectedKind;
    }

    public int size() {
        return codes.length;
    }

    public String code(int i) {
        return codes[i];
    }

    public String label(int i) {
        return labels[i];
    }

    /** The code after tapping option {@code i} when {@code current} is set. */
    public String tap(int i, String current) {
        return clearable && codes[i].equals(current) ? "" : codes[i];
    }
}
