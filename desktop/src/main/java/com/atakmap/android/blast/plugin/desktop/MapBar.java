package com.atakmap.android.blast.plugin.desktop;

import com.atakmap.android.blast.plugin.core.Mgrs;
import com.atakmap.android.blast.plugin.core.port.PositionSource;
import com.atakmap.android.blast.plugin.desktop.map.TileSource;
import com.atakmap.android.blast.plugin.desktop.map.TileStore;
import com.atakmap.android.blast.plugin.swing.MemoryPorts;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * The bar above the map (which map, zoom, go to a place, your position, map
 * files and the tile cache) and the map's right-click menu. Choices are
 * kept in the desktop's settings.
 */
final class MapBar {

    private static final String SEP = "\u001F";

    private final MemoryPorts ports;
    private final MapPanel map;
    private final TileStore tiles;
    private final Component owner;
    private final Consumer<String> toast;
    private final JToolBar bar = new JToolBar();
    private final DefaultComboBoxModel<TileSource> model =
            new DefaultComboBoxModel<>();
    private final JComboBox<TileSource> which = new JComboBox<>(model);
    private boolean quiet;

    MapBar(MemoryPorts ports, MapPanel map, TileStore tiles, Component owner,
            Consumer<String> toast) {
        this.ports = ports;
        this.map = map;
        this.tiles = tiles;
        this.owner = owner;
        this.toast = toast;
        bar.setFloatable(false);
        bar.add(new JLabel(" Map "));
        which.setMaximumSize(which.getPreferredSize());
        which.setToolTipText("Which map to show");
        bar.add(which);
        bar.addSeparator();
        bar.add(button("+", "Zoom in", () -> map.zoomBy(1)));
        bar.add(button("−", "Zoom out", () -> map.zoomBy(-1)));
        bar.addSeparator();
        bar.add(button("Go to…", "Centre the map on a grid reference or "
                + "latitude, longitude", this::goTo));
        bar.add(button("My position…", "Type your position (this "
                + "computer has no GPS)", this::myPosition));
        JButton more = new JButton("Maps ▾");
        more.setToolTipText("Add a map, open an MBTiles file, the map cache");
        more.addActionListener(e -> moreMenu().show(more, 0, more
                .getHeight()));
        bar.add(more);
        reload(ports.getString("map.source", TileSource.OSM.id));
        which.addActionListener(e -> {
            if (quiet)
                return;
            TileSource s = (TileSource) which.getSelectedItem();
            map.setSource(s);
            ports.putString("map.source", s == null ? "none" : s.id);
        });
        map.menu = this::pointMenu;
    }

    JToolBar component() {
        return bar;
    }

    private static JButton button(String text, String tip, Runnable r) {
        JButton b = new JButton(text);
        b.setToolTipText(tip);
        b.addActionListener(e -> r.run());
        return b;
    }

    // ------------------------------------------------------------------
    // The list of maps

    /** Rebuild the list from settings and select {@code id}. */
    private void reload(String id) {
        quiet = true;
        model.removeAllElements();
        for (TileSource s : TileSource.builtIn())
            model.addElement(s);
        for (TileSource s : customs())
            model.addElement(s);
        List<String> dead = new ArrayList<>();
        for (String path : list("map.mbtiles")) {
            try {
                model.addElement(TileStore.openMbtiles(new File(path)));
            } catch (IOException | RuntimeException e) {
                dead.add(path);
            }
        }
        TileSource pick = TileSource.NONE;
        for (int i = 0; i < model.getSize(); i++)
            if (model.getElementAt(i).id.equals(id))
                pick = model.getElementAt(i);
        which.setSelectedItem(pick);
        which.setMaximumSize(which.getPreferredSize());
        quiet = false;
        map.setSource(pick);
        if (!dead.isEmpty())
            toast.accept("Map file not found: " + new File(dead.get(0))
                    .getName());
    }

