# Blast desktop test host

Blast's app core running on a plain Java desktop, with no ATAK: the same
cards, buttons, new-incident steps, placing, forms and Reference tab, drawn
in Swing, plus a plain offline map that draws the core's overlay shapes.
It is a test of the "app core + thin TAK layer" split (refactor step 6) and
the start of the Swing panel a TAK-X Blast will reuse.

Not part of the ATAK plugin build or its TAK.gov source zip.

Run with Java 17 or later:

    cd desktop
    gradle run

What it does not have: map imagery, networking (Send only reports what it
would send), MGRS (grid references fall back to latitude / longitude) and
a magnetic declination model (enter true bearings). Right-click the map to
set a "GPS" position. Incidents are kept in memory; settings and exports go
to `~/.blast-desktop/`.
