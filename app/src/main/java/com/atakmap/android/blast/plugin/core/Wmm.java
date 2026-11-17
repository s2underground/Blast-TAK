package com.atakmap.android.blast.plugin.core;

/**
 * The World Magnetic Model 2025 (NOAA NCEI and the British Geological
 * Survey; public domain), for hosts without a declination model of their
 * own (the desktop). Valid 2025.0 to 2030.0; outside that it extrapolates,
 * as ATAK's and Android's models do. Accuracy is the model's (declination
 * typically within half a degree away from the magnetic poles).
 */
public final class Wmm {

    private Wmm() {
    }

    private static final double EPOCH = 2025.0;
    private static final int N = 12;
    /** Geomagnetic reference radius, km. */
    private static final double RE = 6371.2;
    /** WGS 84, km. */
    private static final double A = 6378.137, F = 1 / 298.257223563,
            E2 = F * (2 - F);

    /** n, m, g, h (nT), g', h' (nT / year): WMM2025.COF. */
    private static final double[] COF = {
            1, 0, -29351.8, 0.0, 12.0, 0.0,
            1, 1, -1410.8, 4545.4, 9.7, -21.5,
            2, 0, -2556.6, 0.0, -11.6, 0.0,
            2, 1, 2951.1, -3133.6, -5.2, -27.7,
            2, 2, 1649.3, -815.1, -8.0, -12.1,
            3, 0, 1361.0, 0.0, -1.3, 0.0,
            3, 1, -2404.1, -56.6, -4.2, 4.0,
            3, 2, 1243.8, 237.5, 0.4, -0.3,
            3, 3, 453.6, -549.5, -15.6, -4.1,
            4, 0, 895.0, 0.0, -1.6, 0.0,
            4, 1, 799.5, 278.6, -2.4, -1.1,
            4, 2, 55.7, -133.9, -6.0, 4.1,
            4, 3, -281.1, 212.0, 5.6, 1.6,
            4, 4, 12.1, -375.6, -7.0, -4.4,
            5, 0, -233.2, 0.0, 0.6, 0.0,
            5, 1, 368.9, 45.4, 1.4, -0.5,
            5, 2, 187.2, 220.2, 0.0, 2.2,
            5, 3, -138.7, -122.9, 0.6, 0.4,
            5, 4, -142.0, 43.0, 2.2, 1.7,
            5, 5, 20.9, 106.1, 0.9, 1.9,
            6, 0, 64.4, 0.0, -0.2, 0.0,
            6, 1, 63.8, -18.4, -0.4, 0.3,
            6, 2, 76.9, 16.8, 0.9, -1.6,
            6, 3, -115.7, 48.8, 1.2, -0.4,
            6, 4, -40.9, -59.8, -0.9, 0.9,
            6, 5, 14.9, 10.9, 0.3, 0.7,
            6, 6, -60.7, 72.7, 0.9, 0.9,
            7, 0, 79.5, 0.0, -0.0, 0.0,
            7, 1, -77.0, -48.9, -0.1, 0.6,
            7, 2, -8.8, -14.4, -0.1, 0.5,
            7, 3, 59.3, -1.0, 0.5, -0.8,
            7, 4, 15.8, 23.4, -0.1, 0.0,
            7, 5, 2.5, -7.4, -0.8, -1.0,
            7, 6, -11.1, -25.1, -0.8, 0.6,
            7, 7, 14.2, -2.3, 0.8, -0.2,
            8, 0, 23.2, 0.0, -0.1, 0.0,
            8, 1, 10.8, 7.1, 0.2, -0.2,
            8, 2, -17.5, -12.6, 0.0, 0.5,
            8, 3, 2.0, 11.4, 0.5, -0.4,
            8, 4, -21.7, -9.7, -0.1, 0.4,
            8, 5, 16.9, 12.7, 0.3, -0.5,
            8, 6, 15.0, 0.7, 0.2, -0.6,
            8, 7, -16.8, -5.2, -0.0, 0.3,
            8, 8, 0.9, 3.9, 0.2, 0.2,
            9, 0, 4.6, 0.0, -0.0, 0.0,
            9, 1, 7.8, -24.8, -0.1, -0.3,
            9, 2, 3.0, 12.2, 0.1, 0.3,
            9, 3, -0.2, 8.3, 0.3, -0.3,
            9, 4, -2.5, -3.3, -0.3, 0.3,
            9, 5, -13.1, -5.2, 0.0, 0.2,
            9, 6, 2.4, 7.2, 0.3, -0.1,
            9, 7, 8.6, -0.6, -0.1, -0.2,
            9, 8, -8.7, 0.8, 0.1, 0.4,
            9, 9, -12.9, 10.0, -0.1, 0.1,
            10, 0, -1.3, 0.0, 0.1, 0.0,
            10, 1, -6.4, 3.3, 0.0, 0.0,
            10, 2, 0.2, 0.0, 0.1, -0.0,
            10, 3, 2.0, 2.4, 0.1, -0.2,
            10, 4, -1.0, 5.3, -0.0, 0.1,
            10, 5, -0.6, -9.1, -0.3, -0.1,
            10, 6, -0.9, 0.4, 0.0, 0.1,
            10, 7, 1.5, -4.2, -0.1, 0.0,
            10, 8, 0.9, -3.8, -0.1, -0.1,
            10, 9, -2.7, 0.9, -0.0, 0.2,
            10, 10, -3.9, -9.1, -0.0, -0.0,
            11, 0, 2.9, 0.0, 0.0, 0.0,
            11, 1, -1.5, 0.0, -0.0, -0.0,
            11, 2, -2.5, 2.9, 0.0, 0.1,
            11, 3, 2.4, -0.6, 0.0, -0.0,
            11, 4, -0.6, 0.2, 0.0, 0.1,
            11, 5, -0.1, 0.5, -0.1, -0.0,
            11, 6, -0.6, -0.3, 0.0, -0.0,
            11, 7, -0.1, -1.2, -0.0, 0.1,
            11, 8, 1.1, -1.7, -0.1, -0.0,
            11, 9, -1.0, -2.9, -0.1, 0.0,
            11, 10, -0.2, -1.8, -0.1, 0.0,
            11, 11, 2.6, -2.3, -0.1, 0.0,
            12, 0, -2.0, 0.0, 0.0, 0.0,
            12, 1, -0.2, -1.3, 0.0, -0.0,
            12, 2, 0.3, 0.7, -0.0, 0.0,
            12, 3, 1.2, 1.0, -0.0, -0.1,
            12, 4, -1.3, -1.4, -0.0, 0.1,
            12, 5, 0.6, -0.0, -0.0, -0.0,
            12, 6, 0.6, 0.6, 0.1, -0.0,
            12, 7, 0.5, -0.1, -0.0, -0.0,
            12, 8, -0.1, 0.8, 0.0, 0.0,
            12, 9, -0.4, 0.1, 0.0, -0.0,
            12, 10, -0.2, -1.0, -0.1, -0.0,
            12, 11, -1.3, 0.1, -0.0, 0.0,
            12, 12, -0.7, 0.2, -0.1, -0.1
    };

