package com.atakmap.android.blast.plugin.core.app;

import com.atakmap.android.blast.plugin.core.BlastCodec;
import com.atakmap.android.blast.plugin.core.Geo;
import com.atakmap.android.blast.plugin.core.Names;
import com.atakmap.android.blast.plugin.core.NucCodec;
import com.atakmap.android.blast.plugin.core.NucMil;
import com.atakmap.android.blast.plugin.core.NucRecord;
import com.atakmap.android.blast.plugin.core.NucReference;
import com.atakmap.android.blast.plugin.core.UxoCodec;
import com.atakmap.android.blast.plugin.core.UxoReference;
import com.atakmap.android.blast.plugin.core.port.Clipboard;
import com.atakmap.android.blast.plugin.core.port.Clock;
import com.atakmap.android.blast.plugin.core.port.Files;
import com.atakmap.android.blast.plugin.core.port.Incidents;
import com.atakmap.android.blast.plugin.core.port.MapControl;
import com.atakmap.android.blast.plugin.core.port.PositionSource;
import com.atakmap.android.blast.plugin.core.port.Settings;
import com.atakmap.android.blast.plugin.core.port.Share;
import com.atakmap.android.blast.plugin.core.port.Team;
import com.atakmap.android.blast.plugin.core.screen.Button;
import com.atakmap.android.blast.plugin.core.screen.Dialog;
import com.atakmap.android.blast.plugin.core.screen.FormValues;
import com.atakmap.android.blast.plugin.core.screen.IncidentList;
import com.atakmap.android.blast.plugin.core.screen.Live;
import com.atakmap.android.blast.plugin.core.screen.MainPane;
import com.atakmap.android.blast.plugin.core.screen.NucCard;
import com.atakmap.android.blast.plugin.core.screen.ReferenceTab;
import com.atakmap.android.blast.plugin.core.screen.Screen;
import com.atakmap.android.blast.plugin.core.screen.StandoffCard;
import com.atakmap.android.blast.plugin.core.screen.Text;
import com.atakmap.android.blast.plugin.core.screen.TitleRow;
import com.atakmap.android.blast.plugin.core.screen.UxoCard;
import com.atakmap.android.blast.plugin.core.screen.WindPicker;

import java.util.ArrayList;
import java.util.List;

/**
 * The Blast app as the host sees it: which incident is open, what the
 * open card and the incident list show (and whether they changed since
 * last time), the live YOU / Team lines, what every card, list, tab and
 * Reference button does, the new-incident section and placing, the report
 * forms, and the settings. Hosts call {@link #frame()} on each refresh,
 * draw {@link #newIncident}, pass button taps to {@link #tap} (or
 * {@link #tapNew} for the new-incident section), dialog choices to
 * {@link #choose}, form buttons to {@link FormDialog#press} and map taps
 * to {@link #picked}, then carry out the {@link Outcome}.
 */
public final class BlastApp {

    /** Settings keys and defaults (the same keys as 1.1). */
    public static final String PREF_AUTO_SEND = "blast.autoSend",
            PREF_EMERGENCY = "blast.emergencyNumber",
            DEFAULT_EMERGENCY = "911",
            /** Distance labels on incident rings (this device). */
            PREF_LABELS = "blast.ringLabels";

    /** "Hide ▲" beside SELECTED INCIDENT. */
    public static final String HIDE = "hide_selected";
    /** The emergency-number dialog's Save. */
    public static final String SET_EMERGENCY = "set_emergency";

    private enum Kind {
        STANDOFF, UXO, NUC
    }

    private final Incidents incidents;
    private final Team team;
    private final PositionSource position;
    private final Settings settings;
    private final MapControl map;
    private final IncidentActions actions;
    private final Placement placement;
    private final CardForms forms;

    private String selectedUid;
    /** What the card / list show now; null = redraw next frame. */
    private String cardSig, listSig;
    /** The card on screen: its incident, kind and wind picker. */
    private String cardUid;
    private Kind cardKind;
    private WindPicker wind;

    public BlastApp(Incidents incidents, Share share, Team team,
            PositionSource position, Clock clock, Clipboard clipboard,
            Files files, Settings settings, MapControl map) {
        this.incidents = incidents;
        this.team = team;
        this.position = position;
        this.settings = settings;
        this.map = map;
        this.actions = new IncidentActions(incidents, share, team, clock,
                clipboard, files);
        this.placement = new Placement(this, incidents, share, team,
                position, clock, map);
        this.forms = new CardForms(this, incidents, position, team, actions,
                placement);
    }

    // ------------------------------------------------------------------
    // New incident

