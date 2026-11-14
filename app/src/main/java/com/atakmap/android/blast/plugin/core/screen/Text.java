package com.atakmap.android.blast.plugin.core.screen;

/** A paragraph of text. Sizes are in sp-like points; padding in dp. */
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

    public Text(String slot, String text, float size, Tone tone, boolean bold,
            int padTopDp, int padBottomDp, String icon) {
        this(slot, text, size, tone, bold, padTopDp, padBottomDp, icon,
                false);
    }

    public Text(String slot, String text, float size, Tone tone, boolean bold,
            int padTopDp, int padBottomDp, String icon, boolean mono) {
        super(slot);
        this.text = text == null ? "" : text;
        this.size = size;
        this.tone = tone == null ? Tone.TEXT : tone;
        this.bold = bold;
        this.padTopDp = padTopDp;
        this.padBottomDp = padBottomDp;
        this.icon = icon;
        this.mono = mono;
    }

    public static Text of(String text, float size, Tone tone, boolean bold) {
        return new Text(null, text, size, tone, bold, 0, 0, null);
    }

    /** A section heading (small, bold, blue, spaced above). */
    public static Text heading(String text) {
        return of(text, 12, Tone.BLUE, true).padded(8, 2);
    }

    public Text monospace() {
        return new Text(slot, text, size, tone, bold, padTopDp, padBottomDp,
                icon, true);
    }

    public Text padded(int topDp, int bottomDp) {
        return new Text(slot, text, size, tone, bold, topDp, bottomDp, icon,
                mono);
    }

    public Text withIcon(String iconId) {
        return new Text(slot, text, size, tone, bold, padTopDp, padBottomDp,
                iconId, mono);
    }

    public Text inSlot(String slotName) {
        return new Text(slotName, text, size, tone, bold, padTopDp,
                padBottomDp, icon, mono);
    }

    /** Same look, new words and colour (for live slots). */
    public Text with(String newText, Tone newTone) {
        return new Text(slot, newText, size, newTone, bold, padTopDp,
                padBottomDp, icon, mono);
    }
}
