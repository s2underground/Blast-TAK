package com.atakmap.android.blast.plugin.core;

import com.atakmap.android.blast.plugin.core.app.IncidentActions;
import com.atakmap.android.blast.plugin.core.port.Clipboard;
import com.atakmap.android.blast.plugin.core.port.Clock;
import com.atakmap.android.blast.plugin.core.port.Files;
import com.atakmap.android.blast.plugin.core.port.Incidents;
import com.atakmap.android.blast.plugin.core.port.Share;
import com.atakmap.android.blast.plugin.core.port.Team;
import com.atakmap.android.blast.plugin.core.screen.CardActions;
import com.atakmap.android.blast.plugin.core.screen.Dialog;
import com.atakmap.android.blast.plugin.core.screen.FormValues;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Refactor step 5b: the incident buttons and dialogs run against fake
 * ports that log every call, so the golden file shows what each action
 * asks the host to do.
 */
final class ActionsSnapshot {

    private ActionsSnapshot() {
    }

    static final class World implements Incidents, Share, Team, Clock,
            Clipboard, Files {
        final Map<String, Incident> map = new LinkedHashMap<>();
        final StringBuilder log;
        boolean failSend, failWrite, noClipboard, failFiles;
        long now = GoldenSnapshotTest.T;

        World(StringBuilder log) {
            this.log = log;
        }

        void add(String uid, String name, BlastRecord s, UxoRecord u,
                NucRecord n) {
            map.put(uid, new Incident(uid, name + " (shown)", name,
                    GoldenSnapshotTest.LAT, GoldenSnapshotTest.LON, s, u, n));
        }

        @Override
        public List<Incident> all() {
            return new ArrayList<>(map.values());
        }

        @Override
        public Incident get(String uid) {
            return map.get(uid);
        }

        private boolean put(String uid, BlastRecord s, UxoRecord u,
                NucRecord n, String what) {
            Incident in = map.get(uid);
            log.append("  port: write ").append(uid).append(" ").append(what)
                    .append("\n");
            if (in == null || failWrite)
                return false;
            map.put(uid, new Incident(uid, in.displayName, in.name, in.lat,
                    in.lon, s, u, n));
            return true;
        }

        @Override
        public boolean write(String uid, BlastRecord r) {
            return put(uid, r, null, null, BlastCodec.toXml(r));
        }

        @Override
        public boolean write(String uid, UxoRecord r) {
            return put(uid, null, r, null, UxoCodec.toXml(r));
        }

        @Override
        public boolean write(String uid, NucRecord r) {
            return put(uid, null, null, r, NucCodec.toXml(r));
        }

        @Override
        public boolean rename(String uid, String typed) {
            log.append("  port: rename ").append(uid).append(" [")
                    .append(typed).append("]\n");
            Incident in = map.get(uid);
            String clean = Names.sanitize(typed);
            if (in == null || clean == null)
                return false;
            String name = Names.withStatus(clean, in.cleared());
            map.put(uid, new Incident(uid, name, name, in.lat, in.lon,
                    in.standoff, in.uxo, in.nuc));
            return true;
        }

        @Override
        public void delete(String uid) {
            log.append("  port: delete here ").append(uid).append("\n");
            map.remove(uid);
        }

        @Override
        public Map<String, String> events(String uid) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put(uid, "<event uid='" + uid + "'/>");
            return m;
        }

        @Override
        public int sendToAll(String uid) {
            log.append("  port: send to all ").append(uid).append("\n");
            if (failSend)
                throw new IllegalStateException("no network");
            return 3;
        }

        @Override
        public void sendTo(String uid) {
            log.append("  port: send to (picker) ").append(uid).append("\n");
        }

        @Override
        public void deleteForEveryone(String uid) {
            log.append("  port: delete for everyone ").append(uid)
                    .append("\n");
            map.remove(uid);
        }

        @Override
        public String callsign() {
            return "CHARLIE-3";
        }

        @Override
        public List<double[]> others() {
            return new ArrayList<>();
        }

