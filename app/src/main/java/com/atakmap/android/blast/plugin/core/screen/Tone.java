package com.atakmap.android.blast.plugin.core.screen;

/**
 * Colour roles for screen text and swatches. Each host maps them to its
 * own palette (ATAK: Blast's dark-pane colours), so a light desktop theme
 * can differ without the core knowing.
 */
public enum Tone {
    /** Normal text. */
    TEXT,
    /** Secondary text, and anything that is cleared. */
    DIM,
    /** Caution: shelter-in-place, notes that need attention. */
    AMBER,
    /** Danger: inside the mandatory zone, conflicting data. */
    RED,
    /** Safe: at or beyond the preferred distance. */
    GREEN,
    /** Section headings. */
    BLUE,
    /** Military ordnance (UXO): the cordon and its threat line. */
    ORANGE,
    /** Nuclear reference: the threat line. */
    NUC
}
