package com.atakmap.android.blast.plugin.desktop;

import com.atakmap.android.blast.plugin.core.Names;
import com.atakmap.android.blast.plugin.desktop.net.Mesh;
import com.atakmap.android.blast.plugin.swing.MemoryPorts;
import com.atakmap.android.blast.plugin.swing.SwingScreen;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

/** The desktop's network settings and its contact picker. */
final class NetDialogs {

    private NetDialogs() {
    }

    static final String[] TEAMS = { "Cyan", "White", "Yellow", "Orange",
            "Magenta", "Red", "Maroon", "Purple", "Dark Blue", "Blue",
            "Teal", "Green", "Dark Green", "Brown" };

    /** The Network settings; applies them (restarting the mesh) on OK. */
    static void settings(Window owner, MemoryPorts ports, Mesh mesh) {
        JCheckBox on = new JCheckBox("Send and receive incidents on the "
                + "local network (ATAK mesh)", mesh.enabled());
        JTextField callsign = new JTextField(ports.callsign(), 16);
        JCheckBox sa = new JCheckBox("Share my position (others see this "
                + "computer as a contact and can send to it)",
                mesh.sharesPosition());
        JComboBox<String> team = new JComboBox<>(TEAMS);
        team.setSelectedItem(mesh.teamColor());
        List<NetworkInterface> nets = Mesh.candidates();
        List<String> names = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        names.add("All networks");
        ids.add("");
        for (NetworkInterface n : nets) {
            names.add(labelOf(n));
            ids.add(n.getName());
        }
        JComboBox<String> which = new JComboBox<>(names.toArray(
                new String[0]));
        int at = ids.indexOf(mesh.only());
        which.setSelectedIndex(Math.max(0, at));

        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        p.add(on, c);
        c.gridwidth = 1;
        c.gridy++;
        p.add(new JLabel("Callsign"), c);
        c.gridx = 1;
        p.add(callsign, c);
        c.gridx = 0;
        c.gridy++;
        p.add(new JLabel("Network"), c);
        c.gridx = 1;
        p.add(which, c);
        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        p.add(sa, c);
        c.gridwidth = 1;
        c.gridy++;
        p.add(new JLabel("Team colour"), c);
        c.gridx = 1;
        p.add(team, c);
        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        JLabel status = new JLabel("<html><body style='width:360px'>Now: "
                + esc(mesh.status()) + "</body></html>");
        p.add(status, c);
        c.gridy++;
        p.add(new JLabel("<html><body style='width:360px'>For ATAK devices on"
                + " the same Wi-Fi or network with no TAK server (mesh, "
                + "multicast 239.2.3.1:6969). Your computer's firewall may "
                + "ask to allow Blast the first time.</body></html>"), c);
        while (true) {
            int r = JOptionPane.showConfirmDialog(owner, p, "Network",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION)
                return;
            String cs = Names.sanitize(callsign.getText());
            if (cs == null) {
                JOptionPane.showMessageDialog(owner, "Type a callsign.",
                        "Network", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            ports.putString("callsign", cs);
            ports.putBoolean("net.on", on.isSelected());
            ports.putBoolean("net.sa", sa.isSelected());
            ports.putString("net.team", (String) team.getSelectedItem());
            ports.putString("net.if", ids.get(Math.max(0, which
                    .getSelectedIndex())));
            mesh.restart();
            mesh.beaconNow();
            return;
        }
    }

    private static String labelOf(NetworkInterface n) {
        String d = n.getDisplayName();
        StringBuilder s = new StringBuilder(d == null || d.isEmpty()
                ? n.getName() : d);
        java.util.Enumeration<java.net.InetAddress> a = n.getInetAddresses();
        while (a.hasMoreElements()) {
            java.net.InetAddress x = a.nextElement();
            if (x instanceof java.net.Inet4Address)
                s.append(" (").append(x.getHostAddress()).append(')');
        }
        return s.toString();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }

    /** Contacts heard on the network, for a look. */
    static void contacts(Window owner, Mesh mesh) {
        List<Mesh.Contact> l = mesh.contacts();
        StringBuilder b = new StringBuilder("<html><body style='width:380px'>");
        if (l.isEmpty())
            b.append("No contacts yet. Devices appear here when they share "
                    + "their position on this network.<br><br>Network: ")
                    .append(esc(mesh.status()));
        for (Mesh.Contact c : l)
            b.append("<b>").append(esc(c.callsign)).append("</b>")
                    .append(c.team == null ? "" : " (" + esc(c.team) + ")")
                    .append(c.direct() == null ? " — via a server only"
                            : "")
                    .append("<br>");
        b.append("</body></html>");
        JOptionPane.showMessageDialog(owner, new JLabel(b.toString()),
                "Contacts", JOptionPane.PLAIN_MESSAGE);
    }

    /** Ask whom to send to; empty if cancelled. */
    static List<Mesh.Contact> pick(Window owner, String what,
            List<Mesh.Contact> people) {
        JDialog d = new JDialog(owner, "Send " + what,
                JDialog.ModalityType.APPLICATION_MODAL);
        JList<Mesh.Contact> list = new JList<>(people.toArray(
                new Mesh.Contact[0]));
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setBackground(SwingScreen.CARD);
        list.setForeground(SwingScreen.TEXT);
        list.setFixedCellHeight(28);
        list.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> l,
                    Object v, int i, boolean sel, boolean focus) {
                Mesh.Contact c = (Mesh.Contact) v;
                return super.getListCellRendererComponent(l, SwingScreen
                        .plain(c.callsign), i, sel, focus);
            }
        });
        JLabel hint = new JLabel("Choose one or more contacts "
                + "(Ctrl or Shift to choose several).");
        hint.setForeground(SwingScreen.TEXT);
        hint.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(380, Math.min(360, 28 * people
                .size() + 4)));
        List<Mesh.Contact> chosen = new ArrayList<>();
        JButton cancel = new JButton("Cancel");
        JButton send = new JButton("Send");
        SwingScreen.style(cancel, SwingScreen.BTN);
        SwingScreen.style(send, SwingScreen.BTN);
        send.setEnabled(false);
        list.addListSelectionListener(e -> send.setEnabled(!list
                .isSelectionEmpty()));
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
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        return chosen;
    }
}
