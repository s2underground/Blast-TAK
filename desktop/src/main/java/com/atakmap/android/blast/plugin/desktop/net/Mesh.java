package com.atakmap.android.blast.plugin.desktop.net;

import com.atakmap.android.blast.plugin.core.BlastCodec;
import com.atakmap.android.blast.plugin.core.BlastRecord;
import com.atakmap.android.blast.plugin.core.CotEvents;
import com.atakmap.android.blast.plugin.core.Names;
import com.atakmap.android.blast.plugin.core.NucCodec;
import com.atakmap.android.blast.plugin.core.NucRecord;
import com.atakmap.android.blast.plugin.core.UxoCodec;
import com.atakmap.android.blast.plugin.core.UxoRecord;
import com.atakmap.android.blast.plugin.core.port.PositionSource;
import com.atakmap.android.blast.plugin.core.port.Share;
import com.atakmap.android.blast.plugin.core.port.Team;
import com.atakmap.android.blast.plugin.swing.MemoryPorts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

/**
 * Sharing over the local network the way ATAK's mesh does, with no TAK
 * server: CoT on the SA multicast group 239.2.3.1:6969 (what ATAK's
 * "send to all" uses without a server), direct sends to a contact over TCP
 * (its SA endpoint, usually port 4242), and this computer's own TCP port
 * 4242 for devices that send to it. Received events may be XML or TAK
 * Protocol v1 (protobuf); this side sends XML, which every ATAK reads.
 *
 * <p>Received Blast incidents and remote deletes go into the desktop's
 * incidents; contacts' positions (their SA) are shown on the map and used
 * for "Team" distances. Sending your own position (SA) is optional.
 */
public final class Mesh implements Share, Team {

    public static final String GROUP = "239.2.3.1";
    public static final int PORT = 6969;
    public static final int TCP_PORT = 4242;
    private static final long SA_EVERY_S = 30;
    private static final long SA_STALE_MS = 6 * 60 * 1000L;
    private static final int MAX_STREAM = 4 << 20;

    /** Another device on the network, from its SA. */
    public static final class Contact {
        public final String uid;
        public volatile String callsign, endpoint, team;
        public volatile double lat, lon;
        public volatile long staleMs;

        Contact(String uid) {
            this.uid = uid;
        }

        /** Host and port for a direct send, or null (server only). */
        public String[] direct() {
            if (endpoint == null)
                return null;
            String[] p = endpoint.split(":");
            if (p.length != 3 || p[0].isEmpty() || p[0].equals("*")
                    || !p[2].equalsIgnoreCase("tcp"))
                return null;
            try {
                int port = Integer.parseInt(p[1]);
                return port > 0 && port < 65536 ? new String[] { p[0], p[1] }
                        : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /** What the host supplies. */
    public interface Hooks {
        void toast(String s);

        /** The ring labels setting (the circles' labels_on). */
        boolean labels();

        /** Something received: redraw the map and the pane. */
        void changed();

        /** Pick contacts to send to (empty: cancelled). */
        List<Contact> pick(String what, List<Contact> people);
    }

    private final MemoryPorts ports;
    private final Hooks hooks;
    private final Map<String, Contact> contacts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer = Executors
            .newSingleThreadScheduledExecutor(r -> daemon(r, "blast-sa"));
    private volatile MulticastSocket rx;
    private volatile ServerSocket tcp;
    private volatile List<NetworkInterface> nets = Collections.emptyList();
    private volatile String status = "Off";
    private volatile boolean running;

    public Mesh(MemoryPorts ports, Hooks hooks) {
        this.ports = ports;
        this.hooks = hooks;
        timer.scheduleWithFixedDelay(this::beacon, 5, SA_EVERY_S,
                TimeUnit.SECONDS);
        timer.scheduleWithFixedDelay(this::expire, 60, 60, TimeUnit.SECONDS);
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    // ------------------------------------------------------------------
    // Settings

    public boolean enabled() {
        return ports.getBoolean("net.on", true);
    }

    public boolean sharesPosition() {
        return ports.getBoolean("net.sa", false);
    }

    public String teamColor() {
        return ports.getString("net.team", "Cyan");
    }

    /** "" for every network, or one interface's name. */
    public String only() {
        return ports.getString("net.if", "");
    }

    /** This computer's uid on the network (made once). */
    public String deviceUid() {
        String u = ports.getString("net.uid", "");
        if (u.isEmpty()) {
            u = "BLAST-DESKTOP-" + UUID.randomUUID();
            ports.putString("net.uid", u);
        }
        return u;
    }

    public String status() {
        return status;
    }

    /** Apply the settings: (re)start or stop. */
    public synchronized void restart() {
        stop();
        if (!enabled()) {
            status = "Off: incidents are not sent or received.";
            return;
        }
        start();
    }

    // ------------------------------------------------------------------
    // Interfaces

    /** Networks that can carry the mesh (up, multicast, IPv4). */
    public static List<NetworkInterface> candidates() {
        List<NetworkInterface> l = new ArrayList<>();
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface
                    .getNetworkInterfaces())) {
                if (!n.isUp() || n.isLoopback() || !n.supportsMulticast()
                        || n.isVirtual())
                    continue;
                if (ipv4(n) != null)
                    l.add(n);
            }
        } catch (SocketException e) {
            // none
        }
        return l;
    }

