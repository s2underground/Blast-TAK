package com.atakmap.android.blast.plugin.atak;

import com.atakmap.android.blast.plugin.core.BlastRecord;
import com.atakmap.android.blast.plugin.core.NucRecord;
import com.atakmap.android.blast.plugin.core.UxoRecord;
import com.atakmap.android.blast.plugin.core.port.Clipboard;
import com.atakmap.android.blast.plugin.core.port.Clock;
import com.atakmap.android.blast.plugin.core.port.Files;
import com.atakmap.android.blast.plugin.core.port.Incidents;
import com.atakmap.android.blast.plugin.core.port.MapControl;
import com.atakmap.android.blast.plugin.core.port.Settings;
import com.atakmap.android.blast.plugin.core.port.PositionSource;
import com.atakmap.android.blast.plugin.core.port.Share;
import com.atakmap.android.blast.plugin.core.port.Team;
import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ATAK implementations of the core's ports. */
public final class AtakPorts {

    private static final String TAG = "BlastPorts";

    private AtakPorts() {
    }

    /** The self marker's position; null without a usable fix. */
    public static PositionSource position(final MapView mapView) {
        return new PositionSource() {
            @Override
            public Fix fix() {
                try {
                    Marker self = ATAKUtilities.findSelf(mapView);
                    GeoPoint gp = self != null ? self.getPoint() : null;
                    if (gp == null || !gp.isValid() || (gp.getLatitude() == 0
                            && gp.getLongitude() == 0))
                        return null;
                    // CE is NaN when unknown; CotPoint uses 9999999.
                    double ce = gp.getCE();
                    boolean known = !Double.isNaN(ce) && ce > 0
                            && ce < 9999999;
                    return new Fix(gp.getLatitude(), gp.getLongitude(),
                            known ? ce : Double.NaN);
                } catch (RuntimeException e) {
                    Log.w(TAG, "no self position", e);
                    return null;
                }
            }
        };
    }

    /**
     * The device callsign, and every individual contact's map item except
     * the self marker.
     */
    public static Team team(final MapView mapView) {
        return new Team() {
            @Override
            public String callsign() {
                try {
                    String cs = mapView.getDeviceCallsign();
                    return cs == null ? "" : cs;
                } catch (RuntimeException e) {
                    return "";
                }
            }

            @Override
            public List<double[]> others() {
                List<double[]> out = new ArrayList<>();
                try {
                    Marker self = ATAKUtilities.findSelf(mapView);
                    for (Contact c : Contacts.getInstance().getAllContacts()) {
                        if (!(c instanceof IndividualContact))
                            continue;
                        MapItem mi = ((IndividualContact) c).getMapItem();
                        if (!(mi instanceof PointMapItem) || mi == self)
                            continue;
                        GeoPoint gp = ((PointMapItem) mi).getPoint();
                        if (gp != null && gp.isValid())
                            out.add(new double[] { gp.getLatitude(),
                                    gp.getLongitude() });
                    }
                } catch (RuntimeException e) {
                    Log.w(TAG, "contact scan failed", e);
                }
                return out;
            }
        };
    }

    /** The incident markers held by {@link BlastStore}. */
    public static Incidents incidents(final BlastStore store) {
        return new Incidents() {
            @Override
            public List<Incident> all() {
                List<Incident> out = new ArrayList<>();
                for (Marker m : store.all()) {
                    Incident in = incident(m);
                    if (in != null)
                        out.add(in);
                }
                return out;
            }

            @Override
            public Incident get(String uid) {
                Marker m = store.liveMarker(uid);
                return m == null ? null : incident(m);
            }

            @Override
            public boolean write(String uid, BlastRecord r) {
                Marker m = store.liveMarker(uid);
                return m != null && store.write(m, r);
            }

            @Override
            public boolean write(String uid, UxoRecord r) {
                Marker m = store.liveMarker(uid);
                return m != null && store.writeUxo(m, r);
            }

            @Override
            public boolean write(String uid, NucRecord r) {
                Marker m = store.liveMarker(uid);
                return m != null && store.writeNuc(m, r);
            }

            @Override
            public boolean rename(String uid, String typed) {
                Marker m = store.liveMarker(uid);
                return m != null && store.rename(m, typed);
            }

            @Override
            public void delete(String uid) {
                store.deleteIncident(uid);
            }

            @Override
            public Map<String, String> events(String uid) {
                Map<String, String> cot = new LinkedHashMap<>();
                Marker m = store.liveMarker(uid);
                if (m == null)
                    return cot;
                for (MapItem mi : store.items(m)) {
                    CotEvent ev = CotEventFactory.createCotEvent(mi);
                    if (ev != null && ev.isValid())
                        cot.put(mi.getUID(), ev.toString());
                }
                return cot;
            }
        };
    }