    /**
     * The new-incident section as it is now. {@code current}: what is in
     * its boxes on screen, to keep (after a change from outside the
     * section); null when the core already has it (after one of its
     * outcomes).
     */
    public Screen newIncident(FormValues current) {
        return placement.screen(current);
    }

    /**
     * A button, box or tick in the new-incident section; null if
     * {@code action} isn't one of its.
     */
    public Outcome tapNew(String action, FormValues v) {
        return placement.tap(action, v);
    }

    /**
     * The map was tapped while waiting for one (the host has stopped
     * listening): where (null for no usable point, else with its height),
     * {@code onMarker} if the tap was on a marker, and {@code current} as
     * for {@link #newIncident}.
     */
    public Outcome picked(PositionSource.Fix at, boolean onMarker,
            FormValues current) {
        return placement.picked(at, onMarker, current);
    }

    /** The host stopped listening for a map tap itself (tab, pane closed). */
    public void pickCancelled() {
        placement.pickCancelled();
    }

    /**
     * The marker to offer as "Place at {@code name}", or null to offer
     * none. {@code where} gives its position when placing.
     */
    public void target(String name, PositionSource where) {
        placement.target(name, where);
    }

    /** Forget the new-incident choices (pane opened, incident placed). */
    public void resetNew() {
        placement.reset();
    }

    // ------------------------------------------------------------------
    // Settings

    public String emergencyNumber() {
        String n = settings.getString(PREF_EMERGENCY, DEFAULT_EMERGENCY);
        return n == null || n.trim().isEmpty() ? DEFAULT_EMERGENCY : n.trim();
    }

    public boolean autoSend() {
        return settings.getBoolean(PREF_AUTO_SEND, false);
    }

    public void setAutoSend(boolean on) {
        settings.putBoolean(PREF_AUTO_SEND, on);
    }

    /** Ring distance labels shown on the map (default: yes, as 1.1). */
    public boolean ringLabels() {
        return settings.getBoolean(PREF_LABELS, true);
    }

    // ------------------------------------------------------------------
    // Selection

    /** The open incident's uid, or null. */
    public String selected() {
        return selectedUid;
    }

    /** Open an incident's card (redraws card and list). */
    public void select(String uid) {
        selectedUid = uid;
        invalidate(true, true);
    }

    /** Close the card. */
    public void collapse() {
        select(null);
    }

    /** Redraw the card and / or the list next frame. */
    public void invalidate(boolean card, boolean list) {
        if (card)
            cardSig = null;
        if (list)
            listSig = null;
    }

    /** After an incident went away: keep the selection if it is not it. */
    public void afterDelete() {
        if (selectedUid != null && incidents.get(selectedUid) == null)
            selectedUid = null;
        invalidate(true, true);
    }

    // ------------------------------------------------------------------
    // Frames

    /** What changed since the last frame. */
    public static final class Frame {
        /** The card changed: show {@link #header} and {@link #card}. */
        public final boolean cardChanged;
        /** Card header and body, or null to hide the card. */
        public final Screen header, card;
        /** Live lines for the open card, or null to leave them. */
        public final Text you, team;
        /** The new list, or null if it is unchanged. */
        public final Screen list;

        Frame(boolean cardChanged, Screen header, Screen card, Text you,
                Text team, Screen list) {
            this.cardChanged = cardChanged;
            this.header = header;
            this.card = card;
            this.you = you;
            this.team = team;
            this.list = list;
        }
    }

    /** The card and list as they should look now. */
    public Frame frame() {
        Incidents.Incident in = selectedUid == null ? null
                : incidents.get(selectedUid);
        String sig = in == null ? "" : in.uid + "|"
                + (in.standoff == null ? "" : BlastCodec.toXml(in.standoff))
                + "|" + (in.uxo == null ? "" : UxoCodec.toXml(in.uxo)) + "|"
                + (in.nuc == null ? "" : NucCodec.toXml(in.nuc)) + "|"
                + in.displayName + "|" + emergencyNumber() + "|"
                + ringLabels();
        boolean changed = !sig.equals(cardSig);
        Screen header = null, card = null;
        if (changed) {
            cardSig = sig;
            cardUid = null;
            cardKind = null;
            wind = null;
            if (in != null) {
                header = new Screen().add(new TitleRow(Text.step(
                        "SELECTED INCIDENT"), Button.of(HIDE, "Hide ▲"),
                        false));
                card = card(in);
            }
        }
        Text you = null, teamLine = null;
        if (in != null && cardUid != null && !Double.isNaN(in.lat)
                && !Double.isNaN(in.lon)) {
            Text[] t = in.nuc != null ? Live.nuc(in.nuc, in.lat, in.lon,
                    position, team) : in.uxo != null ? Live.uxo(in.uxo,
                            in.lat, in.lon, position, team)
                            : Live.standoff(in.standoff, in.lat, in.lon,
                                    position, team);
            you = t[0];
            teamLine = t[1];
        }
        return new Frame(changed, header, card, you, teamLine, list());
    }

