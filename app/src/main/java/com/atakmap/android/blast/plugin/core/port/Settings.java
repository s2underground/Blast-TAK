package com.atakmap.android.blast.plugin.core.port;

/** Host service: small settings kept between runs (key → value). */
public interface Settings {

    /** The stored text, or {@code def} if none. */
    String getString(String key, String def);

    void putString(String key, String value);

    boolean getBoolean(String key, boolean def);

    void putBoolean(String key, boolean value);
}
