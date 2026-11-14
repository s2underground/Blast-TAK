package com.atakmap.android.blast.plugin.core.screen;

import com.atakmap.android.blast.plugin.core.UxoCodec;
import com.atakmap.android.blast.plugin.core.UxoRecord;
import com.atakmap.android.blast.plugin.core.UxoText;

/**
 * The EOD 9-line report form: fields, choices, live priority note and
 * preview, and the report it saves.
 */
public final class UxoReportForm {

    private UxoReportForm() {
    }

    public static final String LAND = "land", POC = "poc", EMP = "emp",
            COUNT = "count", DESC = "desc", CBRN = "cbrn", RES = "res",
            IMP = "imp", PROT = "prot", PRI = "pri";
    public static final String SLOT_PRI_NOTE = "pri_note",
            SLOT_PREVIEW = "preview";

    static final String[] EMPLACEMENTS = { "Dropped", "Projected", "Placed",
            "Thrown", "Possible IED", "Unknown" };
    static final UxoRecord.Priority[] PRIORITIES = {
            UxoRecord.Priority.IMMEDIATE, UxoRecord.Priority.INDIRECT,
            UxoRecord.Priority.MINOR, UxoRecord.Priority.NO_THREAT };
    static final String[] PRIORITY_NOTES = {
            "Stops movement or threatens vital assets.",
            "Slows movement or threatens important assets.",
            "Reduces movement or threatens non-critical assets.",
            "Little or no effect at present." };

    /** Longest number of items accepted (digits). */
    static final int COUNT_DIGITS = 4;

    public static String title(String incidentName) {
        return "EOD 9-line: " + incidentName;
    }

    public static Screen form(UxoRecord r, String emergencyNumber) {
        UxoRecord.Report rp = r.report;
        int max = UxoCodec.MAX_REPORT_TEXT;
        Screen s = new Screen();
        s.add(Text.note("Line 1 (time found) and line 2 (grid) are filled in "
                + "from the incident."));
        s.add(new Input(LAND, "Line 2, landmark / how to find it",
                rp.landmark, false, max));
        s.add(new Input(POC, "Line 3, contact (callsign, phone, frequency)",
                rp.contact, false, max));
        s.add(Text.label("Line 4, how it got there"));
        String emp = "";
        for (String e : EMPLACEMENTS)
            if (e.equals(rp.emplacement))
                emp = e;
        s.add(new Choice(EMP, EMPLACEMENTS, EMPLACEMENTS, 3, emp, true,
                Button.Kind.SELECTED_UXO));
        s.add(new Input(COUNT, "Line 4, number of items seen", rp.count > 0
                ? Integer.toString(rp.count) : "", true, COUNT_DIGITS));
        s.add(new Input(DESC, "Line 4, description: size, shape, colour, "
                + "markings, condition", rp.description, false, max));
        s.add(new Input(CBRN, "Line 5, chemical or other signs (leaks, smell, "
                + "sick people)", rp.cbrn, false, max));
        s.add(new Input(RES, "Line 6, resources threatened (road, homes, "
                + "school…)", rp.resources, false, max));
        s.add(new Input(IMP, "Line 7, impact (road closed, …)", rp.impact,
                false, max));
        s.add(new Input(PROT, "Line 8, protective measures taken (e.g. people "
                + "kept out)", rp.protective, false, max));
        s.add(Text.label("Line 9, recommended priority"));
        String[] codes = new String[PRIORITIES.length];
        String[] labels = new String[PRIORITIES.length];
        String pri = "";
        for (int i = 0; i < PRIORITIES.length; i++) {
            codes[i] = PRIORITIES[i].name();
            labels[i] = PRIORITIES[i].label;
            if (PRIORITIES[i] == rp.priority)
                pri = codes[i];
        }
        s.add(new Choice(PRI, codes, labels, 2, pri, true,
                Button.Kind.SELECTED_UXO));
        s.add(Text.note("").inSlot(SLOT_PRI_NOTE));
        s.add(Text.label("Preview"));
        s.add(Text.preview(SLOT_PREVIEW));
        s.add(Text.note(UxoText.REPORT_CAUTION + " " + UxoText.PRIORITY_NOTE
                + "\nEmergency number: " + emergencyNumber));
        return s;
    }

    /** The report the values describe (lengths and count cleaned). */
    public static UxoRecord.Report report(FormValues v) {
        int n = 0;
        try {
            n = Integer.parseInt(v.get(COUNT).trim());
        } catch (NumberFormatException ignored) {
            // empty or not a number: no count
        }
        UxoRecord.Priority pri = UxoRecord.Priority.UNSET;
        for (UxoRecord.Priority p : PRIORITIES)
            if (p.name().equals(v.get(PRI)))
                pri = p;
        return new UxoRecord.Report(n, clean(v, DESC), clean(v, LAND),
                v.get(EMP), clean(v, CBRN), clean(v, RES), clean(v, IMP),
                clean(v, PROT), pri, clean(v, POC));
    }

    private static String clean(FormValues v, String id) {
        return UxoCodec.longText(v.get(id));
    }

    /** The note under the priority buttons. */
    public static String priorityNote(FormValues v) {
        for (int i = 0; i < PRIORITIES.length; i++)
            if (PRIORITIES[i].name().equals(v.get(PRI)))
                return PRIORITY_NOTES[i];
        return "Tap one (tap again to clear).";
    }

    /** The 9-line text for the preview and "Copy text". */
    public static String preview(UxoRecord r, FormValues v, String callsign,
            String grid) {
        return UxoText.nineLine(r.withReport(report(v)), callsign, grid);
    }
}
