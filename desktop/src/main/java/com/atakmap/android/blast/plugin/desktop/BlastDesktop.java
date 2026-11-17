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
    final JFrame frame = new JFrame("Blast " + VERSION + (LABEL.isEmpty()
            ? "" : " (" + LABEL + ": not for release)"));
    private final JLabel status = new JLabel(" ");

    static final String VERSION = "1.2";

    /**
     * FlatLaf's dark look, tuned to the Blast pane's colours, so menus,
     * the bar above the map and dialogs match the pane instead of Swing's
     * old grey Metal look.
     */
    static void look() {
        try {
            java.util.Map<String, String> extra = new java.util.HashMap<>();
            extra.put("@accentColor", "#1565C0");
            extra.put("@background", "#1E1E1E");
            extra.put("@foreground", "#EEEEEE");
            com.formdev.flatlaf.FlatLaf.setGlobalExtraDefaults(extra);
            com.formdev.flatlaf.FlatDarkLaf.setup();
            UIManager.put("Component.arc", 8);
            UIManager.put("Button.arc", 8);
            UIManager.put("TextComponent.arc", 6);
            UIManager.put("ScrollBar.width", 12);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2,
                    2, 2));
            UIManager.put("PopupMenu.borderCornerRadius", 8);
            UIManager.put("TitlePane.unifiedBackground", true);
        } catch (RuntimeException | LinkageError e) {
            try {
                UIManager.setLookAndFeel(UIManager
                        .getCrossPlatformLookAndFeelClassName());
            } catch (Exception ex) {
                // default look
            }
        }
    }
    /** A test build's label from the jar ("test1"), or "". */
    static final String LABEL = label();

    private static String label() {
        try (java.io.InputStream in = BlastDesktop.class.getResourceAsStream(
                "build-label.txt")) {
            return in == null ? "" : new String(in.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (java.io.IOException | RuntimeException e) {
            return "";
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BlastDesktop(new File(
                System.getProperty("user.home"), ".blast-desktop"))
                        .frame.setVisible(true));
    }

    BlastDesktop(File home) {
        look();
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
        javax.swing.JButton about = new javax.swing.JButton("About");
        about.addActionListener(e -> about(home));
        bar.component().add(about);
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
        java.awt.Rectangle screen = java.awt.GraphicsEnvironment
                .getLocalGraphicsEnvironment().getMaximumWindowBounds();
        frame.setSize(Math.min(1400, screen.width), Math.min(900,
                screen.height));
        frame.setLocationRelativeTo(null);
        try {
            frame.setIconImage(javax.imageio.ImageIO.read(BlastDesktop.class
                    .getResource("blast.png")));
        } catch (java.io.IOException | RuntimeException e) {
            // default icon
        }
        mesh.restart();
        new Timer(3000, e -> {
            pane.refresh();
            map.repaint();
        }).start();
    }

    private void about(File home) {
        String html = "<html><body style='width:420px'><h2>Blast " + VERSION
                + " for desktop</h2>" + (LABEL.isEmpty() ? ""
                        : "<p><b>Test build (" + LABEL + "): for testing "
                                + "only, not a release. Please don't pass "
                                + "it on.</b></p>")
                + "<p>S2 Underground. Bomb threat stand-off distances (DHS/DOJ"
                + " card), military ordnance (ATP 4-32.2) and a nuclear "
                + "planning reference, shared with ATAK Blast on the local "
                + "network.</p><p>Blast's distances are minimums, not "
                + "guarantees, and the nuclear mode is a planning and "
                + "training reference, not a prediction of a real event. It "
                + "does not replace professional EOD, bomb squad, emergency "
                + "management or department procedures.</p>"
                + "<p>Your incidents, settings and map cache: "
                + home.getAbsolutePath().replace("&", "&amp;").replace("<",
                        "&lt;")
                + "</p><p>Sources: the Reference tab.</p></body></html>";
        Object[] opts = { "OK", "Third-party notices" };
        java.awt.Image icon = frame.getIconImages().isEmpty() ? null
                : frame.getIconImages().get(0);
        int r = javax.swing.JOptionPane.showOptionDialog(frame, html,
                "About Blast", javax.swing.JOptionPane.DEFAULT_OPTION,
                javax.swing.JOptionPane.PLAIN_MESSAGE, icon == null ? null
                        : new javax.swing.ImageIcon(icon.getScaledInstance(64,
                                64, java.awt.Image.SCALE_SMOOTH)), opts,
                opts[0]);
        if (r == 1)
            notices();
    }

    private void notices() {
        String text;
        try (java.io.InputStream in = BlastDesktop.class.getResourceAsStream(
                "THIRD-PARTY-NOTICES.txt")) {
            text = in == null ? "Not found." : new String(in.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            text = "Not found.";
        }
        javax.swing.JTextArea t = new javax.swing.JTextArea(text, 28, 76);
        t.setEditable(false);
        t.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,
                java.awt.Font.PLAIN, 12));
        t.setCaretPosition(0);
        javax.swing.JOptionPane.showMessageDialog(frame,
                new javax.swing.JScrollPane(t), "Third-party notices",
                javax.swing.JOptionPane.PLAIN_MESSAGE);
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
