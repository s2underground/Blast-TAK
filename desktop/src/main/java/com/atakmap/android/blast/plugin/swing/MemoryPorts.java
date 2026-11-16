package com.atakmap.android.blast.plugin.swing;

import com.atakmap.android.blast.plugin.core.BlastCodec;
import com.atakmap.android.blast.plugin.core.BlastRecord;
import com.atakmap.android.blast.plugin.core.Names;
import com.atakmap.android.blast.plugin.core.NucCodec;
import com.atakmap.android.blast.plugin.core.NucRecord;
import com.atakmap.android.blast.plugin.core.UxoCodec;
import com.atakmap.android.blast.plugin.core.UxoRecord;
import com.atakmap.android.blast.plugin.core.port.Clipboard;
import com.atakmap.android.blast.plugin.core.port.Clock;
import com.atakmap.android.blast.plugin.core.port.Files;
import com.atakmap.android.blast.plugin.core.port.Incidents;
import com.atakmap.android.blast.plugin.core.port.PositionSource;
import com.atakmap.android.blast.plugin.core.port.Settings;
import com.atakmap.android.blast.plugin.core.port.Share;
import com.atakmap.android.blast.plugin.core.port.Team;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Ports for desktop hosts: incidents kept in memory (numbered and named
 * as ATAK's BlastStore does), sending only reported (no network yet), an
 * optional position (set by hand, or from the host's own source), the
 * system clipboard, exports to a folder, and settings in a properties
 * file.
 */
public class MemoryPorts implements Incidents, Share, Team, PositionSource,
        Clock, Clipboard, Files, Settings {

    /** One incident: where it is and its record (exactly one is set). */
    public static final class Stored {
        public final String uid;
        public String name;
        public final double lat, lon;
        public BlastRecord standoff;
        public UxoRecord uxo;
        public NucRecord nuc;
        /**
         * Received from another device and not changed here since: the
         * host has it drawn already (TAK-X draws the received CoT itself).
         */
        public boolean remote;

        Stored(String uid, String name, double lat, double lon) {
            this.uid = uid;
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }

        public boolean cleared() {
            return nuc != null ? nuc.isCleared()
                    : uxo != null ? uxo.isCleared() : standoff.isCleared();
        }
    }

    private final Map<String, Stored> incidents = new LinkedHashMap<>();
    private final File home;
    private final Properties props = new Properties();
    /** Callsign when none is set in the settings. */
    public String defaultCallsign = "DESKTOP";
    /** Messages for the user (the window's status line). */
    private final Consumer<String> report;
    /** Changes to the incidents (the map redraws). */
    public Runnable changed = () -> { };
    /** The "GPS" position, or null (most desktops have none). */
    private Fix gps;

    public MemoryPorts(File home, Consumer<String> report) {
        this.home = home;
        this.report = report;
        try (InputStream in = new FileInputStream(settingsFile())) {
            props.load(in);
        } catch (IOException e) {
            // first run: defaults
        }
        loadIncidents();
    }

    // ------------------------------------------------------------------
    // Keeping incidents across restarts (incidents.properties: uid, name,
    // position and the record as its CoT detail XML, in placing order)

    private File incidentsFile() {
        return new File(home, "incidents.properties");
    }

    /** Save, then tell the host (map redraw). */
    private void touched() {
        saveIncidents();
        changed.run();
    }

    private void saveIncidents() {
        Properties p = new Properties();
        int i = 0;
        for (Stored st : incidents.values()) {
            String k = Integer.toString(i++) + ".";
            p.setProperty(k + "uid", st.uid);
            p.setProperty(k + "name", st.name);
            p.setProperty(k + "lat", Double.toString(st.lat));
            p.setProperty(k + "lon", Double.toString(st.lon));
            p.setProperty(k + "xml", st.nuc != null ? NucCodec.toXml(st.nuc)
                    : st.uxo != null ? UxoCodec.toXml(st.uxo)
                            : BlastCodec.toXml(st.standoff));
            if (st.remote)
                p.setProperty(k + "remote", "true");
        }
        p.setProperty("count", Integer.toString(i));
        if (!home.isDirectory() && !home.mkdirs())
            return;
        File tmp = new File(home, "incidents.properties.tmp");
        try (OutputStream os = new FileOutputStream(tmp)) {
            p.store(os, "Blast incidents (desktop)");
        } catch (IOException e) {
            report.accept("Could not save the incidents: " + e.getMessage());
            return;
        }
        File f = incidentsFile();
        if ((f.exists() && !f.delete()) || !tmp.renameTo(f))
            report.accept("Could not save the incidents.");
    }

    private void loadIncidents() {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(incidentsFile())) {
            p.load(in);
        } catch (IOException e) {
            return; // none saved yet
        }
        int n;
        try {
            n = Integer.parseInt(p.getProperty("count", "0"));
        } catch (NumberFormatException e) {
            return;
        }
        for (int i = 0; i < n; i++) {
            String k = i + ".";
            try {
                String uid = p.getProperty(k + "uid");
                String xml = p.getProperty(k + "xml", "");
                Stored st = new Stored(uid, p.getProperty(k + "name", ""),
                        Double.parseDouble(p.getProperty(k + "lat")),
                        Double.parseDouble(p.getProperty(k + "lon")));
                if (xml.contains("<" + NucCodec.DETAIL + " "))
                    st.nuc = NucCodec.fromXml(xml);
                else if (xml.contains("<" + UxoCodec.DETAIL + " "))
                    st.uxo = UxoCodec.fromXml(xml);
                else
                    st.standoff = BlastCodec.fromXml(xml);
                st.remote = Boolean.parseBoolean(p.getProperty(k + "remote"));
                if (uid != null && (st.nuc != null || st.uxo != null
                        || st.standoff != null))
                    incidents.put(uid, st);
            } catch (RuntimeException e) {
                report.accept("Skipped a saved incident that could not be "
                        + "read.");
            }
        }
    }

    /**
     * Add or replace an incident from outside (one received over the
     * network): exactly one record set. Saved like the others.
     */
    public void put(String uid, String name, double lat, double lon,
            BlastRecord s, UxoRecord u, NucRecord n, boolean remote) {
        Stored st = new Stored(uid, name, lat, lon);
        st.standoff = s;
        st.uxo = u;
        st.nuc = n;
        st.remote = remote;
        incidents.put(uid, st);
        touched();
    }

    /** The record of an incident as its CoT detail XML. */
    public static String recordXml(Stored st) {
        return st.nuc != null ? NucCodec.toXml(st.nuc)
                : st.uxo != null ? UxoCodec.toXml(st.uxo)
                        : BlastCodec.toXml(st.standoff);
    }

    public List<Stored> stored() {
        return new ArrayList<>(incidents.values());
    }

    public Stored stored(String uid) {
        return incidents.get(uid);
    }

    /** Where the position comes from instead of a hand-set one. */
    private PositionSource source;

    public void setGps(Fix f) {
        gps = f;
    }

    public boolean hasGps() {
        return fix() != null;
    }

    /** Use the host's own position source (a GPS, a fixed point). */
    public void setPositionSource(PositionSource p) {
        source = p;
    }

    // ------------------------------------------------------------------
    // Incidents

    private static Incident incident(Stored s) {
        return new Incident(s.uid, s.name, s.name, s.lat, s.lon, s.standoff,
                s.uxo, s.nuc);
    }

    @Override
    public List<Incident> all() {
        List<Incident> out = new ArrayList<>();
        for (Stored s : incidents.values())
            out.add(incident(s));
        return out;
    }

    @Override
    public Incident get(String uid) {
        Stored s = uid == null ? null : incidents.get(uid);
        return s == null ? null : incident(s);
    }

    private int next(int kind) {
        int max = 0;
        for (Stored s : incidents.values()) {
            int k = s.nuc != null ? 2 : s.uxo != null ? 1 : 0;
            if (k != kind)
                continue;
            int n = s.nuc != null ? Math.max(s.nuc.number,
                    Names.nucNumberIn(s.name))
                    : s.uxo != null ? Math.max(s.uxo.number,
                            Names.uxoNumberIn(s.name))
                            : Math.max(s.standoff.number,
                                    Names.numberIn(s.name));
            max = Math.max(max, n);
        }
        return max + 1;
    }

    private Stored add(Fix at, String name) {
        Stored s = new Stored(UUID.randomUUID().toString(), name, at.lat,
                at.lon);
        incidents.put(s.uid, s);
        return s;
    }

    @Override
    public String create(Fix at, BlastRecord r) {
        int n = next(0);
        r = r.withNumber(n);
        add(at, Names.initial(n, r.rowLabel)).standoff = r;
        touched();
        return last();
    }

    @Override
    public String create(Fix at, UxoRecord r) {
        int n = next(1);
        r = r.withNumber(n);
        add(at, Names.initialUxo(n, r.plainLabel())).uxo = r;
        touched();
        return last();
    }

    @Override
    public String create(Fix at, NucRecord r) {
        int n = next(2);
        r = r.withNumber(n);
        add(at, Names.initialNuc(n, r.plainLabel())).nuc = r;
        touched();
        return last();
    }

    private String last() {
        String uid = null;
        for (String k : incidents.keySet())
            uid = k;
        return uid;
    }

    private boolean put(String uid, BlastRecord s, UxoRecord u, NucRecord n,
            boolean cleared) {
        Stored st = incidents.get(uid);
        if (st == null)
            return false;
        st.standoff = s;
        st.uxo = u;
        st.nuc = n;
        st.name = Names.withStatus(st.name, cleared);
        st.remote = false; // changed here: this device draws it now
        touched();
        return true;
    }

    @Override
    public boolean write(String uid, BlastRecord r) {
        return put(uid, r, null, null, r.isCleared());
    }

    @Override
    public boolean write(String uid, UxoRecord r) {
        return put(uid, null, r, null, r.isCleared());
    }

    @Override
    public boolean write(String uid, NucRecord r) {
        return put(uid, null, null, r, r.isCleared());
    }

    @Override
    public boolean rename(String uid, String typed) {
        Stored s = incidents.get(uid);
        String clean = Names.sanitize(typed);
        if (s == null || clean == null)
            return false;
        s.name = Names.withStatus(clean, s.cleared());
        s.remote = false;
        touched();
        return true;
    }

    @Override
    public void delete(String uid) {
        incidents.remove(uid);
        touched();
    }

    /** A minimal CoT event per incident (for data-package exports). */
    @Override
    public Map<String, String> events(String uid) {
        Map<String, String> m = new LinkedHashMap<>();
        Stored s = incidents.get(uid);
        if (s == null)
            return m;
        String detail = s.nuc != null ? NucCodec.toXml(s.nuc)
                : s.uxo != null ? UxoCodec.toXml(s.uxo)
                        : BlastCodec.toXml(s.standoff);
        m.put(uid, String.format(Locale.US, "<event version=\"2.0\" uid=\"%s\""
                + " type=\"a-u-G\" how=\"h-e\"><point lat=\"%.7f\" lon=\"%.7f\""
                + " hae=\"9999999.0\" ce=\"9999999.0\" le=\"9999999.0\"/>"
                + "<detail><contact callsign=\"%s\"/>%s</detail></event>",
                uid, s.lat, s.lon, xml(s.name), detail));
        return m;
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ------------------------------------------------------------------
    // Share (no network here: say what would be sent)

    @Override
    public int sendToAll(String uid) {
        Stored s = incidents.get(uid);
        report.accept("Not connected: would send " + (s == null ? uid
                : s.name) + " to everyone.");
        return 0;
    }

    @Override
    public void sendTo(String uid) {
        Stored s = incidents.get(uid);
        report.accept("Not connected: would ask whom to send " + (s == null
                ? uid : s.name) + " to.");
    }

    @Override
    public void deleteForEveryone(String uid) {
        report.accept("Not connected: would tell everyone to delete it.");
        delete(uid);
    }

    // ------------------------------------------------------------------
    // Team, position, clock

    @Override
    public String callsign() {
        return props.getProperty("callsign", defaultCallsign);
    }

    @Override
    public List<double[]> others() {
        return new ArrayList<>();
    }

    @Override
    public Fix fix() {
        if (source != null) {
            try {
                Fix f = source.fix();
                if (f != null)
                    return f;
            } catch (RuntimeException e) {
                // no position from the host
            }
        }
        return gps;
    }

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }

    // ------------------------------------------------------------------
    // Clipboard and files

    @Override
    public boolean copy(String label, String text) {
        if (GraphicsEnvironment.isHeadless())
            return false;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new StringSelection(text), null);
        return true;
    }

    public File exportFolder() {
        return new File(home, "export");
    }

    @Override
    public String save(String fileName, byte[] data) throws IOException {
        File dir = exportFolder();
        if (!dir.isDirectory() && !dir.mkdirs())
            throw new IOException("cannot create " + dir);
        File out = new File(dir, fileName);
        if (!out.getCanonicalFile().getParentFile().equals(
                dir.getCanonicalFile()))
            throw new IOException("bad export path");
        try (OutputStream os = new FileOutputStream(out)) {
            os.write(data);
        }
        return out.getName();
    }

    @Override
    public String folderLabel() {
        return exportFolder().getPath();
    }

    // ------------------------------------------------------------------
    // Settings

    private File settingsFile() {
        return new File(home, "settings.properties");
    }

    private void store() {
        if (!home.isDirectory() && !home.mkdirs())
            return;
        try (OutputStream os = new FileOutputStream(settingsFile())) {
            props.store(os, "Blast desktop");
        } catch (IOException e) {
            report.accept("Could not save the settings: " + e.getMessage());
        }
    }

    @Override
    public String getString(String key, String def) {
        return props.getProperty(key, def);
    }

    @Override
    public void putString(String key, String value) {
        props.setProperty(key, value);
        store();
    }

    @Override
    public boolean getBoolean(String key, boolean def) {
        String v = props.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        props.setProperty(key, Boolean.toString(value));
        store();
    }
}
