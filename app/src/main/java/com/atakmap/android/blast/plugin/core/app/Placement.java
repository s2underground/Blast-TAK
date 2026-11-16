package com.atakmap.android.blast.plugin.core.app;

import com.atakmap.android.blast.plugin.core.BlastRecord;
import com.atakmap.android.blast.plugin.core.NucPresets;
import com.atakmap.android.blast.plugin.core.NucRecord;
import com.atakmap.android.blast.plugin.core.StandoffTable;
import com.atakmap.android.blast.plugin.core.UxoRecord;
import com.atakmap.android.blast.plugin.core.port.Clock;
import com.atakmap.android.blast.plugin.core.port.Incidents;
import com.atakmap.android.blast.plugin.core.port.Magnetic;
import com.atakmap.android.blast.plugin.core.port.MapControl;
import com.atakmap.android.blast.plugin.core.port.PositionSource;
import com.atakmap.android.blast.plugin.core.port.Share;
import com.atakmap.android.blast.plugin.core.port.Team;
import com.atakmap.android.blast.plugin.core.screen.Dialog;
import com.atakmap.android.blast.plugin.core.screen.FormValues;
import com.atakmap.android.blast.plugin.core.screen.NewIncident;
import com.atakmap.android.blast.plugin.core.screen.NewIncident.Mode;
import com.atakmap.android.blast.plugin.core.screen.PolarForm;
import com.atakmap.android.blast.plugin.core.screen.RangeBearingForm;
import com.atakmap.android.blast.plugin.core.screen.Screen;

import java.util.Arrays;
import java.util.List;

/**
 * The new-incident section at work: its choices ({@link NewIncident}),
 * the marker the pane was opened on, the map tap, and placing an
 * incident from any of them (or from the range-and-bearing and polar-plot
 * forms). Owned by {@link BlastApp}.
 */
final class Placement {

    /** List-dialog actions (come back through {@link #choose}). */
    static final String PRESET_REGION = "preset_region",
            PRESET_LIST = "preset_list:";
    /** "Could not place the incident." */
    static final String PLACE_FAILED = "Could not place the incident.",
            NO_POSITION = "Could not compute the position.";

    private final BlastApp app;
    private final Incidents incidents;
    private final Share share;
    private final Team team;
    private final PositionSource position;
    private final Clock clock;
    private final MapControl map;
    private final NewIncident model;

    /** The marker the pane was opened on ("Place at …"), or null. */
    private String targetName;
    private PositionSource target;
    /** The section whose map tap is running, or NONE. */
    private Mode picking = Mode.NONE;

    Placement(BlastApp app, Incidents incidents, Share share, Team team,
            PositionSource position, Clock clock, MapControl map) {
        this.app = app;
        this.incidents = incidents;
        this.share = share;
        this.team = team;
        this.position = position;
        this.clock = clock;
        this.map = map;
        this.model = new NewIncident(StandoffTable.current());
    }

    // ------------------------------------------------------------------
    // State the host sets

    /** The marker to offer as "Place at …" (null name: none). */
    void target(String name, PositionSource where) {
        targetName = name == null || where == null ? null : name;
        target = targetName == null ? null : where;
    }

    /** Forget every choice (and any map tap). */
    void reset() {
        picking = Mode.NONE;
        model.reset();
    }

    /** The host stopped listening for a map tap on its own. */
    void pickCancelled() {
        picking = Mode.NONE;
    }

    Screen screen(FormValues capture) {
        if (capture != null)
            model.capture(capture);
        return model.screen(picking, targetName, app.autoSend());
    }

    // ------------------------------------------------------------------
    // Taps

