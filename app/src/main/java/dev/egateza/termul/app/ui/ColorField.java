package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.i18n.I18n;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.Pattern;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Satu warna: kotak warna (klik untuk pemilih warna) dan kolom hex {@code #RRGGBB}. Teks yang belum valid tidak
 * mengubah warna; kolomnya ditandai merah sampai benar. Yang opsional boleh kosong (= null, ikut tema dasar).
 */
final class ColorField extends JPanel {

    private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{6})");

    private final boolean optional;
    private final Swatch swatch = new Swatch();
    private final JTextField text = new JTextField(7);
    private String hex; // null hanya kalau optional
    private boolean updating;
    private Runnable onChange = () -> {
    };

    ColorField(String initial, boolean optional) {
        super(new FlowLayout(FlowLayout.LEFT, 4, 0));
        this.optional = optional;
        add(swatch);
        add(text);
        if (optional) {
            text.putClientProperty("JTextField.placeholderText", I18n.t("theme.editor.followBase"));
            text.setColumns(12);
        }
        setHex(initial);
        text.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                typed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                typed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                typed();
            }
        });
        text.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                showValid(); // teks setengah jadi dikembalikan ke warna terakhir yang valid
            }
        });
        swatch.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!isEnabled()) {
                    return;
                }
                Color chosen = JColorChooser.showDialog(ColorField.this, I18n.t("theme.editor.pickColor"),
                        swatch.color());
                if (chosen != null) {
                    apply(String.format("#%02X%02X%02X", chosen.getRed(), chosen.getGreen(), chosen.getBlue()));
                }
            }
        });
    }

    void onChange(Runnable r) {
        this.onChange = r;
    }

    /** @return {@code #RRGGBB} huruf besar; null hanya untuk field opsional yang dikosongkan */
    String hex() {
        return hex;
    }

    /** Ganti warna tanpa memicu {@link #onChange}. */
    void setHex(String value) {
        hex = value;
        showValid();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        text.setEnabled(enabled);
        swatch.setEnabled(enabled);
        swatch.setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private void apply(String value) {
        hex = value;
        showValid();
        onChange.run();
    }

    private void typed() {
        if (updating) {
            return;
        }
        String s = text.getText().strip();
        if (optional && s.isEmpty()) {
            hex = null;
            text.putClientProperty("JComponent.outline", null);
            swatch.repaint();
            onChange.run();
            return;
        }
        var m = HEX.matcher(s);
        if (m.matches()) {
            hex = "#" + m.group(1).toUpperCase(java.util.Locale.ROOT);
            text.putClientProperty("JComponent.outline", null);
            swatch.repaint();
            onChange.run();
        } else {
            text.putClientProperty("JComponent.outline", "error");
        }
    }

    private void showValid() {
        updating = true;
        try {
            text.setText(hex == null ? "" : hex);
            text.putClientProperty("JComponent.outline", null);
        } finally {
            updating = false;
        }
        swatch.repaint();
    }

    private final class Swatch extends JComponent {
        Swatch() {
            setPreferredSize(new Dimension(24, 22));
            setToolTipText(I18n.t("theme.editor.pickColor"));
        }

        /** Warna yang tampil; field kosong tampil sebagai warna panel. */
        Color color() {
            return hex == null ? UIManager.getColor("Panel.background") : Color.decode(hex);
        }

        @Override
        protected void paintComponent(Graphics g) {
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            g2.setColor(UIManager.getColor("Component.borderColor"));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            if (hex == null) {
                g2.drawLine(4, getHeight() - 5, getWidth() - 5, 4); // garis coret: tidak diatur
            }
            if (!isEnabled()) {
                g2.setColor(new Color(128, 128, 128, 90));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            }
            g2.dispose();
        }
    }
}
