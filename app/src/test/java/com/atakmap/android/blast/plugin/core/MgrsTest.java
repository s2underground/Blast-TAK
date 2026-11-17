package com.atakmap.android.blast.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import org.junit.Test;

/**
 * The core's MGRS converter (for hosts without one: the desktop) against
 * reference values from NGA's GeoTrans 3.10 and ATAK 5.5's MGRSPoint
 * (mgrs-reference.txt), plus round trips and malformed input.
 */
public class MgrsTest {

    private static String plain(String s) {
        return s.replace(" ", "");
    }

    @Test
    public void matchesGeoTransAndAtak() throws Exception {
        InputStream in = getClass().getClassLoader()
                .getResourceAsStream("mgrs-reference.txt");
        BufferedReader r = new BufferedReader(new InputStreamReader(in,
                StandardCharsets.UTF_8));
        int n = 0, atak = 0;
        String line;
        while ((line = r.readLine()) != null) {
            if (line.startsWith("#") || line.trim().isEmpty())
                continue;
            String[] p = line.trim().split(" +");
            double lat = Double.parseDouble(p[0]),
                    lon = Double.parseDouble(p[1]);
            String ours = Mgrs.of(lat, lon);
            assertEquals(line, p[2], plain(ours));
            if (!p[3].equals("-")) {
                assertEquals(line, p[3], plain(ours));
                atak++;
            }
            // Its square's corner is the point, truncated: within 1.5 m.
            double[] back = Mgrs.parse(ours);
            assertTrue(line, metres(lat, lon, back) < 1.5);
            n++;
        }
        assertTrue(n > 200 && atak > 200);
    }

    @Test
    public void format() {
        assertEquals("16S GH 19064 13261", Mgrs.of(38.0406, -84.5037));
        assertEquals("16S GH 1906 1326", Mgrs.of(38.0406, -84.5037, 4));
        assertEquals("16S GH", Mgrs.of(38.0406, -84.5037, 0));
        assertEquals("05R ML 08564 05402", Mgrs.of(27.16820514717368,
                -153.9229367996846));
        assertEquals("31N AA 66021 00000", Mgrs.of(0, 0));
        // Norway and Svalbard zone exceptions.
        assertEquals("32V LN 07793 12209", Mgrs.of(60.5, 5.5));
        assertEquals("33X WG 13696 80760", Mgrs.of(78.2, 15.6));
        assertEquals("33X VP 41721 30624", Mgrs.of(84.0, 10));
        assertEquals("32C NS 19384 18247", Mgrs.of(-80, 10));
        // UPS (GeoTrans; ATAK has none and extends UTM instead).
        assertEquals("Z AB 96454 52981", Mgrs.of(85, 10));
        assertEquals("A ZT 03545 47018", Mgrs.of(-85, -10));
        assertEquals("Z AG 00000 98889", Mgrs.of(89.99, 0));
        assertEquals("B AM 00192 98906", Mgrs.of(-89.99, 170));
        assertEquals("Z AH 00000 00000", Mgrs.of(90, 0));
        assertEquals("B AN 00000 00000", Mgrs.of(-90, 0));
        // Longitudes wrap; 180 is zone 1 (ATAK says 61).
        assertEquals(Mgrs.of(10, -170), Mgrs.of(10, 190));
        assertEquals("01P AJ 69256 85503", Mgrs.of(8, 180));
    }

    @Test
    public void parse() {
        double[] p = Mgrs.parse("16S GH 19064 13261");
        assertTrue(metres(38.0406, -84.5037, p) < 1.5);
        // Spaces, case, ATAK's invisible marks and fewer digits.
        double[] q = Mgrs.parse("16sgh‎ 1906 1326 ");
        assertTrue(metres(38.0406, -84.5037, q) < 15);
        assertEquals(90, Mgrs.parse("ZAH0000000000")[0], 1e-9);
        assertEquals(-90, Mgrs.parse("B AN 00000 00000")[0], 1e-9);
        assertTrue(metres(-85, -10, Mgrs.parse("AZT0354547018")) < 1.5);
        // One-digit zones are accepted.
        assertEquals(Mgrs.parse("05RML0856405402")[0],
                Mgrs.parse("5RML0856405402")[0], 0);
    }

    @Test
    public void rejectsWhatIsNotAReference() {
        String[] bad = { null, "", "16S", "16SGH123", "16SGH12345678901",
                "61SGH1906413261", "00SGH1906413261", "0BGS",
                "16IGH1906413261", "16SGI1906413261", "16SGO1906413261",
                "16SGH19O6413261", "ZDA", "YAA", "CAA",
                // squares outside their band or zone
                "26VNG40889516", "32XLL4799877", "31VFL56", "29DRV7481",
                "56GNM00" };
        for (String s : bad) {
            try {
                Mgrs.parse(s);
                fail(s);
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void roundTripsEverywhere() {
        Random rnd = new Random(42);
        for (int i = 0; i < 20000; i++) {
            double lat = i < 2000 ? (rnd.nextBoolean() ? 1 : -1) * (80
                    + 10 * rnd.nextDouble()) : -90 + 180 * rnd.nextDouble();
            double lon = -180 + 360 * rnd.nextDouble();
            String ref = Mgrs.of(lat, lon);
            double[] back = Mgrs.parse(ref);
            assertTrue(ref, metres(lat, lon, back) < 1.5);
            // The inverse is exact: the corner maps back onto the
            // reference's own easting and northing to a micrometre.
            String[] parts = ref.split(" ");
            double[] en = lat > 84 || lat < -80 ? Mgrs.upsForward(back[0],
                    back[1]) : Mgrs.utmForward(back[0], back[1],
                            Integer.parseInt(parts[0].substring(0, 2)));
            assertEquals(ref, 0, offset(en[0], parts[2]), 1e-6);
            assertEquals(ref, 0, offset(en[1], parts[3]), 1e-6);
        }
    }

    @Test
    public void invalidPositions() {
        double[][] bad = { { 91, 0 }, { -90.1, 0 }, { Double.NaN, 0 },
                { 0, Double.NaN }, { 0, Double.POSITIVE_INFINITY } };
        for (double[] b : bad) {
            try {
                Mgrs.of(b[0], b[1]);
                fail(b[0] + "," + b[1]);
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    /** How far a coordinate is from the given 5 digits, mod 100 km. */
    private static double offset(double v, String digits) {
        double d = (v - Long.parseLong(digits)) % 100000;
        if (d > 50000)
            d -= 100000;
        if (d < -50000)
            d += 100000;
        return d;
    }

    private static double metres(double lat, double lon, double[] p) {
        double dy = (lat - p[0]) * 111320;
        double dl = ((lon - p[1]) % 360 + 540) % 360 - 180;
        double dx = dl * 111320 * Math.cos(Math.toRadians(lat));
        return Math.sqrt(dx * dx + dy * dy);
    }
}
