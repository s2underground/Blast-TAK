package com.s2underground.blast.takx;

import com.atakmap.android.blast.plugin.core.CotEvents;
import com.atakmap.android.blast.plugin.core.port.Share;
import com.atakmap.android.blast.plugin.swing.MemoryPorts;
import com.atakmap.android.blast.plugin.swing.SwingScreen;

import gov.tak.api.comms.commo.ICotEventSender;
import gov.tak.api.contact.IIndividualContact;
import gov.tak.api.contact.IIndividualContactManager;
import gov.tak.api.cot.event.CotEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/**
 * Sending incidents from TAK-X: the same CoT events ATAK Blast sends
 * ({@link CotEvents}: the marker first, then its rings and outlines), to
 * everyone or to contacts picked from TAK-X's contact list, and ATAK's
 * remote delete for "delete for everyone".
 */
final class TakxShare implements Share {

    private static final Logger LOG = LoggerFactory.getLogger(
            TakxShare.class);

    /** What the plugin supplies. */
    interface Hooks {
        /** This device's uid and callsign, for the events' creator. */
        CotEvents.Sender me();

        /** The ring labels setting (the circles' labels_on). */
        boolean labels();

        void toast(String s);

        Window owner();
    }

    private final MemoryPorts ports;
    /** Either may be null (TAK-X didn't offer it). */
    private final ICotEventSender sender;
    private final IIndividualContactManager contacts;
    private final Hooks hooks;

    TakxShare(MemoryPorts ports, ICotEventSender sender,
            IIndividualContactManager contacts, Hooks hooks) {
        this.ports = ports;
        this.sender = sender;
        this.contacts = contacts;
        this.hooks = hooks;
    }

    /** The incident's events as they would be sent now. */
    List<CotEvents.Event> events(String uid) {
        MemoryPorts.Stored s = ports.stored(uid);
        if (s == null)
            return Collections.emptyList();
        CotEvents.Sender me = hooks.me();
        long now = ports.nowMillis();
        boolean labels = hooks.labels();
        return s.nuc != null ? CotEvents.incident(s.uid, s.name, s.lat,
                s.lon, s.nuc, me, now, labels)
                : s.uxo != null ? CotEvents.incident(s.uid, s.name, s.lat,
                        s.lon, s.uxo, me, now, labels)
                        : CotEvents.incident(s.uid, s.name, s.lat, s.lon,
                                s.standoff, me, now, labels);
    }

    private ICotEventSender sender() {
        if (sender == null)
            throw new IllegalStateException("TAK-X offers no CoT sender");
        return sender;
    }

    // ------------------------------------------------------------------
    // Share

    @Override
    public int sendToAll(String uid) {
        ICotEventSender out = sender();
        int n = 0;
        for (CotEvents.Event e : events(uid)) {
            try {
                out.broadcastEvent(CotEvent.parse(e.xml));
                n++;
            } catch (RuntimeException ex) {
                LOG.warn("could not send {}", e.uid, ex);
            }
        }
        LOG.info("sent {} to everyone: {} events", uid, n);
        return n;
    }

    @Override
    public void sendTo(String uid) {
        MemoryPorts.Stored s = ports.stored(uid);
        if (s == null)
            return;
        if (sender == null || contacts == null) {
            hooks.toast("Sending to contacts isn't available in this TAK-X.");
            return;
        }
        List<IIndividualContact> people = contacts();
        if (people.isEmpty()) {
            hooks.toast("No contacts to send to.");
            return;
        }
        List<IIndividualContact> chosen = pick(s.name, people);
        if (chosen.isEmpty())
            return;
        List<String> uids = new ArrayList<>();
        for (IIndividualContact c : chosen)
            uids.add(c.getUid());
        int n = 0;
        for (CotEvents.Event e : events(uid)) {
            try {
                sender.sendEvent(CotEvent.parse(e.xml), uids);
                n++;
            } catch (RuntimeException ex) {
                LOG.warn("could not send {}", e.uid, ex);
            }
        }
        LOG.info("sent {} to {} contacts: {} events", uid, uids.size(), n);
        hooks.toast("Sent " + s.name + " to " + (uids.size() == 1
                ? name(chosen.get(0)) : uids.size() + " contacts") + " ("
                + n + " items).");
    }

