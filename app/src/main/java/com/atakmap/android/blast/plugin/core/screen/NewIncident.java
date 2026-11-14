package com.atakmap.android.blast.plugin.core.screen;

import com.atakmap.android.blast.plugin.core.BlastRecord;
import com.atakmap.android.blast.plugin.core.Formats;
import com.atakmap.android.blast.plugin.core.NucModel;
import com.atakmap.android.blast.plugin.core.NucPresets;
import com.atakmap.android.blast.plugin.core.NucRecord;
import com.atakmap.android.blast.plugin.core.NucText;
import com.atakmap.android.blast.plugin.core.StandoffRow;
import com.atakmap.android.blast.plugin.core.StandoffTable;
import com.atakmap.android.blast.plugin.core.UxoRecord;
import com.atakmap.android.blast.plugin.core.UxoTable;
import com.atakmap.android.blast.plugin.core.UxoText;
import com.atakmap.android.blast.plugin.core.ZoneCalculator;
import com.atakmap.android.blast.plugin.core.ZoneCalculator.MassUnit;

import java.util.List;
import java.util.Locale;

/**
 * The "NEW INCIDENT" part of the pane: the three threat kinds and, for the
 * chosen one, its steps (what it is, details, place it). It holds the
 * user's choices; hosts render {@link #screen}, pass taps to {@link #tap},
 * do what the {@link Result} asks (place, pick on the map, open a list),
 * and build the record with {@link #standoffRecord}, {@link #uxoRecord} or
 * {@link #nucRecord}.
 */
public final class NewIncident {

    public enum Mode {
        NONE, IED, UXO, NUC
    }

    // Kind of threat
    public static final String MODE_IED = "mode_ied", MODE_UXO = "mode_uxo",
            MODE_NUC = "mode_nuc";

    // Bomb threat (CISA/FBI card)
    /** {@code ROW + row id}. */
    public static final String ROW = "row_";
    public static final String CUSTOM = "custom", QTY = "qty",
            QTY_CHANGED = "qty_changed", LB = "unit_lb", KG = "unit_kg";
    public static final String AUTO_SEND = "auto_send";

    // Military ordnance (ATP 4-32.2)
    /** {@code UXO_CAT + category id}. */
    public static final String UXO_CAT = "uxo_cat_";
    public static final String UXO_LARGE = "uxo_large",
            UXO_MULTI = "uxo_multi";
    /** {@code UXO_WHERE + Where name}. */
    public static final String UXO_WHERE = "uxo_where_";
    public static final String UXO_RB = "uxo_rb";

    // Nuclear reference
    public static final String YIELD = "yield",
            YIELD_CHANGED = "yield_changed", KT = "nuc_kt", MT = "nuc_mt";
    /** {@code PRESETS + Group name}. */
    public static final String PRESETS = "nuc_presets_";
    /** {@code BURST + Burst name}. */
    public static final String BURST = "nuc_burst_";
    public static final String NUC_POLAR = "nuc_polar";

    /** Placement buttons: prefix "ied_", "uxo_" or "nuc_" + one of these. */
    public static final String TARGET = "target", PICK = "pick",
            SELF = "self", CENTER = "center";

    public static final String SLOT_IED = "ied_sel", SLOT_UXO = "uxo_sel",
            SLOT_NUC = "nuc_sel";

    // Messages
    public static final String NO_GPS = "No GPS position yet.",
            PLACED = "Incident placed. Use SEND TO ALL to share it.",
            PLACED_SENT = "Incident placed and sent to everyone.",
            UXO_PLACED = "UXO incident placed (not sent). Use REPORT or SEND "
                    + "on its card when you are outside the cordon.",
            NUC_PLACED = "Nuclear reference placed (not sent). Use SEND on "
                    + "its card to share it.",
            FIELDED_TITLE = "Fielded weapons: choose a country";

