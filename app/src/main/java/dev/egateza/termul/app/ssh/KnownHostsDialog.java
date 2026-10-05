package dev.egateza.termul.app.ssh;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.log.LogPanel;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.UiAsync;
import dev.egateza.termul.ssh.hostkey.KnownHostsStore;
import dev.egateza.termul.ssh.hostkey.KnownHostsStore.Entry;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

/**
 * Daftar entry known_hosts aplikasi; entry yang dipilih bisa dihapus (dengan konfirmasi) supaya host berikutnya
 * melewati TOFU lagi. Baca/tulis file di executor {@code io}, bukan di EDT.
 */
public final class KnownHostsDialog extends JDialog {

    private final KnownHostsStore store;
    private final Executor io;
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final TableRowSorter<Model> sorter = new TableRowSorter<>(model);
    private final JTextField search = new JTextField();
    private final JButton remove = new JButton(I18n.t("knownHosts.remove"));

    public KnownHostsDialog(Window owner, KnownHostsStore store, Executor io) {
        super(owner, I18n.t("knownHosts.title"), ModalityType.APPLICATION_MODAL);
        this.store = store;
        this.io = io;

        table.setRowSorter(sorter);
        table.setFillsViewportHeight(true);
        table.getSelectionModel().addListSelectionListener(e -> remove.setEnabled(table.getSelectedRowCount() > 0));
        int[] widths = {260, 150, 420};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        search.putClientProperty("JTextField.placeholderText", I18n.t("knownHosts.search"));
        search.putClientProperty("JTextField.showClearButton", true);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });

        remove.setEnabled(false);
        remove.addActionListener(e -> removeSelected());
        var openFolder = new JButton(I18n.t("knownHosts.openFolder"));
        openFolder.setToolTipText(store.file().toString());
        openFolder.addActionListener(e -> UiAsync.run(io, () -> LogPanel.openFolder(store.file().toAbsolutePath().getParent()),
                err -> Dialogs.error(this, I18n.t("main.error.openConfigDir"), err)));
        var close = new JButton(I18n.t("knownHosts.close"));
        close.addActionListener(e -> dispose());

        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(remove);
        buttons.add(openFolder);
        buttons.add(close);

        var top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        top.add(search, BorderLayout.CENTER);

        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(new JScrollPane(table), BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(close);
        setSize(880, 420);
        setLocationRelativeTo(owner);
        reload();
    }

    private void applyFilter() {
        String text = search.getText().strip();
        sorter.setRowFilter(text.isEmpty() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
    }

    private void reload() {
        UiAsync.run(io, store::entries, model::setEntries, err -> Dialogs.error(this, I18n.t("knownHosts.error.read"), err));
    }

    private void removeSelected() {
        var selected = new ArrayList<Entry>();
        for (int row : table.getSelectedRows()) {
            selected.add(model.entries.get(table.convertRowIndexToModel(row)));
        }
        if (selected.isEmpty() || !confirmRemove(this, selected)) {
            return;
        }
        UiAsync.run(io, () -> store.remove(selected), removed -> reload(),
                err -> Dialogs.error(this, I18n.t("knownHosts.error.write"), err));
    }

    /** Konfirmasi hapus: menampilkan host + fingerprint yang akan dilupakan. EDT. */
    public static boolean confirmRemove(Component parent, List<Entry> entries) {
        String list = entries.stream()
                .map(e -> "  " + hostLabel(e) + "  " + e.algorithm() + "  " + (e.fingerprint() == null ? "-" : e.fingerprint()))
                .collect(Collectors.joining("\n"));
        return Dialogs.confirm(parent, I18n.t("knownHosts.remove.title"),
                I18n.t("knownHosts.remove.confirm", String.valueOf(entries.size()), list));
    }

    static String hostLabel(Entry e) {
        String hosts = e.hashed() ? I18n.t("knownHosts.hashed") : e.hosts();
        return e.marker() == null ? hosts : "@" + e.marker() + " " + hosts;
    }

    private static final class Model extends AbstractTableModel {
        private static final String[] COLUMNS = {"knownHosts.col.host", "knownHosts.col.type", "knownHosts.col.fingerprint"};
        private List<Entry> entries = List.of();

        void setEntries(List<Entry> list) {
            entries = list;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int c) {
            return I18n.t(COLUMNS[c]);
        }

        @Override
        public Object getValueAt(int row, int col) {
            Entry e = entries.get(row);
            return switch (col) {
                case 0 -> hostLabel(e);
                case 1 -> e.algorithm();
                case 2 -> e.fingerprint() == null ? "-" : e.fingerprint();
                default -> "";
            };
        }
    }
}
