package com.s2underground.blast.takx;

import com.atakmap.android.blast.plugin.core.overlay.OverlayShape;
import com.atakmap.android.blast.plugin.core.overlay.Overlays;
import com.atakmap.android.blast.plugin.swing.MemoryPorts;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.AnnotationAttributes;
import gov.nasa.worldwind.render.GlobeAnnotation;
import gov.takx.api.globe.IGlobeDelegate;
import gov.takx.api.layer.IRaptorLayer;
import gov.takx.api.layer.LayerGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Labels for the incidents' rings and outlines on the TAK-X globe (TAK-X
 * shapes don't show their names on the globe): a ring's title at the top
 * of the ring, an outline's title at its centre, as on ATAK. One WorldWind
 * layer, kept out of TAK-X's layer list; follows Blast's labels setting.
 */
final class TakxLabels {

    private static final Logger LOG = LoggerFactory.getLogger(
            TakxLabels.class);
    private static final double M_PER_DEG = 111_320;
    private static final int MAX_CHARS = 70;

    private final IGlobeDelegate globe;
    private final MemoryPorts ports;
    private final BooleanSupplier shown;
    private final RenderableLayer layer = new RenderableLayer();
    private final AnnotationAttributes style = new AnnotationAttributes();
    private IRaptorLayer added;

    TakxLabels(IGlobeDelegate globe, MemoryPorts ports, BooleanSupplier shown) {
        this.globe = globe;
        this.ports = ports;
        this.shown = shown;
        layer.setName("Blast labels");
        layer.setPickEnabled(false);
        style.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        style.setTextColor(Color.WHITE);
        style.setBackgroundColor(new Color(0, 0, 0, 150));
        style.setBorderWidth(0);
        style.setCornerRadius(2);
        style.setInsets(new Insets(2, 5, 2, 5));
        style.setLeader(AVKey.SHAPE_NONE);
        style.setFrameShape(AVKey.SHAPE_RECTANGLE);
        style.setAdjustWidthToText(AVKey.SIZE_FIT_TEXT);
        style.setTextAlign(AVKey.CENTER);
        style.setDrawOffset(new Point(0, 0));
        try {
            added = globe.addLayer(layer, LayerGroup.UNLISTED_OVERLAY);
        } catch (RuntimeException e) {
            LOG.warn("could not add the label layer", e);
        }
    }

    /** Rebuild every label from the incidents and the setting. */
    void refresh() {
        layer.removeAllRenderables();
        if (shown.getAsBoolean())
            for (MemoryPorts.Stored s : ports.stored())
                for (OverlayShape sh : shapes(s))
                    label(s, sh);
        try {
            globe.redraw();
        } catch (RuntimeException e) {
            LOG.debug("redraw failed", e);
        }
    }

    private static List<OverlayShape> shapes(MemoryPorts.Stored s) {
        return s.nuc != null ? Overlays.nuc(s.nuc, s.lat, s.lon)
                : s.uxo != null ? Overlays.uxo(s.uxo)
                        : Overlays.standoff(s.standoff);
    }

    private void label(MemoryPorts.Stored s, OverlayShape sh) {
        if (sh.title == null || sh.title.isEmpty())
            return;
        double lat, lon;
        if (sh.kind == OverlayShape.Kind.CIRCLE) {
            // Top of the ring.
            lat = s.lat + sh.radiusM / M_PER_DEG;
            lon = s.lon;
        } else {
            double a = 0, b = 0;
            int n = sh.pointCount();
            if (n == 0)
                return;
            for (int i = 0; i < n; i++) {
                a += sh.lat(i);
                b += sh.lon(i);
            }
            lat = a / n;
            lon = b / n;
        }
        String t = sh.title.length() > MAX_CHARS
                ? sh.title.substring(0, MAX_CHARS - 1) + "…" : sh.title;
        GlobeAnnotation g = new GlobeAnnotation(t, Position.fromDegrees(lat,
                lon, 0), style);
        g.setAlwaysOnTop(true);
        layer.addRenderable(g);
    }

    /** Remove the layer (plugin stopping). */
    void remove() {
        layer.removeAllRenderables();
        try {
            if (added != null)
                globe.removeLayer(added);
        } catch (RuntimeException e) {
            LOG.debug("label layer already gone", e);
        }
    }
}
