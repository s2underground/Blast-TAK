package com.atakmap.android.blast.plugin.core.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One row of buttons sharing the width by weight. */
public final class Buttons extends Item {

    public final List<Button> buttons;
    /** Space above the row, in dp. */
    public final int marginTopDp;

    public Buttons(Button... bs) {
        this(0, bs);
    }

    public Buttons(int marginTopDp, Button... bs) {
        super(null);
        this.marginTopDp = marginTopDp;
        List<Button> l = new ArrayList<>();
        Collections.addAll(l, bs);
        this.buttons = Collections.unmodifiableList(l);
    }
}
