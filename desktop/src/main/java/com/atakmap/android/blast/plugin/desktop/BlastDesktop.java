package com.atakmap.android.blast.plugin.desktop;

import com.atakmap.android.blast.plugin.core.NucText;
import com.atakmap.android.blast.plugin.core.app.BlastApp;
import com.atakmap.android.blast.plugin.core.overlay.OverlayShape;
import com.atakmap.android.blast.plugin.core.overlay.Overlays;
import com.atakmap.android.blast.plugin.core.port.MapControl;
import com.atakmap.android.blast.plugin.core.port.PositionSource;
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
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 * Blast on a desktop, without ATAK: the core behind the shared Swing pane
 * ({@link SwingPane}) and a plain offline map. Only the window, the map
 * and the ports are this host's own.
 */
public final class BlastDesktop implements SwingPane.Host {

    private final MemoryPorts ports;
    private final BlastApp app;
    private final MapPanel map;
    final SwingPane pane;
    final JFrame frame = new JFrame("Blast (desktop test host)");
    private final JLabel status = new JLabel(" ");

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
        // The map starts on the National Mall (no GPS assumed).
        double lat = Double.parseDouble(ports.getString("map.lat", "38.8895"));
        double lon = Double.parseDouble(ports.getString("map.lon", "-77.0353"));
        MapPanel[] m = new MapPanel[1];
        MapControl mc = new MapControl() {
            @Override
            public PositionSource.Fix center() {
                return m[0].center();
            }

            @Override
            public String mgrs(double la, double lo) {
                // No MGRS library here: the core falls back to lat / lon.
                throw new UnsupportedOperationException("no MGRS");
            }

            @Override
            public double magneticToTrue(double la, double lo, double deg) {
                // No declination model here: the core asks for true bearings.
                throw new UnsupportedOperationException("no declination");
            }
        };
        app = new BlastApp(ports, ports, ports, ports, ports, ports, ports,
                ports, mc);
        map = new MapPanel(ports, lat, lon, app::ringLabels,
                this::openIncident);
        m[0] = map;
        ports.changed = map::repaint;
        pane = new SwingPane(app, this);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, map,
                pane.component());
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
            }
        });
        frame.setSize(1400, 900);
        new Timer(3000, e -> {
            pane.refresh();
            map.repaint();
        }).start();
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
