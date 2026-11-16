package com.atakmap.android.blast.plugin.core.app;

import com.atakmap.android.blast.plugin.core.screen.FormValues;
import com.atakmap.android.blast.plugin.core.screen.Screen;

import java.util.Collections;
import java.util.Map;

/**
 * A form in a dialog (NBC 1, the EOD 9-line, range and bearing, polar
 * plot): a title, the form's items, up to three buttons, what its live
 * lines say as the user types ({@link #live}) and what its buttons do
 * ({@link #press}). The host shows it in its own dialog style, calls
 * {@code live} once when it opens and after every edit, tap or tick, and
 * passes a button to {@code press} with the form's values; Cancel only
 * closes it.
 */
public final class FormDialog {

    /** The buttons, as passed to {@link #press}. */
    public static final String POSITIVE = "positive", NEUTRAL = "neutral";

    public final String title;
    public final Screen form;
    /** Long forms scroll. */
    public final boolean scrolls;
    /** Padding around the form: sides and top, in dp. */
    public final int padDp, padTopDp;
    /** Button labels; null = no such button. */
    public final String positive, neutral, negative;

    /** What a form does (package use). */
    interface Logic {
        /** Live slot texts for these values (slot id to text). */
        Map<String, String> live(FormValues v);

        /** A button: {@link #POSITIVE} or {@link #NEUTRAL}. */
        Outcome press(String button, FormValues v);
    }

    private final Logic logic;

    FormDialog(String title, Screen form, boolean scrolls, int padDp,
            int padTopDp, String positive, String neutral, String negative,
            Logic logic) {
        this.title = title;
        this.form = form;
        this.scrolls = scrolls;
        this.padDp = padDp;
        this.padTopDp = padTopDp;
        this.positive = positive;
        this.neutral = neutral;
        this.negative = negative;
        this.logic = logic;
    }

    /** The live lines' texts for what is in the form now (slot to text). */
    public Map<String, String> live(FormValues v) {
        Map<String, String> m = logic.live(v);
        return m == null ? Collections.<String, String> emptyMap() : m;
    }

    /** A button was pressed; the form closes and the host does this. */
    public Outcome press(String button, FormValues v) {
        Outcome o = logic.press(button, v);
        return o == null ? Outcome.NOTHING : o;
    }

    /** No live lines. */
    static final Map<String, String> NO_LIVE = Collections.emptyMap();
}
