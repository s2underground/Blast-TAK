package com.atakmap.android.blast.plugin.core.screen;

import com.atakmap.android.blast.plugin.core.Formats;
import com.atakmap.android.blast.plugin.core.NucRecord;
import com.atakmap.android.blast.plugin.core.NucReports;
import com.atakmap.android.blast.plugin.core.ZoneCalculator;
import com.atakmap.android.blast.plugin.core.port.Magnetic;

import java.util.List;

/** The NBC 1 NUC observer report form (FM 3-11.3 Fig. G-3). */
public final class Nbc1Form {

    private Nbc1Form() {
    }

    public static final String AZ = "az", MAG = "mag", FTB = "ftb",
            CW = "cw", GQ = "gq", KILO = "kilo", SO = "so", DEL = "del",
            CON = "con";
    public static final String SLOT_PREVIEW = "preview";
    public static final String TITLE = "NBC 1 NUC report";

    /** Digits accepted in each number field. */
    static final int DIGITS = 4;

    /**
     * What was typed in BRAVO when the report was last saved on this
     * device. The record only keeps the true bearing (that is what is
     * shared), so without this a compass reading would be shown as the
     * saved true bearing and converted again if Magnetic were ticked.
     */
    public static final class Entry {
        final String typed;
        final boolean magnetic;
        /** The true bearing that was saved from it. */
        final double savedTrueDeg;

        Entry(String typed, boolean magnetic, double savedTrueDeg) {
            this.typed = typed;
            this.magnetic = magnetic;
            this.savedTrueDeg = savedTrueDeg;
        }
    }

    /** Remember what was typed for the next time the form opens. */
    public static Entry entry(FormValues v, NucRecord.Obs saved) {
        return new Entry(v.get(AZ), v.checked(MAG), saved.azDeg);
    }

    /** As {@link #form(NucRecord, double, Entry)} with no saved entry. */
    public static Screen form(NucRecord r, double gpsAzToGz) {
        return form(r, gpsAzToGz, null);
    }

    /**
     * @param gpsAzToGz true bearing from your GPS position to ground zero,
     *        used when no azimuth was saved (NaN if unknown)
     * @param last what was typed when this device last saved the report,
     *        or null; used only if the saved bearing still comes from it
     */
    public static Screen form(NucRecord r, double gpsAzToGz, Entry last) {
        NucRecord.Obs o = r.obs;
        boolean saved = !Double.isNaN(o.azDeg);
        boolean reuse = saved && last != null && last.magnetic
                && Math.abs(last.savedTrueDeg - o.azDeg) < 1e-6;
        double az0 = saved ? o.azDeg : gpsAzToGz;
        String value = reuse ? last.typed
                : Double.isNaN(az0) ? "" : Long.toString(Math.round(az0));
        String note = reuse ? " (as you entered it, magnetic)"
                : saved ? " (saved as a true bearing)"
                        : !Double.isNaN(az0)
                                ? " (filled from your GPS to ground zero, true)"
                                : "";
        Screen s = new Screen();
        s.add(Text.note(NucReports.PRECEDENCE));
        s.add(new Input(AZ, "BRAVO: direction to the burst from you, degrees"
                + note, value, true, DIGITS));
        s.add(new Check(MAG, "Magnetic (from a compass)", reuse));
        s.add(Text.note(NucReports.FLASH_HELP));
        s.add(new Input(FTB, "JULIET: flash-to-bang, seconds",
                Double.isNaN(o.flashToBangS) ? ""
                        : Long.toString(Math.round(o.flashToBangS)), true,
                DIGITS));
        s.add(Text.note(NucReports.WIDTH_HELP));
        s.add(new Input(CW, "LIMA: cloud width at H+5, degrees",
                Double.isNaN(o.cloudWidthDeg) ? ""
                        : Formats.decimalTrim(o.cloudWidthDeg), true, DIGITS));
        Button.Kind sel = Button.Kind.SELECTED;
        s.add(Text.label("FOXTROT: ground zero"));
        s.add(new Choice(GQ, new String[] { "EE", "AA" }, new String[] {
                "Estimated (EE)", "Actual: I can see GZ (AA)" }, 2,
                o.gzQualifier, false, sel));
        s.add(Text.label("KILO: crater"));
        s.add(new Choice(KILO, new String[] { "CRATER", "NONE", "UNK" },
                new String[] { "Crater", "No crater", "Unknown" }, 3, o.crater,
                true, sel));
        s.add(Text.label("GOLF: delivery"));
        s.add(new Choice(SO, new String[] { "SUS", "OBS" }, new String[] {
                "Suspected", "Observed" }, 2, o.golfSo, false, sel));
        s.add(new Choice(DEL, new String[] { "UNK", "AIR", "MSL", "CAN" },
                new String[] { "Unknown", "Aircraft", "Missile", "Cannon" }, 4,
                o.golfDelivery, false, sel));
        s.add(new Choice(CON, new String[] { "UNK", "BOM", "MSL", "SHL" },
                new String[] { "Unknown", "Bomb", "Missile", "Shell" }, 4,
                o.golfContainer, false, sel));
        s.add(Text.note(NucReports.HOTEL_HELP + " This report uses the "
                + "incident's burst type: " + NucReports.hotel(r.burst) + "."));
        s.add(Text.preview(SLOT_PREVIEW));
        return s;
    }

    /**
     * The observations the values describe.
     *
     * @param selfLat your latitude (NaN if no GPS)
     * @param magnetic converts a compass bearing, or null when there is no
     *        own position (the bearing is then used as entered)
     */
    public static NucRecord.Obs obs(FormValues v, double selfLat,
            double selfLon, Magnetic magnetic) {
        double a = ZoneCalculator.parseQuantity(v.get(AZ));
        if (v.checked(MAG) && !Double.isNaN(a) && magnetic != null)
            a = magnetic.toTrue(a);
        return new NucRecord.Obs(selfLat, selfLon, a,
                ZoneCalculator.parseQuantity(v.get(FTB)),
                ZoneCalculator.parseQuantity(v.get(CW)), v.get(KILO),
                v.get(SO), v.get(DEL), v.get(CON), v.get(GQ));
    }

    /** The NBC 1 text ("Copy text"). */
    public static String report(NucRecord withObs, String obsGrid,
            String gzGrid) {
        return NucReports.nbc1(withObs, obsGrid, gzGrid);
    }

    /** The preview: the report plus what is still missing. */
    public static String preview(NucRecord withObs, String obsGrid,
            String gzGrid) {
        String s = report(withObs, obsGrid, gzGrid);
        List<String> miss = NucReports.nbc1Missing(withObs, obsGrid);
        if (!miss.isEmpty()) {
            s += "\n\nMissing:";
            for (String x : miss)
                s += "\n- " + x;
        }
        return s;
    }
}
