package com.atakmap.android.blast.plugin.core.screen;

import com.atakmap.android.blast.plugin.core.BlastRecord;
import com.atakmap.android.blast.plugin.core.Geo;
import com.atakmap.android.blast.plugin.core.NucRecord;
import com.atakmap.android.blast.plugin.core.UxoRecord;
import com.atakmap.android.blast.plugin.core.port.PositionSource;
import com.atakmap.android.blast.plugin.core.port.Team;

import java.util.ArrayList;
import java.util.List;

/**
 * The open card's two live lines (YOU and Team) from the incident's
 * position and the {@link PositionSource} and {@link Team} ports. Hosts
 * call one of these every refresh and put the results in the card's
 * {@code you} and {@code team} slots.
 */
public final class Live {

    private Live() {
    }

    /** {you, team} for a bomb threat at (lat, lon). */
    public static Text[] standoff(BlastRecord r, double lat, double lon,
            PositionSource pos, Team team) {
        PositionSource.Fix f = fix(pos);
        return new Text[] {
                f == null ? StandoffCard.you(r, false, Double.NaN, Double.NaN)
                        : StandoffCard.you(r, true, Geo.distance(lat, lon,
                                f.lat, f.lon), f.accuracyM),
                StandoffCard.team(r, distances(lat, lon, team)) };
    }

    /** {you, team} for military ordnance at (lat, lon). */
    public static Text[] uxo(UxoRecord r, double lat, double lon,
            PositionSource pos, Team team) {
        PositionSource.Fix f = fix(pos);
        return new Text[] {
                f == null ? UxoCard.you(r, false, Double.NaN, Double.NaN)
                        : UxoCard.you(r, true, Geo.distance(lat, lon, f.lat,
                                f.lon), f.accuracyM),
                UxoCard.team(r, distances(lat, lon, team)) };
    }

    /** {you, team} for a nuclear reference with ground zero at (lat, lon). */
    public static Text[] nuc(NucRecord r, double lat, double lon,
            PositionSource pos, Team team) {
        PositionSource.Fix f = fix(pos);
        List<double[]> others = others(team);
        double[][] en = new double[others.size()][];
        for (int i = 0; i < en.length; i++)
            en[i] = eastNorth(lat, lon, others.get(i)[0], others.get(i)[1]);
        return new Text[] {
                NucCard.you(r, f == null ? null
                        : eastNorth(lat, lon, f.lat, f.lon)),
                NucCard.team(r, en) };
    }

    /**
     * Metres east and north of (lat, lon) along the geodesic (distance and
     * initial bearing), or null if the distance is unknown.
     */
    static double[] eastNorth(double lat, double lon, double pLat,
            double pLon) {
        double d = Geo.distance(lat, lon, pLat, pLon);
        if (Double.isNaN(d))
            return null;
        double b = Math.toRadians(Geo.bearing(lat, lon, pLat, pLon));
        return new double[] { d * Math.sin(b), d * Math.cos(b) };
    }

    private static PositionSource.Fix fix(PositionSource pos) {
        return pos == null ? null : pos.fix();
    }

    private static List<double[]> others(Team team) {
        List<double[]> l = team == null ? null : team.others();
        return l == null ? new ArrayList<double[]>() : l;
    }

    private static double[] distances(double lat, double lon, Team team) {
        List<double[]> others = others(team);
        double[] d = new double[others.size()];
        for (int i = 0; i < d.length; i++)
            d[i] = Geo.distance(lat, lon, others.get(i)[0], others.get(i)[1]);
        return d;
    }
}