    static final int MAX_YIELD_CHARS = 8;
    private static final NucPresets.Group[] PRESET_GROUPS = {
            NucPresets.Group.PLANNING, NucPresets.Group.FIELDED,
            NucPresets.Group.HISTORICAL, NucPresets.Group.YIELD };
    private static final NucModel.Burst[] BURSTS = { NucModel.Burst.UNKNOWN,
            NucModel.Burst.SURFACE, NucModel.Burst.AIR };
    private static final UxoRecord.Where[] WHERES = { UxoRecord.Where.PATH,
            UxoRecord.Where.AREA, UxoRecord.Where.VEHICLE };

    private final StandoffTable table;
    private Mode mode = Mode.NONE;

    private String rowId;
    private boolean custom;
    private MassUnit unit = MassUnit.LB;
    private String qty = "";

    private String uxoCat;
    private boolean uxoLarge, uxoMulti;
    private UxoRecord.Where uxoWhere = UxoRecord.Where.UNSET;

    private String yield = "";
    private boolean megatons;
    private String presetId;
    private NucModel.Burst burst = NucModel.Burst.UNKNOWN;
    private final WindPicker wind = new WindPicker(Double.NaN, Double.NaN);

    public NewIncident(StandoffTable table) {
        this.table = table;
    }

    /**
     * Forget every choice (the pane opens with none, and each placed
     * incident needs a fresh one). The unit choice (lb / kg) is kept.
     */
    public void reset() {
        mode = Mode.NONE;
        rowId = null;
        custom = false;
        qty = "";
        uxoCat = null;
        uxoLarge = false;
        uxoMulti = false;
        uxoWhere = UxoRecord.Where.UNSET;
        yield = "";
        megatons = false;
        presetId = null;
        burst = NucModel.Burst.UNKNOWN;
        wind.set(Double.NaN, Double.NaN);
    }

    public Mode mode() {
        return mode;
    }

    // ------------------------------------------------------------------
    // Taps

    /** What the host should do after a tap. */
    public enum Request {
        /** Only redraw. */
        NONE,
        /** The threat kind changed: stop any map pick, then redraw. */
        MODE_CHANGED,
        /** Place at the marker the pane was opened on. */
        PLACE_TARGET,
        /** Start (or cancel) a map tap. */
        PICK,
        PLACE_SELF, PLACE_CENTER,
        /** Ask for range and bearing from the user's position (UXO). */
        RANGE_BEARING,
        /** Ask what the observer saw (nuclear polar plot). */
        POLAR,
        /** Offer the presets of {@link Result#group}. */
        PRESETS,
        /** Put the cursor in the box {@link Result#focus}. */
        FOCUS,
        /** Store the auto-send check (host preference). */
        AUTO_SEND
    }

    public static final class Result {
        public final Request request;
        /** The section the request is for (placement requests). */
        public final Mode mode;
        public final NucPresets.Group group;
        public final String focus;
        /** A message for the user, or null. */
        public final String toast;

        Result(Request request, Mode mode, NucPresets.Group group,
                String focus, String toast) {
            this.request = request;
            this.mode = mode;
            this.group = group;
            this.focus = focus;
            this.toast = toast;
        }
    }

    private static Result result(Request r) {
        return new Result(r, Mode.NONE, null, null, null);
    }

    /** Remember what is typed in the boxes (those {@code v} has). */
    public void capture(FormValues v) {
        if (v.has(QTY))
            qty = v.get(QTY);
        if (v.has(YIELD))
            yield = v.get(YIELD);
        wind.capture(v);
    }

