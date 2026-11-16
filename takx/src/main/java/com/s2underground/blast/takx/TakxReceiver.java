package com.s2underground.blast.takx;

import com.atakmap.android.blast.plugin.core.BlastCodec;
import com.atakmap.android.blast.plugin.core.BlastRecord;
import com.atakmap.android.blast.plugin.core.CotEvents;
import com.atakmap.android.blast.plugin.core.Names;
import com.atakmap.android.blast.plugin.core.NucCodec;
import com.atakmap.android.blast.plugin.core.NucRecord;
import com.atakmap.android.blast.plugin.core.UxoCodec;
import com.atakmap.android.blast.plugin.core.UxoRecord;
import com.atakmap.android.blast.plugin.swing.MemoryPorts;

import gov.tak.api.comms.commo.ICotEventListener;
import gov.tak.api.cot.event.CotDetail;
import gov.tak.api.cot.event.CotEvent;
import gov.tak.api.cot.importer.ImportResult;
import gov.tak.api.engine.map.coords.IGeoPoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

import javax.swing.SwingUtilities;

/**
 * Incidents other devices send (ATAK Blast or another TAK-X): a Blast
 * marker ({@code a-h-G} with a Blast detail) becomes an incident here,
 * with its card; ATAK's remote delete for an incident's marker removes
 * it. TAK-X's own importers still take every event and draw the marker,
 * rings and outlines themselves, so received incidents are marked remote
 * and not drawn a second time (see {@link TakxShapes}). This listener
 * only observes: it always answers IGNORE.
 */
final class TakxReceiver implements ICotEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(
            TakxReceiver.class);

    /** Longer uids than this aren't Blast's (ATAK uses UUIDs). */
    private static final int MAX_UID = 128;

    private final MemoryPorts ports;

    TakxReceiver(MemoryPorts ports) {
        this.ports = ports;
    }

    @Override
    public CompletableFuture<ImportResult> cotEventReceived(CotEvent e) {
        try {
            if (e != null) {
                String type = e.getType();
                if (CotEvents.MARKER_TYPE.equals(type))
                    marker(e);
                else if (CotEvents.DELETE_TYPE.equals(type))
                    deleted(e);
            }
        } catch (RuntimeException | LinkageError ex) {
            LOG.warn("could not read a received event", ex);
        }
        return CompletableFuture.completedFuture(ImportResult.IGNORE);
    }

    private static boolean uidOk(String uid) {
        return uid != null && !uid.isEmpty() && uid.length() <= MAX_UID;
    }

    /** A marker with a Blast detail: add or update the incident. */
    private void marker(CotEvent e) {
        CotDetail nuc = e.findDetail(NucCodec.DETAIL);
        CotDetail uxo = nuc != null ? null : e.findDetail(UxoCodec.DETAIL);
        CotDetail std = nuc != null || uxo != null ? null
                : e.findDetail(BlastCodec.DETAIL);
        if (nuc == null && uxo == null && std == null)
            return; // not a Blast incident
        String uid = e.getUID();
        IGeoPoint p = e.getPoint();
        if (!uidOk(uid) || p == null)
            return;
        double lat = p.getLatitude(), lon = p.getLongitude();
        if (!(lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180))
            return;
        NucRecord n = null;
        UxoRecord u = null;
        BlastRecord s = null;
        try {
            if (nuc != null)
                n = NucCodec.fromXml(nuc.toString());
            else if (uxo != null)
                u = UxoCodec.fromXml(uxo.toString());
            else
                s = BlastCodec.fromXml(std.toString());
        } catch (RuntimeException ex) {
            LOG.info("a Blast marker {} that could not be read", uid, ex);
            return;
        }
        if (n == null && u == null && s == null)
            return;
        String name = Names.sanitize(e.getCallsign());
        if (name == null)
            name = n != null ? Names.initialNuc(n.number, n.plainLabel())
                    : u != null ? Names.initialUxo(u.number, u.plainLabel())
                            : Names.initial(s.number, s.rowLabel);
        final String nm = name;
        final NucRecord fn = n;
        final UxoRecord fu = u;
        final BlastRecord fs = s;
        LOG.info("received incident {} ({})", uid, nm);
        // TAK-X imports and draws this event too: the incident is remote
        // (drawn by TAK-X) from now on, even if it was ours.
        SwingUtilities.invokeLater(() -> ports.put(uid, nm, lat, lon, fs, fu,
                fn, true));
    }

    /** ATAK's remote delete: removes the incident if it names its marker. */
    private void deleted(CotEvent e) {
        CotDetail link = e.findDetail("link");
        String uid = link == null ? null : link.getAttribute("uid");
        if (!uidOk(uid))
            return;
        SwingUtilities.invokeLater(() -> {
            if (ports.stored(uid) != null) {
                LOG.info("incident {} deleted by its sender", uid);
                ports.delete(uid);
            }
        });
    }
}
