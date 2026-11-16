package com.s2underground.blast.takx;

import com.atakmap.android.blast.plugin.core.app.BlastApp;
import com.atakmap.android.blast.plugin.core.port.MapControl;
import com.atakmap.android.blast.plugin.core.port.PositionSource;
import com.atakmap.android.blast.plugin.swing.MemoryPorts;
import com.atakmap.android.blast.plugin.swing.SwingPane;
import com.atakmap.android.blast.plugin.swing.SwingScreen;
import com.atakmap.coremap.maps.conversion.GeomagneticField;
import com.atakmap.coremap.maps.coords.MGRSPoint;

import gov.tak.api.location.ILocationProviderManager;
import gov.tak.api.maps.coords.GeoPoint;
import gov.tak.api.maps.coords.GeoPointMetaData;
import gov.takx.api.globe.IGlobeDelegate;
import gov.takx.api.mapobject.IMapObject;
import gov.takx.api.mapobject.IMapObjectManager;
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

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Blast for TAK-X: the same core and Swing pane as the desktop host,
 * docked on the right of TAK-X, with incidents drawn on the TAK-X globe
 * as TAK-X shapes. This first version keeps incidents on this computer
 * (nothing is sent yet).
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
    static final String VERSION = "0.1.3";

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

    private MemoryPorts ports;
    private BlastApp app;
    private SwingPane pane;
    private TakxShapes shapes;
    private JPanel panel;
    private final JLabel status = new JLabel(" ");
    private Timer timer;
    private IGlobeDelegate globe;
    private IMapObjectSelectListener selectListener;
    private MouseAdapter clickListener;
    private SwingPane.Picked picking;

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
            ports.defaultCallsign = "TAK-X";
            ports.setPositionSource(this::ownPosition);
            app = new BlastApp(ports, ports, ports, ports, ports, ports,
                    ports, ports, mapControl());
            shapes = new TakxShapes(delegate.getShapeService(), ports,
                    optional(pointDropper), optional(mapObjects), delegate);
            ports.changed = () -> SwingUtilities.invokeLater(shapes::sync);
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
            listenToGlobe();
            timer = new Timer(3000, e -> pane.refresh());
            timer.start();
        } catch (RuntimeException e) {
            LOG.error("Blast could not start", e);
        }
    }

    private void teardownUI() {
        try {
            if (timer != null)
                timer.stop();
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
            if (panel != null)
                delegate.getUISpaceManager().removeComponent(panel);
        } catch (RuntimeException e) {
            LOG.warn("Blast did not stop cleanly", e);
        }
    }

    /** Clicks on the globe: place (when the core asked) or open a card. */
    private void listenToGlobe() {
        if (globe == null)
            return;
        selectListener = new IMapObjectSelectListener() {
            @Override
            public void selected(MapObjectSelectEvent e) {
                if (picking != null || !e.isLeftClick() || !e.hasMapObjects())
                    return;
                for (IMapObject o : e.getMapObjects()) {
                    String uid = shapes.incidentOf(o);
                    if (uid != null) {
                        SwingUtilities.invokeLater(() -> pane.open(uid));
                        return;
                    }
                }
            }
        };
        globe.addSelectListener(selectListener);
        clickListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (picking == null || e.getButton() != MouseEvent.BUTTON1)
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
            }
        };
        gov.nasa.worldwind.WorldWindow w = window();
        if (w != null)
            w.getInputHandler().addMouseListener(clickListener);
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
        if (locations == null || !locations.isResolvable())
            return null;
        GeoPointMetaData m = locations.get().getCurrentLocation();
        GeoPoint gp = m == null ? null : m.get();
        if (gp == null || !gp.isValid() || (gp.getLatitude() == 0
                && gp.getLongitude() == 0))
            return null;
        return PositionSource.Fix.at(gp.getLatitude(), gp.getLongitude());
    }

    private MapControl mapControl() {
        return new MapControl() {
            @Override
            public PositionSource.Fix center() {
                if (globe == null)
                    return null;
                GeoPoint c = globe.getCenterLocation();
                return c == null || !c.isValid() ? null
                        : PositionSource.Fix.at(c.getLatitude(),
                                c.getLongitude());
            }

            @Override
            public String mgrs(double lat, double lon) {
                return new MGRSPoint(lat, lon).getFormattedString();
            }

            @Override
            public double magneticToTrue(double lat, double lon,
                    double magneticDeg) {
                float decl = new GeomagneticField((float) lat, (float) lon,
                        0f, System.currentTimeMillis()).getDeclination();
                double t = (magneticDeg + decl) % 360;
                return t < 0 ? t + 360 : t;
            }
        };
    }

    // ------------------------------------------------------------------
    // SwingPane.Host

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
        // TAK-X shows shape names in its own way; nothing to redraw yet.
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

    @Override
    public Window owner() {
        return panel == null ? null : SwingUtilities.getWindowAncestor(panel);
    }
}
