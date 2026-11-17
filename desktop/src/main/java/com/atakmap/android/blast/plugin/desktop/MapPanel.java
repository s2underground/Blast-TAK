package com.atakmap.android.blast.plugin.desktop;

import com.atakmap.android.blast.plugin.core.Geo;
import com.atakmap.android.blast.plugin.core.Mgrs;
import com.atakmap.android.blast.plugin.core.overlay.OverlayShape;
import com.atakmap.android.blast.plugin.core.overlay.Overlays;
import com.atakmap.android.blast.plugin.core.overlay.Style;
import com.atakmap.android.blast.plugin.core.port.PositionSource;
import com.atakmap.android.blast.plugin.desktop.map.TileSource;
import com.atakmap.android.blast.plugin.desktop.map.TileStore;
import com.atakmap.android.blast.plugin.desktop.net.Mesh;
import com.atakmap.android.blast.plugin.swing.MemoryPorts;
import com.atakmap.android.blast.plugin.swing.SwingPane;
import com.atakmap.android.blast.plugin.swing.SwingScreen;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.swing.JPanel;

/**
 * The desktop map: Web Mercator tiles (online with a disk cache, an offline
 * MBTiles file, or a plain grid) with every incident drawn from the core's
 * overlay model ({@link Overlays}), the same shapes, colours and line
 * styles the ATAK layer draws; rings are true geodesic circles. Drag to
 * pan, wheel to zoom, click a marker to open its card; when the core asks
 * for a map tap, the next click places.
 */