    /**
     * Run a tap or edit. {@code v} holds the boxes and checks. Returns null
     * when {@code action} isn't one of this screen's.
     */
    public Result tap(String action, FormValues v) {
        if (action == null)
            return null;
        capture(v);
        WindPicker.Tap w = wind.tap(action, v);
        if (w != null)
            return new Result(Request.NONE, Mode.NUC, null, null, w.error);
        Result place = placement(action);
        if (place != null)
            return place;
        if (action.startsWith(ROW)) {
            String id = action.substring(ROW.length());
            for (StandoffRow r : table.rows())
                if (r.id.equals(id)) {
                    custom = false;
                    rowId = id;
                    return result(Request.NONE);
                }
            return null;
        }
        if (action.startsWith(UXO_CAT)) {
            UxoTable.Category c = UxoTable.category(action.substring(
                    UXO_CAT.length()));
            if (c == null)
                return null;
            uxoCat = c.id;
            if (!c.isUnknown())
                uxoLarge = false;
            return result(Request.NONE);
        }
        if (action.startsWith(UXO_WHERE)) {
            uxoWhere = UxoRecord.Where.parse(action.substring(
                    UXO_WHERE.length()));
            return result(Request.NONE);
        }
        if (action.startsWith(PRESETS)) {
            for (NucPresets.Group g : PRESET_GROUPS)
                if (action.equals(PRESETS + g.name()))
                    return new Result(Request.PRESETS, Mode.NUC, g, null,
                            null);
            return null;
        }
        if (action.startsWith(BURST)) {
            for (NucModel.Burst b : BURSTS)
                if (action.equals(BURST + b.name())) {
                    burst = b;
                    return result(Request.NONE);
                }
            return null;
        }
        switch (action) {
            case MODE_IED:
                return mode(Mode.IED);
            case MODE_UXO:
                return mode(Mode.UXO);
            case MODE_NUC:
                return mode(Mode.NUC);
            case CUSTOM:
                custom = true;
                rowId = null;
                return new Result(Request.FOCUS, Mode.IED, null, QTY, null);
            case LB:
                unit = MassUnit.LB;
                return result(Request.NONE);
            case KG:
                unit = MassUnit.KG;
                return result(Request.NONE);
            case QTY_CHANGED:
                return result(Request.NONE);
            case AUTO_SEND:
                return result(Request.AUTO_SEND);
            case UXO_LARGE:
                uxoLarge = !uxoLarge;
                return result(Request.NONE);
            case UXO_MULTI:
                uxoMulti = v.checked(UXO_MULTI);
                return result(Request.NONE);
            case YIELD_CHANGED:
                presetId = null;
                return result(Request.NONE);
            case KT:
            case MT:
                megatons = MT.equals(action);
                presetId = null;
                return result(Request.NONE);
            default:
                return null;
        }
    }

    private Result mode(Mode m) {
        mode = m;
        return result(Request.MODE_CHANGED);
    }

    private static Result placement(String action) {
        Mode m = action.startsWith("ied_") ? Mode.IED
                : action.startsWith("uxo_") ? Mode.UXO
                        : action.startsWith("nuc_") ? Mode.NUC : null;
        if (m == null)
            return null;
        String what = action.substring(4);
        Request r = TARGET.equals(what) ? Request.PLACE_TARGET
                : PICK.equals(what) ? Request.PICK
                        : SELF.equals(what) ? Request.PLACE_SELF
                                : CENTER.equals(what) ? Request.PLACE_CENTER
                                        : null;
        if (r == null && UXO_RB.equals(action))
            r = Request.RANGE_BEARING;
        if (r == null && NUC_POLAR.equals(action))
            r = Request.POLAR;
        return r == null ? null : new Result(r, m, null, null, null);
    }

    /** Set the yield from a preset (as if typed) and remember the preset. */
    public void choosePreset(NucPresets.Preset p) {
        megatons = p.kt >= 1000;
        yield = Formats.decimalTrim(megatons ? p.kt / 1000.0 : p.kt);
        presetId = p.id;
    }

    // ------------------------------------------------------------------
    // Readiness and records

    /** The bomb-threat selection, or null. */
    ZoneCalculator.Selection selection() {
        if (custom) {
            double q = ZoneCalculator.parseQuantity(qty);
            return Double.isNaN(q) ? null
                    : ZoneCalculator.forQuantity(table, q, unit);
        }
        return rowId == null ? null : ZoneCalculator.forRow(table, rowId);
    }

