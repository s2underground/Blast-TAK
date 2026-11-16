package com.atakmap.android.blast.plugin.swing;

import com.atakmap.android.blast.plugin.core.screen.Button;
import com.atakmap.android.blast.plugin.core.screen.Buttons;
import com.atakmap.android.blast.plugin.core.screen.Check;
import com.atakmap.android.blast.plugin.core.screen.Choice;
import com.atakmap.android.blast.plugin.core.screen.EntryRow;
import com.atakmap.android.blast.plugin.core.screen.FormValues;
import com.atakmap.android.blast.plugin.core.screen.Ink;
import com.atakmap.android.blast.plugin.core.screen.Input;
import com.atakmap.android.blast.plugin.core.screen.Item;
import com.atakmap.android.blast.plugin.core.screen.KeyRow;
import com.atakmap.android.blast.plugin.core.screen.Legend;
import com.atakmap.android.blast.plugin.core.screen.ListRow;
import com.atakmap.android.blast.plugin.core.screen.Screen;
import com.atakmap.android.blast.plugin.core.screen.Section;
import com.atakmap.android.blast.plugin.core.screen.Text;
import com.atakmap.android.blast.plugin.core.screen.TitleRow;
import com.atakmap.android.blast.plugin.core.screen.Tone;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/**
 * Renders core screens ({@link Screen}) as Swing components, in the same
 * dark style and layout as the ATAK pane: the desktop twin of BlastPane's
 * renderer. TAK-X panels are Swing, so this is the part a TAK-X Blast
 * reuses.
 */
public final class SwingScreen {

    private SwingScreen() {
    }

    // Blast's dark pane colours (as the ATAK pane).
    public static final Color BG = new Color(0x1E1E1E), CARD = new Color(0x2A2A2A),
            TEXT = new Color(0xEEEEEE), DIM = new Color(0xAAAAAA),
            RED = new Color(0xFF5252), AMBER = new Color(0xFFC107),
            GREEN = new Color(0x69F0AE), BLUE = new Color(0x40C4FF),
            SEL = new Color(0x455A64), BTN = new Color(0x3C3C3C),
            SEND = new Color(0x1565C0), ORANGE = new Color(0xFF9100),
            UXO_SEL = new Color(0x8D4A00), NUC_SEL = new Color(0x5E2A72),
            NUC = new Color(0xE040FB);

    public static Color color(Tone t) {
        switch (t) {
            case DIM:
                return DIM;
            case AMBER:
                return AMBER;
            case RED:
                return RED;
            case GREEN:
                return GREEN;
            case BLUE:
                return BLUE;
            case ORANGE:
                return ORANGE;
            case NUC:
                return NUC;
            default:
                return TEXT;
        }
    }

    public static Color color(Ink k) {
        return k.tone != null ? color(k.tone) : new Color(k.argb, true);
    }

    public static Color buttonColor(Button.Kind k) {
        switch (k) {
            case PRIMARY:
                return SEND;
            case SELECTED:
                return NUC_SEL;
            case SELECTED_UXO:
                return UXO_SEL;
            case SELECTED_STANDOFF:
                return SEL;
            default:
                return BTN;
        }
    }

