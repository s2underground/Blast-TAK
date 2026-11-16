package com.atakmap.android.blast.plugin.core.port;

/**
 * Magnetic to true bearing at one position. Not a host port any more:
 * {@code core/app} builds one from {@link MapControl#magneticToTrue} for
 * the user's position and hands it to the forms (NBC 1, polar plot).
 */
public interface Magnetic {

    /** True bearing in degrees, or NaN if the declination is unavailable. */
    double toTrue(double magneticDeg);
}
