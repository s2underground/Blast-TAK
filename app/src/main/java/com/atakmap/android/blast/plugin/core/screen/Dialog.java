package com.atakmap.android.blast.plugin.core.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A small question for the user: title, message, at most one text box or
 * one list of tick boxes, and up to three buttons (positive, neutral and
 * Cancel). The host shows it in its own dialog style and passes the
 * chosen button's action back with the box's text ({@link #FIELD}) and
 * the ticks ({@link #option}).
 */
public final class Dialog {

    /** FormValues id of the text box. */
    public static final String FIELD = "field";

    /** FormValues id of tick box {@code i}. */
    public static String option(int i) {
        return "opt_" + i;
    }

    public enum FieldKind {
        TEXT, PHONE
    }

    public final String title;
    /** Null for none. */
    public final String message;

    /** The text box, or null. */
    public final String fieldValue;
    public final FieldKind fieldKind;
    public final int fieldMaxLength;
    /** Select the whole text when the box gets focus. */
    public final boolean fieldSelectAll;

    /** Tick-box list (null for none) and their starting state. */
    public final List<String> options;
    private final boolean[] checked;

    /** Button labels (null = no such button) and their actions. */
    public final String positive, positiveAction, neutral, neutralAction,
            negative;

    /** The incident the dialog is about (null for none). */
    public final String uid;
    /** Several incidents (bulk delete), or empty. */
    public final List<String> uids;

    private Dialog(Builder b) {
        title = b.title;
        message = b.message;
        fieldValue = b.fieldValue;
        fieldKind = b.fieldKind;
        fieldMaxLength = b.fieldMaxLength;
        fieldSelectAll = b.fieldSelectAll;
        options = b.options == null ? null
                : Collections.unmodifiableList(new ArrayList<>(b.options));
        checked = b.checked == null ? null : b.checked.clone();
        positive = b.positive;
        positiveAction = b.positiveAction;
        neutral = b.neutral;
        neutralAction = b.neutralAction;
        negative = b.negative;
        uid = b.uid;
        uids = Collections.unmodifiableList(new ArrayList<>(b.uids));
    }

    public boolean hasField() {
        return fieldKind != null;
    }

    public boolean checked(int i) {
        return checked != null && checked[i];
    }

    public static Builder builder(String title) {
        return new Builder(title);
    }

    public static final class Builder {
        private final String title;
        private String message;
        private String fieldValue;
        private FieldKind fieldKind;
        private int fieldMaxLength;
        private boolean fieldSelectAll;
        private List<String> options;
        private boolean[] checked;
        private String positive, positiveAction, neutral, neutralAction,
                negative;
        private String uid;
        private final List<String> uids = new ArrayList<>();

        Builder(String title) {
            this.title = title;
        }

        public Builder message(String m) {
            message = m;
            return this;
        }

        public Builder field(String value, FieldKind kind, int maxLength,
                boolean selectAll) {
            fieldValue = value;
            fieldKind = kind;
            fieldMaxLength = maxLength;
            fieldSelectAll = selectAll;
            return this;
        }

        public Builder options(List<String> labels, boolean[] on) {
            options = labels;
            checked = on;
            return this;
        }

        public Builder positive(String label, String action) {
            positive = label;
            positiveAction = action;
            return this;
        }

        public Builder neutral(String label, String action) {
            neutral = label;
            neutralAction = action;
            return this;
        }

        public Builder negative(String label) {
            negative = label;
            return this;
        }

        public Builder about(String incidentUid) {
            uid = incidentUid;
            return this;
        }

        public Builder about(List<String> incidentUids) {
            uids.addAll(incidentUids);
            return this;
        }

        public Dialog build() {
            return new Dialog(this);
        }
    }
}
