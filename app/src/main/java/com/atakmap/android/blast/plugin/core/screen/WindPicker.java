package com.atakmap.android.blast.plugin.core.screen;

import com.atakmap.android.blast.plugin.core.NucRecord;
import com.atakmap.android.blast.plugin.core.ZoneCalculator;

/**
 * The surface-wind picker used on the nuclear card and in the new-incident
 * screen: eight compass buttons, an exact bearing, three speed presets, an
 * exact speed in mph or km/h, and "wind unknown". It holds the picker's
 * state; hosts render {@link #screen()}, pass button taps to {@link #tap},
 * and re-render (or restyle in place) afterwards.
 */
public final class WindPicker {

    /** Compass buttons: {@code FROM + index}, index 0 (N) to 7 (NW). */
    public static final String FROM = "wind_from_";
    /** Speed presets: {@code SPEED + index}, 0 to 2. */
    public static final String SPEED = "wind_speed_";
    public static final String SET_FROM = "wind_set_from",
            UNIT = "wind_unit", SET_SPEED = "wind_set_speed",
            NONE = "wind_none";
    /** The exact-value boxes. */
    public static final String EXACT_FROM = "wind_exact_from",
            EXACT_SPEED = "wind_exact_speed";
    /** The live status line under the picker. */
    public static final String SLOT_STATUS = "wind_status";

    public static final String BAD_FROM = "Enter a direction from 0 to 359.",
            BAD_SPEED = "Enter a speed of at least 1 mph (2 km/h).";

    static final String[] COMPASS = { "N", "NE", "E", "SE", "S", "SW", "W",
            "NW" };
    /** Speed presets, mph: light, moderate (Glasstone's 15 mph), strong. */
    static final double[] SPEEDS = { 8, 15, 30 };
    static final String[] SPEED_LABELS = { "Light\n~8 mph",
            "Moderate\n~15 mph", "Strong\n~30 mph" };
    static final double KMH_PER_MPH = 1.609344;

    private double from, mph;
    private boolean kmh;
    /** What is typed in the two exact boxes. */
    private String exactFrom = "", exactSpeed = "";

    public WindPicker(double fromDeg, double mph) {
        set(fromDeg, mph);
    }

    /** Replace the wind (NaN, NaN for none) and empty the exact boxes. */
    public void set(double fromDeg, double mph) {
        this.from = fromDeg;
        this.mph = mph;
        exactFrom = "";
        exactSpeed = "";
    }

    /** Remember what is typed in the exact boxes (those {@code v} has). */
    public void capture(FormValues v) {
        if (v.has(EXACT_FROM))
            exactFrom = v.get(EXACT_FROM);
        if (v.has(EXACT_SPEED))
            exactSpeed = v.get(EXACT_SPEED);
    }

    public boolean valid() {
        return NucRecord.validWind(from, mph);
    }

    /** Direction the wind blows from, degrees true; NaN when not set. */
    public double fromDeg() {
        return valid() ? from : Double.NaN;
    }

    /** Speed in mph; NaN when not set. */
    public double mph() {
        return valid() ? mph : Double.NaN;
    }

    /** What a tap did. */
    public static final class Tap {
        /** The wind changed: hosts save or apply {@link #fromDeg()}/{@link #mph()}. */
        public final boolean changed;
        /** A message for the user (bad entry), or null. */
        public final String error;

        Tap(boolean changed, String error) {
            this.changed = changed;
            this.error = error;
        }
    }

    private static final Tap CHANGED = new Tap(true, null),
            RESTYLE = new Tap(false, null);

