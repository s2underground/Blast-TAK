package com.atakmap.android.blast.plugin.core.screen;

import com.atakmap.android.blast.plugin.core.Formats;
import com.atakmap.android.blast.plugin.core.NucMil;
import com.atakmap.android.blast.plugin.core.NucModel;
import com.atakmap.android.blast.plugin.core.NucRecord;
import com.atakmap.android.blast.plugin.core.NucReports;
import com.atakmap.android.blast.plugin.core.NucText;

/**
 * The selected-incident card for the nuclear reference mode: banner, DO
 * NOW, NBC 1 button, live YOU / Team slots, the ring key, the fallout
 * picture (civil bands or military Zones I/II with NBC 3), the EDM entry
 * and {@link WindPicker}, actions and sources.
 */
public final class NucCard {

    private NucCard() {
    }

    public static final String SLOT_YOU = StandoffCard.SLOT_YOU,
            SLOT_TEAM = StandoffCard.SLOT_TEAM;

    /** Open the NBC 1 NUC report builder. */
    public static final String NBC1 = "nbc1";
    /** Switch the fallout picture. */
    public static final String PIC_CIVIL = "pic_civil",
            PIC_MILITARY = "pic_military";
    /** Copy the NBC 3 NUC text ({@link #nbc3Text}). */
    public static final String COPY_NBC3 = "copy_nbc3";
    /** Reference tab sections. */
    public static final String HOW_ZONES = "how_zones", WHY = "why",
            WHAT_TO_DO = "what_to_do";

    /** The EDM box and its button. */
    public static final String EDM = "edm", APPLY_EDM = "apply_edm";
    public static final String EDM_ERROR = "Enter 3, 6 or 9 digits from the "
            + "EDM line.", EDM_APPLIED = "EDM applied on this device. Use "
                    + "SEND to share it.";

    /** Ring key text colours (brighter than the map strokes, for text). */
    public static int ringInk(NucText.Ring ring) {
        switch (ring) {
            case SDZ:
                return 0xFFFF5252;
            case MDZ:
                return 0xFFFF9100;
            case LDZ:
                return 0xFFFFEA00;
            case BURN:
                return 0xFF18FFFF;
            case RAD:
                return 0xFF8C9EFF;
            default:
                return 0xFFE0E0E0;
        }
    }

    static final int FALLOUT_TEXT = 0xFFE1BEE7, ZONE_I_TEXT = 0xFFFF8A80,
            ZONE_II_TEXT = 0xFFFFE57F;

    /**
     * The card for one nuclear incident.
     *
     * @param name the incident's display name on the map
     * @param gzGrid ground zero as MGRS to 10 m, for the NBC 3 text
     */
    public static Screen card(NucRecord r, String name, String gzGrid) {
        return card(r, name, gzGrid, wind(r));
    }

    /** The wind picker for this incident's card, set to its saved wind. */
    public static WindPicker wind(NucRecord r) {
        return new WindPicker(r.windFromDeg, r.windMph);
    }

