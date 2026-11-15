package com.atakmap.android.blast.plugin.core.port;

/** Host service: the current time. */
public interface Clock {

    /** Milliseconds since 1970-01-01 UTC. */
    long nowMillis();
}
