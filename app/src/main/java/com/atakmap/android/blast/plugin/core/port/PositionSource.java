package com.atakmap.android.blast.plugin.core.port;

/**
 * Host service: the user's own position. Optional on every host (a desktop
 * may or may not have a GPS), so {@link #fix()} may return null.
 */
public interface PositionSource {

    /**
     * A usable position. Height and the host's raw error values ride along
     * so that an incident placed there keeps them (the core never reads
     * them).
     */
    final class Fix {
        public final double lat, lon;
        /** Horizontal accuracy (CE) in metres, NaN if unknown. */
        public final double accuracyM;
        /**
         * As the host had them: height above the ellipsoid (m), circular
         * and linear error (m); NaN where unknown.
         */
        public final double haeM, ceM, leM;

        public Fix(double lat, double lon, double accuracyM) {
            this(lat, lon, accuracyM, Double.NaN, accuracyM, Double.NaN);
        }

        public Fix(double lat, double lon, double accuracyM, double haeM,
                double ceM, double leM) {
            this.lat = lat;
            this.lon = lon;
            this.accuracyM = accuracyM;
            this.haeM = haeM;
            this.ceM = ceM;
            this.leM = leM;
        }

        /** A computed position (no height or errors). */
        public static Fix at(double lat, double lon) {
            return new Fix(lat, lon, Double.NaN);
        }
    }

    /** The current fix, or null when there is no usable position. */
    Fix fix();
}
