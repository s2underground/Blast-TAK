package com.atakmap.android.blast.plugin.core.port;

/** Host service: facts about the host's map. */
public interface MapControl {

    /** The map centre {lat, lon}, or null. */
    double[] center();

    /**
     * A position written as an MGRS grid reference the way the host shows
     * it (e.g. "16T EN 18272 54221"); may throw if it can't.
     */
    String mgrs(double lat, double lon);
}
