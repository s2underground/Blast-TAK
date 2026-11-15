package com.atakmap.android.blast.plugin.core.screen;

import com.atakmap.android.blast.plugin.core.Guidance;
import com.atakmap.android.blast.plugin.core.StandoffTable;
import com.atakmap.android.blast.plugin.core.UxoTable;

/** The pane's title, sources line and tabs, and its footer. */
public final class MainPane {

    private MainPane() {
    }

    public static final String TAB_INCIDENTS = "tab_incidents",
            TAB_REFERENCE = "tab_reference";

    /** Title, sources and the two tabs ({@code reference} = Reference on). */
    public static Screen header(StandoffTable table, boolean reference) {
        Screen s = new Screen();
        s.add(Text.of("BLAST STANDOFF", 18, Tone.TEXT, true));
        s.add(Text.of("Bomb threats: " + table.title + " ("
                + table.editionLabel() + "). Military ordnance (UXO): "
                + UxoTable.SHORT_NAME + " (" + UxoTable.EDITION_LABEL + "). "
                + "Nuclear reference: FEMA 2022, Glasstone & Dolan 1977.", 12,
                Tone.DIM, false));
        s.add(new Buttons(6, Button.pick(TAB_INCIDENTS, "INCIDENTS",
                !reference, Button.Kind.SELECTED_STANDOFF), Button.pick(
                        TAB_REFERENCE, "REFERENCE", reference,
                        Button.Kind.SELECTED_STANDOFF)).spacedBelow(2));
        return s;
    }

    /** The disclaimer at the bottom of each tab. */
    public static Text footer() {
        return Text.of(Guidance.DISCLAIMER, 11, Tone.DIM, false).padded(12, 4);
    }
}