    /** The typed yield in kt, or NaN. */
    double yieldKt() {
        double v = ZoneCalculator.parseQuantity(yield);
        return Double.isNaN(v) ? Double.NaN : megatons ? v * 1000 : v;
    }

    public boolean ready(Mode m) {
        switch (m) {
            case IED: {
                ZoneCalculator.Selection s = selection();
                return s != null && s.isUsable();
            }
            case UXO:
                return uxoCat != null && uxoWhere != UxoRecord.Where.UNSET;
            case NUC:
                return NucModel.validYield(yieldKt());
            default:
                return false;
        }
    }

    /** Why {@code m} can't be placed yet (for a toast). */
    public static String notReady(Mode m) {
        switch (m) {
            case UXO:
                return "Choose what you see and where you are first.";
            case NUC:
                return "Enter a yield from 0.1 kt to 50 Mt first.";
            default:
                return "Choose a threat first.";
        }
    }

    /** What the map shows while waiting for a tap. */
    public static String pickPrompt(Mode m) {
        return m == Mode.UXO
                ? "Tap the map where the item is (don't go closer). "
                        + "Press Cancel to stop."
                : m == Mode.NUC
                        ? "Tap the map at ground zero. Press Cancel to stop."
                        : "Tap the map or a marker where the device is. "
                                + "Press Cancel to stop.";
    }

    /** The bomb-threat record; call only when {@link #ready} (IED). */
    public BlastRecord standoffRecord(String byCallsign, long timeMillis) {
        return BlastRecord.create(selection(), byCallsign, timeMillis);
    }

    /** The UXO record; call only when {@link #ready} (UXO). */
    public UxoRecord uxoRecord(UxoRecord.Placement how, String byCallsign,
            long timeMillis) {
        return UxoRecord.create(uxoCat, uxoMulti,
                UxoTable.UNKNOWN.equals(uxoCat) && uxoLarge, uxoWhere, how,
                byCallsign, timeMillis);
    }

    /**
     * The nuclear record; call only when {@link #ready} (NUC).
     *
     * @param obs the observer's report for a polar plot, or null
     */
    public NucRecord nucRecord(NucRecord.Obs obs, String byCallsign,
            long timeMillis) {
        NucRecord r = NucRecord.create(yieldKt(), burst, presetId,
                wind.fromDeg(), wind.mph(), byCallsign, timeMillis);
        return obs == null ? r : r.withObs(obs);
    }

    // ------------------------------------------------------------------
    // Preset lists

    /** The country list for fielded weapons, with each one's count. */
    public static String[] regionItems() {
        String[] regions = NucPresets.REGIONS;
        String[] items = new String[regions.length];
        for (int i = 0; i < regions.length; i++)
            items[i] = regions[i] + " ("
                    + NucPresets.fielded(regions[i]).size() + ")";
        return items;
    }

    public static String regionTitle(String region) {
        return region + ": public estimates, upper value of ranges";
    }

    /** Title of a (non-fielded) group's list. */
    public static String groupTitle(NucPresets.Group g) {
        return g == NucPresets.Group.HISTORICAL
                ? "Tests and historical weapons: published estimates"
                : g.label;
    }

    /** List lines; By-yield presets show their label only. */
    public static String[] presetItems(List<NucPresets.Preset> ps,
            boolean labelOnly) {
        String[] items = new String[ps.size()];
        for (int i = 0; i < items.length; i++) {
            NucPresets.Preset p = ps.get(i);
            items[i] = labelOnly ? p.label : p.label + " — " + p.note;
        }
        return items;
    }

    // ------------------------------------------------------------------
    // Screen

