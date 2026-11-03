package dev.egateza.termul.app.ui.profiles;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.snippet.SnippetUi;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.UiAsync;
import dev.egateza.termul.core.io.AtomicFiles;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import dev.egateza.termul.core.profile.ProfileTransfer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Export profiles (prototipe): pilih cakupan, sertakan snippet, pratinjau JSON, salin atau simpan. EDT. */
final class ProfileExportDialog extends JDialog {

    private record Choice(ProfileManagerDialog.ExportScope scope, String label, List<HostProfile> profiles) {
    }

    private final ProfileManagerHost host;
    private final ProfileSnapshot snapshot;
    private final BiConsumer<String, Boolean> toast;
    private final List<Choice> choices = new ArrayList<>();
    private final List<ChoiceButton> buttons = new ArrayList<>();
    private final SnippetUi.Toggle snippets;
    private final JTextArea preview = new JTextArea();
    private final JLabel meta = SnippetUi.mutedLabel("");
    private final String fileName = "termul-profiles-" + LocalDate.now() + ".json";
    private Choice current;

    ProfileExportDialog(Window owner, ProfileManagerHost host, ProfileSnapshot snapshot, List<HostProfile> shown,
                        List<HostProfile> picked, HostProfile one, ProfileManagerDialog.ExportScope initial,
                        BiConsumer<String, Boolean> toast) {
        super(owner, I18n.t("pm.export.title"), ModalityType.APPLICATION_MODAL);
        this.host = host;
        this.snapshot = snapshot;
        this.toast = toast;
        choices.add(new Choice(ProfileManagerDialog.ExportScope.ALL, I18n.t("pm.export.all"), snapshot.profiles()));
        choices.add(new Choice(ProfileManagerDialog.ExportScope.SHOWN, I18n.t("pm.export.shown"), shown));
        if (picked.size() > 1) {
            choices.add(new Choice(ProfileManagerDialog.ExportScope.PICKED, I18n.t("pm.export.picked"), picked));
        } else if (one != null) {
            choices.add(new Choice(ProfileManagerDialog.ExportScope.ONE, one.name(), List.of(one)));
        }
        current = choices.stream().filter(c -> c.scope() == initial).findFirst().orElse(choices.get(0));
        int total = host.snippetCount();
        snippets = SnippetUi.toggle(I18n.t("pm.export.snippets"), I18n.t("pm.export.snippets.hint", total),
                total > 0, SnippetUi::accent);
        snippets.box().setEnabled(total > 0);
        snippets.box().addActionListener(e -> refresh());

        var top = new SnippetUi.TrackingPanel(null);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBackground(SnippetUi.panel());
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(16, 18, 12, 18)));
        PmDialogs.add(top, SnippetUi.label(I18n.t("pm.export.title"), SnippetUi.font(1, Font.BOLD), SnippetUi.fg()), 12);
        for (var c : choices) {
            var b = new ChoiceButton(c);
            buttons.add(b);
            PmDialogs.add(top, b, 6);
        }
        PmDialogs.add(top, snippets.row(), 0);

        var mid = new JPanel(new BorderLayout(0, 6));
        mid.setBackground(SnippetUi.panel());
        mid.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
        var line = new JPanel(new BorderLayout(8, 0));
        line.setOpaque(false);
        var left = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(ProfileManagerDialog.sectionLabel(I18n.t("pm.export.preview")));
        left.add(SnippetUi.label(fileName, SnippetUi.mono(-1.5f), SnippetUi.muted()));
        line.add(left, BorderLayout.WEST);
        line.add(meta, BorderLayout.EAST);
        mid.add(line, BorderLayout.NORTH);
        preview.setEditable(false);
        preview.setFont(SnippetUi.mono(-1.5f));
        preview.setForeground(SnippetUi.fg());
        preview.setBackground(SnippetUi.field());
        preview.getAccessibleContext().setAccessibleName(I18n.t("pm.export.preview"));
        var scroll = new JScrollPane(preview);
        scroll.setBorder(BorderFactory.createLineBorder(SnippetUi.border()));
        scroll.setPreferredSize(new Dimension(0, 300));
        mid.add(scroll, BorderLayout.CENTER);
        mid.add(SnippetUi.text(I18n.t("pm.export.hint"), SnippetUi.font(-1, Font.PLAIN), SnippetUi.muted()),
                BorderLayout.SOUTH);

        var cancel = new SnippetUi.Button(I18n.t("common.cancel"), null, SnippetUi.Variant.DEFAULT);
        cancel.addActionListener(e -> dispose());
        var copy = new SnippetUi.Button(I18n.t("pm.export.copy"), null, SnippetUi.Variant.DEFAULT);
        copy.addActionListener(e -> copy());
        var save = new SnippetUi.Button(I18n.t("pm.export.save"), null, SnippetUi.Variant.PRIMARY);
        save.addActionListener(e -> saveAs());

        var content = new JPanel(new BorderLayout());
        content.add(top, BorderLayout.NORTH);
        content.add(mid, BorderLayout.CENTER);
        content.add(PmDialogs.foot(null, cancel, copy, save), BorderLayout.SOUTH);
        refresh();
        PmDialogs.shell(this, owner, content, 640, 640);
        getRootPane().setDefaultButton(save);
    }

    private byte[] json() {
        try {
            return ProfileTransfer.export(snapshot, current.profiles(),
                    snippets.box().isSelected() ? host.exportSnippets() : null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void refresh() {
        buttons.forEach(ChoiceButton::repaint);
        preview.setText(new String(json(), StandardCharsets.UTF_8));
        preview.setCaretPosition(0);
        meta.setText(ProfileFacts.profiles(current.profiles().size()));
    }

    private void copy() {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(new String(json(), StandardCharsets.UTF_8)), null);
        dispose();
        toast.accept(I18n.t("pm.export.copied", ProfileFacts.profiles(current.profiles().size())), false);
    }

    private void saveAs() {
        var chooser = new JFileChooser();
        chooser.setDialogTitle(I18n.t("pm.export.title"));
        chooser.setSelectedFile(new java.io.File(fileName));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON (*.json)", "json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath();
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
            target = target.resolveSibling(target.getFileName() + ".json");
        }
        if (Files.exists(target)
                && !Dialogs.confirm(this, I18n.t("export.title"), I18n.t("export.overwrite", target.toString()))) {
            return;
        }
        byte[] bytes = json();
        Path out = target;
        int n = current.profiles().size();
        UiAsync.run(host.io(), () -> {
            try {
                AtomicFiles.write(out, bytes);
            } catch (IOException e) {
                throw new UncheckedIOException(I18n.t("pm.export.failed", out.toString()), e);
            }
        }, err -> Dialogs.error(this, I18n.t("pm.export.title"), err)).thenRun(() -> javax.swing.SwingUtilities
                .invokeLater(() -> {
                    dispose();
                    toast.accept(I18n.t("pm.export.saved", ProfileFacts.profiles(n)), false);
                }));
    }

    /** Pilihan radio 32 px (prototipe): bulatan, label, jumlah. */
    private final class ChoiceButton extends JButton {
        private final Choice choice;

        ChoiceButton(Choice choice) {
            super(choice.label());
            this.choice = choice;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(true);
            setOpaque(false);
            setHorizontalAlignment(LEFT);
            setFont(SnippetUi.font(0, Font.PLAIN));
            setForeground(SnippetUi.fg());
            setBorder(BorderFactory.createEmptyBorder(0, 34, 0, 10));
            setPreferredSize(new Dimension(0, 32));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            getAccessibleContext().setAccessibleName(choice.label());
            addActionListener(e -> {
                current = choice;
                refresh();
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            boolean on = current == choice;
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (on) {
                g2.setColor(SnippetUi.selection());
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            }
            g2.setColor(on ? SnippetUi.accent() : SnippetUi.border());
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            int cy = getHeight() / 2;
            g2.setStroke(new java.awt.BasicStroke(2));
            g2.drawOval(11, cy - 6, 12, 12);
            if (on) {
                g2.fillOval(14, cy - 3, 6, 6);
            }
            g2.setFont(getFont());
            g2.setColor(SnippetUi.muted());
            String count = ProfileFacts.profiles(choice.profiles().size());
            var fm = g2.getFontMetrics();
            g2.drawString(count, getWidth() - 10 - fm.stringWidth(count), (getHeight() - fm.getHeight()) / 2
                    + fm.getAscent());
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
