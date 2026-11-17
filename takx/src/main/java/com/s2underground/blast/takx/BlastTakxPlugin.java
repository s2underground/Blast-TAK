package com.s2underground.blast.takx;

import com.atakmap.android.blast.plugin.core.CotEvents;
import com.atakmap.android.blast.plugin.core.Mgrs;
import com.atakmap.android.blast.plugin.core.Wmm;
import com.atakmap.android.blast.plugin.core.app.BlastApp;
import com.atakmap.android.blast.plugin.core.port.MapControl;
import com.atakmap.android.blast.plugin.core.port.PositionSource;
import com.atakmap.android.blast.plugin.swing.MemoryPorts;
import com.atakmap.android.blast.plugin.swing.SwingPane;
import com.atakmap.android.blast.plugin.swing.SwingScreen;
import com.atakmap.coremap.maps.conversion.GeomagneticField;
import com.atakmap.coremap.maps.coords.MGRSPoint;

import gov.tak.api.comms.commo.ICommonCommoManager;
import gov.tak.api.comms.commo.ICotEventSender;
import gov.tak.api.contact.IIndividualContactManager;
import gov.tak.api.location.ILocationProviderManager;
import gov.tak.api.maps.coords.GeoPoint;
import gov.tak.api.maps.coords.GeoPointMetaData;
import gov.takx.api.annotation.CallSign;
import gov.takx.api.annotation.NodeUUID;
import gov.takx.api.globe.IGlobeDelegate;
import gov.takx.api.mapobject.IMapObject;
import gov.takx.api.mapobject.IMapObjectManager;
import gov.takx.api.mapobject.IMapObjectManagerListener;
import gov.takx.api.mapobject.IMapObjectSelectListener;
import gov.takx.api.mapobject.MapObjectSelectEvent;
import gov.takx.api.plugin.annotations.AppPlugin;
import gov.takx.api.plugin.annotations.PluginDescriptor;
import gov.takx.api.plugin.annotations.PluginSpecification;
import gov.takx.api.plugin.app.AAppPlugin;
import gov.takx.api.plugin.support.SupportedPlatformVersion;
import gov.takx.api.pointdropper.IPointDropperService;
import gov.takx.api.ui.ComponentPosition;
import gov.takx.platform.ui.ComponentDescriptor;
import gov.takx.platform.ui.ComponentDescriptorBuilder;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Blast for TAK-X: the same core and Swing pane as the desktop host,
 * docked on the right of TAK-X, with incidents drawn on the TAK-X globe
 * as TAK-X shapes. Incidents are sent and received as the same CoT ATAK
 * Blast uses ({@link TakxShare}, {@link TakxReceiver}), so ATAK and TAK-X
 * users see each other's incidents.
 */
@AppPlugin(
        spec = @PluginSpecification(
                family = BlastTakxPlugin.FAMILY,
                type = "Blast",
                version = BlastTakxPlugin.VERSION,
                iconPath = "blast.png",
                minimumSupportedVersion = SupportedPlatformVersion.V5_8),
        descriptor = @PluginDescriptor(
                name = "Blast",
                description = "Standoff distances for bomb threats, "
                        + "military ordnance and nuclear detonations",
                longDescription = "Bomb threats (DHS/DOJ Bomb Threat "
                        + "Stand-Off Card), military ordnance (ATP 4-32.2) "
                        + "and a nuclear detonation reference (FEMA, "
                        + "Glasstone & Dolan): rings and fallout on the "
                        + "globe, cards, reports and reference.",
                vendor = "S2 Underground"))
@SuppressWarnings("UnusedDeclaration")
public class BlastTakxPlugin extends AAppPlugin implements SwingPane.Host {

    static final String FAMILY = "com.s2underground.blast";
    static final String VERSION = "1.2.1";

    private static final Logger LOG = LoggerFactory.getLogger(
            BlastTakxPlugin.class);

    /** TAK-X's position providers (optional: absent = no position). */
    @Inject
    private Instance<ILocationProviderManager> locations;
    /** For the incident markers (optional too). */
    @Inject
    private Instance<IPointDropperService> pointDropper;
    @Inject
    private Instance<IMapObjectManager> mapObjects;
    /** Sending and receiving CoT, and the contact list (optional too). */
    @Inject
    private Instance<ICotEventSender> cotSender;
    @Inject
    private Instance<ICommonCommoManager> commo;
    @Inject
    private Instance<IIndividualContactManager> contacts;
    /** This device's callsign and uid in TAK-X. */
    @Inject
    @CallSign
    private Instance<String> callsign;
    @Inject
    @NodeUUID
    private Instance<String> nodeUid;

