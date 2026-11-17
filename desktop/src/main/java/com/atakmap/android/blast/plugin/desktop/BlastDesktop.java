package com.atakmap.android.blast.plugin.desktop;

import com.atakmap.android.blast.plugin.core.NucText;
import com.atakmap.android.blast.plugin.core.app.BlastApp;
import com.atakmap.android.blast.plugin.core.overlay.OverlayShape;
import com.atakmap.android.blast.plugin.core.overlay.Overlays;
import com.atakmap.android.blast.plugin.core.Mgrs;
import com.atakmap.android.blast.plugin.core.Wmm;
import com.atakmap.android.blast.plugin.core.port.MapControl;
import com.atakmap.android.blast.plugin.core.port.PositionSource;
import com.atakmap.android.blast.plugin.desktop.map.TileStore;
import com.atakmap.android.blast.plugin.desktop.net.Mesh;
import com.atakmap.android.blast.plugin.swing.MemoryPorts;
import com.atakmap.android.blast.plugin.swing.SwingPane;
import com.atakmap.android.blast.plugin.swing.SwingScreen;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Window;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 * Blast on a desktop, without ATAK: the core behind the shared Swing pane
 * ({@link SwingPane}) and a tile map (online with a cache, MBTiles files or
 * none). Only the window, the map and the ports are this host's own; MGRS
 * and declination come from the core ({@link Mgrs}, {@link Wmm}).
 */
public final class BlastDesktop implements SwingPane.Host {

    private final MemoryPorts ports;
    private final BlastApp app;
    private final MapPanel map;
    private final TileStore tiles;
    private final Mesh mesh;
    final SwingPane pane;
    final JFrame frame = new JFrame("Blast " + VERSION);
    private final JLabel status = new JLabel(" ");

    static final String VERSION = "1.2";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BlastDesktop(new File(
                System.getProperty("user.home"), ".blast-desktop"))
                        .frame.setVisible(true));
    }

    BlastDesktop(File home) {
        try {
            UIManager.setLookAndFeel(UIManager
                    .getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            // default look
        }
        ports = new MemoryPorts(home, this::toast);
        MapBar.restoreMe(ports);
        // The map starts on the National Mall (no GPS assumed).
        double lat = number(ports.getString("map.lat", ""), 38.8895);
        double lon = number(ports.getString("map.lon", ""), -77.0353);
        double zoom = number(ports.getString("map.zoom", ""), 15);
        MapPanel[] m = new MapPanel[1];
        MapControl mc = new MapControl() {
            @Override
            public PositionSource.Fix center() {
                return m[0].center();
            }

            @Override
            public String mgrs(double la, double lo) {
                return Mgrs.of(la, lo);
            }

            @Override
            public double magneticToTrue(double la, double lo, double deg) {
                double t = deg + Wmm.declination(la, lo, 0,
                        Wmm.decimalYear(System.currentTimeMillis()));
                return ((t % 360) + 360) % 360;
            }
        };
        BlastApp[] ap = new BlastApp[1];
        mesh = new Mesh(ports, new Mesh.Hooks() {
            @Override
            public void toast(String s) {
                BlastDesktop.this.toast(s);
            }

            @Override
            public boolean labels() {
                return ap[0] == null || ap[0].ringLabels();
            }

            @Override
            public void changed() {
                if (pane != null)
                    pane.refresh();
                if (m[0] != null)
                    m[0].repaint();
            }

            @Override
            public List<Mesh.Contact> pick(String what,
                    List<Mesh.Contact> people) {
                return NetDialogs.pick(frame, what, people);
            }
        });
        app = new BlastApp(ports, mesh, mesh, ports, ports, ports, ports,
                ports, mc);
        ap[0] = app;
        MapPanel[] mp = new MapPanel[1];
        tiles = new TileStore(new File(home, "tiles"), MapBar.cacheCapMb(
                ports) * 1_000_000L, () -> SwingUtilities.invokeLater(() -> {
                    if (mp[0] != null)
                        mp[0].repaint();
                }));
        map = new MapPanel(ports, tiles, lat, lon, zoom, app::ringLabels,
                this::openIncident);
        mp[0] = map;
        m[0] = map;
        map.team = mesh::contacts;
        MapBar bar = new MapBar(ports, map, tiles, frame, this::toast);
        bar.component().addSeparator();
        javax.swing.JButton net = new javax.swing.JButton("Network\u2026");
        net.setToolTipText("Share incidents with ATAK devices on this "
                + "network");
        net.addActionListener(e -> NetDialogs.settings(frame, ports, mesh));
        bar.component().add(net);
        javax.swing.JButton people = new javax.swing.JButton("Contacts");
        people.addActionListener(e -> NetDialogs.contacts(frame, mesh));
        bar.component().add(people);
        JPanel mapSide = new JPanel(new BorderLayout());
        mapSide.add(bar.component(), BorderLayout.NORTH);
        mapSide.add(map, BorderLayout.CENTER);
        ports.changed = map::repaint;
        pane = new SwingPane(app, this);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                mapSide, pane.component());
        split.setResizeWeight(1);
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        status.setOpaque(true);
        status.setBackground(new Color(0x111111));
        status.setForeground(SwingScreen.TEXT);
        frame.getContentPane().add(split);
        frame.getContentPane().add(status, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                PositionSource.Fix c = map.center();
                ports.putString("map.lat", Double.toString(c.lat));
                ports.putString("map.lon", Double.toString(c.lon));
                ports.putString("map.zoom", Double.toString(map.zoom()));
                mesh.shutdown();
            }
        });
        frame.setSize(1400, 900);
        mesh.restart();
        new Timer(3000, e -> {
            pane.refresh();
            map.repaint();
        }).start();
    }

    private static double number(String s, double def) {
        try {
            double d = Double.parseDouble(s);
            return Double.isNaN(d) || Double.isInfinite(d) ? def : d;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** A marker was clicked on the map. */
    private void openIncident(String uid) {
        pane.open(uid);
    }

    // ------------------------------------------------------------------
    // SwingPane.Host

    @Override
    public void toast(String s) {
        status.setText(new SimpleDateFormat("HH:mm:ss", Locale.US)
                .format(new Date()) + "  " + s);
    }

    @Override
    public void labelsChanged() {
        map.repaint();
    }

    /** Frame the map on an incident's outer ring. */
    @Override
    public void fly(String uid) {
        MemoryPorts.Stored s = ports.stored(uid);
        if (s == null)
            return;
        List<OverlayShape> shapes = s.nuc != null
                ? Overlays.nucRings(s.nuc) : s.uxo != null
                        ? Overlays.uxo(s.uxo) : Overlays.standoff(s.standoff);
        double r = 100;
        for (OverlayShape sh : shapes)
            if (sh.kind == OverlayShape.Kind.CIRCLE
                    && (sh.id.equals(Overlays.PREF)
                            || sh.id.equals(Overlays.CORDON)
                            || sh.id.equals(NucText.Ring.LDZ.suffix)))
                r = sh.radiusM;
        map.frame(s.lat, s.lon, r);
    }

    @Override
    public void startPick(String prompt, SwingPane.Picked picked) {
        map.startPick(prompt, picked);
    }

    @Override
    public boolean stopPick() {
        return map.stopPick();
    }

    @Override
    public Window owner() {
        return frame;
    }

    MapPanel map() {
        return map;
    }

    MemoryPorts ports() {
        return ports;
    }
}
