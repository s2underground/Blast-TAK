package com.atakmap.android.blast.plugin.core;

import java.util.List;

/**
 * Refactor step 6b-2: the CoT events an incident is shared as by hosts
 * that send CoT themselves (TAK-X). Their structure was checked field by
 * field against a real ATAK Blast data package (bomb threat, UXO, nuclear
 * with wind: 20 events, no differences); this golden locks it in. Every
 * event must also parse as XML.
 */
final class CotEventsSnapshot {

    private CotEventsSnapshot() {
    }

    private static void add(StringBuilder sb, String what,
            List<CotEvents.Event> events) throws Exception {
        sb.append("=== ").append(what).append(": ").append(events.size())
                .append(" events\n");
        for (CotEvents.Event e : events) {
            SafeXml.parse(e.xml, false);
            sb.append("--- ").append(e.uid).append(" (").append(e.type)
                    .append(")\n").append(e.xml.replace("><", ">\n<"))
                    .append("\n");
        }
    }

    static String snapshot() throws Exception {
        StandoffTable table = StandoffTable.current();
        String by = GoldenSnapshotTest.BY;
        long t = GoldenSnapshotTest.T;
        double lat = GoldenSnapshotTest.LAT, lon = GoldenSnapshotTest.LON;
        CotEvents.Sender from = new CotEvents.Sender("TAKX-test-node",
                "TAK-X <1> & 'co'");
        long now = t + 60_000;
        StringBuilder sb = new StringBuilder();

        BlastRecord car = BlastRecord.create(ZoneCalculator.forRow(table,
                "CAR"), by, t).withNumber(1);
        add(sb, "bomb threat, labels on", CotEvents.incident("s1",
                "BLAST 1 Car", lat, lon, car, from, now, true));
        add(sb, "bomb threat cleared, labels off", CotEvents.incident("s2",
                "BLAST 2 CLEARED", lat, lon, car.cleared("BRAVO-2", t),
                from, now, false));
        UxoRecord shell = UxoRecord.create("PROJ_MORTAR", true, false,
                UxoRecord.Where.AREA, UxoRecord.Placement.RB, by, t)
                .withNumber(1);
        add(sb, "UXO", CotEvents.incident("u1", "UXO 1 Shell", lat, lon,
                shell, from, now, true));
        NucRecord nuc = NucRecord.create(10, NucModel.Burst.SURFACE,
                "FEMA_10", 270, 15, by, t).withNumber(1);
        add(sb, "nuclear with wind", CotEvents.incident("n1", "NUC 1 10 kt",
                lat, lon, nuc, from, now, true));
        add(sb, "nuclear, military picture", CotEvents.incident("n2",
                "NUC 2 10 kt", lat, lon, nuc.withMil(nuc.mil.withOn(true)),
                from, now, true));

        String del = CotEvents.delete("s1.pref", CotEvents.CIRCLE_TYPE, now);
        SafeXml.parse(del, false);
        sb.append("=== delete for everyone\n").append(del.replaceAll(
                "uid='[0-9a-f-]{36}'", "uid='<random>'").replace("><",
                        ">\n<")).append("\n");
        return sb.toString();
    }
}
