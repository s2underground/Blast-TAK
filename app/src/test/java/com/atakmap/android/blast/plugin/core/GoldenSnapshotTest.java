package com.atakmap.android.blast.plugin.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import com.atakmap.android.blast.plugin.core.screen.Item;
import com.atakmap.android.blast.plugin.core.screen.Screen;
import com.atakmap.android.blast.plugin.core.screen.Text;

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
    public void wind() {
        Golden.check("wind.txt", windSnapshot());
    }

    @Test
    public void newIncident() {
        Golden.check("new-incident.txt", newIncidentSnapshot());
    }

    @Test
    public void pane() {
        Golden.check("pane.txt", paneSnapshot());
    }

    @Test
    public void live() {
        Golden.check("live.txt", liveSnapshot());
    }

    @Test
    public void actions() {
        Golden.check("actions.txt", ActionsSnapshot.snapshot());
    }

    @Test
    public void app() {
        Golden.check("app.txt", AppSnapshot.snapshot());
    }

    /** Refactor step 6b-2: the CoT events TAK-X sends. */
    @Test
    public void cotEvents() throws Exception {
        Golden.check("cot-events.txt", CotEventsSnapshot.snapshot());
    }

    /** Refactor step 5d: placing, the map tap and the forms. */
    @Test
    public void place() {
        Golden.check("place.txt", PlaceSnapshot.snapshot());
    }

    @Test
    public void reference() {
        Golden.check("reference.txt", referenceSnapshot());
    }

    /** The same output under another time zone and locale. */
    @Test
    public void deterministicAcrossLocales() throws Exception {
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
            Golden.check("wind.txt", windSnapshot());
            Golden.check("new-incident.txt", newIncidentSnapshot());
            Golden.check("pane.txt", paneSnapshot());
            Golden.check("live.txt", liveSnapshot());
            Golden.check("actions.txt", ActionsSnapshot.snapshot());
            Golden.check("app.txt", AppSnapshot.snapshot());
            Golden.check("place.txt", PlaceSnapshot.snapshot());
            Golden.check("cot-events.txt", CotEventsSnapshot.snapshot());
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
                "1e3", "-5", "60001", "2,5", "12.500,5" })
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

    /**
     * Refactor step 4e: the wind picker through a run of taps (what it
     * shows and what it reports after each), and the EDM entry.
     */
    static String windSnapshot() {
        StringBuilder sb = new StringBuilder();
        com.atakmap.android.blast.plugin.core.screen.WindPicker w =
                new com.atakmap.android.blast.plugin.core.screen.WindPicker(
                        Double.NaN, Double.NaN);
        sb.append("=== no wind\n").append(ScreenDump.dump(w.screen()));
        String[][] taps = { { "wind_from_1", "", "" },
                { "wind_speed_2", "", "" }, { "wind_set_from", "abc", "" },
                { "wind_set_from", "361", "" }, { "wind_set_from", "360", "" },
                { "wind_set_from", "247.6", "" },
                { "wind_set_speed", "", "0.5" }, { "wind_set_speed", "", "12" },
                { "wind_unit", "", "" }, { "wind_set_speed", "", "1" },
                { "wind_set_speed", "", "40" }, { "wind_unit", "", "" },
                { "wind_set_speed", "", "201" }, { "wind_none", "", "" },
                { "wind_set_from", "90", "" }, { "wind_from_8", "", "" },
                { "wind_speed_x", "", "" }, { "send_all", "", "" } };
        for (String[] t : taps) {
            com.atakmap.android.blast.plugin.core.screen.FormValues v =
                    new com.atakmap.android.blast.plugin.core.screen.FormValues()
                            .put(com.atakmap.android.blast.plugin.core.screen
                                    .WindPicker.EXACT_FROM, t[1])
                            .put(com.atakmap.android.blast.plugin.core.screen
                                    .WindPicker.EXACT_SPEED, t[2]);
            com.atakmap.android.blast.plugin.core.screen.WindPicker.Tap r =
                    w.tap(t[0], v);
            sb.append("\n=== tap ").append(t[0]);
            if (!t[1].isEmpty())
                sb.append(" from=[").append(t[1]).append("]");
            if (!t[2].isEmpty())
                sb.append(" speed=[").append(t[2]).append("]");
            sb.append(" -> ").append(r == null ? "not a wind action"
                    : (r.changed ? "changed" : "unchanged")
                            + (r.error == null ? "" : ", error: " + r.error))
                    .append("\nwind: ").append(Golden.num(w.fromDeg(), 1))
                    .append(" from, ").append(Golden.num(w.mph(), 2))
                    .append(" mph\n");
            if (r != null)
                sb.append(ScreenDump.dump(w.screen()));
        }
        w.set(Double.NaN, Double.NaN);
        sb.append("\n=== set none\n").append(ScreenDump.item(w.status()))
                .append("\n");
        for (double f : new double[] { 0, 11.2, 11.3, 348.7, 348.8, 359.9,
                -10, 725 })
            sb.append("compass ").append(Golden.num(f, 1)).append(": ")
                    .append(com.atakmap.android.blast.plugin.core.screen
                            .WindPicker.compassName(f)).append("\n");
        sb.append("\n=== EDM entry\n");
        for (String e : new String[] { "", "12", "090", "090016",
                "090016045", "0900161", "abcdef" }) {
            NucMil.Edm m = NucMil.parseEdm(e);
            sb.append("[").append(e).append("] -> ").append(m == null
                    ? com.atakmap.android.blast.plugin.core.screen.NucCard
                            .EDM_ERROR : "ok").append("\n");
        }
        return sb.toString();
    }

    /**
     * Refactor step 4f: the new-incident screens through a run of taps,
     * and the records they place.
     */
    static String newIncidentSnapshot() {
        StringBuilder sb = new StringBuilder();
        com.atakmap.android.blast.plugin.core.screen.NewIncident n =
                new com.atakmap.android.blast.plugin.core.screen.NewIncident(
                        StandoffTable.current());
        com.atakmap.android.blast.plugin.core.screen.NewIncident.Mode none =
                com.atakmap.android.blast.plugin.core.screen.NewIncident.Mode.NONE;
        sb.append("=== start\n").append(ScreenDump.dump(n.screen(none, null,
                false)));
        String first = StandoffTable.current().rows().get(0).id;
        String last = StandoffTable.current().rows().get(StandoffTable
                .current().rows().size() - 1).id;
        // { action, box values (id=value;...), F = full dump }
        String[][] taps = {
                { "mode_ied", "", "F" }, { "row_" + first, "", "F" },
                { "row_" + last, "", "" }, { "custom", "", "F" },
                { "qty_changed", "qty=800", "" },
                { "unit_kg", "qty=800", "" }, { "qty_changed", "qty=abc", "" },
                { "qty_changed", "qty=1,000,000", "" },
                { "qty_changed", "qty=0", "" }, { "unit_lb", "qty=50", "F" },
                { "auto_send", "auto_send=true", "" },
                { "ied_pick", "", "" }, { "ied_self", "", "" },
                { "ied_center", "", "" }, { "ied_target", "", "" },
                { "mode_uxo", "", "F" }, { "uxo_cat_UNKNOWN", "", "F" },
                { "uxo_large", "", "" }, { "uxo_multi", "uxo_multi=true", "" },
                { "uxo_cat_MISSILE_ROCKET", "", "" },
                { "uxo_where_AREA", "", "F" }, { "uxo_rb", "", "" },
                { "mode_nuc", "", "F" }, { "yield_changed", "yield=abc", "" },
                { "yield_changed", "yield=0.05", "" },
                { "yield_changed", "yield=20", "" }, { "nuc_mt", "yield=20", "" },
                { "nuc_mt", "yield=1.2", "" }, { "nuc_kt", "yield=1.2", "" },
                { "nuc_burst_SURFACE", "yield=1.2", "" },
                { "wind_from_6", "yield=1.2", "" },
                { "wind_set_speed", "yield=1.2;wind_exact_speed=9999", "" },
                { "nuc_presets_FIELDED", "", "" },
                { "nuc_presets_YIELD", "", "" }, { "nuc_polar", "", "" },
                { "uxo_nothing", "", "" }, { "row_", "", "" },
                { "nuc_burst_X", "", "" } };
        for (String[] t : taps) {
            com.atakmap.android.blast.plugin.core.screen.FormValues v =
                    new com.atakmap.android.blast.plugin.core.screen.FormValues();
            for (String kv : t[1].split(";")) {
                if (kv.isEmpty())
                    continue;
                String[] p = kv.split("=", 2);
                if (p[1].equals("true"))
                    v.put(p[0], true);
                else
                    v.put(p[0], p[1]);
            }
            com.atakmap.android.blast.plugin.core.screen.NewIncident.Result r =
                    n.tap(t[0], v);
            sb.append("\n=== tap ").append(t[0]);
            if (!t[1].isEmpty())
                sb.append(" [").append(t[1]).append("]");
            sb.append(" -> ");
            if (r == null) {
                sb.append("not this screen's\n");
                continue;
            }
            sb.append(r.request).append(" mode=").append(r.mode);
            if (r.group != null)
                sb.append(" group=").append(r.group);
            if (r.focus != null)
                sb.append(" focus=").append(r.focus);
            if (r.toast != null)
                sb.append(" toast=").append(r.toast);
            sb.append("\nnow ").append(n.mode()).append("; ready IED/UXO/NUC ")
                    .append(n.ready(com.atakmap.android.blast.plugin.core.screen
                            .NewIncident.Mode.IED)).append("/")
                    .append(n.ready(com.atakmap.android.blast.plugin.core.screen
                            .NewIncident.Mode.UXO)).append("/")
                    .append(n.ready(com.atakmap.android.blast.plugin.core.screen
                            .NewIncident.Mode.NUC)).append("\n");
            Screen sc = n.screen(none, null, false);
            if (t[2].equals("F")) {
                sb.append(ScreenDump.dump(sc));
            } else {
                for (String slot : new String[] { "ied_sel", "uxo_sel",
                        "nuc_sel" }) {
                    Item it = find(sc.items(), slot);
                    if (it != null)
                        sb.append(ScreenDump.item(it)).append("\n");
                }
            }
        }
        com.atakmap.android.blast.plugin.core.screen.NewIncident.Mode ied =
                com.atakmap.android.blast.plugin.core.screen.NewIncident.Mode.IED,
                uxo = com.atakmap.android.blast.plugin.core.screen.NewIncident.Mode.UXO,
                nuc = com.atakmap.android.blast.plugin.core.screen.NewIncident.Mode.NUC;
        sb.append("\n=== nuclear, picking, opened on a marker\n")
                .append(ScreenDump.dump(n.screen(nuc, "Rally Point 2",
                        true)));
        sb.append("\n=== records\n");
        sb.append(BlastCodec.toXml(n.standoffRecord(BY, T))).append("\n");
        sb.append(UxoCodec.toXml(n.uxoRecord(UxoRecord.Placement.RB, BY, T)))
                .append("\n");
        sb.append(NucCodec.toXml(n.nucRecord(null, BY, T))).append("\n");
        n.choosePreset(NucPresets.group(NucPresets.Group.PLANNING).get(0));
        sb.append("\n=== preset ").append(NucPresets.group(
                NucPresets.Group.PLANNING).get(0).id).append("\n");
        sb.append(ScreenDump.item(find(n.screen(none, null, false).items(),
                "nuc_sel"))).append("\n");
        for (Item it : n.screen(none, null, false).items())
            dumpEntries(sb, it);
        n.reset();
        sb.append("\n=== reset\n").append(ScreenDump.dump(n.screen(none,
                null, false)));
        for (com.atakmap.android.blast.plugin.core.screen.NewIncident.Mode m
                : new com.atakmap.android.blast.plugin.core.screen.NewIncident.Mode[] {
                        ied, uxo, nuc })
            sb.append("notReady ").append(m).append(": ")
                    .append(com.atakmap.android.blast.plugin.core.screen
                            .NewIncident.notReady(m))
                    .append("\nprompt ").append(m).append(": ")
                    .append(com.atakmap.android.blast.plugin.core.screen
                            .NewIncident.pickPrompt(m)).append("\n");
        sb.append("\n=== preset lists\n").append(com.atakmap.android.blast
                .plugin.core.screen.NewIncident.FIELDED_TITLE).append("\n");
        for (String i : com.atakmap.android.blast.plugin.core.screen
                .NewIncident.regionItems())
            sb.append("  ").append(i).append("\n");
        sb.append(com.atakmap.android.blast.plugin.core.screen.NewIncident
                .regionTitle(NucPresets.REGIONS[0])).append("\n");
        for (NucPresets.Group g : NucPresets.Group.values())
            if (g != NucPresets.Group.FIELDED) {
                sb.append(com.atakmap.android.blast.plugin.core.screen
                        .NewIncident.groupTitle(g)).append("\n");
                for (String i : com.atakmap.android.blast.plugin.core.screen
                        .NewIncident.presetItems(NucPresets.group(g),
                                g == NucPresets.Group.YIELD))
                    sb.append("  ").append(i).append("\n");
            }
        return sb.toString();
    }

    private static Item find(List<Item> items, String slot) {
        for (Item it : items) {
            if (slot.equals(it.slot))
                return it;
            if (it instanceof com.atakmap.android.blast.plugin.core.screen.Section) {
                Item f = find(((com.atakmap.android.blast.plugin.core.screen
                        .Section) it).items, slot);
                if (f != null)
                    return f;
            }
        }
        return null;
    }

    private static void dumpEntries(StringBuilder sb, Item it) {
        if (it instanceof com.atakmap.android.blast.plugin.core.screen.EntryRow)
            sb.append(ScreenDump.item(it)).append("\n");
        else if (it instanceof com.atakmap.android.blast.plugin.core.screen.Section)
            for (Item i : ((com.atakmap.android.blast.plugin.core.screen
                    .Section) it).items)
                dumpEntries(sb, i);
    }

    /**
     * Refactor step 4g: pane header and tabs, the incident list, the
     * Reference tab and the range & bearing form.
     */
    static String paneSnapshot() {
        StringBuilder sb = new StringBuilder();
        StandoffTable table = StandoffTable.current();
        for (boolean ref : new boolean[] { false, true })
            sb.append("=== header, reference=").append(ref).append("\n")
                    .append(ScreenDump.dump(com.atakmap.android.blast.plugin
                            .core.screen.MainPane.header(table, ref)));
        sb.append(ScreenDump.item(com.atakmap.android.blast.plugin.core.screen
                .MainPane.footer())).append("\n");

        sb.append("\n=== list placeholder\n").append(ScreenDump.dump(
                com.atakmap.android.blast.plugin.core.screen.IncidentList
                        .placeholder()));
        List<com.atakmap.android.blast.plugin.core.screen.IncidentList.Entry>
                none = new ArrayList<>();
        sb.append("\n=== list empty\n").append(ScreenDump.dump(
                com.atakmap.android.blast.plugin.core.screen.IncidentList
                        .screen(none, null)));

        BlastRecord car = BlastRecord.create(ZoneCalculator.forRow(table,
                "CAR"), BY, T);
        UxoRecord shell = UxoRecord.create("PROJ_MORTAR", false, false,
                UxoRecord.Where.PATH, UxoRecord.Placement.MAP, BY, T);
        NucRecord nuc = NucRecord.create(10, NucModel.Burst.SURFACE,
                "FEMA_10", 270, 15, BY, T);
        List<com.atakmap.android.blast.plugin.core.screen.IncidentList.Entry>
                es = new ArrayList<>();
        es.add(com.atakmap.android.blast.plugin.core.screen.IncidentList.Entry
                .of("u1", "IED 1", car, 2000));
        es.add(com.atakmap.android.blast.plugin.core.screen.IncidentList.Entry
                .of("u2", "IED 2 (cleared)", car.cleared("BRAVO-2", T), 5));
        es.add(com.atakmap.android.blast.plugin.core.screen.IncidentList.Entry
                .of("u3", "UXO 1", shell, 120));
        es.add(com.atakmap.android.blast.plugin.core.screen.IncidentList.Entry
                .of("u4", "UXO 2", shell, 900));
        es.add(com.atakmap.android.blast.plugin.core.screen.IncidentList.Entry
                .of("u5", "NUC 1", nuc, 1500));
        es.add(com.atakmap.android.blast.plugin.core.screen.IncidentList.Entry
                .of("u6", "NUC 2", nuc, 60000));
        es.add(com.atakmap.android.blast.plugin.core.screen.IncidentList.Entry
                .of("u7", "IED 3 (no position)", car, Double.NaN));
        es.add(com.atakmap.android.blast.plugin.core.screen.IncidentList.Entry
                .of("u8", "IED 4", car, 50));
        es.add(com.atakmap.android.blast.plugin.core.screen.IncidentList.Entry
                .of("u9", "NUC 3 (cleared)", nuc.cleared("BRAVO-2", T), 10));
        List<com.atakmap.android.blast.plugin.core.screen.IncidentList.Entry>
                sorted = com.atakmap.android.blast.plugin.core.screen
                        .IncidentList.sorted(es);
        sb.append("\n=== list, u3 selected\n").append(ScreenDump.dump(
                com.atakmap.android.blast.plugin.core.screen.IncidentList
                        .screen(sorted, "u3")));
        sb.append("signature: ").append(com.atakmap.android.blast.plugin.core
                .screen.IncidentList.signature(sorted, "u3")).append("\n");
        sb.append("uid of go:u5 = ").append(com.atakmap.android.blast.plugin
                .core.screen.IncidentList.uid("go:u5", "go:")).append(", of "
                        + "sel:u5 as go = ").append(com.atakmap.android.blast
                                .plugin.core.screen.IncidentList.uid("sel:u5",
                                        "go:")).append("\n");

        sb.append("\n=== reference tab\n").append(ScreenDump.dump(
                com.atakmap.android.blast.plugin.core.screen.ReferenceTab
                        .screen("112")));

        sb.append("\n=== range & bearing form\n").append(ScreenDump.dump(
                com.atakmap.android.blast.plugin.core.screen.RangeBearingForm
                        .form()));
        com.atakmap.android.blast.plugin.core.port.Magnetic mag =
                new com.atakmap.android.blast.plugin.core.port.Magnetic() {
                    @Override
                    public double toTrue(double m) {
                        return m - 4.5;
                    }
                };
        com.atakmap.android.blast.plugin.core.port.Magnetic broken =
                new com.atakmap.android.blast.plugin.core.port.Magnetic() {
                    @Override
                    public double toTrue(double m) {
                        return Double.NaN;
                    }
                };
        String[][] cases = { { "", "90", "", "" }, { "0.5", "90", "", "" },
                { "20001", "90", "", "" }, { "3", "90", "feet", "" },
                { "250", "", "", "" }, { "250", "361", "", "" },
                { "250", "90", "", "" }, { "250", "90", "", "mag" },
                { "1,000", "359.5", "feet", "mag" }, { "250", "360", "", "" } };
        for (String[] c : cases)
            for (com.atakmap.android.blast.plugin.core.port.Magnetic m
                    : new com.atakmap.android.blast.plugin.core.port.Magnetic[] {
                            mag, broken }) {
                com.atakmap.android.blast.plugin.core.screen.FormValues v =
                        new com.atakmap.android.blast.plugin.core.screen
                                .FormValues().put("dist", c[0]).put("brg",
                                        c[1]).put("feet", !c[2].isEmpty())
                                .put("mag", !c[3].isEmpty());
                com.atakmap.android.blast.plugin.core.screen.RangeBearingForm
                        .Result r = com.atakmap.android.blast.plugin.core
                                .screen.RangeBearingForm.place(v, LAT, LON, m);
                sb.append("rb dist=[").append(c[0]).append("] brg=[")
                        .append(c[1]).append("]").append(c[2].isEmpty() ? ""
                                : " ft").append(c[3].isEmpty() ? " true"
                                        : " magnetic").append(m == broken
                                                ? " (no declination)" : "")
                        .append(" -> ").append(r.error != null ? r.error
                                : Golden.ll(r.lat, r.lon)).append("\n");
            }
        return sb.toString();
    }

    private static com.atakmap.android.blast.plugin.core.port.PositionSource at(
            final double lat, final double lon, final double ce) {
        return new com.atakmap.android.blast.plugin.core.port.PositionSource() {
            @Override
            public Fix fix() {
                return Double.isNaN(lat) ? null : new Fix(lat, lon, ce);
            }
        };
    }

    private static com.atakmap.android.blast.plugin.core.port.Team team(
            final double[]... pts) {
        return new com.atakmap.android.blast.plugin.core.port.Team() {
            @Override
            public String callsign() {
                return BY;
            }

            @Override
            public List<double[]> others() {
                return new ArrayList<>(Arrays.asList(pts));
            }
        };
    }

    /**
     * Refactor step 5a: the YOU and Team lines from the position and team
     * ports, for each kind of incident, at a range of offsets.
     */
    static String liveSnapshot() {
        StringBuilder sb = new StringBuilder();
        StandoffTable table = StandoffTable.current();
        BlastRecord car = BlastRecord.create(ZoneCalculator.forRow(table,
                "CAR"), BY, T);
        UxoRecord shell = UxoRecord.create("PROJ_MORTAR", false, false,
                UxoRecord.Where.PATH, UxoRecord.Placement.RB, BY, T);
        NucRecord nuc = NucRecord.create(10, NucModel.Burst.SURFACE,
                "FEMA_10", 270, 15, BY, T);
        // Offsets (azimuth deg, distance m) from the incident.
        double[][] offs = { { 0, 0 }, { 0, 50 }, { 45, 120 }, { 90, 380 },
                { 180, 600 }, { 270, 1500 }, { 90, 3000 }, { 80, 20000 },
                { 100, 60000 }, { 315, 90000 } };
        double[] ces = { Double.NaN, 5, 60, 400 };
        com.atakmap.android.blast.plugin.core.port.Team crowd = team(
                Geo.destination(LAT, LON, 10, 30), Geo.destination(LAT, LON,
                        200, 700), Geo.destination(LAT, LON, 95, 25000),
                Geo.destination(LAT, LON, 0, 200000));
        com.atakmap.android.blast.plugin.core.port.Team nobody = team();
        String[] kinds = { "standoff CAR", "uxo PROJ_MORTAR (RB)",
                "nuclear 10 kt surface, wind from 270 at 15 mph" };
        for (int k = 0; k < 3; k++) {
            for (boolean cleared : new boolean[] { false, true }) {
                sb.append("\n=== ").append(kinds[k]).append(cleared
                        ? ", cleared" : "").append("\n");
                for (double[] o : offs)
                    for (int j = 0; j < ces.length; j++) {
                        double ce = ces[j];
                        if (j > 0 && o[1] != 50 && o[1] != 380)
                            continue;
                        double[] p = Geo.destination(LAT, LON, o[0], o[1]);
                        Text[] t = live(k, cleared, car, shell, nuc,
                                at(p[0], p[1], ce), crowd);
                        sb.append("at ").append(Golden.num(o[0], 0))
                                .append("° ").append(Golden.num(o[1], 0))
                                .append(" m, CE ").append(Golden.num(ce, 0))
                                .append(":\n").append(ScreenDump.item(t[0]))
                                .append("\n");
                    }
                Text[] none = live(k, cleared, car, shell, nuc,
                        at(Double.NaN, 0, 0), nobody);
                sb.append("no fix, nobody:\n").append(ScreenDump.item(
                        none[0])).append("\n").append(ScreenDump.item(
                                none[1])).append("\n");
                Text[] t = live(k, cleared, car, shell, nuc, null, crowd);
                sb.append("team of 4:\n").append(ScreenDump.item(t[1]))
                        .append("\n");
            }
        }
        return sb.toString();
    }

    private static Text[] live(int kind, boolean cleared, BlastRecord car,
            UxoRecord shell, NucRecord nuc,
            com.atakmap.android.blast.plugin.core.port.PositionSource pos,
            com.atakmap.android.blast.plugin.core.port.Team team) {
        if (kind == 0)
            return com.atakmap.android.blast.plugin.core.screen.Live.standoff(
                    cleared ? car.cleared("BRAVO-2", T) : car, LAT, LON, pos,
                    team);
        if (kind == 1)
            return com.atakmap.android.blast.plugin.core.screen.Live.uxo(
                    cleared ? shell.cleared("BRAVO-2", T) : shell, LAT, LON,
                    pos, team);
        return com.atakmap.android.blast.plugin.core.screen.Live.nuc(cleared
                ? nuc.cleared("BRAVO-2", T) : nuc, LAT, LON, pos, team);
    }
}
