package dev.egateza.termul.app.ui.profiles;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.snippet.SnippetUi;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.UiAsync;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Manage folders (prototipe): urutan folder (naik/turun di antara folder seinduk), ganti nama dan hapus langsung di
 * baris, Ungrouped selalu terakhir, dan folder baru. Folder yang dihapus memindahkan profilnya ke Ungrouped. EDT.
 */
final class ProfileFoldersDialog extends JDialog {

    static final int MAX_NAME = 24;

    private final ProfileManagerHost host;
    private final BiConsumer<String, Boolean> toast;
    private final Consumer<ProfileSnapshot> listener = s -> SwingUtilities.invokeLater(() -> rebuild(s));
    private final JPanel rows = new SnippetUi.TrackingPanel(null);
    private final JLabel countLabel = SnippetUi.mutedLabel("");
    private final JTextField newName = new JTextField();
    private final JLabel addError = SnippetUi.label("", SnippetUi.font(-0.5f, Font.PLAIN), SnippetUi.danger());
    private String editing;      // EDT; folder yang sedang diganti nama
    private String deleting;     // EDT; folder yang menunggu konfirmasi hapus
    private String editError;    // EDT
    private final JTextField editField = new JTextField();

    ProfileFoldersDialog(Window owner, ProfileManagerHost host, BiConsumer<String, Boolean> toast) {
        super(owner, I18n.t("pm.folders.title"), ModalityType.APPLICATION_MODAL);
        this.host = host;
        this.toast = toast;
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setBackground(SnippetUi.panel());
        rows.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        var scroll = new JScrollPane(rows);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(SnippetUi.panel());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        SnippetUi.styleField(newName, 30);
        newName.putClientProperty("JTextField.placeholderText", I18n.t("pm.folders.new.ph"));
        newName.getAccessibleContext().setAccessibleName(I18n.t("pm.folders.new"));
        newName.getDocument().addDocumentListener(ProfileManagerDialog.onChange(() -> showAddError(null)));
        newName.addActionListener(e -> addFolder());
        var add = new SnippetUi.Button(I18n.t("pm.folders.add"),
                SnippetUi.stroke(SnippetUi.PLUS, 13, SnippetUi::fg, 2.4f), SnippetUi.Variant.DEFAULT);
        add.addActionListener(e -> addFolder());
        var addRow = new JPanel(new BorderLayout(8, 0));
        addRow.setOpaque(false);
        addRow.add(newName, BorderLayout.CENTER);
        addRow.add(add, BorderLayout.EAST);
        addError.setVisible(false);
        var newBox = new SnippetUi.TrackingPanel(null);
        newBox.setLayout(new BoxLayout(newBox, BoxLayout.Y_AXIS));
        newBox.setBackground(SnippetUi.panel());
        newBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(12, 18, 12, 18)));
        PmDialogs.add(newBox, ProfileManagerDialog.sectionLabel(I18n.t("pm.folders.new")), 6);
        PmDialogs.add(newBox, addRow, 4);
        PmDialogs.add(newBox, addError, 0);
        var done = new SnippetUi.Button(I18n.t("pm.folders.done"), null, SnippetUi.Variant.PRIMARY);
        done.addActionListener(e -> dispose());
        var foot = PmDialogs.foot(null, done);
        foot.setBackground(SnippetUi.bar());

        var south = new JPanel(new BorderLayout());
        south.add(newBox, BorderLayout.NORTH);
        south.add(foot, BorderLayout.SOUTH);
        var content = new JPanel(new BorderLayout());
        content.add(PmDialogs.head(I18n.t("pm.folders.title"), I18n.t("pm.folders.sub"), countLabel),
                BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        rebuild(host.store().snapshot());
        PmDialogs.shell(this, owner, content, 520, 560);
        getRootPane().setDefaultButton(null);
        host.store().addListener(listener);
    }

    @Override
    public void dispose() {
        host.store().removeListener(listener);
        super.dispose();
    }

    private void rebuild(ProfileSnapshot s) {
        rows.removeAll();
        var groups = s.allGroups();
        countLabel.setText(I18n.t(groups.size() == 1 ? "pm.folders.count.one" : "pm.folders.count", groups.size()));
        for (String g : groups) {
            var siblings = s.childGroups(ProfileSnapshot.parentOf(g));
            int index = siblings.indexOf(g);
            PmDialogs.add(rows, row(s, g, index == 0, index == siblings.size() - 1), 2);
        }
        var unc = new JPanel(new BorderLayout(8, 0));
        unc.setOpaque(false);
        unc.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        var label = SnippetUi.label(I18n.t("pm.folder.ungrouped") + "  ", SnippetUi.font(0, Font.PLAIN),
                SnippetUi.muted());
        label.setIcon(SnippetUi.folder(14, SnippetUi::muted));
        label.setIconTextGap(8);
        var texts = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        texts.setOpaque(false);
        texts.add(label);
        texts.add(SnippetUi.label(I18n.t("pm.folders.ungrouped.note"), SnippetUi.font(-1, Font.PLAIN),
                SnippetUi.muted()));
        unc.add(texts, BorderLayout.CENTER);
        unc.add(SnippetUi.label(ProfileFacts.profiles(s.profilesIn("").size()), SnippetUi.font(-1, Font.PLAIN),
                SnippetUi.muted()), BorderLayout.EAST);
        unc.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        PmDialogs.add(rows, unc, 0);
        rows.add(Box.createVerticalGlue());
        rows.revalidate();
        rows.repaint();
    }

    private JComponent row(ProfileSnapshot s, String g, boolean first, boolean last) {
        int depth = (int) g.chars().filter(c -> c == '/').count();
        String leaf = g.substring(g.lastIndexOf('/') + 1);
        int n = (int) s.profiles().stream().filter(p -> p.group().equals(g) || p.group().startsWith(g + "/")).count();
        var line = new JPanel(new BorderLayout(8, 0));
        line.setOpaque(false);
        line.setBorder(BorderFactory.createEmptyBorder(4, 8 + depth * 18, 4, 8));
        if (g.equals(editing)) {
            SnippetUi.styleField(editField, 28);
            editField.getAccessibleContext().setAccessibleName(I18n.t("pm.folders.name"));
            editField.putClientProperty("JComponent.outline", editError == null ? null : "error");
            var cancel = small(I18n.t("common.cancel"), SnippetUi.Variant.DEFAULT, () -> {
                editing = null;
                editError = null;
                rebuild(host.store().snapshot());
            });
            var save = small(I18n.t("common.save"), SnippetUi.Variant.PRIMARY, () -> rename(g));
            for (var l : editField.getActionListeners()) {
                editField.removeActionListener(l);
            }
            editField.addActionListener(e -> rename(g));
            line.add(editField, BorderLayout.CENTER);
            line.add(buttons(cancel, save), BorderLayout.EAST);
            SwingUtilities.invokeLater(editField::requestFocusInWindow);
        } else if (g.equals(deleting)) {
            line.add(SnippetUi.text(n == 0 ? I18n.t("pm.folders.delete.empty", leaf)
                    : I18n.t("pm.folders.delete.move", leaf, ProfileFacts.profiles(n)), SnippetUi.font(0, Font.PLAIN),
                    SnippetUi.fg()), BorderLayout.CENTER);
            var cancel = small(I18n.t("common.cancel"), SnippetUi.Variant.PRIMARY, () -> {
                deleting = null;
                rebuild(host.store().snapshot());
            });
            var del = small(I18n.t("pm.delete.ok"), SnippetUi.Variant.DANGER, () -> delete(g, leaf, n));
            line.add(buttons(cancel, del), BorderLayout.EAST);
        } else {
            var name = SnippetUi.label(leaf, SnippetUi.font(0, Font.BOLD), SnippetUi.fg());
            name.setIcon(SnippetUi.folder(14, SnippetUi::favorite));
            name.setIconTextGap(8);
            line.add(name, BorderLayout.CENTER);
            var count = SnippetUi.label(ProfileFacts.profiles(n), SnippetUi.font(-1, Font.PLAIN), SnippetUi.muted());
            var up = icon(ProfileManagerDialog.UP, I18n.t("pm.folders.up", leaf), first,
                    () -> move(g, -1), SnippetUi.Variant.ICON);
            var down = icon(ProfileManagerDialog.DOWN, I18n.t("pm.folders.down", leaf), last,
                    () -> move(g, 1), SnippetUi.Variant.ICON);
            var ren = icon(ProfileManagerDialog.EDIT, I18n.t("pm.folders.rename", leaf), false, () -> {
                editing = g;
                deleting = null;
                editError = null;
                editField.setText(leaf);
                rebuild(host.store().snapshot());
            }, SnippetUi.Variant.ICON);
            var del = icon(SnippetUi.TRASH, I18n.t("pm.folders.delete", leaf), false, () -> {
                deleting = g;
                editing = null;
                rebuild(host.store().snapshot());
            }, SnippetUi.Variant.DANGER);
            line.add(buttons(count, up, down, ren, del), BorderLayout.EAST);
        }
        var box = new SnippetUi.TrackingPanel(null);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        PmDialogs.add(box, line, 0);
        if (g.equals(editing) && editError != null) {
            var err = SnippetUi.label(editError, SnippetUi.font(-0.5f, Font.PLAIN), SnippetUi.danger());
            err.setBorder(BorderFactory.createEmptyBorder(0, 30 + depth * 18, 4, 8));
            PmDialogs.add(box, err, 0);
        }
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
        return box;
    }

    private static JPanel buttons(JComponent... parts) {
        var p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        p.setOpaque(false);
        for (var c : parts) {
            p.add(c);
        }
        return p;
    }

    private static SnippetUi.Button small(String text, SnippetUi.Variant v, Runnable action) {
        var b = new SnippetUi.Button(text, null, v);
        b.addActionListener(e -> action.run());
        b.setPreferredSize(new Dimension(b.getPreferredSize().width, 28));
        return b;
    }

    private static SnippetUi.Button icon(String path, String tip, boolean dim, Runnable action, SnippetUi.Variant v) {
        var b = new SnippetUi.Button(null, SnippetUi.stroke(path, 13, () -> v == SnippetUi.Variant.DANGER
                ? SnippetUi.danger() : SnippetUi.alpha(SnippetUi.muted(), dim ? 0.35f : 1f), 2.1f),
                SnippetUi.Variant.ICON);
        b.setToolTipText(tip);
        b.getAccessibleContext().setAccessibleName(tip);
        b.setEnabled(!dim);
        b.addActionListener(e -> action.run());
        return b;
    }

    /**
     * Pesan error nama folder (prototipe {@code folderErr}), null kalau valid.
     *
     * @param siblings nama folder seinduk lain (tanpa folder yang sedang diganti nama)
     */
    static String folderError(String name, java.util.Collection<String> siblings) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty()) {
            return I18n.t("pm.folders.err.empty");
        }
        if (n.length() > MAX_NAME) {
            return I18n.t("pm.folders.err.long", MAX_NAME);
        }
        if (n.contains("/") || n.contains("\\")) {
            return I18n.t("pm.folders.err.slash");
        }
        String lower = n.toLowerCase(Locale.ROOT);
        if (lower.equals(I18n.t("pm.folder.ungrouped").toLowerCase(Locale.ROOT)) || lower.equals("ungrouped")
                || siblings.stream().anyMatch(x -> x.toLowerCase(Locale.ROOT).equals(lower))) {
            return I18n.t("pm.folders.err.used");
        }
        return null;
    }

    private static java.util.List<String> leaves(ProfileSnapshot s, String parent, String except) {
        return s.childGroups(parent).stream().filter(g -> !g.equals(except))
                .map(g -> g.substring(g.lastIndexOf('/') + 1)).toList();
    }

    private void showAddError(String error) {
        addError.setText(error == null ? "" : error);
        addError.setVisible(error != null);
        newName.putClientProperty("JComponent.outline", error == null ? null : "error");
    }

    private void addFolder() {
        var s = host.store().snapshot();
        String err = folderError(newName.getText(), leaves(s, "", null));
        if (err != null) {
            showAddError(err);
            return;
        }
        String name = newName.getText().strip();
        UiAsync.run(host.io(), () -> host.store().addGroup(name), r -> {
            newName.setText("");
            toast.accept(I18n.t("pm.folders.added", name), false);
        }, e -> Dialogs.error(this, I18n.t("main.error.createGroup"), e));
    }

    private void rename(String g) {
        var s = host.store().snapshot();
        String parent = ProfileSnapshot.parentOf(g);
        String err = folderError(editField.getText(), leaves(s, parent, g));
        if (err != null) {
            editError = err;
            rebuild(s);
            return;
        }
        String target = HostProfile.normalizeGroup(parent.isEmpty() ? editField.getText().strip()
                : parent + "/" + editField.getText().strip());
        editing = null;
        editError = null;
        if (target.equals(g)) {
            rebuild(s);
            return;
        }
        UiAsync.run(host.io(), () -> host.store().renameGroup(g, target),
                e -> Dialogs.error(this, I18n.t("main.error.renameGroup"), e));
    }

    private void move(String g, int delta) {
        UiAsync.run(host.io(), () -> host.store().moveGroup(g, delta),
                e -> Dialogs.error(this, I18n.t("main.error.renameGroup"), e));
    }

    private void delete(String g, String leaf, int n) {
        deleting = null;
        UiAsync.run(host.io(), () -> host.store().deleteGroupKeepProfiles(g), r -> toast.accept(n == 0
                ? I18n.t("pm.folders.deleted", leaf)
                : I18n.t("pm.folders.deletedMoved", leaf, ProfileFacts.profiles(n)), false),
                e -> Dialogs.error(this, I18n.t("main.error.deleteGroup"), e));
    }
}
