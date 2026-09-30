package dev.egateza.myterm.app.edit;

import dev.egateza.myterm.app.edit.EditManager.Entry;
import dev.egateza.myterm.app.sftp.Formats;
import dev.egateza.myterm.sftp.edit.RemoteEditSession.State;
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
        super(owner, "File yang sedang diedit", ModalityType.MODELESS);
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
        buttons.add(button("Buka lagi di editor", manager::reopenEditor));
        buttons.add(button("Upload / coba lagi", manager::retry));
        buttons.add(button("Buka folder cache", e -> {
            try {
                Desktop.getDesktop().open(e.session().localFile().getParent().toFile());
            } catch (IOException | UnsupportedOperationException ex) {
                dev.egateza.myterm.app.ui.Dialogs.error(this, "Buka folder", ex.getMessage());
            }
        }));
        buttons.add(button("Selesai (tutup sesi)", e -> manager.close(e)));

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
        private static final String[] COLUMNS = {"Host", "File remote", "Status", "Pesan", "Upload terakhir"};
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
            return COLUMNS[c];
        }

        @Override
        public Object getValueAt(int row, int col) {
            Entry e = entries.get(row);
            var s = e.session();
            return switch (col) {
                case 0 -> e.profile().name();
                case 1 -> s.remotePath();
                case 2 -> switch (s.state()) {
                    case OPENING -> "Membuka";
                    case EDITING -> "Tersinkron";
                    case UPLOADING -> "Mengupload";
                    case NEEDS_ATTENTION -> "Perlu perhatian";
                    case CLOSED -> "Ditutup";
                };
                case 3 -> s.lastMessage();
                case 4 -> s.lastUpload() == null ? "-" : Formats.time(s.lastUpload());
                default -> "";
            };
        }
    }
}
