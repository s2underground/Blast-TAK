package com.atakmap.android.blast.plugin.core.app;

import com.atakmap.android.blast.plugin.core.BlastRecord;
import com.atakmap.android.blast.plugin.core.Exports;
import com.atakmap.android.blast.plugin.core.Names;
import com.atakmap.android.blast.plugin.core.NucRecord;
import com.atakmap.android.blast.plugin.core.UxoRecord;
import com.atakmap.android.blast.plugin.core.UxoText;
import com.atakmap.android.blast.plugin.core.port.Clipboard;
import com.atakmap.android.blast.plugin.core.port.Clock;
import com.atakmap.android.blast.plugin.core.port.Files;
import com.atakmap.android.blast.plugin.core.port.Incidents;
import com.atakmap.android.blast.plugin.core.port.Share;
import com.atakmap.android.blast.plugin.core.port.Team;
import com.atakmap.android.blast.plugin.core.screen.CardActions;
import com.atakmap.android.blast.plugin.core.screen.Dialog;
import com.atakmap.android.blast.plugin.core.screen.FormValues;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the incident buttons do, through the host's ports: send (with a
 * confirmation for UXO and nuclear), send to, fly to, rename, export, mark
 * cleared / reopen, delete, delete all cleared, copy, and saving changed
 * records. Each call returns an {@link Outcome}: a message, a dialog to
 * show (its buttons come back through {@link #choose}), and what to
 * redraw.
 */
public final class IncidentActions {

    /** Export formats, in the order offered. */
    public enum Format {
        DATA_PACKAGE(".zip", "Data package (ATAK / WinTAK / iTAK)"),
        KMZ(".kmz", "KMZ (Google Earth)"),
        GPX(".gpx", "GPX (Garmin)");

        public final String extension;
        public final String label;

        Format(String extension, String label) {
            this.extension = extension;
            this.label = label;
        }
    }

    // Dialog button actions (come back through choose()).
    public static final String SEND_CONFIRMED = "send_confirmed",
            RENAME_SEND = "rename_send", RENAME_LOCAL = "rename_local",
            EXPORT_SAVE = "export_save", CLEAR_SEND = "clear_send",
            CLEAR_LOCAL = "clear_local", REOPEN_SEND = "reopen_send",
            REOPEN_LOCAL = "reopen_local", DELETE_ALL = "delete_all",
            DELETE_LOCAL = "delete_local",
            DELETE_CLEARED_ALL = "delete_cleared_all",
            DELETE_CLEARED_LOCAL = "delete_cleared_local";

    public static final String GONE = "This incident is no longer on the map.",
            UPDATE_FAILED = "Could not update the incident.";

    static final Outcome NOTHING = Outcome.NOTHING;

    private static Outcome toast(String s) {
        return Outcome.toast(s);
    }

    private static Outcome dialog(Dialog d) {
        return Outcome.dialog(d);
    }

    private static Outcome redraw(String toast, boolean list) {
        return Outcome.redraw(toast, list);
    }

    private static Outcome gone() {
        return new Outcome(GONE, null, false, false, true, null);
    }

    /** After a delete: no message, tidy and redraw. */
    private static final Outcome DELETED = new Outcome(null, null, false,
            false, true, null);

    private final Incidents incidents;
    private final Share share;
    private final Team team;
    private final Clock clock;
    private final Clipboard clipboard;
    private final Files files;

    public IncidentActions(Incidents incidents, Share share, Team team,
            Clock clock, Clipboard clipboard, Files files) {
        this.incidents = incidents;
        this.share = share;
        this.team = team;
        this.clock = clock;
        this.clipboard = clipboard;
        this.files = files;
    }

    // ------------------------------------------------------------------
    // Card buttons

    /**
     * A card button shared by every kind of incident (ids in
     * {@link CardActions}). Returns null for any other action.
     */
    public Outcome card(String uid, String action) {
        switch (action) {
            case CardActions.SEND_ALL:
                return sendAll(uid);
            case CardActions.SEND_TO: {
                if (live(uid) == null)
                    return gone();
                share.sendTo(uid);
                return NOTHING;
            }
            case CardActions.FLY:
                return new Outcome(null, null, false, false, false, uid);
            case CardActions.RENAME:
                return rename(uid);
            case CardActions.EXPORT:
                return export(uid);
            case CardActions.STATUS:
                return status(uid);
            case CardActions.DELETE:
                return delete(uid);
            default:
                return null;
        }
    }

    private Incidents.Incident live(String uid) {
        return uid == null ? null : incidents.get(uid);
    }

    /** SEND TO ALL: at once for a bomb threat, after a check otherwise. */
    Outcome sendAll(String uid) {
        Incidents.Incident in = live(uid);
        if (in == null)
            return gone();
        if (in.uxo != null)
            return dialog(Dialog.builder("Send to everyone?")
                    .message("Sends the marker, cordon and report to every "
                            + "connected TAK client.\n\n"
                            + UxoText.REPORT_CAUTION
                            + "\n\nOnly transmit from outside the cordon.")
                    .positive("Send to all", SEND_CONFIRMED)
                    .negative("Cancel").about(uid).build());
        if (in.nuc != null)
            return dialog(Dialog.builder("Send to everyone?")
                    .message("Sends the marker, rings and any fallout "
                            + "outlines to every connected TAK client. They "
                            + "are labelled planning estimates.\n\nIf this "
                            + "is an exercise, say so in the name first "
                            + "(Rename…).")
                    .positive("Send to all", SEND_CONFIRMED)
                    .negative("Cancel").about(uid).build());
        return send(uid);
    }

    private Outcome send(String uid) {
        try {
            if (live(uid) == null)
                return gone();
            int n = share.sendToAll(uid);
            return toast("Sent to everyone (" + n + " items).");
        } catch (RuntimeException e) {
            return toast("Send failed.");
        }
    }

    Outcome rename(String uid) {
        Incidents.Incident in = live(uid);
        if (in == null)
            return gone();
        return dialog(Dialog.builder("Rename incident")
                .message("The name is the marker's callsign, so every TAK "
                        + "client shows it. CLEARED is added automatically "
                        + "while the incident is cleared.")
                .field(Names.base(in.name), Dialog.FieldKind.TEXT,
                        Names.MAX_LENGTH, true)
                .positive("Rename and send to all", RENAME_SEND)
                .neutral("This device only", RENAME_LOCAL).negative("Cancel")
                .about(uid).build());
    }

    Outcome export(String uid) {
        Incidents.Incident in = live(uid);
        if (in == null)
            return gone();
        List<String> labels = new ArrayList<>();
        boolean[] on = new boolean[Format.values().length];
        for (Format f : Format.values()) {
            labels.add(f.label);
            on[f.ordinal()] = true;
        }
        return dialog(Dialog.builder("Export " + in.name)
                .options(labels, on).positive("Save", EXPORT_SAVE)
                .negative("Cancel").about(uid).build());
    }

    Outcome status(String uid) {
        Incidents.Incident in = live(uid);
        if (in == null)
            return gone();
        boolean clearing = !in.cleared();
        return dialog(Dialog.builder(clearing ? "Mark incident cleared?"
                : "Reopen incident?")
                .message(clearing
                        ? "Records your callsign and the time. The rings turn "
                                + "grey and stay on the map until deleted."
                                + (in.uxo != null ? "\n\nOnly mark a UXO "
                                        + "incident cleared when authorities "
                                        + "say it has been removed." : "")
                                + (in.nuc != null ? "\n\nFor a nuclear "
                                        + "reference this marks it ENDED "
                                        + "(exercise over or no longer "
                                        + "needed)." : "")
                        : "Sets the incident back to active with the "
                                + "original distances.")
                .positive(clearing ? "Clear and send to all"
                        : "Reopen and send to all",
                        clearing ? CLEAR_SEND : REOPEN_SEND)
                .neutral("This device only",
                        clearing ? CLEAR_LOCAL : REOPEN_LOCAL)
                .negative("Cancel").about(uid).build());
    }

    Outcome delete(String uid) {
        Incidents.Incident in = live(uid);
        if (in == null)
            return gone();
        return dialog(Dialog.builder("Delete " + in.displayName + "?")
                .message("Removes the marker and the rings of this "
                        + "incident.\n\nDelete for everyone also sends a "
                        + "remove request to every connected TAK client. "
                        + "Clients that are offline keep their copy.")
                .positive("Delete for everyone", DELETE_ALL)
                .neutral("This device only", DELETE_LOCAL).negative("Cancel")
                .about(uid).build());
    }

    /** "Delete all cleared" in the list heading. */
    public Outcome deleteCleared() {
        List<String> uids = new ArrayList<>();
        StringBuilder names = new StringBuilder();
        for (Incidents.Incident in : incidents.all())
            if (in.cleared()) {
                uids.add(in.uid);
                names.append("\n• ").append(in.displayName);
            }
        if (uids.isEmpty())
            return NOTHING;
        return dialog(Dialog.builder("Delete " + uids.size()
                + " cleared incident" + (uids.size() == 1 ? "" : "s") + "?")
                .message("Removes each marker and its rings:" + names
                        + "\n\nActive incidents are not touched.")
                .positive("Delete for everyone", DELETE_CLEARED_ALL)
                .neutral("This device only", DELETE_CLEARED_LOCAL)
                .negative("Cancel").about(uids).build());
    }

    // ------------------------------------------------------------------
    // Dialog buttons

    /**
     * A dialog button. {@code v} holds the dialog's text box and ticks
     * ({@link Dialog#FIELD}, {@link Dialog#option}).
     */
    public Outcome choose(Dialog d, String action, FormValues v) {
        if (d == null || action == null)
            return NOTHING;
        String uid = d.uid;
        switch (action) {
            case SEND_CONFIRMED:
                return send(uid);
            case RENAME_SEND:
            case RENAME_LOCAL:
                return doRename(uid, v.get(Dialog.FIELD),
                        RENAME_SEND.equals(action));
            case EXPORT_SAVE: {
                boolean[] on = new boolean[Format.values().length];
                for (int i = 0; i < on.length; i++)
                    on[i] = v.has(Dialog.option(i)) ? v.checked(Dialog
                            .option(i)) : d.checked(i);
                return doExport(uid, on);
            }
            case CLEAR_SEND:
            case CLEAR_LOCAL:
            case REOPEN_SEND:
            case REOPEN_LOCAL:
                return setStatus(uid, action.startsWith("clear"),
                        action.endsWith("_send"));
            case DELETE_ALL:
                deleteForEveryone(uid);
                return DELETED;
            case DELETE_LOCAL:
                incidents.delete(uid);
                return DELETED;
            case DELETE_CLEARED_ALL:
                for (String u : d.uids)
                    deleteForEveryone(u);
                return DELETED;
            case DELETE_CLEARED_LOCAL:
                for (String u : d.uids)
                    incidents.delete(u);
                return DELETED;
            default:
                return NOTHING;
        }
    }

    private void deleteForEveryone(String uid) {
        try {
            if (live(uid) != null)
                share.deleteForEveryone(uid);
        } catch (RuntimeException e) {
            // As 1.1: the pane is redrawn either way.
        }
    }

    Outcome doRename(String uid, String typed, boolean send) {
        try {
            if (live(uid) == null)
                return gone();
            if (Names.sanitize(typed) == null)
                return toast("Enter a name.");
            if (!incidents.rename(uid, typed))
                return toast("Could not rename the incident.");
            if (send)
                share.sendToAll(uid);
            return redraw(null, true);
        } catch (RuntimeException e) {
            return toast("Could not rename the incident.");
        }
    }

    /**
     * Mark cleared or reopen. The record is read again now: the dialog may
     * have been open while someone else changed or deleted the incident.
     */
    Outcome setStatus(String uid, boolean clearing, boolean send) {
        try {
            Incidents.Incident in = live(uid);
            if (in == null)
                return gone();
            String by = team.callsign();
            long now = clock.nowMillis();
            boolean ok;
            if (in.nuc != null)
                ok = incidents.write(uid, clearing ? in.nuc.cleared(by, now)
                        : in.nuc.reopened());
            else if (in.uxo != null)
                ok = incidents.write(uid, clearing ? in.uxo.cleared(by, now)
                        : in.uxo.reopened());
            else if (in.standoff != null)
                ok = incidents.write(uid, clearing ? in.standoff.cleared(by,
                        now) : in.standoff.reopened());
            else
                return NOTHING;
            if (!ok)
                return gone();
            if (send)
                share.sendToAll(uid);
            return redraw(null, true);
        } catch (RuntimeException e) {
            return toast(UPDATE_FAILED);
        }
    }

    Outcome doExport(String uid, boolean[] on) {
        Incidents.Incident in = live(uid);
        if (in == null)
            return gone();
        StringBuilder done = new StringBuilder();
        StringBuilder failed = new StringBuilder();
        for (Format f : Format.values()) {
            if (!on[f.ordinal()])
                continue;
            try {
                // One time per file, for its name and its contents (as 1.1).
                long now = clock.nowMillis();
                String saved = files.save(Exports.fileStem(in.name, now)
                        + f.extension, bytes(in, f, now));
                done.append("\n• ").append(saved);
            } catch (IOException | RuntimeException e) {
                failed.append("\n• ").append(f.label);
            }
        }
        if (done.length() == 0 && failed.length() == 0)
            return NOTHING;
        String msg = "";
        if (done.length() > 0)
            msg += "Saved in " + files.folderLabel() + ":" + done
                    + "\n\nImport a data package with ATAK's Import "
                    + "Manager or send it as a file; copy the KMZ to Google "
                    + "Earth and the GPX to a Garmin's GPX folder.";
        if (failed.length() > 0)
            msg += (msg.isEmpty() ? "" : "\n\n") + "Could not save:" + failed;
        return dialog(Dialog.builder("Export").message(msg)
                .positive("OK", null).build());
    }

    /** One incident in one format. */
    byte[] bytes(Incidents.Incident in, Format f, long now)
            throws IOException {
        if (Double.isNaN(in.lat) || Double.isNaN(in.lon))
            throw new IOException("no position");
        String name = in.name;
        switch (f) {
            case DATA_PACKAGE: {
                Map<String, String> cot = incidents.events(in.uid);
                if (cot == null || cot.isEmpty())
                    throw new IOException("nothing to export");
                return zip(Exports.dataPackage(UUID.randomUUID().toString(),
                        name, cot));
            }
            case KMZ:
                return zip(in.nuc != null ? Exports.kmzNuc(name, in.lat,
                        in.lon, in.nuc) : in.uxo != null ? Exports.kmzUxo(
                                name, in.lat, in.lon, in.uxo)
                                : Exports.kmz(standoff(in)));
            case GPX:
            default: {
                String gpx = in.nuc != null ? Exports.gpxNuc(name, in.lat,
                        in.lon, in.nuc, now) : in.uxo != null
                                ? Exports.gpxUxo(name, in.lat, in.lon, in.uxo,
                                        now)
                                : Exports.gpx(standoff(in), now);
                return gpx.getBytes(Charset.forName("UTF-8"));
            }
        }
    }

    private static Exports.Incident standoff(Incidents.Incident in)
            throws IOException {
        if (in.standoff == null)
            throw new IOException("not an incident");
        return new Exports.Incident(in.name, in.lat, in.lon, in.standoff);
    }

    private static byte[] zip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Exports.zip(files, out);
        return out.toByteArray();
    }

    // ------------------------------------------------------------------
    // Copy and save

    /** Copy a report to the clipboard. */
    public Outcome copy(String text) {
        try {
            return clipboard.copy("Blast report", text) ? toast(
                    "Report copied. Paste it into a text, chat or email.")
                    : NOTHING;
        } catch (RuntimeException e) {
            return toast("Could not copy the report.");
        }
    }

    /**
     * Save a changed nuclear record (EDM, military picture, NBC 1, wind).
     *
     * @param done message on success, or null
     */
    public Outcome saveNuc(String uid, NucRecord changed, String done) {
        try {
            if (live(uid) == null)
                return gone();
            if (!incidents.write(uid, changed))
                return gone();
            return redraw(done, false);
        } catch (RuntimeException e) {
            return toast(UPDATE_FAILED);
        }
    }

    /** Set or remove the wind on a nuclear incident (from its card). */
    public Outcome saveWind(String uid, double fromDeg, double mph) {
        try {
            Incidents.Incident in = live(uid);
            if (in == null || in.nuc == null)
                return in == null ? gone() : NOTHING;
            if (!incidents.write(uid, in.nuc.withWind(fromDeg, mph)))
                return gone();
            return redraw(NucRecord.validWind(fromDeg, mph)
                    ? "Wind updated on this device. Use SEND to share it."
                    : "Wind removed on this device. Use SEND to share it.",
                    false);
        } catch (RuntimeException e) {
            return toast(UPDATE_FAILED);
        }
    }

    /** Save the EOD 9-line on a UXO incident. */
    public Outcome saveReport(String uid, UxoRecord.Report rep) {
        try {
            Incidents.Incident in = live(uid);
            if (in == null || in.uxo == null)
                return in == null ? gone() : NOTHING;
            if (!incidents.write(uid, in.uxo.withReport(rep)))
                return gone();
            return redraw("Report saved on the incident. Share it with SEND "
                    + "TO ALL or Send to… when you choose.", true);
        } catch (RuntimeException e) {
            return toast("Could not save the report.");
        }
    }

    /** The export formats' labels (for hosts and tests). */
    public static List<String> formatLabels() {
        List<String> l = new ArrayList<>();
        for (Format f : Format.values())
            l.add(f.label);
        return l;
    }
}
