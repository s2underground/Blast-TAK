package com.atakmap.android.blast.plugin.core.port;

/** Host service: facts about the host's map. */
public interface MapControl {

    /** The map centre, or null if it isn't a valid position. */
    PositionSource.Fix center();

    /**
     * A position written as an MGRS grid reference the way the host shows
     * it (e.g. "16T EN 18272 54221"); may throw if it can't.
     */
    String mgrs(double lat, double lon);

    /**
     * A compass (magnetic) bearing taken at a position, as a true bearing
     * (the host's declination model there); may throw if it can't.
     */
    double magneticToTrue(double lat, double lon, double magneticDeg);
}
