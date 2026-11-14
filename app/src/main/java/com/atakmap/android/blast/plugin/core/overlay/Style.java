package com.atakmap.android.blast.plugin.core.overlay;

/** How one overlay shape is drawn: plain ARGB colours and a line kind. */
public final class Style {

    /**
     * Line kind. {@link #DEFAULT} means "whatever the host draws by default"
     * (ATAK: solid); kept separate so the ATAK layer never changes a style
     * it didn't set before.
     */
    public enum Line {
        DEFAULT, SOLID, DASHED, DOTTED
    }

    public final int strokeArgb;
    public final int fillArgb;
    /** Line width in the host's stroke-weight units (ATAK: 1-10). */
    public final double width;
    public final Line line;

    public Style(int strokeArgb, int fillArgb, double width, Line line) {
        this.strokeArgb = strokeArgb;
        this.fillArgb = fillArgb;
        this.width = width;
        this.line = line == null ? Line.DEFAULT : line;
    }

    /** True if the fill is fully transparent. */
    public boolean noFill() {
        return (fillArgb >>> 24) == 0;
    }
}