    private List<String> list(String key) {
        List<String> l = new ArrayList<>();
        for (String s : ports.getString(key, "").split("\n"))
            if (!s.isEmpty())
                l.add(s);
        return l;
    }

    private void putList(String key, List<String> l) {
        ports.putString(key, String.join("\n", l));
    }

    private List<TileSource> customs() {
        List<TileSource> out = new ArrayList<>();
        for (String line : list("map.custom")) {
            String[] p = line.split(SEP, -1);
            if (p.length < 4)
                continue;
            try {
                out.add(TileSource.custom(p[0], p[1], p[2], Integer.parseInt(
                        p[3])));
            } catch (RuntimeException e) {
                // skip a bad entry
            }
        }
        return out;
    }

    private JPopupMenu moreMenu() {
        JPopupMenu m = new JPopupMenu();
        JMenuItem add = new JMenuItem("Add a tile address (XYZ)…");
        add.addActionListener(e -> addCustom());
        m.add(add);
        JMenuItem mb = new JMenuItem("Open an MBTiles file…");
        mb.addActionListener(e -> openMbtiles());
        m.add(mb);
        TileSource cur = map.source();
        boolean removable = cur.kind == TileSource.Kind.MBTILES
                || cur.id.startsWith("xyz-");
        JMenuItem rm = new JMenuItem("Remove “" + cur.name
                + "” from the list");
        rm.setEnabled(removable);
        rm.addActionListener(e -> remove(cur));
        m.add(rm);
        m.addSeparator();
        JMenuItem cache = new JMenuItem("Map cache…");
        cache.addActionListener(e -> cacheDialog());
        m.add(cache);
        return m;
    }

