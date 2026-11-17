# Blast Desktop

Blast for Windows, macOS and Linux: the same cards, buttons, new-incident
steps, forms, reports, exports and Reference tab as the ATAK plugin, on a
map, with no ATAK or TAK server needed. Incidents are shared with ATAK
Blast (1.0 and newer) on the local network the way ATAK's mesh does.

It runs Blast's core unchanged (`../app/src/main/java/.../core`, see
`../docs/CORE.md`) with the shared Swing pane (`src/.../swing`, also used
by Blast for TAK-X). Not part of the ATAK plugin build or its TAK.gov
source zip.

## Install

Download the file for your computer from the release:

| Computer | File |
|---|---|
| Windows 10 / 11 (64-bit) | `Blast-Desktop-1.2-windows-x64.msi` (installer, no administrator needed) or `…-windows-x64-portable.zip` (unzip and run `Blast.exe`) |
| Mac with Apple silicon (M1 and later) | `Blast-Desktop-1.2-macos-arm64.dmg` |
| Mac with Intel | `Blast-Desktop-1.2-macos-x64.dmg` |
| Linux (Debian, Ubuntu and similar, 64-bit) | `Blast-Desktop-1.2-linux-x64.deb` (`sudo apt install ./Blast-Desktop-1.2-linux-x64.deb`) or `…-linux-x64-portable.tar.gz` (unpack and run `Blast/bin/Blast`) |

Java comes with it; nothing else to install. `SHA256SUMS.txt` lists each
file's checksum.

The installers aren't code-signed, so the first start asks:

- **Windows:** "Windows protected your PC" → **More info** → **Run
  anyway**.
- **macOS:** open the dmg, drag Blast to Applications, then in
  Applications **right-click Blast → Open → Open**. If macOS says Blast
  "is damaged", run `xattr -dr com.apple.quarantine /Applications/Blast.app`
  in Terminal and open it again.
- **Linux:** no prompt.

Your incidents, settings and map cache are kept in `.blast-desktop` in
your user folder (uninstalling Blast leaves them).

## Using it

Everything on the cards works as in the ATAK plugin and its user manual.
What's different on a desktop:

- **Map:** choose it top left. OpenStreetMap and the USGS maps need the
  internet; places you have viewed stay in the map cache and show offline
  (**Maps ▾ → Map cache…** sets its size or empties it). **Maps ▾ → Open an
  MBTiles file…** shows an offline raster map file (PNG or JPEG tiles);
  **Add a tile address (XYZ)…** adds another online map whose terms allow
  it. **No map (grid)** shows latitude/longitude lines only. Drag to pan,
  wheel or + / − to zoom, double-click to zoom in. The grid reference and
  latitude/longitude under the pointer are shown bottom left.
- **Your position:** a computer has no GPS. **My position…** takes a grid
  reference or latitude, longitude, or right-click the map → **Set my
  position here**. Blast uses it for "place at my position", distances to
  you and the card's YOU line, and remembers it.
- **Go to…** centres the map on a grid reference or latitude, longitude.
  Right-click the map to copy a point's grid reference.
- **Bearings:** compass (magnetic) bearings are converted with the World
  Magnetic Model 2025, as ATAK does.

### Sharing with ATAK (Network…)

For ATAK devices on the same Wi-Fi or network with no TAK server
("mesh"). On by default; **Network…** sets it up:

- **Callsign:** your name on other devices.
- **Send to all** on a card sends the incident to every device on the
  network (ATAK's multicast group 239.2.3.1, port 6969), exactly as ATAK
  Blast sends it. **Send to…** sends to contacts you pick.
- Incidents others send to everyone appear on the map and in the list;
  **Delete… → for everyone** removes one from other devices too.
- **Share my position** (off by default) makes this computer a contact on
  ATAK, at the position you typed, so ATAK users can see it and send to it
  directly (TCP port 4242). Contacts that share their position show on the
  map (**Contacts** lists them) and count on each card's Team line.
- The first time, your firewall may ask whether Blast may use the network:
  allow it on private networks. Some Wi-Fi networks (guest, hotel,
  corporate) block multicast; then nothing arrives.

Blast Desktop doesn't connect to TAK servers. For that, use Blast for TAK-X
or ATAK.

Blast's distances are minimums, not guarantees, and the nuclear mode is a
planning and training reference, not a prediction of a real event. It does
not replace professional EOD, bomb squad, emergency management or
department procedures.

## Build and run from source

With a JDK 17 or later:

    cd desktop
    gradle run        # start it
    gradle test       # the desktop's own tests (the core's run with the plugin)

Installers are built with a JDK 21 `jpackage` on each operating system:

    gradle jpackage                          # msi / dmg / deb for this OS
    gradle jpackage -PinstallerType=app-image  # portable zip / tar.gz

They land in `build/dist`. GitHub Actions builds all of them
(`.github/workflows/desktop.yml`): push a tag `desktop-v<version>` and it
drafts a release with the installers and their checksums, or run it from
the Actions tab to get them as artifacts.

Third-party software and data: `packaging/content/THIRD-PARTY-NOTICES.txt`
(installed with the app).