    /** An incident marker as the core sees it, or null if it has no record. */
    public static Incidents.Incident incident(Marker m) {
        NucRecord n = BlastStore.readNuc(m);
        UxoRecord u = n == null ? BlastStore.readUxo(m) : null;
        BlastRecord r = n == null && u == null ? BlastStore.read(m) : null;
        if (n == null && u == null && r == null)
            return null;
        GeoPoint gp = m.getPoint();
        boolean ok = gp != null && gp.isValid();
        return new Incidents.Incident(m.getUID(),
                ATAKUtilities.getDisplayName(m), BlastStore.nameOf(m),
                ok ? gp.getLatitude() : Double.NaN,
                ok ? gp.getLongitude() : Double.NaN, r, u, n);
    }

    /** Sending through ATAK's commo ({@link Sharing}). */
    public static Share share(final BlastStore store, final Sharing sharing) {
        return new Share() {
            @Override
            public int sendToAll(String uid) {
                Marker m = store.liveMarker(uid);
                return m == null ? 0 : sharing.sendToAll(m);
            }

            @Override
            public void sendTo(String uid) {
                Marker m = store.liveMarker(uid);
                if (m != null)
                    sharing.sendTo(m);
            }

            @Override
            public void deleteForEveryone(String uid) {
                Marker m = store.liveMarker(uid);
                if (m != null)
                    sharing.deleteForEveryone(m);
            }
        };
    }

    public static Clock clock() {
        return new Clock() {
            @Override
            public long nowMillis() {
                return System.currentTimeMillis();
            }
        };
    }

    /** Android's clipboard, through ATAK's activity. */
    public static Clipboard clipboard(final android.content.Context ctx) {
        return new Clipboard() {
            @Override
            public boolean copy(String label, String text) {
                android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) ctx
                                .getSystemService(
                                        android.content.Context.CLIPBOARD_SERVICE);
                if (cm == null)
                    return false;
                cm.setPrimaryClip(android.content.ClipData.newPlainText(label,
                        text));
                return true;
            }
        };
    }

    /** atak/export/Blast/. */
    public static File exportDirectory() {
        return FileSystemUtils.getItem(FileSystemUtils.EXPORT_DIRECTORY
                + File.separator + "Blast");
    }

    /** Exports go to {@link #exportDirectory()}. */
    public static Files files() {
        return new Files() {
            @Override
            public String save(String fileName, byte[] data)
                    throws IOException {
                File dir = exportDirectory();
                if (!IOProviderFactory.exists(dir)
                        && !IOProviderFactory.mkdirs(dir))
                    throw new IOException("cannot create export folder");
                File out = new File(dir, fileName);
                // Names only have [A-Za-z0-9_-] and an extension; check
                // anyway that the file stays inside the export folder.
                if (!out.getCanonicalFile().getParentFile()
                        .equals(dir.getCanonicalFile()))
                    throw new IOException("bad export path");
                try (OutputStream os = IOProviderFactory.getOutputStream(out)) {
                    os.write(data);
                }
                return out.getName();
            }

            @Override
            public String folderLabel() {
                return "atak/export/Blast";
            }
        };
    }

    /** ATAK's shared preferences. */
    public static Settings settings(
            final android.content.SharedPreferences prefs) {
        return new Settings() {
            @Override
            public String getString(String key, String def) {
                return prefs.getString(key, def);
            }

            @Override
            public void putString(String key, String value) {
                prefs.edit().putString(key, value).apply();
            }

            @Override
            public boolean getBoolean(String key, boolean def) {
                return prefs.getBoolean(key, def);
            }

            @Override
            public void putBoolean(String key, boolean value) {
                prefs.edit().putBoolean(key, value).apply();
            }
        };
    }

    /** The map centre and ATAK's MGRS formatting. */
    public static MapControl map(final MapView mapView) {
        return new MapControl() {
            @Override
            public double[] center() {
                com.atakmap.coremap.maps.coords.GeoPointMetaData c = mapView
                        .getCenterPoint();
                GeoPoint gp = c == null ? null : c.get();
                return gp == null ? null
                        : new double[] { gp.getLatitude(), gp.getLongitude() };
            }

            @Override
            public String mgrs(double lat, double lon) {
                GeoPoint gp = new GeoPoint(lat, lon);
                if (!gp.isValid())
                    throw new IllegalArgumentException("invalid point");
                return com.atakmap.coremap.conversions.CoordinateFormatUtilities
                        .formatToString(gp, com.atakmap.coremap.conversions
                                .CoordinateFormat.MGRS);
            }
        };
    }
}
