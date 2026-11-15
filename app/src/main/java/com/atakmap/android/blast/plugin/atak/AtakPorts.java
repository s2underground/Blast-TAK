package com.atakmap.android.blast.plugin.atak;

import com.atakmap.android.blast.plugin.core.port.PositionSource;
import com.atakmap.android.blast.plugin.core.port.Team;
import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/** ATAK implementations of the core's position and team ports. */
public final class AtakPorts {

    private static final String TAG = "BlastPorts";

    private AtakPorts() {
    }

    /** The self marker's position; null without a usable fix. */
    public static PositionSource position(final MapView mapView) {
        return new PositionSource() {
            @Override
            public Fix fix() {
                try {
                    Marker self = ATAKUtilities.findSelf(mapView);
                    GeoPoint gp = self != null ? self.getPoint() : null;
                    if (gp == null || !gp.isValid() || (gp.getLatitude() == 0
                            && gp.getLongitude() == 0))
                        return null;
                    // CE is NaN when unknown; CotPoint uses 9999999.
                    double ce = gp.getCE();
                    boolean known = !Double.isNaN(ce) && ce > 0
                            && ce < 9999999;
                    return new Fix(gp.getLatitude(), gp.getLongitude(),
                            known ? ce : Double.NaN);
                } catch (RuntimeException e) {
                    Log.w(TAG, "no self position", e);
                    return null;
                }
            }
        };
    }

    /** Every individual contact's map item except the self marker. */
    public static Team team(final MapView mapView) {
        return new Team() {
            @Override
            public List<double[]> others() {
                List<double[]> out = new ArrayList<>();
                try {
                    Marker self = ATAKUtilities.findSelf(mapView);
                    for (Contact c : Contacts.getInstance().getAllContacts()) {
                        if (!(c instanceof IndividualContact))
                            continue;
                        MapItem mi = ((IndividualContact) c).getMapItem();
                        if (!(mi instanceof PointMapItem) || mi == self)
                            continue;
                        GeoPoint gp = ((PointMapItem) mi).getPoint();
                        if (gp != null && gp.isValid())
                            out.add(new double[] { gp.getLatitude(),
                                    gp.getLongitude() });
                    }
                } catch (RuntimeException e) {
                    Log.w(TAG, "contact scan failed", e);
                }
                return out;
            }
        };
    }
}