    Outcome tap(String action, FormValues v) {
        NewIncident.Result r = model.tap(action, v);
        if (r == null)
            return null;
        Outcome o = Outcome.newIncident(r.toast);
        switch (r.request) {
            case AUTO_SEND:
                app.setAutoSend(v.checked(NewIncident.AUTO_SEND));
                return o;
            case MODE_CHANGED:
                return stopPicking(o);
            case PRESETS:
                return o.withDialog(presets(r.group));
            case FOCUS:
                return o.withFocus(r.focus);
            case PLACE_TARGET: {
                PositionSource.Fix at = target == null ? null : target.fix();
                return at == null ? o
                        : place(r.mode, at, UxoRecord.Placement.MARKER, null)
                                .withNewIncident();
            }
            case PICK:
                if (picking != Mode.NONE)
                    return stopPicking(o);
                if (!model.ready(r.mode))
                    return o;
                picking = r.mode;
                return o.withPick(NewIncident.pickPrompt(r.mode));
            case PLACE_SELF: {
                PositionSource.Fix f = position == null ? null
                        : position.fix();
                return f == null ? o.withToast(NewIncident.NO_GPS)
                        : place(r.mode, f, UxoRecord.Placement.MAP, null)
                                .withNewIncident();
            }
            case PLACE_CENTER: {
                PositionSource.Fix c = map == null ? null : map.center();
                return c == null || !valid(c.lat, c.lon) ? o
                        : place(r.mode, c, UxoRecord.Placement.CENTER, null)
                                .withNewIncident();
            }
            case RANGE_BEARING: {
                Outcome f = rangeBearing();
                return f.form != null ? o.withForm(f.form)
                        : o.withToast(f.toast);
            }
            case POLAR: {
                Outcome f = polar();
                return f.form != null ? o.withForm(f.form)
                        : o.withToast(f.toast);
            }
            default:
                return o;
        }
    }

    private Outcome stopPicking(Outcome o) {
        if (picking == Mode.NONE)
            return o;
        picking = Mode.NONE;
        return o.withStopPick();
    }

    /**
     * The map was tapped (the host has stopped listening). {@code current}
     * holds what is in the section's boxes. Null: no usable point.
     */
    Outcome picked(PositionSource.Fix at, boolean onMarker,
            FormValues current) {
        Mode kind = picking;
        picking = Mode.NONE;
        if (current != null)
            model.capture(current);
        Outcome o = Outcome.newIncident(null);
        if (kind == Mode.NONE || at == null || !valid(at.lat, at.lon))
            return o;
        return place(kind, at, onMarker ? UxoRecord.Placement.MARKER
                : UxoRecord.Placement.MAP, null).withNewIncident();
    }

    // ------------------------------------------------------------------
    // Placing

    /**
     * Place the chosen threat of {@code mode} at a position.
     *
     * @param how how a UXO position was found
     * @param obs what the observer saw (nuclear polar plot), or null
     */
    private Outcome place(Mode mode, PositionSource.Fix at,
            UxoRecord.Placement how, NucRecord.Obs obs) {
        try {
            if (!model.ready(mode))
                return Outcome.toast(NewIncident.notReady(mode));
            String by = team.callsign();
            long now = clock.nowMillis();
            String uid;
            String done;
            if (mode == Mode.UXO) {
                uid = incidents.create(at, model.uxoRecord(how, by, now));
                done = NewIncident.UXO_PLACED;
            } else if (mode == Mode.NUC) {
                uid = incidents.create(at, model.nucRecord(obs, by, now));
                done = NewIncident.NUC_PLACED;
            } else {
                BlastRecord r = model.standoffRecord(by, now);
                uid = incidents.create(at, r);
                done = NewIncident.PLACED;
                if (uid != null && app.autoSend()) {
                    share.sendToAll(uid);
                    done = NewIncident.PLACED_SENT;
                }
            }
            if (uid == null)
                return Outcome.toast(PLACE_FAILED);
            return placed(uid, done);
        } catch (RuntimeException e) {
            return Outcome.toast(PLACE_FAILED);
        }
    }

    /** Open the new incident's card and start the next one afresh. */
    private Outcome placed(String uid, String message) {
        target(null, null);
        app.select(uid);
        boolean wasPicking = picking != Mode.NONE;
        reset();
        Outcome o = Outcome.redraw(message, true).withNewIncident()
                .withTop();
        return wasPicking ? o.withStopPick() : o;
    }

    /** What a host point check would accept. */
    static boolean valid(double lat, double lon) {
        return !Double.isNaN(lat) && !Double.isNaN(lon) && lat >= -90
                && lat <= 90 && lon >= -180 && lon <= 180;
    }

    /** Compass bearings at a position, through the host's model. */
    Magnetic magneticAt(final double lat, final double lon) {
        return new Magnetic() {
            @Override
            public double toTrue(double magneticDeg) {
                try {
                    return map.magneticToTrue(lat, lon, magneticDeg);
                } catch (RuntimeException e) {
                    return Double.NaN;
                }
            }
        };
    }

    // ------------------------------------------------------------------
    // Forms

