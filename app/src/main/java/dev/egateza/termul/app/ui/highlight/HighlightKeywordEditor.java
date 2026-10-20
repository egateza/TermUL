package dev.egateza.termul.app.ui.highlight;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.highlight.HighlightKeyword;
import dev.egateza.termul.core.highlight.HighlightMatcher;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Editor satu keyword (ikon pensil atau double-click di tabel): teks/regex, warna latar dan teks, gaya, catatan, dan baris
 * Uji yang dirender seperti di terminal. Regex atau grup yang tidak valid menahan tombol OK. Keyword JavaScript dari Tabby
 * hanya bisa dilihat.
 */
final class HighlightKeywordEditor extends JDialog {

    /** Contoh baris log untuk kolom Uji kalau keyword berupa regex. */
    static final String SAMPLE = "2026-10-20 10:15:02 ERROR [main] koneksi gagal: user=ega id=42";

    private final HighlightKeyword original;
    private final JTextField text = new JTextField(32);
    private final JCheckBox regex = new JCheckBox(I18n.t("highlight.keyword.regex"));
    private final JCheckBox caseSensitive = new JCheckBox(I18n.t("highlight.keyword.case"));
    private final JCheckBox matchGroup = new JCheckBox(I18n.t("highlight.keyword.matchGroup"));
    private final JTextField group = new JTextField(6);
    private final JCheckBox background = new JCheckBox(I18n.t("highlight.keyword.background"));
    private final HighlightColorField backgroundColor;
    private final JCheckBox foreground = new JCheckBox(I18n.t("highlight.keyword.foreground"));
    private final HighlightColorField foregroundColor;
    private final JCheckBox bold = new JCheckBox(I18n.t("highlight.keyword.bold"));
    private final JCheckBox italic = new JCheckBox(I18n.t("highlight.keyword.italic"));
    private final JCheckBox underline = new JCheckBox(I18n.t("highlight.keyword.underline"));
    private final JCheckBox dim = new JCheckBox(I18n.t("highlight.keyword.dim"));
    private final JCheckBox inverse = new JCheckBox(I18n.t("highlight.keyword.inverse"));
    private final JCheckBox invisible = new JCheckBox(I18n.t("highlight.keyword.invisible"));
    private final JTextField remark = new JTextField(32);
    private final JTextField sample = new JTextField(32);
    private final HighlightPreview preview;
    private final JLabel error = new JLabel(" ");
    private final JButton ok = new JButton(I18n.t("highlight.ok"));
    private HighlightKeyword result;

