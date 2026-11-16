package com.atakmap.android.blast.plugin.core;

import com.atakmap.android.blast.plugin.core.app.BlastApp;
import com.atakmap.android.blast.plugin.core.app.FormDialog;
import com.atakmap.android.blast.plugin.core.app.Outcome;
import com.atakmap.android.blast.plugin.core.port.PositionSource;
import com.atakmap.android.blast.plugin.core.screen.Dialog;
import com.atakmap.android.blast.plugin.core.screen.FormValues;

import java.util.Map;

/**
 * Refactor step 5d: the new-incident section, placing (target marker, own
 * position, map centre, map tap, range and bearing, polar plot), the
 * nuclear preset lists and the NBC 1 and 9-line forms, through a session
 * against the logging fake ports.
 */
final class PlaceSnapshot {

    private PlaceSnapshot() {
    }

    private static final FormValues NONE = new FormValues();

    private static Outcome tap(StringBuilder sb, BlastApp app, String action,
            FormValues v) {
        sb.append("\n=== new: ").append(action).append("\n");
        Outcome o = app.tapNew(action, v);
        ActionsSnapshot.out(sb, o);
        return o;
    }

    private static Outcome tap(StringBuilder sb, BlastApp app,
            String action) {
        return tap(sb, app, action, NONE);
    }

    /** The section's lines that contain any of {@code keys}. */
    private static void section(StringBuilder sb, BlastApp app, String what,
            String... keys) {
        sb.append("--- section (").append(what).append(")\n");
        for (String line : ScreenDump.dump(app.newIncident(null)).split("\n"))
            for (String k : keys)
                if (line.contains(k)) {
                    sb.append(line).append("\n");
                    break;
                }
    }

    private static Outcome press(StringBuilder sb, FormDialog f, String button,
            FormValues v) {
        sb.append("press ").append(button).append("\n");
        Outcome o = f.press(button, v);
        ActionsSnapshot.out(sb, o);
        return o;
    }

    private static void live(StringBuilder sb, FormDialog f, FormValues v) {
        for (Map.Entry<String, String> e : f.live(v).entrySet())
            sb.append("  live ").append(e.getKey()).append(": ")
                    .append(e.getValue().replace("\n", "\\n")).append("\n");
    }

    private static Outcome choose(StringBuilder sb, BlastApp app, Dialog d,
            int item) {
        sb.append("choose line ").append(item).append(" of ").append(d.title)
                .append("\n");
        Outcome o = app.choose(d, d.itemAction, new FormValues().put(
                Dialog.ITEM, Integer.toString(item)));
        ActionsSnapshot.out(sb, o);
        return o;
    }

