package com.atakmap.android.blast.plugin.core.screen;

import com.atakmap.android.blast.plugin.core.BlastRecord;
import com.atakmap.android.blast.plugin.core.Formats;
import com.atakmap.android.blast.plugin.core.Guidance;
import com.atakmap.android.blast.plugin.core.Labels;
import com.atakmap.android.blast.plugin.core.PositionCheck;
import com.atakmap.android.blast.plugin.core.StandoffRow;

/**
 * The selected-incident card for a bomb threat (CISA/FBI card): what it
 * says, in what order and colour, and which actions it offers. Hosts render
 * it and fill the two live slots from {@link #you} and {@link #team}.
 */
public final class StandoffCard {

    private StandoffCard() {
    }

    public static final String SLOT_YOU = "you", SLOT_TEAM = "team";

    public static final String SEND_ALL = "send_all", SEND_TO = "send_to",
            FLY = "fly", RENAME = "rename", EXPORT = "export",
            STATUS = "status", DELETE = "delete";

    /** Swatches for the mandatory / shelter-in-place / preferred rows. */
    private static final Tone[] ZONE_TONES = { Tone.RED, Tone.AMBER,
            Tone.GREEN };

    /**
     * The card for one incident.
     *
     * @param name the incident's display name on the map
     */
    public static Screen card(BlastRecord r, String name) {
        boolean cleared = r.isCleared();
        Screen s = new Screen();
        s.add(Text.of(name, 17, cleared ? Tone.DIM : Tone.TEXT, true)
                .withIcon(r.rowId));
        s.add(Text.of(Labels.threat(r) + (cleared ? " · CLEARED" : ""), 13,
                cleared ? Tone.DIM : Tone.AMBER, true));
        String[][] rows = Labels.zoneRows(r);
        for (int i = 0; i < rows.length; i++)
            s.add(new Legend(cleared ? Tone.DIM : ZONE_TONES[i], rows[i],
                    new float[] { 1.3f, 1f, 1f },
                    new boolean[] { false, true, true }, 14, Tone.TEXT));
        s.add(Text.of(Labels.metresNote(r), 11, Tone.DIM, false));
        s.add(Text.of("", 14, Tone.TEXT, false).padded(6, 0).inSlot(SLOT_YOU));
        s.add(Text.of("", 12, Tone.DIM, false).inSlot(SLOT_TEAM));
        if (r.table() == null)
            s.add(Text.of("This build doesn't have the " + r.sourceId + " "
                    + Formats.editionLabel(r.edition) + " table. Showing the "
                    + "distances the sender drew.", 12, Tone.AMBER, false));
        else if (r.disagreesWithTable())
            s.add(Text.of("These distances differ from this build's copy of "
                    + "the same table. Check with the sender.", 12, Tone.RED,
                    false));
        s.add(Text.of(Labels.provenance(r), 11, Tone.DIM, false).padded(4,
                4));
        s.add(new Buttons(new Button(SEND_ALL, "SEND TO ALL",
                Button.Kind.PRIMARY, 1.4f, true), Button.of(SEND_TO,
                        "Send to…"), Button.of(FLY, "Fly to")));
        s.add(new Buttons(Button.of(RENAME, "Rename…"), Button.of(EXPORT,
                "Export…")));
        s.add(new Buttons(Button.of(STATUS, cleared ? "Reopen"
                : "Mark cleared"), Button.of(DELETE, "Delete…")));
        s.add(Text.of(Guidance.CARD_ACTIONS, 13, Tone.TEXT, false).padded(6,
                2));
        for (String c : Guidance.CAVEATS)
            s.add(Text.of("• " + c, 12, Tone.DIM, false));
        return s;
    }

    /**
     * The "YOU" line.
     *
     * @param hasFix false when there is no usable own position (or no
     *        incident point): distance and accuracy are then ignored
     * @param distanceM metres from the incident to you
     * @param accuracyM horizontal accuracy (CE) in metres, NaN if unknown
     */
    public static Text you(BlastRecord r, boolean hasFix, double distanceM,
            double accuracyM) {
        Text base = Text.of("", 14, Tone.TEXT, false).padded(6, 0)
                .inSlot(SLOT_YOU);
        if (!hasFix)
            return base.with("YOU: no GPS position yet.", Tone.DIM);
        StandoffRow row = r.row();
        boolean ceKnown = !Double.isNaN(accuracyM);
        PositionCheck.Result res = PositionCheck.check(row, distanceM,
                accuracyM);
        if (res == null)
            return base.with("YOU: position check unavailable.", Tone.DIM);
        String s = "YOU: " + res.headline() + "\n" + res.guidance();
        String w = res.boundaryWarning();
        if (w != null)
            s += "\n" + w;
        if (ceKnown && accuracyM > row.mandatoryRadiusM())
            s += "\nGPS accuracy is poor (about " + Formats.measured(accuracyM)
                    + "): treat this check as approximate.";
        if (r.isCleared())
            s = "Incident marked cleared. " + s;
        return base.with(s, r.isCleared() ? Tone.DIM
                : res.zone == PositionCheck.Zone.MANDATORY ? Tone.RED
                        : res.zone == PositionCheck.Zone.SHELTER ? Tone.AMBER
                                : Tone.GREEN);
    }

    /**
     * The team line, from the distances (metres) of other contacts on the
     * map to the incident.
     */
    public static Text team(BlastRecord r, double[] distancesM) {
        Text base = Text.of("", 12, Tone.DIM, false).inSlot(SLOT_TEAM);
        int[] n = PositionCheck.count(r.row(), distancesM);
        return base.with(distancesM.length == 0
                ? "Team: no other contacts on the map."
                : "Team on the map: " + n[0] + " in mandatory, " + n[1]
                        + " in shelter-in-place, " + n[2]
                        + " at or beyond preferred.",
                n[0] > 0 && !r.isCleared() ? Tone.RED : Tone.DIM);
    }
}