    static Inet4Address ipv4(NetworkInterface n) {
        for (InetAddress a : Collections.list(n.getInetAddresses()))
            if (a instanceof Inet4Address && !a.isLoopbackAddress())
                return (Inet4Address) a;
        return null;
    }

    private List<NetworkInterface> chosen() {
        List<NetworkInterface> all = candidates();
        String only = only();
        if (only.isEmpty())
            return all;
        List<NetworkInterface> l = new ArrayList<>();
        for (NetworkInterface n : all)
            if (n.getName().equals(only))
                l.add(n);
        return l;
    }

    // ------------------------------------------------------------------
    // Start and stop

    private void start() {
        List<NetworkInterface> use = chosen();
        nets = use;
        if (use.isEmpty()) {
            status = "No network found (Wi-Fi or Ethernet with an IPv4 "
                    + "address).";
            return;
        }
        running = true;
        StringBuilder names = new StringBuilder();
        try {
            MulticastSocket s = new MulticastSocket(PORT);
            InetSocketAddress group = new InetSocketAddress(InetAddress
                    .getByName(GROUP), PORT);
            int joined = 0;
            for (NetworkInterface n : use) {
                try {
                    s.joinGroup(group, n);
                    joined++;
                    if (names.length() > 0)
                        names.append(", ");
                    names.append(label(n));
                } catch (IOException e) {
                    // that one can't
                }
            }
            if (joined == 0) {
                s.close();
                status = "Couldn't join the mesh group on any network.";
                running = false;
                return;
            }
            rx = s;
            daemon(() -> receiveLoop(s), "blast-mesh-rx").start();
        } catch (IOException e) {
            status = "Can't listen on port " + PORT + ": " + e.getMessage();
            running = false;
            return;
        }
        String direct;
        try {
            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(TCP_PORT));
            tcp = ss;
            daemon(() -> acceptLoop(ss), "blast-mesh-tcp").start();
            direct = "devices can send to this computer on port " + TCP_PORT
                    + ".";
        } catch (IOException e) {
            direct = "port " + TCP_PORT + " is in use (another TAK app?), so "
                    + "devices can only send to everyone, not to this "
                    + "computer directly.";
        }
        status = "On: " + names + "; " + direct;
        timer.execute(this::beacon);
    }

    private synchronized void stop() {
        running = false;
        MulticastSocket s = rx;
        rx = null;
        if (s != null)
            s.close();
        ServerSocket t = tcp;
        tcp = null;
        if (t != null) {
            try {
                t.close();
            } catch (IOException e) {
                // closed
            }
        }
    }

    public void shutdown() {
        stop();
        timer.shutdownNow();
    }

    static String label(NetworkInterface n) {
        Inet4Address a = ipv4(n);
        String d = n.getDisplayName();
        return (d == null || d.isEmpty() ? n.getName() : d)
                + (a == null ? "" : " (" + a.getHostAddress() + ")");
    }

    // ------------------------------------------------------------------
    // Receiving