        @Override
        public long nowMillis() {
            return now;
        }

        @Override
        public boolean copy(String label, String text) {
            if (noClipboard)
                return false;
            log.append("  port: copy [").append(label).append("] ")
                    .append(text.length()).append(" chars\n");
            return true;
        }

        @Override
        public String save(String fileName, byte[] data) throws IOException {
            log.append("  port: save ").append(fileName).append(" (")
                    .append(data.length > 0 ? "non-empty" : "EMPTY")
                    .append(")\n");
            if (failFiles && fileName.endsWith(".kmz"))
                throw new IOException("disk full");
            return fileName;
        }

        @Override
        public String folderLabel() {
            return "export/Blast";
        }
    }

    static String dialog(Dialog d) {
        StringBuilder sb = new StringBuilder("  dialog: ").append(d.title);
        if (d.uid != null)
            sb.append(" [about ").append(d.uid).append("]");
        if (!d.uids.isEmpty())
            sb.append(" [about ").append(d.uids).append("]");
        sb.append("\n");
        if (d.message != null)
            sb.append("    message: ").append(d.message.replace("\n", "\\n"))
                    .append("\n");
        if (d.hasField())
            sb.append("    field ").append(d.fieldKind).append(" max=")
                    .append(d.fieldMaxLength).append(d.fieldSelectAll
                            ? " select-all" : "").append(" = [")
                    .append(d.fieldValue).append("]\n");
        if (d.options != null)
            for (int i = 0; i < d.options.size(); i++)
                sb.append("    [").append(d.checked(i) ? "x" : " ")
                        .append("] ").append(d.options.get(i)).append("\n");
        if (d.positive != null)
            sb.append("    positive: ").append(d.positive).append(" -> ")
                    .append(d.positiveAction).append("\n");
        if (d.neutral != null)
            sb.append("    neutral: ").append(d.neutral).append(" -> ")
                    .append(d.neutralAction).append("\n");
        if (d.negative != null)
            sb.append("    negative: ").append(d.negative).append("\n");
        return sb.toString();
    }

    static void out(StringBuilder sb, IncidentActions.Outcome o) {
        if (o == null) {
            sb.append("  -> null (not a shared card action)\n");
            return;
        }
        if (o.toast != null)
            sb.append("  toast: ").append(o.toast).append("\n");
        if (o.dialog != null)
            sb.append(dialog(o.dialog));
        StringBuilder f = new StringBuilder();
        if (o.card)
            f.append(" card");
        if (o.list)
            f.append(" list");
        if (o.gone)
            f.append(" gone");
        if (o.flyTo != null)
            f.append(" fly-to=").append(o.flyTo);
        if (f.length() > 0)
            sb.append("  redraw:").append(f).append("\n");
    }

    static String snapshot() {
        StringBuilder sb = new StringBuilder();
        World w = new World(sb);
        IncidentActions a = new IncidentActions(w, w, w, w, w, w);
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
        w.add("s2", "BLAST 2 CLEARED", car.cleared("BRAVO-2",
                GoldenSnapshotTest.T), null, null);

        String[] acts = { CardActions.SEND_ALL, CardActions.SEND_TO,
                CardActions.FLY, CardActions.RENAME, CardActions.EXPORT,
                CardActions.STATUS, CardActions.DELETE, "nbc1" };
        for (String uid : new String[] { "s1", "u1", "n1", "s2", "gone" })
            for (String act : acts) {
                sb.append("\n=== ").append(uid).append(" ").append(act)
                        .append("\n");
                out(sb, a.card(uid, act));
            }

        sb.append("\n=== send failure\n");
        w.failSend = true;
        out(sb, a.card("s1", CardActions.SEND_ALL));
        IncidentActions.Outcome o = a.card("u1", CardActions.SEND_ALL);
        out(sb, a.choose(o.dialog, IncidentActions.SEND_CONFIRMED,
                new FormValues()));
        w.failSend = false;
        sb.append("\n=== confirm UXO send\n");
        out(sb, a.choose(o.dialog, IncidentActions.SEND_CONFIRMED,
                new FormValues()));

        sb.append("\n=== rename\n");
        Dialog rn = a.card("s1", CardActions.RENAME).dialog;
        for (String typed : new String[] { "   ", "Main gate <car>" })
            out(sb, a.choose(rn, IncidentActions.RENAME_LOCAL,
                    new FormValues().put(Dialog.FIELD, typed)));
        out(sb, a.choose(rn, IncidentActions.RENAME_SEND, new FormValues()
                .put(Dialog.FIELD, "Gate 2")));

        sb.append("\n=== export\n");
        Dialog ex = a.card("n1", CardActions.EXPORT).dialog;
        out(sb, a.choose(ex, IncidentActions.EXPORT_SAVE, new FormValues()));
        w.failFiles = true;
        out(sb, a.choose(ex, IncidentActions.EXPORT_SAVE, new FormValues()
                .put(Dialog.option(0), false)));
        w.failFiles = false;
        out(sb, a.choose(ex, IncidentActions.EXPORT_SAVE, new FormValues()
                .put(Dialog.option(0), false).put(Dialog.option(1), false)
                .put(Dialog.option(2), false)));
        for (String uid : new String[] { "s1", "u1" })
            out(sb, a.choose(a.card(uid, CardActions.EXPORT).dialog,
                    IncidentActions.EXPORT_SAVE, new FormValues()));

        sb.append("\n=== status\n");
        w.now = GoldenSnapshotTest.T + 3600000;
        Dialog st = a.card("u1", CardActions.STATUS).dialog;
        out(sb, a.choose(st, st.neutralAction, new FormValues()));
        out(sb, a.card("u1", CardActions.STATUS));
        Dialog st2 = a.card("u1", CardActions.STATUS).dialog;
        out(sb, a.choose(st2, st2.positiveAction, new FormValues()));
        w.failWrite = true;
        Dialog st3 = a.card("n1", CardActions.STATUS).dialog;
        out(sb, a.choose(st3, st3.positiveAction, new FormValues()));
        w.failWrite = false;

        sb.append("\n=== saves and copy\n");
        out(sb, a.saveWind("n1", 90, 8));
        out(sb, a.saveWind("n1", Double.NaN, Double.NaN));
        out(sb, a.saveWind("s1", 90, 8));
        out(sb, a.saveNuc("n1", nuc.withNumber(7), "Saved."));
        out(sb, a.saveNuc("n1", nuc, null));
        out(sb, a.saveReport("u1", UxoRecord.Report.EMPTY));
        out(sb, a.saveReport("gone", UxoRecord.Report.EMPTY));
        out(sb, a.copy("NBC 3 NUC ..."));
        w.noClipboard = true;
        out(sb, a.copy("NBC 3 NUC ..."));

        sb.append("\n=== delete\n");
        Dialog del = a.card("s2", CardActions.DELETE).dialog;
        out(sb, a.choose(del, IncidentActions.DELETE_LOCAL,
                new FormValues()));
        out(sb, a.choose(del, IncidentActions.DELETE_ALL, new FormValues()));
        out(sb, a.choose(a.card("n1", CardActions.DELETE).dialog,
                IncidentActions.DELETE_ALL, new FormValues()));

        sb.append("\n=== delete all cleared\n");
        w.add("s3", "BLAST 3 CLEARED", car.cleared("BRAVO-2",
                GoldenSnapshotTest.T), null, null);
        Dialog dc = a.deleteCleared().dialog;
        sb.append(dialog(dc));
        out(sb, a.choose(dc, IncidentActions.DELETE_CLEARED_ALL,
                new FormValues()));
        out(sb, a.deleteCleared());
        sb.append("left: ").append(w.map.keySet()).append("\n");
        sb.append("formats: ").append(IncidentActions.formatLabels())
                .append("\n");
        return sb.toString();
    }
}
