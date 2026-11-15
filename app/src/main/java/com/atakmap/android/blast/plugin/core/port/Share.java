package com.atakmap.android.blast.plugin.core.port;

/** Host service: sending incidents to other TAK clients. */
public interface Share {

    /** Broadcast the incident to everyone; returns the number of items sent. */
    int sendToAll(String uid);

    /** Let the user choose recipients (the host's own contact picker). */
    void sendTo(String uid);

    /** Ask every client to remove the incident, then remove it here. */
    void deleteForEveryone(String uid);
}
