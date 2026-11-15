package com.atakmap.android.blast.plugin.core.screen;

import com.atakmap.android.blast.plugin.core.Formats;
import com.atakmap.android.blast.plugin.core.Geo;
import com.atakmap.android.blast.plugin.core.ZoneCalculator;
import com.atakmap.android.blast.plugin.core.port.Magnetic;

/**
 * UXO "Range & bearing": place the item by distance and bearing from the
 * user's GPS position, without walking closer.
 */
public final class RangeBearingForm {

    private RangeBearingForm() {
    }

    public static final String DIST = "dist", FEET = "feet", BRG = "brg",
            MAG = "mag";
    public static final String TITLE = "Place by range & bearing",
            NO_GPS = "No GPS position yet: tap the map instead.";

    public static Screen form() {
        Screen s = new Screen();
        s.add(Input.hinted(DIST, "Distance to the item"));
        s.add(new Check(FEET, "Distance in feet (otherwise metres)", false));
        s.add(Input.hinted(BRG, "Bearing to the item, degrees (0-359)"));
        s.add(new Check(MAG, "Magnetic bearing (from a compass)", true));
        s.add(Text.of("Measured from your GPS position. Estimate from where "
                + "you are now; never walk closer to measure.", 0,
                Tone.DEFAULT, false));
        return s;
    }

    /** Where the item goes, or why it can't be placed. */
    public static final class Result {
        /** Message for the user, or null on success. */
        public final String error;
        public final double lat, lon;

        Result(String error, double lat, double lon) {
            this.error = error;
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static Result place(FormValues v, double selfLat, double selfLon,
            Magnetic magnetic) {
        double r = ZoneCalculator.parseQuantity(v.get(DIST));
        double b = ZoneCalculator.parseQuantity(v.get(BRG));
        if (v.checked(FEET))
            r = Formats.ftToM(r);
        if (!(r >= 1 && r <= 20000))
            return fail("Enter a distance between 1 m and 20 km.");
        if (!(b >= 0 && b <= 360))
            return fail("Enter a bearing from 0 to 359.");
        double t = b;
        if (v.checked(MAG)) {
            t = magnetic == null ? Double.NaN : magnetic.toTrue(b);
            if (Double.isNaN(t))
                return fail("Magnetic declination unavailable: enter a true "
                        + "bearing.");
        }
        double[] ll = Geo.destination(selfLat, selfLon, ((t % 360) + 360) % 360,
                r);
        return new Result(null, ll[0], ll[1]);
    }

    private static Result fail(String msg) {
        return new Result(msg, Double.NaN, Double.NaN);
    }
}
