package com.atakmap.android.blast.plugin.swing;

import com.atakmap.android.blast.plugin.core.StandoffTable;
import com.atakmap.android.blast.plugin.core.app.BlastApp;
import com.atakmap.android.blast.plugin.core.app.FormDialog;
import com.atakmap.android.blast.plugin.core.app.Outcome;
import com.atakmap.android.blast.plugin.core.port.PositionSource;
import com.atakmap.android.blast.plugin.core.screen.Dialog;
import com.atakmap.android.blast.plugin.core.screen.FormValues;
import com.atakmap.android.blast.plugin.core.screen.IncidentList;
import com.atakmap.android.blast.plugin.core.screen.MainPane;
import com.atakmap.android.blast.plugin.core.screen.ReferenceTab;
import com.atakmap.android.blast.plugin.core.screen.Screen;
import com.atakmap.android.blast.plugin.core.screen.StandoffCard;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Blast's pane in Swing, for any desktop host (the plain desktop test host,
 * TAK-X): it renders what {@link BlastApp} returns, passes taps back and
 * carries out each {@link Outcome}, as BlastPane does on ATAK. The map
 * itself belongs to the host ({@link Host}).
 */
public final class SwingPane {

    /** What the pane needs from the host's map and window. */
    public interface Host {
        /** A short message for the user. */
        void toast(String message);

        /** Frame the map on an incident. */
        void fly(String uid);

        /** The labels setting changed: redraw labels. */
        void labelsChanged();

        /**
         * Wait for a click on the map, showing {@code prompt}; then call
         * {@code picked} (null if the click had no position).
         */
        void startPick(String prompt, Picked picked);

        /** Stop waiting for a click; false if not waiting. */
        boolean stopPick();

        /** The window dialogs belong to (may be null). */
        Window owner();
    }

    /** A map click for the core. */
    public interface Picked {
        void at(PositionSource.Fix where, boolean onMarker);
    }

    private final BlastApp app;
    private final Host host;
    private final StandoffTable table = StandoffTable.current();
    private final SwingScreen.Column root = new SwingScreen.Column();
    private final JScrollPane scroll;
    private final SwingScreen.Column incidentsView, referenceView, zoneCard;
    public final SwingScreen.Rendered headerView, refView, listView,
            newView;
    public SwingScreen.Rendered cardView;

    public SwingPane(BlastApp app, Host host) {
        this.app = app;
        this.host = host;
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
        scroll = new JScrollPane(root);
        scroll.getViewport().setBackground(SwingScreen.BG);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setPreferredSize(new Dimension(470, 800));
        app.resetNew();
        renderNew();
        refresh();
    }

    /** The pane, to dock or add to a window. */
    public JComponent component() {
        return scroll;
    }

    // ------------------------------------------------------------------
    // Rendering

    /** Redraw what changed (call on a timer and after outside changes). */
    public void refresh() {
        BlastApp.Frame f = app.frame();
        if (f.cardChanged) {
            zoneCard.removeAll();
            cardView = null;
            if (f.card != null) {
                Consumer<String> act = a -> tap(a, cardView == null
                        ? new FormValues() : cardView.values());
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
    }

    /** Open an incident's card (a click on its map item). */
    public void open(String uid) {
        app.select(uid);
        showTab(false, null);
        refresh();
    }

    /** The pane was opened again: no threat chosen (as on ATAK). */
    public void resetThreat() {
        host.stopPick();
        app.resetNew();
        renderNew();
    }

    private void renderNew() {
        newView.update(app.newIncident(null));
        root.revalidate();
    }

    private void updateNew() {
        newView.update(app.newIncident(newView.values()));
        root.revalidate();
    }

    public void tap(String action, FormValues v) {
        Outcome o = app.tap(action, v);
        if (o != null)
            handle(o);
    }

    public void newAction(String action) {
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
            int y = a == null ? 0
                    : SwingUtilities.convertPoint(a, 0, 0, root).y;
            scroll.getViewport().setViewPosition(new Point(0, y));
        });
    }

    /** Cancel a map tap from here (tab change, pane closed). */
    public void stopPick() {
        if (host.stopPick()) {
            app.pickCancelled();
            updateNew();
        }
    }

    /** Carry out what the core asked for. */
    public void handle(Outcome o) {
        if (o.toast != null)
            host.toast(o.toast);
        if (o.dialog != null)
            show(o.dialog);
        if (o.restyle != null && cardView != null)
            cardView.update(o.restyle);
        if (o.labels)
            host.labelsChanged();
        if (o.settings)
            refView.update(new Screen().add(ReferenceTab.emergency(
                    app.emergencyNumber())));
        if (o.flyTo != null)
            host.fly(o.flyTo);
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
            host.stopPick();
        if (o.pick != null)
            host.startPick(o.pick, (at, onMarker) -> SwingUtilities
                    .invokeLater(() -> handle(app.picked(at, onMarker,
                            newView.values()))));
        if (o.newIncident)
            renderNew();
        if (o.focus != null) {
            JTextField e = newView.entries.get(o.focus);
            if (e != null)
                e.requestFocusInWindow();
        }
        if (o.top)
            SwingUtilities.invokeLater(() -> scroll.getViewport()
                    .setViewPosition(new Point(0, 0)));
        if (o.form != null)
            show(o.form);
        if (o.next != null)
            handle(o.next);
    }

