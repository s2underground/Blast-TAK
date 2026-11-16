package com.atakmap.android.blast.plugin.desktop;

import com.atakmap.android.blast.plugin.core.NucText;
import com.atakmap.android.blast.plugin.core.StandoffTable;
import com.atakmap.android.blast.plugin.core.app.BlastApp;
import com.atakmap.android.blast.plugin.core.app.FormDialog;
import com.atakmap.android.blast.plugin.core.app.Outcome;
import com.atakmap.android.blast.plugin.core.overlay.OverlayShape;
import com.atakmap.android.blast.plugin.core.overlay.Overlays;
import com.atakmap.android.blast.plugin.core.port.MapControl;
import com.atakmap.android.blast.plugin.core.port.PositionSource;
import com.atakmap.android.blast.plugin.core.screen.Dialog;
import com.atakmap.android.blast.plugin.core.screen.FormValues;
import com.atakmap.android.blast.plugin.core.screen.IncidentList;
import com.atakmap.android.blast.plugin.core.screen.MainPane;
import com.atakmap.android.blast.plugin.core.screen.ReferenceTab;
import com.atakmap.android.blast.plugin.core.screen.Screen;
import com.atakmap.android.blast.plugin.core.screen.StandoffCard;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 * Blast on a desktop, without ATAK: the same core (screens, actions,
 * placing, forms) behind a Swing pane and a plain map. The host side here
 * is only rendering, the map and the ports, like the ATAK layer.
 */
public final class BlastDesktop {

    private final DesktopPorts ports;
    private final BlastApp app;
    private final MapPanel map;
    private final StandoffTable table = StandoffTable.current();
    final JFrame frame = new JFrame("Blast (desktop test host)");
    private final SwingScreen.Column root = new SwingScreen.Column();
    private final JScrollPane scroll;
    private final JLabel status = new JLabel(" ");

