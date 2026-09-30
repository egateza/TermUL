package dev.egateza.myterm.app.ui;

import dev.egateza.myterm.core.profile.AuthMethod;
import dev.egateza.myterm.core.profile.EnvironmentTag;
import dev.egateza.myterm.core.profile.HostProfile;
import dev.egateza.myterm.core.profile.ProfileSnapshot;
import dev.egateza.myterm.vault.SecretType;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;

/** Dialog modal tambah/edit {@link HostProfile}. Tidak ada field secret di sini (secret masuk vault). */
public final class ProfileDialog extends JDialog {

    /** Pilihan jump host; {@code profile == null} berarti tanpa jump host. */
    private record JumpChoice(HostProfile profile) {
        @Override
        public String toString() {
            return profile == null ? "(tanpa jump host)" : profile.name() + "  —  " + profile.address();
        }
    }

    private final UUID id;
    private final JTextField name = new JTextField(24);
    private final JComboBox<String> group = new JComboBox<>();
    private final JTextField host = new JTextField(24);
    private final JSpinner port = new JSpinner(new SpinnerNumberModel(HostProfile.DEFAULT_PORT, 1, 65_535, 1));
    private final JTextField username = new JTextField(16);
    private final JComboBox<AuthMethod> auth = new JComboBox<>(AuthMethod.values());
    private final JTextField keyPath = new JTextField(24);
    private final JButton browseKey = new JButton("...");
    private final JComboBox<JumpChoice> jumpHost = new JComboBox<>();
    private final JComboBox<EnvironmentTag> environment = new JComboBox<>(EnvironmentTag.values());
    private final JTextField initialDir = new JTextField(24);
    private final JTextArea notes = new JTextArea(3, 24);
    private final JCheckBox autoSudo = new JCheckBox("Auto-inject password sudo (dengan guard)");
    private final Map<SecretType, JPasswordField> secretFields = new EnumMap<>(SecretType.class);
    private final Map<SecretType, JCheckBox> clearBoxes = new EnumMap<>(SecretType.class);
    private final Set<SecretType> storedSecrets;
    private Result result;

    /** Hasil dialog: profil + perubahan secret (secret di dalamnya wajib dipakai atau di-discard). */
    public record Result(HostProfile profile, Map<SecretType, SecretChange> secrets) {
    }

    private ProfileDialog(Window owner, String title, HostProfile initial, String defaultGroup, ProfileSnapshot snapshot,
                          Set<SecretType> storedSecrets) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.id = initial == null ? UUID.randomUUID() : initial.id();
        this.storedSecrets = Set.copyOf(storedSecrets);
        for (SecretType type : SecretType.values()) {
            secretFields.put(type, new JPasswordField(20));
            clearBoxes.put(type, new JCheckBox("Hapus"));
        }

