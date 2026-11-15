package com.atakmap.android.blast.plugin.core.screen;

import com.atakmap.android.blast.plugin.core.BlastRecord;
import com.atakmap.android.blast.plugin.core.Formats;
import com.atakmap.android.blast.plugin.core.NucRecord;
import com.atakmap.android.blast.plugin.core.NucText;
import com.atakmap.android.blast.plugin.core.PositionCheck;
import com.atakmap.android.blast.plugin.core.UxoRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * "INCIDENTS ON THE MAP": the heading with its counts and "Delete all
 * cleared", and one row per incident (active first, then nearest), each
 * with where you stand and Go to / 9-line / Delete.
 */
public final class IncidentList {

    private IncidentList() {
    }

    /** Row actions: prefix + incident uid. */
    public static final String SELECT = "sel:", GO = "go:", NINE_LINE = "nine:",
            DELETE = "del:";
    public static final String DELETE_CLEARED = "delete_cleared";
    public static final String SLOT_HEADING = "list_heading";
    /** The rebuilt section holding the rows. */
    public static final String ROWS = "rows";

    /** One incident on the map: exactly one record is set. */
    public static final class Entry {
        public final String uid;
        public final String name;
        public final BlastRecord standoff;
        public final UxoRecord uxo;
        public final NucRecord nuc;
        /** Metres from you (or the map centre); NaN if unknown. */
        public final double distanceM;

        private Entry(String uid, String name, BlastRecord s, UxoRecord u,
                NucRecord n, double d) {
            this.uid = uid;
            this.name = name;
            this.standoff = s;
            this.uxo = u;
            this.nuc = n;
            this.distanceM = d;
        }

        public static Entry of(String uid, String name, BlastRecord r,
                double d) {
            return new Entry(uid, name, r, null, null, d);
        }

        public static Entry of(String uid, String name, UxoRecord r,
                double d) {
            return new Entry(uid, name, null, r, null, d);
        }

        public static Entry of(String uid, String name, NucRecord r,
                double d) {
            return new Entry(uid, name, null, null, r, d);
        }

        public boolean cleared() {
            return nuc != null ? nuc.isCleared()
                    : uxo != null ? uxo.isCleared() : standoff.isCleared();
        }
    }

    /** Active incidents first, then by distance (a sorted copy). */
    public static List<Entry> sorted(List<Entry> entries) {
        List<Entry> l = new ArrayList<>(entries);
        Collections.sort(l, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                boolean ca = a.cleared(), cb = b.cleared();
                if (ca != cb)
                    return ca ? 1 : -1;
                return Double.compare(a.distanceM, b.distanceM);
            }
        });
        return l;
    }

    /**
     * What the list shows, as one string: when it is unchanged the host
     * can skip redrawing (distances count in 10 m steps).
     */
    public static String signature(List<Entry> sorted, String selectedUid) {
        StringBuilder sig = new StringBuilder();
        for (Entry e : sorted)
            sig.append(e.uid).append(e.cleared()).append(e.name)
                    .append(Math.round(e.distanceM / 10)).append(';');
        return sig.append(selectedUid).toString();
    }

    public static int clearedCount(List<Entry> entries) {
        int n = 0;
        for (Entry e : entries)
            if (e.cleared())
                n++;
        return n;
    }

    /** Before the first refresh: the bare heading, no rows. */
    public static Screen placeholder() {
        Screen s = new Screen();
        s.add(heading("INCIDENTS ON THE MAP", 0));
        s.add(new Section(ROWS, new Screen()).rebuilt());
        return s;
    }

    private static TitleRow heading(String text, int cleared) {
        return new TitleRow(Text.step(text).inSlot(SLOT_HEADING),
                Button.of(DELETE_CLEARED, "Delete all cleared" + (cleared > 0
                        ? " (" + cleared + ")" : "")).shown(cleared > 0),
                true);
    }

    /**
     * The list.
     *
     * @param sorted entries from {@link #sorted}
     * @param selectedUid the incident whose card is open, or null
     */
    public static Screen screen(List<Entry> sorted, String selectedUid) {
        int cleared = clearedCount(sorted);
        int active = sorted.size() - cleared;
        Screen s = new Screen();
        s.add(heading("INCIDENTS ON THE MAP: " + active + " active"
                + (cleared > 0 ? ", " + cleared + " cleared" : ""), cleared));
        Screen rows = new Screen();
        if (sorted.isEmpty())
            rows.add(Text.of("None. Choose the kind of threat below and place "
                    + "it.", 13, Tone.DIM, false));
        for (Entry e : sorted)
            rows.add(row(e, e.uid.equals(selectedUid)));
        s.add(new Section(ROWS, rows).rebuilt());
        return s;
    }

    private static ListRow row(Entry e, boolean sel) {
        boolean clr = e.cleared();
        Text t = Text.of((sel ? "▾ " : "▸ ") + e.name + where(e), 14,
                clr ? Tone.DIM : Tone.TEXT, sel);
        if (e.standoff != null)
            t = t.withIcon(e.standoff.rowId);
        Ink bar = Ink.of(clr ? Tone.DIM : e.nuc != null ? Tone.NUC
                : e.uxo != null ? Tone.ORANGE : Tone.RED);
        List<Button> bs = new ArrayList<>();
        bs.add(Button.of(GO + e.uid, "Go to"));
        if (e.uxo != null)
            bs.add(Button.of(NINE_LINE + e.uid, "9-line"));
        bs.add(Button.of(DELETE + e.uid, "Delete").asDanger());
        return new ListRow(SELECT + e.uid, sel, bar, t,
                bs.toArray(new Button[0]));
    }

    /** The distance line, and where you stand while it is active. */
    static String where(Entry e) {
        double d = e.distanceM;
        if (Double.isNaN(d))
            return "";
        String w = "\n" + Formats.measured(d);
        if (e.cleared())
            return w;
        if (e.nuc != null) {
            NucRecord.Radii r = e.nuc.radii;
            NucText.Ring z = d < r.sdzM ? NucText.Ring.SDZ
                    : d < r.mdzM ? NucText.Ring.MDZ
                            : d < r.ldzM ? NucText.Ring.LDZ : null;
            return w + " · you: " + (z == null ? "outside the damage zones"
                    : z.label);
        }
        if (e.uxo != null)
            return w + " · you: " + (d < e.uxo.cordonM ? "INSIDE" : "outside")
                    + " cordon " + Formats.thousands(e.uxo.cordonM) + " m";
        return w + " · you: "
                + PositionCheck.zoneOf(e.standoff.row(), d).title;
    }

    /** The uid in a row action, or null if {@code action} has no prefix. */
    public static String uid(String action, String prefix) {
        return action != null && action.startsWith(prefix)
                ? action.substring(prefix.length()) : null;
    }
}