    // ------------------------------------------------------------------
    // Dialogs

    private JDialog dialog(String title) {
        JDialog d = new JDialog(host.owner(), title,
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
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
        JButton b = new JButton(SwingScreen.label(label));
        SwingScreen.style(b, SwingScreen.BTN);
        return b;
    }

    /**
     * Size and show a dialog. Wrapped text only knows its height once it
     * has its width, and pack() measures it before that (the rename
     * dialog's message took an extra line and pushed the name box out of
     * sight). So: pack, settle the width, lay out at that width, then take
     * the height the whole root pane needs there (it includes a title bar
     * the look and feel draws itself, as TAK-X's does), capped to the
     * screen; and once more after the dialog is on screen. Text boxes keep
     * their own height, and the first one gets the keyboard.
     */
    private void place(JDialog w) {
        w.getRootPane().putClientProperty(BLAST_DIALOG, Boolean.TRUE);
        javax.swing.text.JTextComponent first = prepareBoxes(
                w.getContentPane(), null);
        w.pack();
        int width = Math.max(w.getWidth(), 440);
        fit(w, width);
        fit(w, width);
        w.setLocationRelativeTo(scroll);
        w.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (fit(w, w.getWidth()))
                        w.setLocationRelativeTo(scroll);
                    if (first != null && !first.requestFocusInWindow())
                        first.requestFocus();
                });
            }
        });
        w.setVisible(true);
    }

    /** Marks Blast's dialogs (hosts can tell them apart, e.g. in logs). */
    public static final String BLAST_DIALOG = "blast.dialog";

    /**
     * Lay the dialog out at {@code width} and give it the height it needs
     * there (only growing once it is on screen). True if it was resized.
     */
    private static boolean fit(JDialog w, int width) {
        int maxH = Integer.MAX_VALUE;
        try {
            java.awt.GraphicsConfiguration gc = w.getGraphicsConfiguration();
            if (gc != null) {
                java.awt.Insets si = w.getToolkit().getScreenInsets(gc);
                maxH = gc.getBounds().height - si.top - si.bottom;
            }
        } catch (RuntimeException e) {
            // no screen information: no cap
        }
        w.setSize(width, w.getHeight());
        w.validate();
        // Layouts cache their children's sizes; wrapped text just got a
        // width (and so a new height) without telling them.
        invalidateAll(w.getRootPane());
        java.awt.Insets in = w.getInsets();
        int h = Math.min(w.getRootPane().getPreferredSize().height + in.top
                + in.bottom, maxH);
        if (w.isShowing() ? h <= w.getHeight() : h == w.getHeight())
            return false;
        w.setSize(width, h);
        w.validate();
        return true;
    }

    /**
     * Text boxes keep their preferred height (a column layout would
     * otherwise squash or stretch them), and a click in one always takes
     * the keyboard, even when the dialog's window isn't the active one.
     * Returns the first editable box.
     */
    private static javax.swing.text.JTextComponent prepareBoxes(
            java.awt.Container c, javax.swing.text.JTextComponent first) {
        for (java.awt.Component k : c.getComponents()) {
            if (k instanceof JTextField) {
                JTextField f = (JTextField) k;
                int h = f.getPreferredSize().height;
                f.setMinimumSize(new Dimension(f.getMinimumSize().width, h));
                f.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
            }
            if (k instanceof javax.swing.text.JTextComponent) {
                javax.swing.text.JTextComponent t =
                        (javax.swing.text.JTextComponent) k;
                if (t.isEditable() && t.isFocusable()) {
                    t.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override
                        public void mousePressed(java.awt.event.MouseEvent e) {
                            if (!t.isFocusOwner())
                                t.requestFocus();
                        }
                    });
                    if (first == null)
                        first = t;
                }
            } else if (k instanceof java.awt.Container) {
                first = prepareBoxes((java.awt.Container) k, first);
            }
        }
        return first;
    }

    private static void invalidateAll(java.awt.Container c) {
        c.invalidate();
        for (java.awt.Component k : c.getComponents())
            if (k instanceof java.awt.Container)
                invalidateAll((java.awt.Container) k);
            else
                k.invalidate();
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
                JCheckBox cb = new JCheckBox(SwingScreen.plain(
                        d.options.get(i)), d.checked(i));
                cb.setOpaque(false);
                cb.setForeground(SwingScreen.TEXT);
                ticks.add(cb);
                body.add(cb);
            }
        if (d.items != null) {
            String[] names = new String[d.items.size()];
            for (int i = 0; i < names.length; i++)
                names[i] = SwingScreen.plain(d.items.get(i));
            JList<String> list = new JList<>(names);
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
        choice(bar, w, d, d.negative, null, input, ticks);
        choice(bar, w, d, d.neutral, d.neutralAction, input, ticks);
        choice(bar, w, d, d.positive, d.positiveAction, input, ticks);
        w.getContentPane().add(body);
        w.getContentPane().add(bar, BorderLayout.SOUTH);
        place(w);
    }

    private void choice(JPanel bar, JDialog w, Dialog d, String label,
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
        place(w);
    }

    /** Open dialogs (for scripted checks). */
    public static List<Window> dialogs() {
        List<Window> out = new ArrayList<>();
        for (Window w : Window.getWindows())
            if (w instanceof JDialog && w.isVisible())
                out.add(w);
        return out;
    }
}
