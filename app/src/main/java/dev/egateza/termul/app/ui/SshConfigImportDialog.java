package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.sshconfig.SshConfigImport;
import dev.egateza.termul.core.sshconfig.SshConfigImport.Candidate;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

/**
 * Pilih host dari {@code ~/.ssh/config} yang akan diimpor. Host yang sudah ada tidak bisa dipilih; jump host yang
 * dibutuhkan host terpilih ikut disimpan otomatis. Panggil di EDT.
 */
public final class SshConfigImportDialog {

    private SshConfigImportDialog() {
    }

    /** @return profil yang perlu disimpan (bisa kosong kalau dibatalkan) */
    public static List<HostProfile> show(Component parent, String source, List<Candidate> plan) {
        if (plan.isEmpty()) {
            Dialogs.info(parent, I18n.t("sshconfig.title"), I18n.t("sshconfig.empty", source));
            return List.of();
        }
        var names = new HashMap<UUID, String>();
        plan.forEach(c -> names.put(c.profile().id(), c.profile().name()));
        var model = new Model(plan, names);
        var table = new JTable(model);
        table.setFillsViewportHeight(true);
        int[] widths = {40, 160, 220, 150, 80, 260};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        var scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(900, 320));
        var panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel(I18n.t("sshconfig.intro", source)), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        int ok = JOptionPane.showConfirmDialog(parent, panel, I18n.t("sshconfig.title"), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return List.of();
        }
        return SshConfigImport.toSave(plan, model.selected);
    }

    private static final class Model extends AbstractTableModel {
        private static final String[] COLUMNS = {"", "sshconfig.col.name", "sshconfig.col.address",
                "sshconfig.col.via", "sshconfig.col.auth", "sshconfig.col.note"};
        private final List<Candidate> rows;
        private final Map<UUID, String> names;
        private final java.util.Set<UUID> selected = new HashSet<>();

        Model(List<Candidate> plan, Map<UUID, String> names) {
            this.rows = new ArrayList<>(plan);
            this.names = names;
            plan.stream().filter(c -> !c.existing() && !c.fromJump()).forEach(c -> selected.add(c.profile().id()));
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int c) {
            return c == 0 ? "" : I18n.t(COLUMNS[c]);
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return c == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return col == 0 && !rows.get(row).existing();
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            UUID id = rows.get(row).profile().id();
            if (Boolean.TRUE.equals(value)) {
                selected.add(id);
            } else {
                selected.remove(id);
            }
            fireTableCellUpdated(row, col);
        }

        @Override
        public Object getValueAt(int row, int col) {
            Candidate c = rows.get(row);
            HostProfile p = c.profile();
            return switch (col) {
                case 0 -> selected.contains(p.id());
                case 1 -> p.name();
                case 2 -> p.address();
                case 3 -> p.jumpHostId() == null ? "" : names.getOrDefault(p.jumpHostId(), "?");
                case 4 -> p.authMethod() == AuthMethod.KEY ? I18n.t("sshconfig.auth.key") : I18n.t("sshconfig.auth.default");
                case 5 -> c.existing() ? I18n.t("sshconfig.note.existing")
                        : c.fromJump() && c.note().isEmpty() ? I18n.t("sshconfig.note.jump") : c.note();
                default -> "";
            };
        }
    }
}
