package com.atakmap.android.blast.plugin.core.app;

import com.atakmap.android.blast.plugin.core.screen.Dialog;
import com.atakmap.android.blast.plugin.core.screen.Screen;

/**
 * What the host should do after a button, a dialog choice or another
 * user action: show a message, a dialog or a form, redraw, switch tab,
 * fly the map, start or stop a map tap. Fields left unset mean "nothing
 * to do". The host carries them out in the order they are declared here,
 * then carries out {@link #next}.
 */
public final class Outcome {

    /** A short message, or null. */
    public final String toast;
    /** A dialog to show, or null. */
    public final Dialog dialog;
    /** Restyle the open card in place with these items, or null. */
    public final Screen restyle;
    /** The ring-labels setting changed: relabel every incident's rings. */
    public final boolean labels;
    /** A setting shown elsewhere changed (the emergency number). */
    public final boolean settings;
    /** Fly the map to this incident (host), or null. */
    public final String flyTo;
    /** The incident went away: tidy the selection and redraw. */
    public final boolean gone;
    /** Redraw the open card / the list. */
    public final boolean card, list;
    /** Switch tab: TRUE = Reference, FALSE = Incidents, null = stay. */
    public final Boolean reference;
    /** Reference section to scroll to (with {@link #reference}), or null. */
    public final String section;
    /** Stop listening for a map tap (the core already stopped waiting). */
    public final boolean stopPick;
    /** Start listening for a map tap, showing this prompt; or null. */
    public final String pick;
    /**
     * Redraw the new-incident section from {@link BlastApp#newIncident}
     * as it is now (without taking what is in its boxes).
     */
    public final boolean newIncident;
    /** Put the cursor in this new-incident box, or null. */
    public final String focus;
    /** Scroll the pane to the top (a new incident's card opened). */
    public final boolean top;
    /** A form to show, or null. */
    public final FormDialog form;
    /** Then do this as well (a second message, say), or null. */
    public final Outcome next;

    /** A message, a dialog and / or redraws (the common case). */
    Outcome(String toast, Dialog dialog, boolean card, boolean list,
            boolean gone, String flyTo) {
        this(new B().toast(toast).dialog(dialog).redraw(card, list)
                .gone(gone).flyTo(flyTo));
    }

    private Outcome(B b) {
        toast = b.toast;
        dialog = b.dialog;
        restyle = b.restyle;
        labels = b.labels;
        settings = b.settings;
        flyTo = b.flyTo;
        gone = b.gone;
        card = b.card;
        list = b.list;
        reference = b.reference;
        section = b.section;
        stopPick = b.stopPick;
        pick = b.pick;
        newIncident = b.newIncident;
        focus = b.focus;
        top = b.top;
        form = b.form;
        next = b.next;
    }

    /** A copy to change. */
    private B b() {
        B b = new B();
        b.toast = toast;
        b.dialog = dialog;
        b.restyle = restyle;
        b.labels = labels;
        b.settings = settings;
        b.flyTo = flyTo;
        b.gone = gone;
        b.card = card;
        b.list = list;
        b.reference = reference;
        b.section = section;
        b.stopPick = stopPick;
        b.pick = pick;
        b.newIncident = newIncident;
        b.focus = focus;
        b.top = top;
        b.form = form;
        b.next = next;
        return b;
    }

    /** Builder (package use). */
    static final class B {
        private String toast;
        private Dialog dialog;
        private Screen restyle;
        private boolean labels, settings;
        private String flyTo;
        private boolean gone, card, list;
        private Boolean reference;
        private String section;
        private boolean stopPick;
        private String pick;
        private boolean newIncident;
        private String focus;
        private boolean top;
        private FormDialog form;
        private Outcome next;

        B toast(String s) {
            toast = s;
            return this;
        }

        B dialog(Dialog d) {
            dialog = d;
            return this;
        }

        B redraw(boolean c, boolean l) {
            card = c;
            list = l;
            return this;
        }

        B gone(boolean g) {
            gone = g;
            return this;
        }

        B flyTo(String uid) {
            flyTo = uid;
            return this;
        }

        Outcome build() {
            return new Outcome(this);
        }
    }

    static final Outcome NOTHING = new B().build();

    static Outcome toast(String s) {
        return new B().toast(s).build();
    }

    static Outcome dialog(Dialog d) {
        return new B().dialog(d).build();
    }

    static Outcome redraw(String toast, boolean list) {
        return new B().toast(toast).redraw(true, list).build();
    }

    static Outcome tab(boolean reference, String section) {
        B b = new B();
        b.reference = reference;
        b.section = section;
        return b.build();
    }

    static Outcome form(FormDialog f) {
        B b = new B();
        b.form = f;
        return b.build();
    }

    /** Redraw the new-incident section (and show {@code toast}). */
    static Outcome newIncident(String toast) {
        B b = new B().toast(toast);
        b.newIncident = true;
        return b.build();
    }

    /** The same with the card and list redrawn too. */
    Outcome redrawAll() {
        return b().redraw(true, true).build();
    }

    Outcome withRestyle(Screen s) {
        B b = b();
        b.restyle = s;
        return b.build();
    }

    Outcome withToast(String s) {
        return b().toast(s).build();
    }

    Outcome withDialog(Dialog d) {
        return b().dialog(d).build();
    }

    Outcome withLabels() {
        B b = b();
        b.labels = true;
        return b.build();
    }

    Outcome withSettings() {
        B b = b();
        b.settings = true;
        return b.build();
    }

    Outcome withNewIncident() {
        B b = b();
        b.newIncident = true;
        return b.build();
    }

    Outcome withFocus(String box) {
        B b = b();
        b.focus = box;
        return b.build();
    }

    Outcome withStopPick() {
        B b = b();
        b.stopPick = true;
        return b.build();
    }

    Outcome withPick(String prompt) {
        B b = b();
        b.pick = prompt;
        return b.build();
    }

    Outcome withTop() {
        B b = b();
        b.top = true;
        return b.build();
    }

    Outcome withForm(FormDialog f) {
        B b = b();
        b.form = f;
        return b.build();
    }

    /** This, then {@code o} (appended after any existing next). */
    Outcome then(Outcome o) {
        B b = b();
        b.next = next == null ? o : next.then(o);
        return b.build();
    }
}
