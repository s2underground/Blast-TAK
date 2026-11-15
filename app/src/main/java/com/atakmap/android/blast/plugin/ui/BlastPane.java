package com.atakmap.android.blast.plugin.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.blast.plugin.R;
import com.atakmap.android.blast.plugin.atak.BlastStore;
import com.atakmap.android.blast.plugin.atak.Exporter;
import com.atakmap.android.blast.plugin.core.port.Magnetic;
import com.atakmap.android.blast.plugin.core.screen.Buttons;
import com.atakmap.android.blast.plugin.core.screen.Check;
import com.atakmap.android.blast.plugin.core.screen.Choice;
import com.atakmap.android.blast.plugin.core.screen.FormValues;
import com.atakmap.android.blast.plugin.core.screen.Input;
import com.atakmap.android.blast.plugin.core.screen.Nbc1Form;
import com.atakmap.android.blast.plugin.core.screen.PolarForm;
import com.atakmap.android.blast.plugin.core.screen.UxoReportForm;
import com.atakmap.android.blast.plugin.core.screen.CardActions;
import com.atakmap.android.blast.plugin.core.screen.UxoCard;
import com.atakmap.android.blast.plugin.core.screen.Ink;
import com.atakmap.android.blast.plugin.core.screen.Item;
import com.atakmap.android.blast.plugin.core.screen.KeyRow;
import com.atakmap.android.blast.plugin.core.screen.NucCard;
import com.atakmap.android.blast.plugin.core.screen.EntryRow;
import com.atakmap.android.blast.plugin.core.screen.WindPicker;
import com.atakmap.android.blast.plugin.core.screen.NewIncident;
import com.atakmap.android.blast.plugin.core.screen.NewIncident.Mode;
import com.atakmap.android.blast.plugin.core.screen.Section;
import com.atakmap.android.blast.plugin.core.screen.IncidentList;
import com.atakmap.android.blast.plugin.core.screen.ListRow;
import com.atakmap.android.blast.plugin.core.screen.MainPane;
import com.atakmap.android.blast.plugin.core.screen.RangeBearingForm;
import com.atakmap.android.blast.plugin.core.screen.ReferenceTab;
import com.atakmap.android.blast.plugin.core.screen.TitleRow;
import com.atakmap.android.blast.plugin.core.screen.Legend;
import com.atakmap.android.blast.plugin.core.screen.Screen;
import com.atakmap.android.blast.plugin.core.screen.StandoffCard;
import com.atakmap.android.blast.plugin.core.screen.Text;
import com.atakmap.android.blast.plugin.core.screen.Tone;
import com.atakmap.android.blast.plugin.core.Names;
import com.atakmap.android.blast.plugin.atak.Sharing;
import com.atakmap.android.blast.plugin.core.BlastRecord;
import com.atakmap.android.blast.plugin.core.StandoffTable;
import com.atakmap.android.blast.plugin.core.UxoRecord;
import com.atakmap.android.blast.plugin.core.UxoReference;
import com.atakmap.android.blast.plugin.core.UxoText;
import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.coremap.log.Log;
import com.atakmap.android.blast.plugin.core.Geo;
import com.atakmap.android.blast.plugin.core.NucMil;
import com.atakmap.android.blast.plugin.core.NucPresets;
import com.atakmap.android.blast.plugin.core.NucRecord;
import com.atakmap.android.blast.plugin.core.NucReference;
import com.atakmap.android.blast.plugin.core.NucText;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The Blast pane, built in code: the list of incidents on the map, the
 * selected incident's card (distances, where you are, team counts,
 * provenance, actions, caveats) and the new-incident picker. All text is
 * concatenated; no String.format anywhere. Runs on the main thread only.
 * Every action re-checks that its incident is still on the map, so a card
 * left open after a remote delete can't resurrect the incident.
 */
public final class BlastPane {

    private static final String TAG = "BlastPane";
    private static final String PREF_AUTO_SEND = "blast.autoSend";
    private static final String PREF_EMERGENCY = "blast.emergencyNumber";
    private static final String DEFAULT_EMERGENCY = "911";
    /** 0.1-0.7 remembered the last threat; no longer used (removed on start). */
    private static final String PREF_LAST_ROW_OLD = "blast.lastRow";
    private static final long TICK_MS = 3000;

    private static final int BG = 0xFF1E1E1E;
    private static final int CARD = 0xFF2A2A2A;
    private static final int TEXT = 0xFFEEEEEE;
    private static final int DIM = 0xFFAAAAAA;
    private static final int RED = 0xFFFF5252;
    private static final int AMBER = 0xFFFFC107;
    private static final int GREEN = 0xFF69F0AE;
    private static final int BLUE = 0xFF40C4FF;
    private static final int SEL = 0xFF455A64;
    private static final int BTN = 0xFF3C3C3C;
    private static final int SEND = 0xFF1565C0;
    private static final int ORANGE = 0xFFFF9100;
    private static final int UXO_SEL = 0xFF8D4A00;
    private static final int NUC_SEL = 0xFF5E2A72;
    private static final int NUC_COLOR = 0xFFE040FB;

    /** Which kind of incident the New incident section is placing. */
    private final Context plugin;
    private final MapView mapView;
    private final BlastStore store;
    private final Sharing sharing;
    private final Exporter exporter;
    private final SharedPreferences prefs;
    private final StandoffTable table = StandoffTable.current();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final ScrollView root;
    private LinearLayout incidentsView, referenceView;
    /** Rendered core screens: pane header, Reference tab, incident list. */
    private Rendered headerView, refView, listView;

    /** The new-incident choices (core) and their rendered views. */
    private final NewIncident newIncident = new NewIncident(table);
    private Rendered newView;
    /** What the map tap places. */
    private Mode pickKind = Mode.NONE;
    private LinearLayout zoneCard;
    /** Debug builds only: what the incident lookup found. */
    private TextView debugLine;

    private Marker placeTarget;
    private String selectedZoneUid;
    /**
     * What the card / list currently show; null means "redraw next time".
     * (0.4 reset these to "", which is also the signature of "no incident",
     * so a deleted incident's card was never cleared.)
     */
    private String cardSignature = null;
    private String listSignature = null;
    private TextView youText, teamText;

