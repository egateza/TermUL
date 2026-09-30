package dev.egateza.termul.app.edit;

import dev.egateza.termul.app.edit.EditManager.Entry;
import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.sftp.Formats;
import dev.egateza.termul.sftp.edit.RemoteEditSession.State;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/** Jendela non-modal: daftar file remote yang sedang diedit beserta status sinkronisasinya. */
public final class EditTrackerDialog extends JDialog {

    private final EditManager manager;
    private final Model model = new Model();
    private final JTable table = new JTable(model);

    public EditTrackerDialog(Window owner, EditManager manager) {
        super(owner, I18n.t("edit.tracker.title"), ModalityType.MODELESS);
        this.manager = manager;
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t, v, sel, focus, row, col);
                Entry e = model.entries.get(t.convertRowIndexToModel(row));
                if (!sel) {
                    setForeground(e.session().state() == State.NEEDS_ATTENTION ? new Color(0xE5484D) : t.getForeground());
                }
                return this;
            }
        });
        int[] widths = {120, 320, 110, 260, 120};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(button(I18n.t("edit.tracker.reopen"), manager::reopenEditor));
        buttons.add(button(I18n.t("edit.tracker.retry"), manager::retry));
        buttons.add(button(I18n.t("edit.tracker.openCache"), e -> {
            try {
                Desktop.getDesktop().open(e.session().localFile().getParent().toFile());
            } catch (IOException | UnsupportedOperationException ex) {
                dev.egateza.termul.app.ui.Dialogs.error(this, I18n.t("edit.tracker.openFolderTitle"), ex.getMessage());
            }
        }));
        buttons.add(button(I18n.t("edit.tracker.finish"), e -> manager.close(e)));

        getContentPane().add(new JScrollPane(table), BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        setSize(960, 300);
        setLocationRelativeTo(owner);

        manager.addListener(() -> SwingUtilities.invokeLater(this::reload));
        reload();
    }

    private JButton button(String label, Consumer<Entry> action) {
        var b = new JButton(label);
        b.addActionListener(ev -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                action.accept(model.entries.get(table.convertRowIndexToModel(row)));
            }
        });
        return b;
    }

    private void reload() {
        model.setEntries(manager.entries());
    }

    private static final class Model extends AbstractTableModel {
        private static final String[] COLUMNS = {"edit.tracker.col.host", "edit.tracker.col.file", "edit.tracker.col.status",
                "edit.tracker.col.message", "edit.tracker.col.lastUpload"};
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
            var s = e.session();
            return switch (col) {
                case 0 -> e.profile().name();
                case 1 -> s.remotePath();
                case 2 -> switch (s.state()) {
                    case OPENING -> I18n.t("edit.tracker.state.opening");
                    case EDITING -> I18n.t("edit.tracker.state.synced");
                    case UPLOADING -> I18n.t("edit.tracker.state.uploading");
                    case NEEDS_ATTENTION -> I18n.t("edit.tracker.state.attention");
                    case CLOSED -> I18n.t("edit.tracker.state.closed");
                };
                case 3 -> s.lastMessage();
                case 4 -> s.lastUpload() == null ? "-" : Formats.time(s.lastUpload());
                default -> "";
            };
        }
    }
}
