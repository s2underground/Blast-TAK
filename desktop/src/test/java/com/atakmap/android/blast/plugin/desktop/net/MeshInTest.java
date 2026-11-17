package com.atakmap.android.blast.plugin.desktop.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import org.junit.Test;

/** Reading what other devices send: TAK Protocol v1 and CoT XML. */
public class MeshInTest {

    // A tiny protobuf writer for the tests.
    private static void varint(ByteArrayOutputStream o, long v) {
        while ((v & ~0x7FL) != 0) {
            o.write((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        o.write((int) v);
    }

    private static void str(ByteArrayOutputStream o, int f, String s) {
        bytes(o, f, s.getBytes(StandardCharsets.UTF_8));
    }

    private static void bytes(ByteArrayOutputStream o, int f, byte[] b) {
        varint(o, (f << 3) | 2);
        varint(o, b.length);
        o.write(b, 0, b.length);
    }

    private static void dbl(ByteArrayOutputStream o, int f, double d) {
        varint(o, (f << 3) | 1);
        o.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .putDouble(d).array(), 0, 8);
    }

    private static void u64(ByteArrayOutputStream o, int f, long v) {
        varint(o, f << 3);
        varint(o, v);
    }

    static byte[] takMessage(String uid, String type, double lat, double lon,
            String callsign, String endpoint, String xmlDetail) {
        ByteArrayOutputStream contact = new ByteArrayOutputStream();
        if (endpoint != null)
            str(contact, 1, endpoint);
        str(contact, 2, callsign);
        ByteArrayOutputStream group = new ByteArrayOutputStream();
        str(group, 1, "Yellow");
        str(group, 2, "Team Member");
        ByteArrayOutputStream detail = new ByteArrayOutputStream();
        str(detail, 1, xmlDetail);
        bytes(detail, 2, contact.toByteArray());
        bytes(detail, 3, group.toByteArray());
        ByteArrayOutputStream ev = new ByteArrayOutputStream();
        str(ev, 1, type);
        str(ev, 5, uid);
        u64(ev, 6, 1_800_000_000_000L);
        u64(ev, 7, 1_800_000_000_000L);
        u64(ev, 8, 1_800_000_600_000L);
        str(ev, 9, "m-g");
        dbl(ev, 10, lat);
        dbl(ev, 11, lon);
        dbl(ev, 12, 9999999.0);
        dbl(ev, 13, 9999999.0);
        dbl(ev, 14, 9999999.0);
        bytes(ev, 15, detail.toByteArray());
        ByteArrayOutputStream msg = new ByteArrayOutputStream();
        bytes(msg, 2, ev.toByteArray());
        return msg.toByteArray();
    }

    static byte[] mesh(byte[] takMessage) {
        byte[] b = new byte[takMessage.length + 3];
        b[0] = (byte) 0xBF;
        b[1] = 0x01;
        b[2] = (byte) 0xBF;
        System.arraycopy(takMessage, 0, b, 3, takMessage.length);
        return b;
    }

    @Test
    public void protobufSa() {
        byte[] p = mesh(takMessage("ANDROID-1", "a-f-G-U-C", 38.5, -77.25,
                "ALPHA <1>", "10.0.0.5:4242:tcp", "<uid Droid='ALPHA'/>"));
        String xml = TakProto.meshToXml(p, p.length);
        assertNotNull(xml);
        CotIn e = CotIn.parse(xml);
        assertEquals("ANDROID-1", e.uid);
        assertEquals("a-f-G-U-C", e.type);
        assertEquals(38.5, e.lat, 0);
        assertEquals(-77.25, e.lon, 0);
        assertEquals("ALPHA <1>", e.callsign);
        assertEquals("10.0.0.5:4242:tcp", e.endpoint);
        assertEquals("Yellow", e.team);
        assertEquals(1_800_000_600_000L, e.staleMs);
        assertTrue(e.hasDetail("uid"));
    }

    @Test
    public void xmlEvent() {
        CotIn e = CotIn.parse("<?xml version='1.0' encoding='UTF-8' "
                + "standalone='yes'?><event version='2.0' uid='u1' "
                + "type='t-x-d-d' time='2027-01-15T08:00:00.000Z' "
                + "start='2027-01-15T08:00:00.000Z' "
                + "stale='2027-01-15T08:20:00.123456Z' how='h-g-i-g-o'>"
                + "<point lat='0' lon='0' hae='0' ce='0' le='0'/><detail>"
                + "<link uid='abc' relation='none' type='a-h-G'/>"
                + "<__forcedelete/></detail></event>");
        assertEquals("abc", e.linkUid);
        assertEquals(1800001200123L, e.staleMs);
    }

    @Test
    public void refusesWhatIsntCot() {
        assertNull(CotIn.parse("<!DOCTYPE e [<!ENTITY x 'y'>]><event uid='a' "
                + "type='a-h-G'>&x;</event>"));
        assertNull(CotIn.parse("<event uid='a' type='a-h-G'>"));
        assertNull(CotIn.parse("<other uid='a' type='a-h-G'/>"));
        assertNull(CotIn.parse("<event uid='' type='a-h-G'/>"));
        assertNull(TakProto.meshToXml(new byte[] { (byte) 0xBF, 1,
                (byte) 0xBF, (byte) 0xFF, (byte) 0xFF }, 5));
    }

    /** Random bytes and mutated packets never throw. */
    @Test
    public void fuzz() {
        Random r = new Random(7);
        byte[] good = mesh(takMessage("A", "a-h-G", 1, 2, "c", null,
                "<__blast/>"));
        for (int i = 0; i < 20000; i++) {
            byte[] b = good.clone();
            int n = 1 + r.nextInt(6);
            for (int k = 0; k < n; k++)
                b[3 + r.nextInt(b.length - 3)] = (byte) r.nextInt(256);
            String xml = TakProto.meshToXml(b, r.nextInt(b.length + 1));
            if (xml != null)
                CotIn.parse(xml);
        }
    }
}