    /**
     * The new-incident part of the pane as it looks now.
     *
     * @param picking the section whose map tap is running, or NONE
     * @param targetName the marker the pane was opened on (offered as
     *        "Place at …"), or null
     * @param autoSend the host's "send new incidents right away" setting
     */
    public Screen screen(Mode picking, String targetName, boolean autoSend) {
        Screen s = new Screen();
        s.add(Text.step("NEW INCIDENT: WHAT KIND OF THREAT?"));
        s.add(new Buttons(Button.pick(MODE_IED,
                "SUSPICIOUS DEVICE\nbomb threat / IED\n(CISA/FBI card)",
                mode == Mode.IED, Button.Kind.SELECTED_STANDOFF),
                Button.pick(MODE_UXO, "MILITARY ORDNANCE (UXO)\nshells, "
                        + "bombs, mines,\nrockets, grenades",
                        mode == Mode.UXO, Button.Kind.SELECTED_UXO)));
        s.add(new Buttons(Button.pick(MODE_NUC, "NUCLEAR DETONATION",
                mode == Mode.NUC, Button.Kind.SELECTED)));
        s.add(Text.of("Not sure? Choose MILITARY ORDNANCE if it looks "
                + "factory-made, otherwise SUSPICIOUS DEVICE.", 12, Tone.DIM,
                false));
        s.add(new Section("uxo", uxo(picking, targetName))
                .shown(mode == Mode.UXO));
        s.add(new Section("nuc", nuc(picking, targetName))
                .shown(mode == Mode.NUC));
        s.add(new Section("ied", ied(picking, targetName, autoSend))
                .shown(mode == Mode.IED));
        return s;
    }

    /** "Place at …", then the row of other ways to place. */
    private void placeButtons(Screen s, String prefix, boolean ok,
            Mode picking, Mode section, String targetName, Button... more) {
        s.add(new Buttons(new Button(prefix + TARGET, targetName == null ? ""
                : "Place at " + targetName, Button.Kind.PRIMARY, 1f, false)
                        .shown(targetName != null).enabled(ok)));
        Button[] row = new Button[more.length + 1];
        row[0] = Button.of(prefix + PICK, picking == section ? "Cancel"
                : "Tap map").enabled(ok);
        for (int i = 0; i < more.length; i++)
            row[i + 1] = more[i].enabled(ok);
        s.add(new Buttons(row));
    }

    private Screen ied(Mode picking, String targetName, boolean autoSend) {
        Screen s = new Screen();
        s.add(Text.step("1. THREAT"));
        List<StandoffRow> rows = table.rows();
        for (int i = 0; i < rows.size(); i += 2) {
            int n = Math.min(i + 2, rows.size()) - i;
            Button[] line = new Button[n];
            for (int j = 0; j < n; j++) {
                StandoffRow r = rows.get(i + j);
                line[j] = Button.pick(ROW + r.id, r.label + "\n"
                        + Formats.thousands(r.capacityLb) + " lb",
                        !custom && r.id.equals(rowId),
                        Button.Kind.SELECTED_STANDOFF).withIcon(r.id);
            }
            s.add(new Buttons(line));
        }
        s.add(new Buttons(Button.pick(CUSTOM, "Custom quantity (lb or kg)",
                custom, Button.Kind.SELECTED_STANDOFF)));
        Screen q = new Screen();
        q.add(new EntryRow(QTY, "Estimate (TNT equivalent)",
                ZoneCalculator.MAX_QUANTITY_CHARS, false, 2f,
                Button.pick(LB, "lb", unit == MassUnit.LB,
                        Button.Kind.SELECTED_STANDOFF),
                Button.pick(KG, "kg", unit == MassUnit.KG,
                        Button.Kind.SELECTED_STANDOFF)).withValue(qty)
                                .onChange(QTY_CHANGED).topAligned());
        s.add(new Section("qty_row", q).shown(custom));
        s.add(iedText());

        s.add(Text.step("2. PLACE"));
        placeButtons(s, "ied_", ready(Mode.IED), picking, Mode.IED,
                targetName, Button.of("ied_" + SELF, "My position"),
                Button.of("ied_" + CENTER, "Map center"));
        s.add(new Check(AUTO_SEND, "Send new incidents to everyone right away",
                autoSend));
        return s;
    }