    private void addCustom() {
        JTextField name = new JTextField(24), url = new JTextField(40),
                attr = new JTextField(24);
        JSpinner max = new JSpinner(new SpinnerNumberModel(18, 1, 22, 1));
        JPanel p = form(new String[] { "Name", "Address", "Credit shown",
                "Highest zoom" }, new Component[] { name, url, attr, max });
        JLabel help = new JLabel("<html>A Web Mercator tile address with {z},"
                + " {x} and {y}, for example<br>https://tiles.example.org/{z}/"
                + "{x}/{y}.png<br>Use only services whose terms allow it."
                + "</html>");
        Object[] msg = { help, p };
        while (true) {
            if (JOptionPane.showConfirmDialog(owner, msg, "Add a map",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                    != JOptionPane.OK_OPTION)
                return;
            try {
                TileSource s = TileSource.custom(name.getText(), url
                        .getText(), attr.getText(), (Integer) max.getValue());
                List<String> l = list("map.custom");
                l.add(String.join(SEP, s.name, s.template, s.attribution,
                        Integer.toString(s.maxZoom)));
                putList("map.custom", l);
                ports.putString("map.source", s.id);
                reload(s.id);
                return;
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(owner, ex.getMessage(),
                        "Add a map", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void openMbtiles() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("MBTiles maps",
                "mbtiles"));
        if (fc.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION)
            return;
        File f = fc.getSelectedFile();
        try {
            TileSource s = TileStore.openMbtiles(f);
            List<String> l = list("map.mbtiles");
            if (!l.contains(f.getAbsolutePath()))
                l.add(f.getAbsolutePath());
            putList("map.mbtiles", l);
            ports.putString("map.source", s.id);
            reload(s.id);
            if (s.bounds != null) {
                double[] c = { map.center().lat, map.center().lon };
                boolean inside = c[1] >= s.bounds[0] && c[1] <= s.bounds[2]
                        && c[0] >= s.bounds[1] && c[0] <= s.bounds[3];
                if (!inside)
                    map.frameBox(s.bounds);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(owner, e.getMessage(),
                    "Open an MBTiles file", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void remove(TileSource s) {
        if (s.kind == TileSource.Kind.MBTILES) {
            List<String> l = list("map.mbtiles");
            l.remove(s.file.getAbsolutePath());
            putList("map.mbtiles", l);
        } else {
            List<String> l = list("map.custom");
            l.removeIf(line -> {
                String[] p = line.split(SEP, -1);
                return p.length > 1 && s.template.equals(p[1]);
            });
            putList("map.custom", l);
        }
        reload(TileSource.OSM.id);
    }

    private void cacheDialog() {
        long cap = cacheCapMb(ports);
        JSpinner size = new JSpinner(new SpinnerNumberModel((int) cap, 50,
                20000, 50));
        String used = String.format(Locale.US, "%.0f MB", tiles.cacheBytes()
                / 1e6);
        JPanel p = form(new String[] { "In use", "Keep at most (MB)" },
                new Component[] { new JLabel(used), size });
        Object[] opts = { "OK", "Empty the cache", "Cancel" };
        int r = JOptionPane.showOptionDialog(owner, new Object[] {
                new JLabel("<html>Online map tiles are kept on this computer"
                        + "<br>so places you have viewed show offline.</html>"),
                p }, "Map cache", JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, opts, opts[0]);
        if (r == 0) {
            int mb = (Integer) size.getValue();
            ports.putString("map.cacheMB", Integer.toString(mb));
            tiles.setCap(mb * 1_000_000L);
        } else if (r == 1) {
            tiles.clearCache();
            toast.accept("Map cache emptied.");
        }
    }

    static long cacheCapMb(MemoryPorts ports) {
        try {
            return Math.max(50, Long.parseLong(ports.getString("map.cacheMB",
                    "500")));
        } catch (NumberFormatException e) {
            return 500;
        }
    }

    private static JPanel form(String[] labels, Component[] fields) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < labels.length; i++) {
            c.gridx = 0;
            c.gridy = i;
            c.fill = GridBagConstraints.NONE;
            p.add(new JLabel(labels[i]), c);
            c.gridx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            p.add(fields[i], c);
        }
        return p;
    }

    // ------------------------------------------------------------------
    // Places

    /**
     * A typed position: an MGRS reference, or latitude and longitude in
     * decimal degrees ("38.889, -77.035"). Null if it is neither.
     */
    static double[] parsePlace(String s) {
        if (s == null)
            return null;
        String t = s.trim();
        if (t.isEmpty())
            return null;
        String[] p = t.split("[,;\\s]+");
        if (p.length == 2) {
            try {
                double lat = Double.parseDouble(p[0]),
                        lon = Double.parseDouble(p[1]);
                if (lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180)
                    return new double[] { lat, lon };
                return null;
            } catch (NumberFormatException e) {
                // not decimal degrees
            }
        }
        try {
            // The middle of the square named, not its corner.
            return centre(Mgrs.parse(t), t);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The centre of an MGRS square from its south-west corner. */
    private static double[] centre(double[] sw, String ref) {
        String compact = ref.replaceAll("[\\s\\u200E\\u200F\\u00A0]", "");
        int i = 0;
        while (i < compact.length() && Character.isDigit(compact.charAt(i)))
            i++;
        String rest = compact.substring(Math.min(compact.length(), i + 3));
        int d = rest.length() / 2;
        double size = Math.pow(10, 5 - d);
        if (size <= 1)
            return sw;
        // Half a square north and east, near enough at these sizes.
        double dLat = size / 2 / 111_320.0;
        double dLon = size / 2 / (111_320.0 * Math.max(0.01, Math.cos(Math
                .toRadians(sw[0]))));
        return new double[] { sw[0] + dLat, sw[1] + dLon };
    }

    private String ask(String title, String text, String initial) {
        Object r = JOptionPane.showInputDialog(owner, text, title,
                JOptionPane.PLAIN_MESSAGE, null, null, initial);
        return r == null ? null : r.toString();
    }

    private void goTo() {
        String hint = "Grid reference (e.g. 18S UJ 23371 06519) or latitude, "
                + "longitude (e.g. 38.8895, -77.0353)";
        String s = ask("Go to", hint, "");
        while (s != null) {
            double[] ll = parsePlace(s);
            if (ll != null) {
                map.goTo(ll[0], ll[1], Math.max(map.zoom(), 14));
                return;
            }
            s = ask("Go to", "Not a grid reference or latitude, longitude.\n"
                    + hint, s);
        }
    }

    private void myPosition() {
        PositionSource.Fix f = ports.fix();
        String now = f == null ? "" : Mgrs.of(f.lat, f.lon);
        JTextField box = new JTextField(now, 28);
        Object[] msg = { new JLabel("<html>Your position, as a grid reference"
                + " or latitude, longitude.<br>Blast uses it for “Place "
                + "at my position” and distances to you.</html>"), box };
        Object[] opts = f == null ? new Object[] { "Set", "Cancel" }
                : new Object[] { "Set", "Clear my position", "Cancel" };
        while (true) {
            int r = JOptionPane.showOptionDialog(owner, msg, "My position",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                    null, opts, opts[0]);
            if (r == 0) {
                double[] ll = parsePlace(box.getText());
                if (ll == null) {
                    JOptionPane.showMessageDialog(owner, "Not a grid reference"
                            + " or latitude, longitude.", "My position",
                            JOptionPane.WARNING_MESSAGE);
                    continue;
                }
                setMe(ll[0], ll[1]);
                map.goTo(ll[0], ll[1], Double.NaN);
            } else if (r == 1 && f != null) {
                setMe(Double.NaN, Double.NaN);
            }
            return;
        }
    }

    private void setMe(double lat, double lon) {
        if (Double.isNaN(lat)) {
            ports.setGps(null);
            ports.putString("me", "");
        } else {
            ports.setGps(new PositionSource.Fix(lat, lon, Double.NaN));
            ports.putString("me", lat + "," + lon);
        }
        map.repaint();
    }

    /** Restore the position typed last time. */
    static void restoreMe(MemoryPorts ports) {
        String[] p = ports.getString("me", "").split(",");
        if (p.length != 2)
            return;
        try {
            ports.setGps(new PositionSource.Fix(Double.parseDouble(p[0]),
                    Double.parseDouble(p[1]), Double.NaN));
        } catch (NumberFormatException e) {
            // none
        }
    }

    private void pointMenu(MouseEvent e) {
        double[] ll = map.latLon(e.getX(), e.getY());
        JPopupMenu m = new JPopupMenu();
        String grid;
        try {
            grid = Mgrs.of(ll[0], ll[1]);
        } catch (IllegalArgumentException ex) {
            grid = String.format(Locale.US, "%.5f, %.5f", ll[0], ll[1]);
        }
        JMenuItem title = new JMenuItem(grid);
        title.setEnabled(false);
        m.add(title);
        m.addSeparator();
        JMenuItem set = new JMenuItem("Set my position here");
        set.addActionListener(a -> setMe(ll[0], ll[1]));
        m.add(set);
        JMenuItem clear = new JMenuItem("Clear my position");
        clear.setEnabled(ports.hasGps());
        clear.addActionListener(a -> setMe(Double.NaN, Double.NaN));
        m.add(clear);
        m.addSeparator();
        String g2 = grid;
        JMenuItem copy = new JMenuItem("Copy grid reference");
        copy.addActionListener(a -> copy(g2));
        m.add(copy);
        String dd = String.format(Locale.US, "%.6f, %.6f", ll[0], ll[1]);
        JMenuItem copyLl = new JMenuItem("Copy latitude, longitude");
        copyLl.addActionListener(a -> copy(dd));
        m.add(copyLl);
        JMenuItem centre = new JMenuItem("Centre the map here");
        centre.addActionListener(a -> map.goTo(ll[0], ll[1], Double.NaN));
        m.add(centre);
        m.show(map, e.getX(), e.getY());
    }

    private void copy(String s) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new StringSelection(s), null);
            toast.accept("Copied " + s);
        } catch (IllegalStateException e) {
            toast.accept("Couldn't copy.");
        }
    }
}
