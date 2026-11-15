package com.atakmap.android.blast.plugin.core.port;

/**
 * Host service: the user's own position. Optional on every host (a desktop
 * may or may not have a GPS), so {@link #fix()} may return null.
 */
public interface PositionSource {

    /** A usable own position. */
    final class Fix {
        public final double lat, lon;
        /** Horizontal accuracy (CE) in metres, NaN if unknown. */
        public final double accuracyM;

        public Fix(double lat, double lon, double accuracyM) {
            this.lat = lat;
            this.lon = lon;
            this.accuracyM = accuracyM;
        }
    }

    /** The current fix, or null when there is no usable position. */
    Fix fix();
}
