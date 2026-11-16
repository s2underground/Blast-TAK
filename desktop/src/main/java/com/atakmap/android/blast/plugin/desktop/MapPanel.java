package com.atakmap.android.blast.plugin.desktop;

import com.atakmap.android.blast.plugin.core.overlay.OverlayShape;
import com.atakmap.android.blast.plugin.core.overlay.Overlays;
import com.atakmap.android.blast.plugin.core.overlay.Style;
import com.atakmap.android.blast.plugin.core.port.PositionSource;
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
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

/**
 * A plain flat map (no tiles: this host is offline) that draws every
 * incident from the core's overlay model ({@link Overlays}), exactly the
 * shapes, colours and line styles the ATAK layer draws. Drag to pan, wheel
 * to zoom, click a marker to open its card; when the core asks for a map
 * tap, the next click places.
 */
final class MapPanel extends JPanel {

    private static final double M_PER_DEG = 111_320;
    private static final Color LAND = new Color(0x26302A),
            GRID = new Color(0x3A463E), GRID_TEXT = new Color(0x7F8C84);

    private final MemoryPorts ports;
    private final BooleanSupplier labels;
    private final Consumer<String> open;
    private double cLat, cLon;
    /** Metres per pixel. */
    private double scale = 20;
    private SwingPane.Picked picking;
    private String prompt;
    private int dragX, dragY;
    private boolean dragged;

