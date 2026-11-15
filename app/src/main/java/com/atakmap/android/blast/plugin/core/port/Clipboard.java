package com.atakmap.android.blast.plugin.core.port;

/** Host service: the system clipboard. */
public interface Clipboard {

    /**
     * Put plain text on the clipboard. Returns false if there is no
     * clipboard (nothing is said to the user); may throw on failure.
     */
    boolean copy(String label, String text);
}