    private Text iedText() {
        Text base = Text.of("", 13, Tone.TEXT, false).padded(4, 4)
                .inSlot(SLOT_IED);
        ZoneCalculator.Selection sel = selection();
        if (sel == null)
            return base.with(custom
                    ? "Enter your estimate. The card row at or above it is used."
                    : "Choose the threat that best matches what you see.",
                    Tone.DIM);
        if (!sel.isUsable())
            return base.with(sel.explain(), Tone.RED);
        StandoffRow r = sel.row;
        return base.with(sel.explain() + "\nMandatory evac "
                + Formats.ft(r.mandatoryFt) + " ("
                + Formats.thousands(r.mandatoryMetres()) + " m)"
                + " · Shelter-in-place to " + Formats.ft(r.shelterToFt)
                + " · Preferred " + Formats.ft(r.preferredFt) + "+ ("
                + Formats.thousands(r.preferredMetres()) + " m)", Tone.TEXT);
    }

    private Screen uxo(Mode picking, String targetName) {
        Screen s = new Screen();
        s.add(Text.step("1. WHAT DO YOU SEE?   (do not go closer)"));
        for (UxoTable.Category c : UxoTable.pickerCategories())
            s.add(new Buttons(Button.pick(UXO_CAT + c.id, c.plainLabel + "   "
                    + Formats.thousands(c.cordonM) + " m", c.id.equals(uxoCat),
                    Button.Kind.SELECTED_UXO).alignedStart()).spacedBelow(2));
        boolean unknown = UxoTable.UNKNOWN.equals(uxoCat);
        s.add(new Buttons(Button.pick(UXO_LARGE, "It looks large (bomb or "
                + "missile size): " + Formats.thousands(UxoTable.UNKNOWN_LARGE_M)
                + " m", uxoLarge, Button.Kind.SELECTED_UXO).shown(unknown)));
        s.add(new Check(UXO_MULTI, "More than one item (adds "
                + UxoTable.MULTI_ADD_M + " m to the cordon)", uxoMulti));

        s.add(Text.step("2. WHERE ARE YOU?"));
        String[] labels = { "On a road or path I know is safe",
                "I may be in a minefield or an area with bomblets",
                "In a vehicle" };
        for (int i = 0; i < WHERES.length; i++)
            s.add(new Buttons(Button.pick(UXO_WHERE + WHERES[i].name(),
                    labels[i], uxoWhere == WHERES[i], Button.Kind.SELECTED_UXO)
                    .alignedStart()).spacedBelow(2));

        s.add(uxoText(unknown));

        s.add(Text.step("3. PLACE IT (never walk closer to get a fix)"));
        placeButtons(s, "uxo_", ready(Mode.UXO), picking, Mode.UXO,
                targetName, Button.of(UXO_RB, "Range & bearing"),
                Button.of("uxo_" + CENTER, "Map center"));
        s.add(Text.of("UXO incidents are never sent automatically: share "
                + "them from the incident card when you choose.", 12,
                Tone.DIM, false));
        return s;
    }

    private Text uxoText(boolean unknown) {
        Text base = Text.of("", 13, Tone.TEXT, false).padded(4, 4)
                .inSlot(SLOT_UXO);
        if (uxoCat == null)
            return base.with("Choose what you see, then where you are.",
                    Tone.DIM);
        int m = UxoTable.cordonM(uxoCat, uxoMulti, unknown && uxoLarge);
        String s = "Cordon " + UxoText.cordon(m) + ", minimum. "
                + "Fragments may travel farther.";
        boolean unset = uxoWhere == UxoRecord.Where.UNSET;
        s += unset ? "\nNow choose where you are."
                : "\n" + UxoText.drill(uxoWhere);
        return base.with(s, unset ? Tone.AMBER : Tone.TEXT);
    }

