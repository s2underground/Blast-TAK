package com.atakmap.android.blast.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.atakmap.android.blast.plugin.core.overlay.OverlayShape;
import com.atakmap.android.blast.plugin.core.overlay.Overlays;
import com.atakmap.android.blast.plugin.core.screen.Item;
import com.atakmap.android.blast.plugin.core.screen.Screen;
import com.atakmap.android.blast.plugin.core.screen.Text;
import com.atakmap.android.blast.plugin.core.screen.UxoCard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * What this build does with incidents from a newer Blast: a higher record
 * version, attributes it doesn't know, a card edition, ordnance type or
 * nuclear model it doesn't have, values it doesn't know. They must still
 * show and draw (from what the sender carried), and re-sending them here
 * must keep the newer attributes, so mixed versions keep working.
 */
public class FutureVersionsTest {

    private static final long T = 1_793_000_000_000L;

    private static Map<String, String> blast() {
        return new LinkedHashMap<>(BlastCodec.encode(BlastRecord.create(
                ZoneCalculator.forRow(StandoffTable.current(), "CAR"), "A",
                T)));
    }

    @Test
    public void blastFromANewerVersion() {
        Map<String, String> a = blast();
        a.put("v", "7");
        a.put("ed", "2031-01");
        a.put("row", "DRONE");
        a.put("lbl", "Drone");
        a.put("mand_ft", "400");
        a.put("sip_ft", "1999");
        a.put("pref_ft", "2000");
        a.put("st", "SUSPENDED");
        a.put("newthing", "x");
        BlastRecord r = BlastCodec.decode(a);
        assertNotNull(r);
        assertEquals(400, r.mandatoryFt);
        assertEquals(2000, r.preferredFt);
        assertEquals("Drone", r.rowLabel);
        // An unknown status shows as active (the safe reading).
        assertFalse(r.isCleared());
        assertFalse(Overlays.standoff(r).isEmpty());
        // Re-sent from here: the newer attributes travel on.
        Map<String, String> again = BlastCodec.encode(r.withNumber(3));
        assertEquals("x", again.get("newthing"));
        assertEquals("2031-01", again.get("ed"));
        assertEquals("2000", again.get("pref_ft"));
        assertNotNull(BlastCodec.decode(again));
    }

    @Test
    public void uxoTypeANewerBlastAdded() {
        Map<String, String> a = new LinkedHashMap<>(UxoCodec.encode(UxoRecord
                .create("PROJ_MORTAR", false, false, UxoRecord.Where.PATH,
                        UxoRecord.Placement.MAP, "A", T)));
        // Same ATP edition, a category this build doesn't have.
        a.put("cat", "LOITERING_MUNITION");
        a.put("lbl", "Loitering munition");
        a.put("cord_m", "450");
        a.put("where", "SOMEWHERE_NEW");
        UxoRecord r = UxoCodec.decode(a);
        assertNotNull("a new type must not be dropped", r);
        assertEquals(450, r.cordonM);
        assertEquals("Loitering munition", r.doctrineLabel());
        List<OverlayShape> shapes = Overlays.uxo(r);
        assertFalse(shapes.isEmpty());
        assertEquals(450, shapes.get(0).radiusM, 0);
        String card = text(UxoCard.card(r, "UXO 1", "911", "18S"));
        assertTrue(card, card.contains("doesn't have this type"));
        assertTrue(UxoText.basis(r).contains("Distance as sent"));
        Map<String, String> again = UxoCodec.encode(r);
        assertEquals("LOITERING_MUNITION", again.get("cat"));
        assertEquals("450", again.get("cord_m"));
    }

    @Test
    public void nuclearFromANewerModel() {
        Map<String, String> a = new LinkedHashMap<>(NucCodec.encode(NucRecord
                .create(10, NucModel.Burst.SURFACE, "", 270, 15, "A", T)));
        a.put("v", "4");
        a.put("mdl", "glasstone-2035");
        a.put("burst", "UNDERWATER");
        a.put("future", "1");
        NucRecord r = NucCodec.decode(a);
        assertNotNull(r);
        assertFalse(Overlays.nuc(r, 38.9, -77.0).isEmpty());
        Map<String, String> again = NucCodec.encode(r);
        assertEquals("glasstone-2035", again.get("mdl"));
        assertEquals("1", again.get("future"));
    }

    private static String text(Screen s) {
        StringBuilder b = new StringBuilder();
        for (Item it : s.items())
            if (it instanceof Text)
                b.append(((Text) it).text).append('\n');
        return b.toString();
    }
}