    /**
     * The card, with the host's own {@code wind} picker (from
     * {@link #wind}) so the host can keep handling its taps.
     */
    public static Screen card(NucRecord r, String name, String gzGrid,
            WindPicker wind) {
        boolean grey = r.isCleared();
        Screen s = new Screen();
        s.add(Text.of(name, 17, grey ? Tone.DIM : Tone.TEXT, true));
        s.add(Text.of(NucText.threat(r) + (grey ? " · ENDED" : ""), 13,
                grey ? Tone.DIM : Tone.NUC, true));
        s.add(Text.of(NucText.BANNER, 12, Tone.AMBER, true));
        if (!r.isKnownModel())
            s.add(Text.of("This build doesn't have model " + r.modelId + " "
                    + r.modelEd + ". Showing the distances the sender drew; "
                    + "fallout is not drawn.", 12, Tone.AMBER, false));
        else if (r.receivedMismatch)
            s.add(Text.of("The sender's distances differ from this build's "
                    + "for the same model. This build's are drawn. Check with "
                    + "the sender.", 12, Tone.RED, false));

        s.add(Text.heading("DO NOW"));
        int i = 1;
        for (String line : NucText.DO_NOW) {
            s.add(Text.of(i + "  " + line, 13, Tone.TEXT, i == 1));
            i++;
        }
        s.add(Text.of(NucText.FLASH, 12, Tone.DIM, false));
        s.add(new Buttons(6, new Button(NBC1, r.obs.isEmpty()
                ? "NBC 1 NUC REPORT…" : "NBC 1 NUC REPORT… (edit)",
                Button.Kind.PRIMARY, 1f, true)));

        s.add(Text.of("", 14, Tone.TEXT, false).padded(6, 0).inSlot(SLOT_YOU));
        s.add(Text.of("", 12, Tone.DIM, false).inSlot(SLOT_TEAM));

        s.add(Text.heading("RINGS (each an outer bound)"));
        for (NucText.Ring ring : NucText.Ring.values()) {
            String dist = ring == NucText.Ring.GET_INSIDE
                    ? "50 mi (80 km), dotted"
                    : NucText.dist(ring.radiusM(r))
                            + (ring == NucText.Ring.BURN ? ", dashed" : "");
            Ink ink = grey ? Ink.of(Tone.DIM) : Ink.argb(ringInk(ring));
            s.add(new KeyRow(KeyRow.Kind.TALL, ink, ring.label + ": " + dist,
                    ink, NucText.meaning(ring)));
        }

        fallout(s, r, grey, gzGrid, wind);

        s.add(CardActions.sendRow(6, false));
        s.add(CardActions.renameRow());
        s.add(CardActions.statusRow(grey));
        s.add(new Buttons(Button.of(WHY, "Why these distances?")));
        s.add(new Buttons(Button.of(WHAT_TO_DO, "What to do (FEMA)")));
        s.add(Text.of(NucText.basis(r), 12, Tone.DIM, false));
        for (String c : NucText.CAVEATS)
            s.add(Text.of("• " + c, 12, Tone.DIM, false));
        s.add(Text.of(NucText.provenance(r), 11, Tone.DIM, false).padded(4,
                4));
        return s;
    }

    /** The FALLOUT PICTURE section. */
    private static void fallout(Screen s, NucRecord r, boolean grey,
            String gzGrid, WindPicker wind) {
        s.add(Text.heading("FALLOUT PICTURE"));
        s.add(new Buttons(new Button(PIC_CIVIL, "CIVIL\nFEMA / Glasstone",
                r.mil.on ? Button.Kind.NORMAL : Button.Kind.SELECTED, 1f,
                false), new Button(PIC_MILITARY,
                        "MILITARY\nFM 3-3-1 Zones I / II", r.mil.on
                                ? Button.Kind.SELECTED : Button.Kind.NORMAL,
                        1f, false)));
        s.add(Text.of(NucText.falloutLine(r), 13, Tone.TEXT, false));
        if (r.drawsFallout()) {
            int[] fr = NucModel.FALLOUT_RATES;
            for (int k = 0; k < fr.length; k++)
                s.add(new KeyRow(KeyRow.Kind.SHORT, grey ? Ink.of(Tone.DIM)
                        : Ink.argb(NucText.FALLOUT_STROKE[k]),
                        Formats.thousands(fr[k]) + " R/h at H+1: "
                                + NucText.dist(NucModel.contour(r.yieldKt,
                                        fr[k], r.windMph).downwindM)
                                + " downwind", grey ? Ink.of(Tone.DIM)
                                        : Ink.argb(FALLOUT_TEXT), null));
            s.add(Text.of("Idealized contours (Glasstone Table 9.93, 100% "
                    + "fission). Dose rates fall about tenfold for every "
                    + "sevenfold increase in time: 100 R/h at H+1 is about 10 "
                    + "R/h at H+7 and 1 R/h at H+49. Lack of visible fallout "
                    + "does not mean no fallout.", 12, Tone.DIM, false));
        }
        NucMil.Prediction p = r.drawsMilitary() ? r.militaryPrediction()
                : null;
        if (p != null) {
            s.add(new KeyRow(KeyRow.Kind.SHORT, grey ? Ink.of(Tone.DIM)
                    : Ink.argb(NucText.ZONE_I_STROKE), "Zone I: "
                            + Math.round(p.zoneIKm) + " km", grey
                                    ? Ink.of(Tone.DIM)
                                    : Ink.argb(ZONE_I_TEXT), null));
            s.add(Text.of(NucReports.ZONE_I, 12, Tone.DIM, false));
            s.add(new KeyRow(KeyRow.Kind.SHORT, grey ? Ink.of(Tone.DIM)
                    : Ink.argb(NucText.ZONE_II_STROKE), "Zone II: "
                            + Math.round(p.zoneIIKm) + " km", grey
                                    ? Ink.of(Tone.DIM)
                                    : Ink.argb(ZONE_II_TEXT), null));
            s.add(Text.of(NucReports.ZONE_II, 12, Tone.DIM, false));
            if (!p.arrivalHours().isEmpty())
                s.add(new KeyRow(KeyRow.Kind.SHORT, grey ? Ink.of(Tone.DIM)
                        : Ink.argb(NucText.ARC_STROKE),
                        "Dashed arcs: estimated arrival H+1, H+2 …",
                        Ink.of(Tone.TEXT), null));
            s.add(Text.of("Arrival may be as early as half the estimate. No "
                    + "fallout by twice the estimate (or 12 h) means none "
                    + "expected.", 12, Tone.DIM, false));
            s.add(Text.of(NucReports.nbc3(r, p, gzGrid), 12, Tone.TEXT, false)
                    .monospace());
            s.add(new Buttons(Button.of(COPY_NBC3, "Copy NBC 3 NUC text")));
            for (String c : NucReports.CAVEATS)
                s.add(Text.of("• " + c, 12, Tone.DIM, false));
            s.add(new Buttons(Button.of(HOW_ZONES,
                    "How Zones I and II are drawn")));
        }
        if (r.mil.on && r.isKnownModel()) {
            String g = NucMil.group(r.yieldKt);
            s.add(Text.of("Effective downwind message (EDM) line for this "
                    + "yield's group" + (g == null ? "" : " (" + g + ")")
                    + ": 6 digits dddsss, 9 digits with angle, or 3 digits "
                    + "(Zone I km, winds below 8 km/h).", 12, Tone.DIM,
                    false));
            s.add(new EntryRow(EDM, "e.g. 090016", 9, true, 2f,
                    Button.of(APPLY_EDM, "Apply EDM")));
        }
        if (r.isKnownModel()) {
            s.add(Text.heading(r.mil.on ? "OR A SURFACE WIND (rough guide)"
                    : r.hasWind() ? "CHANGE WIND" : "ADD WIND"));
            wind.addTo(s);
        }
    }