    private Screen nuc(Mode picking, String targetName) {
        Screen s = new Screen();
        s.add(Text.of(NucText.BANNER + " For planning and training: shows "
                + "FEMA's protective actions and reference distances for a "
                + "detonation of the size you choose.", 12, Tone.AMBER, false)
                .padded(4, 4));
        s.add(Text.step("1. YIELD"));
        s.add(new EntryRow(YIELD, "Yield", MAX_YIELD_CHARS, false, 2f,
                Button.pick(KT, "kt", !megatons, Button.Kind.SELECTED),
                Button.pick(MT, "Mt", megatons, Button.Kind.SELECTED))
                        .withValue(yield).onChange(YIELD_CHANGED));
        Button[] groups = new Button[PRESET_GROUPS.length];
        String[] gl = { "FEMA\nscenarios", "Fielded\nweapons",
                "Tests &\nhistory", "By\nyield" };
        for (int i = 0; i < groups.length; i++)
            groups[i] = Button.of(PRESETS + PRESET_GROUPS[i].name(), gl[i]);
        s.add(new Buttons(groups).asTiles());

        s.add(Text.step("2. BURST"));
        for (NucModel.Burst b : BURSTS)
            s.add(new Buttons(Button.pick(BURST + b.name(),
                    b == NucModel.Burst.UNKNOWN
                            ? "Unknown: draw the worst case of both "
                                    + "(recommended)"
                            : b == NucModel.Burst.SURFACE
                                    ? "Surface / ground level (most fallout)"
                                    : "Air burst (outer envelope, little "
                                            + "fallout)",
                    burst == b, Button.Kind.SELECTED).alignedStart())
                            .spacedBelow(2));

        s.add(Text.step("3. WIND (optional, for fallout)"));
        wind.addTo(s);

        Screen preview = new Screen();
        s.add(nucText(preview));
        s.add(new Section("nuc_preview", preview).rebuilt());

        s.add(Text.step("4. PLACE GROUND ZERO"));
        placeButtons(s, "nuc_", ready(Mode.NUC), picking, Mode.NUC,
                targetName, Button.of("nuc_" + CENTER, "Map center"),
                Button.of(NUC_POLAR, "From what I saw"));
        s.add(Text.of("Nuclear reference incidents are never sent "
                + "automatically: share them from the card when you choose.",
                12, Tone.DIM, false));
        return s;
    }

    /** The yield line; fills {@code preview} with the rings it will draw. */
    private Text nucText(Screen preview) {
        Text base = Text.of("", 13, Tone.TEXT, false).padded(4, 4)
                .inSlot(SLOT_NUC);
        double kt = yieldKt();
        if (Double.isNaN(kt))
            return base.with("Type a yield or choose a preset.", Tone.DIM);
        if (!NucModel.validYield(kt))
            return base.with("Enter 0.1 kt to 50 Mt.", Tone.RED);
        NucRecord.Radii r = NucRecord.Radii.of(kt, burst);
        NucPresets.Preset p = NucPresets.byId(presetId);
        String s = (p != null ? p.label + " (" + p.note + "): " : "")
                + NucPresets.yieldLabel(kt) + ", "
                + burst.label.toLowerCase(Locale.US) + ". Will draw:";
        double[] rs = { r.sdzM, r.mdzM, r.ldzM, r.burnM, r.radM };
        NucText.Ring[] rings = { NucText.Ring.SDZ, NucText.Ring.MDZ,
                NucText.Ring.LDZ, NucText.Ring.BURN, NucText.Ring.RAD };
        for (int k = 0; k < rings.length; k++) {
            Ink ink = Ink.argb(NucCard.ringInk(rings[k]));
            preview.add(new KeyRow(KeyRow.Kind.TALL, ink, rings[k].label + ": "
                    + NucText.dist(rs[k]), ink, null));
        }
        if (NucModel.extrapolated(kt))
            s += "\nOutside Glasstone's 1 kt-10 Mt charts: burn and "
                    + "radiation rings are extrapolated.";
        return base.with(s, Tone.TEXT);
    }
}
