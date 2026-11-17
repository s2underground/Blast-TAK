package com.atakmap.android.blast.plugin.desktop.map;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where map tiles come from: an online XYZ service (Web Mercator, 256 px
 * tiles, {z}/{x}/{y} in the address), an offline MBTiles file, or none (a
 * plain grid).
 */
public final class TileSource {

    public enum Kind {
        NONE, ONLINE, MBTILES
    }

    public final Kind kind;
    /** Short id for settings and the cache folder. */
    public final String id;
    public final String name;
    /** Address with {z}, {x}, {y} (and optionally {s}); ONLINE only. */
    public final String template;
    /** Shown in the map's corner whenever its tiles are. */
    public final String attribution;
    public final int minZoom, maxZoom;
    /** MBTILES only. */
    public final File file;
    /** MBTILES: west, south, east, north from its metadata, or null. */
    public final double[] bounds;

    private TileSource(Kind kind, String id, String name, String template,
            String attribution, int minZoom, int maxZoom, File file,
            double[] bounds) {
        this.kind = kind;
        this.id = id;
        this.name = name;
        this.template = template;
        this.attribution = attribution;
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
        this.file = file;
        this.bounds = bounds;
    }

    public static final TileSource NONE = new TileSource(Kind.NONE, "none",
            "No map (grid)", null, "", 0, 22, null, null);

    /**
     * OpenStreetMap's standard tiles. Their tile usage policy: identify the
     * app (User-Agent), cache, show the attribution, at most two
     * connections, no bulk or offline downloading.
     */
    public static final TileSource OSM = new TileSource(Kind.ONLINE, "osm",
            "OpenStreetMap", "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
            "© OpenStreetMap contributors", 0, 19, null, null);

    /** USGS The National Map (US public domain; best over the US). */
    public static final TileSource USGS_TOPO = new TileSource(Kind.ONLINE,
            "usgs-topo", "USGS Topo",
            "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/{z}/{y}/{x}",
            "Tiles courtesy of the U.S. Geological Survey", 0, 16, null,
            null);

    public static final TileSource USGS_IMAGERY = new TileSource(Kind.ONLINE,
            "usgs-imagery", "USGS Imagery",
            "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/{z}/{y}/{x}",
            "Tiles courtesy of the U.S. Geological Survey", 0, 16, null,
            null);

    public static final TileSource USGS_IMAGERY_TOPO = new TileSource(
            Kind.ONLINE, "usgs-imagery-topo", "USGS Imagery + Topo",
            "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryTopo/MapServer/tile/{z}/{y}/{x}",
            "Tiles courtesy of the U.S. Geological Survey", 0, 16, null,
            null);

    public static List<TileSource> builtIn() {
        List<TileSource> l = new ArrayList<>();
        l.add(OSM);
        l.add(USGS_TOPO);
        l.add(USGS_IMAGERY);
        l.add(USGS_IMAGERY_TOPO);
        l.add(NONE);
        return l;
    }

    public static TileSource builtIn(String id) {
        for (TileSource s : builtIn())
            if (s.id.equals(id))
                return s;
        return null;
    }

    /**
     * A tile service the user typed: an http(s) address with {z}, {x} and
     * {y}. Throws IllegalArgumentException if it isn't one.
     */
    public static TileSource custom(String name, String template,
            String attribution, int maxZoom) {
        String t = template == null ? "" : template.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("https://") || lower.startsWith("http://"))
                || !t.contains("{z}") || !t.contains("{x}")
                || !(t.contains("{y}") || t.contains("{-y}")))
            throw new IllegalArgumentException(
                    "The address must start with http:// or https:// and "
                            + "contain {z}, {x} and {y}.");
        String n = name == null || name.trim().isEmpty() ? "Custom map"
                : name.trim();
        return new TileSource(Kind.ONLINE, "xyz-" + hash(t), n, t,
                attribution == null ? "" : attribution.trim(), 0,
                Math.max(1, Math.min(22, maxZoom)), null, null);
    }

    /** An MBTiles file (name and zoom range from its metadata). */
    public static TileSource mbtiles(File f, String name, String attribution,
            int minZoom, int maxZoom, double[] bounds) {
        return new TileSource(Kind.MBTILES, "mbtiles-" + hash(f
                .getAbsolutePath()), name, null, attribution, minZoom,
                maxZoom, f, bounds);
    }

    /** The address of one tile. */
    public String url(int z, int x, int y) {
        int n = 1 << z;
        String s = template.replace("{z}", Integer.toString(z))
                .replace("{x}", Integer.toString(x))
                .replace("{-y}", Integer.toString(n - 1 - y))
                .replace("{y}", Integer.toString(y));
        if (s.contains("{s}"))
            s = s.replace("{s}", String.valueOf("abc".charAt(Math.floorMod(
                    x + y, 3))));
        return s;
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 6; i++)
                b.append(String.format(Locale.ROOT, "%02x", d[i]));
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
