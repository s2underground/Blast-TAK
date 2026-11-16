package com.atakmap.android.blast.plugin.core;

import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

/**
 * Golden-file helper. Files live in src/test/resources/golden/. To
 * regenerate after an intended change, run the tests with
 * {@code -Pblast.golden.update=true} (Gradle) or
 * {@code -Dblast.golden.update=true} (plain JUnit, from the project or
 * app folder) and review the diff before committing. The folder is fixed:
 * no path comes from outside the code.
 */
final class Golden {

    private Golden() {
    }

    /** Fixed-precision number, no locale, no negative zero. */
    static String num(double v, int places) {
        if (Double.isNaN(v))
            return "NaN";
        if (Double.isInfinite(v))
            return v > 0 ? "Inf" : "-Inf";
        String s = new BigDecimal(v).setScale(places, RoundingMode.HALF_EVEN)
                .stripTrailingZeros().toPlainString();
        return s.equals("-0") ? "0" : s;
    }

    static String ll(double lat, double lon) {
        return num(lat, 7) + "," + num(lon, 7);
    }

    static void check(String name, String actual) {
        if (Boolean.getBoolean("blast.golden.update")) {
            write(new File(goldenDir(), name), actual);
            return;
        }
        String expected = read("/golden/" + name);
        if (expected == null)
            fail("missing golden file " + name
                    + " (run with -Pblast.golden.update=true to create it)");
        if (expected.equals(actual))
            return;
        String[] e = expected.split("\n", -1), a = actual.split("\n", -1);
        int n = Math.min(e.length, a.length);
        for (int i = 0; i < n; i++)
            if (!e[i].equals(a[i]))
                fail(name + " differs at line " + (i + 1) + "\n  expected: "
                        + e[i] + "\n  actual:   " + a[i]);
        fail(name + " differs in length: expected " + e.length
                + " lines, got " + a.length);
    }

    private static String read(String resource) {
        try (InputStream in = Golden.class.getResourceAsStream(resource)) {
            if (in == null)
                return null;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0)
                bo.write(buf, 0, r);
            // Tolerate CRLF checkouts on Windows.
            return new String(bo.toByteArray(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    /** src/test/resources/golden, from the project or the app folder. */
    private static File goldenDir() {
        for (String base : new String[] { ".", "app" }) {
            File f = new File(base, "src/test/resources/golden");
            if (f.isDirectory())
                return f;
        }
        throw new AssertionError("golden folder not found");
    }

    private static void write(File f, String s) {
        f.getParentFile().mkdirs();
        try (OutputStream o = new FileOutputStream(f)) {
            o.write(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
