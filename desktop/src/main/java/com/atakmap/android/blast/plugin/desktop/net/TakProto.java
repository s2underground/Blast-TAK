package com.atakmap.android.blast.plugin.desktop.net;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * TAK Protocol version 1 (the protobuf form ATAK uses on the mesh once
 * every device supports it), read into the classic CoT XML so one parser
 * handles both. Mesh packets start 0xBF 0x01 0xBF; stream frames start
 * 0xBF and a varint length. Only what a CoT event holds is read
 * (TakMessage.cotEvent); anything malformed gives null.
 */
public final class TakProto {

    private TakProto() {
    }

    /** True if a datagram is a TAK Protocol v1 mesh packet. */
    public static boolean isMesh(byte[] b, int len) {
        return len >= 3 && (b[0] & 0xFF) == 0xBF && b[1] == 0x01
                && (b[2] & 0xFF) == 0xBF;
    }

    /** A mesh packet as CoT XML, or null. */
    public static String meshToXml(byte[] b, int len) {
        if (!isMesh(b, len))
            return null;
        return takMessageToXml(b, 3, len);
    }

    /** A TakMessage (bytes from..to) as CoT XML, or null. */
    public static String takMessageToXml(byte[] b, int from, int to) {
        try {
            Reader r = new Reader(b, from, to);
            String xml = null;
            while (r.more()) {
                int tag = r.tag();
                if (tag >>> 3 == 2 && (tag & 7) == 2) {
                    int[] range = r.bytes();
                    xml = event(b, range[0], range[1]);
                } else {
                    r.skip(tag);
                }
            }
            return xml;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // CotEvent

    private static String event(byte[] b, int from, int to) {
        Reader r = new Reader(b, from, to);
        String type = "", access = null, qos = null, opex = null, uid = "",
                how = "";
        long send = 0, start = 0, stale = 0;
        double lat = 0, lon = 0, hae = 9999999, ce = 9999999, le = 9999999;
        StringBuilder detail = null;
        while (r.more()) {
            int tag = r.tag(), f = tag >>> 3;
            switch (f) {
                case 1: type = r.string(tag); break;
                case 2: access = r.string(tag); break;
                case 3: qos = r.string(tag); break;
                case 4: opex = r.string(tag); break;
                case 5: uid = r.string(tag); break;
                case 6: send = r.varint(tag); break;
                case 7: start = r.varint(tag); break;
                case 8: stale = r.varint(tag); break;
                case 9: how = r.string(tag); break;
                case 10: lat = r.dbl(tag); break;
                case 11: lon = r.dbl(tag); break;
                case 12: hae = r.dbl(tag); break;
                case 13: ce = r.dbl(tag); break;
                case 14: le = r.dbl(tag); break;
                case 15: {
                    int[] d = r.bytes(tag);
                    detail = detail(b, d[0], d[1]);
                    break;
                }
                default: r.skip(tag);
            }
        }
        if (uid.isEmpty() || type.isEmpty())
            return null;
        StringBuilder x = new StringBuilder(512);
        x.append("<event version=\"2.0\" uid=\"").append(esc(uid))
                .append("\" type=\"").append(esc(type)).append("\" how=\"")
                .append(esc(how)).append("\" time=\"").append(time(send))
                .append("\" start=\"").append(time(start))
                .append("\" stale=\"").append(time(stale)).append('"');
        if (access != null)
            x.append(" access=\"").append(esc(access)).append('"');
        if (qos != null)
            x.append(" qos=\"").append(esc(qos)).append('"');
        if (opex != null)
            x.append(" opex=\"").append(esc(opex)).append('"');
        x.append("><point lat=\"").append(num(lat)).append("\" lon=\"")
                .append(num(lon)).append("\" hae=\"").append(num(hae))
                .append("\" ce=\"").append(num(ce)).append("\" le=\"")
                .append(num(le)).append("\"/><detail>");
        if (detail != null)
            x.append(detail);
        return x.append("</detail></event>").toString();
    }

    /** Detail: the structured parts as their XML, then xmlDetail. */
    private static StringBuilder detail(byte[] b, int from, int to) {
        Reader r = new Reader(b, from, to);
        StringBuilder s = new StringBuilder();
        String xml = "";
        while (r.more()) {
            int tag = r.tag(), f = tag >>> 3;
            if ((tag & 7) != 2) {
                r.skip(tag);
                continue;
            }
            int[] d = r.bytes(tag);
            switch (f) {
                case 1:
                    xml = new String(b, d[0], d[1] - d[0],
                            StandardCharsets.UTF_8);
                    break;
                case 2: {
                    String[] v = strings(b, d, 2);
                    s.append("<contact");
                    if (!v[0].isEmpty())
                        s.append(" endpoint=\"").append(esc(v[0])).append('"');
                    s.append(" callsign=\"").append(esc(v[1])).append("\"/>");
                    break;
                }
                case 3: {
                    String[] v = strings(b, d, 2);
                    s.append("<__group name=\"").append(esc(v[0]))
                            .append("\" role=\"").append(esc(v[1]))
                            .append("\"/>");
                    break;
                }
                case 4: {
                    String[] v = strings(b, d, 2);
                    s.append("<precisionlocation geopointsrc=\"")
                            .append(esc(v[0])).append("\" altsrc=\"")
                            .append(esc(v[1])).append("\"/>");
                    break;
                }
                case 5: {
                    Reader q = new Reader(b, d[0], d[1]);
                    long battery = 0;
                    while (q.more()) {
                        int t = q.tag();
                        if (t >>> 3 == 1)
                            battery = q.varint(t);
                        else
                            q.skip(t);
                    }
                    s.append("<status battery=\"").append(battery)
                            .append("\"/>");
                    break;
                }
                case 6: {
                    String[] v = strings(b, d, 4);
                    s.append("<takv device=\"").append(esc(v[0]))
                            .append("\" platform=\"").append(esc(v[1]))
                            .append("\" os=\"").append(esc(v[2]))
                            .append("\" version=\"").append(esc(v[3]))
                            .append("\"/>");
                    break;
                }
                case 7: {
                    Reader q = new Reader(b, d[0], d[1]);
                    double speed = 0, course = 0;
                    while (q.more()) {
                        int t = q.tag();
                        if (t >>> 3 == 1)
                            speed = q.dbl(t);
                        else if (t >>> 3 == 2)
                            course = q.dbl(t);
                        else
                            q.skip(t);
                    }
                    s.append("<track speed=\"").append(num(speed))
                            .append("\" course=\"").append(num(course))
                            .append("\"/>");
                    break;
                }
                default:
                    break;
            }
        }
        return s.append(xml);
    }

    /** String fields 1..n of a small message. */
    private static String[] strings(byte[] b, int[] d, int n) {
        String[] v = new String[n];
        for (int i = 0; i < n; i++)
            v[i] = "";
        Reader q = new Reader(b, d[0], d[1]);
        while (q.more()) {
            int t = q.tag(), f = t >>> 3;
            if (f >= 1 && f <= n && (t & 7) == 2)
                v[f - 1] = q.string(t);
            else
                q.skip(t);
        }
        return v;
    }

    // ------------------------------------------------------------------
    // Pieces

    private static String time(long ms) {
        SimpleDateFormat f = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ms));
    }