    private static final double[][] G = new double[N + 1][N + 1],
            H = new double[N + 1][N + 1], GD = new double[N + 1][N + 1],
            HD = new double[N + 1][N + 1];

    static {
        // Schmidt semi-normalisation folded into the coefficients, so the
        // Legendre functions below can stay Gauss-normalised.
        double[][] s = new double[N + 1][N + 1];
        s[0][0] = 1;
        for (int n = 1; n <= N; n++) {
            s[n][0] = s[n - 1][0] * (2 * n - 1) / n;
            for (int m = 1; m <= n; m++)
                s[n][m] = s[n][m - 1] * Math.sqrt((n - m + 1)
                        * (m == 1 ? 2.0 : 1.0) / (n + m));
        }
        for (int i = 0; i < COF.length; i += 6) {
            int n = (int) COF[i], m = (int) COF[i + 1];
            G[n][m] = COF[i + 2] * s[n][m];
            H[n][m] = COF[i + 3] * s[n][m];
            GD[n][m] = COF[i + 4] * s[n][m];
            HD[n][m] = COF[i + 5] * s[n][m];
        }
    }

    /**
     * Magnetic declination in degrees, east positive (true = magnetic +
     * declination), at a WGS 84 position and height (metres above the
     * ellipsoid) and a date as a decimal year (2026.5 = early July 2026).
     */
    public static double declination(double lat, double lon,
            double heightM, double year) {
        double[] f = field(lat, lon, heightM, year);
        return Math.toDegrees(Math.atan2(f[1], f[0]));
    }

