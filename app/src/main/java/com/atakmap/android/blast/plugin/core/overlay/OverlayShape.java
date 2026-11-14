package com.atakmap.android.blast.plugin.core.overlay;

/**
 * One shape an incident draws on the map, described without any map API.
 * A host (ATAK, TAK-X, an export) renders it. Circles are centred on the
 * incident's own point; polygons and polylines carry their own points.
 */
public final class OverlayShape {

    public enum Kind {
        CIRCLE, POLYGON, POLYLINE
    }

    public final Kind kind;
    /** Suffix appended to the incident's uid to name this part. */
    public final String id;
    public final String title;
    public final Style style;
    /** Circle radius in metres (NaN for other kinds). */
    public final double radiusM;
    /** Polygon/polyline points as {lat, lon} (null for circles). */
    private final double[][] points;

    private OverlayShape(Kind kind, String id, String title, Style style,
            double radiusM, double[][] points) {
        this.kind = kind;
        this.id = id;
        this.title = title;
        this.style = style;
        this.radiusM = radiusM;
        this.points = points;
    }

    public static OverlayShape circle(String id, double radiusM, String title,
            Style style) {
        return new OverlayShape(Kind.CIRCLE, id, title, style, radiusM, null);
    }

    /** A closed outline ({@code closed}) or an open line. */
    public static OverlayShape outline(String id, double[][] latLon,
            boolean closed, String title, Style style) {
        double[][] copy = new double[latLon.length][];
        for (int i = 0; i < latLon.length; i++)
            copy[i] = new double[] { latLon[i][0], latLon[i][1] };
        return new OverlayShape(closed ? Kind.POLYGON : Kind.POLYLINE, id,
                title, style, Double.NaN, copy);
    }

    public boolean isClosed() {
        return kind == Kind.POLYGON;
    }

    public int pointCount() {
        return points == null ? 0 : points.length;
    }

    public double lat(int i) {
        return points[i][0];
    }

    public double lon(int i) {
        return points[i][1];
    }
}