    /**
     * Run a button tap. {@code v} holds the two exact boxes. Returns null
     * when {@code action} isn't one of the picker's.
     */
    public Tap tap(String action, FormValues v) {
        if (action == null || !action.startsWith("wind_"))
            return null;
        capture(v);
        if (action.startsWith(FROM)) {
            int k = index(action, FROM, COMPASS.length);
            if (k < 0)
                return null;
            from = k * 45;
            if (Double.isNaN(mph))
                mph = SPEEDS[1];
            return CHANGED;
        }
        if (action.startsWith(SPEED)) {
            int k = index(action, SPEED, SPEEDS.length);
            if (k < 0)
                return null;
            mph = SPEEDS[k];
            return CHANGED;
        }
        switch (action) {
            case SET_FROM: {
                double f = ZoneCalculator.parseQuantity(v.get(EXACT_FROM));
                if (!(f >= 0 && f <= 360))
                    return new Tap(false, BAD_FROM);
                from = f % 360;
                if (Double.isNaN(mph))
                    mph = SPEEDS[1];
                return CHANGED;
            }
            case UNIT:
                kmh = !kmh;
                return RESTYLE;
            case SET_SPEED: {
                double s = ZoneCalculator.parseQuantity(v.get(EXACT_SPEED));
                if (kmh)
                    s = s / KMH_PER_MPH;
                if (!(s >= 1 && s <= 200))
                    return new Tap(false, BAD_SPEED);
                mph = s;
                return CHANGED;
            }
            case NONE:
                from = Double.NaN;
                mph = Double.NaN;
                return CHANGED;
            default:
                return null;
        }
    }

    private static int index(String action, String prefix, int n) {
        try {
            int k = Integer.parseInt(action.substring(prefix.length()));
            return k >= 0 && k < n ? k : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The picker as it looks now. */
    public Screen screen() {
        Screen s = new Screen();
        addTo(s);
        return s;
    }

    /** Append the picker's items to {@code s}. */
    public void addTo(Screen s) {
        boolean ok = valid();
        s.add(Text.of("Which way is the wind blowing FROM?", 13, Tone.TEXT,
                true));
        for (int row = 0; row < 2; row++) {
            Button[] bs = new Button[4];
            for (int k = row * 4; k < row * 4 + 4; k++)
                bs[k - row * 4] = pick(FROM + k, COMPASS[k],
                        ok && Math.abs(angleDiff(from, k * 45)) < 0.5);
            s.add(new Buttons(bs));
        }
        s.add(new EntryRow(EXACT_FROM, "Exact ° true (0-359)", 5, false, 2f,
                Button.of(SET_FROM, "Set")).withValue(exactFrom));
        s.add(Text.of("Wind speed", 13, Tone.TEXT, true));
        Button[] sp = new Button[SPEEDS.length];
        for (int k = 0; k < SPEEDS.length; k++)
            sp[k] = pick(SPEED + k, SPEED_LABELS[k],
                    ok && Math.abs(mph - SPEEDS[k]) < 0.05);
        s.add(new Buttons(sp));
        s.add(new EntryRow(EXACT_SPEED, "Exact speed", 5, false, 2f,
                Button.of(UNIT, kmh ? "km/h" : "mph"),
                Button.of(SET_SPEED, "Set")).withValue(exactSpeed));
        s.add(new Buttons(pick(NONE, "Wind unknown (no fallout drawn)", !ok)));
        s.add(status());
    }

    private static Button pick(String action, String label, boolean on) {
        return Button.pick(action, label, on, Button.Kind.SELECTED);
    }

    /** The status line. */
    public Text status() {
        Text base = Text.of("", 12, Tone.DIM, false).inSlot(SLOT_STATUS);
        if (!valid())
            return base.with("No wind: fallout contours are not drawn. "
                    + "Everyone within 50 miles should still get inside.",
                    Tone.DIM);
        return base.with("Wind FROM " + Math.round(from) + "° ("
                + compassName(from) + ") at " + Math.round(mph) + " mph / "
                + Math.round(mph * KMH_PER_MPH)
                + " km/h: fallout drifts toward "
                + Math.round((from + 180) % 360) + "° ("
                + compassName(from + 180) + "). Surface wind is a rough "
                + "guide: fallout follows the winds aloft.", Tone.TEXT);
    }

    static double angleDiff(double a, double b) {
        return ((a - b) % 360 + 540) % 360 - 180;
    }

    /** Nearest of 16 compass points. */
    public static String compassName(double deg) {
        String[] p = { "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S",
                "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW" };
        int i = (int) Math.round((((deg % 360) + 360) % 360) / 22.5) % 16;
        return p[i];
    }
}
