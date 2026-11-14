package com.atakmap.android.blast.plugin.core.screen;

/**
 * A map key line: a coloured bar beside a bold title, optionally with a
 * meaning underneath. {@link Kind#TALL} (bar as tall as the text, title
 * 14 + meaning 12) is used for rings; {@link Kind#SHORT} (small bar, title
 * 13) for fallout bands and zones.
 */
public final class KeyRow extends Item {

    public enum Kind {
        TALL, SHORT
    }

    public final Kind kind;
    public final Ink bar;
    public final String title;
    public final Ink titleInk;
    /** Shown under the title in DIM (TALL only), or null. */
    public final String meaning;

    public KeyRow(Kind kind, Ink bar, String title, Ink titleInk,
            String meaning) {
        super(null);
        this.kind = kind;
        this.bar = bar;
        this.title = title;
        this.titleInk = titleInk;
        this.meaning = meaning;
    }
}