    private MemoryPorts ports;
    private BlastApp app;
    private SwingPane pane;
    private TakxShapes shapes;
    private TakxLabels labels;
    private JPanel panel;
    private final JLabel status = new JLabel(" ");
    private Timer timer;
    private IGlobeDelegate globe;
    private IMapObjectSelectListener selectListener;
    private MouseAdapter clickListener;
    private SwingPane.Picked picking;
    private TakxReceiver receiver;
    private ICommonCommoManager listeningTo;
    private IMapObjectManager watching;
    private IMapObjectManagerListener removals;

    @Override
    public void start() {
        SwingUtilities.invokeLater(this::setupUI);
    }

    @Override
    public void stop() {
        SwingUtilities.invokeLater(this::teardownUI);
    }

    @Override
    public void shutdown() {
        panel = null;
    }

    // ------------------------------------------------------------------
    // Setup

    private void setupUI() {
        try {
            ports = new MemoryPorts(new File(System.getProperty("user.home"),
                    ".blast-takx"), this::toast);
            ports.defaultCallsign = takxCallsign();
            ports.setPositionSource(this::ownPosition);
            TakxShare share = new TakxShare(ports, optional(cotSender),
                    optional(contacts), new TakxShare.Hooks() {
                        @Override
                        public CotEvents.Sender me() {
                            ports.defaultCallsign = takxCallsign();
                            return new CotEvents.Sender(deviceUid(),
                                    ports.callsign());
                        }

                        @Override
                        public boolean labels() {
                            return app.ringLabels();
                        }

                        @Override
                        public void toast(String s) {
                            BlastTakxPlugin.this.toast(s);
                        }

                        @Override
                        public Window owner() {
                            return BlastTakxPlugin.this.owner();
                        }
                    });
            app = new BlastApp(ports, share, ports, ports, ports, ports,
                    ports, ports, mapControl());
            shapes = new TakxShapes(delegate.getShapeService(), ports,
                    optional(pointDropper), optional(mapObjects), delegate);
            ports.changed = () -> SwingUtilities.invokeLater(this::redraw);
            pane = new SwingPane(app, this);

            panel = new JPanel(new BorderLayout());
            panel.setBackground(SwingScreen.BG);
            panel.add(pane.component());
            status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            status.setOpaque(true);
            status.setBackground(new Color(0x111111));
            status.setForeground(SwingScreen.TEXT);
            panel.add(status, BorderLayout.SOUTH);

            ComponentDescriptor d = new ComponentDescriptorBuilder(panel,
                    "Blast")
                    .setIcon(getClass().getClassLoader().getResource(
                            "blast.png"))
                    .setComponentPosition(ComponentPosition.RIGHT)
                    .build();
            delegate.getUISpaceManager().addComponent(d, panel);
            delegate.getUISpaceManager().showComponent(panel);

            globe = delegate.getGlobeManager().getPrimaryGlobeDelegate();
            if (globe != null)
                labels = new TakxLabels(globe, ports, app::ringLabels);
            listenToGlobe();
            listenToNetwork();
            listenForDeletes();
            timer = new Timer(3000, e -> {
                try {
                    checkMarkers();
                    pane.refresh();
                } catch (RuntimeException | LinkageError ex) {
                    LOG.warn("refresh failed", ex);
                }
            });
            timer.start();
        } catch (RuntimeException | LinkageError e) {
            // LinkageError: a TAK-X version without an API Blast uses.
            LOG.error("Blast could not start", e);
            if (panel != null)
                toast("Blast could not start fully: see takx.log.");
        }
    }

    private void teardownUI() {
        try {
            if (timer != null)
                timer.stop();
            if (listeningTo != null && receiver != null)
                listeningTo.removeCotEventListener(receiver);
            listeningTo = null;
            if (watching != null && removals != null)
                watching.removeMapObjectManagerListener(removals);
            watching = null;
            stopPick();
            if (globe != null) {
                if (selectListener != null)
                    globe.removeSelectListener(selectListener);
                gov.nasa.worldwind.WorldWindow w = window();
                if (clickListener != null && w != null)
                    w.getInputHandler().removeMouseListener(clickListener);
            }
            if (shapes != null)
                shapes.clear();
            if (labels != null)
                labels.remove();
            if (panel != null)
                delegate.getUISpaceManager().removeComponent(panel);
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("Blast did not stop cleanly", e);
        }
    }

