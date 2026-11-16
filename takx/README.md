# Blast for TAK-X

Blast as a TAK-X app plugin: the same app core as the ATAK plugin and the
shared Swing pane (`../desktop/.../swing`), docked on the right of TAK-X,
with incidents drawn on the TAK-X globe as TAK-X shapes.

Version 0.1 (refactor step 6b-1): incidents stay on this computer and are
not kept when the plugin stops; nothing is sent or received yet.

## Build

TAK-X plugins compile against jars from a TAK-X install. They are licensed
and must never be committed. With TAK-X installed (Java 17 or later):

    cd takx
    gradle jar -PtakxInstallDir="C:/Program Files/TAKX-CIV"
    gradle install -PtakxInstallDir="C:/Program Files/TAKX-CIV"

or, with the four jars copied to a folder,
`gradle jar -PtakxLibs=/path/to/jars` (platform-5.8.0, takkernel-shared and
takkernel-engine 9.11.5, worldwind 2.2.1-TAKX-4).

Install by copying `build/libs/blast-takx-<version>.jar` into
`<TAK-X install>/plugins`, then restart TAK-X and start Blast from the
app plugins.
