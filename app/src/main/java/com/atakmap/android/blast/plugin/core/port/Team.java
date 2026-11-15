package com.atakmap.android.blast.plugin.core.port;

import java.util.List;

/** Host service: the user's callsign and the other people on the map. */
public interface Team {

    /** The user's own callsign ("" if unknown). */
    String callsign();

    /**
     * Positions {lat, lon} of the other contacts the host shows (not the
     * user), in any order; empty when there are none.
     */
    List<double[]> others();
}
