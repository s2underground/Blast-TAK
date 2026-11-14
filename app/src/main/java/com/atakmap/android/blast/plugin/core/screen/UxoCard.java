package com.atakmap.android.blast.plugin.core.screen;

import com.atakmap.android.blast.plugin.core.Formats;
import com.atakmap.android.blast.plugin.core.UxoRecord;
import com.atakmap.android.blast.plugin.core.UxoText;

/**
 * The selected-incident card for military ordnance (ATP 4-32.2): cordon,
 * live YOU / Team lines, the EOD 9-line, DO NOW, moving-away drills and the
 * actions. Hosts render it and fill the live slots from {@link #you} and
 * {@link #team}.
 */
public final class UxoCard {

    private UxoCard() {
    }

    public static final String SLOT_YOU = StandoffCard.SLOT_YOU,
            SLOT_TEAM = StandoffCard.SLOT_TEAM;

    /** Open the EOD 9-line report builder. */
    public static final String REPORT = "report";
    /** Open the Reference tab at "What the rings mean". */
    public static final String WHY = "why";

    /** Beyond this GPS accuracy (m) the YOU check is called approximate. */
    static final double POOR_ACCURACY_M = 50;

    /**
     * The card for one UXO incident.
     *
     * @param name the incident's display name on the map
     * @param emergencyNumber the user's emergency number (e.g. "911")
     * @param grid the item's grid reference for the 9-line (may be "")
     */
    public static Screen card(UxoRecord r, String name,
            String emergencyNumber, String grid) {
        boolean cleared = r.isCleared();
        Screen s = new Screen();
        s.add(Text.of(name, 17, cleared ? Tone.DIM : Tone.TEXT, true));
        s.add(Text.of(UxoText.threat(r) + (cleared ? " · CLEARED" : ""), 13,
                cleared ? Tone.DIM : Tone.ORANGE, true));
        s.add(Text.of("CORDON " + UxoText.cordon(r.cordonM) + "  (minimum)",
                18, cleared ? Tone.DIM : Tone.ORANGE, true).padded(4, 0));
        s.add(Text.of(UxoText.basis(r) + " Fragments may travel farther.", 12,
                Tone.DIM, false));
        if (!r.isKnownEdition())
            s.add(Text.of("This build doesn't have " + r.sourceId + " "
                    + r.edition + ". Showing the distance the sender drew.",
                    12, Tone.AMBER, false));
        else if (r.receivedMismatch)
            s.add(Text.of("The sender's distance differs from this build's "
                    + "copy of the same table. This build's distance is "
                    + "drawn. Check with the sender.", 12, Tone.RED, false));
        s.add(Text.of("", 14, Tone.TEXT, false).padded(6, 0).inSlot(SLOT_YOU));
        s.add(Text.of("", 12, Tone.DIM, false).inSlot(SLOT_TEAM));
        s.add(new Buttons(6, new Button(REPORT, r.report.isEmpty()
                ? "EOD 9-LINE REPORT…" : "EOD 9-LINE REPORT… (edit)",
                Button.Kind.PRIMARY, 1f, true)));

        s.add(Text.heading("DO NOW"));
        int i = 1;
        for (String line : UxoText.doNow(r))
            s.add(Text.of((i++) + "  " + line, 13, Tone.TEXT, false));
        s.add(Text.of("Emergency number: " + emergencyNumber
                + " (change it in the Reference tab)", 12, Tone.DIM, false));

        s.add(Text.heading("MOVING AWAY"));
        UxoRecord.Where first = r.where == UxoRecord.Where.UNSET
                ? UxoRecord.Where.PATH : r.where;
        if (r.where != UxoRecord.Where.UNSET)
            s.add(Text.of("Reported from " + UxoText.whereLabel(r.where) + ".",
                    12, Tone.DIM, false));
        s.add(Text.of(UxoText.drill(first), 13, Tone.TEXT, true));
        for (UxoRecord.Where w : new UxoRecord.Where[] { UxoRecord.Where.PATH,
                UxoRecord.Where.AREA, UxoRecord.Where.VEHICLE })
            if (w != first)
                s.add(Text.of(UxoText.drill(w), 12, Tone.DIM, false));

        if (!r.report.isEmpty()) {
            s.add(Text.heading("REPORT (saved on this incident)"));
            s.add(Text.of(UxoText.nineLine(r, r.byCallsign, grid), 12,
                    Tone.TEXT, false).monospace());
        }

        s.add(CardActions.sendRow(6, false));
        s.add(CardActions.renameRow());
        s.add(CardActions.statusRow(cleared));
        s.add(new Buttons(Button.of(WHY, "Why this distance?")));
        for (String c : UxoText.CAVEATS)
            s.add(Text.of("• " + c, 12, Tone.DIM, false));
        s.add(Text.of(UxoText.provenance(r), 11, Tone.DIM, false).padded(4,
                4));
        return s;
    }

    /**
     * The "YOU" line.
     *
     * @param hasFix false when there is no usable own position
     * @param distanceM metres from the item to you
     * @param accuracyM horizontal accuracy (CE) in metres, NaN if unknown
     */
    public static Text you(UxoRecord r, boolean hasFix, double distanceM,
            double accuracyM) {
        Text base = Text.of("", 14, Tone.TEXT, false).padded(6, 0)
                .inSlot(SLOT_YOU);
        if (!hasFix)
            return base.with("YOU: no GPS position yet.", Tone.DIM);
        boolean ceKnown = !Double.isNaN(accuracyM);
        UxoText.You you = UxoText.check(r.cordonM, distanceM, accuracyM);
        if (you == null)
            return base.with("YOU: position check unavailable.", Tone.DIM);
        String s = "YOU: " + you.headline(r.cordonM) + "\n"
                + you.guidance(r.cordonM);
        String w = you.boundaryWarning();
        if (w != null)
            s += "\n" + w;
        if (ceKnown && accuracyM > POOR_ACCURACY_M)
            s += "\nGPS accuracy is poor (about " + Formats.measured(accuracyM)
                    + "): treat this check as approximate.";
        if (r.placement == UxoRecord.Placement.RB)
            s += "\nThe item's position is an estimate (range and bearing).";
        if (r.isCleared())
            s = "Incident marked cleared. " + s;
        return base.with(s, r.isCleared() ? Tone.DIM
                : you.inside ? Tone.RED : Tone.GREEN);
    }

    /** The team line, from teammates' distances (m) to the item. */
    public static Text team(UxoRecord r, double[] distancesM) {
        Text base = Text.of("", 12, Tone.DIM, false).inSlot(SLOT_TEAM);
        int[] n = UxoText.count(r.cordonM, distancesM);
        return base.with(distancesM.length == 0
                ? "Team: no other contacts on the map."
                : "Team on the map: " + n[0] + " inside the cordon, " + n[1]
                        + " outside.",
                n[0] > 0 && !r.isCleared() ? Tone.RED : Tone.DIM);
    }
}
