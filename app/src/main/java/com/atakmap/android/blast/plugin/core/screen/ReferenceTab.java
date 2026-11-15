package com.atakmap.android.blast.plugin.core.screen;

import com.atakmap.android.blast.plugin.core.NucReference;
import com.atakmap.android.blast.plugin.core.UxoReference;

import java.util.Locale;

/**
 * The Reference tab: the emergency number (with Change…), every reference
 * section (UXO, bomb threat, nuclear) and the disclaimer. Each section's
 * heading is a slot named {@link #anchor} so hosts can scroll to it.
 */
public final class ReferenceTab {

    private ReferenceTab() {
    }

    public static final String SLOT_EMERGENCY = "emergency",
            CHANGE_EMERGENCY = "change_emergency";
    public static final String EMERGENCY_TITLE = "Emergency number",
            EMERGENCY_HELP = "Shown on UXO cards and reports, e.g. 911, 112, "
                    + "101 / 102 or a mine action centre.";
    /** Longest emergency number accepted. */
    public static final int EMERGENCY_MAX = 24;

    /** The slot holding section {@code id}'s heading. */
    public static String anchor(String sectionId) {
        return "ref_" + sectionId;
    }

    public static Text emergency(String number) {
        return Text.of("Emergency number: " + number, 14, Tone.TEXT, true)
                .inSlot(SLOT_EMERGENCY);
    }

    public static Screen screen(String emergencyNumber) {
        Screen s = new Screen();
        s.add(new TitleRow(emergency(emergencyNumber), Button.of(
                CHANGE_EMERGENCY, "Change…"), false));
        for (UxoReference.Section sec : NucReference.allSections()) {
            s.add(Text.step(sec.title.toUpperCase(Locale.US))
                    .inSlot(anchor(sec.id)));
            for (String para : sec.body) {
                boolean bullet = para.startsWith("• ");
                s.add(Text.of(para, 13, bullet ? Tone.TEXT : Tone.DIM, false)
                        .padded(0, 3).indented(bullet ? 8 : 0).linked());
            }
        }
        s.add(MainPane.footer());
        return s;
    }
}
