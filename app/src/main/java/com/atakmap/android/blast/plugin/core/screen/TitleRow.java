package com.atakmap.android.blast.plugin.core.screen;

/**
 * A line of text with one small button at its end (e.g. "Emergency
 * number: 911  [Change…]").
 */
public final class TitleRow extends Item {

    public final Text text;
    public final Button button;
    /**
     * Button sits at the bottom of the row (beside a heading); otherwise
     * text and button are centred vertically.
     */
    public final boolean buttonAtBottom;

    public TitleRow(Text text, Button button, boolean buttonAtBottom) {
        super(null);
        this.text = text;
        this.button = button;
        this.buttonAtBottom = buttonAtBottom;
    }
}
