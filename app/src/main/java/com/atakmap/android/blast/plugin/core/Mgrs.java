package com.atakmap.android.blast.plugin.core;

import java.util.Locale;

/**
 * MGRS on WGS 84, both ways, for hosts without their own (the desktop):
 * UTM between 80°S and 84°N (with the Norway and Svalbard zone
 * exceptions) and UPS at the poles, as NGA's GeoTrans does. Digits are
 * truncated, not rounded (MGRS convention), so a reference names the
 * square the point is in. Checked against GeoTrans and, within UTM, ATAK.
 *
 * <p>Transverse Mercator uses Krüger's series to sixth order in n
 * (Karney 2011), accurate to well under a millimetre within a zone.
 */
public final class Mgrs {

    private Mgrs() {
    }

    // WGS 84
    private static final double A = 6378137.0;
    private static final double F = 1 / 298.257223563;
    private static final double E2 = F * (2 - F);
    private static final double E = Math.sqrt(E2);
    private static final double N = F / (2 - F);

    private static final double K0_UTM = 0.9996, K0_UPS = 0.994;
    private static final double A_RECT;
    private static final double[] ALPHA = new double[7], BETA = new double[7];

    static {
        double n = N, n2 = n * n, n3 = n2 * n, n4 = n3 * n, n5 = n4 * n,
                n6 = n5 * n;
        A_RECT = A / (1 + n) * (1 + n2 / 4 + n4 / 64 + n6 / 256);
        ALPHA[1] = n / 2 - 2 * n2 / 3 + 5 * n3 / 16 + 41 * n4 / 180
                - 127 * n5 / 288 + 7891 * n6 / 37800;
        ALPHA[2] = 13 * n2 / 48 - 3 * n3 / 5 + 557 * n4 / 1440
                + 281 * n5 / 630 - 1983433 * n6 / 1935360;
        ALPHA[3] = 61 * n3 / 240 - 103 * n4 / 140 + 15061 * n5 / 26880
                + 167603 * n6 / 181440;
        ALPHA[4] = 49561 * n4 / 161280 - 179 * n5 / 168
                + 6601661 * n6 / 7257600;
        ALPHA[5] = 34729 * n5 / 80640 - 3418889 * n6 / 1995840;
        ALPHA[6] = 212378941 * n6 / 319334400;
        BETA[1] = n / 2 - 2 * n2 / 3 + 37 * n3 / 96 - n4 / 360
                - 81 * n5 / 512 + 96199 * n6 / 604800;
        BETA[2] = n2 / 48 + n3 / 15 - 437 * n4 / 1440 + 46 * n5 / 105
                - 1118711 * n6 / 3870720;
        BETA[3] = 17 * n3 / 480 - 37 * n4 / 840 - 209 * n5 / 4480
                + 5569 * n6 / 90720;
        BETA[4] = 4397 * n4 / 161280 - 11 * n5 / 504
                - 830251 * n6 / 7257600;
        BETA[5] = 4583 * n5 / 161280 - 108847 * n6 / 3991680;
        BETA[6] = 20648693 * n6 / 638668800;
    }

    /** Latitude bands C..X, 8° each from 80°S (X is 12°: 72–84°N). */
    private static final String BANDS = "CDEFGHJKLMNPQRSTUVWX";
    /** 100 km column letters by UTM zone set (zone % 3). */
    private static final String[] COLS = { "STUVWXYZ", "ABCDEFGH",
            "JKLMNPQR" };
    /** 100 km row letters (cycle of 20; even zones start 5 later). */
    private static final String ROWS = "ABCDEFGHJKLMNPQRSTUV";

    // ------------------------------------------------------------------
    // To MGRS

    /**
     * The MGRS reference of a point, 1 m digits, parts separated by single
     * spaces, the zone in two digits as ATAK and GeoTrans write it:
     * "16S GH 19064 13261", "05R ML 08564 05402"; polar: "Z AH 00000
     * 00000". Throws on
     * an invalid latitude / longitude.
     */
    public static String of(double lat, double lon) {
        return of(lat, lon, 5);
    }