final class MapPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final double MAX_LAT = 85.05112878;
    /** Metres per pixel at the equator at zoom 0. */
    private static final double MPP0 = 156543.03392804097;
    private static final Color LAND = new Color(0x26302A),
            GRID = new Color(0x3A463E), GRID_TEXT = new Color(0x7F8C84),
            BACK = new Color(0x1C1F1D);
    private static final int CIRCLE_POINTS = 128;

    private final MemoryPorts ports;
    private final TileStore tiles;
    private final BooleanSupplier labels;
    private final Consumer<String> open;
    /** Other devices on the network. */
    java.util.function.Supplier<List<Mesh.Contact>> team =
            java.util.Collections::emptyList;
    /** Right-click: the host's menu for a point. */
    Consumer<MouseEvent> menu = e -> { };
    private TileSource source = TileSource.NONE;
    private double cLat, cLon;
    private double zoom = 15;
    private SwingPane.Picked picking;
    private String prompt;
    private int dragX, dragY;
    private boolean dragged;
    private int mouseX = -1, mouseY = -1;
    private final Map<String, double[][]> circles = new LinkedHashMap<
            String, double[][]>(64, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, double[][]> e) {
            return size() > 400;
        }
    };

    MapPanel(MemoryPorts ports, TileStore tiles, double lat, double lon,
            double zoom, BooleanSupplier labels, Consumer<String> open) {
        this.ports = ports;
        this.tiles = tiles;
        this.cLat = clampLat(lat);
        this.cLon = wrap(lon);
        this.zoom = clampZoom(zoom);
        this.labels = labels;
        this.open = open;
        setBackground(BACK);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragX = e.getX();
                dragY = e.getY();
                dragged = false;
                requestFocusInWindow();
                if (e.isPopupTrigger())
                    menu.accept(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (Math.abs(e.getX() - dragX) + Math.abs(e.getY() - dragY) == 0)
                    return;
                pan(e.getX() - dragX, e.getY() - dragY);
                dragX = e.getX();
                dragY = e.getY();
                dragged = true;
                mouseX = e.getX();
                mouseY = e.getY();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger())
                    menu.accept(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (dragged || e.getButton() != MouseEvent.BUTTON1)
                    return;
                if (e.getClickCount() == 2 && picking == null
                        && markerAt(e.getX(), e.getY()) == null) {
                    zoomAt(e.getX(), e.getY(), 1);
                    return;
                }
                click(e.getX(), e.getY());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                mouseX = -1;
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                zoomAt(e.getX(), e.getY(), -e.getPreciseWheelRotation()
                        * 0.5);
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(m);
    }

    // ------------------------------------------------------------------
    // Source and view

    void setSource(TileSource s) {
        source = s == null ? TileSource.NONE : s;
        zoom = clampZoom(zoom);
        tiles.retry();
        repaint();
    }

    TileSource source() {
        return source;
    }

    double zoom() {
        return zoom;
    }

    private double clampZoom(double z) {
        return Math.max(1, Math.min(z, 21));
    }

    private static double clampLat(double lat) {
        return Math.max(-MAX_LAT, Math.min(MAX_LAT, lat));
    }

    private static double wrap(double lon) {
        double l = ((lon + 180) % 360 + 360) % 360 - 180;
        return l;
    }

    PositionSource.Fix center() {
        return PositionSource.Fix.at(cLat, cLon);
    }

    void goTo(double lat, double lon, double z) {
        cLat = clampLat(lat);
        cLon = wrap(lon);
        if (!Double.isNaN(z))
            zoom = clampZoom(z);
        repaint();
    }

    /** Centre on a point so that a ring of {@code radiusM} fits. */
    void frame(double lat, double lon, double radiusM) {
        int side = Math.max(200, Math.min(getWidth(), getHeight()));
        double mpp = Math.max(0.3, radiusM * 2.4 / side);
        double z = Math.log(MPP0 * Math.cos(Math.toRadians(lat)) / mpp)
                / Math.log(2);
        goTo(lat, lon, z);
    }

    /** Fit a west, south, east, north box. */
    void frameBox(double[] wsen) {
        double lat = (wsen[1] + wsen[3]) / 2, lon = (wsen[0] + wsen[2]) / 2;
        double w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        double dx = Math.abs(wx(wsen[2], 0) - wx(wsen[0], 0));
        double dy = Math.abs(wy(wsen[1], 0) - wy(wsen[3], 0));
        double z = Math.min(Math.log(w / Math.max(dx, 1e-9)), Math.log(h
                / Math.max(dy, 1e-9))) / Math.log(2) - 0.2;
        goTo(lat, lon, z);
    }

    // ------------------------------------------------------------------
    // Projection: Web Mercator world pixels at a zoom

    private static double size(double z) {
        return 256 * Math.pow(2, z);
    }

    private static double wx(double lon, double z) {
        return (lon + 180) / 360 * size(z);
    }

    private static double wy(double lat, double z) {
        double phi = Math.toRadians(clampLat(lat));
        return (1 - Math.log(Math.tan(phi) + 1 / Math.cos(phi)) / Math.PI)
                / 2 * size(z);
    }

    private static double latOf(double y, double z) {
        double n = Math.PI * (1 - 2 * y / size(z));
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    double[] latLon(int x, int y) {
        double px = wx(cLon, zoom) + x - getWidth() / 2.0;
        double py = wy(cLat, zoom) + y - getHeight() / 2.0;
        return new double[] { latOf(py, zoom), wrap(px / size(zoom) * 360
                - 180) };
    }

    private double x(double lon) {
        double d = wx(lon, zoom) - wx(cLon, zoom);
        double s = size(zoom);
        // The copy of the world nearest the centre.
        if (d > s / 2)
            d -= s;
        else if (d < -s / 2)
            d += s;
        return getWidth() / 2.0 + d;
    }

    private double y(double lat) {
        return getHeight() / 2.0 + wy(lat, zoom) - wy(cLat, zoom);
    }

    /** Metres per screen pixel at the centre. */
    double metresPerPixel() {
        return MPP0 * Math.cos(Math.toRadians(cLat)) / Math.pow(2, zoom);
    }

    private void pan(int dx, int dy) {
        double px = wx(cLon, zoom) - dx, py = wy(cLat, zoom) - dy;
        py = Math.max(0, Math.min(size(zoom), py));
        cLat = clampLat(latOf(py, zoom));
        cLon = wrap(px / size(zoom) * 360 - 180);
        repaint();
    }

    private void zoomAt(int x, int y, double by) {
        double[] before = latLon(x, y);
        zoom = clampZoom(zoom + by);
        // Keep the point under the pointer where it was.
        double px = wx(before[1], zoom) - (x - getWidth() / 2.0);
        double py = wy(before[0], zoom) - (y - getHeight() / 2.0);
        py = Math.max(0, Math.min(size(zoom), py));
        cLat = clampLat(latOf(py, zoom));
        cLon = wrap(px / size(zoom) * 360 - 180);
        repaint();
    }

    void zoomBy(double by) {
        zoomAt(getWidth() / 2, getHeight() / 2, by);
    }

    // ------------------------------------------------------------------
    // Map taps

    void startPick(String prompt, SwingPane.Picked p) {
        this.prompt = prompt;
        this.picking = p;
        setCursor(java.awt.Cursor.getPredefinedCursor(
                java.awt.Cursor.CROSSHAIR_CURSOR));
        repaint();
    }

    /** Stop waiting for a tap; false if not waiting. */
    boolean stopPick() {
        if (picking == null)
            return false;
        picking = null;
        prompt = null;
        setCursor(null);
        repaint();
        return true;
    }

    boolean picking() {
        return picking != null;
    }

    /** The incident marker under (x, y), or null. */
    private MemoryPorts.Stored markerAt(int x, int y) {
        MemoryPorts.Stored best = null;
        double bestD = 12;
        for (MemoryPorts.Stored s : ports.stored()) {
            double d = Math.hypot(x(s.lon) - x, y(s.lat) - y);
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    void click(int x, int y) {
        MemoryPorts.Stored s = markerAt(x, y);
        if (picking != null) {
            SwingPane.Picked p = picking;
            stopPick();
            if (s != null)
                p.at(PositionSource.Fix.at(s.lat, s.lon), true);
            else {
                double[] ll = latLon(x, y);
                p.at(PositionSource.Fix.at(ll[0], ll[1]), false);
            }
        } else if (s != null) {
            open.accept(s.uid);
        }
    }

    // ------------------------------------------------------------------
    // Drawing

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (source.kind == TileSource.Kind.NONE) {
            g.setColor(LAND);
            g.fillRect(0, 0, getWidth(), getHeight());
            grid(g);
        } else {
            tiles(g);
        }
        boolean showLabels = labels.getAsBoolean();
        for (MemoryPorts.Stored s : ports.stored())
            for (OverlayShape sh : shapes(s))
                shape(g, s, sh, showLabels);
        for (MemoryPorts.Stored s : ports.stored())
            marker(g, s);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        for (Mesh.Contact c : team.get()) {
            double px = x(c.lon), py = y(c.lat);
            g.setColor(new Color(0x00BCD4));
            g.fill(new Ellipse2D.Double(px - 5, py - 5, 10, 10));
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new Ellipse2D.Double(px - 5, py - 5, 10, 10));
            outlined(g, c.callsign, (int) px + 8, (int) py + 4);
        }
        PositionSource.Fix me = ports.fix();
        if (me != null) {
            double px = x(me.lon), py = y(me.lat);
            g.setColor(new Color(0x2979FF));
            g.fill(new Ellipse2D.Double(px - 7, py - 7, 14, 14));
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2));
            g.draw(new Ellipse2D.Double(px - 7, py - 7, 14, 14));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            outlined(g, "You", (int) px - g.getFontMetrics().stringWidth(
                    "You") / 2, (int) py + 22);
        }
        corner(g);
        if (prompt != null) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            FontMetrics fm = g.getFontMetrics();
            int w = fm.stringWidth(prompt) + 24;
            g.setColor(new Color(0, 0, 0, 200));
            g.fillRoundRect((getWidth() - w) / 2, 12, w, 30, 8, 8);
            g.setColor(Color.WHITE);
            g.drawString(prompt, (getWidth() - w) / 2 + 12, 32);
        }
    }

    private void tiles(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int tz = (int) Math.max(source.minZoom, Math.min(source.maxZoom,
                Math.round(zoom)));
        double s = Math.pow(2, zoom - tz);
        double tile = 256 * s;
        int n = 1 << tz;
        double cx = wx(cLon, tz) * s, cy = wy(cLat, tz) * s;
        double left = cx - getWidth() / 2.0, top = cy - getHeight() / 2.0;
        int x0 = (int) Math.floor(left / tile),
                x1 = (int) Math.floor((left + getWidth()) / tile);
        int y0 = Math.max(0, (int) Math.floor(top / tile)),
                y1 = Math.min(n - 1, (int) Math.floor((top + getHeight())
                        / tile));
        Set<String> want = new HashSet<>();
        for (int ty = y0; ty <= y1; ty++)
            for (int tx = x0; tx <= x1; tx++)
                want.add(TileStore.keyOf(source, tz, Math.floorMod(tx, n), ty));
        tiles.want(want);
        // The grid shows wherever no tile has arrived (offline).
        g.setColor(LAND);
        g.fillRect(0, 0, getWidth(), getHeight());
        grid(g);
        for (int ty = y0; ty <= y1; ty++)
            for (int tx = x0; tx <= x1; tx++) {
                int sx = (int) Math.floor(tx * tile - left),
                        sy = (int) Math.floor(ty * tile - top);
                int ex = (int) Math.floor((tx + 1) * tile - left),
                        ey = (int) Math.floor((ty + 1) * tile - top);
                int wx = Math.floorMod(tx, n);
                BufferedImage img = tiles.tile(source, tz, wx, ty);
                if (img != null) {
                    g.drawImage(img, sx, sy, ex - sx, ey - sy, null);
                    continue;
                }
                // A lower zoom's tile, enlarged, while this one loads.
                for (int up = 1; up <= 6 && tz - up >= 0; up++) {
                    BufferedImage p = tiles.cached(source, tz - up, wx >> up,
                            ty >> up);
                    if (p == null)
                        continue;
                    int part = 256 >> up;
                    int ox = (wx & ((1 << up) - 1)) * part,
                            oy = (ty & ((1 << up) - 1)) * part;
                    g.drawImage(p, sx, sy, ex, ey, ox, oy, ox + part,
                            oy + part, null);
                    break;
                }
            }
    }

    private static List<OverlayShape> shapes(MemoryPorts.Stored s) {
        return s.nuc != null ? Overlays.nuc(s.nuc, s.lat, s.lon)
                : s.uxo != null ? Overlays.uxo(s.uxo)
                        : Overlays.standoff(s.standoff);
    }

    private static Color argb(int c) {
        return new Color(c, true);
    }

    private static Stroke stroke(Style st) {
        float w = (float) Math.max(1, st.width);
        switch (st.line) {
            case DASHED:
                return new BasicStroke(w, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_ROUND, 10, new float[] { 12, 8 }, 0);
            case DOTTED:
                return new BasicStroke(w, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND, 10, new float[] { 2, 6 }, 0);
            default:
                return new BasicStroke(w);
        }
    }

    /** A geodesic circle as {lat, lon} points (cached). */
    private double[][] circle(double lat, double lon, double r) {
        String k = lat + "," + lon + "," + r;
        double[][] c = circles.get(k);
        if (c == null) {
            c = new double[CIRCLE_POINTS][];
            for (int i = 0; i < CIRCLE_POINTS; i++)
                c[i] = Geo.destination(lat, lon, 360.0 * i / CIRCLE_POINTS,
                        r);
            circles.put(k, c);
        }
        return c;
    }

    private void shape(Graphics2D g, MemoryPorts.Stored s, OverlayShape sh,
            boolean showLabels) {
        Path2D.Double p = new Path2D.Double();
        double lx, ly;
        if (sh.kind == OverlayShape.Kind.CIRCLE) {
            double[][] c = circle(s.lat, s.lon, sh.radiusM);
            for (int i = 0; i < c.length; i++) {
                double px = x(c[i][1]), py = y(c[i][0]);
                if (i == 0)
                    p.moveTo(px, py);
                else
                    p.lineTo(px, py);
            }
            p.closePath();
            lx = x(c[0][1]);
            ly = y(c[0][0]) - 4;
        } else {
            double sx = 0, sy = 0;
            for (int i = 0; i < sh.pointCount(); i++) {
                double px = x(sh.lon(i)), py = y(sh.lat(i));
                sx += px;
                sy += py;
                if (i == 0)
                    p.moveTo(px, py);
                else
                    p.lineTo(px, py);
            }
            if (sh.isClosed())
                p.closePath();
            lx = sx / Math.max(1, sh.pointCount());
            ly = sy / Math.max(1, sh.pointCount());
        }
        Style st = sh.style;
        if (!st.noFill() && sh.kind != OverlayShape.Kind.POLYLINE) {
            g.setColor(argb(st.fillArgb));
            g.fill(p);
        }
        g.setColor(argb(st.strokeArgb));
        g.setStroke(stroke(st));
        g.draw(p);
        if (showLabels && sh.title != null && !sh.title.isEmpty()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            FontMetrics fm = g.getFontMetrics();
            String t = sh.title.length() > 70 ? sh.title.substring(0, 67)
                    + "…" : sh.title;
            int w = fm.stringWidth(t);
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect((int) lx - w / 2 - 3, (int) ly - fm.getAscent(), w + 6,
                    fm.getHeight());
            g.setColor(Color.WHITE);
            g.drawString(t, (int) lx - w / 2, (int) ly);
        }
    }

    private void marker(Graphics2D g, MemoryPorts.Stored s) {
        double px = x(s.lon), py = y(s.lat);
        Color c = s.cleared() ? Color.GRAY : s.nuc != null ? SwingScreen.NUC
                : s.uxo != null ? SwingScreen.ORANGE : SwingScreen.RED;
        g.setStroke(new BasicStroke(2));
        g.setColor(c);
        g.fill(new Ellipse2D.Double(px - 6, py - 6, 12, 12));
        g.setColor(Color.WHITE);
        g.draw(new Ellipse2D.Double(px - 6, py - 6, 12, 12));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        outlined(g, s.name, (int) px + 9, (int) py + 4);
    }

    /** White text with a dark outline: readable on any imagery. */
    private static void outlined(Graphics2D g, String t, int x, int y) {
        g.setColor(new Color(0, 0, 0, 200));
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                if (dx != 0 || dy != 0)
                    g.drawString(t, x + dx, y + dy);
        g.setColor(Color.WHITE);
        g.drawString(t, x, y);
    }

    /** Lat/lon lines at a round spacing (no map). */
    private void grid(Graphics2D g) {
        double[] tl = latLon(0, 0), br = latLon(getWidth(), getHeight());
        double spanDeg = Math.max(1e-6, tl[0] - br[0]);
        double step = Math.pow(10, Math.floor(Math.log10(spanDeg / 4)));
        if (spanDeg / step > 12)
            step *= 5;
        else if (spanDeg / step > 6)
            step *= 2;
        int dec = Math.max(0, Math.min(6, (int) Math.ceil(-Math.log10(step))));
        String fmt = "%." + dec + "f";
        g.setStroke(new BasicStroke(1));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        for (double la = Math.floor(br[0] / step) * step; la <= tl[0];
                la += step) {
            int yy = (int) y(la);
            g.setColor(GRID);
            g.drawLine(0, yy, getWidth(), yy);
            g.setColor(GRID_TEXT);
            g.drawString(String.format(Locale.US, fmt, la), 4, yy - 2);
        }
        double lonSpan = (br[1] - tl[1] + 360) % 360;
        if (lonSpan == 0)
            lonSpan = 360;
        double lstep = step;
        while (lonSpan / lstep > 40)
            lstep *= 2;
        for (double lo = Math.floor(tl[1] / lstep) * lstep; lo <= tl[1]
                + lonSpan; lo += lstep) {
            int xx = (int) x(lo);
            g.setColor(GRID);
            g.drawLine(xx, 0, xx, getHeight());
            g.setColor(GRID_TEXT);
            g.drawString(String.format(Locale.US, fmt, wrap(lo)), xx + 2,
                    getHeight() - 40);
        }
    }

    /**
     * Bottom left: the position under the pointer and a scale bar. Bottom
     * right: the map's attribution and any tile problem.
     */
    private void corner(Graphics2D g) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        FontMetrics fm = g.getFontMetrics();
        // Scale bar
        double mpp = metresPerPixel();
        double target = mpp * 120;
        double m = Math.pow(10, Math.floor(Math.log10(target)));
        if (target / m >= 5)
            m *= 5;
        else if (target / m >= 2)
            m *= 2;
        int px = (int) (m / mpp);
        int x0 = 12, y0 = getHeight() - 32;
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(x0 - 6, y0 - 22, px + 12, 30);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2));
        g.drawLine(x0, y0, x0 + px, y0);
        g.drawLine(x0, y0 - 5, x0, y0 + 5);
        g.drawLine(x0 + px, y0 - 5, x0 + px, y0 + 5);
        g.drawString(m >= 1000 ? String.format(Locale.US, "%.0f km", m / 1000)
                : String.format(Locale.US, "%.0f m", m), x0, y0 - 8);
        // Pointer position
        String at = null;
        if (mouseX >= 0) {
            double[] ll = latLon(mouseX, mouseY);
            String grid;
            try {
                grid = Mgrs.of(ll[0], ll[1]);
            } catch (IllegalArgumentException e) {
                grid = "";
            }
            at = grid + "   " + String.format(Locale.US, "%.5f, %.5f", ll[0],
                    ll[1]);
        }
        if (at != null)
            box(g, fm, at, 6, getHeight() - 6, false);
        // Attribution and problems
        String attr = source.attribution;
        if (attr != null && !attr.isEmpty())
            box(g, fm, attr, getWidth() - 6, getHeight() - 6, true);
        String problem = source.kind == TileSource.Kind.NONE ? null
                : tiles.problem();
        int loading = source.kind == TileSource.Kind.ONLINE ? tiles.loading()
                : 0;
        if (problem == null && loading > 0)
            problem = "Loading " + loading + (loading == 1 ? " map square"
                    : " map squares") + "…";
        if (problem != null) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            box(g, g.getFontMetrics(), problem, getWidth() - 6,
                    getHeight() - 26, true);
        }
    }

    private static void box(Graphics2D g, FontMetrics fm, String t, int x,
            int baseY, boolean rightAligned) {
        int w = fm.stringWidth(t);
        int left = rightAligned ? x - w - 8 : x;
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(left, baseY - fm.getAscent() - 3, w + 8, fm.getHeight()
                + 4);
        g.setColor(Color.WHITE);
        g.drawString(t, left + 4, baseY - 2);
    }
}