    private Screen card(Incidents.Incident in) {
        cardUid = in.uid;
        if (in.nuc != null) {
            cardKind = Kind.NUC;
            wind = NucCard.wind(in.nuc);
            return NucCard.card(in.nuc, in.displayName, mgrs10(in.lat,
                    in.lon), wind, ringLabels());
        }
        if (in.uxo != null) {
            cardKind = Kind.UXO;
            return UxoCard.card(in.uxo, in.displayName, emergencyNumber(),
                    grid(in.lat, in.lon), ringLabels());
        }
        cardKind = Kind.STANDOFF;
        return StandoffCard.card(in.standoff, in.displayName,
                ringLabels());
    }

    /** The list, or null if it would look the same as last time. */
    private Screen list() {
        double[] ref = referencePoint();
        List<IncidentList.Entry> entries = new ArrayList<>();
        for (Incidents.Incident in : incidents.all()) {
            double d = ref == null ? Double.NaN : Geo.distance(ref[0], ref[1],
                    in.lat, in.lon);
            if (in.nuc != null)
                entries.add(IncidentList.Entry.of(in.uid, in.displayName,
                        in.nuc, d));
            else if (in.uxo != null)
                entries.add(IncidentList.Entry.of(in.uid, in.displayName,
                        in.uxo, d));
            else if (in.standoff != null)
                entries.add(IncidentList.Entry.of(in.uid, in.displayName,
                        in.standoff, d));
        }
        List<IncidentList.Entry> sorted = IncidentList.sorted(entries);
        String sig = IncidentList.signature(sorted, selectedUid);
        if (sig.equals(listSig))
            return null;
        listSig = sig;
        return IncidentList.screen(sorted, selectedUid);
    }

    /** Own position, or the map centre without a fix (list distances). */
    double[] referencePoint() {
        PositionSource.Fix f = position == null ? null : position.fix();
        if (f != null)
            return new double[] { f.lat, f.lon };
        PositionSource.Fix c = map == null ? null : map.center();
        return c == null ? null : new double[] { c.lat, c.lon };
    }

    // ------------------------------------------------------------------
    // Grid references

    /** MGRS as the host writes it; lat/lon to 5 places if that fails. */
    public String grid(double lat, double lon) {
        if (Double.isNaN(lat) || Double.isNaN(lon))
            return "";
        try {
            return map.mgrs(lat, lon);
        } catch (RuntimeException e) {
            return (Math.round(lat * 1e5) / 1e5) + ", "
                    + (Math.round(lon * 1e5) / 1e5);
        }
    }

