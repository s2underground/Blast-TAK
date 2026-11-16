# Blast for TAK-X

Blast as a TAK-X app plugin: the same app core as the ATAK plugin and the
shared Swing pane (`../desktop/.../swing`), docked on the right of TAK-X,
with incidents drawn on the TAK-X globe as TAK-X shapes.

Version 0.1.5 (refactor step 6b-2): incidents are kept across restarts
(in `~/.blast-takx`) and are sent and received as the same CoT ATAK Blast
uses, so ATAK and TAK-X users see each other's incidents. Incidents
received from other devices are drawn by TAK-X itself (it imports the
CoT); Blast adds their cards, and draws one itself once it is changed
here.

## Build

TAK-X plugins compile against jars from a TAK-X install. They are licensed
and must never be committed. Build with a **JDK 17** installed (the build
asks Gradle for a Java 17 compiler): newer compilers write class details
that TAK-X 5.8's class indexer can't store, and TAK-X then silently leaves
the plugin out of its list. `gradle build` / `gradle install` run
`checkIndex`, which indexes the jar the way TAK-X does and fails if it
can't. With TAK-X installed:

    cd takx
    gradle jar -PtakxInstallDir="C:/Program Files/TAKX-CIV"
    gradle install -PtakxInstallDir="C:/Program Files/TAKX-CIV"

or, with the four jars copied to a folder,
`gradle jar -PtakxLibs=/path/to/jars` (platform-5.8.0, takkernel-shared and
takkernel-engine 9.11.5, worldwind 2.2.1-TAKX-4).

Install by copying `build/libs/plugin.blast-<version>.jar` into
`<TAK-X install>/plugins`, then restart TAK-X and start Blast from the
app plugins.