    /** Bring the globe's shapes, markers and labels up to date. */
    private void redraw() {
        try {
            shapes.sync();
            if (labels != null)
                labels.refresh();
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("could not draw the incidents", e);
        }
    }

    /** Clicks on the globe: place (when the core asked) or open a card. */
    private void listenToGlobe() {
        if (globe == null)
            return;
        selectListener = new IMapObjectSelectListener() {
            @Override
            public void selected(MapObjectSelectEvent e) {
                try {
                    if (picking != null || !e.isLeftClick()
                            || !e.hasMapObjects())
                        return;
                    for (IMapObject o : e.getMapObjects()) {
                        String uid = shapes.incidentOf(o);
                        if (uid != null) {
                            SwingUtilities.invokeLater(() -> pane.guarded(
                                    "open " + uid, () -> pane.open(uid)));
                            return;
                        }
                    }
                } catch (RuntimeException | LinkageError ex) {
                    // Never throw into TAK-X's own event handling.
                    LOG.warn("globe selection not handled", ex);
                }
            }
        };
        globe.addSelectListener(selectListener);
        clickListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    if (picking == null
                            || e.getButton() != MouseEvent.BUTTON1)
                        return;
                    gov.nasa.worldwind.WorldWindow w = window();
                    gov.nasa.worldwind.geom.Position p = w == null ? null
                            : w.getCurrentPosition();
                    SwingPane.Picked done = picking;
                    stopPick();
                    e.consume();
                    done.at(p == null ? null : PositionSource.Fix.at(
                            p.getLatitude().degrees, p.getLongitude().degrees),
                            false);
                } catch (RuntimeException | LinkageError ex) {
                    LOG.warn("globe click not handled", ex);
                    stopPick();
                }
            }
        };
        gov.nasa.worldwind.WorldWindow w = window();
        if (w != null)
            w.getInputHandler().addMouseListener(clickListener);
    }

    /**
     * Deleting an incident's marker in TAK-X deletes the incident on this
     * device (its rings, outlines and labels too), as on ATAK.
     */
    private void listenForDeletes() {
        IMapObjectManager m = optional(mapObjects);
        if (m == null)
            return;
        removals = new IMapObjectManagerListener() {
            @Override
            public void clientMapObjectAdded(IMapObject o) {
            }

            @Override
            public void clientMapObjectRemoved(IMapObject o) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        if (shapes == null || !shapes.isIncidentMarker(o))
                            return;
                        // Check once TAK-X has finished removing it.
                        Timer t = new Timer(500, e -> checkMarkers());
                        t.setRepeats(false);
                        t.start();
                    } catch (RuntimeException | LinkageError ex) {
                        LOG.warn("removal not handled", ex);
                    }
                });
            }

            @Override
            public void mapObjectPartialDelete(IMapObject o, long t) {
            }
        };
        m.addMapObjectManagerListener(removals);
        watching = m;
    }

    /**
     * Delete the incidents whose marker was deleted in TAK-X (checked
     * every refresh, and right after TAK-X reports a removal).
     */
    private void checkMarkers() {
        try {
            if (shapes == null)
                return;
            for (String uid : shapes.deletedMarkers()) {
                MemoryPorts.Stored s = ports.stored(uid);
                LOG.info("marker of {} deleted in TAK-X: deleting the "
                        + "incident", uid);
                shapes.forgetMarker(uid);
                ports.delete(uid);
                if (s != null)
                    toast("Deleted " + s.name + " (its marker was "
                            + "deleted).");
            }
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("could not check the incident markers", e);
        }
    }

    /** Incidents other devices send (see TakxReceiver). */
    private void listenToNetwork() {
        ICommonCommoManager c = optional(commo);
        if (c == null) {
            LOG.warn("no CoT listener: received incidents won't show");
            return;
        }
        receiver = new TakxReceiver(ports);
        c.addCotEventListener(receiver);
        listeningTo = c;
    }

    /** TAK-X's callsign, or "TAK-X". */
    private String takxCallsign() {
        String c = optional(callsign);
        return c == null || c.trim().isEmpty() ? "TAK-X" : c.trim();
    }

    /**
     * This device's uid as other TAK clients know it; without one from
     * TAK-X, one made up once and kept in the settings.
     */
    private String deviceUid() {
        String u = optional(nodeUid);
        if (u != null && !u.trim().isEmpty())
            return u.trim();
        u = ports.getString("device_uid", null);
        if (u == null) {
            u = "TAKX-BLAST-" + UUID.randomUUID();
            ports.putString("device_uid", u);
        }
        return u;
    }

    /**
     * The globe's WorldWind window, as WorldWind's own interface (TAK-X's
     * type also extends JOGL types this plugin doesn't compile against).
     */
    private gov.nasa.worldwind.WorldWindow window() {
        Object w = globe == null ? null : globe.getWorldWindow();
        return w instanceof gov.nasa.worldwind.WorldWindow
                ? (gov.nasa.worldwind.WorldWindow) w : null;
    }

    /** An injected service, or null if TAK-X doesn't offer it. */
    private static <T> T optional(Instance<T> i) {
        try {
            return i != null && i.isResolvable() ? i.get() : null;
        } catch (RuntimeException e) {
            LOG.warn("service not available", e);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Ports TAK-X provides

    /** TAK-X's current position (GPS, fixed or none). */
    private PositionSource.Fix ownPosition() {
        try {
            if (locations == null || !locations.isResolvable())
                return null;
            GeoPointMetaData m = locations.get().getCurrentLocation();
            GeoPoint gp = m == null ? null : m.get();
            if (gp == null || !gp.isValid() || (gp.getLatitude() == 0
                    && gp.getLongitude() == 0))
                return null;
            return PositionSource.Fix.at(gp.getLatitude(),
                    gp.getLongitude());
        } catch (RuntimeException e) {
            LOG.debug("no position from TAK-X", e);
            return null; // the cards say there is no position
        }
    }

    private MapControl mapControl() {
        return new MapControl() {
            @Override
            public PositionSource.Fix center() {
                try {
                    if (globe == null)
                        return null;
                    GeoPoint c = globe.getCenterLocation();
                    return c == null || !c.isValid() ? null
                            : PositionSource.Fix.at(c.getLatitude(),
                                    c.getLongitude());
                } catch (RuntimeException | LinkageError e) {
                    LOG.warn("no globe centre", e);
                    return null;
                }
            }

            @Override
            public String mgrs(double lat, double lon) {
                try {
                    return new MGRSPoint(lat, lon).getFormattedString();
                } catch (RuntimeException | LinkageError e) {
                    // TAK-X's converter missing or failing: Blast's own
                    // (the same grid within 2 cm ties).
                    return Mgrs.of(lat, lon);
                }
            }

            @Override
            public double magneticToTrue(double lat, double lon,
                    double magneticDeg) {
                double decl;
                try {
                    decl = new GeomagneticField((float) lat, (float) lon,
                            0f, System.currentTimeMillis()).getDeclination();
                } catch (RuntimeException | LinkageError e) {
                    // Blast's own World Magnetic Model 2025.
                    decl = Wmm.declination(lat, lon, 0, Wmm.decimalYear(
                            System.currentTimeMillis()));
                }
                double t = (magneticDeg + decl) % 360;
                return t < 0 ? t + 360 : t;
            }
        };
    }

    // ------------------------------------------------------------------
    // SwingPane.Host

    @Override
    public void error(String what, Throwable t) {
        LOG.error("Blast error in {}", what, t);
    }

    @Override
    public void toast(String s) {
        SwingUtilities.invokeLater(() -> status.setText(new SimpleDateFormat(
                "HH:mm:ss", Locale.US).format(new Date()) + "  " + s));
    }

    @Override
    public void fly(String uid) {
        MemoryPorts.Stored s = ports.stored(uid);
        if (s != null && globe != null)
            globe.goTo(s.lat, s.lon);
    }

    @Override
    public void labelsChanged() {
        if (labels != null)
            SwingUtilities.invokeLater(labels::refresh);
    }

    @Override
    public void startPick(String prompt, SwingPane.Picked p) {
        picking = p;
        try {
            delegate.getGlobeManager().showGlobeMessage(prompt);
        } catch (RuntimeException e) {
            toast(prompt);
        }
    }

    @Override
    public boolean stopPick() {
        if (picking == null)
            return false;
        picking = null;
        try {
            delegate.getGlobeManager().hideGlobeMessage();
        } catch (RuntimeException e) {
            // no message shown
        }
        return true;
    }

    /**
     * Dialogs belong to TAK-X's main window, as TAK-X's own dialogs do
     * (AStandardDialog uses the first frame), not to whatever window the
     * docked panel sits in.
     */
    @Override
    public Window owner() {
        Window w = panel == null ? null
                : SwingUtilities.getWindowAncestor(panel);
        Window main = null;
        for (java.awt.Frame f : java.awt.Frame.getFrames())
            if (f.isShowing()) {
                main = f;
                break;
            }
        return main != null ? main : w;
    }
}
