package com.atakmap.android.blast.plugin.core.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One row of buttons sharing the width by weight. A row of one button
 * spans the width.
 */
public final class Buttons extends Item {

    public final List<Button> buttons;
    /** Space above and below the row, in dp. */
    public final int marginTopDp, marginBottomDp;
    /** Two-line tiles of equal height with 1 dp gaps (preset pickers). */
    public final boolean tiles;

    public Buttons(Button... bs) {
        this(0, bs);
    }

    public Buttons(int marginTopDp, Button... bs) {
        this(marginTopDp, 0, false, list(bs));
    }

    private Buttons(int top, int bottom, boolean tiles, List<Button> bs) {
        super(null);
        this.marginTopDp = top;
        this.marginBottomDp = bottom;
        this.tiles = tiles;
        this.buttons = bs;
    }

    private static List<Button> list(Button... bs) {
        List<Button> l = new ArrayList<>();
        Collections.addAll(l, bs);
        return Collections.unmodifiableList(l);
    }

    /** The same row with {@code dp} of space below it. */
    public Buttons spacedBelow(int dp) {
        return new Buttons(marginTopDp, dp, tiles, buttons);
    }

    /** The same row as equal-height tiles. */
    public Buttons asTiles() {
        return new Buttons(marginTopDp, marginBottomDp, true, buttons);
    }
}
