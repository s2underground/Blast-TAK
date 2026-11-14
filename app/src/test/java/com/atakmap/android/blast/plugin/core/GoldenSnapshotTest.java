package com.atakmap.android.blast.plugin.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Refactor step 1: locks everything the core produces today, and what the
 * ATAK layer draws (via {@link DrawSpec}), for a matrix of sample
 * incidents. Any change to these golden files must be intended and
 * reviewed. The CoT detail XML lines are the team-sharing wire format and
 * must never change.
 */
public class GoldenSnapshotTest {

    static final long T = 1793544725000L; // 2026-11-01 14:52:05Z
    static final long NOW = 1793548325000L; // one hour later
    static final double LAT = 38.0406, LON = -84.5037; // Lexington, KY
    static final double LAT_S = -33.8688, LON_E = 151.2093; // Sydney
    static final String BY = "ALPHA-1";
    static final String GRID = "16S GH 12345 67890";
    static final String GRID2 = "16S GH 13345 68890";

    /** Distances (m) from the item used for "where am I" checks. */
    static final double[] DIST = { 0, 20, 21.5, 150, 366, 400, 1000, 5000 };
    /** East/north offsets (m) for nuclear "where am I" checks. */
    static final double[][] EN = { { 0, 0 }, { 500, 0 }, { 0, 2000 },
            { 3000, 3000 }, { 10000, 0 }, { -10000, 0 }, { 30000, 5000 },
            { 80000, 0 } };

    // ------------------------------------------------------------------

    @Test
    public void standoff() {
        Golden.check("standoff.txt", standoffSnapshot());
    }

    @Test
    public void uxo() {
        Golden.check("uxo.txt", uxoSnapshot());
    }

    @Test
    public void nuclear() {
        Golden.check("nuclear.txt", nucSnapshot());
    }

    @Test
    public void standoffCard() {
        Golden.check("standoff-card.txt", standoffCardSnapshot());
    }

    @Test
    public void uxoCard() {
        Golden.check("uxo-card.txt", uxoCardSnapshot());
    }

    @Test
    public void reference() {
        Golden.check("reference.txt", referenceSnapshot());
    }

