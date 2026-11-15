package com.atakmap.android.blast.plugin.core.port;

import java.util.List;

/** Host service: the other people on the map. */
public interface Team {

    /**
     * Positions {lat, lon} of the other contacts the host shows (not the
     * user), in any order; empty when there are none.
     */
    List<double[]> others();
}
