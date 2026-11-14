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
     * @param gpsAzToGz true bearing from your GPS position to ground zero,
     *        used when no azimuth was saved (NaN if unknown)
     */
    public static Screen form(NucRecord r, double gpsAzToGz) {
        NucRecord.Obs o = r.obs;
        double az0 = Double.isNaN(o.azDeg) ? gpsAzToGz : o.azDeg;
        Screen s = new Screen();
        s.add(Text.note(NucReports.PRECEDENCE));
        s.add(new Input(AZ, "BRAVO: direction to the burst from you, degrees"
                + (Double.isNaN(o.azDeg) && !Double.isNaN(az0)
                        ? " (filled from your GPS to ground zero, true)" : ""),
                Double.isNaN(az0) ? "" : Long.toString(Math.round(az0)), true,
                DIGITS));
        s.add(new Check(MAG, "Magnetic (from a compass)", false));
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