    /** A vertical panel that follows the width of its scroll pane. */
    public static final class Column extends JPanel implements Scrollable {
        public Column() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return r.height - 48;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE,
                    getPreferredSize().height);
        }
    }

    /**
     * A rendered core screen: its live slots, buttons (by action), entry
     * boxes, checks and sections, so it can be read and updated in place.
     */
    public static final class Rendered {
        public final Consumer<String> actions;
        public final Map<String, JComponent> slots = new HashMap<>();
        public final Map<String, JButton> buttons = new HashMap<>();
        public final Map<String, JTextField> entries = new HashMap<>();
        final Map<String, JCheckBox> checks = new HashMap<>();
        final Map<String, Column> sections = new HashMap<>();
        /** True while the host sets a box or check (no change action). */
        boolean quiet;

        Rendered(Consumer<String> actions) {
            this.actions = actions == null ? a -> { } : actions;
        }

        public FormValues values() {
            FormValues v = new FormValues();
            for (Map.Entry<String, JTextField> e : entries.entrySet())
                v.put(e.getKey(), e.getValue().getText());
            for (Map.Entry<String, JCheckBox> e : checks.entrySet())
                v.put(e.getKey(), e.getValue().isSelected());
            return v;
        }

        /** Restyle in place from a newer model of the same screen. */
        public void update(Screen fresh) {
            update(fresh.items());
        }

        private void update(List<Item> items) {
            for (Item it : items) {
                if (it instanceof Buttons) {
                    for (Button b : ((Buttons) it).buttons)
                        restyle(b);
                } else if (it instanceof EntryRow) {
                    EntryRow er = (EntryRow) it;
                    for (Button b : er.buttons)
                        restyle(b);
                    JTextField e = entries.get(er.id);
                    if (e != null && er.value != null
                            && !er.value.equals(e.getText()))
                        quietly(() -> setText(e, er.value));
                } else if (it instanceof Check) {
                    Check c = (Check) it;
                    JCheckBox cb = checks.get(c.id);
                    if (cb != null && cb.isSelected() != c.checked)
                        quietly(() -> cb.setSelected(c.checked));
                } else if (it instanceof Section) {
                    Section sec = (Section) it;
                    Column l = sections.get(sec.id);
                    if (l == null)
                        continue;
                    l.setVisible(sec.visible);
                    if (sec.live) {
                        l.removeAll();
                        items(sec.items, l, this);
                    } else {
                        update(sec.items);
                    }
                } else if (it instanceof TitleRow) {
                    TitleRow tr = (TitleRow) it;
                    restyle(tr.button);
                    if (tr.text.slot != null)
                        apply(slots.get(tr.text.slot), tr.text);
                } else if (it instanceof Text && it.slot != null) {
                    apply(slots.get(it.slot), (Text) it);
                }
            }
        }

        private void quietly(Runnable r) {
            quiet = true;
            try {
                r.run();
            } finally {
                quiet = false;
            }
        }

        private void restyle(Button b) {
            JButton v = buttons.get(b.action);
            if (v == null)
                return;
            v.setText(label(b.label));
            v.setBackground(buttonColor(b.kind));
            enable(v, b);
        }
    }

    /**
     * Set a box's text without Swing scrolling the pane to its caret (the
     * core fills boxes itself, e.g. a wind direction tap; that must not
     * move the pane).
     */
    public static void setText(javax.swing.text.JTextComponent c,
            String text) {
        javax.swing.text.Caret caret = c.getCaret();
        if (!(caret instanceof javax.swing.text.DefaultCaret)) {
            c.setText(text);
            return;
        }
        javax.swing.text.DefaultCaret dc = (javax.swing.text.DefaultCaret) caret;
        int policy = dc.getUpdatePolicy();
        dc.setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        try {
            c.setText(text);
        } finally {
            dc.setUpdatePolicy(policy);
        }
    }

    /** Set a slot's text and colour from a core Text. */
    public static void apply(JComponent v, Text t) {
        if (v == null || t == null)
            return;
        if (v instanceof JTextArea)
            ((JTextArea) v).setText(t.text);
        else if (v instanceof JLabel)
            ((JLabel) v).setText(plain(t.text));
        v.setForeground(color(t.tone));
    }

    /** Render a screen into {@code into}. */
    public static Rendered render(Screen s, JPanel into, Consumer<String> actions) {
        Rendered rd = new Rendered(actions);
        items(s.items(), into, rd);
        return rd;
    }

    // ------------------------------------------------------------------
    // Items

    private static Font font(float size, boolean bold, boolean mono) {
        float px = size > 0 ? size : 13;
        return new Font(mono ? Font.MONOSPACED : Font.SANS_SERIF,
                bold ? Font.BOLD : Font.PLAIN, Math.round(px));
    }

    /** A wrapping, read-only text block. */
    public static JTextArea textArea(String s, float size, Color c, boolean bold,
            boolean mono) {
        // Caret policy first: text set in the constructor would queue a
        // scroll to the caret that runs once the area is on screen.
        JTextArea a = new JTextArea();
        stayPut(a);
        a.setText(s);
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.setFocusable(false);
        a.setForeground(c);
        a.setFont(font(size, bold, mono));
        a.setBorder(null);
        a.setAlignmentX(Component.LEFT_ALIGNMENT);
        return a;
    }

    /**
     * Setting a text component's text moves its caret, and Swing then
     * scrolls the pane to show the caret: live lines updated every few
     * seconds would keep yanking the pane. Read-only text never scrolls.
     */
    private static void stayPut(javax.swing.text.JTextComponent c) {
        if (c.getCaret() instanceof javax.swing.text.DefaultCaret)
            ((javax.swing.text.DefaultCaret) c.getCaret()).setUpdatePolicy(
                    javax.swing.text.DefaultCaret.NEVER_UPDATE);
    }

    private static final Pattern URL = Pattern.compile(
            "(https?://[^\\s)]+|www\\.[^\\s)]+|[a-z0-9.-]+\\.(?:gov|org|mil)"
                    + "(?:/[^\\s)]*)?)");

    /** A text whose web addresses open in the browser. */
    private static JComponent linked(Text t) {
        Matcher m = URL.matcher(t.text);
        StringBuilder html = new StringBuilder("<html><body style='font-family:"
                + "sans-serif;font-size:" + Math.round(t.size > 0 ? t.size : 13)
                + "px;color:#" + hex(color(t.tone)) + "'>");
        int at = 0;
        boolean any = false;
        while (m.find()) {
            any = true;
            html.append(esc(t.text.substring(at, m.start())));
            String u = m.group(1).replaceAll("[.,;]$", "");
            String href = u.startsWith("http") ? u : "https://" + u;
            html.append("<a style='color:#40C4FF' href='").append(esc(href))
                    .append("'>").append(esc(u)).append("</a>");
            at = m.start() + u.length();
        }
        if (!any)
            return textArea(t.text, t.size, color(t.tone), t.bold, t.mono);
        html.append(esc(t.text.substring(at))).append("</body></html>");
        JEditorPane p = new JEditorPane();
        stayPut(p);
        p.setContentType("text/html");
        p.setText(html.toString().replace("\n", "<br>"));
        p.setEditable(false);
        p.setOpaque(false);
        p.setBorder(null);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
                try {
                    Desktop.getDesktop().browse(new URI(e.getDescription()));
                } catch (Exception ex) {
                    // no browser: nothing to do
                }
        });
        return p;
    }

    private static String hex(Color c) {
        return String.format("%06X", c.getRGB() & 0xFFFFFF);
    }

    public static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("'", "&#39;");
    }

    private static JComponent text(Text t, Rendered rd) {
        JComponent v = t.links ? linked(t)
                : textArea(t.text, t.size, color(t.tone), t.bold, t.mono);
        if (t.padTopDp != 0 || t.padBottomDp != 0 || t.indentDp != 0)
            v.setBorder(BorderFactory.createEmptyBorder(t.padTopDp,
                    t.indentDp, t.padBottomDp, 0));
        if (t.slot != null)
            rd.slots.put(t.slot, v);
        return v;
    }

    /** Multi-line labels as HTML (escaped); one-line labels as plain text. */
    public static String label(String s) {
        if (s == null)
            return "";
        return s.contains("\n") ? "<html><center>" + esc(s).replace("\n",
                "<br>") + "</center></html>" : plain(s);
    }

    /**
     * Text for labels, buttons and lists, shown as written. Swing renders
     * text that starts with "&lt;html&gt;" as HTML (loading any images it
     * names), and names can come from other devices, so such text gets an
     * invisible zero-width space in front.
     */
    public static String plain(String s) {
        if (s == null)
            return "";
        return s.regionMatches(true, 0, "<html", 0, 5) ? "\u200B" + s : s;
    }

    public static void style(JButton v, Color bg) {
        v.setBackground(bg);
        v.setForeground(Color.WHITE);
        v.setFocusPainted(false);
        v.setOpaque(true);
        v.setContentAreaFilled(true);
        v.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.BLACK),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        v.setFont(font(13, false, false));
    }

    private static void enable(JButton v, Button b) {
        v.setEnabled(b.enabled);
        v.setVisible(b.visible);
    }

    private static JButton button(Button b, Rendered rd) {
        JButton v = new JButton(label(b.label));
        style(v, buttonColor(b.kind));
        if (b.bold)
            v.setFont(v.getFont().deriveFont(Font.BOLD));
        if (b.alignStart)
            v.setHorizontalAlignment(SwingConstants.LEFT);
        if (b.danger)
            v.setForeground(RED);
        enable(v, b);
        v.addActionListener(e -> rd.actions.accept(b.action));
        rd.buttons.put(b.action, v);
        return v;
    }

    /**
     * A panel no taller than it needs at its current width (BoxLayout
     * would stretch it; wrapped text needs the width first).
     */
    public static class Fit extends JPanel {
        Fit(java.awt.LayoutManager l) {
            super(l);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE,
                    getPreferredSize().height);
        }
    }

    /** A row whose children share the width by weight. */
    private static JPanel row() {
        JPanel p = new Fit(new GridBagLayout());
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static GridBagConstraints cell(int x, double weight, int pad) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = 0;
        c.weightx = weight;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(pad, pad, pad, pad);
        return c;
    }

    private static void add(JPanel into, JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        into.add(c);
    }

    /** Rows already fit their height ({@link Fit}). */
    private static <T extends JComponent> T tight(T c) {
        return c;
    }

    public static void items(List<Item> items, JPanel into, Rendered rd) {
        for (Item it : items) {
            if (it instanceof Text) {
                add(into, text((Text) it, rd));
            } else if (it instanceof TitleRow) {
                TitleRow tr = (TitleRow) it;
                JPanel r = row();
                GridBagConstraints c = cell(0, 1, 0);
                r.add(text(tr.text, rd), c);
                GridBagConstraints b = cell(1, 0, 0);
                b.fill = GridBagConstraints.NONE;
                b.anchor = tr.buttonAtBottom ? GridBagConstraints.SOUTH
                        : GridBagConstraints.CENTER;
                r.add(button(tr.button, rd), b);
                add(into, tight(r));
            } else if (it instanceof ListRow) {
                add(into, listRow((ListRow) it, rd));
            } else if (it instanceof Legend) {
                Legend l = (Legend) it;
                JPanel r = row();
                JPanel sw = swatch(color(l.swatch), 12, 12);
                GridBagConstraints s = cell(0, 0, 0);
                s.fill = GridBagConstraints.NONE;
                s.insets = new Insets(0, 0, 0, 6);
                r.add(sw, s);
                for (int i = 0; i < l.cellCount(); i++)
                    r.add(textArea(l.cell(i), l.size, color(l.tone),
                            l.bold(i), false), cell(i + 1, l.weight(i), 0));
                add(into, tight(r));
            } else if (it instanceof KeyRow) {
                add(into, keyRow((KeyRow) it));
            } else if (it instanceof Section) {
                Section sec = (Section) it;
                Column l = new Column();
                l.setVisible(sec.visible);
                items(sec.items, l, rd);
                rd.sections.put(sec.id, l);
                add(into, l);
            } else if (it instanceof Check) {
                Check c = (Check) it;
                JCheckBox cb = new JCheckBox(plain(c.label), c.checked);
                cb.setOpaque(false);
                cb.setForeground(TEXT);
                cb.addItemListener(e -> {
                    if (!rd.quiet)
                        rd.actions.accept(c.id);
                });
                rd.checks.put(c.id, cb);
                add(into, cb);
            } else if (it instanceof EntryRow) {
                add(into, entryRow((EntryRow) it, rd));
            } else if (it instanceof Buttons) {
                add(into, buttons((Buttons) it, rd));
            }
        }
    }

    public static JPanel swatch(Color c, int w, int h) {
        JPanel p = new JPanel();
        p.setBackground(c);
        p.setPreferredSize(new Dimension(w, h));
        p.setMinimumSize(new Dimension(w, h));
        return p;
    }

    private static JComponent buttons(Buttons bs, Rendered rd) {
        JPanel r = row();
        r.setBorder(BorderFactory.createEmptyBorder(bs.marginTopDp, 0,
                bs.marginBottomDp, 0));
        int x = 0;
        for (Button b : bs.buttons) {
            JButton v = button(b, rd);
            r.add(v, cell(x++, bs.buttons.size() == 1 ? 1 : b.weight, 1));
        }
        return tight(r);
    }

    private static JComponent entryRow(EntryRow er, Rendered rd) {
        JPanel r = row();
        JTextField e = field(er.maxLength, er.digitsOnly);
        e.putClientProperty("hint", er.hint);
        e.setToolTipText(er.hint);
        if (er.value != null && !er.value.isEmpty())
            e.setText(er.value);
        if (er.changeAction != null)
            onChange(e, () -> {
                if (!rd.quiet)
                    rd.actions.accept(er.changeAction);
            });
        rd.entries.put(er.id, e);
        GridBagConstraints c = cell(0, er.weight, 1);
        r.add(withHint(e, er.hint), c);
        int x = 1;
        for (Button b : er.buttons)
            r.add(button(b, rd), cell(x++, b.weight, 1));
        return tight(r);
    }

    /** A box with its hint shown above it (Swing has no hint text). */
    private static JComponent withHint(JTextField e, String hint) {
        JPanel p = new Fit(new BorderLayout());
        p.setOpaque(false);
        JLabel l = new JLabel(plain(hint));
        l.setForeground(DIM);
        l.setFont(font(11, false, false));
        p.add(l, BorderLayout.NORTH);
        p.add(e, BorderLayout.CENTER);
        return p;
    }

    public static JTextField field(int maxLength, boolean digitsOnly) {
        JTextField e = new JTextField();
        e.setBackground(new Color(0x333333));
        e.setForeground(TEXT);
        e.setCaretColor(TEXT);
        e.setFont(font(14, false, false));
        e.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x555555)),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        ((AbstractDocument) e.getDocument()).setDocumentFilter(
                new DocumentFilter() {
                    @Override
                    public void replace(FilterBypass fb, int off, int len,
                            String s, AttributeSet a)
                            throws BadLocationException {
                        if (s == null)
                            s = "";
                        if (digitsOnly)
                            s = s.replaceAll("[^0-9]", "");
                        int room = maxLength <= 0 ? s.length()
                                : maxLength - (fb.getDocument().getLength()
                                        - len);
                        if (room < s.length())
                            s = s.substring(0, Math.max(0, room));
                        super.replace(fb, off, len, s, a);
                    }

                    @Override
                    public void insertString(FilterBypass fb, int off,
                            String s, AttributeSet a)
                            throws BadLocationException {
                        replace(fb, off, 0, s, a);
                    }
                });
        return e;
    }

    public static void onChange(JTextField e, Runnable r) {
        e.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent ev) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent ev) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent ev) {
                // attributes only
            }
        });
    }

    private static JComponent listRow(ListRow lr, Rendered rd) {
        JPanel r = row();
        r.setOpaque(true);
        r.setBackground(lr.selected ? SEL : CARD);
        r.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        GridBagConstraints b = cell(0, 0, 0);
        b.insets = new Insets(0, 0, 0, 6);
        r.add(swatch(color(lr.bar), 6, 36), b);
        JComponent t = text(lr.text, rd);
        r.add(t, cell(1, 1, 0));
        int x = 2;
        for (Button btn : lr.buttons) {
            JButton v = button(btn, rd);
            GridBagConstraints c = cell(x++, 0, 1);
            c.fill = GridBagConstraints.NONE;
            r.add(v, c);
        }
        MouseAdapter select = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                rd.actions.accept(lr.action);
            }
        };
        r.addMouseListener(select);
        t.addMouseListener(select);
        r.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JPanel wrap = new Fit(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));
        wrap.add(r);
        return tight(wrap);
    }

    private static JComponent keyRow(KeyRow k) {
        JPanel r = row();
        boolean tall = k.kind == KeyRow.Kind.TALL;
        r.setBorder(BorderFactory.createEmptyBorder(tall ? 3 : 1, 0,
                tall ? 3 : 1, 0));
        GridBagConstraints b = cell(0, 0, 0);
        b.insets = new Insets(0, 0, 0, tall ? 8 : 6);
        if (!tall)
            b.fill = GridBagConstraints.NONE;
        r.add(swatch(color(k.bar), tall ? 8 : 18, tall ? 8 : 6), b);
        Column tx = new Column();
        tx.add(textArea(k.title, tall ? 14 : 13, color(k.titleInk), tall,
                false));
        if (k.meaning != null)
            tx.add(textArea(k.meaning, 12, DIM, false, false));
        r.add(tx, cell(1, 1, 0));
        return tight(r);
    }

    // ------------------------------------------------------------------
    // Forms (dialog content: labelled inputs, choices, checks, notes)

    /** A rendered form: values and live slots. */
    public static final class FormView {
        final Map<String, JTextField> inputs = new HashMap<>();
        final Map<String, JCheckBox> checks = new HashMap<>();
        final Map<String, String[]> choices = new HashMap<>();
        final Map<String, JComponent> slots = new HashMap<>();

        public FormValues values() {
            FormValues v = new FormValues();
            for (Map.Entry<String, JTextField> e : inputs.entrySet())
                v.put(e.getKey(), e.getValue().getText());
            for (Map.Entry<String, JCheckBox> e : checks.entrySet())
                v.put(e.getKey(), e.getValue().isSelected());
            for (Map.Entry<String, String[]> e : choices.entrySet())
                v.put(e.getKey(), e.getValue()[0]);
            return v;
        }

        public void set(String slot, String text) {
            JComponent c = slots.get(slot);
            if (c instanceof JTextArea)
                ((JTextArea) c).setText(text);
        }
    }

    /** Render a core form; {@code onChange} runs after every edit. */
    public static FormView form(Screen s, JPanel box, Runnable onChange) {
        FormView f = new FormView();
        for (Item it : s.items()) {
            if (it instanceof Text) {
                Text t = (Text) it;
                JTextArea v = textArea(t.text, t.size, color(t.tone), t.bold,
                        t.mono);
                v.setBorder(BorderFactory.createEmptyBorder(t.padTopDp, 0,
                        t.padBottomDp, 0));
                if (t.selectable) {
                    v.setFocusable(true);
                    v.setBackground(new Color(0x262626));
                    v.setOpaque(true);
                }
                add(box, v);
                if (t.slot != null)
                    f.slots.put(t.slot, v);
            } else if (it instanceof Input) {
                Input in = (Input) it;
                JTextField e = field(in.hinted ? 8 : in.maxLength,
                        in.numeric && !in.hinted);
                if (in.value != null)
                    e.setText(in.value);
                onChange(e, onChange);
                f.inputs.put(in.id, e);
                if (in.hinted) {
                    add(box, tight(withHint(e, in.label)));
                } else {
                    JTextArea l = textArea(in.label, 13, TEXT, true, false);
                    l.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
                    add(box, l);
                    add(box, tight(e));
                }
            } else if (it instanceof Check) {
                Check c = (Check) it;
                JCheckBox cb = new JCheckBox(plain(c.label), c.checked);
                cb.setOpaque(false);
                cb.setForeground(TEXT);
                cb.addItemListener(e -> onChange.run());
                f.checks.put(c.id, cb);
                add(box, cb);
            } else if (it instanceof Choice) {
                Choice c = (Choice) it;
                String[] value = { c.selected };
                List<JButton> bs = new ArrayList<>();
                Color sel = buttonColor(c.selectedKind);
                JPanel r = null;
                for (int i = 0; i < c.size(); i++) {
                    if (i % c.perRow == 0) {
                        r = row();
                        add(box, r);
                    }
                    JButton b = new JButton(label(c.label(i)));
                    style(b, BTN);
                    int k = i;
                    b.addActionListener(e -> {
                        value[0] = c.tap(k, value[0]);
                        for (int j = 0; j < bs.size(); j++)
                            bs.get(j).setBackground(c.code(j).equals(value[0])
                                    ? sel : BTN);
                        onChange.run();
                    });
                    b.setBackground(c.code(i).equals(value[0]) ? sel : BTN);
                    bs.add(b);
                    r.add(b, cell(i % c.perRow, 1, 1));
                }
                // keep the last row's buttons as wide as the others
                for (int i = c.size(); i % c.perRow != 0; i++) {
                    JPanel gap = new JPanel();
                    gap.setOpaque(false);
                    r.add(gap, cell(i % c.perRow, 1, 1));
                }
                f.choices.put(c.id, value);
            }
        }
        return f;
    }
}
