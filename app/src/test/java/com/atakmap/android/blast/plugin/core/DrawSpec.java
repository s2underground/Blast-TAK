package com.atakmap.android.blast.plugin.core;

import com.atakmap.android.blast.plugin.core.overlay.OverlayShape;
import com.atakmap.android.blast.plugin.core.overlay.Overlays;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes what an incident draws (the core overlay model) in the golden
 * snapshot's text form: uid suffix, kind, radius or points, ARGB colours,
 * line style, weight and title. Until refactor step 3 this transcribed
 * atak/BlastStore by hand; the golden files are unchanged, which proves the
 * overlay model draws exactly what 1.1 drew.
 */
final class DrawSpec {

    private DrawSpec() {
    }

    static String marker(String name) {
        return "marker type=a-h-G title=" + name;
    }

    static List<String> standoff(BlastRecord r) {
        return dump(Overlays.standoff(r));
    }

    static List<String> uxo(UxoRecord r) {
        return dump(Overlays.uxo(r));
    }

    static List<String> nuc(NucRecord r, double lat, double lon) {
        return dump(Overlays.nuc(r, lat, lon));
    }

    static List<String> dump(List<OverlayShape> shapes) {
        List<String> l = new ArrayList<>();
        for (OverlayShape s : shapes) {
            StringBuilder sb = new StringBuilder(s.id);
            if (s.kind == OverlayShape.Kind.CIRCLE) {
                sb.append(" circle radius_m=").append(Golden.num(s.radiusM,
                        3));
            } else {
                sb.append(" shape closed=").append(s.isClosed())
                        .append(" points=").append(s.pointCount());
            }
            sb.append(String.format(Locale.US,
                    " stroke=%08X fill=%08X line=%s weight=%s\n    title=%s",
                    s.style.strokeArgb, s.style.fillArgb,
                    s.style.line.name().toLowerCase(Locale.US),
                    Golden.num(s.style.width, 1), s.title));
            for (int i = 0; i < s.pointCount(); i++)
                sb.append("\n    ").append(Golden.ll(s.lat(i), s.lon(i)));
            l.add(sb.toString());
        }
        return l;
    }
}
