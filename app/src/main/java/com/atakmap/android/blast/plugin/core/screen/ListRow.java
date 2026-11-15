package com.atakmap.android.blast.plugin.core.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One row of a list: a colour bar, the text, small buttons at the end;
 * tapping the row runs {@link #action}.
 */
public final class ListRow extends Item {

    public final String action;
    public final boolean selected;
    public final Ink bar;
    public final Text text;
    public final List<Button> buttons;

    public ListRow(String action, boolean selected, Ink bar, Text text,
            Button... bs) {
        super(null);
        this.action = action;
        this.selected = selected;
        this.bar = bar;
        this.text = text;
        List<Button> l = new ArrayList<>();
        Collections.addAll(l, bs);
        this.buttons = Collections.unmodifiableList(l);
    }
}
