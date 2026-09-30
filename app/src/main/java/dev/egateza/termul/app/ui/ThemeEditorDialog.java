package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.terminal.BackgroundImages;
import dev.egateza.termul.app.terminal.DesktopWallpaper;
import dev.egateza.termul.core.theme.Contrast;
import dev.egateza.termul.core.theme.CustomTheme;
import dev.egateza.termul.core.theme.CustomTheme.TerminalPalette;
import dev.egateza.termul.core.theme.CustomTheme.UiPalette;
import dev.egateza.termul.core.theme.ThemeIds;
import dev.egateza.termul.core.theme.ThemeStore;
import dev.egateza.termul.core.theme.ThemeTemplates;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingWorker;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Editor tema custom (Pengaturan → Tema → Buat/ubah tema): daftar tema di kiri, form warna di tengah, pratinjau
 * terminal dan peringatan kontras di bawahnya. Tiap tema tersimpan sebagai satu file JSON lewat {@link ThemeStore};
 * perubahan baru ditulis saat menekan Simpan. Semua tema di daftar sudah tersimpan: "Baru", "Duplikat", dan
 * "Impor" langsung membuat filenya. Berjalan di EDT; file yang ditulis berukuran beberapa KB.
 */
public final class ThemeEditorDialog extends JDialog {

    /** @param applyId id tema yang harus langsung dipakai (tombol "Simpan dan terapkan"), null = tidak ada */
    public record Result(String applyId) {
    }

