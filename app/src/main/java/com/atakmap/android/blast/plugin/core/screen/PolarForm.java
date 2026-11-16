package com.atakmap.android.blast.plugin.core.screen;

import com.atakmap.android.blast.plugin.core.Geo;
import com.atakmap.android.blast.plugin.core.NucMil;
import com.atakmap.android.blast.plugin.core.NucRecord;
import com.atakmap.android.blast.plugin.core.NucReports;
import com.atakmap.android.blast.plugin.core.ZoneCalculator;
import com.atakmap.android.blast.plugin.core.port.Magnetic;

/**
 * "From what I saw": place ground zero by polar plot (azimuth and
 * flash-to-bang from your GPS position, FM 3-11.3).
 */
public final class PolarForm {

    private PolarForm() {
    }

    public static final String AZ = "az", MAG = "mag", FTB = "ftb";
    public static final String TITLE = "Place from what you observed",
            NO_GPS = "No GPS position yet: tap the map instead.";

    /** Digits accepted in each number field. */
    static final int DIGITS = 4;

    public static Screen form() {
        Screen s = new Screen();
        s.add(new Input(AZ, "Azimuth to the stem or cloud centre, degrees", "",
                true, DIGITS));
        s.add(new Check(MAG, "Magnetic bearing (from a compass)", true));
        s.add(new Input(FTB, "Flash-to-bang, seconds", "", true, DIGITS));
        s.add(Text.note(NucReports.FLASH_HELP + " Ground zero is placed that "
                + "far along the azimuth from your GPS position (polar plot, "
                + "FM 3-11.3) and marked estimated."));
        return s;
    }

    /** Where ground zero goes, or why it can't be placed. */
    public static final class Result {
        /** Message for the user, or null on success. */
        public final String error;
        public final double lat, lon;
        public final NucRecord.Obs obs;

        Result(String error, double lat, double lon, NucRecord.Obs obs) {
            this.error = error;
            this.lat = lat;
            this.lon = lon;
            this.obs = obs;
        }
    }

    public static Result place(FormValues v, double selfLat, double selfLon,
            Magnetic magnetic) {
        double a = ZoneCalculator.parseQuantity(v.get(AZ));
        double s = ZoneCalculator.parseQuantity(v.get(FTB));
        if (!(a >= 0 && a <= 360))
            return fail("Enter an azimuth from 0 to 359.");
        if (!(s > 0 && s <= 300))
            return fail("Enter the flash-to-bang time, 1 to 300 s.");
        double t = a;
        if (v.checked(MAG)) {
            t = magnetic == null ? Double.NaN : magnetic.toTrue(a);
            if (Double.isNaN(t))
                return fail("Magnetic declination unavailable: enter a true "
                        + "bearing.");
        }
        double[] ll = Geo.destination(selfLat, selfLon, ((t % 360) + 360) % 360,
                NucMil.polarKm(s) * 1000);
        return new Result(null, ll[0], ll[1], new NucRecord.Obs(selfLat,
                selfLon, t, s, Double.NaN, "", "SUS", "UNK", "UNK", "EE"));
    }

    private static Result fail(String msg) {
        return new Result(msg, Double.NaN, Double.NaN, null);
    }
}
