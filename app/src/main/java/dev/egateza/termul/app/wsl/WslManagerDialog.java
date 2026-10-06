package dev.egateza.termul.app.wsl;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import dev.egateza.termul.core.wsl.WslDistro;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

/**
 * Daftar distro WSL beserta status dan profil SSH-nya, dengan tombol start/stop. Hanya tampilan: semua aksi diteruskan
 * ke {@link Actions} (dijalankan di luar EDT oleh {@link WslController}). Semua method dipanggil di EDT.
 */
final class WslManagerDialog extends JDialog {

    /** Aksi dari dialog; diimplementasikan oleh {@link WslController}. */
    interface Actions {
        void refresh();

        void start(String distro);

        void stop(String distro);

        void shutdownAll();

        void newProfile(String distro);

        void guide(String distro);

        void check(String distro);

        /** Port sshd distro diisi user di tabel (profil distro ikut diubah kalau ada). */
        void setPort(String distro, int port);
    }

    /** @param port port sshd: dari profil, isian user, atau terdeteksi dari sshd_config; null = belum diketahui */
    private record Row(WslDistro distro, List<HostProfile> profiles, Integer port) {
    }

    private final Model model;
    private final JTable table;
    private final JLabel status = new JLabel(I18n.t("wsl.manager.loading"));
    private final JButton start = new JButton(I18n.t("wsl.manager.start"));
    private final JButton stop = new JButton(I18n.t("wsl.manager.stop"));
    private final JButton newProfile = new JButton(I18n.t("wsl.manager.newProfile"));
    private final JButton guide = new JButton(I18n.t("wsl.manager.guide"));
    private final JButton check = new JButton(I18n.t("wsl.manager.check"));
    private final JButton refresh = new JButton(I18n.t("wsl.manager.refresh"));
    private final JButton shutdownAll = new JButton(I18n.t("wsl.manager.shutdownAll"));
    private boolean busy;
    private boolean loaded; // daftar distro sudah pernah terbaca

    WslManagerDialog(Window owner, Actions actions) {
        // modal: window utama terkunci sampai WSL Manager ditutup; dialog turunannya (profil, Periksa SSH) di atasnya
        super(owner, I18n.t("wsl.manager.title"), ModalityType.APPLICATION_MODAL);
        model = new Model(actions);
        table = new JTable(model);

        table.setFillsViewportHeight(true);
        table.putClientProperty("terminateEditOnFocusLost", true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        // kolom port: angka rata kiri, kosong = belum diketahui (dipakai port cadangan)
        var portRenderer = new javax.swing.table.DefaultTableCellRenderer();
        portRenderer.setHorizontalAlignment(javax.swing.SwingConstants.LEADING);
        table.setDefaultRenderer(Integer.class, portRenderer);
        int[] widths = {170, 90, 80, 240};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        start.addActionListener(e -> selected().ifPresent(r -> actions.start(r.distro().name())));
        stop.addActionListener(e -> selected().ifPresent(r -> {
            if (confirm(I18n.t("wsl.manager.stop.confirm", r.distro().name()))) {
                actions.stop(r.distro().name());
            }
        }));
        newProfile.addActionListener(e -> selected().ifPresent(r -> actions.newProfile(r.distro().name())));
        guide.addActionListener(e -> selected().ifPresent(r -> actions.guide(r.distro().name())));
        check.setToolTipText(I18n.t("wsl.manager.check.tooltip"));
        check.addActionListener(e -> selected().ifPresent(r -> actions.check(r.distro().name())));
        refresh.addActionListener(e -> actions.refresh());
        shutdownAll.setToolTipText(I18n.t("wsl.manager.shutdownAll.tooltip"));
        shutdownAll.addActionListener(e -> {
            if (confirm(I18n.t("wsl.manager.shutdownAll.confirm"))) {
                actions.shutdownAll();
            }
        });

        var hint = new JLabel(I18n.t("wsl.manager.hint.port"));
        hint.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        var north = new JPanel(new BorderLayout());
        north.add(hint, BorderLayout.CENTER);

        var rowButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        rowButtons.add(start);
        rowButtons.add(stop);
        rowButtons.add(newProfile);
        rowButtons.add(check);
        rowButtons.add(guide);
        var globalButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        globalButtons.add(refresh);
        globalButtons.add(shutdownAll);
        var buttons = new JPanel(new BorderLayout());
        buttons.add(rowButtons, BorderLayout.WEST);
        buttons.add(globalButtons, BorderLayout.EAST);
        status.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 2));
        var south = new JPanel(new BorderLayout());
        south.add(buttons, BorderLayout.NORTH);
        south.add(status, BorderLayout.SOUTH);