    private void receiveLoop(MulticastSocket s) {
        byte[] buf = new byte[65536];
        while (running && !s.isClosed()) {
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                s.receive(p);
                packet(p.getData(), p.getLength());
            } catch (IOException e) {
                if (s.isClosed())
                    return;
            } catch (RuntimeException e) {
                // a bad packet: next
            }
        }
    }

    /** One datagram: TAK Protocol v1 or XML. */
    void packet(byte[] b, int len) {
        String xml;
        if (TakProto.isMesh(b, len))
            xml = TakProto.meshToXml(b, len);
        else
            xml = new String(b, 0, len, StandardCharsets.UTF_8);
        received(xml);
    }

    private void acceptLoop(ServerSocket ss) {
        while (running && !ss.isClosed()) {
            try {
                Socket c = ss.accept();
                daemon(() -> stream(c), "blast-mesh-in").start();
            } catch (IOException e) {
                if (ss.isClosed())
                    return;
            }
        }
    }

    /** A TCP connection: XML events, or TAK Protocol v1 stream frames. */
    private void stream(Socket c) {
        try (Socket s = c; InputStream in = s.getInputStream()) {
            s.setSoTimeout(30_000);
            ByteArrayOutputStream got = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int done = 0;
            while ((n = in.read(buf)) > 0) {
                got.write(buf, 0, n);
                if (got.size() > MAX_STREAM)
                    return;
                done = drain(got.toByteArray(), done);
            }
        } catch (IOException | RuntimeException e) {
            // closed or unreadable: done
        }
    }

    /** Handle the complete events in b from {@code from}; returns the end. */
    int drain(byte[] b, int from) {
        int i = from;
        while (i < b.length) {
            if ((b[i] & 0xFF) == 0xBF) {
                // Stream frame: 0xBF, varint length, TakMessage.
                int p = i + 1;
                long len = 0;
                int shift = 0;
                while (true) {
                    if (p >= b.length)
                        return i;
                    int x = b[p++] & 0xFF;
                    len |= (long) (x & 0x7F) << shift;
                    shift += 7;
                    if ((x & 0x80) == 0)
                        break;
                    if (shift > 35)
                        return b.length; // garbage
                }
                if (len > MAX_STREAM)
                    return b.length;
                if (p + len > b.length)
                    return i;
                received(TakProto.takMessageToXml(b, p, p + (int) len));
                i = p + (int) len;
            } else {
                String rest = new String(b, i, b.length - i,
                        StandardCharsets.UTF_8);
                int end = rest.indexOf("</event>");
                if (end < 0)
                    return i;
                String one = rest.substring(0, end + 8);
                received(one.trim());
                i += one.getBytes(StandardCharsets.UTF_8).length;
            }
        }
        return i;
    }

    /** One event (XML), from either path. */
    void received(String xml) {
        if (xml == null)
            return;
        int start = xml.indexOf('<');
        if (start < 0)
            return;
        CotIn e = CotIn.parse(xml.substring(start));
        if (e == null)
            return;
        if (deviceUid().equals(e.uid) || sentRecently(xml))
            return; // our own, back from the network
        if (CotEvents.DELETE_TYPE.equals(e.type)) {
            deleted(e);
        } else if (CotEvents.MARKER_TYPE.equals(e.type)
                && (e.hasDetail(NucCodec.DETAIL) || e.hasDetail(UxoCodec
                        .DETAIL) || e.hasDetail(BlastCodec.DETAIL))) {
            incident(e);
        } else if (e.endpoint != null && e.callsign != null
                && e.type.startsWith("a-") && e.hasPosition()) {
            contact(e);
        }
    }

    private void incident(CotIn e) {
        if (!e.hasPosition() || e.uid.length() > 128)
            return;
        NucRecord n = null;
        UxoRecord u = null;
        BlastRecord s = null;
        try {
            String x;
            if ((x = e.detailXml(NucCodec.DETAIL)) != null)
                n = NucCodec.fromXml(x);
            else if ((x = e.detailXml(UxoCodec.DETAIL)) != null)
                u = UxoCodec.fromXml(x);
            else if ((x = e.detailXml(BlastCodec.DETAIL)) != null)
                s = BlastCodec.fromXml(x);
        } catch (RuntimeException ex) {
            return;
        }
        if (n == null && u == null && s == null)
            return;
        String name = Names.sanitize(e.callsign);
        if (name == null)
            name = n != null ? Names.initialNuc(n.number, n.plainLabel())
                    : u != null ? Names.initialUxo(u.number, u.plainLabel())
                            : Names.initial(s.number, s.rowLabel);
        String nm = name, uid = e.uid;
        double lat = e.lat, lon = e.lon;
        NucRecord fn = n;
        UxoRecord fu = u;
        BlastRecord fs = s;
        SwingUtilities.invokeLater(() -> {
            boolean isNew = ports.stored(uid) == null;
            ports.put(uid, nm, lat, lon, fs, fu, fn, false);
            hooks.changed();
            if (isNew)
                hooks.toast("Received " + nm + ".");
        });
    }

    private void deleted(CotIn e) {
        String uid = e.linkUid;
        if (uid == null || uid.isEmpty() || uid.length() > 128)
            return;
        contacts.remove(uid);
        SwingUtilities.invokeLater(() -> {
            MemoryPorts.Stored s = ports.stored(uid);
            if (s != null) {
                ports.delete(uid);
                hooks.changed();
                hooks.toast(s.name + " was deleted by its sender.");
            }
        });
    }

    private void contact(CotIn e) {
        if (contacts.size() > 2000 && !contacts.containsKey(e.uid))
            return;
        Contact c = contacts.computeIfAbsent(e.uid, Contact::new);
        String cs = Names.sanitize(e.callsign);
        c.callsign = cs == null ? e.uid : cs;
        c.endpoint = e.endpoint;
        c.team = e.team;
        c.lat = e.lat;
        c.lon = e.lon;
        long now = System.currentTimeMillis();
        c.staleMs = e.staleMs > now ? Math.min(e.staleMs, now + 24 * 3600
                * 1000L) : now + SA_STALE_MS;
        SwingUtilities.invokeLater(hooks::changed);
    }

    private void expire() {
        long now = System.currentTimeMillis();
        if (contacts.values().removeIf(c -> c.staleMs < now))
            SwingUtilities.invokeLater(hooks::changed);
    }

    /** Contacts heard recently, by callsign. */
    public List<Contact> contacts() {
        long now = System.currentTimeMillis();
        List<Contact> l = new ArrayList<>();
        for (Contact c : contacts.values())
            if (c.staleMs >= now)
                l.add(c);
        l.sort((a, b) -> a.callsign.toLowerCase(Locale.ROOT).compareTo(
                b.callsign.toLowerCase(Locale.ROOT)));
        return l;
    }

    // ------------------------------------------------------------------
    // Team

    @Override
    public String callsign() {
        return ports.callsign();
    }

    @Override
    public List<double[]> others() {
        List<double[]> l = new ArrayList<>();
        for (Contact c : contacts())
            l.add(new double[] { c.lat, c.lon });
        return l;
    }

    // ------------------------------------------------------------------
    // Sending

    private CotEvents.Sender me() {
        return new CotEvents.Sender(deviceUid(), ports.callsign());
    }

    /** The incident's events as they would be sent now. */
    List<CotEvents.Event> events(String uid) {
        MemoryPorts.Stored s = ports.stored(uid);
        if (s == null)
            return Collections.emptyList();
        long now = ports.nowMillis();
        boolean labels = hooks.labels();
        return s.nuc != null ? CotEvents.incident(s.uid, s.name, s.lat,
                s.lon, s.nuc, me(), now, labels)
                : s.uxo != null ? CotEvents.incident(s.uid, s.name, s.lat,
                        s.lon, s.uxo, me(), now, labels)
                        : CotEvents.incident(s.uid, s.name, s.lat, s.lon,
                                s.standoff, me(), now, labels);
    }

    /** What we multicast in the last few seconds (to skip our echo). */
    private final Map<String, Long> sent = new ConcurrentHashMap<>();

    private boolean sentRecently(String xml) {
        long now = System.currentTimeMillis();
        sent.values().removeIf(t -> now - t > 10_000);
        return sent.containsKey(xml.trim());
    }

    /** Multicast one event on every network in use; true if any took it. */
    private boolean multicast(String xml) {
        sent.put(xml.trim(), System.currentTimeMillis());
        byte[] data = xml.getBytes(StandardCharsets.UTF_8);
        if (data.length > 65000)
            return false;
        boolean any = false;
        try (MulticastSocket out = new MulticastSocket()) {
            out.setTimeToLive(ttl());
            InetAddress group = InetAddress.getByName(GROUP);
            for (NetworkInterface n : nets) {
                try {
                    out.setNetworkInterface(n);
                    out.send(new DatagramPacket(data, data.length, group,
                            PORT));
                    any = true;
                } catch (IOException e) {
                    // not this one
                }
            }
        } catch (IOException e) {
            return false;
        }
        return any;
    }

    /** Hops for multicast (ATAK's default reaches the local network). */
    private int ttl() {
        try {
            return Math.max(1, Math.min(32, Integer.parseInt(ports.getString(
                    "net.ttl", "1"))));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private boolean ready() {
        if (!enabled()) {
            hooks.toast("The local network is off (Network menu): not "
                    + "sent.");
            return false;
        }
        if (rx == null || nets.isEmpty()) {
            hooks.toast("Not connected: " + status);
            return false;
        }
        return true;
    }

    @Override
    public int sendToAll(String uid) {
        if (!ready())
            return 0;
        int n = 0;
        for (CotEvents.Event e : events(uid))
            if (multicast(e.xml))
                n++;
        return n;
    }

    @Override
    public void sendTo(String uid) {
        MemoryPorts.Stored s = ports.stored(uid);
        if (s == null || !ready())
            return;
        List<Contact> people = new ArrayList<>();
        for (Contact c : contacts())
            if (c.direct() != null)
                people.add(c);
        if (people.isEmpty()) {
            hooks.toast("No contacts that can be sent to directly. Contacts "
                    + "appear when their devices share their position on "
                    + "this network.");
            return;
        }
        List<Contact> chosen = hooks.pick(s.name, people);
        if (chosen.isEmpty())
            return;
        List<CotEvents.Event> evs = events(uid);
        String name = s.name;
        daemon(() -> {
            int ok = 0;
            List<String> failed = new ArrayList<>();
            for (Contact c : chosen) {
                if (direct(c, evs))
                    ok++;
                else
                    failed.add(c.callsign);
            }
            String msg = failed.isEmpty() ? "Sent " + name + " to "
                    + (ok == 1 ? chosen.get(0).callsign : ok + " contacts")
                    + "." : "Sent " + name + " to " + ok + " of "
                            + chosen.size() + ". Couldn't reach "
                            + String.join(", ", failed) + ".";
            SwingUtilities.invokeLater(() -> hooks.toast(msg));
        }, "blast-send").start();
    }

    /** Send events to one contact over TCP. */
    private static boolean direct(Contact c, List<CotEvents.Event> evs) {
        String[] hp = c.direct();
        if (hp == null)
            return false;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(hp[0], Integer.parseInt(hp[1])),
                    5000);
            s.setSoTimeout(10_000);
            OutputStream out = s.getOutputStream();
            for (CotEvents.Event e : evs)
                out.write(e.xml.getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public void deleteForEveryone(String uid) {
        if (enabled() && rx != null && !nets.isEmpty()) {
            long now = ports.nowMillis();
            for (CotEvents.Event e : events(uid))
                multicast(CotEvents.delete(e.uid, e.type, now));
        } else {
            hooks.toast("Not connected: deleted on this computer only.");
        }
        ports.delete(uid);
    }

    // ------------------------------------------------------------------
    // Our own position (SA), if the user shares it

    private void beacon() {
        try {
            if (!running || !sharesPosition())
                return;
            PositionSource.Fix f = ports.fix();
            if (f == null)
                return;
            for (NetworkInterface n : nets) {
                Inet4Address a = ipv4(n);
                String endpoint = tcp != null && a != null
                        ? a.getHostAddress() + ":" + TCP_PORT + ":tcp"
                        : "*:-1:stcp";
                sendSa(n, sa(f, endpoint));
            }
        } catch (RuntimeException e) {
            // try again next time
        }
    }

    /** Send our SA now (after a change). */
    public void beaconNow() {
        timer.execute(this::beacon);
    }

    private void sendSa(NetworkInterface n, String xml) {
        byte[] data = xml.getBytes(StandardCharsets.UTF_8);
        try (MulticastSocket out = new MulticastSocket()) {
            out.setTimeToLive(ttl());
            out.setNetworkInterface(n);
            out.send(new DatagramPacket(data, data.length, InetAddress
                    .getByName(GROUP), PORT));
        } catch (IOException e) {
            // next time
        }
    }

    String sa(PositionSource.Fix f, String endpoint) {
        long now = System.currentTimeMillis();
        String cs = TakProto.esc(ports.callsign());
        String os = TakProto.esc(System.getProperty("os.name", "") + " "
                + System.getProperty("os.version", ""));
        return "<?xml version='1.0' encoding='UTF-8' standalone='yes'?>"
                + "<event version='2.0' uid='" + TakProto.esc(deviceUid())
                + "' type='a-f-G-U-C' time='" + time(now) + "' start='"
                + time(now) + "' stale='" + time(now + SA_STALE_MS)
                + "' how='h-e'><point lat='" + f.lat + "' lon='" + f.lon
                + "' hae='9999999.0' ce='9999999.0' le='9999999.0'/>"
                + "<detail><contact endpoint='" + TakProto.esc(endpoint)
                + "' callsign='" + cs + "'/><__group name='" + TakProto.esc(
                        teamColor()) + "' role='Team Member'/>"
                + "<takv device='Desktop' platform='Blast Desktop' os='" + os
                + "' version='1.2'/><uid Droid='" + cs + "'/>"
                + "<precisionlocation geopointsrc='USER' altsrc='???'/>"
                + "</detail></event>";
    }

    private static String time(long ms) {
        SimpleDateFormat f = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ms));
    }
}
