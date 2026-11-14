package com.atakmap.android.blast.plugin.core.overlay;

import com.atakmap.android.blast.plugin.core.BlastRecord;
import com.atakmap.android.blast.plugin.core.Labels;
import com.atakmap.android.blast.plugin.core.NucGeo;
import com.atakmap.android.blast.plugin.core.NucModel;
import com.atakmap.android.blast.plugin.core.NucRecord;
import com.atakmap.android.blast.plugin.core.NucReports;
import com.atakmap.android.blast.plugin.core.NucText;
import com.atakmap.android.blast.plugin.core.StandoffRow;
import com.atakmap.android.blast.plugin.core.UxoRecord;
import com.atakmap.android.blast.plugin.core.UxoText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What each kind of incident draws: every part's id suffix, geometry, title
 * and style. The ATAK layer (and later TAK-X) only renders these lists, so
 * the drawing rules live in one place for every host.
 */
public final class Overlays {

    private Overlays() {
    }

    // Standoff (CISA/FBI card)
    public static final int RED = 0xFFD50000, RED_FILL = 0x55D50000;
    public static final int GREEN = 0xFF00C853, AMBER_FILL = 0x33FFAB00;
    // Cleared incidents
    public static final int GREY = 0xFF9E9E9E, GREY_FILL = 0x149E9E9E;
    /** UXO cordon: dark orange, distinct from the IED card's colours. */
    public static final int ORANGE = 0xFFE65100, ORANGE_FILL = 0x40E65100;
    /** Nuclear rings: FEMA's red / orange / yellow zones. */
    public static final int NUC_SDZ = 0xFFB71C1C, NUC_SDZ_FILL = 0x40B71C1C;
    public static final int NUC_MDZ = 0xFFFF6D00, NUC_MDZ_FILL = 0x30FF6D00;
    public static final int NUC_LDZ = 0xFFFFD600, NUC_LDZ_FILL = 0x1AFFD600;
    public static final int NUC_BURN = 0xFF00B8D4;
    public static final int NUC_RAD = 0xFF536DFE;
    public static final int NUC_GI = 0xFFE0E0E0;
    public static final int NO_FILL = 0x00000000;

    public static final String MAND = ".mand";
    public static final String PREF = ".pref";
    public static final String CORDON = ".cordon";

    /** Every part id any incident can have (for cleanup and lookups). */
    public static final List<String> ALL_IDS;
    /** The nuclear parts that depend on ground zero and wind. */
    public static final List<String> NUC_OUTLINE_IDS;

    static {
        List<String> o = new ArrayList<>();
        for (int rate : NucModel.FALLOUT_RATES)
            o.add(NucText.falloutSuffix(rate));
        o.add(NucText.zoneSuffix(1));
        o.add(NucText.zoneSuffix(2));
        for (int h = 1; h <= NucText.MAX_ARCS; h++)
            o.add(NucText.arcSuffix(h));
        NUC_OUTLINE_IDS = Collections.unmodifiableList(o);
        List<String> l = new ArrayList<>();
        l.add(PREF);
        l.add(MAND);
        l.add(CORDON);
        for (NucText.Ring r : NucText.Ring.values())
            l.add(r.suffix);
        l.addAll(o);
        ALL_IDS = Collections.unmodifiableList(l);
    }

    /** The card's mandatory and preferred rings. */
    public static List<OverlayShape> standoff(BlastRecord r) {
        List<OverlayShape> l = new ArrayList<>();
        StandoffRow row = r.row();
        boolean grey = r.isCleared();
        l.add(OverlayShape.circle(MAND, row.mandatoryRadiusM(),
                Labels.mandatoryRing(r), new Style(grey ? GREY : RED,
                        grey ? GREY_FILL : RED_FILL, 3, Style.Line.DEFAULT)));
        l.add(OverlayShape.circle(PREF, row.preferredRadiusM(),
                Labels.preferredRing(r), new Style(grey ? GREY : GREEN,
                        grey ? GREY_FILL : AMBER_FILL, 4,
                        Style.Line.DEFAULT)));
        return l;
    }