    /** MGRS to 10 m (4 + 4 digits), spaces kept; "" if unavailable. */
    public String mgrs10(double lat, double lon) {
        if (Double.isNaN(lat) || Double.isNaN(lon))
            return "";
        try {
            String s = map.mgrs(lat, lon);
            if (s == null)
                return "";
            String[] t = s.trim().split("\\s+");
            if (t.length == 4 && t[2].length() == 5 && t[3].length() == 5)
                return t[0] + " " + t[1] + " " + t[2].substring(0, 4) + " "
                        + t[3].substring(0, 4);
            return s;
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------
    // Taps

    /**
     * A button on the pane header, the Reference tab, the incident list or
     * the open card. {@code v} holds the boxes of the screen it came from.
     * Returns null for an action this doesn't know.
     */
    public Outcome tap(String action, FormValues v) {
        if (action == null)
            return null;
        switch (action) {
            case MainPane.TAB_INCIDENTS:
                return Outcome.tab(false, null);
            case MainPane.TAB_REFERENCE:
                return Outcome.tab(true, null);
            case HIDE:
                collapse();
                return Outcome.redraw(null, true);
            case ReferenceTab.CHANGE_EMERGENCY:
                return Outcome.dialog(Dialog.builder(
                        ReferenceTab.EMERGENCY_TITLE)
                        .message(ReferenceTab.EMERGENCY_HELP)
                        .field(emergencyNumber(), Dialog.FieldKind.PHONE,
                                ReferenceTab.EMERGENCY_MAX, false)
                        .positive("Save", SET_EMERGENCY).negative("Cancel")
                        .build());
            case IncidentList.DELETE_CLEARED:
                return actions.deleteCleared();
            default:
                break;
        }
        Outcome o = list(action);
        if (o != null)
            return o;
        return cardUid == null ? null : card(action, v);
    }

    private Outcome list(String action) {
        String uid = IncidentList.uid(action, IncidentList.SELECT);
        if (uid != null) {
            // Tap the open incident again to close its card.
            if (uid.equals(selectedUid))
                collapse();
            else
                select(uid);
            return Outcome.redraw(null, true);
        }
        if ((uid = IncidentList.uid(action, IncidentList.GO)) != null)
            return fly(uid);
        if ((uid = IncidentList.uid(action, IncidentList.NINE_LINE)) != null) {
            if (incidents.get(uid) == null)
                return gone();
            select(uid);
            return forms.report(uid).redrawAll();
        }
        if ((uid = IncidentList.uid(action, IncidentList.DELETE)) != null)
            return actions.card(uid, com.atakmap.android.blast.plugin.core
                    .screen.CardActions.DELETE);
        return null;
    }

    private static Outcome gone() {
        return new Outcome(IncidentActions.GONE, null, false, false, true,
                null);
    }

    /** Fly to an incident and open its card. */
    private Outcome fly(String uid) {
        if (incidents.get(uid) == null)
            return gone();
        select(uid);
        return new Outcome(null, null, true, true, false, uid);
    }

    private Outcome card(String action, FormValues v) {
        String uid = cardUid;
        if (com.atakmap.android.blast.plugin.core.screen.CardActions.LABELS
                .equals(action)) {
            settings.putBoolean(PREF_LABELS, !ringLabels());
            invalidate(true, false);
            return Outcome.redraw(null, false).withLabels();
        }
        if (cardKind == Kind.NUC) {
            Outcome o = nuc(uid, action, v);
            if (o != null)
                return o;
        } else if (cardKind == Kind.UXO) {
            if (UxoCard.REPORT.equals(action))
                return forms.report(uid);
            if (UxoCard.WHY.equals(action))
                return Outcome.tab(true, UxoReference.RINGS);
        }
        Outcome o = actions.card(uid, action);
        if (o != null && o.flyTo != null)
            return fly(o.flyTo);
        return o;
    }

    private Outcome nuc(String uid, String action, FormValues v) {
        WindPicker.Tap t = wind == null ? null : wind.tap(action, v);
        if (t != null) {
            Outcome o = t.changed ? actions.saveWind(uid, wind.fromDeg(),
                    wind.mph()) : Outcome.NOTHING;
            if (t.error != null)
                o = o.withToast(t.error);
            return o.withRestyle(wind.screen());
        }
        Incidents.Incident in;
        switch (action) {
            case NucCard.APPLY_EDM: {
                NucMil.Edm e = NucMil.parseEdm(v.get(NucCard.EDM));
                in = incidents.get(uid);
                if (e == null || in == null || in.nuc == null)
                    return Outcome.toast(NucCard.EDM_ERROR);
                return actions.saveNuc(uid, in.nuc.withEdm(e),
                        NucCard.EDM_APPLIED);
            }
            case NucCard.NBC1:
                return forms.nbc1(uid);
            case NucCard.PIC_CIVIL:
            case NucCard.PIC_MILITARY: {
                boolean mil = NucCard.PIC_MILITARY.equals(action);
                in = incidents.get(uid);
                if (in == null || in.nuc == null || in.nuc.mil.on == mil)
                    return Outcome.NOTHING;
                NucRecord r = in.nuc;
                return actions.saveNuc(uid, r.withMil(r.mil.withOn(mil)),
                        null);
            }
            case NucCard.COPY_NBC3: {
                in = incidents.get(uid);
                String text = in == null || in.nuc == null ? null
                        : NucCard.nbc3Text(in.nuc, mgrs10(in.lat, in.lon));
                return text == null ? Outcome.NOTHING : actions.copy(text);
            }
            case NucCard.HOW_ZONES:
                return Outcome.tab(true, NucReference.MIL);
            case NucCard.WHY:
                return Outcome.tab(true, NucReference.RINGS);
            case NucCard.WHAT_TO_DO:
                return Outcome.tab(true, NucReference.ACTIONS);
            default:
                return null;
        }
    }

    /**
     * A dialog button. {@code v} holds the dialog's text box and ticks.
     */
    public Outcome choose(Dialog d, String action, FormValues v) {
        if (SET_EMERGENCY.equals(action)) {
            String n = Names.sanitize(v.get(Dialog.FIELD));
            settings.putString(PREF_EMERGENCY, n == null ? DEFAULT_EMERGENCY
                    : n);
            invalidate(true, false);
            return Outcome.redraw(null, false).withSettings();
        }
        Outcome o = placement.choose(action, v);
        return o != null ? o : actions.choose(d, action, v);
    }
}