    MapPanel(MemoryPorts ports, double lat, double lon,
            BooleanSupplier labels, Consumer<String> open) {
        this.ports = ports;
        this.cLat = lat;
        this.cLon = lon;
        this.labels = labels;
        this.open = open;
        setBackground(LAND);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragX = e.getX();
                dragY = e.getY();
                dragged = false;
                if (e.isPopupTrigger())
                    popup(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                double cos = Math.cos(Math.toRadians(cLat));
                cLon -= (e.getX() - dragX) * scale / (M_PER_DEG * cos);
                cLat += (e.getY() - dragY) * scale / M_PER_DEG;
                dragX = e.getX();
                dragY = e.getY();
                dragged = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger())
                    popup(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (dragged || e.getButton() != MouseEvent.BUTTON1)
                    return;
                click(e.getX(), e.getY());
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double[] before = latLon(e.getX(), e.getY());
                scale *= Math.pow(1.25, e.getPreciseWheelRotation());
                scale = Math.max(0.5, Math.min(scale, 20000));
                double[] after = latLon(e.getX(), e.getY());
                cLat += before[0] - after[0];
                cLon += before[1] - after[1];
                repaint();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(m);
    }

    // ------------------------------------------------------------------
    // Projection (local equirectangular: fine at incident scale)

    double[] latLon(int x, int y) {
        double cos = Math.cos(Math.toRadians(cLat));
        return new double[] {
                cLat - (y - getHeight() / 2.0) * scale / M_PER_DEG,
                cLon + (x - getWidth() / 2.0) * scale / (M_PER_DEG * cos) };
    }

    private double x(double lon) {
        double cos = Math.cos(Math.toRadians(cLat));
        return getWidth() / 2.0 + (lon - cLon) * M_PER_DEG * cos / scale;
    }

    private double y(double lat) {
        return getHeight() / 2.0 - (lat - cLat) * M_PER_DEG / scale;
    }

    PositionSource.Fix center() {
        return PositionSource.Fix.at(cLat, cLon);
    }

    /** Centre on a point so that a ring of {@code radiusM} fits. */
    void frame(double lat, double lon, double radiusM) {
        cLat = lat;
        cLon = lon;
        int side = Math.max(200, Math.min(getWidth(), getHeight()));
        scale = Math.max(1, radiusM * 2.4 / side);
        repaint();
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

    private void popup(MouseEvent e) {
        double[] ll = latLon(e.getX(), e.getY());
        JPopupMenu m = new JPopupMenu();
        JMenuItem set = new JMenuItem("Set my GPS position here");
        set.addActionListener(a -> {
            ports.setGps(new PositionSource.Fix(ll[0], ll[1], 5));
            repaint();
        });
        m.add(set);
        JMenuItem clear = new JMenuItem("No GPS position");
        clear.setEnabled(ports.hasGps());
        clear.addActionListener(a -> {
            ports.setGps(null);
            repaint();
        });
        m.add(clear);
        m.show(this, e.getX(), e.getY());
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
        grid(g);
        boolean showLabels = labels.getAsBoolean();
        for (MemoryPorts.Stored s : ports.stored())
            for (OverlayShape sh : shapes(s))
                shape(g, s, sh, showLabels);
        for (MemoryPorts.Stored s : ports.stored())
            marker(g, s);
        PositionSource.Fix me = ports.fix();
        if (me != null) {
            double px = x(me.lon), py = y(me.lat);
            g.setColor(new Color(0x2979FF));
            g.fill(new Ellipse2D.Double(px - 7, py - 7, 14, 14));
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2));
            g.draw(new Ellipse2D.Double(px - 7, py - 7, 14, 14));
            g.drawString("You (GPS)", (int) px + 10, (int) py + 4);
        }
        scaleBar(g);
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

    private void shape(Graphics2D g, MemoryPorts.Stored s, OverlayShape sh,
            boolean showLabels) {
        java.awt.Shape area;
        double lx, ly;
        if (sh.kind == OverlayShape.Kind.CIRCLE) {
            double r = sh.radiusM / scale;
            double cx = x(s.lon), cy = y(s.lat);
            area = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
            lx = cx;
            ly = cy - r - 4;
        } else {
            Path2D.Double p = new Path2D.Double();
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
            area = p;
            lx = sx / Math.max(1, sh.pointCount());
            ly = sy / Math.max(1, sh.pointCount());
        }
        Style st = sh.style;
        if (!st.noFill() && sh.kind != OverlayShape.Kind.POLYLINE) {
            g.setColor(argb(st.fillArgb));
            g.fill(area);
        }
        g.setColor(argb(st.strokeArgb));
        g.setStroke(stroke(st));
        g.draw(area);
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
        g.drawString(s.name, (int) px + 9, (int) py + 4);
    }

    /** Lat/lon lines at a round spacing. */
    private void grid(Graphics2D g) {
        double[] tl = latLon(0, 0), br = latLon(getWidth(), getHeight());
        double spanDeg = (tl[0] - br[0]);
        double step = Math.pow(10, Math.floor(Math.log10(spanDeg / 4)));
        if (spanDeg / step > 12)
            step *= 5;
        else if (spanDeg / step > 6)
            step *= 2;
        g.setStroke(new BasicStroke(1));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        for (double la = Math.floor(br[0] / step) * step; la <= tl[0];
                la += step) {
            int yy = (int) y(la);
            g.setColor(GRID);
            g.drawLine(0, yy, getWidth(), yy);
            g.setColor(GRID_TEXT);
            g.drawString(String.format(Locale.US, "%.4f", la), 4, yy - 2);
        }
        for (double lo = Math.floor(tl[1] / step) * step; lo <= br[1];
                lo += step) {
            int xx = (int) x(lo);
            g.setColor(GRID);
            g.drawLine(xx, 0, xx, getHeight());
            g.setColor(GRID_TEXT);
            g.drawString(String.format(Locale.US, "%.4f", lo), xx + 2,
                    getHeight() - 4);
        }
    }

    private void scaleBar(Graphics2D g) {
        double target = scale * 120;
        double m = Math.pow(10, Math.floor(Math.log10(target)));
        if (target / m >= 5)
            m *= 5;
        else if (target / m >= 2)
            m *= 2;
        int px = (int) (m / scale);
        int x0 = getWidth() - px - 20, y0 = getHeight() - 24;
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2));
        g.drawLine(x0, y0, x0 + px, y0);
        g.drawLine(x0, y0 - 5, x0, y0 + 5);
        g.drawLine(x0 + px, y0 - 5, x0 + px, y0 + 5);
        g.drawString(m >= 1000 ? String.format(Locale.US, "%.0f km", m / 1000)
                : String.format(Locale.US, "%.0f m", m), x0, y0 - 8);
    }
}
