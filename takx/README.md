# Blast for TAK-X

Blast as a TAK-X app plugin. It has the same features as Blast 1.2.1 for
ATAK (bomb threats, military ordnance, the nuclear reference, cards,
reports, exports and the Reference tab), docked on the right of TAK-X,
with incidents drawn on the TAK-X globe. ATAK and TAK-X users share
incidents both ways: Blast for TAK-X sends and receives the same CoT as
ATAK Blast (bomb threats with 1.0 and newer; military ordnance and nuclear
incidents with 1.1 and newer).

## Install

Requires TAK-X 5.8 (TAKX-CIV 5.8, Java 17, as installed).

1. Download `plugin.blast-1.2.1.zip` from the release and unzip it. It holds
   one file, `plugin.blast-1.2.1.jar`. Keep that name: TAK-X recognises
   plugins by it.
2. Close TAK-X and copy the jar into the `plugins` folder of your TAK-X
   install (for example `C:\Program Files\TAKX\plugins`). Remove any older
   `plugin.blast-*.jar` from that folder.
3. Start TAK-X, open the **Plugins** panel, select **Blast**, then
   **Install** (first time) and **Start**. Blast opens on the right.

If Blast doesn't appear in the list, `takx.log` in the TAK-X folder says
why.

## Using it

- Everything on the cards works as in the ATAK plugin and its user
  manual. Place incidents by clicking the globe, at the globe's centre,
  or at your position if TAK-X has one (a GPS receiver or a fixed
  location).
- **Send to all** and **Send to…** (pick contacts from TAK-X's contact
  list) send to your TAK network. Incidents others send appear on the
  globe and in Blast's list; click the marker to open its card.
- **Deleting an incident's centre marker** deletes the incident on this
  computer, as on ATAK. **Delete… → for everyone** removes it from other
  devices too.
- Your incidents and settings are kept in the `.blast-takx` folder in your
  user folder.

Blast's distances are minimums, not guarantees, and the nuclear mode is a
planning and training reference, not a prediction of a real event. It
does not replace professional EOD, bomb squad, emergency management or
department procedures.

## Build

TAK-X plugins compile against jars from a TAK-X install. Those jars are
licensed and must never be committed. Build with a **JDK 17** installed
(the build asks Gradle for a Java 17 compiler). Newer compilers write
class details that TAK-X 5.8's class indexer can't store, and TAK-X then
silently leaves the plugin out of its list. `gradle build` / `gradle
install` run `checkIndex`, which indexes the jar the way TAK-X does and
fails if it can't. With TAK-X installed:

    cd takx
    gradle jar -PtakxInstallDir="C:/Program Files/TAKX-CIV"
    gradle install -PtakxInstallDir="C:/Program Files/TAKX-CIV"

or, with the four jars copied to a folder,
`gradle jar -PtakxLibs=/path/to/jars` (platform-5.8.0, takkernel-shared and
takkernel-engine 9.11.5, worldwind 2.2.1-TAKX-4).

The plugin is `build/libs/plugin.blast-<version>.jar`. It contains Blast's
core (`../app/src/main/java/.../core`, unchanged) and the shared Swing pane
(`../desktop/.../swing`); see `../docs/CORE.md`.
