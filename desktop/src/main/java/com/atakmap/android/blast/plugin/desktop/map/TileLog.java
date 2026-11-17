package com.atakmap.android.blast.plugin.desktop.map;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;
import java.util.TimeZone;

/**
 * What went wrong while loading map tiles (server answers other than OK,
 * errors, timeouts), for the Maps menu's log and for bug reports. Kept on
 * this computer only (tiles.log in Blast's folder, at most about 1 MB,
 * then started over as tiles.old.log). Holds tile numbers, which show
 * the map areas viewed: the user decides whether to share it.
 */
public final class TileLog {

    private static final long MAX_BYTES = 512 * 1024;
    private static final int KEEP = 400;

    private final File file;
    private final Deque<String> recent = new ArrayDeque<>();

    /** {@code file} null: memory only. */
    public TileLog(File file) {
        this.file = file;
    }

    public synchronized void add(String line) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss'Z'",
                Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        String l = f.format(new Date()) + "  " + line.replace('\n', ' ');
        recent.addLast(l);
        while (recent.size() > KEEP)
            recent.removeFirst();
        if (file == null)
            return;
        try {
            if (file.length() > MAX_BYTES) {
                File old = new File(file.getParentFile(), "tiles.old.log");
                old.delete();
                file.renameTo(old);
            }
            File dir = file.getParentFile();
            if (dir != null && !dir.isDirectory())
                dir.mkdirs();
            try (OutputStream o = new FileOutputStream(file, true)) {
                o.write((l + System.lineSeparator()).getBytes(
                        StandardCharsets.UTF_8));
            }
        } catch (IOException | SecurityException e) {
            // the log is best effort
        }
    }

    /** The latest lines, oldest first. */
    public synchronized String text() {
        if (recent.isEmpty())
            return "No problems loading map tiles since Blast started.";
        return String.join("\n", recent);
    }

    public File file() {
        return file;
    }
}