    private static String num(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d))
            return "9999999.0";
        return Double.toString(d);
    }

    static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                case '\'': b.append("&apos;"); break;
                default:
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r')
                        b.append(c);
            }
        }
        return b.toString();
    }

    /** A protobuf reader over b[pos..end); throws on anything malformed. */
    static final class Reader {
        private final byte[] b;
        private int pos;
        private final int end;

        Reader(byte[] b, int from, int to) {
            if (from < 0 || to > b.length || from > to)
                throw new IllegalArgumentException("range");
            this.b = b;
            this.pos = from;
            this.end = to;
        }

        boolean more() {
            return pos < end;
        }

        long raw() {
            long v = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (pos >= end)
                    throw new IllegalArgumentException("varint");
                int x = b[pos++] & 0xFF;
                v |= (long) (x & 0x7F) << shift;
                if ((x & 0x80) == 0)
                    return v;
            }
            throw new IllegalArgumentException("varint");
        }

        int tag() {
            long t = raw();
            if (t <= 0 || t > Integer.MAX_VALUE)
                throw new IllegalArgumentException("tag");
            return (int) t;
        }

        long varint(int tag) {
            if ((tag & 7) != 0)
                throw new IllegalArgumentException("wire");
            return raw();
        }

        double dbl(int tag) {
            if ((tag & 7) != 1 || end - pos < 8)
                throw new IllegalArgumentException("wire");
            long v = 0;
            for (int i = 0; i < 8; i++)
                v |= (long) (b[pos++] & 0xFF) << (8 * i);
            return Double.longBitsToDouble(v);
        }

        int[] bytes() {
            long n = raw();
            if (n < 0 || n > end - pos)
                throw new IllegalArgumentException("length");
            int[] r = { pos, pos + (int) n };
            pos += (int) n;
            return r;
        }

        int[] bytes(int tag) {
            if ((tag & 7) != 2)
                throw new IllegalArgumentException("wire");
            return bytes();
        }

        String string(int tag) {
            int[] r = bytes(tag);
            return new String(b, r[0], r[1] - r[0], StandardCharsets.UTF_8);
        }

        void skip(int tag) {
            switch (tag & 7) {
                case 0: raw(); break;
                case 1: advance(8); break;
                case 2: bytes(); break;
                case 5: advance(4); break;
                default: throw new IllegalArgumentException("wire");
            }
        }

        private void advance(int n) {
            if (end - pos < n)
                throw new IllegalArgumentException("short");
            pos += n;
        }
    }
}