    /** The UXO cordon. */
    public static List<OverlayShape> uxo(UxoRecord r) {
        boolean grey = r.isCleared();
        return Collections.singletonList(OverlayShape.circle(CORDON,
                r.cordonM, UxoText.ringTitle(r), new Style(grey ? GREY
                        : ORANGE, grey ? GREY_FILL : ORANGE_FILL, 4,
                        Style.Line.DEFAULT)));
    }

    /** Nuclear rings centred on ground zero (they follow the marker). */
    public static List<OverlayShape> nucRings(NucRecord r) {
        List<OverlayShape> l = new ArrayList<>();
        boolean grey = r.isCleared();
        for (NucText.Ring ring : NucText.Ring.values()) {
            int stroke, fill;
            Style.Line line = Style.Line.SOLID;
            double weight = 3;
            switch (ring) {
                case SDZ:
                    stroke = NUC_SDZ;
                    fill = NUC_SDZ_FILL;
                    break;
                case MDZ:
                    stroke = NUC_MDZ;
                    fill = NUC_MDZ_FILL;
                    break;
                case LDZ:
                    stroke = NUC_LDZ;
                    fill = NUC_LDZ_FILL;
                    break;
                case BURN:
                    stroke = NUC_BURN;
                    fill = NO_FILL;
                    line = Style.Line.DASHED;
                    break;
                case RAD:
                    stroke = NUC_RAD;
                    fill = NO_FILL;
                    break;
                default:
                    stroke = NUC_GI;
                    fill = NO_FILL;
                    line = Style.Line.DOTTED;
                    weight = 2;
            }
            if (grey) {
                stroke = GREY;
                fill = fill == NO_FILL ? NO_FILL : GREY_FILL;
            }
            l.add(OverlayShape.circle(ring.suffix, ring.radiusM(r),
                    NucText.ringTitle(r, ring), new Style(stroke, fill,
                            weight, line)));
        }
        return l;
    }

    /**
     * Fallout bands, military zones and arrival arcs for ground zero at
     * (lat, lon). Parts that don't apply (no wind, air burst, civil vs
     * military picture) are simply absent; hosts remove any part of
     * {@link #NUC_OUTLINE_IDS} not in the list.
     */
    public static List<OverlayShape> nucOutlines(NucRecord r, double lat,
            double lon) {
        List<OverlayShape> l = new ArrayList<>();
        boolean grey = r.isCleared();
        int[] rates = NucModel.FALLOUT_RATES;
        for (int i = 0; i < rates.length; i++)
            add(l, NucText.falloutSuffix(rates[i]),
                    NucGeo.fallout(r, rates[i], lat, lon), true,
                    NucText.falloutTitle(r, rates[i]), new Style(
                            grey ? GREY : NucText.FALLOUT_STROKE[i],
                            grey ? GREY_FILL : NucText.FALLOUT_FILL[i], 2,
                            Style.Line.SOLID));
        for (int z = 1; z <= 2; z++)
            add(l, NucText.zoneSuffix(z), NucGeo.zone(r, z, lat, lon), true,
                    NucReports.zoneTitle(r, z), new Style(
                            grey ? GREY : z == 1 ? NucText.ZONE_I_STROKE
                                    : NucText.ZONE_II_STROKE,
                            grey ? GREY_FILL : z == 1 ? NucText.ZONE_I_FILL
                                    : NucText.ZONE_II_FILL, 3,
                            Style.Line.SOLID));
        for (int h = 1; h <= NucText.MAX_ARCS; h++)
            add(l, NucText.arcSuffix(h), NucGeo.arrivalArc(r, h, lat, lon),
                    false, NucReports.arcTitle(r, h), new Style(
                            grey ? GREY : NucText.ARC_STROKE, NO_FILL, 2,
                            Style.Line.DASHED));
        return l;
    }

    /** Everything a nuclear incident draws, rings first. */
    public static List<OverlayShape> nuc(NucRecord r, double lat, double lon) {
        List<OverlayShape> l = new ArrayList<>(nucRings(r));
        l.addAll(nucOutlines(r, lat, lon));
        return l;
    }

    /** Outlines need at least two points; otherwise the part is absent. */
    private static void add(List<OverlayShape> l, String id, double[][] ll,
            boolean closed, String title, Style style) {
        if (ll == null || ll.length < 2)
            return;
        l.add(OverlayShape.outline(id, ll, closed, title, style));
    }
}
