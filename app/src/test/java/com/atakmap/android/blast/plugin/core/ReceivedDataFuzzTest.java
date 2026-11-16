package com.atakmap.android.blast.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.atakmap.android.blast.plugin.core.app.BlastApp;
import com.atakmap.android.blast.plugin.core.overlay.OverlayShape;
import com.atakmap.android.blast.plugin.core.overlay.Overlays;
import com.atakmap.android.blast.plugin.core.screen.Item;
import com.atakmap.android.blast.plugin.core.screen.Screen;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Audit (Nov 16, 2026): records other devices send reach the whole core
 * (cards, overlays, remarks, exports, the CoT TAK-X sends) and, through
 * TAK-X and the desktop, Swing screens. Real records are encoded, their
 * attributes mutated (bad, huge, non-finite, missing, hostile values),
 * decoded, and every record that decodes goes through the core: nothing
 * may throw, geometry must be finite and bounded, text bounded, and
 * every XML the core writes must parse. Fixed seed: the same cases run
 * every time.
 */
public class ReceivedDataFuzzTest {

    private static final long T = GoldenSnapshotTest.T;
    private static final int ROUNDS = 1500;

    /** Values a hostile or broken sender might put in an attribute. */
    private static final String[] VALUES = { "", " ", "NaN", "Infinity",
            "-Infinity", "1e308", "-1e308", "4.9e-324", "-1", "0", "-0",
            "1", "2147483647", "2147483648", "-2147483649",
            "99999999999999999999", "0x10", "1,5", "12abc", "<", "&amp;",
            "'\"<>&", "<html><img src='http://x/y.png'>", "\u0000\u0007",
            "￿\uD800", "ACTIVE", "CLEARED", "SURFACE", "AIR", "PATH",
            "MAP", "RB", "FEMA_10", "CISA-BTSOC", "2025-08",
            "2026-11-01T14:52:05.000Z", "9999-99-99T99:99:99Z", "x",
            repeat('A', 5000), repeat('9', 400) };

    /** Names a hostile or broken sender might give an incident. */
    private static final String[] NAMES = { "BLAST 1 Car", "",
            "<html><img src='http://x/y.png'>", "a&b\"'<>]]>", "\u0001\u001f",
            repeat('N', 3000), "   ", "名前 ☢" };

