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
    public void nucCard() {
        Golden.check("nuclear-card.txt", nucCardSnapshot());
    }

    @Test
    public void forms() {
        Golden.check("forms.txt", formsSnapshot());
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
            Golden.check("nuclear-card.txt", nucCardSnapshot());
            Golden.check("forms.txt", formsSnapshot());
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

    /** Refactor step 4c: the nuclear card as the core describes it. */
    static String nucCardSnapshot() {
        StringBuilder sb = new StringBuilder();
        NucModel.Burst S = NucModel.Burst.SURFACE, A = NucModel.Burst.AIR;
        double nan = Double.NaN;
        NucRecord civ = NucRecord.create(10, S, "FEMA_10", 0, 15, BY, T)
                .withNumber(1);
        NucRecord mil = civ.withMil(new NucRecord.Mil(true, nan, nan, false))
                .withNumber(2);
        List<Object[]> cards = new ArrayList<>();
        cards.add(new Object[] { "civil, wind", civ });
        cards.add(new Object[] { "military, surface wind", mil });
        cards.add(new Object[] { "military, EDM", mil.withEdm(NucMil.parseEdm(
                "270030")).withNumber(3) });
        cards.add(new Object[] { "air burst, no wind", NucRecord.create(455,
                A, "US_W88", nan, nan, BY, T).withNumber(4) });
        cards.add(new Object[] { "observer report, ended", NucRecord.create(
                16, S, "", 270, 10, BY, T).withObs(new NucRecord.Obs(LAT + 0.1,
                        LON - 0.1, 135, 22, 12, "", "", "", "", "AA"))
                .cleared("BRAVO-2", NOW).withNumber(5) });
        NucRecord unknown = NucCodec.decode(withEd(NucCodec.encode(civ),
                "2030-01"));
        cards.add(new Object[] { "unknown model", unknown.withNumber(6) });
        for (Object[] cs : cards) {
            NucRecord r = (NucRecord) cs[1];
            String name = Names.withStatus(Names.initialNuc(r.number,
                    NucPresets.yieldLabel(r.yieldKt)), r.isCleared());
            sb.append("\n=== card ").append(name).append(" (").append(cs[0])
                    .append(")\n");
            sb.append(ScreenDump.dump(com.atakmap.android.blast.plugin.core
                    .screen.NucCard.card(r, name, GRID)));
            sb.append("nbc3Text: ").append(String.valueOf(com.atakmap.android
                    .blast.plugin.core.screen.NucCard.nbc3Text(r, GRID))
                    .replace("\n", "\\n")).append("\n");
            sb.append("you, no fix:\n").append(ScreenDump.item(com.atakmap
                    .android.blast.plugin.core.screen.NucCard.you(r, null)))
                    .append("\n");
            for (double[] en : EN)
                sb.append("you ").append(Golden.num(en[0], 0)).append(",")
                        .append(Golden.num(en[1], 0)).append(":\n")
                        .append(ScreenDump.item(com.atakmap.android.blast
                                .plugin.core.screen.NucCard.you(r, en)))
                        .append("\n");
            sb.append("team none:\n").append(ScreenDump.item(com.atakmap
                    .android.blast.plugin.core.screen.NucCard.team(r,
                            new double[0][]))).append("\n");
            sb.append("team all:\n").append(ScreenDump.item(com.atakmap
                    .android.blast.plugin.core.screen.NucCard.team(r, EN)))
                    .append("\n");
        }
        return sb.toString();
    }

    /** Refactor step 4d: the EOD 9-line, NBC 1 and polar plot forms. */
    static String formsSnapshot() {
        StringBuilder sb = new StringBuilder();
        com.atakmap.android.blast.plugin.core.port.Magnetic mag =
                new com.atakmap.android.blast.plugin.core.port.Magnetic() {
                    @Override
                    public double toTrue(double m) {
                        return m - 4.5; // a fixed 4.5° W declination
                    }
                };
        com.atakmap.android.blast.plugin.core.port.Magnetic broken =
                new com.atakmap.android.blast.plugin.core.port.Magnetic() {
                    @Override
                    public double toTrue(double m) {
                        return Double.NaN;
                    }
                };
        // EOD 9-line: empty, then filled, then a saved report reopened.
        UxoRecord u = UxoRecord.create("PROJ_MORTAR", false, false,
                UxoRecord.Where.PATH, UxoRecord.Placement.MAP, BY, T)
                .withNumber(1);
        sb.append("=== EOD 9-line: ").append(com.atakmap.android.blast.plugin
                .core.screen.UxoReportForm.title("UXO 1")).append("\n");
        com.atakmap.android.blast.plugin.core.screen.Screen f =
                com.atakmap.android.blast.plugin.core.screen.UxoReportForm
                        .form(u, "911");
        sb.append(ScreenDump.dump(f));
        com.atakmap.android.blast.plugin.core.screen.FormValues v =
                com.atakmap.android.blast.plugin.core.screen.FormValues
                        .initial(f);
        sb.append("note: ").append(com.atakmap.android.blast.plugin.core
                .screen.UxoReportForm.priorityNote(v)).append("\n");
        sb.append("preview:\n").append(com.atakmap.android.blast.plugin.core
                .screen.UxoReportForm.preview(u, v, "ALPHA-1", GRID))
                .append("\n");
        v.put("land", "North of the water tower").put("poc", "BRAVO-2 ch 3")
                .put("emp", "Projected").put("count", " 3 ")
                .put("desc", "Three 81 mm mortar rounds, rusted")
                .put("cbrn", "None seen").put("res", "Road")
                .put("imp", "Road closed").put("prot", "Cordon set")
                .put("pri", "INDIRECT");
        sb.append("filled note: ").append(com.atakmap.android.blast.plugin
                .core.screen.UxoReportForm.priorityNote(v)).append("\n");
        sb.append("filled preview:\n").append(com.atakmap.android.blast
                .plugin.core.screen.UxoReportForm.preview(u, v, "ALPHA-1",
                        GRID)).append("\n");
        UxoRecord saved = u.withReport(com.atakmap.android.blast.plugin.core
                .screen.UxoReportForm.report(v));
        sb.append("saved cot: ").append(UxoCodec.toXml(saved)).append("\n");
        sb.append("reopened:\n").append(ScreenDump.dump(com.atakmap.android
                .blast.plugin.core.screen.UxoReportForm.form(saved, "911")));
        v.put("count", "x").put("pri", "");
        sb.append("bad count: ").append(com.atakmap.android.blast.plugin.core
                .screen.UxoReportForm.report(v).count).append(" note: ")
                .append(com.atakmap.android.blast.plugin.core.screen
                        .UxoReportForm.priorityNote(v)).append("\n");

        // NBC 1: GPS azimuth prefill, then entries; magnetic on / broken.
        NucRecord n = NucRecord.create(16, NucModel.Burst.SURFACE, "", 270, 10,
                BY, T).withNumber(1);
        f = com.atakmap.android.blast.plugin.core.screen.Nbc1Form.form(n,
                123.4);
        sb.append("\n=== NBC 1 (GPS azimuth 123.4)\n").append(ScreenDump.dump(f));
        sb.append("=== NBC 1 (no GPS)\n").append(ScreenDump.dump(com.atakmap
                .android.blast.plugin.core.screen.Nbc1Form.form(n,
                        Double.NaN)));
        v = com.atakmap.android.blast.plugin.core.screen.FormValues.initial(f);
        NucRecord.Obs o = com.atakmap.android.blast.plugin.core.screen.Nbc1Form
                .obs(v, LAT, LON, mag);
        sb.append("preview initial:\n").append(com.atakmap.android.blast
                .plugin.core.screen.Nbc1Form.preview(n.withObs(o), GRID2,
                        GRID)).append("\n");
        v.put("ftb", "22").put("cw", "12.5").put("gq", "AA").put("kilo",
                "CRATER").put("so", "OBS").put("del", "MSL").put("con", "MSL");
        v.put("mag", true);
        for (com.atakmap.android.blast.plugin.core.port.Magnetic m
                : new com.atakmap.android.blast.plugin.core.port.Magnetic[] {
                        mag, broken, null }) {
            o = com.atakmap.android.blast.plugin.core.screen.Nbc1Form.obs(v,
                    LAT, LON, m);
            sb.append("obs (magnetic port ").append(m == mag ? "4.5W"
                    : m == broken ? "broken" : "none").append("): az=")
                    .append(Golden.num(o.azDeg, 3)).append(" cot: ")
                    .append(NucCodec.toXml(n.withObs(o))).append("\n");
        }
        o = com.atakmap.android.blast.plugin.core.screen.Nbc1Form.obs(v, LAT,
                LON, mag);
        sb.append("report:\n").append(com.atakmap.android.blast.plugin.core
                .screen.Nbc1Form.report(n.withObs(o), GRID2, GRID))
                .append("\n");
        sb.append("reopened:\n").append(ScreenDump.dump(com.atakmap.android
                .blast.plugin.core.screen.Nbc1Form.form(n.withObs(o), 1)));

        // Polar plot.
        f = com.atakmap.android.blast.plugin.core.screen.PolarForm.form();
        sb.append("\n=== polar: ").append(com.atakmap.android.blast.plugin
                .core.screen.PolarForm.TITLE).append("\n")
                .append(ScreenDump.dump(f));
        String[][] tries = { { "", "20", "true" }, { "400", "20", "true" },
                { "90", "", "true" }, { "90", "301", "true" },
                { "90", "20", "true" }, { "90", "20", "false" },
                { "359", "1", "false" } };
        for (String[] t : tries)
            for (com.atakmap.android.blast.plugin.core.port.Magnetic m
                    : new com.atakmap.android.blast.plugin.core.port.Magnetic[] {
                            mag, broken }) {
                v = com.atakmap.android.blast.plugin.core.screen.FormValues
                        .initial(f).put("az", t[0]).put("ftb", t[1])
                        .put("mag", Boolean.parseBoolean(t[2]));
                com.atakmap.android.blast.plugin.core.screen.PolarForm.Result
                        res = com.atakmap.android.blast.plugin.core.screen
                                .PolarForm.place(v, LAT, LON, m);
                sb.append("place az=").append(t[0]).append(" ftb=")
                        .append(t[1]).append(" mag=").append(t[2])
                        .append(m == mag ? " (4.5W)" : " (broken)")
                        .append(": ").append(res.error != null ? res.error
                                : Golden.ll(res.lat, res.lon) + " obs "
                                        + NucCodec.toXml(n.withObs(res.obs)))
                        .append("\n");
            }
        return sb.toString();
    }

    private static java.util.Map<String, String> withEd(
            java.util.Map<String, String> a, String ed) {
        a.put("ed", ed);
        return a;
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
