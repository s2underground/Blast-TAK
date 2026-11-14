package com.atakmap.android.blast.plugin.core.screen;

/**
 * A paragraph of text. Sizes are in sp-like points (0 = the host's default
 * size); padding in dp. {@link Tone#DEFAULT} keeps the host's default
 * colour (used in dialogs, which follow the host's dialog theme).
 */
public final class Text extends Item {

    public final String text;
    public final float size;
    public final Tone tone;
    public final boolean bold;
    public final int padTopDp, padBottomDp;
    /** Optional icon id shown before the text (host maps it), or null. */
    public final String icon;
    /** Fixed-width font (reports). */
    public final boolean mono;
    /** The user can select and copy the text. */
    public final boolean selectable;

    private Text(String slot, String text, float size, Tone tone,
            boolean bold, int padTopDp, int padBottomDp, String icon,
            boolean mono, boolean selectable) {
        super(slot);
        this.text = text == null ? "" : text;
        this.size = size;
        this.tone = tone == null ? Tone.TEXT : tone;
        this.bold = bold;
        this.padTopDp = padTopDp;
        this.padBottomDp = padBottomDp;
        this.icon = icon;
        this.mono = mono;
        this.selectable = selectable;
    }

    public static Text of(String text, float size, Tone tone, boolean bold) {
        return new Text(null, text, size, tone, bold, 0, 0, null, false,
                false);
    }

    /** A pane heading for a step, e.g. "1. THREAT" (more space round it). */
    public static Text step(String text) {
        return of(text, 12, Tone.BLUE, true).padded(10, 4);
    }

    /** A section heading (small, bold, blue, spaced above). */
    public static Text heading(String text) {
        return of(text, 12, Tone.BLUE, true).padded(8, 2);
    }

    /** Dialog: a bold field label in the dialog's own text style. */
    public static Text label(String text) {
        return of(text, 0, Tone.DEFAULT, true).padded(8, 2);
    }

    /** Dialog: a small explanatory note. */
    public static Text note(String text) {
        return of(text, 12, Tone.DEFAULT, false).padded(2, 2);
    }

    /** Dialog: the live report preview (fixed width, copyable). */
    public static Text preview(String slotName) {
        return new Text(slotName, "", 0, Tone.DEFAULT, false, 8, 8, null,
                true, true);
    }

    private Text copy(String slot2, String text2, Tone tone2, int top,
            int bottom, String icon2, boolean mono2) {
        return new Text(slot2, text2, size, tone2, bold, top, bottom, icon2,
                mono2, selectable);
    }

    public Text monospace() {
        return copy(slot, text, tone, padTopDp, padBottomDp, icon, true);
    }

    public Text padded(int topDp, int bottomDp) {
        return copy(slot, text, tone, topDp, bottomDp, icon, mono);
    }

    public Text withIcon(String iconId) {
        return copy(slot, text, tone, padTopDp, padBottomDp, iconId, mono);
    }

    public Text inSlot(String slotName) {
        return copy(slotName, text, tone, padTopDp, padBottomDp, icon, mono);
    }

    /** Same look, new words and colour (for live slots). */
    public Text with(String newText, Tone newTone) {
        return copy(slot, newText, newTone, padTopDp, padBottomDp, icon,
                mono);
    }

    /** Same look, new words (for live slots). */
    public Text with(String newText) {
        return with(newText, tone);
    }
}