    private boolean picking;
    private MapEventDispatcher.MapEventDispatchListener pickListener;
    private boolean ticking;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!ticking)
                return;
            refresh();
            main.postDelayed(this, TICK_MS);
        }
    };

    public BlastPane(Context pluginContext, MapView mapView, BlastStore store,
            Sharing sharing) {
        this.plugin = pluginContext;
        this.mapView = mapView;
        this.store = store;
        this.sharing = sharing;
        this.exporter = new Exporter(store);
        this.prefs = AtakPreferences.getInstance(mapView.getContext())
                .getSharedPrefs();
        this.root = new ScrollView(plugin);
        root.setBackgroundColor(BG);
        root.addView(buildRoot());
        root.addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        startTicking();
                    }

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        stopTicking();
                        stopPick();
                    }
                });
        // No threat is pre-selected: the user must choose one for every
        // incident (a remembered choice could place the wrong size).
        prefs.edit().remove(PREF_LAST_ROW_OLD).apply();
        resetThreat();
    }

    public View getView() {
        return root;
    }

    // ------------------------------------------------------------------
    // Entry points

    public void onShow() {
        cardSignature = null;
        listSignature = null;
        refresh();
    }

    /** Opened from the radial menu on a zone. */
    public void openZone(Marker zone) {
        placeTarget = null;
        selectedZoneUid = zone.getUID();
        showTab(false, null);
        updateNew();
        onShow();
        main.post(new Runnable() {
            @Override
            public void run() {
                if (zoneCard != null)
                    root.smoothScrollTo(0, 0);
            }
        });
    }

    /** Opened from the radial menu on another marker. */
    public void placeAt(Marker target) {
        placeTarget = target;
        showTab(false, null);
        updateNew();
        onShow();
        root.smoothScrollTo(0, 0);
    }

    public void dispose() {
        stopTicking();
        stopPick();
        main.removeCallbacksAndMessages(null);
    }

    private void startTicking() {
        if (ticking)
            return;
        ticking = true;
        main.post(tick);
    }

    private void stopTicking() {
        ticking = false;
        main.removeCallbacks(tick);
    }

    // ------------------------------------------------------------------
    // Layout

    private View buildRoot() {
        LinearLayout outer = vertical();
        int pad = dp(8);
        outer.setPadding(pad, pad, pad, pad);
        headerView = render(MainPane.header(table, false), outer,
                new ScreenAction() {
                    @Override
                    public void run(String action) {
                        if (MainPane.TAB_INCIDENTS.equals(action))
                            showTab(false, null);
                        else if (MainPane.TAB_REFERENCE.equals(action))
                            showTab(true, null);
                    }
                });
        incidentsView = (LinearLayout) build();
        referenceView = buildReference();
        referenceView.setVisibility(View.GONE);
        outer.addView(incidentsView);
        outer.addView(referenceView);
        return outer;
    }

    /** Show the Reference tab (optionally at a section) or the Incidents tab. */
    private void showTab(boolean reference, final String sectionId) {
        stopPick();
        incidentsView.setVisibility(reference ? View.GONE : View.VISIBLE);
        referenceView.setVisibility(reference ? View.VISIBLE : View.GONE);
        headerView.update(MainPane.header(table, reference));
        main.post(new Runnable() {
            @Override
            public void run() {
                View a = sectionId == null ? null
                        : refView.slots.get(ReferenceTab.anchor(sectionId));
                root.smoothScrollTo(0, a == null ? 0
                        : referenceView.getTop() + a.getTop());
            }
        });
    }

    private View build() {
        LinearLayout col = vertical();

        listView = render(IncidentList.placeholder(), col,
                new ScreenAction() {
                    @Override
                    public void run(String action) {
                        listAction(action);
                    }
                });

        zoneCard = vertical();
        col.addView(zoneCard);

        // The core decides what the new-incident steps say and offer.
        newView = render(newIncident.screen(picking(), targetName(),
                autoSend()), col, new ScreenAction() {
                    @Override
                    public void run(String action) {
                        newAction(action);
                    }
                });

        if (com.atakmap.android.blast.plugin.BuildConfig.DEBUG) {
            debugLine = text("", 10, DIM, false);
            col.addView(debugLine);
        }
        render(new Screen().add(MainPane.footer()), col, null);
        return col;
    }

    // ------------------------------------------------------------------
    // New incident: the core NewIncident model, rendered and acted on here

    /** The section whose map tap is running, or NONE. */
    private Mode picking() {
        return picking ? pickKind : Mode.NONE;
    }

    private String targetName() {
        return placeTarget == null ? null
                : ATAKUtilities.getDisplayName(placeTarget);
    }

    private boolean autoSend() {
        return prefs.getBoolean(PREF_AUTO_SEND, false);
    }

    /** Redraw the new-incident steps from the core (no capture). */
    private void renderNew() {
        if (newView != null)
            newView.update(newIncident.screen(picking(), targetName(),
                    autoSend()));
    }

    /** Redraw after an outside change, keeping what is typed. */
    private void updateNew() {
        if (newView == null)
            return;
        newIncident.capture(newView.values());
        renderNew();
    }

    /** What the new-incident buttons, boxes and checks do. */
    private void newAction(String action) {
        NewIncident.Result r = newIncident.tap(action, newView.values());
        if (r == null)
            return;
        if (r.toast != null)
            toast(r.toast);
        if (r.request == NewIncident.Request.AUTO_SEND)
            prefs.edit().putBoolean(PREF_AUTO_SEND, newView.values()
                    .checked(NewIncident.AUTO_SEND)).apply();
        renderNew();
        switch (r.request) {
            case MODE_CHANGED:
                stopPick();
                break;
            case PRESETS:
                choosePreset(r.group);
                break;
            case FOCUS: {
                EditText e = newView.entries.get(r.focus);
                if (e != null)
                    e.requestFocus();
                break;
            }
            case PLACE_TARGET:
                if (placeTarget != null && placeTarget.getPoint() != null)
                    placeAs(r.mode, placeTarget.getPoint(),
                            UxoRecord.Placement.MARKER);
                break;
            case PICK:
                if (picking)
                    stopPick();
                else
                    startPick(r.mode);
                break;
            case PLACE_SELF: {
                GeoPoint gp = selfPoint();
                if (gp == null)
                    toast(NewIncident.NO_GPS);
                else
                    placeAs(r.mode, gp, UxoRecord.Placement.MAP);
                break;
            }
            case PLACE_CENTER: {
                GeoPointMetaData c = mapView.getCenterPoint();
                if (c != null && c.get() != null && c.get().isValid())
                    placeAs(r.mode, c.get(), UxoRecord.Placement.CENTER);
                break;
            }
            case RANGE_BEARING:
                promptRangeBearing();
                break;
            case POLAR:
                promptPolarPlot();
                break;
            default:
                break;
        }
    }

    /** Place the chosen threat of {@code mode} at {@code at}. */
    private void placeAs(Mode mode, GeoPoint at, UxoRecord.Placement how) {
        if (mode == Mode.UXO)
            placeUxo(at, how);
        else if (mode == Mode.NUC)
            placeNuc(at);
        else
            place(at);
    }

    private void placeUxo(GeoPoint at, UxoRecord.Placement how) {
        try {
            if (!newIncident.ready(Mode.UXO)) {
                toast(NewIncident.notReady(Mode.UXO));
                return;
            }
            UxoRecord r = newIncident.uxoRecord(how, callsign(),
                    System.currentTimeMillis());
            Marker m = store.createUxo(at, r);
            if (m == null) {
                toast("Could not place the incident.");
                return;
            }
            placeTarget = null;
            selectedZoneUid = m.getUID();
            resetThreat();
            toast(NewIncident.UXO_PLACED);
            onShow();
            main.post(new Runnable() {
                @Override
                public void run() {
                    root.smoothScrollTo(0, 0);
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "UXO place failed", e);
            toast("Could not place the incident.");
        }
    }

    /** Place by distance and bearing from the user's own position. */
    private void promptRangeBearing() {
        final GeoPoint self = selfPoint();
        if (self == null) {
            toast(RangeBearingForm.NO_GPS);
            return;
        }
        Context ctx = mapView.getContext();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        box.setPadding(p, dp(8), p, 0);
        final FormView fv = renderForm(RangeBearingForm.form(), ctx, box,
                new Runnable() {
                    @Override
                    public void run() {
                        // nothing live in this form
                    }
                });
        showTyping(new AlertDialog.Builder(ctx)
                .setTitle(RangeBearingForm.TITLE)
                .setView(box)
                .setPositiveButton("Place",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                RangeBearingForm.Result res = RangeBearingForm
                                        .place(fv.values(), self.getLatitude(),
                                                self.getLongitude(),
                                                magneticAt(self));
                                if (res.error != null) {
                                    toast(res.error);
                                    return;
                                }
                                GeoPoint at = new GeoPoint(res.lat, res.lon);
                                if (!at.isValid()) {
                                    toast("Could not compute the position.");
                                    return;
                                }
                                placeUxo(at, UxoRecord.Placement.RB);
                            }
                        })
                .setNegativeButton("Cancel", null)
                );
    }

    // ------------------------------------------------------------------
    // Nuclear detonation reference: yield, burst, optional wind, place

    private void choosePreset(final NucPresets.Group g) {
        if (g == NucPresets.Group.FIELDED) {
            // Country first, so no list gets long.
            final String[] regions = NucPresets.REGIONS;
            new AlertDialog.Builder(mapView.getContext())
                    .setTitle(NewIncident.FIELDED_TITLE)
                    .setItems(NewIncident.regionItems(),
                            new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            showPresets(NewIncident.regionTitle(regions[w]),
                                    NucPresets.fielded(regions[w]), false);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        showPresets(NewIncident.groupTitle(g), NucPresets.group(g),
                g == NucPresets.Group.YIELD);
    }

    private void showPresets(String title,
            final List<NucPresets.Preset> ps, boolean labelOnly) {
        String[] items = NewIncident.presetItems(ps, labelOnly);
        // A message would hide the list, so the note goes in the title.
        AlertDialog.Builder b = new AlertDialog.Builder(mapView.getContext())
                .setTitle(title)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        newIncident.choosePreset(ps.get(w));
                        renderNew();
                    }
                })
                .setNegativeButton("Cancel", null);
        b.show();
    }

    private void saveWind(Marker card, double from, double mph) {
        try {
            Marker m = live(card);
            NucRecord r = m == null ? null : BlastStore.readNuc(m);
            if (r == null)
                return;
            if (!store.writeNuc(m, r.withWind(from, mph))) {
                live(null);
                return;
            }
            cardSignature = null;
            refresh();
            toast(NucRecord.validWind(from, mph)
                    ? "Wind updated on this device. Use SEND to share it."
                    : "Wind removed on this device. Use SEND to share it.");
        } catch (RuntimeException e) {
            Log.e(TAG, "wind update failed", e);
            toast("Could not update the incident.");
        }
    }

    private void placeNuc(GeoPoint at) {
        placeNuc(at, null);
    }

    private void placeNuc(GeoPoint at, NucRecord.Obs obs) {
        try {
            if (!newIncident.ready(Mode.NUC)) {
                toast(NewIncident.notReady(Mode.NUC));
                return;
            }
            NucRecord r = newIncident.nucRecord(obs, callsign(),
                    System.currentTimeMillis());
            Marker m = store.createNuc(at, r);
            if (m == null) {
                toast("Could not place the incident.");
                return;
            }
            placeTarget = null;
            selectedZoneUid = m.getUID();
            resetThreat();
            toast(NewIncident.NUC_PLACED);
            onShow();
            main.post(new Runnable() {
                @Override
                public void run() {
                    root.smoothScrollTo(0, 0);
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "nuclear place failed", e);
            toast("Could not place the incident.");
        }
    }

    // ------------------------------------------------------------------
    // Wind picker (like HAZ-TAK): compass buttons for the direction the
    // wind blows FROM, speed buttons, optional exact values typed inline.

    private EditText inlineNumber(String hint, int maxLen) {
        EditText e = new EditText(plugin);
        doneKey(e);
        e.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setHint(hint);
        e.setTextColor(TEXT);
        e.setHintTextColor(DIM);
        e.setSingleLine(true);
        e.setFilters(new android.text.InputFilter[] {
                new android.text.InputFilter.LengthFilter(maxLen) });
        return e;
    }

    /**
     * The keyboard's action key is always Done (never Next); Done closes
     * the keyboard and leaves the field.
     */
    private void doneKey(final EditText e) {
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        e.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int action,
                    android.view.KeyEvent ev) {
                boolean enter = ev != null
                        && ev.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                        && ev.getAction() == android.view.KeyEvent.ACTION_DOWN;
                if (action != android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                        && !enter)
                    return false;
                hideKeyboard(e);
                return true;
            }
        });
    }

    private void hideKeyboard(View v) {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) v.getContext()
                            .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null)
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (RuntimeException ex) {
            Log.w(TAG, "keyboard not hidden", ex);
        }
        v.clearFocus();
    }

    /** "SELECTED INCIDENT" header with Hide ▲ to collapse the card. */
    private View selectedHeader() {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(header("SELECTED INCIDENT"), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button hide = button("Hide ▲", BTN);
        hide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                collapseSelected();
            }
        });
        row.addView(hide);
        return row;
    }

    private void collapseSelected() {
        selectedZoneUid = null;
        listSignature = null;
        cardSignature = null;
        refresh();
    }

    /** Show a dialog with text fields so the keyboard can reach them. */
    private AlertDialog showTyping(AlertDialog.Builder b) {
        final AlertDialog d = b.create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface di) {
                android.view.Window w = d.getWindow();
                if (w != null) {
                    w.clearFlags(android.view.WindowManager.LayoutParams
                            .FLAG_NOT_FOCUSABLE
                            | android.view.WindowManager.LayoutParams
                                    .FLAG_ALT_FOCUSABLE_IM);
                    w.setSoftInputMode(android.view.WindowManager.LayoutParams
                            .SOFT_INPUT_ADJUST_RESIZE);
                }
            }
        });
        d.show();
        return d;
    }

    // ------------------------------------------------------------------
    // Military picture (FM 3-3-1 / FM 3-11.3) and NBC reports

    /** MGRS to 10 m (4 + 4 digits), spaces kept; "" if unavailable. */
    private static String mgrs10(GeoPoint gp) {
        if (gp == null || !gp.isValid())
            return "";
        try {
            String s = com.atakmap.coremap.conversions.CoordinateFormatUtilities
                    .formatToString(gp,
                            com.atakmap.coremap.conversions.CoordinateFormat.MGRS);
            String[] t = s.trim().split("\\s+");
            if (t.length == 4 && t[2].length() == 5 && t[3].length() == 5)
                return t[0] + " " + t[1] + " " + t[2].substring(0, 4) + " "
                        + t[3].substring(0, 4);
            return s;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Store an updated nuclear record on its marker and refresh. */
    private void saveNuc(Marker card, NucRecord changed, String done) {
        try {
            Marker m = live(card);
            if (m == null)
                return;
            if (!store.writeNuc(m, changed)) {
                live(null);
                return;
            }
            cardSignature = null;
            refresh();
            if (done != null)
                toast(done);
        } catch (RuntimeException e) {
            Log.e(TAG, "nuclear update failed", e);
            toast("Could not update the incident.");
        }
    }

    /** NBC 1 NUC report builder (FM 3-11.3 Fig. G-3), saved on the incident. */
    private void promptNbc1(Marker card) {
        final Marker m = live(card);
        final NucRecord r0 = m == null ? null : BlastStore.readNuc(m);
        if (r0 == null)
            return;
        final GeoPoint self = selfPoint();
        final GeoPoint gz = m.getPoint();
        Context ctx = mapView.getContext();
        ScrollView sv = new ScrollView(ctx);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pd = dp(14);
        box.setPadding(pd, dp(6), pd, 0);
        sv.addView(box);
        final String obsGrid = mgrs10(self);
        final String gzGrid = mgrs10(gz);
        final NucRecord[] built = { r0 };
        final FormView[] fv = new FormView[1];
        final Runnable update = new Runnable() {
            @Override
            public void run() {
                if (fv[0] == null)
                    return;
                built[0] = r0.withObs(Nbc1Form.obs(fv[0].values(),
                        self == null ? Double.NaN : self.getLatitude(),
                        self == null ? Double.NaN : self.getLongitude(),
                        magneticAt(self)));
                fv[0].set(Nbc1Form.SLOT_PREVIEW,
                        Nbc1Form.preview(built[0], obsGrid, gzGrid));
            }
        };
        fv[0] = renderForm(Nbc1Form.form(r0, self != null && gz != null
                ? self.bearingTo(gz) : Double.NaN, nbc1Entries.get(m.getUID())),
                ctx, box, update);
        update.run();
        showTyping(new AlertDialog.Builder(ctx)
                .setTitle(Nbc1Form.TITLE)
                .setView(sv)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        update.run();
                        NucRecord cur = BlastStore.readNuc(m);
                        if (cur != null) {
                            nbc1Entries.put(m.getUID(), Nbc1Form.entry(
                                    fv[0].values(), built[0].obs));
                            saveNuc(m, cur.withObs(built[0].obs),
                                    "Observations saved on the incident.");
                        }
                    }
                })
                .setNeutralButton("Copy text",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                update.run();
                                copy(Nbc1Form.report(built[0], obsGrid,
                                        gzGrid));
                            }
                        })
                .setNegativeButton("Cancel", null));
    }

    /** BRAVO as typed at the last NBC 1 save, per incident (this run). */
    private final java.util.Map<String, Nbc1Form.Entry> nbc1Entries =
            new java.util.HashMap<>();

    /**
     * The Magnetic port for a position: ATAK's declination there (NaN if it
     * fails); null without a position.
     */
    private static Magnetic magneticAt(final GeoPoint where) {
        if (where == null)
            return null;
        return new Magnetic() {
            @Override
            public double toTrue(double magneticDeg) {
                try {
                    return ATAKUtilities.convertFromMagneticToTrue(where,
                            magneticDeg);
                } catch (RuntimeException e) {
                    return Double.NaN;
                }
            }
        };
    }

    /** Place ground zero by polar plot: azimuth and flash-to-bang. */
    private void promptPolarPlot() {
        final GeoPoint self = selfPoint();
        if (self == null) {
            toast("No GPS position yet: tap the map instead.");
            return;
        }
        if (!newIncident.ready(Mode.NUC)) {
            toast(NewIncident.notReady(Mode.NUC));
            return;
        }
        Context ctx = mapView.getContext();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pd = dp(16);
        box.setPadding(pd, dp(8), pd, 0);
        final FormView fv = renderForm(PolarForm.form(), ctx, box,
                new Runnable() {
                    @Override
                    public void run() {
                        // nothing live in this form
                    }
                });
        showTyping(new AlertDialog.Builder(ctx)
                .setTitle(PolarForm.TITLE)
                .setView(box)
                .setPositiveButton("Place", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        PolarForm.Result res = PolarForm.place(fv.values(),
                                self.getLatitude(), self.getLongitude(),
                                magneticAt(self));
                        if (res.error != null) {
                            toast(res.error);
                            return;
                        }
                        GeoPoint at = new GeoPoint(res.lat, res.lon);
                        if (!at.isValid()) {
                            toast("Could not compute the position.");
                            return;
                        }
                        placeNuc(at, res.obs);
                    }
                })
                .setNegativeButton("Cancel", null));
    }

    // ------------------------------------------------------------------
    // Nuclear incident card: protective actions first

    private void buildNucCard(final Marker m, final NucRecord r) {
        zoneCard.setVisibility(View.VISIBLE);
        zoneCard.addView(selectedHeader());
        final LinearLayout card = vertical();
        card.setBackgroundColor(CARD);
        int p = dp(8);
        card.setPadding(p, p, p, p);
        zoneCard.addView(card);
        // The core decides what the card says; this only renders it.
        final WindPicker wind = NucCard.wind(r);
        final Rendered[] rd = new Rendered[1];
        rd[0] = render(NucCard.card(r, ATAKUtilities.getDisplayName(m),
                mgrs10(m.getPoint()), wind), card, new ScreenAction() {
                    @Override
                    public void run(String action) {
                        nucAction(action, m, wind, rd[0]);
                    }
                });
        youText = rd[0].slots.get(NucCard.SLOT_YOU);
        teamText = rd[0].slots.get(NucCard.SLOT_TEAM);
    }

    /** What the nuclear card's buttons do (ids from NucCard / CardActions). */
    private void nucAction(String action, Marker m, WindPicker wind,
            Rendered rd) {
        WindPicker.Tap tap = wind.tap(action, rd.values());
        if (tap != null) {
            if (tap.error != null)
                toast(tap.error);
            rd.update(wind.screen());
            if (tap.changed)
                saveWind(m, wind.fromDeg(), wind.mph());
            return;
        }
        switch (action) {
            case NucCard.APPLY_EDM: {
                NucMil.Edm e = NucMil.parseEdm(rd.values().get(NucCard.EDM));
                NucRecord cur = BlastStore.readNuc(m);
                if (e == null || cur == null) {
                    toast(NucCard.EDM_ERROR);
                    return;
                }
                saveNuc(m, cur.withEdm(e), NucCard.EDM_APPLIED);
                break;
            }
            case NucCard.NBC1:
                promptNbc1(m);
                break;
            case NucCard.PIC_CIVIL:
            case NucCard.PIC_MILITARY: {
                boolean mil = NucCard.PIC_MILITARY.equals(action);
                NucRecord cur = BlastStore.readNuc(m);
                if (cur != null && cur.mil.on != mil)
                    saveNuc(m, cur.withMil(cur.mil.withOn(mil)), null);
                break;
            }
            case NucCard.COPY_NBC3: {
                NucRecord cur = BlastStore.readNuc(m);
                String t = cur == null ? null
                        : NucCard.nbc3Text(cur, mgrs10(m.getPoint()));
                if (t != null)
                    copy(t);
                break;
            }
            case NucCard.HOW_ZONES:
                showTab(true, NucReference.MIL);
                break;
            case NucCard.WHY:
                showTab(true, NucReference.RINGS);
                break;
            case NucCard.WHAT_TO_DO:
                showTab(true, NucReference.ACTIONS);
                break;
            case CardActions.SEND_ALL:
                // Nuclear sending is deliberate: confirm first.
                confirmSendNuc(m);
                break;
            default:
                standoffAction(action, m);
        }
    }

    private void confirmSendNuc(Marker card) {
        final Marker m = live(card);
        if (m == null)
            return;
        new AlertDialog.Builder(mapView.getContext())
                .setTitle("Send to everyone?")
                .setMessage("Sends the marker, rings and any fallout outlines "
                        + "to every connected TAK client. They are labelled "
                        + "planning estimates.\n\nIf this is an exercise, "
                        + "say so in the name first (Rename…).")
                .setPositiveButton("Send to all",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                try {
                                    Marker l = live(m);
                                    if (l == null)
                                        return;
                                    int n = sharing.sendToAll(l);
                                    toast("Sent to everyone (" + n
                                            + " items).");
                                } catch (RuntimeException e) {
                                    Log.e(TAG, "send failed", e);
                                    toast("Send failed.");
                                }
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** East/north metres of a point from ground zero (local tangent plane). */
    private static double[] eastNorth(GeoPoint gz, GeoPoint p) {
        double d = distance(gz, p);
        if (Double.isNaN(d))
            return null;
        double brg = Math.toRadians(gz.bearingTo(p));
        return new double[] { d * Math.sin(brg), d * Math.cos(brg) };
    }

    private void updateYouNuc(Marker m, NucRecord r) {
        if (youText == null)
            return;
        GeoPoint gz = m.getPoint();
        Marker self = ATAKUtilities.findSelf(mapView);
        GeoPoint sp = selfPoint();
        double[] en = gz == null || sp == null ? null : eastNorth(gz, sp);
        apply(youText, NucCard.you(r, en));
        if (teamText != null && gz != null) {
            List<double[]> pts = new ArrayList<>();
            try {
                for (Contact c : Contacts.getInstance().getAllContacts()) {
                    if (!(c instanceof IndividualContact))
                        continue;
                    MapItem mi = ((IndividualContact) c).getMapItem();
                    if (!(mi instanceof PointMapItem) || mi == self)
                        continue;
                    GeoPoint gp = ((PointMapItem) mi).getPoint();
                    if (gp != null && gp.isValid())
                        pts.add(eastNorth(gz, gp));
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "contact scan failed", e);
            }
            apply(teamText, NucCard.team(r, pts.toArray(new double[0][])));
        }
    }

    // ------------------------------------------------------------------
    // UXO incident card

    private void buildUxoCard(final Marker m, final UxoRecord r) {
        zoneCard.setVisibility(View.VISIBLE);
        zoneCard.addView(selectedHeader());
        LinearLayout card = vertical();
        card.setBackgroundColor(CARD);
        int p = dp(8);
        card.setPadding(p, p, p, p);
        zoneCard.addView(card);
        // The core decides what the card says; this only renders it.
        java.util.Map<String, TextView> slots = render(UxoCard.card(r,
                ATAKUtilities.getDisplayName(m), emergencyNumber(), grid(m)),
                card, new ScreenAction() {
                    @Override
                    public void run(String action) {
                        uxoAction(action, m);
                    }
                }).slots;
        youText = slots.get(UxoCard.SLOT_YOU);
        teamText = slots.get(UxoCard.SLOT_TEAM);
    }

    /** What the UXO card's buttons do (ids from UxoCard / CardActions). */
    private void uxoAction(String action, Marker m) {
        switch (action) {
            case UxoCard.REPORT:
                promptReport(m);
                break;
            case CardActions.SEND_ALL:
                // UXO sending is deliberate: confirm first.
                confirmSendUxo(m);
                break;
            case UxoCard.WHY:
                showTab(true, UxoReference.RINGS);
                break;
            default:
                // Send to…, Fly to, Rename, Export, status, Delete: as for
                // every card.
                standoffAction(action, m);
        }
    }

    /** UXO sending is deliberate: confirm before broadcasting. */
    private void confirmSendUxo(Marker card) {
        final Marker m = live(card);
        if (m == null)
            return;
        new AlertDialog.Builder(mapView.getContext())
                .setTitle("Send to everyone?")
                .setMessage("Sends the marker, cordon and report to every "
                        + "connected TAK client.\n\n" + UxoText.REPORT_CAUTION
                        + "\n\nOnly transmit from outside the cordon.")
                .setPositiveButton("Send to all",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                try {
                                    Marker l = live(m);
                                    if (l == null)
                                        return;
                                    int n = sharing.sendToAll(l);
                                    toast("Sent to everyone (" + n
                                            + " items).");
                                } catch (RuntimeException e) {
                                    Log.e(TAG, "send failed", e);
                                    toast("Send failed.");
                                }
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateYouUxo(Marker m, UxoRecord r) {
        if (youText == null)
            return;
        GeoPoint center = m.getPoint();
        Marker self = ATAKUtilities.findSelf(mapView);
        GeoPoint sp = self != null ? self.getPoint() : null;
        boolean fix = !(center == null || sp == null || !sp.isValid()
                || (sp.getLatitude() == 0 && sp.getLongitude() == 0));
        double dist = Double.NaN, acc = Double.NaN;
        if (fix) {
            double ce = sp.getCE();
            boolean ceKnown = !Double.isNaN(ce) && ce > 0 && ce < 9999999;
            dist = distance(center, sp);
            acc = ceKnown ? ce : Double.NaN;
        }
        apply(youText, UxoCard.you(r, fix, dist, acc));
        if (teamText != null && center != null)
            apply(teamText, UxoCard.team(r, teamDistances(center, self)));
    }

    private double[] teamDistances(GeoPoint center, Marker self) {
        List<Double> ds = new ArrayList<>();
        try {
            for (Contact c : Contacts.getInstance().getAllContacts()) {
                if (!(c instanceof IndividualContact))
                    continue;
                MapItem mi = ((IndividualContact) c).getMapItem();
                if (!(mi instanceof PointMapItem) || mi == self)
                    continue;
                GeoPoint gp = ((PointMapItem) mi).getPoint();
                if (gp != null && gp.isValid())
                    ds.add(distance(center, gp));
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "contact scan failed", e);
        }
        double[] arr = new double[ds.size()];
        for (int i = 0; i < arr.length; i++)
            arr[i] = ds.get(i);
        return arr;
    }

    /** The item's position in MGRS (as ATAK formats it). */
    private static String grid(Marker m) {
        GeoPoint gp = m == null ? null : m.getPoint();
        if (gp == null || !gp.isValid())
            return "";
        try {
            return com.atakmap.coremap.conversions.CoordinateFormatUtilities
                    .formatToString(gp,
                            com.atakmap.coremap.conversions.CoordinateFormat.MGRS);
        } catch (RuntimeException e) {
            return (Math.round(gp.getLatitude() * 1e5) / 1e5) + ", "
                    + (Math.round(gp.getLongitude() * 1e5) / 1e5);
        }
    }

    // ------------------------------------------------------------------
    // Report (simple or EOD 9-line), saved on the incident

    private void promptReport(Marker card) {
        final Marker m = live(card);
        if (m == null)
            return;
        final UxoRecord r0 = BlastStore.readUxo(m);
        if (r0 == null)
            return;
        final Context ctx = mapView.getContext();
        ScrollView sv = new ScrollView(ctx);
        final LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pd = dp(16);
        box.setPadding(pd, dp(8), pd, 0);
        sv.addView(box);
        // The core describes the form and computes the note and preview.
        final FormView[] fv = new FormView[1];
        final Runnable update = new Runnable() {
            @Override
            public void run() {
                if (fv[0] == null)
                    return;
                FormValues v = fv[0].values();
                fv[0].set(UxoReportForm.SLOT_PRI_NOTE,
                        UxoReportForm.priorityNote(v));
                fv[0].set(UxoReportForm.SLOT_PREVIEW, UxoReportForm.preview(
                        r0, v, callsign(), grid(m)));
            }
        };
        fv[0] = renderForm(UxoReportForm.form(r0, emergencyNumber()), ctx, box,
                update);
        update.run();
        final Runnable save = new Runnable() {
            @Override
            public void run() {
                saveReport(m, UxoReportForm.report(fv[0].values()));
            }
        };

        showTyping(new AlertDialog.Builder(ctx)
                .setTitle(UxoReportForm.title(BlastStore.nameOf(m)))
                .setView(sv)
                .setPositiveButton("Save",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                save.run();
                            }
                        })
                .setNeutralButton("Copy text",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                save.run();
                                copy(UxoReportForm.preview(r0,
                                        fv[0].values(), callsign(), grid(m)));
                            }
                        })
                .setNegativeButton("Cancel", null)
                );
    }

    private EditText field(Context ctx, LinearLayout box, String hint,
            String value, boolean number) {
        return field(ctx, box, hint, value, number, number ? 4
                : com.atakmap.android.blast.plugin.core.UxoCodec.MAX_REPORT_TEXT);
    }

    private EditText field(Context ctx, LinearLayout box, String hint,
            String value, boolean number, int maxLen) {
        box.addView(label(ctx, hint));
        EditText e = new EditText(ctx);
        doneKey(e);
        e.setText(value);
        e.setInputType(number ? InputType.TYPE_CLASS_NUMBER
                : InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setFilters(new android.text.InputFilter[] {
                new android.text.InputFilter.LengthFilter(maxLen) });
        box.addView(e);
        return e;
    }

    private TextView label(Context ctx, String s) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(8), 0, dp(2));
        return t;
    }

    /** Single-choice push buttons, {@code perRow} to a row (dark style). */
    private List<Button> choiceRow(LinearLayout box, String[] labels,
            int perRow) {
        List<Button> out = new ArrayList<>();
        LinearLayout row = null;
        for (int i = 0; i < labels.length; i++) {
            if (i % perRow == 0) {
                row = horizontal();
                box.addView(row, full());
            }
            Button b = button(labels[i], BTN);
            LinearLayout.LayoutParams lp = weight();
            lp.setMargins(dp(1), dp(1), dp(1), dp(1));
            row.addView(b, lp);
            out.add(b);
        }
        // Keep the last row's buttons the same width as the others.
        int pad = (perRow - labels.length % perRow) % perRow;
        for (int i = 0; i < pad && row != null; i++)
            row.addView(new View(plugin), weight());
        return out;
    }

    private void saveReport(Marker card, UxoRecord.Report rep) {
        try {
            Marker m = live(card);
            UxoRecord r = m == null ? null : BlastStore.readUxo(m);
            if (r == null)
                return;
            if (!store.writeUxo(m, r.withReport(rep))) {
                live(null);
                return;
            }
            toast("Report saved on the incident. Share it with SEND TO ALL or "
                    + "Send to… when you choose.");
            cardSignature = null;
            listSignature = null;
            refresh();
        } catch (RuntimeException e) {
            Log.e(TAG, "report save failed", e);
            toast("Could not save the report.");
        }
    }

    private void copy(String s) {
        try {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) mapView.getContext()
                            .getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText(
                        "Blast report", s));
                toast("Report copied. Paste it into a text, chat or email.");
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "copy failed", e);
            toast("Could not copy the report.");
        }
    }

    private String emergencyNumber() {
        String n = prefs.getString(PREF_EMERGENCY, DEFAULT_EMERGENCY);
        return n == null || n.trim().isEmpty() ? DEFAULT_EMERGENCY : n.trim();
    }

    private void promptEmergencyNumber() {
        final EditText input = new EditText(mapView.getContext());
        doneKey(input);
        input.setSingleLine(true);
        input.setText(emergencyNumber());
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setFilters(new android.text.InputFilter[] {
                new android.text.InputFilter.LengthFilter(
                        ReferenceTab.EMERGENCY_MAX)
        });
        showTyping(new AlertDialog.Builder(mapView.getContext())
                .setTitle(ReferenceTab.EMERGENCY_TITLE)
                .setMessage(ReferenceTab.EMERGENCY_HELP)
                .setView(input)
                .setPositiveButton("Save",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                String v = Names.sanitize(
                                        input.getText().toString());
                                prefs.edit().putString(PREF_EMERGENCY,
                                        v == null ? DEFAULT_EMERGENCY : v)
                                        .apply();
                                if (refView != null)
                                    refView.update(new Screen().add(
                                            ReferenceTab.emergency(
                                                    emergencyNumber())));
                                cardSignature = null;
                                refresh();
                            }
                        })
                .setNegativeButton("Cancel", null)
                );
    }

    // ------------------------------------------------------------------
    // Reference tab

    private LinearLayout buildReference() {
        LinearLayout col = vertical();
        refView = render(ReferenceTab.screen(emergencyNumber()), col,
                new ScreenAction() {
                    @Override
                    public void run(String action) {
                        if (ReferenceTab.CHANGE_EMERGENCY.equals(action))
                            promptEmergencyNumber();
                    }
                });
        return col;
    }

    // ------------------------------------------------------------------
    // Threat selection

    /**
     * Clear the threat choice. Called when the pane is opened and after
     * each incident is placed, so every incident needs a fresh choice.
     */
    public void resetThreat() {
        stopPick();
        newIncident.reset();
        renderNew();
    }

    // ------------------------------------------------------------------
    // Placement

    private void place(GeoPoint at) {
        try {
            if (!newIncident.ready(Mode.IED)) {
                toast(NewIncident.notReady(Mode.IED));
                return;
            }
            BlastRecord r = newIncident.standoffRecord(callsign(),
                    System.currentTimeMillis());
            Marker m = store.create(at, r);
            if (m == null) {
                toast("Could not place the incident.");
                return;
            }
            placeTarget = null;
            selectedZoneUid = m.getUID();
            resetThreat();
            if (autoSend()) {
                sharing.sendToAll(m);
                toast(NewIncident.PLACED_SENT);
            } else {
                toast(NewIncident.PLACED);
            }
            onShow();
            main.post(new Runnable() {
                @Override
                public void run() {
                    root.smoothScrollTo(0, 0);
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "place failed", e);
            toast("Could not place the incident.");
        }
    }

    private void startPick(Mode kind) {
        if (!newIncident.ready(kind))
            return;
        pickKind = kind;
        final MapEventDispatcher d = mapView.getMapEventDispatcher();
        d.pushListeners();
        d.clearListeners(MapEvent.MAP_CLICK);
        d.clearListeners(MapEvent.ITEM_CLICK);
        d.clearListeners(MapEvent.MAP_LONG_PRESS);
        pickListener = new MapEventDispatcher.MapEventDispatchListener() {
            @Override
            public void onMapEvent(MapEvent event) {
                onPicked(event);
            }
        };
        d.addMapEventListener(MapEvent.MAP_CLICK, pickListener);
        d.addMapEventListener(MapEvent.ITEM_CLICK, pickListener);
        d.addMapEventListener(MapEvent.MAP_LONG_PRESS, pickListener);
        picking = true;
        updateNew();
        TextContainer.getInstance().displayPrompt(
                NewIncident.pickPrompt(kind));
    }

    /**
     * Uses MapView.inverseWithElevation, deprecated but not slated for
     * removal (DeprecatedApi forRemoval=false); ATAK's own Tool.findPoint
     * converts taps the same way.
     */
    @SuppressWarnings("deprecation")
    private void onPicked(MapEvent event) {
        GeoPoint gp = null;
        MapItem item = event.getItem();
        if (MapEvent.ITEM_CLICK.equals(event.getType())
                && item instanceof PointMapItem) {
            gp = ((PointMapItem) item).getPoint();
        } else {
            PointF p = event.getPointF();
            if (p != null) {
                GeoPointMetaData m = mapView.inverseWithElevation(p.x, p.y);
                if (m != null)
                    gp = m.get();
            }
        }
        Mode kind = pickKind;
        stopPick();
        if (gp == null || !gp.isValid())
            return;
        if (kind == Mode.UXO)
            placeUxo(gp, MapEvent.ITEM_CLICK.equals(event.getType())
                    ? UxoRecord.Placement.MARKER : UxoRecord.Placement.MAP);
        else if (kind == Mode.NUC)
            placeNuc(gp);
        else
            place(gp);
    }

    private void stopPick() {
        if (!picking)
            return;
        picking = false;
        MapEventDispatcher d = mapView.getMapEventDispatcher();
        if (pickListener != null) {
            d.removeMapEventListener(MapEvent.MAP_CLICK, pickListener);
            d.removeMapEventListener(MapEvent.ITEM_CLICK, pickListener);
            d.removeMapEventListener(MapEvent.MAP_LONG_PRESS, pickListener);
            pickListener = null;
        }
        d.popListeners();
        TextContainer.getInstance().closePrompt();
        pickKind = Mode.NONE;
        updateNew();
    }

    // ------------------------------------------------------------------
    // Refresh

    private void refresh() {
        try {
            refreshCard();
            refreshList();
        } catch (RuntimeException e) {
            Log.e(TAG, "refresh failed", e);
        }
    }

    private Marker selectedZone() {
        if (selectedZoneUid == null)
            return null;
        // Not mapView.getMapItem alone: ATAK's UID index can still return
        // an incident after it was deleted.
        return store.liveMarker(selectedZoneUid);
    }

    private void refreshCard() {
        Marker m = selectedZone();
        NucRecord n = m == null ? null : BlastStore.readNuc(m);
        UxoRecord u = m == null || n != null ? null : BlastStore.readUxo(m);
        BlastRecord r = m == null || u != null || n != null ? null
                : BlastStore.read(m);
        String sig = m == null ? "" : m.getUID() + "|"
                + m.getMetaString(BlastStore.META, "") + "|"
                + m.getMetaString(BlastStore.META_UXO, "") + "|"
                + m.getMetaString(BlastStore.META_NUC, "") + "|"
                + ATAKUtilities.getDisplayName(m) + "|" + emergencyNumber();
        if (!sig.equals(cardSignature)) {
            cardSignature = sig;
            if (n != null) {
                zoneCard.removeAllViews();
                youText = null;
                teamText = null;
                buildNucCard(m, n);
            } else if (u != null) {
                zoneCard.removeAllViews();
                youText = null;
                teamText = null;
                buildUxoCard(m, u);
            } else {
                buildCard(m, r);
            }
        }
        if (m != null && n != null)
            updateYouNuc(m, n);
        else if (m != null && u != null)
            updateYouUxo(m, u);
        else if (m != null && r != null)
            updateYou(m, r);
    }

    private void buildCard(final Marker m, final BlastRecord r) {
        zoneCard.removeAllViews();
        youText = null;
        teamText = null;
        if (m == null || r == null) {
            zoneCard.setVisibility(View.GONE);
            return;
        }
        zoneCard.setVisibility(View.VISIBLE);
        zoneCard.addView(selectedHeader());
        LinearLayout card = vertical();
        card.setBackgroundColor(CARD);
        int p = dp(8);
        card.setPadding(p, p, p, p);
        zoneCard.addView(card);
        // The core decides what the card says; this only renders it.
        java.util.Map<String, TextView> slots = render(StandoffCard.card(r,
                ATAKUtilities.getDisplayName(m)), card, new ScreenAction() {
                    @Override
                    public void run(String action) {
                        standoffAction(action, m);
                    }
                }).slots;
        youText = slots.get(StandoffCard.SLOT_YOU);
        teamText = slots.get(StandoffCard.SLOT_TEAM);
    }

    /** What the standoff card's buttons do (ids from StandoffCard). */
    private void standoffAction(String action, Marker m) {
        switch (action) {
            case StandoffCard.SEND_ALL:
                try {
                    Marker l = live(m);
                    if (l == null)
                        return;
                    int n = sharing.sendToAll(l);
                    toast("Sent to everyone (" + n + " items).");
                } catch (RuntimeException e) {
                    Log.e(TAG, "send failed", e);
                    toast("Send failed.");
                }
                break;
            case StandoffCard.SEND_TO: {
                Marker l = live(m);
                if (l != null)
                    sharing.sendTo(l);
                break;
            }
            case StandoffCard.FLY:
                goTo(m);
                break;
            case StandoffCard.RENAME:
                promptRename(m);
                break;
            case StandoffCard.EXPORT:
                promptExport(m);
                break;
            case StandoffCard.STATUS:
                confirmStatus(m);
                break;
            case StandoffCard.DELETE:
                confirmDelete(m);
                break;
            default:
                Log.w(TAG, "unknown card action " + action);
        }
    }

    // ------------------------------------------------------------------
    // Screen model renderer (core/screen -> Android views)

    /** Runs a button's action id. */
    private interface ScreenAction {
        void run(String action);
    }

    /** Blast's dark-pane colour for a core tone. */
    private static int color(Tone t) {
        switch (t) {
            case DIM:
                return DIM;
            case AMBER:
                return AMBER;
            case RED:
                return RED;
            case GREEN:
                return GREEN;
            case BLUE:
                return BLUE;
            case ORANGE:
                return ORANGE;
            case NUC:
                return NUC_COLOR;
            default:
                return TEXT;
        }
    }

    private static int color(Ink k) {
        return k.tone != null ? color(k.tone) : k.argb;
    }

    /**
     * A rendered core screen: the views of its live slots, buttons (by
     * action), entry boxes, checks and sections, so they can be read and
     * updated in place.
     */
    private final class Rendered {
        final ScreenAction actions;
        final java.util.Map<String, TextView> slots = new java.util.HashMap<>();
        final java.util.Map<String, Button> buttons = new java.util.HashMap<>();
        final java.util.Map<String, EditText> entries = new java.util.HashMap<>();
        final java.util.Map<String, CheckBox> checks = new java.util.HashMap<>();
        final java.util.Map<String, LinearLayout> sections = new java.util.HashMap<>();
        /** True while the host sets a box or check (no change action). */
        boolean quiet;

        Rendered(ScreenAction actions) {
            this.actions = actions;
        }

        /** What is typed in the entry boxes and ticked in the checks. */
        FormValues values() {
            FormValues v = new FormValues();
            for (java.util.Map.Entry<String, EditText> e : entries.entrySet())
                v.put(e.getKey(), e.getValue().getText().toString());
            for (java.util.Map.Entry<String, CheckBox> e : checks.entrySet())
                v.put(e.getKey(), e.getValue().isChecked());
            return v;
        }

        /**
         * Restyle in place from a newer model of the same screen: button
         * labels, colours, enabled and shown states, box text the model
         * sets, checks, sections, and live slot text.
         */
        void update(Screen fresh) {
            update(fresh.items());
        }

        private void update(List<Item> items) {
            for (Item it : items) {
                if (it instanceof Buttons) {
                    for (com.atakmap.android.blast.plugin.core.screen.Button b
                            : ((Buttons) it).buttons)
                        restyle(b);
                } else if (it instanceof EntryRow) {
                    EntryRow er = (EntryRow) it;
                    for (com.atakmap.android.blast.plugin.core.screen.Button b
                            : er.buttons)
                        restyle(b);
                    EditText e = entries.get(er.id);
                    if (e != null && er.value != null
                            && !er.value.equals(e.getText().toString())) {
                        quiet = true;
                        try {
                            e.setText(er.value);
                        } finally {
                            quiet = false;
                        }
                    }
                } else if (it instanceof Check) {
                    Check c = (Check) it;
                    CheckBox cb = checks.get(c.id);
                    if (cb != null && cb.isChecked() != c.checked) {
                        quiet = true;
                        try {
                            cb.setChecked(c.checked);
                        } finally {
                            quiet = false;
                        }
                    }
                } else if (it instanceof Section) {
                    Section sec = (Section) it;
                    LinearLayout l = sections.get(sec.id);
                    if (l == null)
                        continue;
                    l.setVisibility(sec.visible ? View.VISIBLE : View.GONE);
                    if (sec.live) {
                        l.removeAllViews();
                        renderItems(sec.items, l, this);
                    } else {
                        update(sec.items);
                    }
                } else if (it instanceof TitleRow) {
                    TitleRow tr = (TitleRow) it;
                    restyle(tr.button);
                    if (tr.text.slot != null)
                        apply(slots.get(tr.text.slot), tr.text);
                } else if (it instanceof Text && it.slot != null) {
                    apply(slots.get(it.slot), (Text) it);
                }
            }
        }

        private void restyle(
                com.atakmap.android.blast.plugin.core.screen.Button b) {
            Button v = buttons.get(b.action);
            if (v == null)
                return;
            v.setText(b.label);
            tint(v, buttonColor(b.kind));
            enable(v, b);
        }
    }

    /** Enabled (dimmed when not) and shown state of a core button. */
    private static void enable(Button v,
            com.atakmap.android.blast.plugin.core.screen.Button b) {
        v.setEnabled(b.enabled);
        v.setAlpha(b.enabled ? 1f : 0.4f);
        v.setVisibility(b.visible ? View.VISIBLE : View.GONE);
    }

    /** A button for a core button, running its action. */
    private Button screenButton(
            final com.atakmap.android.blast.plugin.core.screen.Button b,
            final Rendered rd) {
        Button v = button(b.label, buttonColor(b.kind));
        if (b.bold)
            v.setTypeface(Typeface.DEFAULT_BOLD);
        if (b.alignStart)
            v.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        if (b.icon != null)
            setIcon(v, threatIcon(b.icon));
        if (b.danger)
            v.setTextColor(RED);
        enable(v, b);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rd.actions.run(b.action);
            }
        });
        rd.buttons.put(b.action, v);
        return v;
    }

    /** A TextView for a core text (kept in {@code rd} if it has a slot). */
    private TextView textView(Text t, Rendered rd) {
        TextView v = text(t.text, t.size, color(t.tone), t.bold);
        if (t.mono)
            v.setTypeface(Typeface.MONOSPACE);
        if (t.icon != null) {
            setIcon(v, threatIcon(t.icon));
            v.setCompoundDrawablePadding(dp(6));
        }
        if (t.padTopDp != 0 || t.padBottomDp != 0 || t.indentDp != 0)
            v.setPadding(dp(t.indentDp), dp(t.padTopDp), 0,
                    dp(t.padBottomDp));
        if (t.links)
            linkify(v, t.text);
        if (t.slot != null)
            rd.slots.put(t.slot, v);
        return v;
    }

    /**
     * Make web addresses in {@code text} tappable. They open in the
     * phone's browser (from ATAK's activity, as the plugin's own context
     * can't start one); text without an address is left as it is.
     */
    private void linkify(TextView v, String text) {
        android.text.SpannableString sp = new android.text.SpannableString(
                text);
        if (!android.text.util.Linkify.addLinks(sp,
                android.text.util.Linkify.WEB_URLS))
            return;
        for (android.text.style.URLSpan u : sp.getSpans(0, sp.length(),
                android.text.style.URLSpan.class)) {
            int a = sp.getSpanStart(u), b = sp.getSpanEnd(u);
            final String url = u.getURL();
            sp.removeSpan(u);
            sp.setSpan(new android.text.style.ClickableSpan() {
                @Override
                public void onClick(View w) {
                    openUrl(url);
                }
            }, a, b, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        v.setLinkTextColor(BLUE);
        v.setText(sp);
        v.setMovementMethod(android.text.method.LinkMovementMethod
                .getInstance());
    }

    private void openUrl(String url) {
        try {
            android.content.Intent i = new android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url));
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            mapView.getContext().startActivity(i);
        } catch (RuntimeException e) {
            Log.w(TAG, "cannot open " + url, e);
            toast("No app on this device can open " + url);
        }
    }

    /** An incident-list style row: colour bar, text, small buttons. */
    private void listRow(final ListRow lr, LinearLayout into,
            final Rendered rd) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(lr.selected ? SEL : CARD);
        int p = dp(6);
        row.setPadding(p, p, p, p);
        View sw = new View(plugin);
        sw.setBackgroundColor(color(lr.bar));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(6),
                dp(36));
        sl.setMargins(0, 0, dp(6), 0);
        row.addView(sw, sl);
        TextView t = textView(lr.text, rd);
        row.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        for (com.atakmap.android.blast.plugin.core.screen.Button b
                : lr.buttons) {
            Button v = screenButton(b, rd);
            v.setMinWidth(dp(52));
            row.addView(v);
        }
        View.OnClickListener select = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rd.actions.run(lr.action);
            }
        };
        t.setOnClickListener(select);
        row.setOnClickListener(select);
        LinearLayout.LayoutParams rl = full();
        rl.setMargins(0, 0, 0, dp(3));
        into.addView(row, rl);
    }

    /** Add a core screen's items to {@code into}. */
    private Rendered render(Screen screen, LinearLayout into,
            final ScreenAction actions) {
        Rendered rd = new Rendered(actions);
        renderItems(screen.items(), into, rd);
        return rd;
    }

    private void renderItems(List<Item> items, LinearLayout into,
            final Rendered rd) {
        for (Item it : items) {
            if (it instanceof Text) {
                into.addView(textView((Text) it, rd));
            } else if (it instanceof TitleRow) {
                TitleRow tr = (TitleRow) it;
                LinearLayout row = horizontal();
                if (!tr.buttonAtBottom)
                    row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(textView(tr.text, rd), weight());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                if (tr.buttonAtBottom)
                    lp.gravity = Gravity.BOTTOM;
                row.addView(screenButton(tr.button, rd), lp);
                into.addView(row);
            } else if (it instanceof ListRow) {
                listRow((ListRow) it, into, rd);
            } else if (it instanceof Legend) {
                Legend l = (Legend) it;
                LinearLayout line = horizontal();
                View sw = new View(plugin);
                sw.setBackgroundColor(color(l.swatch));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        dp(12), dp(12));
                lp.setMargins(0, 0, dp(6), 0);
                lp.gravity = Gravity.CENTER_VERTICAL;
                line.addView(sw, lp);
                for (int i = 0; i < l.cellCount(); i++)
                    line.addView(text(l.cell(i), l.size, color(l.tone),
                            l.bold(i)), new LinearLayout.LayoutParams(0,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    l.weight(i)));
                into.addView(line);
            } else if (it instanceof KeyRow) {
                into.addView(keyRow((KeyRow) it));
            } else if (it instanceof Section) {
                Section sec = (Section) it;
                LinearLayout l = vertical();
                l.setVisibility(sec.visible ? View.VISIBLE : View.GONE);
                renderItems(sec.items, l, rd);
                rd.sections.put(sec.id, l);
                into.addView(l);
            } else if (it instanceof Check) {
                final Check c = (Check) it;
                CheckBox cb = new CheckBox(plugin);
                cb.setText(c.label);
                cb.setTextColor(TEXT);
                if (c.checked)
                    cb.setChecked(true);
                cb.setOnCheckedChangeListener(
                        new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(CompoundButton b,
                                    boolean on) {
                                if (!rd.quiet)
                                    rd.actions.run(c.id);
                            }
                        });
                rd.checks.put(c.id, cb);
                into.addView(cb);
            } else if (it instanceof EntryRow) {
                final EntryRow er = (EntryRow) it;
                LinearLayout row = horizontal();
                if (er.centered)
                    row.setGravity(Gravity.CENTER_VERTICAL);
                EditText e = inlineNumber(er.hint, er.maxLength);
                if (er.digitsOnly)
                    e.setInputType(InputType.TYPE_CLASS_NUMBER);
                if (er.value != null && !er.value.isEmpty())
                    e.setText(er.value);
                if (er.changeAction != null)
                    e.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int a,
                                int b, int c) {
                        }

                        @Override
                        public void onTextChanged(CharSequence s, int a,
                                int b, int c) {
                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            if (!rd.quiet)
                                rd.actions.run(er.changeAction);
                        }
                    });
                row.addView(e, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, er.weight));
                rd.entries.put(er.id, e);
                for (com.atakmap.android.blast.plugin.core.screen.Button b
                        : er.buttons)
                    row.addView(screenButton(b, rd),
                            new LinearLayout.LayoutParams(0,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    b.weight));
                into.addView(row);
            } else if (it instanceof Buttons) {
                Buttons bs = (Buttons) it;
                boolean spaced = bs.marginTopDp != 0 || bs.marginBottomDp != 0;
                LinearLayout row = bs.buttons.size() == 1 ? null : horizontal();
                for (com.atakmap.android.blast.plugin.core.screen.Button b
                        : bs.buttons) {
                    Button v = screenButton(b, rd);
                    if (row == null) {
                        // A lone button spans the card.
                        LinearLayout.LayoutParams lp = full();
                        if (spaced)
                            lp.setMargins(0, dp(bs.marginTopDp), 0,
                                    dp(bs.marginBottomDp));
                        into.addView(v, lp);
                    } else if (bs.tiles) {
                        v.setLines(2);
                        LinearLayout.LayoutParams lp =
                                new LinearLayout.LayoutParams(0,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        b.weight);
                        lp.setMargins(dp(1), dp(1), dp(1), dp(1));
                        row.addView(v, lp);
                    } else {
                        row.addView(v, new LinearLayout.LayoutParams(0,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                b.weight));
                    }
                }
                if (row != null) {
                    if (spaced) {
                        LinearLayout.LayoutParams lp = full();
                        lp.setMargins(0, dp(bs.marginTopDp), 0,
                                dp(bs.marginBottomDp));
                        into.addView(row, lp);
                    } else {
                        into.addView(row);
                    }
                }
            }
        }
    }

    private static int buttonColor(
            com.atakmap.android.blast.plugin.core.screen.Button.Kind k) {
        switch (k) {
            case PRIMARY:
                return SEND;
            case SELECTED:
                return NUC_SEL;
            case SELECTED_UXO:
                return UXO_SEL;
            case SELECTED_STANDOFF:
                return SEL;
            default:
                return BTN;
        }
    }

    /** A map key line (TALL: rings; SHORT: fallout bands, zones). */
    private View keyRow(KeyRow k) {
        LinearLayout row = horizontal();
        View bar = new View(plugin);
        bar.setBackgroundColor(color(k.bar));
        if (k.kind == KeyRow.Kind.TALL) {
            row.setPadding(0, dp(3), 0, dp(3));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                    dp(8), ViewGroup.LayoutParams.MATCH_PARENT);
            bl.setMargins(0, 0, dp(8), 0);
            row.addView(bar, bl);
            LinearLayout tx = vertical();
            tx.addView(text(k.title, 14, color(k.titleInk), true));
            if (k.meaning != null)
                tx.addView(text(k.meaning, 12, DIM, false));
            row.addView(tx, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        } else {
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                    dp(8), dp(18));
            bl.setMargins(0, dp(2), dp(8), dp(2));
            row.addView(bar, bl);
            row.addView(text(k.title, 13, color(k.titleInk), true),
                    new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }

    /** A rendered core form: its inputs, choices, checks and live slots. */
    private final class FormView {
        final java.util.Map<String, EditText> inputs = new java.util.HashMap<>();
        final java.util.Map<String, CheckBox> checks = new java.util.HashMap<>();
        final java.util.Map<String, String[]> choices = new java.util.HashMap<>();
        final java.util.Map<String, TextView> slots = new java.util.HashMap<>();

        FormValues values() {
            FormValues v = new FormValues();
            for (java.util.Map.Entry<String, EditText> e : inputs.entrySet())
                v.put(e.getKey(), e.getValue().getText().toString());
            for (java.util.Map.Entry<String, CheckBox> e : checks.entrySet())
                v.put(e.getKey(), e.getValue().isChecked());
            for (java.util.Map.Entry<String, String[]> e : choices.entrySet())
                v.put(e.getKey(), e.getValue()[0]);
            return v;
        }

        void set(String slot, String text) {
            TextView t = slots.get(slot);
            if (t != null)
                t.setText(text);
        }
    }

    /**
     * Build a core form into a dialog box, in the dialog's own context and
     * style (as 1.1 did). {@code onChange} runs after every edit, tap or
     * check; it is not run here.
     */
    private FormView renderForm(Screen screen, Context ctx, LinearLayout box,
            final Runnable onChange) {
        final FormView f = new FormView();
        TextWatcher tw = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onChange.run();
            }
        };
        for (Item it : screen.items()) {
            if (it instanceof Text) {
                Text t = (Text) it;
                TextView v = new TextView(ctx);
                v.setText(t.text);
                if (t.size > 0)
                    v.setTextSize(TypedValue.COMPLEX_UNIT_SP, t.size);
                if (t.tone != Tone.DEFAULT)
                    v.setTextColor(color(t.tone));
                if (t.bold)
                    v.setTypeface(Typeface.DEFAULT_BOLD);
                if (t.mono)
                    v.setTypeface(Typeface.MONOSPACE);
                if (t.selectable)
                    v.setTextIsSelectable(true);
                v.setPadding(0, dp(t.padTopDp), 0, dp(t.padBottomDp));
                box.addView(v);
                if (t.slot != null)
                    f.slots.put(t.slot, v);
            } else if (it instanceof Input && ((Input) it).hinted) {
                Input in = (Input) it;
                EditText e = new EditText(ctx);
                doneKey(e);
                e.setHint(in.label);
                e.setInputType(InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                e.setSingleLine(true);
                box.addView(e);
                e.addTextChangedListener(tw);
                f.inputs.put(in.id, e);
            } else if (it instanceof Input) {
                Input in = (Input) it;
                EditText e = field(ctx, box, in.label, in.value, in.numeric,
                        in.maxLength);
                e.addTextChangedListener(tw);
                f.inputs.put(in.id, e);
            } else if (it instanceof Check) {
                Check c = (Check) it;
                CheckBox cb = new CheckBox(ctx);
                cb.setText(c.label);
                if (c.checked)
                    cb.setChecked(true);
                box.addView(cb);
                cb.setOnCheckedChangeListener(
                        new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(CompoundButton b,
                                    boolean on) {
                                onChange.run();
                            }
                        });
                f.checks.put(c.id, cb);
            } else if (it instanceof Choice) {
                final Choice c = (Choice) it;
                String[] labels = new String[c.size()];
                for (int i = 0; i < labels.length; i++)
                    labels[i] = c.label(i);
                final List<Button> bs = choiceRow(box, labels, c.perRow);
                final String[] value = { c.selected };
                final int sel = buttonColor(c.selectedKind);
                for (int i = 0; i < bs.size(); i++) {
                    final int k = i;
                    bs.get(i).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            value[0] = c.tap(k, value[0]);
                            for (int j = 0; j < bs.size(); j++)
                                tint(bs.get(j), c.code(j).equals(value[0])
                                        ? sel : BTN);
                            onChange.run();
                        }
                    });
                    tint(bs.get(i), c.code(i).equals(value[0]) ? sel : BTN);
                }
                f.choices.put(c.id, value);
            }
        }
        return f;
    }

    /** Update a live slot's TextView from a core Text. */
    private static void apply(TextView v, Text t) {
        if (v == null || t == null)
            return;
        v.setText(t.text);
        v.setTextColor(color(t.tone));
    }

    private void updateYou(Marker m, BlastRecord r) {
        if (youText == null)
            return;
        GeoPoint center = m.getPoint();
        Marker self = ATAKUtilities.findSelf(mapView);
        GeoPoint sp = self != null ? self.getPoint() : null;
        boolean fix = !(center == null || sp == null || !sp.isValid()
                || (sp.getLatitude() == 0 && sp.getLongitude() == 0));
        double dist = Double.NaN, acc = Double.NaN;
        if (fix) {
            // CE is NaN when unknown; CotPoint uses 9999999 for unknown.
            double ce = sp.getCE();
            boolean ceKnown = !Double.isNaN(ce) && ce > 0 && ce < 9999999;
            dist = distance(center, sp);
            acc = ceKnown ? ce : Double.NaN;
        }
        apply(youText, StandoffCard.you(r, fix, dist, acc));
        if (teamText != null && center != null) {
            List<Double> ds = new ArrayList<>();
            try {
                for (Contact c : Contacts.getInstance().getAllContacts()) {
                    if (!(c instanceof IndividualContact))
                        continue;
                    MapItem mi = ((IndividualContact) c).getMapItem();
                    if (!(mi instanceof PointMapItem) || mi == self)
                        continue;
                    GeoPoint gp = ((PointMapItem) mi).getPoint();
                    if (gp != null && gp.isValid())
                        ds.add(distance(center, gp));
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "contact scan failed", e);
            }
            double[] arr = new double[ds.size()];
            for (int i = 0; i < arr.length; i++)
                arr[i] = ds.get(i);
            apply(teamText, StandoffCard.team(r, arr));
        }
    }

    private void refreshList() {
        final GeoPoint ref = referencePoint();
        if (debugLine != null)
            debugLine.setText("Debug: incidents " + store.lastScan());
        // Read each record once (parsing is not free).
        List<IncidentList.Entry> entries = new ArrayList<>();
        for (Marker m : store.all()) {
            String uid = m.getUID(), name = ATAKUtilities.getDisplayName(m);
            double d = distance(ref, m);
            NucRecord nr = BlastStore.readNuc(m);
            if (nr != null) {
                entries.add(IncidentList.Entry.of(uid, name, nr, d));
                continue;
            }
            UxoRecord u = BlastStore.readUxo(m);
            if (u != null) {
                entries.add(IncidentList.Entry.of(uid, name, u, d));
                continue;
            }
            BlastRecord r = BlastStore.read(m);
            if (r != null)
                entries.add(IncidentList.Entry.of(uid, name, r, d));
        }
        List<IncidentList.Entry> sorted = IncidentList.sorted(entries);
        String sig = IncidentList.signature(sorted, selectedZoneUid);
        if (sig.equals(listSignature))
            return;
        listSignature = sig;
        listView.update(IncidentList.screen(sorted, selectedZoneUid));
    }

    /** What the incident list's rows and buttons do. */
    private void listAction(String action) {
        if (IncidentList.DELETE_CLEARED.equals(action)) {
            confirmDeleteCleared();
            return;
        }
        String uid;
        if ((uid = IncidentList.uid(action, IncidentList.SELECT)) != null) {
            // Tap the selected incident again to collapse its card.
            if (uid.equals(selectedZoneUid)) {
                collapseSelected();
                return;
            }
            selectedZoneUid = uid;
            listSignature = null;
            cardSignature = null;
            refresh();
            return;
        }
        for (String p : new String[] { IncidentList.GO,
                IncidentList.NINE_LINE, IncidentList.DELETE })
            if ((uid = IncidentList.uid(action, p)) != null) {
                Marker m = store.liveMarker(uid);
                if (m == null) {
                    toast("This incident is no longer on the map.");
                    afterDelete();
                    return;
                }
                if (p.equals(IncidentList.GO)) {
                    goTo(m);
                } else if (p.equals(IncidentList.NINE_LINE)) {
                    selectedZoneUid = m.getUID();
                    listSignature = null;
                    cardSignature = null;
                    refresh();
                    promptReport(m);
                } else {
                    confirmDelete(m);
                }
                return;
            }
    }

    private static boolean isCleared(Marker m) {
        NucRecord n = BlastStore.readNuc(m);
        if (n != null)
            return n.isCleared();
        UxoRecord u = BlastStore.readUxo(m);
        if (u != null)
            return u.isCleared();
        BlastRecord r = BlastStore.read(m);
        return r != null && r.isCleared();
    }

    /** Fly to the incident, framing the preferred ring. */
    private void goTo(Marker card) {
        Marker m = live(card);
        if (m == null)
            return;
        selectedZoneUid = m.getUID();
        listSignature = null;
        cardSignature = null;
        MapItem pref = store.liveItem(m.getUID() + BlastStore.PREF_SUFFIX);
        if (pref == null)
            pref = store.liveItem(m.getUID() + BlastStore.CORDON_SUFFIX);
        if (pref == null)
            pref = store.liveItem(m.getUID() + NucText.Ring.LDZ.suffix);
        MapTouchController.goTo(pref != null ? pref : m, false);
        refresh();
    }

    private int threatIcon(String rowId) {
        if (rowId == null)
            return 0;
        switch (rowId) {
            case "PIPE_BOMB":
                return R.drawable.ic_threat_pipe_bomb;
            case "SUICIDE_BOMBER":
                return R.drawable.ic_threat_suicide_bomber;
            case "BRIEFCASE":
                return R.drawable.ic_threat_briefcase;
            case "CAR":
                return R.drawable.ic_threat_car;
            case "SUV_VAN":
                return R.drawable.ic_threat_suv_van;
            case "SMALL_TRUCK":
                return R.drawable.ic_threat_small_truck;
            case "CONTAINER_TRUCK":
                return R.drawable.ic_threat_container_truck;
            case "SEMI_TRAILER":
                return R.drawable.ic_threat_semi_trailer;
            default:
                return 0;
        }
    }

    private void setIcon(TextView v, int res) {
        if (res == 0)
            return;
        try {
            android.graphics.drawable.Drawable d = plugin.getResources()
                    .getDrawable(res, plugin.getTheme());
            int sz = dp(28);
            d.setBounds(0, 0, sz, sz);
            v.setCompoundDrawablesRelative(d, null, null, null);
        } catch (RuntimeException e) {
            Log.w(TAG, "icon " + res + " unavailable", e);
        }
    }

    /** Self position, or the map centre without a fix (for list sorting). */
    private GeoPoint referencePoint() {
        GeoPoint gp = selfPoint();
        if (gp != null)
            return gp;
        GeoPointMetaData c = mapView.getCenterPoint();
        return c != null ? c.get() : null;
    }

    private static double distance(GeoPoint ref, Marker m) {
        return distance(ref, m.getPoint());
    }

    /** WGS84 geodesic distance (core.Geo, tested against GeographicLib). */
    private static double distance(GeoPoint a, GeoPoint b) {
        if (a == null || b == null || !a.isValid() || !b.isValid())
            return Double.NaN;
        return Geo.distance(a.getLatitude(), a.getLongitude(),
                b.getLatitude(), b.getLongitude());
    }

    private GeoPoint selfPoint() {
        try {
            Marker self = ATAKUtilities.findSelf(mapView);
            GeoPoint gp = self != null ? self.getPoint() : null;
            if (gp != null && gp.isValid()
                    && !(gp.getLatitude() == 0 && gp.getLongitude() == 0))
                return gp;
        } catch (RuntimeException e) {
            Log.w(TAG, "no self position", e);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Status and delete

    /**
     * The incident if it is still on the map; otherwise tells the user,
     * refreshes the pane and returns null.
     */
    private Marker live(Marker m) {
        Marker l = m == null ? null : store.liveMarker(m.getUID());
        if (l == null) {
            toast("This incident is no longer on the map.");
            afterDelete();
        }
        return l;
    }

    private void confirmStatus(Marker card) {
        final Marker m = live(card);
        if (m == null)
            return;
        if (!BlastStore.isIncident(m))
            return;
        final boolean clearing = !isCleared(m);
        new AlertDialog.Builder(mapView.getContext())
                .setTitle(clearing ? "Mark incident cleared?"
                        : "Reopen incident?")
                .setMessage(clearing
                        ? "Records your callsign and the time. The rings "
                                + "turn grey and stay on the map until deleted."
                                + (BlastStore.isUxo(m) ? "\n\nOnly mark a UXO "
                                        + "incident cleared when authorities "
                                        + "say it has been removed." : "")
                                + (BlastStore.isNuc(m) ? "\n\nFor a nuclear "
                                        + "reference this marks it ENDED "
                                        + "(exercise over or no longer "
                                        + "needed)." : "")
                        : "Sets the incident back to active with the "
                                + "original distances.")
                .setPositiveButton(clearing ? "Clear and send to all"
                        : "Reopen and send to all",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                setStatus(m, clearing, true);
                            }
                        })
                .setNeutralButton("This device only",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                setStatus(m, clearing, false);
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setStatus(Marker card, boolean clearing, boolean send) {
        try {
            // Re-check and re-read now: the dialog may have been open while
            // the incident was deleted or updated by someone else.
            Marker m = live(card);
            if (m == null)
                return;
            NucRecord nr = BlastStore.readNuc(m);
            UxoRecord u = nr == null ? BlastStore.readUxo(m) : null;
            boolean ok;
            if (nr != null) {
                ok = store.writeNuc(m, clearing
                        ? nr.cleared(callsign(), System.currentTimeMillis())
                        : nr.reopened());
            } else if (u != null) {
                ok = store.writeUxo(m, clearing
                        ? u.cleared(callsign(), System.currentTimeMillis())
                        : u.reopened());
            } else {
                BlastRecord r = BlastStore.read(m);
                if (r == null)
                    return;
                ok = store.write(m, clearing
                        ? r.cleared(callsign(), System.currentTimeMillis())
                        : r.reopened());
            }
            if (!ok) {
                live(null);
                return;
            }
            if (send)
                sharing.sendToAll(m);
            cardSignature = null;
            listSignature = null;
            refresh();
        } catch (RuntimeException e) {
            Log.e(TAG, "status change failed", e);
            toast("Could not update the incident.");
        }
    }

    private void confirmDelete(Marker card) {
        final Marker m = live(card);
        if (m == null)
            return;
        final String name = ATAKUtilities.getDisplayName(m);
        new AlertDialog.Builder(mapView.getContext())
                .setTitle("Delete " + name + "?")
                .setMessage("Removes the marker and the rings of this "
                        + "incident.\n\nDelete for everyone also sends a "
                        + "remove request to every connected TAK client. "
                        + "Clients that are offline keep their copy.")
                .setPositiveButton("Delete for everyone",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                try {
                                    if (store.liveMarker(m.getUID()) != null)
                                        sharing.deleteForEveryone(m);
                                } catch (RuntimeException e) {
                                    Log.e(TAG, "delete failed", e);
                                }
                                afterDelete();
                            }
                        })
                .setNeutralButton("This device only",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                store.delete(m);
                                afterDelete();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptRename(Marker card) {
        final Marker m = live(card);
        if (m == null)
            return;
        final EditText input = new EditText(mapView.getContext());
        doneKey(input);
        input.setSingleLine(true);
        input.setText(Names.base(BlastStore.nameOf(m)));
        input.setSelectAllOnFocus(true);
        input.setFilters(new android.text.InputFilter[] {
                new android.text.InputFilter.LengthFilter(Names.MAX_LENGTH)
        });
        showTyping(new AlertDialog.Builder(mapView.getContext())
                .setTitle("Rename incident")
                .setMessage("The name is the marker's callsign, so every TAK "
                        + "client shows it. CLEARED is added automatically "
                        + "while the incident is cleared.")
                .setView(input)
                .setPositiveButton("Rename and send to all",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                doRename(m, input.getText().toString(), true);
                            }
                        })
                .setNeutralButton("This device only",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                doRename(m, input.getText().toString(), false);
                            }
                        })
                .setNegativeButton("Cancel", null)
                );
    }

    private void doRename(Marker card, String typed, boolean send) {
        try {
            Marker m = live(card);
            if (m == null)
                return;
            if (Names.sanitize(typed) == null) {
                toast("Enter a name.");
                return;
            }
            if (!store.rename(m, typed)) {
                toast("Could not rename the incident.");
                return;
            }
            if (send)
                sharing.sendToAll(m);
            cardSignature = null;
            listSignature = null;
            refresh();
        } catch (RuntimeException e) {
            Log.e(TAG, "rename failed", e);
            toast("Could not rename the incident.");
        }
    }

    private void promptExport(Marker card) {
        final Marker m = live(card);
        if (m == null)
            return;
        final Exporter.Format[] formats = Exporter.Format.values();
        final boolean[] checked = new boolean[formats.length];
        String[] labels = new String[formats.length];
        for (int i = 0; i < formats.length; i++) {
            labels[i] = formats[i].label;
            checked[i] = true;
        }
        new AlertDialog.Builder(mapView.getContext())
                .setTitle("Export " + BlastStore.nameOf(m))
                .setMultiChoiceItems(labels, checked,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int i,
                                    boolean on) {
                                checked[i] = on;
                            }
                        })
                .setPositiveButton("Save",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                doExport(m, formats, checked);
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doExport(Marker card, Exporter.Format[] formats,
            boolean[] checked) {
        Marker m = live(card);
        if (m == null)
            return;
        StringBuilder done = new StringBuilder();
        StringBuilder failed = new StringBuilder();
        for (int i = 0; i < formats.length; i++) {
            if (!checked[i])
                continue;
            try {
                java.io.File f = exporter.export(m, formats[i]);
                done.append("\n• ").append(f.getName());
            } catch (Exception e) {
                Log.e(TAG, "export failed: " + formats[i], e);
                failed.append("\n• ").append(formats[i].label);
            }
        }
        if (done.length() == 0 && failed.length() == 0)
            return;
        String msg = "";
        if (done.length() > 0)
            msg += "Saved in atak/export/Blast:" + done
                    + "\n\nImport a data package with ATAK's Import "
                    + "Manager or send it as a file; copy the KMZ to Google "
                    + "Earth and the GPX to a Garmin's GPX folder.";
        if (failed.length() > 0)
            msg += (msg.isEmpty() ? "" : "\n\n") + "Could not save:" + failed;
        new AlertDialog.Builder(mapView.getContext())
                .setTitle("Export")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    private void confirmDeleteCleared() {
        final List<Marker> cleared = new ArrayList<>();
        for (Marker m : store.all())
            if (isCleared(m))
                cleared.add(m);
        if (cleared.isEmpty())
            return;
        StringBuilder names = new StringBuilder();
        for (Marker m : cleared)
            names.append("\n• ").append(ATAKUtilities.getDisplayName(m));
        new AlertDialog.Builder(mapView.getContext())
                .setTitle("Delete " + cleared.size() + " cleared incident"
                        + (cleared.size() == 1 ? "" : "s") + "?")
                .setMessage("Removes each marker and its rings:" + names
                        + "\n\nActive incidents are not touched.")
                .setPositiveButton("Delete for everyone",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                for (Marker m : cleared) {
                                    try {
                                        if (store.liveMarker(m.getUID()) != null)
                                            sharing.deleteForEveryone(m);
                                    } catch (RuntimeException e) {
                                        Log.e(TAG, "delete failed", e);
                                    }
                                }
                                afterDelete();
                            }
                        })
                .setNeutralButton("This device only",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                for (Marker m : cleared)
                                    store.delete(m);
                                afterDelete();
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void afterDelete() {
        // Keep the selection if a different incident was deleted.
        if (selectedZone() == null)
            selectedZoneUid = null;
        cardSignature = null;
        listSignature = null;
        refresh();
    }

    // ------------------------------------------------------------------
    // Helpers

    private String callsign() {
        try {
            String cs = mapView.getDeviceCallsign();
            return cs == null ? "" : cs;
        } catch (RuntimeException e) {
            return "";
        }
    }

    private void toast(String s) {
        Toast.makeText(mapView.getContext(), s, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v,
                plugin.getResources().getDisplayMetrics()));
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(plugin);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(plugin);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private static LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(plugin);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold)
            t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView header(String s) {
        TextView t = text(s, 12, BLUE, true);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private Button button(String label, int color) {
        Button b = new Button(plugin);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setMinHeight(dp(44));
        tint(b, color);
        return b;
    }

    private void tint(Button b, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(4));
        g.setStroke(dp(1), 0xFF000000);
        b.setBackground(g);
    }
}