    private record BaseChoice(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final java.util.regex.Pattern IMAGE_NAME =
            java.util.regex.Pattern.compile(".+\\.(png|jpe?g|gif|bmp)", java.util.regex.Pattern.CASE_INSENSITIVE);

    private final ThemeStore store;
    private final Set<String> reservedIds; // id tema bawaan: tidak boleh dipakai tema custom
    private final DefaultListModel<CustomTheme> model = new DefaultListModel<>();
    private final JList<CustomTheme> list = new JList<>(model);

    private final JTextField name = new JTextField(24);
    private final JComboBox<BaseChoice> base = new JComboBox<>(new BaseChoice[] {
            new BaseChoice(CustomTheme.BASE_DARK, I18n.t("theme.editor.base.dark")),
            new BaseChoice(CustomTheme.BASE_LIGHT, I18n.t("theme.editor.base.light"))});
    private final ColorField accent = new ColorField(null, true);
    private final ColorField background = new ColorField(null, true);
    private final ColorField foreground = new ColorField(null, true);
    private final ColorField selection = new ColorField(null, true);
    private final ColorField border = new ColorField(null, true);
    private final ColorField termBackground = new ColorField("#000000", false);
    private final ColorField termForeground = new ColorField("#FFFFFF", false);
    private final ColorField termSelection = new ColorField("#333333", false);
    private final ColorField[] ansi = new ColorField[TerminalPalette.ANSI_COUNT];
    private final JTextField imageName = new JTextField(9);
    private final JButton chooseImage = new JButton(I18n.t("theme.editor.term.image.choose"));
    private final JButton clearImage = new JButton(I18n.t("theme.editor.term.image.clear"));
    private final JSlider imageVisibility = new JSlider(TerminalPalette.MIN_VISIBILITY, 100,
            TerminalPalette.DEFAULT_VISIBILITY);
    private final JCheckBox useWallpaper = new JCheckBox(I18n.t("theme.editor.term.image.wallpaper"));
    private String imagePath; // null = tanpa gambar latar
    private String previewKey; // kunci gambar (BackgroundImages.key) yang sedang/sudah dimuat untuk pratinjau
    private BufferedImage previewImage; // null selama belum selesai dimuat atau gagal
    private boolean previewMissing; // pemuatan selesai tapi gambarnya tidak terbaca
    private final TerminalPreview preview;
    private final JLabel warning = new JLabel(" ");
    private final JPanel form = new JPanel(new GridBagLayout());
    private final JButton duplicate = new JButton(I18n.t("theme.editor.duplicate"));
    private final JButton delete = new JButton(I18n.t("theme.editor.delete"));
    private final JButton export = new JButton(I18n.t("theme.editor.export"));
    private final JButton save = new JButton(I18n.t("theme.editor.save"));
    private final JButton saveApply = new JButton(I18n.t("theme.editor.saveApply"));

    private CustomTheme selected; // tema tersimpan yang sedang diedit; null = daftar kosong
    private boolean dirty;
    private boolean loading; // true saat mengisi kontrol dari tema: perubahan tidak dianggap edit user
    private Result result = new Result(null);

    private ThemeEditorDialog(Component parent, ThemeStore store, Font previewFont, Set<String> reservedIds) {
        super(parent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(parent),
                I18n.t("theme.editor.title"), Dialog.ModalityType.APPLICATION_MODAL);
        this.store = store;
        this.reservedIds = reservedIds;
        for (int i = 0; i < ansi.length; i++) {
            ansi[i] = new ColorField("#000000", false);
        }
        this.preview = new TerminalPreview(ThemeTemplates.dark("x", "x").terminal(), previewFont);

        buildForm();
        for (ColorField f : allColorFields()) {
            f.onChange(this::edited);
        }
        name.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                edited();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                edited();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                edited();
            }
        });
        base.addActionListener(e -> edited());
        imageName.setEditable(false);
        chooseImage.addActionListener(e -> pickImage());
        clearImage.addActionListener(e -> {
            imagePath = null;
            edited();
        });
        imageVisibility.addChangeListener(e -> edited());
        useWallpaper.addActionListener(e -> edited());

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel,
                                                          boolean focus) {
                return super.getListCellRendererComponent(l, ((CustomTheme) value).name(), index, sel, focus);
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !loading) {
                onSelectionChanged();
            }
        });

        var newButton = new JButton(I18n.t("theme.editor.new"));
        var importButton = new JButton(I18n.t("theme.editor.import"));
        newButton.addActionListener(e -> createNew());
        duplicate.addActionListener(e -> duplicateSelected());
        delete.addActionListener(e -> deleteSelected());
        importButton.addActionListener(e -> importTheme());
        export.addActionListener(e -> exportTheme());
        var listButtons = new JPanel(new GridLayout(0, 2, 4, 4));
        for (var b : List.of(newButton, duplicate, delete, importButton, export)) {
            listButtons.add(b);
        }
        var left = new JPanel(new BorderLayout(0, 6));
        left.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 4));
        var listTitle = new JLabel(I18n.t("theme.editor.list"));
        listTitle.setFont(listTitle.getFont().deriveFont(Font.BOLD));
        left.add(listTitle, BorderLayout.NORTH);
        var listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new java.awt.Dimension(210, 300));
        left.add(listScroll, BorderLayout.CENTER);
        left.add(listButtons, BorderLayout.SOUTH);

        var formScroll = new JScrollPane(form);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        formScroll.getVerticalScrollBar().setUnitIncrement(16);
        var previewTitle = new JLabel(I18n.t("theme.editor.preview"));
        previewTitle.setFont(previewTitle.getFont().deriveFont(Font.BOLD));
        var previewBox = new JPanel(new BorderLayout(0, 4));
        previewBox.setBorder(BorderFactory.createEmptyBorder(10, 4, 10, 10));
        previewBox.add(previewTitle, BorderLayout.NORTH);
        previewBox.add(preview, BorderLayout.CENTER);
        warning.setForeground(new java.awt.Color(0xD98E04));
        warning.setPreferredSize(new java.awt.Dimension(420, 84)); // muat dua baris peringatan tanpa menggeser tata letak
        warning.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        previewBox.add(warning, BorderLayout.SOUTH);
        var previewColumn = new JPanel(new BorderLayout()); // pratinjau menempel di atas, tidak ikut melar
        previewColumn.add(previewBox, BorderLayout.NORTH);
        var right = new JPanel(new BorderLayout());
        right.add(formScroll, BorderLayout.CENTER);
        right.add(previewColumn, BorderLayout.EAST);

        save.addActionListener(e -> saveCurrent());
        saveApply.addActionListener(e -> {
            if (saveCurrent()) {
                result = new Result(selected.id());
                dispose();
            }
        });
        var close = new JButton(I18n.t("theme.editor.close"));
        close.addActionListener(e -> closeDialog());
        var footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(save);
        footer.add(saveApply);
        footer.add(close);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeDialog();
            }
        });
        getRootPane().registerKeyboardAction(e -> closeDialog(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        add(left, BorderLayout.WEST);
        add(right, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        setSize(1120, 680);
        setLocationRelativeTo(parent);
    }

    /**
     * Tampilkan editor (modal) sampai ditutup.
     *
     * @param reservedIds id tema bawaan, supaya tema custom tidak menimpanya
     * @param activeId    id tema yang sedang dipakai; dipilih di daftar kalau ada
     */
    public static Result show(Component parent, ThemeStore store, Font previewFont, Set<String> reservedIds,
                              String activeId) {
        var dialog = new ThemeEditorDialog(parent, store, previewFont, reservedIds);
        dialog.reloadList(activeId);
        dialog.setVisible(true);
        return dialog.result;
    }

    // --- form ---

    private void buildForm() {
        form.setBorder(BorderFactory.createEmptyBorder(10, 6, 6, 10));
        var c = new GridBagConstraints();
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.gridy = 0;
        form.add(section(I18n.t("theme.editor.section.general"),
                new String[] {I18n.t("theme.editor.name"), I18n.t("theme.editor.base")},
                new JComponent[] {name, base}), c);
        c.gridy = 1;
        form.add(section(I18n.t("theme.editor.section.ui"),
                new String[] {I18n.t("theme.editor.ui.accent"), I18n.t("theme.editor.ui.background"),
                        I18n.t("theme.editor.ui.foreground"), I18n.t("theme.editor.ui.selection"),
                        I18n.t("theme.editor.ui.border")},
                new JComponent[] {accent, background, foreground, selection, border}), c);

        var terminal = new JPanel(new GridBagLayout());
        terminal.setBorder(BorderFactory.createTitledBorder(I18n.t("theme.editor.section.terminal")));
        addRow(terminal, 0, I18n.t("theme.editor.term.background"), termBackground);
        addRow(terminal, 1, I18n.t("theme.editor.term.foreground"), termForeground);
        addRow(terminal, 2, I18n.t("theme.editor.term.selection"), termSelection);
        var imageButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        imageButtons.add(imageName);
        imageButtons.add(chooseImage);
        imageButtons.add(clearImage);
        addWideRow(terminal, 3, I18n.t("theme.editor.term.image"), imageButtons);
        if (DesktopWallpaper.supported()) { // hanya Windows dengan GUI
            addWideRow(terminal, 4, "", useWallpaper);
        }
        addWideRow(terminal, 5, I18n.t("theme.editor.term.image.visibility"), imageVisibility);
        var g = new GridBagConstraints();
        g.gridy = 6;
        g.insets = new Insets(8, 4, 2, 4);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 1;
        terminal.add(new JLabel(I18n.t("theme.editor.ansi.normal")), g);
        g.gridx = 2;
        terminal.add(new JLabel(I18n.t("theme.editor.ansi.bright")), g);
        for (int i = 0; i < 8; i++) {
            addRow(terminal, 7 + i, I18n.t("theme.ansi." + i), ansi[i], ansi[i + 8]);
        }
        c.gridy = 2;
        form.add(terminal, c);
        c.gridy = 3;
        c.weighty = 1;
        form.add(new JPanel(), c); // pendorong: isi tetap di atas kalau jendela tinggi
    }

    private static JPanel section(String title, String[] labels, JComponent[] fields) {
        var panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        for (int i = 0; i < labels.length; i++) {
            addRow(panel, i, labels[i], fields[i]);
        }
        return panel;
    }

    private static void addRow(JPanel panel, int y, String label, JComponent... fields) {
        var c = new GridBagConstraints();
        c.gridy = y;
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        panel.add(new JLabel(label), c);
        for (int i = 0; i < fields.length; i++) {
            c.gridx = i + 1;
            c.weightx = i == fields.length - 1 ? 1 : 0;
            panel.add(fields[i], c);
        }
    }

    /** Baris yang isinya membentang dua kolom, supaya kolom warna di bawahnya tidak ikut melebar. */
    private static void addWideRow(JPanel panel, int y, String label, JComponent field) {
        var c = new GridBagConstraints();
        c.gridy = y;
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        panel.add(field, c);
    }

    private List<ColorField> allColorFields() {
        var all = new ArrayList<>(List.of(accent, background, foreground, selection, border, termBackground,
                termForeground, termSelection));
        all.addAll(List.of(ansi));
        return all;
    }

    private static void setEnabledDeep(Component c, boolean enabled) {
        c.setEnabled(enabled);
        if (c instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                setEnabledDeep(child, enabled);
            }
        }
    }

    // --- load / collect ---

    /** Isi kontrol dari {@code theme}; null = tidak ada tema (form dimatikan). */
    private void load(CustomTheme theme) {
        loading = true;
        try {
            selected = theme;
            dirty = false;
            setEnabledDeep(form, theme != null);
            if (theme != null) {
                name.setText(theme.name());
                base.setSelectedIndex(CustomTheme.BASE_LIGHT.equals(theme.base()) ? 1 : 0);
                accent.setHex(theme.ui().accent());
                background.setHex(theme.ui().background());
                foreground.setHex(theme.ui().foreground());
                selection.setHex(theme.ui().selection());
                border.setHex(theme.ui().border());
                var t = theme.terminal();
                termBackground.setHex(t.background());
                termForeground.setHex(t.foreground());
                termSelection.setHex(t.selection());
                for (int i = 0; i < ansi.length; i++) {
                    ansi[i].setHex(t.ansi().get(i));
                }
                imagePath = t.backgroundImage();
                imageVisibility.setValue(t.imageVisibility());
                useWallpaper.setSelected(t.desktopWallpaper());
            } else {
                name.setText("");
                imagePath = null;
                useWallpaper.setSelected(false);
            }
        } finally {
            loading = false;
        }
        updateState();
    }

    /** @throws IllegalArgumentException nama kosong */
    private CustomTheme collect() {
        if (name.getText().isBlank()) {
            throw new IllegalArgumentException(I18n.t("theme.editor.nameEmpty"));
        }
        return new CustomTheme(selected.id(), name.getText(), ((BaseChoice) base.getSelectedItem()).id(),
                new UiPalette(accent.hex(), background.hex(), foreground.hex(), selection.hex(), border.hex()),
                collectTerminal());
    }

    private TerminalPalette collectTerminal() {
        var colors = new ArrayList<String>();
        for (ColorField f : ansi) {
            colors.add(f.hex());
        }
        return new TerminalPalette(termBackground.hex(), termForeground.hex(), termSelection.hex(), colors,
                imagePath, imageVisibility.getValue(), useWallpaper.isSelected());
    }

    private void edited() {
        if (loading || selected == null) {
            return;
        }
        dirty = true;
        updateState();
    }

    /** Segarkan pratinjau, peringatan kontras, dan status tombol sesuai isi form. */
    private void updateState() {
        boolean has = selected != null;
        duplicate.setEnabled(has);
        delete.setEnabled(has);
        export.setEnabled(has);
        save.setEnabled(has);
        saveApply.setEnabled(has);
        if (!has) {
            warning.setText("<html>" + I18n.t("theme.editor.empty") + "</html>");
            return;
        }
        var palette = collectTerminal();
        boolean wallpaper = palette.desktopWallpaper();
        imageName.setText(wallpaper ? I18n.t("theme.editor.term.image.wallpaperActive")
                : imagePath == null ? I18n.t("theme.editor.term.image.none") : new File(imagePath).getName());
        imageName.setCaretPosition(0); // nama panjang ditampilkan dari awal, bukan dari ujung
        imageName.setToolTipText(wallpaper ? null : imagePath);
        chooseImage.setEnabled(!wallpaper);
        clearImage.setEnabled(!wallpaper && imagePath != null);
        imageVisibility.setEnabled(palette.hasBackdrop());
        preview.setPalette(palette);
        refreshBackdrop(palette);
        warning.setText(contrastWarning(palette, previewMissing ? missingImageMessage(palette) : null));
    }

    private static String missingImageMessage(TerminalPalette palette) {
        return palette.desktopWallpaper() ? I18n.t("theme.editor.term.image.wallpaperMissing")
                : I18n.t("theme.editor.term.image.missing", escapeHtml(new File(palette.backgroundImage()).getName()));
    }

    /** Muat gambar untuk pratinjau di thread lain (file bisa besar); hasil usang dibuang. */
    private void refreshBackdrop(TerminalPalette palette) {
        String key = BackgroundImages.key(palette);
        if (!Objects.equals(key, previewKey)) {
            previewKey = key;
            previewImage = null;
            previewMissing = false;
            if (key != null) {
                new SwingWorker<BufferedImage, Void>() {
                    @Override
                    protected BufferedImage doInBackground() {
                        return BackgroundImages.loadFor(palette);
                    }

                    @Override
                    protected void done() {
                        if (!Objects.equals(key, previewKey)) {
                            return; // user sudah memilih gambar lain
                        }
                        try {
                            previewImage = get();
                        } catch (Exception e) {
                            previewImage = null;
                        }
                        previewMissing = previewImage == null;
                        updateState();
                    }
                }.execute();
            }
        }
        preview.setBackdrop(previewImage, palette.imageVisibility());
    }

    /**
     * Pemilih gambar bawaan OS ({@link FileDialog}): di Windows tampil seperti Explorer, lengkap dengan thumbnail
     * gambar, tidak seperti {@code JFileChooser} milik Swing yang hanya berisi daftar nama file.
     */
    private void pickImage() {
        var dialog = new FileDialog(this, I18n.t("theme.editor.term.image"), FileDialog.LOAD);
        if (imagePath != null && new File(imagePath).getParentFile() != null) {
            dialog.setDirectory(new File(imagePath).getParentFile().getPath());
        }
        // Windows mengabaikan FilenameFilter; di sana pola di kolom nama file yang menyaring. OS lain memakai filter.
        dialog.setFilenameFilter((dir, fileName) -> IMAGE_NAME.matcher(fileName).matches());
        dialog.setFile("*.png;*.jpg;*.jpeg;*.gif;*.bmp");
        dialog.setVisible(true); // modal: kembali setelah dialog ditutup
        if (dialog.getFile() != null) {
            imagePath = new File(dialog.getDirectory(), dialog.getFile()).getAbsolutePath();
            edited();
        }
    }

    private static String contrastWarning(TerminalPalette p, String missingImage) {
        var lines = new ArrayList<String>();
        if (missingImage != null) {
            lines.add(I18n.t("theme.editor.term.image.missing", escapeHtml(new File(missingImage).getName())));
        }
        double fg = Contrast.ratio(p.foreground(), p.background());
        if (fg < Contrast.TEXT_MIN) {
            lines.add(I18n.t("theme.editor.warn.fg", String.format("%.1f", fg)));
        }
        var hard = new ArrayList<String>();
        for (int i = 0; i < p.ansi().size(); i++) {
            // hitam (0) dan hitam terang (8) memang sengaja gelap di latar gelap; tidak diperingatkan
            if (i % 8 != 0 && Contrast.ratio(p.ansi().get(i), p.background()) < Contrast.ANSI_MIN) {
                hard.add(I18n.t("theme.ansi." + (i % 8)) + (i >= 8 ? "+" : ""));
            }
        }
        if (!hard.isEmpty()) {
            lines.add(I18n.t("theme.editor.warn.ansi", String.join(", ", hard)));
        }
        return lines.isEmpty() ? " " : "<html>" + String.join("<br>", lines) + "</html>";
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // --- daftar & aksi ---

    private void reloadList(String selectId) {
        loading = true;
        try {
            model.clear();
            store.list().forEach(model::addElement);
            int index = 0;
            for (int i = 0; i < model.size(); i++) {
                if (model.get(i).id().equals(selectId)) {
                    index = i;
                }
            }
            if (!model.isEmpty()) {
                list.setSelectedIndex(index);
            }
        } finally {
            loading = false;
        }
        load(model.isEmpty() ? null : model.get(list.getSelectedIndex()));
    }

    private void onSelectionChanged() {
        var next = list.getSelectedValue();
        if (next == null || (selected != null && next.id().equals(selected.id()))) {
            return;
        }
        if (!confirmLeave()) {
            loading = true;
            try {
                list.setSelectedValue(modelEntry(selected.id()), true); // kembali ke tema yang sedang diedit
            } finally {
                loading = false;
            }
            return;
        }
        // tema yang tidak disimpan dibuang: isi daftar kembali dari versi tersimpan
        load(modelEntry(next.id()));
    }

    private CustomTheme modelEntry(String id) {
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).id().equals(id)) {
                return model.get(i);
            }
        }
        return null;
    }

    /** @return false kalau user membatalkan; true kalau boleh lanjut (tersimpan, dibuang, atau tidak ada perubahan) */
    private boolean confirmLeave() {
        if (!dirty || selected == null) {
            return true;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                I18n.t("theme.editor.dirty.message", name.getText().strip()),
                I18n.t("theme.editor.dirty.title"), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        return switch (answer) {
            case JOptionPane.YES_OPTION -> saveCurrent();
            case JOptionPane.NO_OPTION -> {
                dirty = false;
                yield true;
            }
            default -> false;
        };
    }

    private boolean saveCurrent() {
        if (selected == null) {
            return false;
        }
        try {
            var theme = collect();
            store.save(theme);
            selected = theme;
            dirty = false;
            loading = true;
            try {
                model.setElementAt(theme, list.getSelectedIndex()); // nama bisa berubah
            } finally {
                loading = false;
            }
            return true;
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), I18n.t("theme.editor.saveFailed"),
                    JOptionPane.WARNING_MESSAGE);
        } catch (UncheckedIOException e) {
            Dialogs.error(this, I18n.t("theme.editor.saveFailed"), e);
        }
        return false;
    }

    private Set<String> takenIds() {
        var ids = new HashSet<>(reservedIds);
        for (int i = 0; i < model.size(); i++) {
            ids.add(model.get(i).id());
        }
        return ids;
    }

    /** Simpan {@code theme} sebagai file baru dan pilih di daftar. */
    private void addAndSelect(CustomTheme theme) {
        try {
            store.save(theme);
        } catch (UncheckedIOException e) {
            Dialogs.error(this, I18n.t("theme.editor.saveFailed"), e);
            return;
        }
        reloadList(theme.id());
    }

    private void createNew() {
        if (!confirmLeave()) {
            return;
        }
        String title = I18n.t("theme.editor.newName");
        addAndSelect(ThemeTemplates.dark(ThemeIds.fromName(title, takenIds()), title));
    }

    private void duplicateSelected() {
        if (selected == null) {
            return;
        }
        CustomTheme source;
        try {
            source = collect(); // salin keadaan di layar, termasuk perubahan yang belum disimpan
        } catch (IllegalArgumentException e) {
            source = selected;
        }
        String copyName = I18n.t("theme.editor.copyName", source.name());
        var copy = ThemeTemplates.copyOf(source, ThemeIds.fromName(copyName, takenIds()), copyName);
        dirty = false; // perubahan sudah ikut tersalin; tema asal kembali ke versi tersimpan
        addAndSelect(copy);
    }

    private void deleteSelected() {
        if (selected == null || !Dialogs.confirm(this, I18n.t("theme.editor.delete"),
                I18n.t("theme.editor.delete.confirm", selected.name()))) {
            return;
        }
        try {
            store.delete(selected.id());
        } catch (UncheckedIOException e) {
            Dialogs.error(this, I18n.t("theme.editor.delete"), e);
            return;
        }
        dirty = false;
        reloadList(null);
    }

    private void importTheme() {
        if (!confirmLeave()) {
            return;
        }
        var chooser = new JFileChooser();
        chooser.setDialogTitle(I18n.t("theme.editor.import.title"));
        chooser.setFileFilter(new FileNameExtensionFilter(I18n.t("theme.editor.file.filter"), "json"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            var imported = store.importFrom(chooser.getSelectedFile().toPath());
            // id dari file orang lain bisa bentrok dengan tema sendiri atau bawaan: beri id baru
            String id = ThemeIds.fromName(imported.id(), takenIds());
            addAndSelect(new CustomTheme(id, imported.name(), imported.base(), imported.ui(), imported.terminal()));
        } catch (IOException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, I18n.t("theme.editor.import.failed", e.getMessage()),
                    I18n.t("theme.editor.import.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportTheme() {
        if (selected == null) {
            return;
        }
        CustomTheme theme;
        try {
            theme = collect();
        } catch (IllegalArgumentException e) {
            theme = selected;
        }
        var chooser = new JFileChooser();
        chooser.setDialogTitle(I18n.t("theme.editor.export.title"));
        chooser.setFileFilter(new FileNameExtensionFilter(I18n.t("theme.editor.file.filter"), "json"));
        chooser.setSelectedFile(new File(theme.id() + ".json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
            file = new File(file.getParentFile(), file.getName() + ".json");
        }
        if (file.exists() && !Dialogs.confirm(this, I18n.t("theme.editor.export.title"),
                I18n.t("theme.editor.overwrite", file.getName()))) {
            return;
        }
        try {
            store.exportTo(theme, file.toPath());
        } catch (IOException e) {
            Dialogs.error(this, I18n.t("theme.editor.export.title"), e);
        }
    }

    private void closeDialog() {
        if (confirmLeave()) {
            dispose();
        }
    }
}
