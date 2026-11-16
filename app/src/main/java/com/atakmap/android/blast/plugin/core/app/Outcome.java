package com.atakmap.android.blast.plugin.core.app;

import com.atakmap.android.blast.plugin.core.screen.Dialog;
import com.atakmap.android.blast.plugin.core.screen.Screen;

/**
 * What the host should do after a button, a dialog choice or another
 * user action: show a message or a dialog, redraw, switch tab, open a
 * form, fly the map. Fields left unset mean "nothing to do".
 */
public final class Outcome {

    /** Report forms the host shows itself (they update as you type). */
    public enum Form {
        NONE, NBC1, REPORT
    }

    /** A short message, or null. */
    public final String toast;
    /** A dialog to show, or null. */
    public final Dialog dialog;
    /** Redraw the open card / the list. */
    public final boolean card, list;
    /** The incident went away: tidy the selection and redraw. */
    public final boolean gone;
    /** Fly the map to this incident (host), or null. */
    public final String flyTo;
    /** Switch tab: TRUE = Reference, FALSE = Incidents, null = stay. */
    public final Boolean reference;
    /** Reference section to scroll to (with {@link #reference}), or null. */
    public final String section;
    /** A form to open, and the incident it is for. */
    public final Form form;
    public final String formUid;
    /** Restyle the open card in place with these items, or null. */
    public final Screen restyle;
    /** A setting shown elsewhere changed (the emergency number). */
    public final boolean settings;

    Outcome(String toast, Dialog dialog, boolean card, boolean list,
            boolean gone, String flyTo) {
        this(toast, dialog, card, list, gone, flyTo, null, null, Form.NONE,
                null, null, false);
    }

    private Outcome(String toast, Dialog dialog, boolean card, boolean list,
            boolean gone, String flyTo, Boolean reference, String section,
            Form form, String formUid, Screen restyle, boolean settings) {
        this.toast = toast;
        this.dialog = dialog;
        this.card = card;
        this.list = list;
        this.gone = gone;
        this.flyTo = flyTo;
        this.reference = reference;
        this.section = section;
        this.form = form;
        this.formUid = formUid;
        this.restyle = restyle;
        this.settings = settings;
    }

    static final Outcome NOTHING = new Outcome(null, null, false, false,
            false, null);

    static Outcome toast(String s) {
        return new Outcome(s, null, false, false, false, null);
    }

    static Outcome dialog(Dialog d) {
        return new Outcome(null, d, false, false, false, null);
    }

    static Outcome redraw(String toast, boolean list) {
        return new Outcome(toast, null, true, list, false, null);
    }

    static Outcome tab(boolean reference, String section) {
        return new Outcome(null, null, false, false, false, null, reference,
                section, Form.NONE, null, null, false);
    }

    static Outcome form(Form f, String uid, boolean redraw) {
        return new Outcome(null, null, redraw, redraw, false, null, null,
                null, f, uid, null, false);
    }

    /** The same with the card and list redrawn too. */
    Outcome redrawAll() {
        return new Outcome(toast, dialog, true, true, gone, flyTo, reference,
                section, form, formUid, restyle, settings);
    }

    Outcome withRestyle(Screen s) {
        return new Outcome(toast, dialog, card, list, gone, flyTo, reference,
                section, form, formUid, s, settings);
    }

    Outcome withToast(String s) {
        return new Outcome(s, dialog, card, list, gone, flyTo, reference,
                section, form, formUid, restyle, settings);
    }

    Outcome withSettings() {
        return new Outcome(toast, dialog, card, list, gone, flyTo, reference,
                section, form, formUid, restyle, true);
    }
}