    static String snapshot() {
        StringBuilder sb = new StringBuilder();
        ActionsSnapshot.World w = new ActionsSnapshot.World(sb);
        BlastApp app = new BlastApp(w, w, w, w, w, w, w, w, w);
        StandoffTable table = StandoffTable.current();
        w.add("u1", "UXO 1 Shell", null, UxoRecord.create("PROJ_MORTAR",
                false, false, UxoRecord.Where.PATH, UxoRecord.Placement.MAP,
                GoldenSnapshotTest.BY, GoldenSnapshotTest.T), null);
        NucRecord nuc = NucRecord.create(10, NucModel.Burst.SURFACE,
                "FEMA_10", 270, 15, GoldenSnapshotTest.BY,
                GoldenSnapshotTest.T);
        w.add("n1", "NUC 1 10 kT", null, null, nuc);
        String keys[] = { "Place at", "Tap map", "Cancel", "[x]", "slot=" };

        sb.append("=== the section at the start\n");
        sb.append(ScreenDump.dump(app.newIncident(null)));

        // ---- Suspicious device
        tap(sb, app, "mode_ied");
        tap(sb, app, "ied_self");
        w.self = new double[] { GoldenSnapshotTest.LAT + 0.003,
                GoldenSnapshotTest.LON };
        tap(sb, app, "ied_self");
        tap(sb, app, "ied_pick");
        tap(sb, app, "custom");
        tap(sb, app, "qty_changed", new FormValues().put("qty", "abc"));
        tap(sb, app, "row_CAR");
        section(sb, app, "car chosen", keys);
        tap(sb, app, "ied_self");
        sb.append("selected: ").append(app.selected()).append("\n");
        section(sb, app, "after placing: a fresh start", keys);

        tap(sb, app, "mode_ied");
        tap(sb, app, "row_PIPE_BOMB");
        tap(sb, app, "auto_send", new FormValues().put("auto_send", true));
        tap(sb, app, "ied_pick");
        section(sb, app, "waiting for a map tap", keys);
        tap(sb, app, "ied_pick");
        tap(sb, app, "ied_pick");
        sb.append("\n=== map tap on a marker (with height)\n");
        ActionsSnapshot.out(sb, app.picked(new PositionSource.Fix(
                GoldenSnapshotTest.LAT, GoldenSnapshotTest.LON + 0.002,
                Double.NaN, 182.5, 4, 9), true, NONE));
        sb.append("\n=== map tap when not waiting\n");
        ActionsSnapshot.out(sb, app.picked(PositionSource.Fix.at(1, 2), false,
                NONE));

        tap(sb, app, "mode_ied");
        tap(sb, app, "row_BRIEFCASE");
        tap(sb, app, "ied_pick");
        sb.append("\n=== map tap with no usable point\n");
        ActionsSnapshot.out(sb, app.picked(null, false, NONE));
        tap(sb, app, "ied_pick");
        sb.append("\n=== the pane stops the map tap itself\n");
        app.pickCancelled();
        section(sb, app, "after the pane cancelled", keys);
        double[] centre = w.centre;
        w.centre = null;
        tap(sb, app, "ied_center");
        w.centre = centre;
        w.failCreate = true;
        tap(sb, app, "ied_center");
        w.failCreate = false;
        tap(sb, app, "ied_center");
        app.setAutoSend(false);

        sb.append("\n=== opened on a marker\n");
        final double[] alpha = { GoldenSnapshotTest.LAT - 0.004,
                GoldenSnapshotTest.LON + 0.001 };
        app.target("Alpha 1", new PositionSource() {
            @Override
            public Fix fix() {
                return alpha == null ? null : Fix.at(alpha[0], alpha[1]);
            }
        });
        tap(sb, app, "mode_ied");
        section(sb, app, "target offered, no threat", keys);
        tap(sb, app, "row_SUV_VAN");
        tap(sb, app, "ied_target");
        section(sb, app, "target used up", keys);
        tap(sb, app, "ied_target");

        // ---- Military ordnance
        tap(sb, app, "mode_uxo");
        tap(sb, app, "uxo_cat_PROJ_MORTAR");
        tap(sb, app, "uxo_rb");
        tap(sb, app, "uxo_where_PATH");
        w.self = null;
        tap(sb, app, "uxo_rb");
        w.self = new double[] { GoldenSnapshotTest.LAT + 0.003,
                GoldenSnapshotTest.LON };
        FormDialog rb = tap(sb, app, "uxo_rb").form;
        live(sb, rb, NONE);
        press(sb, rb, FormDialog.POSITIVE, new FormValues().put("dist", "0")
                .put("brg", "90"));
        w.noDeclination = true;
        press(sb, rb, FormDialog.POSITIVE, new FormValues().put("dist", "300")
                .put("brg", "90").put("mag", true));
        w.noDeclination = false;
        press(sb, rb, FormDialog.POSITIVE, new FormValues().put("dist", "300")
                .put("brg", "90").put("mag", true));
        sb.append("a second press on the same form (choices were reset):\n");
        press(sb, rb, FormDialog.POSITIVE, new FormValues().put("dist", "300")
                .put("brg", "90").put("mag", false));

        // ---- Nuclear
        tap(sb, app, "mode_nuc");
        tap(sb, app, "nuc_polar");
        Dialog fielded = tap(sb, app, "nuc_presets_FIELDED").dialog;
        Dialog region = choose(sb, app, fielded, 0).dialog;
        choose(sb, app, region, 0);
        section(sb, app, "a fielded preset chosen", "slot=nuc_sel");
        Dialog fema = tap(sb, app, "nuc_presets_PLANNING").dialog;
        choose(sb, app, fema, 99);
        choose(sb, app, fema, 1);
        section(sb, app, "a FEMA preset chosen", "slot=nuc_sel");
        tap(sb, app, "nuc_presets_YIELD");
        tap(sb, app, "nuc_presets_HISTORICAL");
        tap(sb, app, "yield_changed", new FormValues().put("yield", "20"));
        tap(sb, app, "nuc_burst_AIR");
        FormDialog polar = tap(sb, app, "nuc_polar").form;
        press(sb, polar, FormDialog.POSITIVE, new FormValues().put("az", "45")
                .put("mag", false).put("ftb", ""));
        press(sb, polar, FormDialog.NEUTRAL, NONE);

        sb.append("\n=== a typed wind survives a failed map-tap placement\n");
        tap(sb, app, "nuc_pick", new FormValues().put("yield", "20"));
        w.failCreate = true;
        ActionsSnapshot.out(sb, app.picked(PositionSource.Fix.at(
                GoldenSnapshotTest.LAT, GoldenSnapshotTest.LON), false,
                new FormValues().put("wind_exact_from", "123")));
        w.failCreate = false;
        section(sb, app, "wind box kept", "wind_exact_from");

        press(sb, polar, FormDialog.POSITIVE, new FormValues().put("az", "45")
                .put("mag", false).put("ftb", "12"));
        NucRecord placed = w.get("new" + w.created).nuc;
        sb.append("placed by polar plot: flash-to-bang ")
                .append(placed.obs.flashToBangS).append(" s, azimuth ")
                .append(placed.obs.azDeg).append(", ")
                .append(placed.obs.gzQualifier).append("\n");

        // ---- NBC 1 and 9-line from the cards
        sb.append("\n=== NBC 1 on n1\n");
        app.tap("sel:n1", NONE);
        app.frame();
        FormDialog nbc1 = app.tap("nbc1", NONE).form;
        sb.append(ActionsSnapshot.form(nbc1));
        FormValues obs = FormValues.initial(nbc1.form).put("az", "100")
                .put("mag", true).put("ftb", "9").put("kilo", "CRATER")
                .put("so", "OBS").put("del", "MSL").put("con", "MSL");
        live(sb, nbc1, obs);
        press(sb, nbc1, FormDialog.NEUTRAL, obs);
        press(sb, nbc1, FormDialog.POSITIVE, obs);
        app.frame();
        FormDialog again = app.tap("nbc1", NONE).form;
        for (String line : ScreenDump.dump(again.form).split("\n"))
            if (line.contains("BRAVO") || line.contains("Magnetic"))
                sb.append(line).append("\n");
        w.map.remove("n1");
        press(sb, nbc1, FormDialog.POSITIVE, obs);
        ActionsSnapshot.out(sb, app.tap("nbc1", NONE));

        sb.append("\n=== 9-line on u1\n");
        Outcome nine = app.tap("nine:u1", NONE);
        ActionsSnapshot.out(sb, nine);
        FormDialog rep = nine.form;
        FormValues nv = FormValues.initial(rep.form).put("pri", "IMMEDIATE")
                .put("count", "2").put("desc", "Two mortar rounds");
        live(sb, rep, nv);
        press(sb, rep, FormDialog.NEUTRAL, nv);
        press(sb, rep, FormDialog.POSITIVE, nv);
        app.frame();
        ActionsSnapshot.out(sb, app.tap("report", NONE));
        sb.append("left: ").append(w.map.keySet()).append("\n");
        return sb.toString();
    }
}