    /** Place a UXO by distance and bearing from your position. */
    private Outcome rangeBearing() {
        final PositionSource.Fix self = position == null ? null
                : position.fix();
        if (self == null)
            return Outcome.toast(RangeBearingForm.NO_GPS);
        final Magnetic mag = magneticAt(self.lat, self.lon);
        return Outcome.form(new FormDialog(RangeBearingForm.TITLE,
                RangeBearingForm.form(), false, 16, 8, "Place", null,
                "Cancel", new FormDialog.Logic() {
                    @Override
                    public java.util.Map<String, String> live(FormValues v) {
                        return FormDialog.NO_LIVE;
                    }

                    @Override
                    public Outcome press(String button, FormValues v) {
                        if (!FormDialog.POSITIVE.equals(button))
                            return Outcome.NOTHING;
                        RangeBearingForm.Result res = RangeBearingForm.place(
                                v, self.lat, self.lon, mag);
                        if (res.error != null)
                            return Outcome.toast(res.error);
                        if (!valid(res.lat, res.lon))
                            return Outcome.toast(NO_POSITION);
                        return place(Mode.UXO, PositionSource.Fix.at(res.lat,
                                res.lon), UxoRecord.Placement.RB, null);
                    }
                }));
    }

    /** Place ground zero by polar plot: azimuth and flash-to-bang. */
    private Outcome polar() {
        final PositionSource.Fix self = position == null ? null
                : position.fix();
        if (self == null)
            return Outcome.toast(PolarForm.NO_GPS);
        if (!model.ready(Mode.NUC))
            return Outcome.toast(NewIncident.notReady(Mode.NUC));
        final Magnetic mag = magneticAt(self.lat, self.lon);
        return Outcome.form(new FormDialog(PolarForm.TITLE, PolarForm.form(),
                false, 16, 8, "Place", null, "Cancel",
                new FormDialog.Logic() {
                    @Override
                    public java.util.Map<String, String> live(FormValues v) {
                        return FormDialog.NO_LIVE;
                    }

                    @Override
                    public Outcome press(String button, FormValues v) {
                        if (!FormDialog.POSITIVE.equals(button))
                            return Outcome.NOTHING;
                        PolarForm.Result res = PolarForm.place(v, self.lat,
                                self.lon, mag);
                        if (res.error != null)
                            return Outcome.toast(res.error);
                        if (!valid(res.lat, res.lon))
                            return Outcome.toast(NO_POSITION);
                        return place(Mode.NUC, PositionSource.Fix.at(res.lat,
                                res.lon), null, res.obs);
                    }
                }));
    }

    // ------------------------------------------------------------------
    // Nuclear presets

    /** The list for a preset group (fielded weapons: countries first). */
    private static Dialog presets(NucPresets.Group g) {
        if (g == NucPresets.Group.FIELDED)
            return Dialog.builder(NewIncident.FIELDED_TITLE)
                    .items(Arrays.asList(NewIncident.regionItems()),
                            PRESET_REGION)
                    .negative("Cancel").build();
        // A message would hide the list, so any note is in the title.
        return list(NewIncident.groupTitle(g), NucPresets.group(g),
                g == NucPresets.Group.YIELD, g.name());
    }

    private static Dialog list(String title, List<NucPresets.Preset> ps,
            boolean labelOnly, String key) {
        return Dialog.builder(title)
                .items(Arrays.asList(NewIncident.presetItems(ps, labelOnly)),
                        PRESET_LIST + key)
                .negative("Cancel").build();
    }

    /** The presets a list key names: a Group name or "FIELDED:" + region. */
    private static List<NucPresets.Preset> presetsOf(String key) {
        if (key.startsWith("FIELDED:"))
            return NucPresets.fielded(key.substring("FIELDED:".length()));
        for (NucPresets.Group g : NucPresets.Group.values())
            if (g.name().equals(key))
                return NucPresets.group(g);
        return null;
    }

    /** A line tapped in a preset list; null if not a preset list. */
    Outcome choose(String action, FormValues v) {
        if (action == null)
            return null;
        int i;
        try {
            i = Integer.parseInt(v.get(Dialog.ITEM));
        } catch (RuntimeException e) {
            i = -1;
        }
        if (PRESET_REGION.equals(action)) {
            String[] regions = NucPresets.REGIONS;
            if (i < 0 || i >= regions.length)
                return Outcome.NOTHING;
            return Outcome.dialog(list(NewIncident.regionTitle(regions[i]),
                    NucPresets.fielded(regions[i]), false,
                    "FIELDED:" + regions[i]));
        }
        if (!action.startsWith(PRESET_LIST))
            return null;
        List<NucPresets.Preset> ps = presetsOf(action.substring(
                PRESET_LIST.length()));
        if (ps == null || i < 0 || i >= ps.size())
            return Outcome.NOTHING;
        model.choosePreset(ps.get(i));
        return Outcome.newIncident(null);
    }
}
