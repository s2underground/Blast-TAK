# Blast's core: a guide

Blast is split in two:

- **The core** (`app/src/main/java/com/atakmap/android/blast/plugin/core/`)
  is everything Blast knows and decides: the standoff tables, the UXO and
  nuclear models, what to draw, what every card, list, form and dialog
  says, what every button does, the reports, the exports and the CoT it
  shares. It is plain Java 8 with no Android, ATAK, TAK-X or Swing code,
  so it runs anywhere Java runs.
- **Hosts** put it on a screen. A host draws what the core describes,
  passes the user's taps back to it, and provides a few services through
  small interfaces (the "ports"). Hosts in this repository:
  - `atak/` + `ui/` + `BlastPlugin`: the ATAK plugin (Android views).
  - `desktop/`: Blast Desktop for Windows, macOS and Linux (Swing, a
    tile map, sharing with ATAK on the local network).
  - `takx/`: the TAK-X plugin (Swing pane, TAK-X globe and CoT).

The same core runs unchanged in all three. This guide is for anyone
reading the core, and for the other S2 Underground plugins, which are
being split the same way and reuse its building blocks.

## The rules the core keeps

These are checked by tests, not just conventions.

1. **Plain Java only.** No `android.*`, `androidx.*`, `com.atakmap.*`
   (other than the core's own package), `gov.tak.*`, `gov.takx.*`,
   `javax.swing.*`, `java.awt.*` or `org.json.*` (`CoreBoundaryTest`).
2. **Same output on every machine.** Every number format, case change,
   date and calendar names its `Locale` and time zone (UTC); no default
   locale or zone; no clock reads: time comes in through the `Clock` port
   or as an argument (`CoreBoundaryTest`). The golden tests run under a
   second locale and time zone and must produce identical files.
3. **Runs on Android 5** (Blast's minimum, API 21). Java 8 language
   features are fine (lambdas are desugared), but not the library classes
   Android only has from 7 or 8: `java.util.function`, streams,
   `java.time`, `java.nio.file`, `Base64`, `Optional`, the new
   `Map`/`List` default methods, `List.sort`, `String.join` and so on
   (`CoreBoundaryTest`). The core also compiles with `javac --release 8`.
4. **The CoT wire format never changes.** The `<__blast>`,
   `<__blast_uxo>` and `<__blast_nuc>` details, their attribute names and
   values stay as released, so every version interoperates (codec tests
   and goldens).
5. **Received data is untrusted.** Anything decoded from another device
   is validated (unusable records decode to null), and everything the
   core then makes from it stays finite, bounded and well-formed
   (`ReceivedDataFuzzTest`).

## Packages

| Package | What it holds | Reusable by other plugins? |
|---|---|---|
| `core` | Blast's domain: tables and models (`StandoffTable`, `UxoTable`, `NucModel`, `NucPresets`), records and their CoT codecs (`BlastRecord`/`BlastCodec`, `UxoRecord`/`UxoCodec`, `NucRecord`/`NucCodec`), text (`Labels`, `UxoText`, `NucText`, references, reports), exports (`Exports`: KML, KMZ, GPX, data packages), geodesy (`Geo`, `NucGeo`, `Polygons`), `Mgrs` (MGRS both ways, UTM and UPS, checked against GeoTrans and ATAK) and `Wmm` (World Magnetic Model 2025, for hosts without MGRS or declination of their own), `CotEvents` (the CoT a host sends, for hosts that build CoT themselves) | Blast's own, but `Geo`, `Mgrs`, `Wmm`, `Formats`, `Exports` (zip, data-package manifest) and `CotEvents` are good starting points |
| `core/overlay` | **What to draw.** `OverlayShape` (CIRCLE with a radius, POLYGON, POLYLINE; an `id` suffix appended to the owner's uid; a title; a `Style`: stroke and fill ARGB, width, line DEFAULT / SOLID / DASHED / DOTTED). `Overlays` builds Blast's shapes per incident. | `OverlayShape` and `Style`: yes, copy as is. `Overlays` is Blast's. |
| `core/screen` | **What a screen shows**, as data. Generic items: `Screen`, `Item`, `Text`, `Tone`, `Ink`, `Legend`, `KeyRow`, `Button`, `Buttons`, `TitleRow`, `ListRow`, `Section`, `Input`, `Check`, `Choice`, `EntryRow`, `FormValues`, `Dialog`. Blast's screens built from them: `StandoffCard`, `UxoCard`, `NucCard`, `WindPicker`, `NewIncident`, `IncidentList`, `MainPane`, `ReferenceTab`, `Live`, `CardActions` and the forms (`Nbc1Form`, `UxoReportForm`, `RangeBearingForm`, `PolarForm`). | The generic items: yes. The screens: as examples. |
| `core/port` | **What a host provides.** `Incidents` (the incidents on the map: list, create, write, rename, delete, their CoT for a data package), `Share` (send to all, send to chosen contacts, delete for everyone), `Team` (own callsign, other contacts' positions), `PositionSource` (an optional fix: a desktop may have none), `MapControl` (map centre, MGRS, magnetic to true), `Clock`, `Clipboard`, `Files` (export folder), `Settings`. `Magnetic` is used inside the core (built from `MapControl`). | The pattern, and `PositionSource`, `Clock`, `Clipboard`, `Files`, `Settings` nearly as they are. |
| `core/app` | **The app itself.** `BlastApp` (the controller), `Outcome` (what the host must do next), `FormDialog` (a form in a dialog), and the work behind them: `IncidentActions` (card buttons and their dialogs), `Placement` (new incidents, map taps), `CardForms` (NBC 1, EOD 9-line). | The pattern: controller + `Outcome` + `FormDialog`. |

## How a host drives the core

A host creates one `BlastApp` with its ports, then:

1. **Draws.** On every refresh it calls `app.frame()`. The `Frame` says
   whether the open card changed (then draw `header` and `card`, or hide
   the card when they are null), the card's two live lines (`you`,
   `team`: update in place), and the incident list if it changed. It
   draws the new-incident section from `app.newIncident(...)` and the
   map from `Overlays` for each incident.
2. **Passes taps back.** Every button carries an action id. Card, list,
   tab and Reference buttons go to `app.tap(action, values)`, the
   new-incident section to `app.tapNew(...)`, dialog buttons to
   `app.choose(dialog, action, values)`, form buttons to
   `form.press(button, values)`. `values` (`FormValues`) holds what is in
   that screen's boxes and ticks.
3. **Carries out the `Outcome`** that comes back, field by field in the
   order they are declared: toast, dialog, restyle the card in place,
   relabel the map, settings changed, fly to, incident gone, redraw card
   / list, switch tab (and scroll to a Reference section), stop / start a
   map tap, redraw the new-incident section, focus a box, scroll to top,
   show a form, then `next`.
4. **Map taps are a request and a reply.** When an `Outcome` has `pick`,
   the host shows that prompt and listens for one map tap; when it comes
   it calls `app.picked(fix, onMarker, values)` and carries out that
   `Outcome` too. If the host stops listening on its own (tab change,
   pane closed) it calls `app.pickCancelled()`.

The core never calls the host back on its own: the host asks, the core
answers. That keeps the core free of threads and UI toolkits, and makes
every session reproducible in a test.

## Tests that hold it in place

Run them with the plugin's Gradle build (`testCivDebugUnitTest`).

- **Goldens** (`GoldenSnapshotTest`, files in
  `app/src/test/resources/golden/`): everything the core produces for a
  matrix of inputs, in text: records and CoT details, remarks, every card
  and dialog, reports, exports, overlay geometry, the CoT events, whole
  app sessions against fake ports that log every port call (`app.txt`,
  `actions.txt`, `place.txt`). A change to any output shows as a diff.
  Regenerate on purpose with
  `./gradlew testCivDebugUnitTest -Pblast.golden.update=true` and read the
  diff before committing.
- **Boundary and rules** (`CoreBoundaryTest`): the rules above.
- **Received data** (`ReceivedDataFuzzTest`): thousands of mutated
  records through the whole core.
- **Unit tests** for the codecs, models, formats and exports.

## Reusing it in another plugin

1. Copy `core/overlay/OverlayShape.java` and `Style.java`, the generic
   `core/screen` items listed above, and `CoreBoundaryTest`, into the new
   plugin's own `core` package (change the package name; keep the class
   names so the plugins stay alike).
2. Write the plugin's own domain classes, its overlay builder (like
   `Overlays`) and its screens from the generic items.
3. Define only the ports the plugin needs, in the same style: small
   interfaces, plain values, the core never assumes a port has data
   (no GPS, no MGRS, a failed send are all normal answers).
4. Write the controller the same way: state in the controller, `frame()`
   for what to draw, `tap()` / `choose()` returning an `Outcome`.
5. Lock behaviour with goldens before moving code, as Blast did, so the
   refactor can't change what users see.

## Building the hosts

- ATAK plugin: the usual plugin build (`./gradlew assembleCivDebug`,
  tests `testCivDebugUnitTest`, lint `lintCivDebug`). The TAK.gov source
  archive leaves `desktop/` and `takx/` out (`.gitattributes`).
- Desktop: `cd desktop && gradle run` (Java 17); installers with
  `gradle jpackage` (JDK 21) or the `Blast Desktop` GitHub workflow (see
  `desktop/README.md`).
- TAK-X: `cd takx && gradle build -PtakxInstallDir=<TAK-X install>`. It
  compiles against jars from a TAK-X install, which are licensed and are
  never committed. Build with a JDK 17 compiler (see `takx/README.md`).