    /** As {@link #of(double, double)} with 0–5 digits per coordinate. */
    public static String of(double lat, double lon, int digits) {
        if (!(lat >= -90 && lat <= 90) || Double.isNaN(lon)
                || Double.isInfinite(lon))
            throw new IllegalArgumentException("invalid position");
        if (digits < 0 || digits > 5)
            throw new IllegalArgumentException("digits 0-5");
        lon = wrap(lon);
        if (lat > 84 || lat < -80)
            return ups(lat, lon, digits);
        int zone = zone(lat, lon);
        double[] en = utmForward(lat, lon, zone);
        char band = BANDS.charAt(Math.min(19, (int) Math.floor((lat + 80)
                / 8)));
        long e = (long) Math.floor(en[0]);
        long n = (long) Math.floor(en[1]);
        int col = (int) (e / 100000);
        int set = zone % 3;
        char c1 = COLS[set].charAt(col - 1);
        int row = (int) ((n / 100000) % 20);
        if (zone % 2 == 0)
            row = (row + 5) % 20;
        char c2 = ROWS.charAt(row);
        return (zone < 10 ? "0" : "") + zone + band + " " + c1 + c2 + digits(e % 100000,
                n % 100000, digits);
    }

    /** UTM zone, with the Norway and Svalbard exceptions. */
    static int zone(double lat, double lon) {
        int z = (int) Math.floor((lon + 180) / 6) + 1;
        if (z > 60)
            z = 60;
        if (lat >= 56 && lat < 64 && lon >= 3 && lon < 12)
            return 32;
        if (lat >= 72 && lat <= 84 && lon >= 0 && lon < 42) {
            if (lon < 9)
                return 31;
            if (lon < 21)
                return 33;
            if (lon < 33)
                return 35;
            return 37;
        }
        return z;
    }

    private static String digits(long e, long n, int digits) {
        if (digits == 0)
            return "";
        long div = 1;
        for (int i = digits; i < 5; i++)
            div *= 10;
        String fmt = "%0" + digits + "d";
        return " " + String.format(Locale.US, fmt, e / div) + " "
                + String.format(Locale.US, fmt, n / div);
    }

    private static double wrap(double lon) {
        double l = lon % 360;
        if (l >= 180)
            l -= 360;
        if (l < -180)
            l += 360;
        return l;
    }

    /** Easting, northing in a UTM zone (false northing in the south). */
    static double[] utmForward(double lat, double lon, int zone) {
        double phi = Math.toRadians(lat);
        double lam = Math.toRadians(lon - (zone * 6 - 183));
        double sinPhi = Math.sin(phi);
        double t = Math.sinh(atanh(sinPhi) - E * atanh(E * sinPhi));
        double xiP = Math.atan2(t, Math.cos(lam));
        double etaP = atanh(Math.sin(lam) / Math.sqrt(1 + t * t));
        double xi = xiP, eta = etaP;
        for (int j = 1; j <= 6; j++) {
            xi += ALPHA[j] * Math.sin(2 * j * xiP) * Math.cosh(2 * j * etaP);
            eta += ALPHA[j] * Math.cos(2 * j * xiP) * Math.sinh(2 * j * etaP);
        }
        double easting = 500000 + K0_UTM * A_RECT * eta;
        double northing = K0_UTM * A_RECT * xi;
        if (lat < 0)
            northing += 10000000;
        return new double[] { easting, northing };
    }

    /** Latitude, longitude from UTM (southern = false northing added). */
    static double[] utmInverse(double easting, double northing, int zone,
            boolean south) {
        double xi = (northing - (south ? 10000000 : 0)) / (K0_UTM * A_RECT);
        double eta = (easting - 500000) / (K0_UTM * A_RECT);
        double xiP = xi, etaP = eta;
        for (int j = 1; j <= 6; j++) {
            xiP -= BETA[j] * Math.sin(2 * j * xi) * Math.cosh(2 * j * eta);
            etaP -= BETA[j] * Math.cos(2 * j * xi) * Math.sinh(2 * j * eta);
        }
        double tauP = Math.sin(xiP) / Math.sqrt(Math.sinh(etaP)
                * Math.sinh(etaP) + Math.cos(xiP) * Math.cos(xiP));
        double lam = Math.atan2(Math.sinh(etaP), Math.cos(xiP));
        double tau = conformalToGeodetic(tauP);
        return new double[] { Math.toDegrees(Math.atan(tau)),
                wrap(zone * 6 - 183 + Math.toDegrees(lam)) };
    }

