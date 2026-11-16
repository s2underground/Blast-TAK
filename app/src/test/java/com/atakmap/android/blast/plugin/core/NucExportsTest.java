package com.atakmap.android.blast.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;


public class NucExportsTest {

    private static int count(String s, String sub) {
        int n = 0;
        for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + 1))
            n++;
        return n;
    }

    private static void parses(String xml) throws Exception {
        SafeXml.parse(xml, false);
    }

    @Test
    public void kmlHasRingsFalloutAndPoint() throws Exception {
        NucRecord r = NucRecord.create(10, NucModel.Burst.SURFACE, "", 270, 15,
                "A & B", 1793544725000L).withNumber(1);
        String kml = Exports.kmlNuc("NUC 1 <10 kt>", 38.9, -77.0, r);
        parses(kml);
        assertEquals(NucText.Ring.values().length
                + NucModel.FALLOUT_RATES.length, count(kml, "<Polygon>"));
        assertEquals(1, count(kml, "<Point>"));
        assertEquals("ff1c1cb7", Exports.kmlColor(0xFFB71C1C));
        // Fallout goes east of ground zero (wind from the west).
        double[][] fo = NucGeo.fallout(r, 10, 38.9, -77.0);
        double maxLon = -180;
        for (double[] p : fo)
            maxLon = Math.max(maxLon, p[1]);
        assertTrue(maxLon > -77.0 + 1.0);
        String air = Exports.kmlNuc("x", 38.9, -77.0, NucRecord.create(10,
                NucModel.Burst.AIR, "", 270, 15, "", 0));
        assertEquals(NucText.Ring.values().length, count(air, "<Polygon>"));
    }

    @Test
    public void gpxHasTracks() throws Exception {
        NucRecord r = NucRecord.create(100, NucModel.Burst.UNKNOWN, "", 90, 20,
                "A", 1793544725000L);
        String gpx = Exports.gpxNuc("NUC 2", 51.5, -0.1, r, 1793544725000L);
        parses(gpx);
        assertEquals(NucText.Ring.values().length
                + NucModel.FALLOUT_RATES.length, count(gpx, "<trk>"));
        assertEquals(1, count(gpx, "<wpt "));
    }

    /**
     * A cleared incident's KML is grey like the map: rings the map draws
     * unfilled (burn, 100 rad, Get Inside) stay unfilled; the damage rings
     * keep a grey fill.
     */
    @Test
    public void clearedKmlMatchesTheMap() throws Exception {
        NucRecord r = NucRecord.create(10, NucModel.Burst.SURFACE, "", 270, 15,
                "A", 1793544725000L).cleared("B", 1793544785000L);
        String kml = Exports.kmlNuc("NUC 1", 38.9, -77.0, r);
        parses(kml);
        String grey = Exports.kmlColor(com.atakmap.android.blast.plugin.core
                .overlay.Overlays.GREY_FILL);
        java.util.List<com.atakmap.android.blast.plugin.core.overlay
                .OverlayShape> rings = com.atakmap.android.blast.plugin.core
                        .overlay.Overlays.nucRings(r);
        NucText.Ring[] all = NucText.Ring.values();
        assertEquals(all.length, rings.size());
        for (int i = 0; i < all.length; i++) {
            int a = kml.indexOf("<Style id=\"ring" + i + "\">");
            String style = kml.substring(a, kml.indexOf("</Style>", a));
            boolean mapFilled = !rings.get(i).style
                    .noFill();
            String fill = style.substring(style.indexOf("<PolyStyle>"));
            assertEquals("ring" + i + " " + style, mapFilled,
                    fill.contains(grey));
        }
    }
}
