package com.atakmap.android.blast.plugin.core;

import com.atakmap.android.blast.plugin.core.overlay.OverlayShape;
import com.atakmap.android.blast.plugin.core.overlay.Overlays;
import com.atakmap.android.blast.plugin.core.overlay.Style;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * The CoT events an incident is shared as, for hosts that send CoT
 * themselves (TAK-X): the same events ATAK Blast's data packages and
 * broadcasts carry (ATAK writes them with its own CoT exporters): the
 * marker ({@code a-h-G} with the Blast detail and remarks) first, then
 * each ring ({@code u-d-c-c}, anchored to the marker) and outline
 * ({@code u-d-f}); and ATAK's remote-delete event ({@code t-x-d-d}).
 * Receivers with Blast rebuild everything from the marker's detail;
 * receivers without it draw the shapes.
 */
public final class CotEvents {

    private CotEvents() {
    }

    /** The incident marker's CoT type (as ATAK Blast's). */
    public static final String MARKER_TYPE = "a-h-G";
    public static final String CIRCLE_TYPE = "u-d-c-c",
            OUTLINE_TYPE = "u-d-f", DELETE_TYPE = "t-x-d-d";
    /** The sender's own contact type (as ATAK writes for its user). */
    static final String SELF_TYPE = "a-f-G-U-C";
    /** CotPoint's "unknown" for hae, ce and le. */
    static final String UNKNOWN = "9999999.0";

    private static final long DAY_MS = 24L * 3600 * 1000;
    private static final long YEAR_MS = 365L * DAY_MS;

    /** Who is sending: the device's uid and callsign. */
    public static final class Sender {
        public final String uid, callsign;

        public Sender(String uid, String callsign) {
            this.uid = uid;
            this.callsign = callsign;
        }
    }

    /** One event and what a delete for it needs. */
    public static final class Event {
        public final String uid, type, xml;

        Event(String uid, String type, String xml) {
            this.uid = uid;
            this.type = type;
            this.xml = xml;
        }
    }

    // ------------------------------------------------------------------
    // Incidents

    public static List<Event> incident(String uid, String name, double lat,
            double lon, BlastRecord r, Sender from, long nowMs,
            boolean labels) {
        return events(uid, name, lat, lon, BlastCodec.toXml(r),
                Labels.remarks(r), r.timeMillis, Overlays.standoff(r), from,
                nowMs, labels);
    }

    public static List<Event> incident(String uid, String name, double lat,
            double lon, UxoRecord r, Sender from, long nowMs,
            boolean labels) {
        return events(uid, name, lat, lon, UxoCodec.toXml(r),
                UxoText.remarks(r), r.timeMillis, Overlays.uxo(r), from,
                nowMs, labels);
    }

    public static List<Event> incident(String uid, String name, double lat,
            double lon, NucRecord r, Sender from, long nowMs,
            boolean labels) {
        return events(uid, name, lat, lon, NucCodec.toXml(r),
                NucText.remarks(r), r.timeMillis, Overlays.nuc(r, lat, lon),
                from, nowMs, labels);
    }

    private static List<Event> events(String uid, String name, double lat,
            double lon, String detail, String remarks, long placedMs,
            List<OverlayShape> shapes, Sender from, long nowMs,
            boolean labels) {
        List<Event> out = new ArrayList<>();
        String creator = creator(from, placedMs > 0 ? placedMs : nowMs);
        StringBuilder m = head(uid, MARKER_TYPE, nowMs, nowMs + YEAR_MS,
                "h-g-i-g-o", lat, lon);
        m.append("<detail><status readiness='true'/><archive/>")
                .append("<color argb='-1'/>")
                .append("<link uid='").append(esc(from.uid))
                .append("' production_time='")
                .append(time(placedMs > 0 ? placedMs : nowMs))
                .append("' type='").append(SELF_TYPE)
                .append("' parent_callsign='").append(esc(from.callsign))
                .append("' relation='p-p'/><archive/>").append(creator)
                .append("<contact callsign='").append(esc(name))
                .append("'/>").append(detail).append("<remarks>")
                .append(escText(remarks)).append("</remarks></detail></event>");
        out.add(new Event(uid, MARKER_TYPE, m.toString()));
        for (OverlayShape sh : shapes)
            out.add(shape(uid, lat, lon, sh, creator, nowMs, labels));
        return out;
    }

