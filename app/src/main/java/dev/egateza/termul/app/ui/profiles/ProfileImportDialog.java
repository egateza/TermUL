package dev.egateza.termul.app.ui.profiles;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.snippet.SnippetUi;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.UiAsync;

import dev.egateza.termul.core.profile.ProfileSnapshot;
import dev.egateza.termul.core.profile.ProfileTransfer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * Import profiles (prototipe): dari file TermUL (.json) atau OpenSSH config, folder tujuan, status tiap profil
 * (New / Unchanged / Conflict) dengan pilihan Skip / Overwrite / Keep both untuk konflik. File import tidak pernah
 * berisi password. EDT.
 */
final class ProfileImportDialog extends JDialog {

    private enum Source { JSON, SSH }

    private final ProfileManagerHost host;
    private final BiConsumer<String, UUID> done;
    private final ProfileManagerDialog.FolderChoice fromFile = new ProfileManagerDialog.FolderChoice(null,
            I18n.t("pm.import.fromFile"));
    private Source source = Source.JSON;
    private final Map<Source, ProfileManagerDialog.SegButton> sourceButtons = new HashMap<>();
    private final JLabel file = SnippetUi.label("", SnippetUi.mono(-1.5f), SnippetUi.muted());
    private final JComboBox<ProfileManagerDialog.FolderChoice> folder = new JComboBox<>();
    private final JPanel table = new SnippetUi.TrackingPanel(null);
    private final JLabel summary = SnippetUi.mutedLabel("");
    private final SnippetUi.Button importButton = new SnippetUi.Button(I18n.t("pm.import.apply"), null,
            SnippetUi.Variant.PRIMARY);
    private final String defaultFolder;
    private ProfileTransfer.Source loaded = new ProfileTransfer.Source(List.of(), List.of());
    private List<ProfileTransfer.Row> rows = List.of();
    private final Map<Integer, ProfileTransfer.Action> actions = new HashMap<>();
    private Path path;

