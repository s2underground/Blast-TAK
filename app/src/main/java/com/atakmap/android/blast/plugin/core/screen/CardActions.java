package com.atakmap.android.blast.plugin.core.screen;

/** Action ids and button rows shared by every incident card. */
public final class CardActions {

    private CardActions() {
    }

    public static final String SEND_ALL = "send_all", SEND_TO = "send_to",
            FLY = "fly", RENAME = "rename", EXPORT = "export",
            STATUS = "status", DELETE = "delete", LABELS = "labels";

    /** SEND TO ALL (wide, blue), Send to…, Fly to. */
    public static Buttons sendRow(int marginTopDp, boolean boldSend) {
        return new Buttons(marginTopDp, new Button(SEND_ALL, "SEND TO ALL",
                Button.Kind.PRIMARY, 1.4f, boldSend), Button.of(SEND_TO,
                        "Send to…"), Button.of(FLY, "Fly to"));
    }

    /** Rename… / Export…. */
    public static Buttons renameRow() {
        return new Buttons(Button.of(RENAME, "Rename…"), Button.of(EXPORT,
                "Export…"));
    }

    /**
     * Hide or show the labels on every incident's rings (distances) and
     * fallout / zone outlines (names) on this device ({@code shown} = they
     * are shown now).
     */
    public static Buttons labelsRow(boolean shown) {
        return labelsRow(shown, false);
    }

    /**
     * @param fallout the card can draw fallout / zone outlines (nuclear),
     *        whose names the switch also hides
     */
    public static Buttons labelsRow(boolean shown, boolean fallout) {
        String what = fallout ? "ring & fallout labels" : "ring labels";
        return new Buttons(Button.of(LABELS, (shown ? "Hide " : "Show ")
                + what));
    }

    /** Mark cleared (or Reopen) / Delete…. */
    public static Buttons statusRow(boolean cleared) {
        return new Buttons(Button.of(STATUS, cleared ? "Reopen"
                : "Mark cleared"), Button.of(DELETE, "Delete…"));
    }
}