    /** tan(latitude) from tan(conformal latitude) (Karney's Newton). */
    private static double conformalToGeodetic(double tauP) {
        double tau = tauP;
        for (int i = 0; i < 10; i++) {
            double s1 = Math.sqrt(1 + tau * tau);
            double sigma = Math.sinh(E * atanh(E * tau / s1));
            double tauI = tau * Math.sqrt(1 + sigma * sigma) - sigma * s1;
            double d = (tauP - tauI) / Math.sqrt(1 + tauI * tauI)
                    * (1 + (1 - E2) * tau * tau) / ((1 - E2) * s1);
            tau += d;
            if (Math.abs(d) < 1e-14)
                break;
        }
        return tau;
    }

    private static double atanh(double x) {
        return 0.5 * Math.log((1 + x) / (1 - x));
    }

    // ------------------------------------------------------------------
    // UPS

    /**
     * UPS 100 km letters, as GeoTrans: zones A (south, west of 0°), B
     * (south, east), Y (north, west), Z (north, east). Columns west of the
     * pole run J..Z from easting 800 km, east of it A..R from 2,000 km;
     * rows A..Z from northing 800 km (south) or 1,300 km (north); I and O
     * are never used, nor D, E, M, N, V, W as columns.
     */
    private static final String UPS_WEST = "JKLPQRSTUXYZ",
            UPS_EAST = "ABCFGHJKLPQR",
            UPS_ROWS = "ABCDEFGHJKLMNPQRSTUVWXYZ";

    private static final double UPS_C = Math.sqrt(Math.pow(1 + E, 1 + E)
            * Math.pow(1 - E, 1 - E));

    private static String ups(double lat, double lon, int digits) {
        boolean north = lat > 0;
        double[] en = upsForward(lat, lon);
        long e = (long) Math.floor(en[0]);
        long n = (long) Math.floor(en[1]);
        boolean east = e >= 2000000;
        char zone = north ? (east ? 'Z' : 'Y') : (east ? 'B' : 'A');
        String cols = east ? UPS_EAST : UPS_WEST;
        int col = (int) ((e - (east ? 2000000 : 800000)) / 100000);
        int row = (int) ((n - (north ? 1300000 : 800000)) / 100000);
        return zone + " " + cols.charAt(col) + UPS_ROWS.charAt(row)
                + digits(e % 100000, n % 100000, digits);
    }

    static double[] upsForward(double lat, double lon) {
        boolean north = lat > 0;
        double phi = Math.toRadians(Math.abs(lat));
        double lam = Math.toRadians(lon);
        double s = Math.sin(phi);
        double t = Math.tan(Math.PI / 4 - phi / 2)
                / Math.pow((1 - E * s) / (1 + E * s), E / 2);
        double rho = 2 * A * K0_UPS * t / UPS_C;
        double easting = 2000000 + rho * Math.sin(lam);
        double northing = north ? 2000000 - rho * Math.cos(lam)
                : 2000000 + rho * Math.cos(lam);
        return new double[] { easting, northing };
    }

    static double[] upsInverse(double easting, double northing,
            boolean north) {
        double dx = easting - 2000000, dy = northing - 2000000;
        double rho = Math.sqrt(dx * dx + dy * dy);
        double t = rho * UPS_C / (2 * A * K0_UPS);
        double phi = Math.PI / 2 - 2 * Math.atan(t);
        for (int i = 0; i < 20; i++) {
            double s = Math.sin(phi);
            double next = Math.PI / 2 - 2 * Math.atan(t * Math.pow((1 - E
                    * s) / (1 + E * s), E / 2));
            boolean done = Math.abs(next - phi) < 1e-14;
            phi = next;
            if (done)
                break;
        }
        double lam = rho == 0 ? 0 : north ? Math.atan2(dx, -dy)
                : Math.atan2(dx, dy);
        double lat = Math.toDegrees(phi);
        return new double[] { north ? lat : -lat, wrap(Math.toDegrees(
                lam)) };
    }

    // ------------------------------------------------------------------
    // From MGRS