        var content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(north, BorderLayout.NORTH);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setSize(720, 360);
        setLocationRelativeTo(owner);
        updateButtons();
    }

    /**
     * Tampilkan daftar terbaru; baris terpilih dipertahankan. Selama user mengisi port, tabel tidak diganti (refresh
     * berikutnya menyusul).
     *
     * @param portOf port sshd per distro, null kalau belum diketahui
     */
    void setDistros(List<WslDistro> distros, ProfileSnapshot snapshot, java.util.function.Function<String, Integer> portOf) {
        if (table.isEditing()) {
            return;
        }
        String selectedName = selected().map(r -> r.distro().name()).orElse(null);
        var rows = new ArrayList<Row>();
        for (var d : distros) {
            rows.add(new Row(d, snapshot.profiles().stream()
                    .filter(p -> d.name().equalsIgnoreCase(p.wslDistro())).toList(), portOf.apply(d.name())));
        }
        model.setRows(rows);
        if (!loaded) {
            loaded = true;
            if (!busy) {
                status.setText(" ");
            }
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).distro().name().equals(selectedName)) {
                table.setRowSelectionInterval(i, i);
            }
        }
        if (table.getSelectedRow() < 0 && !rows.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }
        if (rows.isEmpty() && !busy) {
            status.setText(I18n.t("wsl.manager.empty"));
        }
        updateButtons();
    }

    /** Operasi sedang berjalan: tombol dimatikan dan {@code message} tampil di bawah. */
    void setBusy(String message) {
        busy = true;
        status.setFont(status.getFont().deriveFont(Font.ITALIC));
        status.setText(message);
        updateButtons();
    }

    /** Operasi selesai; {@code message} (boleh null) tampil di bawah. */
    void setIdle(String message) {
        busy = false;
        status.setFont(status.getFont().deriveFont(Font.PLAIN));
        status.setText(message == null || message.isBlank() ? " " : message);
        updateButtons();
    }

    private Optional<Row> selected() {
        int row = table.getSelectedRow();
        return row < 0 ? Optional.empty() : Optional.of(model.rows.get(table.convertRowIndexToModel(row)));
    }

    private void updateButtons() {
        var row = selected();
        boolean running = row.map(r -> r.distro().running()).orElse(false);
        start.setEnabled(!busy && row.isPresent() && !running);
        stop.setEnabled(!busy && running);
        newProfile.setEnabled(!busy && row.isPresent() && row.get().profiles().isEmpty());
        guide.setEnabled(row.isPresent());
        check.setEnabled(!busy && row.isPresent());
        refresh.setEnabled(!busy);
        shutdownAll.setEnabled(!busy && model.rows.stream().anyMatch(r -> r.distro().running()));
    }

    private boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(this, message, getTitle(), JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /**
     * Panduan sekali jalan untuk menyiapkan sshd di distro (Debian/Ubuntu). Perintahnya bisa di-copy; TermUL sendiri
     * hanya menjalankan sshd, tidak memasang atau mengubah konfigurasinya.
     */
    static void showGuide(Component parent, String distro, int port) {
        var text = new JTextArea(guideCommands(port), 12, 78);
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, text.getFont().getSize()));
        text.setCaretPosition(0);
        var panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel(I18n.t("wsl.guide.intro", distro, String.valueOf(port))), BorderLayout.NORTH);
        panel.add(new JScrollPane(text), BorderLayout.CENTER);
        panel.add(new JLabel(I18n.t("wsl.guide.footer")), BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(parent, panel, I18n.t("wsl.guide.title", distro), JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Hasil "Periksa SSH": daftar cek dan, kalau ada masalah, perintah perbaikan yang bisa di-copy.
     *
     * @param recheck dipanggil kalau user memilih "Periksa lagi"
     */
    static void showCheck(Component parent, SshCheckReport report, Runnable recheck) {
        var panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel(I18n.t("wsl.check.header", report.distro(), String.valueOf(report.port()))),
                BorderLayout.NORTH);
        var checklist = monospace(report.checklist(), 6);
        checklist.setOpaque(false);
        String fixCommands = report.fixCommands();
        String optional = report.ok() ? report.optionalCommands() : "";
        String commands = fixCommands.isEmpty() ? optional : fixCommands;
        var body = new JPanel(new BorderLayout(0, 8));
        body.add(checklist, BorderLayout.NORTH);
        if (report.ok()) {
            var ok = new JPanel(new BorderLayout(0, 8));
            ok.add(new JLabel(I18n.t("wsl.check.ok")), BorderLayout.NORTH);
            if (!optional.isEmpty()) {
                var opt = new JPanel(new BorderLayout(0, 4));
                opt.add(new JLabel(I18n.t("wsl.check.optionalIntro")), BorderLayout.NORTH);
                opt.add(new JScrollPane(monospace(optional, 4)), BorderLayout.CENTER);
                ok.add(opt, BorderLayout.CENTER);
            }
            body.add(ok, BorderLayout.CENTER);
        } else if (fixCommands.isEmpty()) {
            body.add(new JLabel(I18n.t("wsl.fix.unreachable", String.valueOf(report.port()))), BorderLayout.CENTER);
        } else {
            var fix = new JPanel(new BorderLayout(0, 4));
            fix.add(new JLabel(I18n.t("wsl.check.fixIntro")), BorderLayout.NORTH);
            fix.add(new JScrollPane(monospace(fixCommands, 9)), BorderLayout.CENTER);
            body.add(fix, BorderLayout.CENTER);
        }
        panel.add(body, BorderLayout.CENTER);
        String copy = I18n.t("wsl.check.copy");
        String again = I18n.t("wsl.check.again");
        String close = I18n.t("wsl.check.close");
        Object[] options = commands.isEmpty() ? new Object[] {again, close} : new Object[] {copy, again, close};
        int choice = JOptionPane.showOptionDialog(parent, panel, I18n.t("wsl.check.title", report.distro()),
                JOptionPane.DEFAULT_OPTION, report.ok() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE,
                null, options, options[0]);
        if (choice < 0) {
            return;
        }
        if (options[choice] == copy) {
            // hanya perintah shell, tidak ada secret
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(commands), null);
        } else if (options[choice] == again) {
            recheck.run();
        }
    }

    private static JTextArea monospace(String text, int rows) {
        var area = new JTextArea(text, rows, 78);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, area.getFont().getSize()));
        area.setCaretPosition(0);
        return area;
    }

    /** Perintah shell (tidak diterjemahkan); komentar dari i18n. */
    static String guideCommands(int port) {
        return String.join("\n",
                "# " + I18n.t("wsl.guide.step.install"),
                "sudo apt update && sudo apt install -y openssh-server",
                "",
                "# " + I18n.t("wsl.guide.step.port", String.valueOf(port)),
                "sudo sed -i -E 's/^#?Port .*/Port " + port + "/' /etc/ssh/sshd_config",
                "",
                "# " + I18n.t("wsl.guide.step.listen"),
                "# sudo sed -i -E 's/^#?ListenAddress 0\\.0\\.0\\.0.*/ListenAddress 127.0.0.1/' /etc/ssh/sshd_config",
                "",
                "# " + I18n.t("wsl.guide.step.socket"),
                "systemctl is-enabled ssh.socket 2>/dev/null | grep -q enabled"
                        + " && sudo systemctl disable --now ssh.socket && sudo systemctl enable ssh.service",
                "",
                "# " + I18n.t("wsl.guide.step.restart"),
                "sudo service ssh restart",
                "ss -ltn | grep " + port);
    }

    private static final class Model extends AbstractTableModel {
        private final Actions actions;
        private List<Row> rows = List.of();

        Model(Actions actions) {
            this.actions = actions;
        }

        void setRows(List<Row> rows) {
            this.rows = List.copyOf(rows);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> I18n.t("wsl.manager.column.distro");
                case 1 -> I18n.t("wsl.manager.column.status");
                case 2 -> I18n.t("wsl.manager.column.port");
                default -> I18n.t("wsl.manager.column.profile");
            };
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 2 ? Integer.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 2;
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columnIndex != 2 || !(value instanceof Integer port)) {
                return;
            }
            if (port < 1 || port > 65_535) {
                java.awt.Toolkit.getDefaultToolkit().beep();
                return;
            }
            var row = rows.get(rowIndex);
            if (!port.equals(row.port())) {
                var copy = new ArrayList<>(rows);
                copy.set(rowIndex, new Row(row.distro(), row.profiles(), port));
                rows = List.copyOf(copy);
                fireTableRowsUpdated(rowIndex, rowIndex);
                actions.setPort(row.distro().name(), port);
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            var row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.distro().name();
                case 1 -> I18n.t(row.distro().running() ? "wsl.status.running" : "wsl.status.stopped");
                case 2 -> row.port();
                default -> row.profiles().isEmpty() ? I18n.t("wsl.manager.noProfile")
                        : row.profiles().stream().map(p -> p.name() + " (" + p.address() + ")")
                        .collect(Collectors.joining(", "));
            };
        }
    }
}
