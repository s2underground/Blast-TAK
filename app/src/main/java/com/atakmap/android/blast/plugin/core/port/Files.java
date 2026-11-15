package com.atakmap.android.blast.plugin.core.port;

import java.io.IOException;

/** Host service: the folder exports are saved in. */
public interface Files {

    /**
     * Save a file in the export folder. {@code fileName} has only letters,
     * digits, '-', '_' and its extension; hosts still check it stays in
     * the folder. Returns the saved file's name.
     */
    String save(String fileName, byte[] data) throws IOException;

    /** Where exports go, as the user would find it (e.g. "atak/export/Blast"). */
    String folderLabel();
}
