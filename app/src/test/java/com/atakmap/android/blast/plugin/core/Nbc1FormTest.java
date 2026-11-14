package com.atakmap.android.blast.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.atakmap.android.blast.plugin.core.port.Magnetic;
import com.atakmap.android.blast.plugin.core.screen.Check;
import com.atakmap.android.blast.plugin.core.screen.FormValues;
import com.atakmap.android.blast.plugin.core.screen.Input;
import com.atakmap.android.blast.plugin.core.screen.Item;
import com.atakmap.android.blast.plugin.core.screen.Nbc1Form;
import com.atakmap.android.blast.plugin.core.screen.Screen;

import org.junit.Test;

/**
 * BRAVO with a compass bearing must not drift when the report is saved,
 * reopened and saved again (user report, Nov 14 2026: each save with
 * Magnetic ticked took the declination off again).
 */
public class Nbc1FormTest {

    static final Magnetic WEST_4_5 = new Magnetic() {
        @Override
        public double toTrue(double m) {
            return m - 4.5;
        }
    };

    static NucRecord incident() {
        return NucRecord.create(16, NucModel.Burst.SURFACE, "", 270, 10,
                "ALPHA-1", 1793544725000L).withNumber(1);
    }

    static Input bravo(Screen s) {
        for (Item it : s.items())
            if (it instanceof Input && Nbc1Form.AZ.equals(((Input) it).id))
                return (Input) it;
        throw new AssertionError("no BRAVO");
    }

    static Check magnetic(Screen s) {
        for (Item it : s.items())
            if (it instanceof Check)
                return (Check) it;
        throw new AssertionError("no Magnetic box");
    }

    @Test
    public void compassBearingIsStableAcrossSaves() {
        NucRecord r = incident();
        Nbc1Form.Entry last = null;
        for (int round = 0; round < 5; round++) {
            Screen s = Nbc1Form.form(r, Double.NaN, last);
            FormValues v = FormValues.initial(s);
            if (round == 0)
                v.put(Nbc1Form.AZ, "123").put(Nbc1Form.MAG, true);
            // The user opens and saves again without changing anything.
            NucRecord.Obs o = Nbc1Form.obs(v, 38, -84, WEST_4_5);
            assertEquals("round " + round, 118.5, o.azDeg, 1e-9);
            last = Nbc1Form.entry(v, o);
            r = r.withObs(o);
            if (round > 0) {
                assertEquals("123", bravo(s).value);
                assertTrue(magnetic(s).checked);
            }
        }
    }

    @Test
    public void withoutTheEntryTheSavedTrueBearingIsLabelled() {
        NucRecord r = incident().withObs(Nbc1Form.obs(new FormValues()
                .put(Nbc1Form.AZ, "123").put(Nbc1Form.MAG, true), 38, -84,
                WEST_4_5));
        Screen s = Nbc1Form.form(r, 200);
        assertEquals("119", bravo(s).value);
        assertTrue(bravo(s).label.endsWith("(saved as a true bearing)"));
        assertFalse(magnetic(s).checked);
        // Saving as shown keeps the true bearing (rounded as shown).
        assertEquals(119, Nbc1Form.obs(FormValues.initial(s), 38, -84,
                WEST_4_5).azDeg, 1e-9);
    }

    @Test
    public void staleEntryIsIgnored() {
        NucRecord r = incident();
        FormValues v = new FormValues().put(Nbc1Form.AZ, "123")
                .put(Nbc1Form.MAG, true);
        NucRecord.Obs o = Nbc1Form.obs(v, 38, -84, WEST_4_5);
        Nbc1Form.Entry e = Nbc1Form.entry(v, o);
        // Someone else's report replaced the bearing since.
        NucRecord other = r.withObs(Nbc1Form.obs(new FormValues().put(
                Nbc1Form.AZ, "90"), 38, -84, WEST_4_5));
        Screen s = Nbc1Form.form(other, Double.NaN, e);
        assertEquals("90", bravo(s).value);
        assertFalse(magnetic(s).checked);
    }

    @Test
    public void gpsPrefillWhenNothingSaved() {
        Screen s = Nbc1Form.form(incident(), 200.4);
        assertEquals("200", bravo(s).value);
        assertTrue(bravo(s).label.endsWith(
                "(filled from your GPS to ground zero, true)"));
        assertFalse(magnetic(s).checked);
    }
}
