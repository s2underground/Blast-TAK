package com.atakmap.android.blast.plugin.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Test-side transcription of what {@code atak/BlastStore} draws on the map
 * for each kind of incident (Blast 1.1, commit cc74a04): one line per map
 * item with its uid suffix, kind, geometry, title and style.
 *
 * <p>This is the reference the core overlay model (refactor step 3) must
 * reproduce exactly. Keep it in step with BlastStore until then; after step
 * 3 the golden files stay and this class is replaced by the core model.</p>
 */
final class DrawSpec {

    // BlastStore colours (ARGB).
    static final int RED = 0xFFD50000, RED_FILL = 0x55D50000;
    static final int GREEN = 0xFF00C853, AMBER_FILL = 0x33FFAB00;
    static final int GREY = 0xFF9E9E9E, GREY_FILL = 0x149E9E9E;
    static final int ORANGE = 0xFFE65100, ORANGE_FILL = 0x40E65100;
    static final int NUC_SDZ = 0xFFB71C1C, NUC_SDZ_FILL = 0x40B71C1C;
    static final int NUC_MDZ = 0xFFFF6D00, NUC_MDZ_FILL = 0x30FF6D00;
    static final int NUC_LDZ = 0xFFFFD600, NUC_LDZ_FILL = 0x1AFFD600;
    static final int NUC_BURN = 0xFF00B8D4;
    static final int NUC_RAD = 0xFF536DFE;
    static final int NUC_GI = 0xFFE0E0E0;
    static final int NO_FILL = 0x00000000;

    /** Line styles: "default" means BlastStore never sets one. */
    static final String DEFAULT = "default", SOLID = "solid",
            DASHED = "dashed", DOTTED = "dotted";

    private DrawSpec() {
    }

    static String marker(String name) {
        return "marker type=a-h-G title=" + name;
    }

    static List<String> standoff(BlastRecord r) {
        List<String> l = new ArrayList<>();
        StandoffRow row = r.row();
        boolean grey = r.isCleared();
        l.add(circle(".mand", row.mandatoryRadiusM(), Labels.mandatoryRing(r),
                grey ? GREY : RED, grey ? GREY_FILL : RED_FILL, DEFAULT, 3));
        l.add(circle(".pref", row.preferredRadiusM(), Labels.preferredRing(r),
                grey ? GREY : GREEN, grey ? GREY_FILL : AMBER_FILL, DEFAULT,
                4));
        return l;
    }

    static List<String> uxo(UxoRecord r) {
        List<String> l = new ArrayList<>();
        boolean grey = r.isCleared();
        l.add(circle(".cordon", r.cordonM, UxoText.ringTitle(r),
                grey ? GREY : ORANGE, grey ? GREY_FILL : ORANGE_FILL, DEFAULT,
                4));
        return l;
    }

    static List<String> nuc(NucRecord r, double lat, double lon) {
        List<String> l = new ArrayList<>();
        boolean grey = r.isCleared();
        for (NucText.Ring ring : NucText.Ring.values()) {
            int stroke, fill;
            String line = SOLID;
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
                    line = DASHED;
                    break;
                case RAD:
                    stroke = NUC_RAD;
                    fill = NO_FILL;
                    break;
                default:
                    stroke = NUC_GI;
                    fill = NO_FILL;
                    line = DOTTED;
                    weight = 2;
            }
            if (grey) {
                stroke = GREY;
                fill = fill == NO_FILL ? NO_FILL : GREY_FILL;
            }
            l.add(circle(ring.suffix, ring.radiusM(r), NucText.ringTitle(r,
                    ring), stroke, fill, line, weight));
        }
        int[] rates = NucModel.FALLOUT_RATES;
        for (int i = 0; i < rates.length; i++)
            add(l, NucText.falloutSuffix(rates[i]),
                    NucGeo.fallout(r, rates[i], lat, lon), true,
                    NucText.falloutTitle(r, rates[i]),
                    grey ? GREY : NucText.FALLOUT_STROKE[i],
                    grey ? GREY_FILL : NucText.FALLOUT_FILL[i], SOLID, 2);
        for (int z = 1; z <= 2; z++)
            add(l, NucText.zoneSuffix(z), NucGeo.zone(r, z, lat, lon), true,
                    NucReports.zoneTitle(r, z),
                    grey ? GREY : z == 1 ? NucText.ZONE_I_STROKE
                            : NucText.ZONE_II_STROKE,
                    grey ? GREY_FILL : z == 1 ? NucText.ZONE_I_FILL
                            : NucText.ZONE_II_FILL, SOLID, 3);
        for (int h = 1; h <= NucText.MAX_ARCS; h++)
            add(l, NucText.arcSuffix(h), NucGeo.arrivalArc(r, h, lat, lon),
                    false, NucReports.arcTitle(r, h),
                    grey ? GREY : NucText.ARC_STROKE, NO_FILL, DASHED, 2);
        return l;
    }

    /** BlastStore.shape(): no item when there are fewer than 2 points. */
    private static void add(List<String> l, String suffix, double[][] ll,
            boolean closed, String title, int stroke, int fill, String line,
            double weight) {
        if (ll == null || ll.length < 2)
            return;
        StringBuilder sb = new StringBuilder();
        sb.append(suffix).append(" shape closed=").append(closed)
                .append(" points=").append(ll.length)
                .append(style(title, stroke, fill, line, weight));
        for (double[] p : ll)
            sb.append("\n    ").append(Golden.ll(p[0], p[1]));
        l.add(sb.toString());
    }

    private static String circle(String suffix, double radiusM, String title,
            int stroke, int fill, String line, double weight) {
        return suffix + " circle radius_m=" + Golden.num(radiusM, 3)
                + style(title, stroke, fill, line, weight);
    }

    private static String style(String title, int stroke, int fill,
            String line, double weight) {
        return String.format(Locale.US,
                " stroke=%08X fill=%08X line=%s weight=%s\n    title=%s",
                stroke, fill, line, Golden.num(weight, 1), title);
    }
}
