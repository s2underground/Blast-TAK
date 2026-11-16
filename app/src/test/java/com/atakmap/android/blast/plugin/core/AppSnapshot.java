package com.atakmap.android.blast.plugin.core;

import com.atakmap.android.blast.plugin.core.app.BlastApp;
import com.atakmap.android.blast.plugin.core.app.Outcome;
import com.atakmap.android.blast.plugin.core.screen.Dialog;
import com.atakmap.android.blast.plugin.core.screen.FormValues;
import com.atakmap.android.blast.plugin.core.screen.Item;
import com.atakmap.android.blast.plugin.core.screen.Screen;
import com.atakmap.android.blast.plugin.core.screen.Text;

/**
 * Refactor step 5c: the app controller through a session (selecting,
 * frames and what they redraw, list / card / tab / Reference buttons,
 * settings) against the logging fake ports.
 */
final class AppSnapshot {

    private AppSnapshot() {
    }

    private static void frame(StringBuilder sb, BlastApp app, String what) {
        BlastApp.Frame f = app.frame();
        sb.append("\n--- frame (").append(what).append("): card ")
                .append(f.cardChanged ? f.card == null ? "HIDDEN" : "NEW"
                        : "same")
                .append(", list ").append(f.list == null ? "same" : "NEW")
                .append("\n");
        if (f.cardChanged && f.card != null) {
            sb.append(ScreenDump.dump(f.header));
            int n = 0;
            for (Item it : f.card.items())
                if (n++ < 2)
                    sb.append(ScreenDump.item(it)).append("\n");
            sb.append("  … ").append(f.card.items().size())
                    .append(" items\n");
        }
        if (f.you != null)
            sb.append(ScreenDump.item(f.you)).append("\n")
                    .append(ScreenDump.item(f.team)).append("\n");
        if (f.list != null)
            sb.append(ScreenDump.dump(f.list));
    }

    private static Outcome tap(StringBuilder sb, BlastApp app, String action,
            FormValues v) {
        sb.append("\n=== tap ").append(action).append("\n");
        Outcome o = app.tap(action, v);
        ActionsSnapshot.out(sb, o);
        return o;
    }

    static String snapshot() {
        StringBuilder sb = new StringBuilder();
        ActionsSnapshot.World w = new ActionsSnapshot.World(sb);
        BlastApp app = new BlastApp(w, w, w, w, w, w, w, w, w);
        StandoffTable table = StandoffTable.current();
        BlastRecord car = BlastRecord.create(ZoneCalculator.forRow(table,
                "CAR"), GoldenSnapshotTest.BY, GoldenSnapshotTest.T);
        UxoRecord shell = UxoRecord.create("PROJ_MORTAR", false, false,
                UxoRecord.Where.PATH, UxoRecord.Placement.MAP,
                GoldenSnapshotTest.BY, GoldenSnapshotTest.T);
        NucRecord nuc = NucRecord.create(10, NucModel.Burst.SURFACE,
                "FEMA_10", 270, 15, GoldenSnapshotTest.BY,
                GoldenSnapshotTest.T);
        w.add("s1", "BLAST 1 Car", car, null, null);
        w.add("u1", "UXO 1 Shell", null, shell, null);
        w.add("n1", "NUC 1 10 kT", null, null, nuc);
        FormValues none = new FormValues();

        sb.append("settings: emergency ").append(app.emergencyNumber())
                .append(", auto-send ").append(app.autoSend()).append("\n");
        frame(sb, app, "start, no fix: distances from the map centre");
        frame(sb, app, "again");
        w.self = new double[] { GoldenSnapshotTest.LAT + 0.003,
                GoldenSnapshotTest.LON };
        frame(sb, app, "a fix arrives: distances from you");

        tap(sb, app, "sel:s1", none);
        frame(sb, app, "after selecting");
        frame(sb, app, "again");
        tap(sb, app, "sel:s1", none);
        frame(sb, app, "after tapping it again");
        tap(sb, app, "go:u1", none);
        frame(sb, app, "after Go to");
        tap(sb, app, "report", none);
        tap(sb, app, "why", none);
        tap(sb, app, "send_all", none);
        tap(sb, app, "fly", none);
        tap(sb, app, "nine:u1", none);
        tap(sb, app, "hide_selected", none);
        frame(sb, app, "after Hide");
        tap(sb, app, "why", none);

        tap(sb, app, "sel:n1", none);
        frame(sb, app, "nuclear card");
        tap(sb, app, "wind_from_2", none);
        frame(sb, app, "after the wind change");
        tap(sb, app, "wind_set_from", new FormValues().put(
                "wind_exact_from", "400"));
        tap(sb, app, "apply_edm", new FormValues().put("edm", "12"));
        tap(sb, app, "apply_edm", new FormValues().put("edm", "090016"));
        tap(sb, app, "pic_military", none);
        tap(sb, app, "pic_military", none);
        tap(sb, app, "copy_nbc3", none);
        tap(sb, app, "pic_civil", none);
        tap(sb, app, "copy_nbc3", none);
        tap(sb, app, "how_zones", none);
        tap(sb, app, "why", none);
        tap(sb, app, "what_to_do", none);
        tap(sb, app, "nbc1", none);
        tap(sb, app, "send_all", none);
        frame(sb, app, "after the nuclear buttons");

        tap(sb, app, "tab_reference", none);
        tap(sb, app, "tab_incidents", none);
        Outcome o = tap(sb, app, "change_emergency", none);
        for (String typed : new String[] { "  112 ", "   " }) {
            sb.append("choose Save [").append(typed).append("]\n");
            ActionsSnapshot.out(sb, app.choose(o.dialog,
                    BlastApp.SET_EMERGENCY, new FormValues().put(Dialog.FIELD,
                            typed)));
            sb.append("emergency now ").append(app.emergencyNumber())
                    .append("\n");
        }
        app.setAutoSend(true);
        sb.append("auto-send now ").append(app.autoSend()).append("\n");

        tap(sb, app, "del:u1", none);
        tap(sb, app, "go:gone", none);
        tap(sb, app, "nine:gone", none);
        tap(sb, app, "delete_cleared", none);
        sb.append("\n=== unknown action -> ")
                .append(app.tap("nonsense", none)).append("\n");

        w.map.remove("n1");
        sb.append("\nn1 removed elsewhere; afterDelete\n");
        app.afterDelete();
        sb.append("selected: ").append(app.selected()).append("\n");
        frame(sb, app, "after the open incident went away");

        sb.append("\n=== grid references\n");
        double lat = GoldenSnapshotTest.LAT, lon = GoldenSnapshotTest.LON;
        sb.append("grid ").append(app.grid(lat, lon)).append(" | mgrs10 ")
                .append(app.mgrs10(lat, lon)).append("\n");
        sb.append("NaN: [").append(app.grid(Double.NaN, lon)).append("] [")
                .append(app.mgrs10(lat, Double.NaN)).append("]\n");
        w.mgrsFails = true;
        sb.append("host can't format: grid ").append(app.grid(lat, lon))
                .append(" | mgrs10 [").append(app.mgrs10(lat, lon))
                .append("]\n");
        return sb.toString();
    }

    static String text(Text t) {
        return t == null ? "null" : t.text;
    }

    static int size(Screen s) {
        return s == null ? -1 : s.items().size();
    }
}
