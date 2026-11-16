package com.atakmap.android.blast.plugin.core.app;

import com.atakmap.android.blast.plugin.core.Geo;
import com.atakmap.android.blast.plugin.core.NucRecord;
import com.atakmap.android.blast.plugin.core.UxoRecord;
import com.atakmap.android.blast.plugin.core.port.Incidents;
import com.atakmap.android.blast.plugin.core.port.Magnetic;
import com.atakmap.android.blast.plugin.core.port.PositionSource;
import com.atakmap.android.blast.plugin.core.port.Team;
import com.atakmap.android.blast.plugin.core.screen.FormValues;
import com.atakmap.android.blast.plugin.core.screen.Nbc1Form;
import com.atakmap.android.blast.plugin.core.screen.UxoReportForm;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The report forms opened from an incident card: NBC 1 NUC (nuclear) and
 * the EOD 9-line (UXO), each saved on the incident or copied as text.
 * Owned by {@link BlastApp}.
 */
final class CardForms {

    static final String NBC1_SAVED = "Observations saved on the incident.";

    private final BlastApp app;
    private final Incidents incidents;
    private final PositionSource position;
    private final Team team;
    private final IncidentActions actions;
    private final Placement placement;

    /** BRAVO as typed at the last NBC 1 save, per incident (this run). */
    private final Map<String, Nbc1Form.Entry> nbc1Entries = new HashMap<>();

    CardForms(BlastApp app, Incidents incidents, PositionSource position,
            Team team, IncidentActions actions, Placement placement) {
        this.app = app;
        this.incidents = incidents;
        this.position = position;
        this.team = team;
        this.actions = actions;
        this.placement = placement;
    }

    private static Outcome gone() {
        return new Outcome(IncidentActions.GONE, null, false, false, true,
                null);
    }

    /** NBC 1 NUC report builder (FM 3-11.3 Fig. G-3), saved on the incident. */
    Outcome nbc1(final String uid) {
        Incidents.Incident in = uid == null ? null : incidents.get(uid);
        if (in == null)
            return gone();
        final NucRecord r0 = in.nuc;
        if (r0 == null)
            return Outcome.NOTHING;
        PositionSource.Fix self = position == null ? null : position.fix();
        final double selfLat = self == null ? Double.NaN : self.lat;
        final double selfLon = self == null ? Double.NaN : self.lon;
        final Magnetic mag = self == null ? null
                : placement.magneticAt(self.lat, self.lon);
        final String obsGrid = app.mgrs10(selfLat, selfLon);
        final String gzGrid = app.mgrs10(in.lat, in.lon);
        double az = self == null ? Double.NaN
                : Geo.bearing(selfLat, selfLon, in.lat, in.lon);
        return Outcome.form(new FormDialog(Nbc1Form.TITLE, Nbc1Form.form(r0,
                az, nbc1Entries.get(uid)), true, 14, 6, "Save", "Copy text",
                "Cancel", new FormDialog.Logic() {
                    private NucRecord built(FormValues v) {
                        return r0.withObs(Nbc1Form.obs(v, selfLat, selfLon,
                                mag));
                    }

                    @Override
                    public Map<String, String> live(FormValues v) {
                        Map<String, String> m = new LinkedHashMap<>();
                        m.put(Nbc1Form.SLOT_PREVIEW, Nbc1Form.preview(
                                built(v), obsGrid, gzGrid));
                        return m;
                    }

                    @Override
                    public Outcome press(String button, FormValues v) {
                        NucRecord b = built(v);
                        if (FormDialog.NEUTRAL.equals(button))
                            return actions.copy(Nbc1Form.report(b, obsGrid,
                                    gzGrid));
                        if (!FormDialog.POSITIVE.equals(button))
                            return Outcome.NOTHING;
                        Incidents.Incident cur = incidents.get(uid);
                        if (cur == null)
                            return gone();
                        if (cur.nuc == null)
                            return Outcome.NOTHING;
                        nbc1Entries.put(uid, Nbc1Form.entry(v, b.obs));
                        return actions.saveNuc(uid, cur.nuc.withObs(b.obs),
                                NBC1_SAVED);
                    }
                }));
    }

    /** The EOD 9-line (or simple report), saved on the incident. */
    Outcome report(final String uid) {
        Incidents.Incident in = uid == null ? null : incidents.get(uid);
        if (in == null)
            return gone();
        final UxoRecord r0 = in.uxo;
        if (r0 == null)
            return Outcome.NOTHING;
        final String callsign = team.callsign();
        final String grid = app.grid(in.lat, in.lon);
        return Outcome.form(new FormDialog(UxoReportForm.title(in.name),
                UxoReportForm.form(r0, app.emergencyNumber()), true, 16, 8,
                "Save", "Copy text", "Cancel", new FormDialog.Logic() {
                    @Override
                    public Map<String, String> live(FormValues v) {
                        Map<String, String> m = new LinkedHashMap<>();
                        m.put(UxoReportForm.SLOT_PRI_NOTE,
                                UxoReportForm.priorityNote(v));
                        m.put(UxoReportForm.SLOT_PREVIEW, UxoReportForm
                                .preview(r0, v, callsign, grid));
                        return m;
                    }

                    @Override
                    public Outcome press(String button, FormValues v) {
                        if (!FormDialog.POSITIVE.equals(button)
                                && !FormDialog.NEUTRAL.equals(button))
                            return Outcome.NOTHING;
                        Outcome saved = actions.saveReport(uid,
                                UxoReportForm.report(v));
                        if (FormDialog.POSITIVE.equals(button))
                            return saved;
                        // Copy text saves first, then copies.
                        return saved.then(actions.copy(UxoReportForm
                                .preview(r0, v, callsign, grid)));
                    }
                }));
    }
}
