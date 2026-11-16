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
}