    private static Event shape(String owner, double lat, double lon,
            OverlayShape sh, String creator, long nowMs, boolean labels) {
        String uid = owner + sh.id;
        boolean circle = sh.kind == OverlayShape.Kind.CIRCLE;
        double[] at = circle ? new double[] { lat, lon } : centre(sh);
        StringBuilder e = head(uid, circle ? CIRCLE_TYPE : OUTLINE_TYPE,
                nowMs, nowMs + YEAR_MS, "h-e", at[0], at[1]);
        e.append("<detail>");
        Style st = sh.style;
        if (circle) {
            String r = Double.toString(sh.radiusM);
            e.append("<shape><ellipse major='").append(r).append("' minor='")
                    .append(r).append("' angle='360'/>")
                    .append("<link uid='").append(esc(uid))
                    .append(".Style' type='b-x-KmlStyle' relation='p-c'>")
                    .append("<Style><LineStyle><color>")
                    .append(hex(st.strokeArgb)).append("</color><width>")
                    .append(width(st)).append("</width></LineStyle>")
                    .append("<PolyStyle><color>").append(hex(fill(st)))
                    .append("</color></PolyStyle></Style></link>")
                    .append("<link uid='").append(esc(owner))
                    .append("' type='").append(MARKER_TYPE)
                    .append("' relation='p-p-CenterAnchor'/></shape>");
        } else {
            int n = sh.pointCount();
            for (int i = 0; i < n; i++)
                e.append("<link point='").append(coord(sh.lat(i))).append(',')
                        .append(coord(sh.lon(i))).append("'/>");
            // A closed outline ends where it starts.
            if (sh.isClosed() && n > 0 && (sh.lat(0) != sh.lat(n - 1)
                    || sh.lon(0) != sh.lon(n - 1)))
                e.append("<link point='").append(coord(sh.lat(0)))
                        .append(',').append(coord(sh.lon(0))).append("'/>");
        }
        e.append("<__shapeExtras cpvis='true' editable='false'/><archive/>")
                .append(creator)
                .append("<contact callsign='").append(esc(sh.title == null
                        ? "" : sh.title)).append("'/>")
                .append("<strokeColor value='").append(st.strokeArgb)
                .append("'/><strokeWeight value='").append(width(st))
                .append("'/><strokeStyle value='").append(lineStyle(st))
                .append("'/><fillColor value='").append(fill(st))
                .append("'/><labels_on value='").append(circle && labels)
                .append("'/></detail></event>");
        return new Event(uid, circle ? CIRCLE_TYPE : OUTLINE_TYPE,
                e.toString());
    }

    // ------------------------------------------------------------------
    // Remote delete (as ATAK's CotDeleteImporter expects)

    public static String delete(String itemUid, String itemType, long nowMs) {
        StringBuilder e = head(UUID.randomUUID().toString(), DELETE_TYPE,
                nowMs, nowMs + 20 * 60 * 1000L, "h-g-i-g-o", 0, 0);
        e.append("<detail><link uid='").append(esc(itemUid))
                .append("' relation='none' type='").append(esc(itemType))
                .append("'/><__forcedelete/></detail></event>");
        return e.toString();
    }

    // ------------------------------------------------------------------
    // Pieces

    private static StringBuilder head(String uid, String type, long nowMs,
            long staleMs, String how, double lat, double lon) {
        String t = time(nowMs);
        return new StringBuilder(2048)
                .append("<?xml version='1.0' encoding='UTF-8' "
                        + "standalone='yes'?>")
                .append("<event version='2.0' uid='").append(esc(uid))
                .append("' type='").append(type).append("' time='").append(t)
                .append("' start='").append(t).append("' stale='")
                .append(time(staleMs)).append("' how='").append(how)
                .append("' access='Undefined'>")
                .append("<point lat='").append(coord(lat)).append("' lon='")
                .append(coord(lon)).append("' hae='").append(UNKNOWN)
                .append("' ce='").append(UNKNOWN).append("' le='")
                .append(UNKNOWN).append("' />");
    }

    private static String creator(Sender from, long whenMs) {
        return "<creator uid='" + esc(from.uid) + "' callsign='"
                + esc(from.callsign) + "' time='" + time(whenMs) + "' type='"
                + SELF_TYPE + "'/>";
    }

    /**
     * The point ATAK gives an outline's event: the mean of its vertices,
     * not counting a closing point that repeats the first.
     */
    static double[] centre(OverlayShape sh) {
        int n = sh.pointCount();
        if (n > 1 && sh.lat(0) == sh.lat(n - 1) && sh.lon(0) == sh.lon(n - 1))
            n--;
        if (n == 0)
            return new double[] { 0, 0 };
        double la = 0, lo = 0;
        for (int i = 0; i < n; i++) {
            la += sh.lat(i);
            lo += sh.lon(i);
        }
        return new double[] { la / n, lo / n };
    }

    /** No fill: fully transparent (ATAK's "0"). */
    private static int fill(Style st) {
        return st.noFill() ? 0 : st.fillArgb;
    }

    private static String width(Style st) {
        return Double.toString(st.width);
    }

    private static String lineStyle(Style st) {
        switch (st.line) {
            case DASHED:
                return "dashed";
            case DOTTED:
                return "dotted";
            default:
                return "solid";
        }
    }

    /** ARGB as 8 hex digits, as ATAK's KML style does. */
    static String hex(int argb) {
        return String.format(Locale.US, "%08x", argb);
    }

    /** Degrees to 7 decimals without trailing zeros, as ATAK writes. */
    static String coord(double d) {
        String s = String.format(Locale.US, "%.7f", d);
        if (s.indexOf('.') >= 0) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith("."))
                s = s.substring(0, s.length() - 1);
        }
        return s.equals("-0") ? "0" : s;
    }

    /** CoT time: UTC with milliseconds. */
    static String time(long ms) {
        SimpleDateFormat f = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ms));
    }

    /** For attribute values in single quotes. */
    static String esc(String s) {
        if (s == null)
            return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':
                    b.append("&amp;");
                    break;
                case '<':
                    b.append("&lt;");
                    break;
                case '>':
                    b.append("&gt;");
                    break;
                case '\'':
                    b.append("&apos;");
                    break;
                case '"':
                    b.append("&quot;");
                    break;
                default:
                    if (c == '\t')
                        b.append(c);
                    else if (Character.isHighSurrogate(c) && i + 1 < s.length()
                            && Character.isLowSurrogate(s.charAt(i + 1)))
                        b.append(c).append(s.charAt(++i));
                    else if (BlastCodec.xmlChar(c))
                        b.append(c);
                    // else: forbidden in XML 1.0, dropped
            }
        }
        return b.toString();
    }

    /** Element text (remarks): the same escapes. */
    static String escText(String s) {
        return esc(s);
    }
}