        group.setEditable(true);
        group.addItem("");
        snapshot.allGroups().forEach(group::addItem);
        jumpHost.addItem(new JumpChoice(null));
        snapshot.profiles().stream()
                .filter(p -> !p.id().equals(id))
                .forEach(p -> jumpHost.addItem(new JumpChoice(p)));
        auth.setRenderer(labelRenderer(v -> switch ((AuthMethod) v) {
            case KEY -> "Private key";
            case AGENT -> "SSH agent";
            case PASSWORD -> "Password";
        }));
        environment.setRenderer(labelRenderer(v -> EnvColors.label((EnvironmentTag) v)));
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);

        if (initial != null) {
            fill(initial, snapshot);
        } else {
            group.setSelectedItem(defaultGroup == null ? "" : defaultGroup);
            auth.setSelectedItem(AuthMethod.AGENT);
            environment.setSelectedItem(EnvironmentTag.NONE);
        }
        auth.addActionListener(e -> updateEnabled());
        environment.addActionListener(e -> updateEnabled());
        browseKey.addActionListener(e -> chooseKey());
        updateEnabled();

        getContentPane().add(buildForm(), BorderLayout.CENTER);
        getContentPane().add(buildButtons(), BorderLayout.SOUTH);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
    }

    /** Menampilkan dialog profil baru. */
    public static Optional<Result> create(Component parent, String group, ProfileSnapshot snapshot) {
        return show(parent, "Host baru", null, group, snapshot, Set.of());
    }

    /** Menampilkan dialog edit profil. {@code storedSecrets} = secret yang sudah ada di vault. */
    public static Optional<Result> edit(Component parent, HostProfile profile, ProfileSnapshot snapshot,
                                        Set<SecretType> storedSecrets) {
        return show(parent, "Edit host: " + profile.name(), profile, profile.group(), snapshot, storedSecrets);
    }

    private static Optional<Result> show(Component parent, String title, HostProfile initial, String group,
                                         ProfileSnapshot snapshot, Set<SecretType> storedSecrets) {
        Window owner = parent instanceof Window w ? w : javax.swing.SwingUtilities.getWindowAncestor(parent);
        var dialog = new ProfileDialog(owner, title, initial, group, snapshot, storedSecrets);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    private void fill(HostProfile p, ProfileSnapshot snapshot) {
        name.setText(p.name());
        group.setSelectedItem(p.group());
        host.setText(p.host());
        port.setValue(p.port());
        username.setText(p.username());
        auth.setSelectedItem(p.authMethod());
        keyPath.setText(Objects.requireNonNullElse(p.privateKeyPath(), ""));
        environment.setSelectedItem(p.environment());
        initialDir.setText(Objects.requireNonNullElse(p.initialDirectory(), ""));
        notes.setText(Objects.requireNonNullElse(p.notes(), ""));
        autoSudo.setSelected(p.autoSudo());
        if (p.jumpHostId() != null) {
            snapshot.find(p.jumpHostId()).ifPresent(j -> {
                for (int i = 0; i < jumpHost.getItemCount(); i++) {
                    var choice = jumpHost.getItemAt(i);
                    if (choice.profile() != null && choice.profile().id().equals(j.id())) {
                        jumpHost.setSelectedIndex(i);
                    }
                }
            });
        }
    }

    private void updateEnabled() {
        boolean key = auth.getSelectedItem() == AuthMethod.KEY;
        keyPath.setEnabled(key);
        browseKey.setEnabled(key);
        boolean prod = environment.getSelectedItem() == EnvironmentTag.PROD;
        autoSudo.setToolTipText(prod
                ? "Tidak disarankan untuk host produksi: gunakan hotkey Ctrl+Shift+P."
                : "Password dikirim otomatis hanya setelah Anda menjalankan sudo/su (lihat docs/SECURITY.md).");
    }

    private void chooseKey() {
        var chooser = new JFileChooser(keyPath.getText().isBlank()
                ? Path.of(System.getProperty("user.home"), ".ssh").toFile()
                : Path.of(keyPath.getText()).toFile());
        chooser.setDialogTitle("Pilih private key");
        chooser.setFileHidingEnabled(false);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            keyPath.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void onSave() {
        try {
            var jump = (JumpChoice) jumpHost.getSelectedItem();
            var env = (EnvironmentTag) environment.getSelectedItem();
            if (autoSudo.isSelected() && env == EnvironmentTag.PROD && !Dialogs.confirm(this, "Auto-sudo di produksi",
                    "Auto-inject password sudo di host produksi berisiko (prompt spoofing).\nTetap aktifkan?")) {
                return;
            }
            var profile = new HostProfile(id, name.getText(), Objects.toString(group.getSelectedItem(), ""),
                    host.getText(), (Integer) port.getValue(), username.getText(),
                    (AuthMethod) auth.getSelectedItem(), keyPath.getText(),
                    jump == null || jump.profile() == null ? null : jump.profile().id(),
                    env, initialDir.getText(), notes.getText(), autoSudo.isSelected());
            result = new Result(profile, collectSecretChanges());
            dispose();
        } catch (IllegalArgumentException e) {
            Dialogs.error(this, "Data profil tidak valid", e.getMessage());
        }
    }

    private JComponent buildForm() {
        var form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        int row = 0;
        row = addRow(form, row, "Nama", name);
        row = addRow(form, row, "Grup", group);
        row = addRow(form, row, "Host", host);
        var portUser = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        portUser.add(port);
        portUser.add(new JLabel("   Username  "));
        portUser.add(username);
        row = addRow(form, row, "Port", portUser);
        row = addRow(form, row, "Autentikasi", auth);
        var keyPanel = new JPanel(new BorderLayout(4, 0));
        keyPanel.add(keyPath, BorderLayout.CENTER);
        keyPanel.add(browseKey, BorderLayout.EAST);
        row = addRow(form, row, "Private key", keyPanel);
        row = addRow(form, row, "Jump host", jumpHost);
        row = addRow(form, row, "Environment", environment);
        row = addRow(form, row, "Direktori awal", initialDir);
        row = addRow(form, row, "Catatan", new JScrollPane(notes));
        row = addRow(form, row, "", autoSudo);

        var header = new JLabel("Secret (terenkripsi di vault; kosong = tidak diubah)");
        header.setBorder(BorderFactory.createEmptyBorder(10, 0, 2, 0));
        header.setFont(header.getFont().deriveFont(java.awt.Font.BOLD));
        var hc = new GridBagConstraints();
        hc.gridx = 0;
        hc.gridy = row++;
        hc.gridwidth = 2;
        hc.anchor = GridBagConstraints.LINE_START;
        form.add(header, hc);
        for (SecretType type : SecretType.values()) {
            var field = secretFields.get(type);
            var clear = clearBoxes.get(type);
            field.putClientProperty("JTextField.placeholderText",
                    storedSecrets.contains(type) ? "•••••• (tersimpan)" : "(belum ada)");
            clear.setEnabled(storedSecrets.contains(type));
            clear.addActionListener(e -> field.setEnabled(!clear.isSelected()));
            var panel = new JPanel(new BorderLayout(6, 0));
            panel.add(field, BorderLayout.CENTER);
            panel.add(clear, BorderLayout.EAST);
            row = addRow(form, row, secretLabel(type), panel);
        }
        return form;
    }

    private static String secretLabel(SecretType type) {
        return switch (type) {
            case LOGIN_PASSWORD -> "Password login";
            case KEY_PASSPHRASE -> "Passphrase key";
            case SUDO_PASSWORD -> "Password sudo";
            case ROOT_PASSWORD -> "Password root (su)";
        };
    }

    private Map<SecretType, SecretChange> collectSecretChanges() {
        var changes = new EnumMap<SecretType, SecretChange>(SecretType.class);
        for (SecretType type : SecretType.values()) {
            var field = secretFields.get(type);
            if (clearBoxes.get(type).isSelected()) {
                changes.put(type, new SecretChange.Clear());
            } else if (field.getDocument().getLength() > 0) {
                changes.put(type, new SecretChange.Set(field.getPassword()));
            }
        }
        clearSecretFields();
        return changes;
    }

    private void clearSecretFields() {
        secretFields.values().forEach(f -> f.setText(""));
    }

    @Override
    public void dispose() {
        clearSecretFields();
        super.dispose();
    }

    private static int addRow(JPanel form, int row, String label, JComponent field) {
        var lc = new GridBagConstraints();
        lc.gridx = 0;
        lc.gridy = row;
        lc.anchor = GridBagConstraints.LINE_END;
        lc.insets = new Insets(3, 0, 3, 8);
        form.add(new JLabel(label), lc);
        var fc = new GridBagConstraints();
        fc.gridx = 1;
        fc.gridy = row;
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1;
        fc.insets = new Insets(3, 0, 3, 0);
        form.add(field, fc);
        return row + 1;
    }

    private JComponent buildButtons() {
        var ok = new JButton("Simpan");
        var cancel = new JButton("Batal");
        ok.addActionListener(e -> onSave());
        cancel.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        var panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.add(ok);
        panel.add(cancel);
        return panel;
    }

    private static DefaultListCellRenderer labelRenderer(java.util.function.Function<Object, String> label) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value != null) {
                    setText(label.apply(value));
                }
                return this;
            }
        };
    }
}
