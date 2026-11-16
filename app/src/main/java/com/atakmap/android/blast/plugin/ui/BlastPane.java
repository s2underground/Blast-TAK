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
import com.atakmap.android.blast.plugin.core.screen.Buttons;
import com.atakmap.android.blast.plugin.core.screen.Check;
import com.atakmap.android.blast.plugin.core.screen.Choice;
import com.atakmap.android.blast.plugin.core.screen.FormValues;
import com.atakmap.android.blast.plugin.core.screen.Input;
import com.atakmap.android.blast.plugin.core.screen.Ink;
import com.atakmap.android.blast.plugin.core.screen.Item;
import com.atakmap.android.blast.plugin.core.screen.KeyRow;
import com.atakmap.android.blast.plugin.core.screen.EntryRow;
import com.atakmap.android.blast.plugin.core.screen.Section;
import com.atakmap.android.blast.plugin.core.screen.IncidentList;
import com.atakmap.android.blast.plugin.core.screen.ListRow;
import com.atakmap.android.blast.plugin.core.screen.MainPane;
import com.atakmap.android.blast.plugin.core.screen.ReferenceTab;
import com.atakmap.android.blast.plugin.core.screen.TitleRow;
import com.atakmap.android.blast.plugin.core.port.PositionSource;
import com.atakmap.android.blast.plugin.core.port.Team;
import com.atakmap.android.blast.plugin.atak.AtakPorts;
import com.atakmap.android.blast.plugin.core.app.BlastApp;
import com.atakmap.android.blast.plugin.core.app.FormDialog;
import com.atakmap.android.blast.plugin.core.app.Outcome;
import com.atakmap.android.blast.plugin.core.screen.Dialog;
import com.atakmap.android.blast.plugin.core.screen.Legend;
import com.atakmap.android.blast.plugin.core.screen.Screen;
import com.atakmap.android.blast.plugin.core.screen.StandoffCard;
import com.atakmap.android.blast.plugin.core.screen.Text;
import com.atakmap.android.blast.plugin.core.screen.Tone;
import com.atakmap.android.blast.plugin.atak.Sharing;
import com.atakmap.android.blast.plugin.core.StandoffTable;
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
import com.atakmap.android.blast.plugin.core.NucText;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.ArrayList;
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
    private final SharedPreferences prefs;
    private final StandoffTable table = StandoffTable.current();
    private final Handler main = new Handler(Looper.getMainLooper());
    /** The app core: selection, card, list, new incident, forms. */
    private final BlastApp app;

    private final ScrollView root;
    private LinearLayout incidentsView, referenceView;
    /** Rendered core screens: pane header, Reference tab, incident list. */
    private Rendered headerView, refView, listView;

    /** The rendered new-incident section (the core holds its choices). */
    private Rendered newView;
    private LinearLayout zoneCard;
    /** Debug builds only: what the incident lookup found. */
    private TextView debugLine;

    /** The open card's rendered views (live slots, boxes) or null. */
    private Rendered cardView;

    /** Listening for a map tap (the core asked), or null. */
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
        PositionSource position = AtakPorts.position(mapView);
        Team team = AtakPorts.team(mapView);
        this.prefs = AtakPreferences.getInstance(mapView.getContext())
                .getSharedPrefs();
        this.app = new BlastApp(AtakPorts.incidents(store),
                AtakPorts.share(store, sharing), team, position,
                AtakPorts.clock(), AtakPorts.clipboard(mapView.getContext()),
                AtakPorts.files(), AtakPorts.settings(prefs),
                AtakPorts.map(mapView));
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
        app.invalidate(true, true);
        refresh();
    }

    /** Opened from the radial menu on a zone. */
    public void openZone(Marker zone) {
        app.target(null, null);
        app.select(zone.getUID());
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
        app.target(ATAKUtilities.getDisplayName(target),
                AtakPorts.pointOf(target));
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
                        tap(action, new FormValues());
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
                        tap(action, new FormValues());
                    }
                });

        zoneCard = vertical();
        col.addView(zoneCard);

        // The core decides what the new-incident steps say and offer.
        newView = render(app.newIncident(null), col, new ScreenAction() {
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
    // New incident: the core's section, rendered here

    /** Redraw the new-incident steps from the core (no capture). */
    private void renderNew() {
        if (newView != null)
            newView.update(app.newIncident(null));
    }

    /** Redraw after an outside change, keeping what is typed. */
    private void updateNew() {
        if (newView != null)
            newView.update(app.newIncident(newView.values()));
    }

    /** A new-incident button, box or check: the core decides. */
    private void newAction(String action) {
        Outcome o = app.tapNew(action, newView.values());
        if (o == null)
            Log.w(TAG, "unknown action " + action);
        else
            handle(o);
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
    // Form fields

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

    private String emergencyNumber() {
        return app.emergencyNumber();
    }

    // ------------------------------------------------------------------
    // Reference tab

    private LinearLayout buildReference() {
        LinearLayout col = vertical();
        refView = render(ReferenceTab.screen(emergencyNumber()), col,
                new ScreenAction() {
                    @Override
                    public void run(String action) {
                        tap(action, new FormValues());
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
        stopListening();
        app.resetNew();
        renderNew();
    }

    // ------------------------------------------------------------------
    // Map tap: the core asks for one; this listens and hands the point back

    private void startListening(String prompt) {
        stopListening();
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
        TextContainer.getInstance().displayPrompt(prompt);
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
        boolean onMarker = MapEvent.ITEM_CLICK.equals(event.getType());
        if (onMarker && item instanceof PointMapItem) {
            gp = ((PointMapItem) item).getPoint();
        } else {
            PointF p = event.getPointF();
            if (p != null) {
                GeoPointMetaData m = mapView.inverseWithElevation(p.x, p.y);
                if (m != null)
                    gp = m.get();
            }
        }
        stopListening();
        handle(app.picked(gp == null || !gp.isValid() ? null
                : AtakPorts.fixOf(gp, Double.NaN), onMarker,
                newView.values()));
    }

    /** Stop listening for a map tap; false if not listening. */
    private boolean stopListening() {
        if (pickListener == null)
            return false;
        MapEventDispatcher d = mapView.getMapEventDispatcher();
        d.removeMapEventListener(MapEvent.MAP_CLICK, pickListener);
        d.removeMapEventListener(MapEvent.ITEM_CLICK, pickListener);
        d.removeMapEventListener(MapEvent.MAP_LONG_PRESS, pickListener);
        pickListener = null;
        d.popListeners();
        TextContainer.getInstance().closePrompt();
        return true;
    }

    /** Cancel a map tap from here (tab change, pane closed). */
    private void stopPick() {
        if (!stopListening())
            return;
        app.pickCancelled();
        updateNew();
    }

    // ------------------------------------------------------------------
    // Refresh

    private void refresh() {
        try {
            refreshCardAndList();
        } catch (RuntimeException e) {
            Log.e(TAG, "refresh failed", e);
        }
    }

    /** Draw what the core says the card and list look like now. */
    private void refreshCardAndList() {
        if (debugLine != null)
            debugLine.setText("Debug: incidents " + store.lastScan());
        BlastApp.Frame f = app.frame();
        if (f.cardChanged) {
            zoneCard.removeAllViews();
            cardView = null;
            if (f.card == null) {
                zoneCard.setVisibility(View.GONE);
            } else {
                zoneCard.setVisibility(View.VISIBLE);
                ScreenAction act = new ScreenAction() {
                    @Override
                    public void run(String action) {
                        tap(action, cardView == null ? new FormValues()
                                : cardView.values());
                    }
                };
                render(f.header, zoneCard, act);
                LinearLayout card = vertical();
                card.setBackgroundColor(CARD);
                int p = dp(8);
                card.setPadding(p, p, p, p);
                zoneCard.addView(card);
                // The core decides what the card says; this only renders it.
                cardView = render(f.card, card, act);
            }
        }
        if (cardView != null) {
            apply(cardView.slots.get(StandoffCard.SLOT_YOU), f.you);
            apply(cardView.slots.get(StandoffCard.SLOT_TEAM), f.team);
        }
        if (f.list != null)
            listView.update(f.list);
    }

    /** A button on the header, the list, the open card or the Reference. */
    private void tap(String action, FormValues v) {
        Outcome o = app.tap(action, v);
        if (o == null)
            Log.w(TAG, "unknown action " + action);
        else
            handle(o);
    }

    /** Carry out what the core asked for after an action. */
    private void handle(Outcome o) {
        if (o.toast != null)
            toast(o.toast);
        if (o.dialog != null)
            show(o.dialog);
        if (o.restyle != null && cardView != null)
            cardView.update(o.restyle);
        if (o.labels)
            store.relabelAll();
        if (o.settings && refView != null)
            refView.update(new Screen().add(ReferenceTab.emergency(
                    emergencyNumber())));
        if (o.flyTo != null)
            frame(o.flyTo);
        if (o.gone) {
            afterDelete();
        } else if (o.card || o.list) {
            app.invalidate(o.card, o.list);
            refresh();
        }
        if (o.reference != null)
            showTab(o.reference, o.section);
        if (o.stopPick)
            stopListening();
        if (o.pick != null)
            startListening(o.pick);
        if (o.newIncident)
            renderNew();
        if (o.focus != null) {
            EditText e = newView.entries.get(o.focus);
            if (e != null)
                e.requestFocus();
        }
        if (o.top)
            main.post(new Runnable() {
                @Override
                public void run() {
                    root.smoothScrollTo(0, 0);
                }
            });
        if (o.form != null)
            show(o.form);
        if (o.next != null)
            handle(o.next);
    }

    /** Show a core dialog; its buttons go back to the core. */
    private void show(final Dialog d) {
        Context ctx = mapView.getContext();
        AlertDialog.Builder b = new AlertDialog.Builder(ctx)
                .setTitle(d.title);
        if (d.message != null)
            b.setMessage(d.message);
        EditText field = null;
        if (d.hasField()) {
            field = new EditText(ctx);
            doneKey(field);
            field.setSingleLine(true);
            field.setText(d.fieldValue);
            if (d.fieldKind == Dialog.FieldKind.PHONE)
                field.setInputType(InputType.TYPE_CLASS_PHONE);
            if (d.fieldSelectAll)
                field.setSelectAllOnFocus(true);
            field.setFilters(new android.text.InputFilter[] {
                    new android.text.InputFilter.LengthFilter(
                            d.fieldMaxLength) });
            b.setView(field);
        }
        final boolean[] ticks = new boolean[d.options == null ? 0
                : d.options.size()];
        if (d.options != null) {
            for (int i = 0; i < ticks.length; i++)
                ticks[i] = d.checked(i);
            b.setMultiChoiceItems(d.options.toArray(new String[0]), ticks,
                    new DialogInterface.OnMultiChoiceClickListener() {
                        @Override
                        public void onClick(DialogInterface di, int i,
                                boolean on) {
                            ticks[i] = on;
                        }
                    });
        }
        if (d.items != null)
            b.setItems(d.items.toArray(new String[0]),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface di, int w) {
                            handle(app.choose(d, d.itemAction,
                                    new FormValues().put(Dialog.ITEM,
                                            Integer.toString(w))));
                        }
                    });
        final EditText input = field;
        if (d.positive != null)
            b.setPositiveButton(d.positive, choice(d, d.positiveAction, input,
                    ticks));
        if (d.neutral != null)
            b.setNeutralButton(d.neutral, choice(d, d.neutralAction, input,
                    ticks));
        if (d.negative != null)
            b.setNegativeButton(d.negative, null);
        if (input != null)
            showTyping(b);
        else
            b.show();
    }

    private DialogInterface.OnClickListener choice(final Dialog d,
            final String action, final EditText input, final boolean[] ticks) {
        if (action == null)
            return null;
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface di, int w) {
                FormValues v = new FormValues();
                if (input != null)
                    v.put(Dialog.FIELD, input.getText().toString());
                for (int i = 0; i < ticks.length; i++)
                    v.put(Dialog.option(i), ticks[i]);
                handle(app.choose(d, action, v));
            }
        };
    }

    /** Show a core form; its live lines and buttons go back to the core. */
    private void show(final FormDialog f) {
        Context ctx = mapView.getContext();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(f.padDp), dp(f.padTopDp), dp(f.padDp), 0);
        View content = box;
        if (f.scrolls) {
            ScrollView sv = new ScrollView(ctx);
            sv.addView(box);
            content = sv;
        }
        final FormView[] fv = new FormView[1];
        final Runnable live = new Runnable() {
            @Override
            public void run() {
                if (fv[0] == null)
                    return;
                for (java.util.Map.Entry<String, String> e : f.live(
                        fv[0].values()).entrySet())
                    fv[0].set(e.getKey(), e.getValue());
            }
        };
        fv[0] = renderForm(f.form, ctx, box, live);
        live.run();
        AlertDialog.Builder b = new AlertDialog.Builder(ctx)
                .setTitle(f.title)
                .setView(content);
        if (f.positive != null)
            b.setPositiveButton(f.positive, press(f, FormDialog.POSITIVE,
                    fv));
        if (f.neutral != null)
            b.setNeutralButton(f.neutral, press(f, FormDialog.NEUTRAL, fv));
        if (f.negative != null)
            b.setNegativeButton(f.negative, null);
        showTyping(b);
    }

    private DialogInterface.OnClickListener press(final FormDialog f,
            final String button, final FormView[] fv) {
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface di, int w) {
                handle(f.press(button, fv[0].values()));
            }
        };
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

    /** Frame the map on an incident's outer ring (or its marker). */
    private void frame(String uid) {
        Marker m = store.liveMarker(uid);
        if (m == null)
            return;
        MapItem pref = store.liveItem(uid + BlastStore.PREF_SUFFIX);
        if (pref == null)
            pref = store.liveItem(uid + BlastStore.CORDON_SUFFIX);
        if (pref == null)
            pref = store.liveItem(uid + NucText.Ring.LDZ.suffix);
        MapTouchController.goTo(pref != null ? pref : m, false);
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

    // ------------------------------------------------------------------
    // Status and delete

    private void afterDelete() {
        // Keep the selection if a different incident was deleted.
        app.afterDelete();
        refresh();
    }

    // ------------------------------------------------------------------
    // Helpers

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