    @Override
    public void deleteForEveryone(String uid) {
        ICotEventSender out = sender;
        if (out != null) {
            long now = ports.nowMillis();
            for (CotEvents.Event e : events(uid)) {
                try {
                    out.broadcastEvent(CotEvent.parse(CotEvents.delete(e.uid,
                            e.type, now)));
                } catch (RuntimeException ex) {
                    LOG.warn("delete event failed for {}", e.uid, ex);
                }
            }
        } else {
            hooks.toast("TAK-X can't send: deleted here only.");
        }
        ports.delete(uid);
    }

    // ------------------------------------------------------------------
    // Contacts

    /** TAK-X's contacts, without this device, by name. */
    private List<IIndividualContact> contacts() {
        String self = hooks.me().uid;
        List<IIndividualContact> out = new ArrayList<>();
        try {
            for (IIndividualContact c : contacts.getContacts())
                if (c != null && c.getUid() != null && !c.getUid().isEmpty()
                        && !c.getUid().equals(self))
                    out.add(c);
        } catch (RuntimeException e) {
            LOG.warn("could not list contacts", e);
        }
        out.sort((a, b) -> name(a).toLowerCase(Locale.ROOT).compareTo(
                name(b).toLowerCase(Locale.ROOT)));
        return out;
    }

    private static String name(IIndividualContact c) {
        String n = c.getName();
        return n == null || n.trim().isEmpty() ? c.getUid() : n.trim();
    }

    /** Ask whom to send to; empty if cancelled. */
    private List<IIndividualContact> pick(String what,
            List<IIndividualContact> people) {
        JDialog d = new JDialog(hooks.owner(), "Send " + what,
                JDialog.ModalityType.APPLICATION_MODAL);
        JList<IIndividualContact> list = new JList<>(
                people.toArray(new IIndividualContact[0]));
        list.setSelectionMode(
                ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setBackground(SwingScreen.CARD);
        list.setForeground(SwingScreen.TEXT);
        list.setFixedCellHeight(28);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l,
                    Object v, int i, boolean sel, boolean focus) {
                return super.getListCellRendererComponent(l,
                        SwingScreen.plain(name((IIndividualContact) v)), i,
                        sel, focus);
            }
        });
        JLabel hint = new JLabel("Choose one or more contacts "
                + "(Ctrl or Shift to choose several).");
        hint.setForeground(SwingScreen.TEXT);
        hint.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(380, Math.min(360,
                28 * people.size() + 4)));

        List<IIndividualContact> chosen = new ArrayList<>();
        JButton cancel = new JButton("Cancel");
        JButton send = new JButton("Send");
        SwingScreen.style(cancel, SwingScreen.BTN);
        SwingScreen.style(send, SwingScreen.BTN);
        send.setEnabled(false);
        list.addListSelectionListener(e -> send.setEnabled(
                !list.isSelectionEmpty()));
        cancel.addActionListener(e -> d.dispose());
        send.addActionListener(e -> {
            chosen.addAll(list.getSelectedValuesList());
            d.dispose();
        });
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bar.setBackground(SwingScreen.BG);
        bar.add(cancel);
        bar.add(send);
        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(SwingScreen.BG);
        body.setBorder(BorderFactory.createEmptyBorder(12, 14, 6, 14));
        body.add(hint, BorderLayout.NORTH);
        body.add(sp);
        d.getContentPane().setBackground(SwingScreen.BG);
        d.getContentPane().add(body);
        d.getContentPane().add(bar, BorderLayout.SOUTH);
        d.pack();
        d.setLocationRelativeTo(hooks.owner());
        d.setVisible(true); // modal: returns when closed
        return chosen;
    }
}