    /**
     * The south-west corner of an MGRS square, as {lat, lon}. Spaces and
     * case don't matter: "16S GH 1906 1326", "16sgh19061326". Throws
     * IllegalArgumentException for anything that isn't a valid reference.
     */
    public static double[] parse(String ref) {
        if (ref == null)
            throw new IllegalArgumentException("no reference");
        String s = ref.replaceAll("[\\s\\u200E\\u200F\\u00A0]", "")
                .toUpperCase(Locale.ROOT);
        if (s.length() > 20)
            throw new IllegalArgumentException("too long");
        int i = 0;
        while (i < s.length() && i < 2 && Character.isDigit(s.charAt(i)))
            i++;
        int zone = i == 0 ? 0 : Integer.parseInt(s.substring(0, i));
        if (s.length() < i + 3)
            throw new IllegalArgumentException("too short");
        char band = s.charAt(i), c1 = s.charAt(i + 1), c2 = s.charAt(i + 2);
        String num = s.substring(i + 3);
        if (num.length() % 2 != 0 || num.length() > 10
                || !num.matches("[0-9]*"))
            throw new IllegalArgumentException("digits");
        int d = num.length() / 2;
        long scale = 1;
        for (int k = d; k < 5; k++)
            scale *= 10;
        long e = d == 0 ? 0 : Long.parseLong(num.substring(0, d)) * scale;
        long n = d == 0 ? 0 : Long.parseLong(num.substring(d)) * scale;
        if (i == 0)
            return parseUps(band, c1, c2, e, n, scale);
        if (zone < 1 || zone > 60)
            throw new IllegalArgumentException("zone");
        int b = BANDS.indexOf(band);
        int col = COLS[zone % 3].indexOf(c1);
        int row = ROWS.indexOf(c2);
        if (b < 0 || col < 0 || row < 0)
            throw new IllegalArgumentException("letters");
        if (zone % 2 == 0)
            row = (row + 15) % 20;
        double easting = (col + 1) * 100000.0 + e;
        double base = row * 100000.0 + n;
        boolean south = b < 10;
        double bandLo = -80 + 8 * b, bandHi = b == 19 ? 84 : bandLo + 8;
        // The row letter repeats every 2,000 km: take the square that
        // overlaps the band (only one can: bands are at most 12° tall).
        double size = scale;
        for (int k = 0; k < 6; k++) {
            double northing = base + k * 2000000.0;
            // Past 84°N / below 80°S the zone has no squares (and the
            // inverse would wrap over the pole).
            if (south ? northing < 1000000 || northing >= 10000000
                    : northing >= 9400000)
                continue;
            double[] sw = utmInverse(easting, northing, zone, south);
            double[] se = utmInverse(easting + size, northing, zone, south);
            double[] nw = utmInverse(easting, northing + size, zone, south);
            double[] ne = utmInverse(easting + size, northing + size, zone,
                    south);
            double lo = Math.min(sw[0], se[0]), hi = Math.max(nw[0], ne[0]);
            if (!(hi > bandLo && lo < bandHi))
                continue;
            // ...and reaches into its own zone (the Norway and Svalbard
            // exceptions make some zone and band pairs smaller or absent).
            double[][] corners = { sw, se, nw, ne };
            for (double[] c : corners)
                if (zone(Math.max(bandLo, Math.min(c[0], bandHi - 1e-9)),
                        c[1]) == zone)
                    return sw;
            throw new IllegalArgumentException("not in its zone");
        }
        throw new IllegalArgumentException("not in its band");
    }

    private static double[] parseUps(char zone, char c1, char c2, long e,
            long n, long scale) {
        boolean north = zone == 'Y' || zone == 'Z';
        boolean east = zone == 'B' || zone == 'Z';
        if (!north && zone != 'A' && zone != 'B')
            throw new IllegalArgumentException("zone");
        int col = (east ? UPS_EAST : UPS_WEST).indexOf(c1);
        int row = UPS_ROWS.indexOf(c2);
        if (col < 0 || row < 0 || north && row > UPS_ROWS.indexOf('P'))
            throw new IllegalArgumentException("letters");
        double easting = (east ? 2000000 : 800000) + col * 100000.0 + e;
        double northing = (north ? 1300000 : 800000) + row * 100000.0 + n;
        // The square must reach into its polar cap: test its point
        // nearest the pole.
        double size = scale;
        double ne = Math.max(easting, Math.min(easting + size, 2000000));
        double nn = Math.max(northing, Math.min(northing + size, 2000000));
        double nearest = upsInverse(ne, nn, north)[0];
        if (north ? nearest <= 84 : nearest >= -80)
            throw new IllegalArgumentException("not polar");
        return upsInverse(easting, northing, north);
    }
}
