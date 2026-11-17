package com.atakmap.android.blast.plugin.core;

import static org.junit.Assert.assertEquals;

import com.atakmap.android.blast.plugin.core.app.BlastApp;

import org.junit.Test;

/**
 * Audit: without MGRS (a desktop, a host error) grid references fall back
 * to latitude / longitude to 5 places, as 1.1 wrote them, and never in
 * exponent form near the equator or the Greenwich meridian.
 */
public class GridFallbackTest {

    @Test
    public void latLonFallback() {
        ActionsSnapshot.World w = new ActionsSnapshot.World(
                new StringBuilder());
        w.mgrsFails = true;
        BlastApp app = new BlastApp(w, w, w, w, w, w, w, w, w);
        assertEquals("38.0406, -84.5037", app.grid(38.0406, -84.5037));
        assertEquals("51.47788, -0.0005", app.grid(51.477881, -0.0005));
        assertEquals("0.00012, 0.0", app.grid(0.00012, 0));
        assertEquals("-0.00001, 179.99999", app.grid(-0.00001, 179.99999));
        assertEquals("", app.grid(Double.NaN, 1));
    }

    /**
     * ATAK's own MGRS text has an invisible LEFT-TO-RIGHT MARK after each
     * part (as CoordinateFormatUtilities writes it): cards and copied
     * reports get plain text, and the 10 m reference is found.
     */
    @Test
    public void atakMgrsText() {
        ActionsSnapshot.World w = new ActionsSnapshot.World(
                new StringBuilder());
        w.mgrsText = "16S\u200E GH\u200E 19064\u200E 13261";
        BlastApp app = new BlastApp(w, w, w, w, w, w, w, w, w);
        assertEquals("16S GH 19064 13261", app.grid(38.0406, -84.5037));
        assertEquals("16S GH 1906 1326", app.mgrs10(38.0406, -84.5037));
        w.mgrsText = " 16S\u00A0 GH  19064\t13261 ";
        assertEquals("16S GH 19064 13261", app.grid(38.0406, -84.5037));
        assertEquals("16S GH 1906 1326", app.mgrs10(38.0406, -84.5037));
        w.mgrsText = "33XVQ5140741846"; // not split into parts: left as is
        assertEquals("33XVQ5140741846", app.mgrs10(85, 10));
    }
}