    /** Positions, including near the poles and the date line. */
    private static final double[][] AT = { { 38.0406, -84.5037 },
            { 89.999, 179.999 }, { -89.999, -179.999 }, { 0, 0 },
            { 51.5, 179.95 } };

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++)
            sb.append(c);
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Samples

    private static List<BlastRecord> standoffs() {
        StandoffTable t = StandoffTable.current();
        List<BlastRecord> l = new ArrayList<>();
        for (String row : new String[] { "PIPE_BOMB", "CAR" }) {
            BlastRecord r = BlastRecord.create(ZoneCalculator.forRow(t, row),
                    "ALPHA-1", T).withNumber(3);
            l.add(r);
            l.add(r.cleared("BRAVO-2", T + 60000));
        }
        return l;
    }

    private static List<UxoRecord> uxos() {
        List<UxoRecord> l = new ArrayList<>();
        String[] cats = { "GREN_MINE_SUBMUN", "PROJ_MORTAR", "MISSILE_ROCKET",
                "LARGE_GUIDED_MISSILE", "BOMB_DISPENSER", "STOCKPILE" };
        UxoRecord.Where[] where = UxoRecord.Where.values();
        UxoRecord.Placement[] plc = UxoRecord.Placement.values();
        for (int i = 0; i < cats.length; i++) {
            UxoRecord r = UxoRecord.create(cats[i], i % 2 == 0, i % 3 == 0,
                    where[i % where.length], plc[i % plc.length], "ALPHA-1",
                    T).withNumber(i + 1);
            l.add(i % 2 == 0 ? r : r.cleared("BRAVO-2", T + 60000));
        }
        return l;
    }

    private static List<NucRecord> nucs() {
        List<NucRecord> l = new ArrayList<>();
        NucRecord a = NucRecord.create(10, NucModel.Burst.SURFACE, "FEMA_10",
                270, 15, "ALPHA-1", T).withNumber(1);
        l.add(a);
        l.add(a.withMil(a.mil.withOn(true)));
        l.add(a.cleared("BRAVO-2", T + 60000));
        l.add(NucRecord.create(0.1, NucModel.Burst.SURFACE, "", Double.NaN,
                Double.NaN, "ALPHA-1", T).withNumber(2));
        for (NucModel.Burst b : NucModel.Burst.values())
            l.add(NucRecord.create(500, b, "", 45, 40, "ALPHA-1", T)
                    .withNumber(4));
        return l;
    }

    // ------------------------------------------------------------------
    // Mutation

    private static Map<String, String> mutate(Map<String, String> a,
            Random rnd) {
        Map<String, String> m = new LinkedHashMap<>(a);
        List<String> keys = new ArrayList<>(m.keySet());
        int changes = 1 + rnd.nextInt(3);
        for (int i = 0; i < changes; i++) {
            int what = rnd.nextInt(10);
            if (what == 0 && !keys.isEmpty())
                m.remove(keys.get(rnd.nextInt(keys.size())));
            else if (what == 1)
                m.put("zz" + rnd.nextInt(5), VALUES[rnd.nextInt(
                        VALUES.length)]);
            else if (!keys.isEmpty())
                m.put(keys.get(rnd.nextInt(keys.size())), VALUES[rnd
                        .nextInt(VALUES.length)]);
        }
        return m;
    }

    private static String xml(String element, Map<String, String> a) {
        StringBuilder sb = new StringBuilder("<").append(element);
        for (Map.Entry<String, String> e : a.entrySet())
            sb.append(' ').append(e.getKey()).append("=\"")
                    .append(BlastCodec.escape(e.getValue())).append('"');
        return sb.append("/>").toString();
    }

    // ------------------------------------------------------------------
    // Checks on everything the core makes from a record

    private static void shapes(String what, List<OverlayShape> l) {
        assertTrue(what + ": " + l.size() + " shapes", l.size() <= 60);
        for (OverlayShape s : l) {
            assertNotNull(what, s.id);
            if (s.kind == OverlayShape.Kind.CIRCLE) {
                assertTrue(what + " radius " + s.radiusM, s.radiusM > 0
                        && s.radiusM < 2.1e7);
            } else {
                int n = s.pointCount();
                assertTrue(what + " points " + n, n >= 2 && n <= 5000);
                for (int i = 0; i < n; i++) {
                    double la = s.lat(i), lo = s.lon(i);
                    assertTrue(what + " point " + la + "," + lo,
                            la >= -90 && la <= 90 && !Double.isNaN(lo)
                                    && !Double.isInfinite(lo));
                }
            }
        }
    }

    private static void text(String what, String s) {
        assertNotNull(what, s);
        assertTrue(what + " length " + s.length(), s.length() < 40000);
    }

    private static void xmlOk(String what, String xml) {
        try {
            SafeXml.parse(xml, false);
        } catch (Exception e) {
            fail(what + " is not well-formed XML (" + e.getMessage() + "): "
                    + (xml.length() > 600 ? xml.substring(0, 600) : xml));
        }
    }

    private static void screen(String what, Screen s) {
        if (s == null)
            return;
        String d = ScreenDump.dump(s);
        text(what, d);
        for (Item it : s.items())
            assertNotNull(what, it);
    }

    /** One incident through the app: list, card and its live lines. */
    private static void app(String uid, String name, double lat, double lon,
            BlastRecord s, UxoRecord u, NucRecord n) {
        ActionsSnapshot.World w = new ActionsSnapshot.World(
                new StringBuilder());
        w.map.put(uid, new com.atakmap.android.blast.plugin.core.port
                .Incidents.Incident(uid, name, name, lat, lon, s, u, n));
        w.self = new double[] { lat + 0.001, lon };
        BlastApp app = new BlastApp(w, w, w, w, w, w, w, w, w);
        app.select(uid);
        BlastApp.Frame f = app.frame();
        String what = "app " + name;
        screen(what, f.header);
        screen(what, f.card);
        screen(what, f.list);
    }

    private static void everything(String what, String name, double lat,
            double lon, BlastRecord s, UxoRecord u, NucRecord n) {
        CotEvents.Sender me = new CotEvents.Sender("TEST-node", "ALPHA-1");
        List<CotEvents.Event> ev;
        if (s != null) {
            shapes(what, Overlays.standoff(s));
            text(what, Labels.remarks(s));
            ev = CotEvents.incident("u", name, lat, lon, s, me, T, true);
            Exports.Incident in = new Exports.Incident(name, lat, lon, s);
            xmlOk(what + " kml", Exports.kml(in));
            xmlOk(what + " gpx", Exports.gpx(in, T));
        } else if (u != null) {
            shapes(what, Overlays.uxo(u));
            text(what, UxoText.remarks(u));
            ev = CotEvents.incident("u", name, lat, lon, u, me, T, true);
            xmlOk(what + " kml", Exports.kmlUxo(name, lat, lon, u));
            xmlOk(what + " gpx", Exports.gpxUxo(name, lat, lon, u, T));
        } else {
            shapes(what, Overlays.nuc(n, lat, lon));
            text(what, NucText.remarks(n));
            ev = CotEvents.incident("u", name, lat, lon, n, me, T, true);
            xmlOk(what + " kml", Exports.kmlNuc(name, lat, lon, n));
            xmlOk(what + " gpx", Exports.gpxNuc(name, lat, lon, n, T));
        }
        for (CotEvents.Event e : ev)
            xmlOk(what + " CoT " + e.uid, e.xml);
        app("u", name, lat, lon, s, u, n);
    }

    // ------------------------------------------------------------------

    private interface Kind<R> {
        Map<String, String> encode(R r);

        R decode(String xml);

        String toXml(R r);

        void run(String what, String name, double lat, double lon, R r);
    }

    private static <R> int fuzz(String element, List<R> samples, Kind<R> k,
            long seed) {
        Random rnd = new Random(seed);
        int decoded = 0;
        for (int i = 0; i < ROUNDS; i++) {
            R sample = samples.get(rnd.nextInt(samples.size()));
            String in = xml(element, mutate(k.encode(sample), rnd));
            R r;
            try {
                r = k.decode(in);
            } catch (RuntimeException e) {
                throw new AssertionError("decoding threw on " + in, e);
            }
            if (r == null)
                continue;
            decoded++;
            String once = k.toXml(r);
            assertEquals("re-encoding is stable for " + in, once,
                    k.toXml(k.decode(once)));
            double[] at = AT[rnd.nextInt(AT.length)];
            String name = NAMES[rnd.nextInt(NAMES.length)];
            try {
                k.run(element + " #" + i, name, at[0], at[1], r);
            } catch (RuntimeException e) {
                throw new AssertionError("the core threw on " + in
                        + " named [" + name + "] at " + at[0] + "," + at[1],
                        e);
            }
        }
        return decoded;
    }

    @Test
    public void standoffRecordsFromOtherDevices() {
        int n = fuzz(BlastCodec.DETAIL, standoffs(), new Kind<BlastRecord>() {
            @Override
            public Map<String, String> encode(BlastRecord r) {
                return BlastCodec.encode(r);
            }

            @Override
            public BlastRecord decode(String xml) {
                return BlastCodec.fromXml(xml);
            }

            @Override
            public String toXml(BlastRecord r) {
                return BlastCodec.toXml(r);
            }

            @Override
            public void run(String what, String name, double lat, double lon,
                    BlastRecord r) {
                everything(what, name, lat, lon, r, null, null);
            }
        }, 1);
        assertTrue("too few mutations decoded: " + n, n > ROUNDS / 4);
    }

    @Test
    public void uxoRecordsFromOtherDevices() {
        int n = fuzz(UxoCodec.DETAIL, uxos(), new Kind<UxoRecord>() {
            @Override
            public Map<String, String> encode(UxoRecord r) {
                return UxoCodec.encode(r);
            }

            @Override
            public UxoRecord decode(String xml) {
                return UxoCodec.fromXml(xml);
            }

            @Override
            public String toXml(UxoRecord r) {
                return UxoCodec.toXml(r);
            }

            @Override
            public void run(String what, String name, double lat, double lon,
                    UxoRecord r) {
                everything(what, name, lat, lon, null, r, null);
            }
        }, 2);
        assertTrue("too few mutations decoded: " + n, n > ROUNDS / 4);
    }

    @Test
    public void nuclearRecordsFromOtherDevices() {
        int n = fuzz(NucCodec.DETAIL, nucs(), new Kind<NucRecord>() {
            @Override
            public Map<String, String> encode(NucRecord r) {
                return NucCodec.encode(r);
            }

            @Override
            public NucRecord decode(String xml) {
                return NucCodec.fromXml(xml);
            }

            @Override
            public String toXml(NucRecord r) {
                return NucCodec.toXml(r);
            }

            @Override
            public void run(String what, String name, double lat, double lon,
                    NucRecord r) {
                everything(what, name, lat, lon, null, null, r);
            }
        }, 3);
        assertTrue("too few mutations decoded: " + n, n > ROUNDS / 4);
    }
}