    /** @param done pesan ringkasan + id profil pertama yang ditambahkan (null kalau tidak ada) */
    ProfileImportDialog(Window owner, ProfileManagerHost host, ProfileSnapshot snapshot, String currentFolder,
                        BiConsumer<String, UUID> done) {
        super(owner, I18n.t("pm.import.title"), ModalityType.APPLICATION_MODAL);
        this.host = host;
        this.done = done;
        this.defaultFolder = currentFolder;

        var top = new SnippetUi.TrackingPanel(null);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBackground(SnippetUi.panel());
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(16, 18, 12, 18)));
        PmDialogs.add(top, SnippetUi.label(I18n.t("pm.import.title"), SnippetUi.font(1, Font.BOLD), SnippetUi.fg()), 12);
        var seg = new JPanel(new GridLayout(1, 2));
        seg.setOpaque(false);
        seg.setBorder(BorderFactory.createLineBorder(SnippetUi.border()));
        seg.setPreferredSize(new Dimension(0, 30));
        for (var s : Source.values()) {
            var b = new ProfileManagerDialog.SegButton(I18n.t(s == Source.JSON ? "pm.import.json" : "pm.import.ssh"));
            b.addActionListener(e -> setSource(s));
            sourceButtons.put(s, b);
            seg.add(b);
        }
        PmDialogs.add(top, seg, 12);

        var grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        var fileBox = new SnippetUi.RoundPanel(new BorderLayout(), SnippetUi::field, SnippetUi::border, 8);
        fileBox.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        fileBox.setPreferredSize(new Dimension(0, 30));
        fileBox.add(file, BorderLayout.CENTER);
        var browse = new SnippetUi.Button(I18n.t("pm.form.browse"), null, SnippetUi.Variant.DEFAULT);
        browse.addActionListener(e -> browse());
        var fileLine = new JPanel(new BorderLayout(8, 0));
        fileLine.setOpaque(false);
        fileLine.add(fileBox, BorderLayout.CENTER);
        fileLine.add(browse, BorderLayout.EAST);
        var a = new GridBagConstraints();
        a.gridx = 0;
        a.weightx = 1;
        a.fill = GridBagConstraints.HORIZONTAL;
        a.anchor = GridBagConstraints.LAST_LINE_START;
        a.insets = new Insets(0, 0, 0, 10);
        grid.add(labeled(I18n.t("pm.import.file"), fileLine), a);
        SnippetUi.styleField(folder, 30);
        folder.setPreferredSize(new Dimension(180, 30));
        folder.getAccessibleContext().setAccessibleName(I18n.t("pm.import.target"));
        var b2 = new GridBagConstraints();
        b2.gridx = 1;
        b2.anchor = GridBagConstraints.LAST_LINE_START;
        grid.add(labeled(I18n.t("pm.import.target"), folder), b2);
        PmDialogs.add(top, grid, 0);
        folderChoices(snapshot);

        table.setLayout(new BoxLayout(table, BoxLayout.Y_AXIS));
        table.setBackground(SnippetUi.panel());
        var scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(SnippetUi.panel());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        var cancel = new SnippetUi.Button(I18n.t("common.cancel"), null, SnippetUi.Variant.DEFAULT);
        cancel.addActionListener(e -> dispose());
        importButton.addActionListener(e -> apply());
        summary.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        var foot = PmDialogs.foot(summary, null, cancel, importButton);

        var content = new JPanel(new BorderLayout());
        content.add(top, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(foot, BorderLayout.SOUTH);
        setSource(Source.JSON);
        PmDialogs.shell(this, owner, content, 720, 620);
    }

    private static JPanel labeled(String label, JComponent c) {
        var p = new SnippetUi.TrackingPanel(null);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        PmDialogs.add(p, ProfileManagerDialog.sectionLabel(label), 5);
        PmDialogs.add(p, c, 0);
        return p;
    }

    private void folderChoices(ProfileSnapshot snapshot) {
        folder.removeAllItems();
        if (source == Source.JSON) {
            folder.addItem(fromFile);
        }
        for (String g : snapshot.allGroups()) {
            int depth = (int) g.chars().filter(c -> c == '/').count();
            folder.addItem(new ProfileManagerDialog.FolderChoice(g, "  ".repeat(depth) + g.substring(g.lastIndexOf('/') + 1)));
        }
        folder.addItem(new ProfileManagerDialog.FolderChoice("", I18n.t("pm.folder.ungrouped")));
        if (source == Source.SSH || !defaultFolder.isEmpty()) {
            for (int i = 0; i < folder.getItemCount(); i++) {
                if (defaultFolder.equals(folder.getItemAt(i).group())) {
                    folder.setSelectedIndex(i);
                }
            }
        }
    }

    private void setSource(Source s) {
        source = s;
        sourceButtons.forEach((k, b) -> b.setOn(k == s));
        folderChoices(host.store().snapshot());
        path = null;
        rows = List.of();
        actions.clear();
        if (s == Source.SSH && Files.isRegularFile(host.defaultSshConfig())) {
            load(host.defaultSshConfig());
        } else {
            file.setText(I18n.t(s == Source.JSON ? "pm.import.noFile" : "pm.import.noSshConfig"));
            rebuild();
        }
    }

    private void browse() {
        var chooser = new JFileChooser(path != null ? path.toFile() : source == Source.SSH
                ? host.defaultSshConfig().getParent().toFile() : null);
        chooser.setDialogTitle(I18n.t("pm.import.title"));
        chooser.setFileHidingEnabled(false);
        if (source == Source.JSON) {
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON (*.json)", "json"));
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            load(chooser.getSelectedFile().toPath());
        }
    }

    private void load(Path p) {
        Source s = source;
        file.setText(p.toString());
        UiAsync.run(host.io(), () -> {
            try {
                if (s == Source.JSON) {
                    return ProfileTransfer.read(Files.readAllBytes(p));
                }
                return new ProfileTransfer.Source(List.of(), host.readSshConfig(p));
            } catch (IOException e) {
                throw new UncheckedIOException(e.getMessage(), e);
            }
        }, src -> {
            if (s != source) {
                return;
            }
            path = p;
            loaded = src;
            rows = ProfileTransfer.plan(host.store().snapshot(), src.profiles());
            actions.clear();
            rebuild();
        }, err -> {
            rows = List.of();
            rebuild();
            Dialogs.error(this, I18n.t("pm.import.title"), err);
        });
    }

    private void rebuild() {
        table.removeAll();
        table.add(rowPanel(true, SnippetUi.label(I18n.t("pm.col.name"), SnippetUi.font(-1, Font.BOLD), SnippetUi.muted()),
                SnippetUi.label(I18n.t("pm.col.address"), SnippetUi.font(-1, Font.BOLD), SnippetUi.muted()),
                SnippetUi.label(I18n.t("pm.import.status"), SnippetUi.font(-1, Font.BOLD), SnippetUi.muted()),
                SnippetUi.label(I18n.t("pm.import.action"), SnippetUi.font(-1, Font.BOLD), SnippetUi.muted())));
        int add = 0;
        int update = 0;
        int skip = 0;
        var changes = new ArrayList<String>();
        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            var in = r.incoming();
            JComponent action;
            switch (r.status()) {
                case NEW -> {
                    add++;
                    action = SnippetUi.mutedLabel(I18n.t("pm.import.act.add"));
                }
                case SAME -> {
                    skip++;
                    action = SnippetUi.mutedLabel(I18n.t("pm.import.act.skip"));
                }
                default -> {
                    var act = actions.getOrDefault(i, ProfileTransfer.Action.SKIP);
                    switch (act) {
                        case SKIP -> skip++;
                        case OVERWRITE -> update++;
                        case KEEP_BOTH -> add++;
                    }
                    changes.add(I18n.t("pm.import.change", in.name(), r.existing().address(), in.address()));
                    var box = new JComboBox<ActionChoice>();
                    for (var x : ProfileTransfer.Action.values()) {
                        box.addItem(new ActionChoice(x));
                    }
                    box.setSelectedIndex(act.ordinal());
                    SnippetUi.styleField(box, 28);
                    box.getAccessibleContext().setAccessibleName(I18n.t("pm.import.actionFor", in.name()));
                    int index = i;
                    box.addActionListener(e -> {
                        if (box.getSelectedItem() instanceof ActionChoice c) {
                            actions.put(index, c.action());
                            rebuild();
                        }
                    });
                    action = box;
                }
            }
            table.add(rowPanel(false, SnippetUi.label(in.name(), SnippetUi.font(0, Font.BOLD), SnippetUi.fg()),
                    SnippetUi.label(in.address(), SnippetUi.mono(-1.5f), SnippetUi.muted()), new StatusBadge(r.status()),
                    action));
        }
        if (!changes.isEmpty()) {
            var h = SnippetUi.text(String.join(" ", changes), SnippetUi.font(-1, Font.PLAIN), SnippetUi.muted());
            h.setBorder(BorderFactory.createEmptyBorder(8, 10, 0, 10));
            PmDialogs.add(table, h, 0);
        }
        var hint = SnippetUi.text(I18n.t("pm.import.noPasswords"), SnippetUi.font(-1, Font.PLAIN), SnippetUi.muted());
        hint.setBorder(BorderFactory.createEmptyBorder(8, 10, 12, 10));
        PmDialogs.add(table, hint, 0);
        table.add(javax.swing.Box.createVerticalGlue());
        table.revalidate();
        table.repaint();
        summary.setText(I18n.t("pm.import.summary", add, update, skip));
        importButton.setEnabled(add + update > 0);
    }

    private static JPanel rowPanel(boolean header, JComponent name, JComponent addr, JComponent status,
                                   JComponent action) {
        var p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SnippetUi.alpha(SnippetUi.border(), 0.6f)),
                BorderFactory.createEmptyBorder(3, 10, 3, 10)));
        int h = header ? 28 : 36;
        p.setPreferredSize(new Dimension(0, h));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        p.setAlignmentX(0f);
        JComponent[] cells = {name, addr, status, action};
        int[] widths = {-1, -1, 84, 120};
        for (int i = 0; i < cells.length; i++) {
            var c = new GridBagConstraints();
            c.gridx = i;
            c.weightx = widths[i] < 0 ? 1 : 0;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(0, 0, 0, i < cells.length - 1 ? 10 : 0);
            if (widths[i] > 0) {
                cells[i].setPreferredSize(new Dimension(widths[i], cells[i].getPreferredSize().height));
            } else {
                cells[i].setPreferredSize(new Dimension(40, cells[i].getPreferredSize().height));
            }
            p.add(cells[i], c);
        }
        return p;
    }

    private record ActionChoice(ProfileTransfer.Action action) {
        @Override
        public String toString() {
            return I18n.t(switch (action) {
                case SKIP -> "pm.import.act.skip";
                case OVERWRITE -> "pm.import.act.overwrite";
                case KEEP_BOTH -> "pm.import.act.both";
            });
        }
    }

    /** Badge status (hijau New, abu-abu Unchanged, kuning Conflict). */
    private static final class StatusBadge extends JComponent {
        private final ProfileTransfer.Status status;

        StatusBadge(ProfileTransfer.Status status) {
            this.status = status;
            setPreferredSize(new Dimension(84, 16));
        }

        @Override
        protected void paintComponent(Graphics g) {
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = switch (status) {
                case NEW -> SnippetUi.success();
                case SAME -> SnippetUi.muted();
                case CONFLICT -> SnippetUi.favorite();
            };
            ProfileManagerDialog.badge(g2, I18n.t(switch (status) {
                case NEW -> "pm.import.st.new";
                case SAME -> "pm.import.st.same";
                case CONFLICT -> "pm.import.st.conflict";
            }), c, 0, getHeight());
            g2.dispose();
        }
    }

    private void apply() {
        var snapshot = host.store().snapshot();
        var choice = (ProfileManagerDialog.FolderChoice) folder.getSelectedItem();
        String target = choice == null ? "" : choice.group();
        var result = ProfileTransfer.apply(snapshot, rows, Map.copyOf(actions), target, loaded);
        UiAsync.run(host.io(), () -> host.store().importProfiles(result.newFolders(), result.toSave(),
                result.favorites(), result.order()), s -> {
            dispose();
            done.accept(I18n.t("pm.import.done", result.added(), result.updated(), result.skipped()),
                    result.firstAdded());
        }, err -> Dialogs.error(this, I18n.t("main.error.saveProfile"), err));
    }
}
