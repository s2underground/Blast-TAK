package com.atakmap.android.blast.plugin.desktop.map;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;

/**
 * Map tiles for the map panel: from memory, the disk cache, the network or
 * an MBTiles file. Never blocks the caller: a tile that isn't in memory is
 * loaded in the background and the panel is told to repaint.
 *
 * <p>Online tiles are cached on disk (kept fresh for 7 days, then
 * revalidated; used however old when offline), with a size cap. At most two
 * downloads run at once and tiles the map no longer shows are not fetched,
 * as OpenStreetMap's tile policy asks.
 */
public final class TileStore {

    public static final String USER_AGENT =
            "BlastDesktop/1.2 (+https://github.com/s2underground/Blast-TAK)";
    private static final long FRESH_MS = 7L * 24 * 3600 * 1000;
    /** Retry a failed tile after 2 s, 6 s, 18 s, then every minute. */
    private static final long RETRY_FIRST_MS = 2_000, RETRY_MAX_MS = 60_000;
    /** A tile the server doesn't have (404): ask again after a minute
     * (some servers make tiles on request and have them later). */
    private static final long MISSING_MS = 60_000;
    /** A tile still loading after this long is asked for again. */
    private static final long STUCK_MS = 45_000;
    /** Downloads at once: OSM's policy allows 2; other servers get 8. */
    private static final int OSM_CONNECTIONS = 2, OTHER_CONNECTIONS = 8;
    /** A tile that hasn't arrived in this long is given up and retried,
     * so a few hung requests can't hold up the rest. */
    private static final Duration TILE_TIMEOUT = Duration.ofSeconds(10);
    private static final int MEMORY_TILES = 600;
    private static final DateTimeFormatter HTTP_DATE = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);

    private final File cacheRoot;
    private final Runnable repaint;
    private volatile long capBytes;
    private final AtomicLong cacheBytes = new AtomicLong(-1);

    private final Map<String, BufferedImage> memory = new LinkedHashMap<
            String, BufferedImage>(256, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(
                Map.Entry<String, BufferedImage> e) {
            return size() > MEMORY_TILES;
        }
    };
    /** Tiles being loaded, and since when. */
    private final Map<String, Long> pending = new HashMap<>();
    private final TileLog log;
    /** Failed tiles: when to ask again, and how many failures so far. */
    private final Map<String, long[]> failed = new HashMap<>();
    private final Map<String, java.util.concurrent.Semaphore> slots =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ScheduledExecutorService later =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "blast-tile-retry");
                t.setDaemon(true);
                return t;
            });
    private volatile Set<String> wanted = Collections.emptySet();

    private final ExecutorService net = Executors.newFixedThreadPool(
            OTHER_CONNECTIONS + 2, r -> {
        Thread t = new Thread(r, "blast-tiles");
        t.setDaemon(true);
        return t;
    });
    /** One thread for files: the cache and MBTiles (SQLite). */
    private final ExecutorService disk = Executors.newSingleThreadExecutor(
            r -> {
                Thread t = new Thread(r, "blast-tile-files");
                t.setDaemon(true);
                return t;
            });
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            // HTTP/1.1: separate connections. Java's HTTP/2 puts every tile
            // on one connection, and some tile servers stall it.
            .version(HttpClient.Version.HTTP_1_1)
            .proxy(ProxySelector.getDefault()).build();
    private final Map<File, Connection> mbtiles = new HashMap<>();

    private volatile String problem;
    private volatile long problemAt;

    public TileStore(File cacheRoot, long capBytes, Runnable repaint) {
        this(cacheRoot, capBytes, repaint, new TileLog(null));
    }

    public TileStore(File cacheRoot, long capBytes, Runnable repaint,
            TileLog log) {
        this.log = log;
        this.cacheRoot = cacheRoot;
        this.capBytes = capBytes;
        this.repaint = repaint;
        disk.execute(this::measure);
    }

    private static String key(TileSource s, int z, int x, int y) {
        return s.id + "/" + z + "/" + x + "/" + y;
    }

    /** Only tiles in this set are fetched (the rest are skipped). */
    public void want(Set<String> keys) {
        wanted = keys;
    }

    /** A tile from memory, or null (then it is loaded if wanted). */
    public BufferedImage tile(TileSource s, int z, int x, int y) {
        String k = key(s, z, x, y);
        synchronized (this) {
            BufferedImage img = memory.get(k);
            if (img != null)
                return img;
            Long since = pending.get(k);
            if (since != null) {
                if (System.currentTimeMillis() - since < STUCK_MS)
                    return null;
                log.add("still loading after " + STUCK_MS / 1000
                        + " s, asking again: " + k);
            }
            long[] f = failed.get(k);
            if (f != null && System.currentTimeMillis() < f[0])
                return null;
            pending.put(k, System.currentTimeMillis());
        }
        disk.execute(() -> {
            try {
                loadLocal(s, z, x, y, k);
            } catch (RuntimeException | Error e) {
                log.add("error loading " + k + ": " + e);
                done(k, null);
            }
        });
        return null;
    }

    /** A tile only if it is in memory now (for stand-ins). */
    public synchronized BufferedImage cached(TileSource s, int z, int x,
            int y) {
        return memory.get(key(s, z, x, y));
    }

    public static String keyOf(TileSource s, int z, int x, int y) {
        return key(s, z, x, y);
    }

    public TileLog log() {
        return log;
    }

    /** How many wanted tiles are loading now. */
    public synchronized int loading() {
        int n = 0;
        for (String k : pending.keySet())
            if (wanted.contains(k))
                n++;
        return n;
    }

    /** What went wrong recently ("offline", a file error), or null. */
    public String problem() {
        return System.currentTimeMillis() - problemAt < 15_000 ? problem
                : null;
    }

    private void problem(String p) {
        problem = p;
        problemAt = System.currentTimeMillis();
    }

    private void done(String k, BufferedImage img) {
        done(k, img, false);
    }

    /**
     * A tile arrived, or failed ({@code img} null). A failed tile is asked
     * for again later (sooner at first), and the map is repainted then so
     * it does, without waiting for the user to pan.
     */
    private void done(String k, BufferedImage img, boolean missing) {
        long wait = 0;
        synchronized (this) {
            pending.remove(k);
            if (img != null) {
                memory.put(k, img);
                failed.remove(k);
            } else {
                long[] f = failed.get(k);
                long n = f == null ? 1 : f[1] + 1;
                wait = missing ? MISSING_MS : Math.min(RETRY_MAX_MS,
                        RETRY_FIRST_MS * (long) Math.pow(3, n - 1));
                failed.put(k, new long[] { System.currentTimeMillis() + wait,
                        n });
            }
        }
        repaint.run();
        if (wait > 0)
            later.schedule(repaint, wait + 50, TimeUnit.MILLISECONDS);
    }

    private java.util.concurrent.Semaphore slots(TileSource s) {
        return slots.computeIfAbsent(s.id, id -> new java.util.concurrent
                .Semaphore(s == TileSource.OSM ? OSM_CONNECTIONS
                        : OTHER_CONNECTIONS));
    }

    private void drop(String k) {
        synchronized (this) {
            pending.remove(k);
        }
    }

    // ------------------------------------------------------------------
    // Local: MBTiles or the disk cache

    private void loadLocal(TileSource s, int z, int x, int y, String k) {
        if (s.kind == TileSource.Kind.MBTILES) {
            done(k, readMbtiles(s.file, z, x, y));
            return;
        }
        File f = cacheFile(k);
        BufferedImage img = null;
        long age = Long.MAX_VALUE;
        if (f.isFile()) {
            try {
                img = decode(Files.readAllBytes(f.toPath()));
                age = System.currentTimeMillis() - f.lastModified();
            } catch (IOException e) {
                img = null;
            }
        }
        if (img != null) {
            synchronized (this) {
                memory.put(k, img);
            }
            repaint.run();
            if (age < FRESH_MS) {
                drop(k);
                return;
            }
        }
        if (!wanted.contains(k)) {
            drop(k);
            return;
        }
        final long since = img != null ? f.lastModified() : 0;
        net.execute(() -> download(s, z, x, y, k, f, since));
    }

    private File cacheFile(String k) {
        return new File(cacheRoot, k + ".tile");
    }

    // ------------------------------------------------------------------
    // Network

    /** {@code since}: when the cached copy was fetched (0: none). */
    private void download(TileSource s, int z, int x, int y, String k,
            File f, long since) {
        boolean have = since > 0;
        if (!wanted.contains(k)) {
            drop(k);
            return;
        }
        java.util.concurrent.Semaphore slot = slots(s);
        try {
            slot.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            drop(k);
            return;
        }
        try {
            // Still on screen after waiting for a slot?
            if (!wanted.contains(k)) {
                drop(k);
                return;
            }
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(s.url(z,
                    x, y))).timeout(TILE_TIMEOUT)
                    .header("User-Agent", USER_AGENT);
            if (have)
                b.header("If-Modified-Since", HTTP_DATE.format(Instant
                        .ofEpochMilli(since).atOffset(ZoneOffset.UTC)));
            HttpResponse<byte[]> r = http.send(b.build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() == 304) {
                f.setLastModified(System.currentTimeMillis());
                drop(k);
                return;
            }
            if (r.statusCode() != 200)
                log.add(s.name + " answered " + r.statusCode() + " for "
                        + z + "/" + x + "/" + y);
            if (r.statusCode() == 404 || r.statusCode() == 204) {
                // No tile here (outside the map's area or zooms): the
                // lower zoom's tile stands in.
                problem(s.name + " has no map for some squares here");
                if (have)
                    drop(k);
                else
                    done(k, null, true);
                return;
            }
            if (r.statusCode() != 200) {
                problem(s.name + " answered " + r.statusCode()
                        + (r.statusCode() == 429 || r.statusCode() == 503
                                ? " (busy: retrying)" : ""));
                if (have)
                    drop(k);
                else
                    done(k, null);
                return;
            }
            BufferedImage img = decode(r.body());
            if (img == null) {
                log.add(s.name + " sent " + r.body().length + " bytes that "
                        + "aren't an image (" + r.headers().firstValue(
                                "Content-Type").orElse("no type") + ") for "
                        + z + "/" + x + "/" + y);
                problem(s.name + " sent something that isn't an image");
                done(k, null);
                return;
            }
            byte[] body = r.body();
            disk.execute(() -> store(f, body));
            done(k, img);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            drop(k);
        } catch (IOException | RuntimeException e) {
            log.add(s.name + " " + z + "/" + x + "/" + y + ": " + e);
            problem((e instanceof java.net.http.HttpTimeoutException
                    ? s.name + " is slow to answer (retrying)"
                    : "Can't reach " + s.name + " (offline?)") + reason(e));
            if (have)
                drop(k);
            else
                done(k, null);
        } finally {
            slot.release();
        }
    }

    /** The cause in a few words, for the map's message. */
    private static String reason(Exception e) {
        String m = e.getMessage();
        String n = e.getClass().getSimpleName();
        if (e instanceof java.net.http.HttpTimeoutException)
            return "";
        return ": " + n + (m == null || m.isEmpty() ? "" : " " + (m.length()
                > 80 ? m.substring(0, 80) + "\u2026" : m));
    }

    private static BufferedImage decode(byte[] data) throws IOException {
        if (data == null || data.length == 0)
            return null;
        return ImageIO.read(new ByteArrayInputStream(data));
    }

    // ------------------------------------------------------------------
    // Disk cache size

    private void store(File f, byte[] data) {
        try {
            File dir = f.getParentFile();
            if (!dir.isDirectory() && !dir.mkdirs())
                return;
            long before = f.isFile() ? f.length() : 0;
            File tmp = new File(dir, f.getName() + ".part");
            Files.write(tmp.toPath(), data);
            Files.move(tmp.toPath(), f.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            if (cacheBytes.get() >= 0
                    && cacheBytes.addAndGet(data.length - before) > capBytes)
                prune();
        } catch (IOException e) {
            problem("Can't write the map cache: " + e.getMessage());
        }
    }

    private void measure() {
        long[] total = { 0 };
        walk(cacheRoot, f -> total[0] += f.length());
        cacheBytes.set(total[0]);
        if (total[0] > capBytes)
            prune();
    }

    /** Remove the least recently fetched tiles down to 90% of the cap. */
    private void prune() {
        List<File> files = new ArrayList<>();
        walk(cacheRoot, files::add);
        files.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        long total = 0;
        for (File f : files)
            total += f.length();
        long target = capBytes * 9 / 10;
        for (File f : files) {
            if (total <= target)
                break;
            long len = f.length();
            if (f.delete())
                total -= len;
        }
        cacheBytes.set(total);
    }

    private static void walk(File dir, java.util.function.Consumer<File> fn) {
        File[] l = dir.listFiles();
        if (l == null)
            return;
        for (File f : l) {
            if (f.isDirectory())
                walk(f, fn);
            else if (f.getName().endsWith(".tile"))
                fn.accept(f);
        }
    }

    public void setCap(long bytes) {
        capBytes = bytes;
        disk.execute(this::measure);
    }

    public long cacheBytes() {
        return Math.max(0, cacheBytes.get());
    }

    /** Delete every cached online tile. */
    public void clearCache() {
        synchronized (this) {
            memory.clear();
            failed.clear();
        }
        disk.execute(() -> {
            walk(cacheRoot, File::delete);
            cacheBytes.set(0);
            repaint.run();
        });
    }

    /** Forget recent failures (after the user changes something). */
    public synchronized void retry() {
        failed.clear();
        problem = null;
    }

    // ------------------------------------------------------------------
    // MBTiles (SQLite, read only)

    private BufferedImage readMbtiles(File file, int z, int x, int y) {
        try {
            Connection c = mbtiles.get(file);
            if (c == null) {
                c = open(file);
                mbtiles.put(file, c);
            }
            try (PreparedStatement st = c.prepareStatement("SELECT tile_data "
                    + "FROM tiles WHERE zoom_level=? AND tile_column=? AND "
                    + "tile_row=?")) {
                st.setInt(1, z);
                st.setInt(2, x);
                st.setInt(3, (1 << z) - 1 - y);
                try (ResultSet rs = st.executeQuery()) {
                    return rs.next() ? decode(rs.getBytes(1)) : null;
                }
            }
        } catch (SQLException | IOException e) {
            problem("Can't read " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private static Connection open(File f) throws SQLException {
        Properties p = new Properties();
        p.setProperty("open_mode", "1"); // SQLITE_OPEN_READONLY
        return DriverManager.getConnection("jdbc:sqlite:" + f
                .getAbsolutePath(), p);
    }

    /**
     * Opens an MBTiles file and reads its metadata. Throws IOException with
     * a message for the user if it can't be used (not MBTiles, vector
     * tiles).
     */
    public static TileSource openMbtiles(File f) throws IOException {
        if (!f.isFile())
            throw new IOException("File not found.");
        try (Connection c = open(f); Statement st = c.createStatement()) {
            Map<String, String> meta = new HashMap<>();
            try (ResultSet rs = st.executeQuery(
                    "SELECT name, value FROM metadata")) {
                while (rs.next())
                    meta.put(rs.getString(1).toLowerCase(Locale.ROOT),
                            rs.getString(2));
            }
            String format = meta.getOrDefault("format", "png")
                    .toLowerCase(Locale.ROOT);
            if (format.equals("pbf") || format.equals("mvt"))
                throw new IOException("This MBTiles file holds vector tiles. "
                        + "Blast shows raster MBTiles (PNG or JPEG images).");
            if (format.equals("webp"))
                throw new IOException("This MBTiles file holds WebP images. "
                        + "Blast shows PNG or JPEG MBTiles.");
            int min = 0, max = 22;
            try (ResultSet rs = st.executeQuery("SELECT MIN(zoom_level), "
                    + "MAX(zoom_level) FROM tiles")) {
                if (rs.next() && rs.getObject(1) != null) {
                    min = rs.getInt(1);
                    max = rs.getInt(2);
                } else {
                    throw new IOException("This MBTiles file has no tiles.");
                }
            }
            String name = meta.getOrDefault("name", f.getName());
            String attr = meta.getOrDefault("attribution", "");
            // Attributions are often HTML: show the text.
            attr = attr.replaceAll("<[^>]*>", "").replace("&copy;", "©")
                    .replace("&amp;", "&").trim();
            double[] bounds = null;
            try {
                String[] b = meta.getOrDefault("bounds", "").split(",");
                if (b.length == 4) {
                    bounds = new double[4];
                    for (int i = 0; i < 4; i++)
                        bounds[i] = Double.parseDouble(b[i].trim());
                }
            } catch (NumberFormatException e) {
                bounds = null;
            }
            return TileSource.mbtiles(f, name, attr, min, max, bounds);
        } catch (SQLException e) {
            throw new IOException("Not an MBTiles file (" + e.getMessage()
                    + ").");
        }
    }
}
