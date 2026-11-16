package com.atakmap.android.blast.plugin.core.port;

import com.atakmap.android.blast.plugin.core.BlastRecord;
import com.atakmap.android.blast.plugin.core.NucRecord;
import com.atakmap.android.blast.plugin.core.UxoRecord;

import java.util.List;
import java.util.Map;

/**
 * Host service: the incidents on this device's map and how to change
 * them. Incidents are known by the uid of their marker.
 */
public interface Incidents {

    /** One incident as the host holds it: exactly one record is set. */
    final class Incident {
        public final String uid;
        /** The name the host shows for it (map label). */
        public final String displayName;
        /** The stored name (the marker's callsign). */
        public final String name;
        /** Position; NaN when the host has none. */
        public final double lat, lon;
        public final BlastRecord standoff;
        public final UxoRecord uxo;
        public final NucRecord nuc;

        public Incident(String uid, String displayName, String name,
                double lat, double lon, BlastRecord standoff, UxoRecord uxo,
                NucRecord nuc) {
            this.uid = uid;
            this.displayName = displayName;
            this.name = name;
            this.lat = lat;
            this.lon = lon;
            this.standoff = standoff;
            this.uxo = uxo;
            this.nuc = nuc;
        }

        public boolean cleared() {
            return nuc != null ? nuc.isCleared()
                    : uxo != null ? uxo.isCleared()
                            : standoff != null && standoff.isCleared();
        }
    }

    /** Every incident on the map. */
    List<Incident> all();

    /** The incident if it is still on the map, else null. */
    Incident get(String uid);

    /**
     * Put a new incident on the map at a position (with the height and
     * errors it came with): its marker, rings and shapes, named by the
     * host's numbering. Returns its uid, or null if it could not be placed.
     */
    String create(PositionSource.Fix at, BlastRecord r);

    String create(PositionSource.Fix at, UxoRecord r);

    String create(PositionSource.Fix at, NucRecord r);

    /** Save a changed record; false if the incident is gone. */
    boolean write(String uid, BlastRecord r);

    boolean write(String uid, UxoRecord r);

    boolean write(String uid, NucRecord r);

    /**
     * Rename (the typed name is cleaned; CLEARED is kept or added to match
     * the status). False if the name is empty or the incident is gone.
     */
    boolean rename(String uid, String typed);

    /** Remove the incident (marker and every drawn part) on this device. */
    void delete(String uid);

    /**
     * The CoT events the host would send for this incident (marker and
     * drawn parts), by item uid, for a data package; empty if none.
     */
    Map<String, String> events(String uid);
}