    private HighlightKeywordEditor(Window owner, HighlightKeyword keyword, HighlightPreview.Look look) {
        super(owner, I18n.t("highlight.keyword.title"), ModalityType.APPLICATION_MODAL);
        this.original = keyword;
        this.backgroundColor = new HighlightColorField(keyword.backgroundColor() == null ? "1"
                : keyword.backgroundColor(), look);
        this.foregroundColor = new HighlightColorField(keyword.foregroundColor() == null ? "0"
                : keyword.foregroundColor(), look);
        this.preview = new HighlightPreview(look);

        var mono = new Font(Font.MONOSPACED, Font.PLAIN, text.getFont().getSize());
        text.setFont(mono);
        sample.setFont(mono);
        group.setFont(mono);
        error.setForeground(UIManager.getColor("Component.error.focusedBorderColor"));

        text.setText(keyword.text());
        regex.setSelected(keyword.regExp());
        caseSensitive.setSelected(keyword.caseSensitive());
        matchGroup.setSelected(keyword.matchGroup());
        group.setText(keyword.matchGroupName() == null ? "1" : keyword.matchGroupName());
        background.setSelected(keyword.background());
        foreground.setSelected(keyword.foreground());
        bold.setSelected(keyword.bold());
        italic.setSelected(keyword.italic());
        underline.setSelected(keyword.underline());
        dim.setSelected(keyword.dim());
        inverse.setSelected(keyword.inverse());
        invisible.setSelected(keyword.invisible());
        remark.setText(keyword.remark() == null ? "" : keyword.remark());
        sample.setText(keyword.regExp() || keyword.text().isEmpty() ? SAMPLE : "… " + keyword.text() + " …");

        var form = new JPanel(new GridBagLayout());
        var gc = new GridBagConstraints();
        gc.insets = new Insets(3, 4, 3, 4);
        gc.anchor = GridBagConstraints.WEST;
        int row = 0;
        row(form, gc, row++, I18n.t("highlight.keyword.text"), text);
        row(form, gc, row++, "", flow(regex, caseSensitive));
        row(form, gc, row++, "", flow(matchGroup, group));
        row(form, gc, row++, I18n.t("highlight.keyword.backgroundLabel"), flow(background, backgroundColor));
        row(form, gc, row++, I18n.t("highlight.keyword.foregroundLabel"), flow(foreground, foregroundColor));
        row(form, gc, row++, I18n.t("highlight.keyword.style"), flow(bold, italic, underline));
        row(form, gc, row++, "", flow(dim, inverse, invisible));
        row(form, gc, row++, I18n.t("highlight.keyword.remark"), remark);
        row(form, gc, row++, I18n.t("highlight.keyword.sample"), sample);
        row(form, gc, row++, I18n.t("highlight.keyword.preview"), preview);
        gc.gridx = 1;
        gc.gridy = row;
        gc.fill = GridBagConstraints.HORIZONTAL;
        form.add(error, gc);

        var cancel = new JButton(I18n.t("highlight.cancel"));
        cancel.addActionListener(e -> dispose());
        ok.addActionListener(e -> accept());
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(cancel);
        buttons.add(ok);
        var content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        DocumentListener refresh = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refresh();
            }
        };
        for (JTextField f : List.of(text, group, sample)) {
            f.getDocument().addDocumentListener(refresh);
        }
        for (JCheckBox b : List.of(regex, caseSensitive, matchGroup, background, foreground, bold, italic, underline,
                dim, inverse, invisible)) {
            b.addActionListener(e -> refresh());
        }
        backgroundColor.setOnChange(this::refresh);
        foregroundColor.setOnChange(this::refresh);
        if (keyword.js()) {
            for (Component c : List.of(text, regex, caseSensitive, matchGroup, group, background, backgroundColor,
                    foreground, foregroundColor, bold, italic, underline, dim, inverse, invisible, remark)) {
                c.setEnabled(false);
            }
        }
        refresh();
        pack();
    }

    /**
     * @param anchor titik layar tempat editor muncul (di bawah baris tabel); null = tengah owner
     * @return keyword yang sudah diubah, atau kosong kalau dibatalkan
     */
    static Optional<HighlightKeyword> edit(Window owner, HighlightKeyword keyword, HighlightPreview.Look look,
                                           Point anchor) {
        var dialog = new HighlightKeywordEditor(owner, keyword, look);
        if (anchor == null) {
            dialog.setLocationRelativeTo(owner);
        } else {
            dialog.setLocation(anchor);
        }
        dialog.text.requestFocusInWindow();
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    private HighlightKeyword current() {
        return new HighlightKeyword(text.getText(), original.enabled(), original.js(), regex.isSelected(),
                caseSensitive.isSelected(), foreground.isSelected(), foregroundColor.value(), background.isSelected(),
                backgroundColor.value(), bold.isSelected(), italic.isSelected(), underline.isSelected(),
                original.underlineStyle(), dim.isSelected(), inverse.isSelected(), invisible.isSelected(),
                regex.isSelected() && matchGroup.isSelected(), group.getText().strip(),
                remark.getText().isBlank() ? null : remark.getText().strip());
    }

    private void refresh() {
        boolean editable = !original.js();
        matchGroup.setEnabled(editable && regex.isSelected());
        group.setEnabled(editable && regex.isSelected() && matchGroup.isSelected());
        backgroundColor.setEnabled(editable && background.isSelected());
        foregroundColor.setEnabled(editable && foreground.isSelected());
        String problem = problem();
        error.setText(problem == null ? " " : problem);
        ok.setEnabled(problem == null || original.js());
        var k = current();
        preview.show(sample.getText(), problem == null ? k : k.withRegExp(false));
    }

    /** @return pesan kesalahan, atau null kalau keyword bisa disimpan */
    private String problem() {
        if (original.js()) {
            return I18n.t("highlight.keyword.jsReadOnly");
        }
        if (text.getText().isEmpty()) {
            return I18n.t("highlight.keyword.empty");
        }
        if (background.isSelected() && !backgroundColor.valid() || foreground.isSelected() && !foregroundColor.valid()) {
            return I18n.t("highlight.keyword.badColor");
        }
        return HighlightMatcher.validate(current()).map(e -> I18n.t("highlight.keyword.badRegex", e)).orElse(null);
    }

    private void accept() {
        if (original.js()) {
            dispose();
            return;
        }
        if (problem() == null) {
            result = current();
            dispose();
        }
    }

    private static JPanel flow(Component... items) {
        var p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        for (Component c : items) {
            p.add(c);
        }
        return p;
    }

    private static void row(JPanel form, GridBagConstraints gc, int row, String label, Component field) {
        gc.gridx = 0;
        gc.gridy = row;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        form.add(new JLabel(label), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, gc);
    }
}
