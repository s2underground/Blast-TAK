package com.s2underground.blast.takx;

import com.atakmap.android.blast.plugin.core.overlay.OverlayShape;
import com.atakmap.android.blast.plugin.core.overlay.Overlays;
import com.atakmap.android.blast.plugin.core.overlay.Style;
import com.atakmap.android.blast.plugin.swing.MemoryPorts;

import gov.takx.api.coordinatesystem.ILocation;
import gov.takx.api.mapobject.IMapObject;
import gov.takx.api.mapobject.IMapObjectManager;
import gov.takx.api.plugin.app.IAppPluginDelegate;
import gov.takx.api.pointdropper.IPointDropperService;
import gov.takx.api.pointdropper.PointDropperConfiguration;
import gov.takx.api.coordinatesystem.RaptorLocation;
import gov.takx.api.shapes.IMutableShape;
import gov.takx.api.shapes.IShapeService;
import gov.takx.dataservices.shapes.Circle;
import gov.takx.dataservices.shapes.Polygon;
import gov.takx.dataservices.shapes.Polyline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws every incident on the TAK-X globe: its marker (a CoT point of the
 * same type as ATAK Blast's, named like the incident) and, from the core's
 * overlay model ({@link Overlays}), circles, polygons and polylines through
 * {@link IShapeService}, with the same colours, fills, widths and dashes
 * the ATAK layer uses. Shapes are unpublished (this plugin's own); an
 * incident is redrawn when its record or name changes.
 *
 * <p>Incidents received from other devices are drawn by TAK-X itself (it
 * imports the CoT), so they are left alone here. When one is changed here
 * it becomes this plugin's to draw, and TAK-X's received copy is removed;
 * likewise when a received incident is deleted here.
 */
final class TakxShapes {

    private static final Logger LOG = LoggerFactory.getLogger(
            TakxShapes.class);

    /** Bit patterns for dashed and dotted lines (TAK-X stipple). */
    private static final short DASHED = (short) 0xFF00, DOTTED = (short) 0xAAAA;

    /** The incident marker's CoT type (as BlastStore.MARKER_TYPE). */
    static final String MARKER_TYPE = "a-h-G";

    private final IShapeService shapes;
    private final MemoryPorts ports;
    /** For the markers; either may be null (then no marker is drawn). */
    private final IPointDropperService dropper;
    private final IMapObjectManager objects;
    private final IAppPluginDelegate delegate;
    /** Incident uid to its marker, and marker uid to incident uid. */
    private final Map<String, IMapObject> markers = new HashMap<>();
    private final Map<String, String> markerOwner = new HashMap<>();
    /** Per incident: what was drawn, and the shape names. */
    private final Map<String, String> drawnSig = new HashMap<>();
    private final Map<String, List<String>> names = new HashMap<>();
    /** Shape name to incident uid (selection on the globe). */
    private final Map<String, String> owner = new HashMap<>();
    /** Incidents TAK-X draws from received CoT. */
    private final Set<String> received = new HashSet<>();

    TakxShapes(IShapeService shapes, MemoryPorts ports,
            IPointDropperService dropper, IMapObjectManager objects,
            IAppPluginDelegate delegate) {
        this.shapes = shapes;
        this.ports = ports;
        this.dropper = dropper;
        this.objects = objects;
        this.delegate = delegate;
    }

    /**
     * The incident a clicked map object is, or null: one this plugin drew,
     * or one TAK-X drew from received CoT (the marker has the incident's
     * uid, the rings and outlines the uid plus a suffix such as ".mand").
     */
    String incidentOf(IMapObject o) {
        if (o == null)
            return null;
        String u = o.getUid();
        String uid = markerOwner.get(u);
        if (uid == null)
            uid = owner.get(o.getName());
        if (uid == null && u != null) {
            if (ports.stored(u) != null)
                return u;
            for (int i = u.lastIndexOf('.'); i > 0; i = u.lastIndexOf('.',
                    i - 1))
                if (ports.stored(u.substring(0, i)) != null)
                    return u.substring(0, i);
        }
        return uid;
    }

    /** Incidents whose marker has been seen on the globe. */
    private final Set<String> seenMarker = new HashSet<>();

    /**
     * Incidents whose marker was deleted in TAK-X: seen on the globe
     * before, and now TAK-X has neither its key nor the object (a hidden
     * marker keeps both). Covers this plugin's markers and TAK-X's copies
     * of received incidents (whose marker has the incident's uid).
     */
    List<String> deletedMarkers() {
        List<String> out = new ArrayList<>();
        if (objects == null)
            return out;
        Set<String> uids = new HashSet<>(), keys = new HashSet<>();
        try {
            for (IMapObject o : objects.getMapObjects()) {
                if (o.getUid() != null)
                    uids.add(o.getUid());
                if (o.getKey() != null)
                    keys.add(o.getKey());
            }
        } catch (Exception | LinkageError e) {
            LOG.debug("could not list map objects", e);
            return out;
        }
        for (Map.Entry<String, IMapObject> e : markers.entrySet()) {
            IMapObject m = e.getValue();
            String key = m.getKey();
            boolean there = uids.contains(m.getUid())
                    || (key != null && (keys.contains(key)
                            || keyExists(key)));
            check(e.getKey(), there, out);
        }
        for (String uid : received)
            check(uid, uids.contains(uid), out);
        return out;
    }

    private boolean keyExists(String key) {
        try {
            return objects.keyExists(key);
        } catch (Exception | LinkageError e) {
            return false;
        }
    }

    private void check(String uid, boolean there, List<String> out) {
        if (there)
            seenMarker.add(uid);
        else if (seenMarker.remove(uid))
            out.add(uid);
    }

    /** Whether a removed object is one of the incident markers. */
    boolean isIncidentMarker(IMapObject o) {
        String u = o == null ? null : o.getUid();
        if (u == null)
            return false;
        if (markerOwner.containsKey(u))
            return true;
        for (IMapObject m : markers.values())
            if (m == o || (o.getKey() != null && o.getKey().equals(
                    m.getKey())))
                return true;
        return ports.stored(u) != null;
    }

    /** The user deleted this incident's marker: don't delete it again. */
    void forgetMarker(String uid) {
        IMapObject m = markers.remove(uid);
        if (m != null)
            markerOwner.remove(m.getUid());
        seenMarker.remove(uid);
    }

    /** Bring the globe up to date with the incidents. */
    void sync() {
        Set<String> live = new HashSet<>();
        for (MemoryPorts.Stored s : ports.stored()) {
            live.add(s.uid);
            if (s.remote) {
                // TAK-X draws it; take down a copy of ours, if any.
                if (received.add(s.uid)) {
                    remove(s.uid);
                    drawnSig.remove(s.uid);
                }
                continue;
            }
            if (received.remove(s.uid)) {
                seenMarker.remove(s.uid);
                dropReceived(s.uid); // changed here: ours to draw now
            }
            String sig = s.name + "|" + recordXml(s);
            if (sig.equals(drawnSig.get(s.uid)))
                continue;
            remove(s.uid);
            draw(s);
            drawnSig.put(s.uid, sig);
        }
        for (String uid : new ArrayList<>(names.keySet()))
            if (!live.contains(uid)) {
                remove(uid);
                drawnSig.remove(uid);
            }
        for (String uid : new ArrayList<>(received))
            if (!live.contains(uid)) {
                received.remove(uid);
                seenMarker.remove(uid);
                dropReceived(uid); // deleted here
            }
    }

    /**
     * Remove what TAK-X drew from an incident's received CoT: its marker
     * (the incident's uid) and shapes (the uid plus a suffix). Best
     * effort: anything left over goes stale as ATAK's would.
     */
    private void dropReceived(String uid) {
        if (objects == null)
            return;
        int n = 0;
        try {
            for (IMapObject o : new ArrayList<IMapObject>(
                    objects.getMapObjects())) {
                String u = o.getUid();
                if (u == null || markerOwner.containsKey(u)
                        || !(u.equals(uid) || u.startsWith(uid + ".")))
                    continue;
                try {
                    objects.deleteMapObject(o);
                    n++;
                } catch (Exception e) {
                    LOG.debug("could not remove {}", u, e);
                }
            }
        } catch (Exception | LinkageError e) {
            LOG.warn("could not remove the received copy of {}", uid, e);
        }
        LOG.debug("removed {} received items of {}", n, uid);
    }

    private static String recordXml(MemoryPorts.Stored s) {
        return s.nuc != null
                ? com.atakmap.android.blast.plugin.core.NucCodec.toXml(s.nuc)
                : s.uxo != null ? com.atakmap.android.blast.plugin.core
                        .UxoCodec.toXml(s.uxo)
                        : com.atakmap.android.blast.plugin.core.BlastCodec
                                .toXml(s.standoff);
    }

    /** Remove everything this plugin drew. */
    void clear() {
        Set<String> uids = new HashSet<>(names.keySet());
        uids.addAll(markers.keySet());
        for (String uid : uids)
            remove(uid);
        drawnSig.clear();
        received.clear();
    }

    private void remove(String uid) {
        removeMarker(uid);
        List<String> l = names.remove(uid);
        if (l == null)
            return;
        for (String n : l) {
            owner.remove(n);
            try {
                shapes.deleteShape(n);
            } catch (Exception e) {
                LOG.debug("shape {} already gone", n);
            }
        }
    }

    private static List<OverlayShape> overlay(MemoryPorts.Stored s) {
        return s.nuc != null ? Overlays.nuc(s.nuc, s.lat, s.lon)
                : s.uxo != null ? Overlays.uxo(s.uxo)
                        : Overlays.standoff(s.standoff);
    }

    /** A unique shape name: its title, then the incident's name. */
    private String nameFor(OverlayShape sh, MemoryPorts.Stored s) {
        String base = (sh.title == null || sh.title.isEmpty() ? sh.id
                : sh.title) + " (" + s.name + ")";
        String n = base;
        for (int i = 2; owner.containsKey(n) || shapes.getShape(n) != null;
                i++)
            n = base + " #" + i;
        return n;
    }

    /** The incident's marker: a CoT point named like the incident. */
    private void drawMarker(MemoryPorts.Stored s) {
        if (dropper == null)
            return;
        try {
            final String name = s.name;
            PointDropperConfiguration c = PointDropperConfiguration
                    .builder(MARKER_TYPE).nameGenerator(() -> name).build();
            IMapObject m = dropper.dropPoint(c, new RaptorLocation(s.lat,
                    s.lon));
            if (m != null) {
                markers.put(s.uid, m);
                markerOwner.put(m.getUid(), s.uid);
            }
        } catch (Exception | LinkageError e) {
            LOG.warn("could not place the marker for {}", s.name, e);
        }
    }

    private void removeMarker(String uid) {
        seenMarker.remove(uid);
        IMapObject m = markers.remove(uid);
        if (m == null)
            return;
        markerOwner.remove(m.getUid());
        try {
            if (objects != null) {
                objects.deleteMapObject(m);
                return;
            }
            delegate.getMapObjectController(m).ifPresent(
                    gov.takx.api.mapobject.IMapObjectController::delete);
        } catch (Exception e) {
            LOG.debug("marker for {} already gone", uid);
        }
    }

    private void draw(MemoryPorts.Stored s) {
        drawMarker(s);
        List<String> drawn = new ArrayList<>();
        names.put(s.uid, drawn);
        for (OverlayShape sh : overlay(s)) {
            String name = nameFor(sh, s);
            try {
                IMutableShape shape;
                if (sh.kind == OverlayShape.Kind.CIRCLE) {
                    Circle c = new Circle(name, new RaptorLocation(s.lat,
                            s.lon), sh.radiusM);
                    style(c, sh.style);
                    shapes.createCircleMapObject(c);
                    shape = c;
                } else {
                    List<ILocation> pts = new ArrayList<>();
                    for (int i = 0; i < sh.pointCount(); i++)
                        pts.add(new RaptorLocation(sh.lat(i), sh.lon(i)));
                    if (sh.kind == OverlayShape.Kind.POLYGON) {
                        Polygon p = new Polygon(name, pts);
                        style(p, sh.style);
                        shapes.createPolygonMapObject(p);
                        shape = p;
                    } else {
                        Polyline p = new Polyline(name, pts);
                        style(p, sh.style);
                        shapes.createPolylineMapObject(p);
                        shape = p;
                    }
                }
                shape.setDescription(sh.title == null ? "" : sh.title);
                drawn.add(name);
                owner.put(name, s.uid);
            } catch (Exception e) {
                LOG.warn("could not draw {}", name, e);
            }
        }
    }

    private static void style(IMutableShape shape, Style st) {
        shape.setLineColor(new Color(st.strokeArgb, true));
        // TAK-X takes the opacity from the fill colour's alpha.
        shape.setFillColor(new Color(st.noFill() ? st.strokeArgb & 0xFFFFFF
                : st.fillArgb, true));
        shape.setLineWidth(Math.max(1, st.width));
        if (st.line == Style.Line.DASHED) {
            shape.setLineStipplePattern(DASHED);
            shape.setLineStippleFactor(2);
        } else if (st.line == Style.Line.DOTTED) {
            shape.setLineStipplePattern(DOTTED);
            shape.setLineStippleFactor(2);
        }
        shape.setEditable(false);
        shape.setAllowDrag(false);
    }
}