    /**
     * The field's north, east and down components (X, Y, Z) in nT.
     */
    static double[] field(double lat, double lon, double heightM,
            double year) {
        if (!(lat >= -90 && lat <= 90) || Double.isNaN(lon)
                || Double.isInfinite(lon) || Double.isNaN(heightM)
                || Double.isInfinite(heightM) || Double.isNaN(year)
                || Double.isInfinite(year))
            throw new IllegalArgumentException("invalid position or date");
        // Keep clear of the poles, where east and north are undefined.
        lat = Math.max(-89.99999, Math.min(89.99999, lat));
        double phi = Math.toRadians(lat), lam = Math.toRadians(lon);
        double h = heightM / 1000;
        double sp = Math.sin(phi), cp = Math.cos(phi);
        double rc = A / Math.sqrt(1 - E2 * sp * sp);
        double p = (rc + h) * cp, z = (rc * (1 - E2) + h) * sp;
        double r = Math.sqrt(p * p + z * z);
        double phiC = Math.asin(z / r);
        double dt = year - EPOCH;

        // Gauss-normalised Legendre functions of cos(colatitude) and
        // their derivatives by colatitude.
        double ct = Math.sin(phiC), st = Math.cos(phiC);
        double[][] pn = new double[N + 1][N + 1], dp = new double[N + 1][N
                + 1];
        pn[0][0] = 1;
        for (int n = 1; n <= N; n++)
            for (int m = 0; m <= n; m++) {
                if (n == m) {
                    pn[n][m] = st * pn[n - 1][m - 1];
                    dp[n][m] = ct * pn[n - 1][m - 1] + st * dp[n - 1][m - 1];
                } else if (n == 1 || m == n - 1) {
                    pn[n][m] = ct * pn[n - 1][m];
                    dp[n][m] = -st * pn[n - 1][m] + ct * dp[n - 1][m];
                } else {
                    double k = ((n - 1) * (n - 1) - m * m)
                            / (double) ((2 * n - 1) * (2 * n - 3));
                    pn[n][m] = ct * pn[n - 1][m] - k * pn[n - 2][m];
                    dp[n][m] = -st * pn[n - 1][m] + ct * dp[n - 1][m]
                            - k * dp[n - 2][m];
                }
            }

        double x = 0, y = 0, zz = 0;
        double ar = RE / r;
        double arn = ar * ar; // (a/r)^(n+2) starts at n = 0
        for (int n = 1; n <= N; n++) {
            arn *= ar;
            for (int m = 0; m <= n; m++) {
                double g = G[n][m] + dt * GD[n][m];
                double hh = H[n][m] + dt * HD[n][m];
                double cm = Math.cos(m * lam), sm = Math.sin(m * lam);
                double c = g * cm + hh * sm;
                x += arn * c * dp[n][m];
                y += arn * m * (g * sm - hh * cm) * pn[n][m];
                zz -= (n + 1) * arn * c * pn[n][m];
            }
        }
        y /= st;
        // Geocentric to geodetic.
        double d = phi - phiC;
        double xg = x * Math.cos(d) + zz * Math.sin(d);
        double zg = -x * Math.sin(d) + zz * Math.cos(d);
        return new double[] { xg, y, zg };
    }

    /**
     * A UTC time in milliseconds as a decimal year (for
     * {@link #declination}).
     */
    public static double decimalYear(long utcMillis) {
        long days = utcMillis / 86400000L;
        if (days * 86400000L > utcMillis)
            days--;
        double dayFrac = (utcMillis - days * 86400000L) / 86400000.0;
        int y = yearOf(days);
        long start = daysFromCivil(y, 1, 1), end = daysFromCivil(y + 1, 1,
                1);
        return y + (days - start + dayFrac) / (end - start);
    }

    private static int yearOf(long days) {
        // Hinnant's civil_from_days, year only.
        long z = days + 719468;
        long era = (z >= 0 ? z : z - 146096) / 146097;
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long m = mp < 10 ? mp + 3 : mp - 9;
        return (int) (yoe + era * 400 + (m <= 2 ? 1 : 0));
    }

    private static long daysFromCivil(int y, int m, int d) {
        y -= m <= 2 ? 1 : 0;
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }
}