    /** The NBC 3 NUC text the card shows, or null if there is none. */
    public static String nbc3Text(NucRecord r, String gzGrid) {
        NucMil.Prediction p = r.drawsMilitary() ? r.militaryPrediction()
                : null;
        return p == null ? null : NucReports.nbc3(r, p, gzGrid);
    }

    /**
     * The "YOU" line.
     *
     * @param eastNorth your position in metres east/north of ground zero,
     *        or null when there is no usable own position
     */
    public static Text you(NucRecord r, double[] eastNorth) {
        Text base = Text.of("", 14, Tone.TEXT, false).padded(6, 0)
                .inSlot(SLOT_YOU);
        NucText.Where w = eastNorth == null ? null
                : NucText.where(r, eastNorth[0], eastNorth[1]);
        if (w == null)
            return base.with("YOU: no GPS position yet.", Tone.DIM);
        String s = "YOU: " + w.headline() + "\n" + w.detail();
        if (r.isCleared())
            s = "Incident marked ended. " + s;
        return base.with(s, r.isCleared() ? Tone.DIM
                : w.zone != null && w.zone != NucText.Ring.GET_INSIDE
                        || w.falloutRate > 0 ? Tone.RED
                                : w.zone == NucText.Ring.GET_INSIDE
                                        ? Tone.AMBER : Tone.GREEN);
    }

    /**
     * The team line, from teammates' positions in metres east/north of
     * ground zero (null entries are skipped).
     */
    public static Text team(NucRecord r, double[][] eastNorth) {
        Text base = Text.of("", 12, Tone.DIM, false).inSlot(SLOT_TEAM);
        int[] n = NucText.count(r, eastNorth);
        return base.with(n[2] == 0 ? "Team: no other contacts on the map."
                : "Team on the map: " + n[0] + " inside the damage zones"
                        + (r.drawsFallout() ? ", " + n[1]
                                + " inside a fallout contour" : "")
                        + " (of " + n[2] + ").",
                (n[0] > 0 || n[1] > 0) && !r.isCleared() ? Tone.RED
                        : Tone.DIM);
    }
}