    /** The same output under another time zone and locale. */
    @Test
    public void deterministicAcrossLocales() {
        TimeZone tz = TimeZone.getDefault();
        Locale loc = Locale.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
            Locale.setDefault(Locale.GERMANY);
            Golden.check("standoff.txt", standoffSnapshot());
            Golden.check("uxo.txt", uxoSnapshot());
            Golden.check("nuclear.txt", nucSnapshot());
            Golden.check("reference.txt", referenceSnapshot());
            Golden.check("standoff-card.txt", standoffCardSnapshot());
            Golden.check("uxo-card.txt", uxoCardSnapshot());
        } finally {
            TimeZone.setDefault(tz);
            Locale.setDefault(loc);
        }
    }

    // ------------------------------------------------------------------
    // Standoff (CISA/FBI card)

    static String standoffSnapshot() {
        StringBuilder sb = new StringBuilder();
        StandoffTable t = StandoffTable.current();
        List<BlastRecord> rs = new ArrayList<>();
        int n = 1;
        for (StandoffRow row : t.rows())
            rs.add(BlastRecord.create(ZoneCalculator.forRow(t, row.id), BY, T)
                    .withNumber(n++));
        rs.add(BlastRecord.create(ZoneCalculator.forQuantity(t, 37,
                ZoneCalculator.MassUnit.LB), BY, T).withNumber(n++));
        rs.add(BlastRecord.create(ZoneCalculator.forQuantity(t, 100,
                ZoneCalculator.MassUnit.KG), BY, T).withNumber(n++));
        rs.add(BlastRecord.create(ZoneCalculator.forRow(t, "CAR"), BY, T)
                .withNumber(n++).cleared("BRAVO-2", NOW));
        sb.append("ZoneCalculator.parseQuantity:");
        for (String q : new String[] { "37", "1,000", "2.5", "", "abc",
                "1e3", "-5", "60001" })
            sb.append(" [").append(q).append("]=")
                    .append(Golden.num(ZoneCalculator.parseQuantity(q), 4));
        sb.append("\n");
        for (BlastRecord r : rs) {
            String name = Names.withStatus(Names.initial(r.number,
                    r.rowLabel), r.isCleared());
            sb.append("\n=== ").append(name).append("\n");
            sb.append("cot: ").append(BlastCodec.toXml(r)).append("\n");
            sb.append("threat: ").append(Labels.threat(r)).append("\n");
            sb.append("source: ").append(Labels.source(r)).append("\n");
            sb.append("metresNote: ").append(Labels.metresNote(r))
                    .append("\n");
            for (String[] row : Labels.zoneRows(r))
                sb.append("zoneRow: ").append(Arrays.toString(row))
                        .append("\n");
            sb.append("provenance:\n").append(Labels.provenance(r))
                    .append("\n");
            sb.append("remarks:\n").append(Labels.remarks(r)).append("\n");
            sb.append("draw:\n  ").append(DrawSpec.marker(name)).append("\n");
            for (String d : DrawSpec.standoff(r))
                sb.append("  ").append(d).append("\n");
            StandoffRow row = r.row();
            for (double d : DIST) {
                PositionCheck.Result c = PositionCheck.check(row, d,
                        PositionCheck.DEFAULT_UNCERTAINTY_M);
                sb.append("check ").append(Golden.num(d, 1)).append(" m: ")
                        .append(c == null ? "null"
                                : c.zone + " toPref="
                                        + Golden.num(c.toPreferredM, 2)
                                        + " near=" + c.nearBoundary)
                        .append("\n");
            }
            sb.append("count: ").append(Arrays.toString(PositionCheck.count(
                    row, DIST))).append("\n");
        }
        BlastRecord car = rs.get(3);
        Exports.Incident in = new Exports.Incident("BLAST 4", LAT, LON, car);
        sb.append("\n=== exports (").append(car.rowLabel).append(")\n");
        sb.append("fileStem: ").append(Exports.fileStem("BLAST 4", T))
                .append("\n");
        sb.append("kml:\n").append(Exports.kml(in)).append("\n");
        sb.append("gpx:\n").append(Exports.gpx(in, NOW)).append("\n");
        sb.append("guidance:\n").append(Guidance.CARD_ACTIONS).append("\n");
        for (String c : Guidance.CAVEATS)
            sb.append("caveat: ").append(c).append("\n");
        sb.append("disclaimer: ").append(Guidance.DISCLAIMER).append("\n");
        return sb.toString();
    }

    /** Refactor step 4: the standoff card as the core describes it. */
    static String standoffCardSnapshot() {
        StringBuilder sb = new StringBuilder();
        StandoffTable t = StandoffTable.current();
        List<BlastRecord> rs = new ArrayList<>();
        rs.add(BlastRecord.create(ZoneCalculator.forRow(t, "BRIEFCASE"), BY,
                T).withNumber(1));
        rs.add(BlastRecord.create(ZoneCalculator.forQuantity(t, 37,
                ZoneCalculator.MassUnit.LB), BY, T).withNumber(2));
        rs.add(BlastRecord.create(ZoneCalculator.forRow(t, "CAR"), BY, T)
                .withNumber(3).cleared("BRAVO-2", NOW));
        double[] acc = { Double.NaN, 5, 200 };
        for (BlastRecord r : rs) {
            String name = Names.withStatus(Names.initial(r.number,
                    r.rowLabel), r.isCleared());
            sb.append("\n=== card ").append(name).append("\n");
            sb.append(ScreenDump.dump(
                    com.atakmap.android.blast.plugin.core.screen.StandoffCard
                            .card(r, name)));
            sb.append("you, no fix:\n").append(ScreenDump.item(
                    com.atakmap.android.blast.plugin.core.screen.StandoffCard
                            .you(r, false, Double.NaN, Double.NaN)))
                    .append("\n");
            for (double d : DIST)
                for (double a : acc)
                    sb.append("you ").append(Golden.num(d, 1)).append(" m ce=")
                            .append(Golden.num(a, 0)).append(":\n")
                            .append(ScreenDump.item(com.atakmap.android.blast
                                    .plugin.core.screen.StandoffCard.you(r,
                                            true, d, a))).append("\n");
            for (double[] team : new double[][] { {}, { 5000 },
                    { 10, 300, 5000 }, { 10, 20 } })
                sb.append("team ").append(Arrays.toString(team)).append(":\n")
                        .append(ScreenDump.item(com.atakmap.android.blast
                                .plugin.core.screen.StandoffCard.team(r,
                                        team))).append("\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // UXO (ATP 4-32.2)

    static String uxoSnapshot() {
        StringBuilder sb = new StringBuilder();
        List<UxoRecord> rs = new ArrayList<>();
        int n = 1;
        for (UxoTable.Category c : UxoTable.categories()) {
            rs.add(UxoRecord.create(c.id, false, false, UxoRecord.Where.PATH,
                    UxoRecord.Placement.MAP, BY, T).withNumber(n++));
            rs.add(UxoRecord.create(c.id, true, true, UxoRecord.Where.AREA,
                    UxoRecord.Placement.RB, BY, T).withNumber(n++));
        }
        rs.add(UxoRecord.create("PROJ_MORTAR", false, false,
                UxoRecord.Where.VEHICLE, UxoRecord.Placement.CENTER, BY, T)
                .withNumber(n++));
        rs.add(UxoRecord.create("PROJ_MORTAR", false, false,
                UxoRecord.Where.UNSET, UxoRecord.Placement.MARKER, BY, T)
                .withNumber(n++).withReport(new UxoRecord.Report(3,
                        "Three 81 mm mortar rounds, rusted", "North of the "
                                + "water tower", "Projected", "None seen",
                        "Road closed", "Main supply route blocked",
                        "Cordon set at 375 m", UxoRecord.Priority.INDIRECT,
                        "BRAVO-2 on channel 3")).cleared("BRAVO-2", NOW));
        for (UxoTable.Category c : UxoTable.categories())
            sb.append("category: ").append(c.id).append(" | ")
                    .append(c.doctrineLabel).append("\n");
        sb.append("picker:");
        for (UxoTable.Category c : UxoTable.pickerCategories())
            sb.append(" ").append(c.id);
        sb.append("\n");
        for (double lb : new double[] { 0, 1, 27, 100, 500, 1000, 100000 })
            sb.append("table1M(").append(Golden.num(lb, 0)).append(")=")
                    .append(UxoTable.table1M(lb)).append("\n");
        for (UxoRecord.Where w : UxoRecord.Where.values())
            sb.append("where ").append(w).append(": ")
                    .append(UxoText.whereLabel(w)).append(" | drill: ")
                    .append(UxoText.drill(w)).append("\n");
        for (UxoRecord.Placement p : UxoRecord.Placement.values())
            sb.append("placement ").append(p).append(": ")
                    .append(UxoText.placement(p)).append("\n");
        for (UxoRecord r : rs) {
            String name = Names.withStatus(Names.initialUxo(r.number,
                    UxoTable.category(r.categoryId).doctrineLabel),
                    r.isCleared());
            sb.append("\n=== ").append(name).append("\n");
            sb.append("cot: ").append(UxoCodec.toXml(r)).append("\n");
            sb.append("threat: ").append(UxoText.threat(r)).append("\n");
            sb.append("cordon: ").append(UxoText.cordon(r.cordonM))
                    .append("\n");
            sb.append("basis: ").append(UxoText.basis(r)).append("\n");
            for (String d : UxoText.doNow(r))
                sb.append("doNow: ").append(d).append("\n");
            sb.append("provenance:\n").append(UxoText.provenance(r))
                    .append("\n");
            sb.append("remarks:\n").append(UxoText.remarks(r)).append("\n");
            sb.append("nineLine:\n").append(UxoText.nineLine(r, BY, GRID))
                    .append("\n");
            sb.append("draw:\n  ").append(DrawSpec.marker(name)).append("\n");
            for (String d : DrawSpec.uxo(r))
                sb.append("  ").append(d).append("\n");
            for (double d : DIST) {
                UxoText.You y = UxoText.check(r.cordonM, d,
                        PositionCheck.DEFAULT_UNCERTAINTY_M);
                sb.append("check ").append(Golden.num(d, 1)).append(" m: ")
                        .append(y == null ? "null"
                                : "inside=" + y.inside + " toCordon="
                                        + Golden.num(y.toCordonM, 2)
                                        + " near=" + y.nearBoundary)
                        .append("\n");
            }
            sb.append("count: ").append(Arrays.toString(UxoText.count(
                    r.cordonM, DIST))).append("\n");
        }
        UxoRecord last = rs.get(rs.size() - 1);
        sb.append("\n=== exports\n");
        sb.append("kml:\n").append(Exports.kmlUxo("UXO 14", LAT, LON, last))
                .append("\n");
        sb.append("gpx:\n").append(Exports.gpxUxo("UXO 14", LAT, LON, last,
                NOW)).append("\n");
        return sb.toString();
    }

    /** Refactor step 4b: the UXO card as the core describes it. */
    static String uxoCardSnapshot() {
        StringBuilder sb = new StringBuilder();
        List<UxoRecord> rs = new ArrayList<>();
        rs.add(UxoRecord.create("PROJ_MORTAR", false, false,
                UxoRecord.Where.PATH, UxoRecord.Placement.MAP, BY, T)
                .withNumber(1));
        rs.add(UxoRecord.create("MISSILE_ROCKET", true, true, UxoRecord.Where.AREA,
                UxoRecord.Placement.RB, BY, T).withNumber(2));
        rs.add(UxoRecord.create("PROJ_MORTAR", false, false,
                UxoRecord.Where.UNSET, UxoRecord.Placement.MARKER, BY, T)
                .withNumber(3).withReport(new UxoRecord.Report(3,
                        "Three 81 mm mortar rounds, rusted", "North of the "
                                + "water tower", "Projected", "None seen",
                        "Road closed", "Main supply route blocked",
                        "Cordon set at 375 m", UxoRecord.Priority.INDIRECT,
                        "BRAVO-2 on channel 3")).cleared("BRAVO-2", NOW));
        double[] acc = { Double.NaN, 5, 80 };
        for (UxoRecord r : rs) {
            String name = Names.withStatus(Names.initialUxo(r.number,
                    UxoTable.category(r.categoryId).doctrineLabel),
                    r.isCleared());
            sb.append("\n=== card ").append(name).append("\n");
            sb.append(ScreenDump.dump(com.atakmap.android.blast.plugin.core
                    .screen.UxoCard.card(r, name, "112", GRID)));
            sb.append("you, no fix:\n").append(ScreenDump.item(
                    com.atakmap.android.blast.plugin.core.screen.UxoCard
                            .you(r, false, Double.NaN, Double.NaN)))
                    .append("\n");
            for (double d : DIST)
                for (double a : acc)
                    sb.append("you ").append(Golden.num(d, 1)).append(" m ce=")
                            .append(Golden.num(a, 0)).append(":\n")
                            .append(ScreenDump.item(com.atakmap.android.blast
                                    .plugin.core.screen.UxoCard.you(r, true,
                                            d, a))).append("\n");
            for (double[] team : new double[][] { {}, { 5000 },
                    { 10, 300, 5000 } })
                sb.append("team ").append(Arrays.toString(team)).append(":\n")
                        .append(ScreenDump.item(com.atakmap.android.blast
                                .plugin.core.screen.UxoCard.team(r, team)))
                        .append("\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Nuclear reference

    static String nucSnapshot() {
        StringBuilder sb = new StringBuilder();
        List<Object[]> cases = new ArrayList<>();
        NucModel.Burst S = NucModel.Burst.SURFACE, A = NucModel.Burst.AIR,
                U = NucModel.Burst.UNKNOWN;
        double nan = Double.NaN;
        cases.add(c("0.1 kt surface, no wind", NucRecord.create(0.1, S,
                "FEMA_01", nan, nan, BY, T), LAT, LON));
        cases.add(c("FEMA 10 kT surface, W wind 15 mph", NucRecord.create(10,
                S, "FEMA_10", 270, 15, BY, T), LAT, LON));
        cases.add(c("10 kt air", NucRecord.create(10, A, "", 270, 15, BY, T),
                LAT, LON));
        cases.add(c("10 kt unknown, no wind", NucRecord.create(10, U, "",
                nan, nan, BY, T), LAT, LON));
        cases.add(c("100 kt surface, NE wind 30 mph, Sydney", NucRecord
                .create(100, S, "", 45, 30, BY, T), LAT_S, LON_E));
        cases.add(c("W88 455 kt unknown, light wind", NucRecord.create(455,
                U, "US_W88", 200, 8, BY, T), LAT, LON));
        cases.add(c("1 Mt surface, S wind 10 mph", NucRecord.create(1000, S,
                "", 180, 10, BY, T), LAT, LON));
        cases.add(c("20 Mt surface, extrapolated", NucRecord.create(20000, S,
                "", 300, 25, BY, T), LAT, LON));
        cases.add(c("50 Mt air", NucRecord.create(50000, A, "", nan, nan, BY,
                T), LAT, LON));
        NucRecord mil = NucRecord.create(35, S, "", 270, 18, BY, T)
                .withMil(new NucRecord.Mil(true, nan, nan, false));
        cases.add(c("military 35 kt, surface wind", mil, LAT, LON));
        cases.add(c("military EDM 6-digit", mil.withEdm(NucMil.parseEdm(
                "270030")), LAT, LON));
        cases.add(c("military EDM 9-digit wide angle", mil.withEdm(NucMil
                .parseEdm("250040070")), LAT, LON));
        cases.add(c("military EDM 3-digit circular", mil.withEdm(NucMil
                .parseEdm("012")), LAT, LON));
        cases.add(c("military, air burst (no prediction)", NucRecord.create(
                35, A, "", 270, 18, BY, T).withMil(new NucRecord.Mil(true,
                        nan, nan, false)), LAT, LON));
        cases.add(c("observer report, cleared", NucRecord.create(16, S, "",
                270, 10, BY, T).withObs(new NucRecord.Obs(LAT + 0.1,
                        LON - 0.1, 135, 22, 12, "", "", "", "", "AA"))
                .cleared("BRAVO-2", NOW), LAT, LON));
        int n = 1;
        for (Object[] cs : cases) {
            String label = (String) cs[0];
            NucRecord r = ((NucRecord) cs[1]).withNumber(n++);
            double lat = (Double) cs[2], lon = (Double) cs[3];
            String name = Names.withStatus(Names.initialNuc(r.number,
                    NucPresets.yieldLabel(r.yieldKt)), r.isCleared());
            sb.append("\n=== ").append(name).append(" (").append(label)
                    .append(")\n");
            sb.append("cot: ").append(NucCodec.toXml(r)).append("\n");
            sb.append("threat: ").append(NucText.threat(r)).append("\n");
            sb.append("extrapolated: ").append(NucModel.extrapolated(
                    r.yieldKt)).append("\n");
            sb.append("basis: ").append(NucText.basis(r)).append("\n");
            sb.append("falloutLine: ").append(NucText.falloutLine(r))
                    .append("\n");
            for (String[] row : NucText.rows(r))
                sb.append("row: ").append(Arrays.toString(row)).append("\n");
            sb.append("provenance:\n").append(NucText.provenance(r))
                    .append("\n");
            sb.append("remarks:\n").append(NucText.remarks(r)).append("\n");
            NucMil.Prediction p = r.militaryPrediction();
            sb.append("drawsFallout=").append(r.drawsFallout())
                    .append(" drawsMilitary=").append(r.drawsMilitary())
                    .append("\n");
            if (p != null) {
                sb.append("summary:\n").append(NucReports.summary(p))
                        .append("\n");
                sb.append("papab: ").append(NucReports.papab(p)).append("\n");
                sb.append("nbc3:\n").append(NucReports.nbc3(r, p, GRID))
                        .append("\n");
                for (double h : new double[] { 0.25, 1, 2, 5 }) {
                    double[][] arc = p.arc(h * p.ewsKph);
                    sb.append("arc at ").append(Golden.num(h, 2))
                            .append(" h: ").append(arc == null ? "none"
                                    : arc.length + " pts").append("\n");
                }
            }
            sb.append("nbc1:\n").append(NucReports.nbc1(r, GRID2, GRID))
                    .append("\n");
            sb.append("nbc1Missing: ").append(NucReports.nbc1Missing(r,
                    GRID2)).append("\n");
            sb.append("draw:\n  ").append(DrawSpec.marker(name)).append("\n");
            for (String d : DrawSpec.nuc(r, lat, lon))
                sb.append("  ").append(d).append("\n");
            for (double[] en : EN) {
                NucText.Where w = NucText.where(r, en[0], en[1]);
                sb.append("where ").append(Golden.num(en[0], 0)).append(",")
                        .append(Golden.num(en[1], 0)).append(": dist=")
                        .append(Golden.num(w.distanceM, 1)).append(" zone=")
                        .append(w.zone).append(" burn=").append(w.inBurn)
                        .append(" rad=").append(w.inRad).append(" fallout=")
                        .append(w.falloutRate).append(" arrivalH=")
                        .append(Golden.num(w.arrivalH, 3)).append(" mil=")
                        .append(w.milZone).append("\n");
            }
            sb.append("count: ").append(Arrays.toString(NucText.count(r, EN)))
                    .append("\n");
        }
        NucRecord fema = NucRecord.create(10, S, "FEMA_10", 270, 15, BY, T)
                .withNumber(2);
        sb.append("\n=== exports (FEMA 10 kT)\n");
        sb.append("kml:\n").append(Exports.kmlNuc("NUC 2", LAT, LON, fema))
                .append("\n");
        sb.append("gpx:\n").append(Exports.gpxNuc("NUC 2", LAT, LON, fema,
                NOW)).append("\n");
        sb.append("kml military:\n").append(Exports.kmlNuc("NUC 10", LAT,
                LON, ((NucRecord) cases.get(9)[1]).withNumber(10)))
                .append("\n");
        sb.append("\n=== presets\n");
        for (NucPresets.Preset p : NucPresets.all())
            sb.append(p.group).append(" | ").append(p.id).append(" | ")
                    .append(p.label).append(" | ")
                    .append(Golden.num(p.kt, 3)).append(" | ").append(p.note)
                    .append(" | ").append(p.source).append(" | region=")
                    .append(NucPresets.region(p)).append("\n");
        for (String reg : NucPresets.REGIONS)
            sb.append("region ").append(reg).append(": ")
                    .append(NucPresets.fielded(reg).size()).append("\n");
        for (NucModel.Burst b : NucModel.Burst.values())
            sb.append("hotel ").append(b).append(": ")
                    .append(NucReports.hotel(b)).append("\n");
        return sb.toString();
    }

    private static Object[] c(String label, NucRecord r, double lat,
            double lon) {
        return new Object[] { label, r, lat, lon };
    }

    // ------------------------------------------------------------------
    // Reference tab

    static String referenceSnapshot() {
        StringBuilder sb = new StringBuilder();
        List<UxoReference.Section> all = new ArrayList<>(
                UxoReference.sections());
        all.addAll(NucReference.allSections());
        for (UxoReference.Section s : all) {
            sb.append("\n=== ").append(s.id).append(" | ").append(s.title)
                    .append("\n");
            for (String p : s.body)
                sb.append(p).append("\n");
        }
        return sb.toString();
    }
}
