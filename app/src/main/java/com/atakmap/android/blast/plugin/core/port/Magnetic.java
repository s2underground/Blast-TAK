package com.atakmap.android.blast.plugin.core.port;

/**
 * Host service: magnetic to true bearing at the user's position (the host
 * knows the declination). First of the core "ports".
 */
public interface Magnetic {

    /** True bearing in degrees, or NaN if the declination is unavailable. */
    double toTrue(double magneticDeg);
}