    private SwingScreen.Column incidentsView, referenceView, zoneCard;
    SwingScreen.Rendered headerView, refView, listView, newView, cardView;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BlastDesktop(new File(
                System.getProperty("user.home"), ".blast-desktop"))
                        .frame.setVisible(true));
    }

    BlastDesktop(File home) {
        try {
            UIManager.setLookAndFeel(UIManager
                    .getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            // default look
        }
        ports = new DesktopPorts(home, this::toast);
        // Desktop map starts on the National Mall (no GPS assumed).
        double lat = Double.parseDouble(ports.getString("map.lat", "38.8895"));
        double lon = Double.parseDouble(ports.getString("map.lon", "-77.0353"));
        MapControl mc = new MapControl() {
            @Override
            public PositionSource.Fix center() {
                return map.center();
            }

            @Override
            public String mgrs(double la, double lo) {
                // No MGRS library in this host: the core falls back to
                // latitude / longitude.
                throw new UnsupportedOperationException("no MGRS");
            }

            @Override
            public double magneticToTrue(double la, double lo, double deg) {
                // No declination model here: the core asks for true bearings.
                throw new UnsupportedOperationException("no declination");
            }
        };
        app = new BlastApp(ports, ports, ports, ports, ports, ports, ports,
                ports, mc);
        map = new MapPanel(ports, lat, lon, app::ringLabels, uid -> {
            app.select(uid);
            showTab(false, null);
            refresh();
        });
        ports.changed = map::repaint;

        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        headerView = SwingScreen.render(MainPane.header(table, false), root,
                a -> tap(a, new FormValues()));
        incidentsView = new SwingScreen.Column();
        listView = SwingScreen.render(IncidentList.placeholder(),
                incidentsView, a -> tap(a, new FormValues()));
        zoneCard = new SwingScreen.Column();
        incidentsView.add(zoneCard);
        newView = SwingScreen.render(app.newIncident(null), incidentsView,
                this::newAction);
        SwingScreen.render(new Screen().add(MainPane.footer()), incidentsView,
                null);
        referenceView = new SwingScreen.Column();
        refView = SwingScreen.render(ReferenceTab.screen(
                app.emergencyNumber()), referenceView,
                a -> tap(a, new FormValues()));
        referenceView.setVisible(false);
        root.add(incidentsView);
        root.add(referenceView);

        JPanel pane = new JPanel(new BorderLayout());
        pane.setBackground(SwingScreen.BG);
        scroll = new JScrollPane(root);
        scroll.getViewport().setBackground(SwingScreen.BG);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        pane.add(scroll);
        pane.setPreferredSize(new Dimension(470, 800));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, map,
                pane);
        split.setResizeWeight(1);
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        status.setOpaque(true);
        status.setBackground(new Color(0x111111));
        status.setForeground(SwingScreen.TEXT);
        frame.getContentPane().add(split);
        frame.getContentPane().add(status, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                PositionSource.Fix c = map.center();
                ports.putString("map.lat", Double.toString(c.lat));
                ports.putString("map.lon", Double.toString(c.lon));
            }
        });
        frame.setSize(1400, 900);
        app.resetNew();
        renderNew();
        refresh();
        new Timer(3000, e -> refresh()).start();
    }

    // ------------------------------------------------------------------
    // Rendering

    void refresh() {
        BlastApp.Frame f = app.frame();
        if (f.cardChanged) {
            zoneCard.removeAll();
            cardView = null;
            if (f.card != null) {
                java.util.function.Consumer<String> act = a -> tap(a,
                        cardView == null ? new FormValues()
                                : cardView.values());
                SwingScreen.render(f.header, zoneCard, act);
                SwingScreen.Column card = new SwingScreen.Column();
                card.setOpaque(true);
                card.setBackground(SwingScreen.CARD);
                card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
                zoneCard.add(card);
                cardView = SwingScreen.render(f.card, card, act);
            }
        }
        if (cardView != null) {
            SwingScreen.apply(cardView.slots.get(StandoffCard.SLOT_YOU),
                    f.you);
            SwingScreen.apply(cardView.slots.get(StandoffCard.SLOT_TEAM),
                    f.team);
        }
        if (f.list != null)
            listView.update(f.list);
        root.revalidate();
        root.repaint();
        map.repaint();
    }

    private void renderNew() {
        newView.update(app.newIncident(null));
        root.revalidate();
    }

    private void updateNew() {
        newView.update(app.newIncident(newView.values()));
        root.revalidate();
    }

    void tap(String action, FormValues v) {
        Outcome o = app.tap(action, v);
        if (o != null)
            handle(o);
    }

    void newAction(String action) {
        Outcome o = app.tapNew(action, newView.values());
        if (o != null)
            handle(o);
    }

    private void showTab(boolean reference, String section) {
        stopPick();
        incidentsView.setVisible(!reference);
        referenceView.setVisible(reference);
        headerView.update(MainPane.header(table, reference));
        root.revalidate();
        SwingUtilities.invokeLater(() -> {
            JComponent a = section == null ? null
                    : refView.slots.get(ReferenceTab.anchor(section));
            int y = a == null ? 0 : SwingUtilities.convertPoint(a, 0, 0,
                    root).y;
            scroll.getViewport().setViewPosition(new java.awt.Point(0, y));
        });
    }

    /** Cancel a map tap from here (tab change). */
    private void stopPick() {
        if (map.stopPick()) {
            app.pickCancelled();
            updateNew();
        }
    }

    /** Carry out what the core asked for. */
    void handle(Outcome o) {
        if (o.toast != null)
            toast(o.toast);
        if (o.dialog != null)
            show(o.dialog);
        if (o.restyle != null && cardView != null)
            cardView.update(o.restyle);
        if (o.labels)
            map.repaint();
        if (o.settings)
            refView.update(new Screen().add(ReferenceTab.emergency(
                    app.emergencyNumber())));
        if (o.flyTo != null)
            fly(o.flyTo);
        if (o.gone) {
            app.afterDelete();
            refresh();
        } else if (o.card || o.list) {
            app.invalidate(o.card, o.list);
            refresh();
        }
        if (o.reference != null)
            showTab(o.reference, o.section);
        if (o.stopPick)
            map.stopPick();
        if (o.pick != null)
            map.startPick(o.pick, (at, onMarker) -> handle(app.picked(at,
                    onMarker, newView.values())));
        if (o.newIncident)
            renderNew();
        if (o.focus != null) {
            JTextField e = newView.entries.get(o.focus);
            if (e != null)
                e.requestFocusInWindow();
        }
        if (o.top)
            SwingUtilities.invokeLater(() -> scroll.getViewport()
                    .setViewPosition(new java.awt.Point(0, 0)));
        if (o.form != null)
            show(o.form);
        if (o.next != null)
            handle(o.next);
    }

    /** Frame the map on an incident's outer ring. */
    private void fly(String uid) {
        DesktopPorts.Stored s = ports.stored(uid);
        if (s == null)
            return;
        List<OverlayShape> shapes = s.nuc != null
                ? Overlays.nucRings(s.nuc) : s.uxo != null
                        ? Overlays.uxo(s.uxo) : Overlays.standoff(s.standoff);
        double r = 100;
        for (OverlayShape sh : shapes)
            if (sh.kind == OverlayShape.Kind.CIRCLE
                    && (sh.id.equals(Overlays.PREF)
                            || sh.id.equals(Overlays.CORDON)
                            || sh.id.equals(NucText.Ring.LDZ.suffix)))
                r = sh.radiusM;
        map.frame(s.lat, s.lon, r);
    }

    void toast(String s) {
        status.setText(new SimpleDateFormat("HH:mm:ss", Locale.US)
                .format(new Date()) + "  " + s);
    }

    // ------------------------------------------------------------------
    // Dialogs

    private JDialog dialog(String title) {
        JDialog d = new JDialog(frame, title, true);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.getContentPane().setBackground(SwingScreen.CARD);
        return d;
    }

    private static JPanel buttonBar() {
        JPanel b = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.RIGHT));
        b.setOpaque(false);
        return b;
    }

    private static JButton dialogButton(String label) {
        JButton b = new JButton(label);
        SwingScreen.style(b, SwingScreen.BTN);
        return b;
    }

    /** A core dialog; its buttons go back to the core. */
    private void show(Dialog d) {
        JDialog w = dialog(d.title);
        SwingScreen.Column body = new SwingScreen.Column();
        body.setBorder(BorderFactory.createEmptyBorder(12, 14, 6, 14));
        if (d.message != null)
            body.add(SwingScreen.textArea(d.message, 13, SwingScreen.TEXT,
                    false, false));
        JTextField field = null;
        if (d.hasField()) {
            field = SwingScreen.field(d.fieldMaxLength, false);
            field.setText(d.fieldValue);
            if (d.fieldSelectAll)
                field.selectAll();
            body.add(field);
        }
        List<JCheckBox> ticks = new ArrayList<>();
        if (d.options != null)
            for (int i = 0; i < d.options.size(); i++) {
                JCheckBox cb = new JCheckBox(d.options.get(i), d.checked(i));
                cb.setOpaque(false);
                cb.setForeground(SwingScreen.TEXT);
                ticks.add(cb);
                body.add(cb);
            }
        if (d.items != null) {
            JList<String> list = new JList<>(d.items.toArray(new String[0]));
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setBackground(SwingScreen.CARD);
            list.setForeground(SwingScreen.TEXT);
            list.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            list.setFixedCellHeight(30);
            list.addListSelectionListener(e -> {
                if (e.getValueIsAdjusting() || list.getSelectedIndex() < 0)
                    return;
                int i = list.getSelectedIndex();
                w.dispose();
                handle(app.choose(d, d.itemAction, new FormValues().put(
                        Dialog.ITEM, Integer.toString(i))));
            });
            JScrollPane sp = new JScrollPane(list);
            sp.setPreferredSize(new Dimension(560, Math.min(420,
                    30 * d.items.size() + 4)));
            body.add(sp);
        }
        JPanel bar = buttonBar();
        JTextField input = field;
        addChoice(bar, w, d, d.negative, null, input, ticks);
        addChoice(bar, w, d, d.neutral, d.neutralAction, input, ticks);
        addChoice(bar, w, d, d.positive, d.positiveAction, input, ticks);
        w.getContentPane().add(body);
        w.getContentPane().add(bar, BorderLayout.SOUTH);
        w.pack();
        w.setSize(Math.max(w.getWidth(), 420), w.getHeight());
        w.setLocationRelativeTo(frame);
        w.setVisible(true);
    }

    private void addChoice(JPanel bar, JDialog w, Dialog d, String label,
            String action, JTextField input, List<JCheckBox> ticks) {
        if (label == null)
            return;
        JButton b = dialogButton(label);
        b.addActionListener(e -> {
            w.dispose();
            if (action == null)
                return;
            FormValues v = new FormValues();
            if (input != null)
                v.put(Dialog.FIELD, input.getText());
            for (int i = 0; i < ticks.size(); i++)
                v.put(Dialog.option(i), ticks.get(i).isSelected());
            handle(app.choose(d, action, v));
        });
        bar.add(b);
    }

    /** A core form; its live lines and buttons go back to the core. */
    private void show(FormDialog f) {
        JDialog w = dialog(f.title);
        SwingScreen.Column box = new SwingScreen.Column();
        box.setBorder(BorderFactory.createEmptyBorder(f.padTopDp + 4,
                f.padDp, 6, f.padDp));
        SwingScreen.FormView[] fv = new SwingScreen.FormView[1];
        Runnable live = () -> {
            if (fv[0] == null)
                return;
            for (Map.Entry<String, String> e : f.live(fv[0].values())
                    .entrySet())
                fv[0].set(e.getKey(), e.getValue());
        };
        fv[0] = SwingScreen.form(f.form, box, live);
        live.run();
        JComponent content = box;
        if (f.scrolls) {
            JScrollPane sp = new JScrollPane(box);
            sp.setBorder(null);
            sp.getViewport().setBackground(SwingScreen.CARD);
            sp.getVerticalScrollBar().setUnitIncrement(24);
            sp.setPreferredSize(new Dimension(520, 620));
            content = sp;
        }
        JPanel bar = buttonBar();
        if (f.negative != null) {
            JButton b = dialogButton(f.negative);
            b.addActionListener(e -> w.dispose());
            bar.add(b);
        }
        for (String[] btn : new String[][] {
                { f.neutral, FormDialog.NEUTRAL },
                { f.positive, FormDialog.POSITIVE } }) {
            if (btn[0] == null)
                continue;
            JButton b = dialogButton(btn[0]);
            b.addActionListener(e -> {
                w.dispose();
                handle(f.press(btn[1], fv[0].values()));
            });
            bar.add(b);
        }
        w.getContentPane().add(content);
        w.getContentPane().add(bar, BorderLayout.SOUTH);
        w.pack();
        w.setSize(Math.max(w.getWidth(), 460), w.getHeight());
        w.setLocationRelativeTo(frame);
        w.setVisible(true);
    }

    /** Open dialogs (for the demo script). */
    static List<Window> dialogs() {
        List<Window> out = new ArrayList<>();
        for (Window w : Window.getWindows())
            if (w instanceof JDialog && w.isVisible())
                out.add(w);
        return out;
    }

    MapPanel map() {
        return map;
    }

    DesktopPorts ports() {
        return ports;
    }
}
