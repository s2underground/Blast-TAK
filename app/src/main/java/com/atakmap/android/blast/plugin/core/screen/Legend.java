package com.atakmap.android.blast.plugin.core.screen;

/**
 * A colour swatch followed by cells laid out across the row by weight
 * (e.g. zone name, feet, metres).
 */
public final class Legend extends Item {

    public final Tone swatch;
    private final String[] cells;
    private final float[] weights;
    private final boolean[] bold;
    public final float size;
    public final Tone tone;

    public Legend(Tone swatch, String[] cells, float[] weights,
            boolean[] bold, float size, Tone tone) {
        super(null);
        if (cells.length != weights.length || cells.length != bold.length)
            throw new IllegalArgumentException("cells, weights and bold "
                    + "must have the same length");
        this.swatch = swatch;
        this.cells = cells.clone();
        this.weights = weights.clone();
        this.bold = bold.clone();
        this.size = size;
        this.tone = tone;
    }

    public int cellCount() {
        return cells.length;
    }

    public String cell(int i) {
        return cells[i];
    }

    public float weight(int i) {
        return weights[i];
    }

    public boolean bold(int i) {
        return bold[i];
    }
}
