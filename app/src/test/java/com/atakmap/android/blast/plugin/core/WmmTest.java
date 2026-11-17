package com.atakmap.android.blast.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * The core's World Magnetic Model 2025 against NOAA's published test values
 * (WMM2025testvalues.pdf, NCEI, 2025).
 */
public class WmmTest {

    /** year, height km, lat, lon, X, Y, Z (nT), D (deg). */
    private static final double[][] NOAA = {
            { 2025.0, 0, 80, 0, 6521.6, 145.9, 54791.5, 1.28 },
            { 2025.0, 0, 0, 120, 39677.8, -109.6, -10580.2, -0.16 },
            { 2025.0, 0, -80, 240, 6117.5, 15751.9, -52022.5, 68.78 },
            { 2025.0, 100, 80, 0, 6216.0, 92.4, 52598.8, 0.85 },
            { 2025.0, 100, 0, 120, 37688.6, -96.2, -10152.1, -0.15 },
            { 2025.0, 100, -80, 240, 5907.6, 14780.3, -49540.7, 68.21 },
            { 2027.5, 0, 80, 0, 6500.8, 294.5, 54869.4, 2.59 },
            { 2027.5, 0, 0, 120, 39701.6, -167.4, -10381.8, -0.24 },
            { 2027.5, 0, -80, 240, 6200.7, 15730.3, -51783.7, 68.49 },
            { 2027.5, 100, 80, 0, 6196.7, 233.8, 52670.5, 2.16 },
            { 2027.5, 100, 0, 120, 37711.5, -148.7, -9969.8, -0.23 },
            { 2027.5, 100, -80, 240, 5984.0, 14760.1, -49317.7, 67.93 },
    };

    @Test
    public void noaaTestValues() {
        for (double[] t : NOAA) {
            String at = t[0] + " " + t[1] + "km " + t[2] + "," + t[3];
            double[] f = Wmm.field(t[2], t[3], t[1] * 1000, t[0]);
            assertEquals(at, t[4], f[0], 0.11);
            assertEquals(at, t[5], f[1], 0.11);
            assertEquals(at, t[6], f[2], 0.11);
            assertEquals(at, t[7], Wmm.declination(t[2], t[3], t[1] * 1000,
                    t[0]), 0.006);
        }
    }

    @Test
    public void decimalYear() {
        assertEquals(2025.0, Wmm.decimalYear(1735689600000L), 1e-12);
        assertEquals(2027.5, Wmm.decimalYear(1814529600000L), 1e-12);
        // 2028 is a leap year: 1 July 00:00 is day 182 of 366.
        assertEquals(2028 + 182 / 366.0, Wmm.decimalYear(1846022400000L),
                1e-12);
        assertEquals(1969 + 364.5 / 365, Wmm.decimalYear(-43200000L),
                1e-12);
    }

    @Test
    public void invalidInput() {
        double[][] bad = { { 91, 0, 0, 2026 }, { Double.NaN, 0, 0, 2026 },
                { 0, Double.NaN, 0, 2026 }, { 0, 0, Double.NaN, 2026 },
                { 0, 0, 0, Double.POSITIVE_INFINITY } };
        for (double[] b : bad) {
            try {
                Wmm.declination(b[0], b[1], b[2], b[3]);
                fail();
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }
}
