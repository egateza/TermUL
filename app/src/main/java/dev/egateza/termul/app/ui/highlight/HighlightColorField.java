package dev.egateza.termul.app.ui.highlight;

import com.jediterm.terminal.emulator.ColorPalette;
import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.terminal.TextHighlighter;
import dev.egateza.termul.core.highlight.HighlightColor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Kolom warna format Tabby: kotak warna (klik untuk memilih dari 16 warna tema, 256 warna xterm, atau warna bebas) dan
 * teks ({@code 0–255}, {@code #RRGGBB}, atau nama warna CSS). Teks yang tidak valid ditandai merah.
 */
final class HighlightColorField extends JPanel {

    private final HighlightPreview.Look look;
    private final Swatch swatch = new Swatch();
    private final JTextField text = new JTextField(8);
    private Runnable onChange = () -> { };

    HighlightColorField(String initial, HighlightPreview.Look look) {
        super(new FlowLayout(FlowLayout.LEFT, 4, 0));
        this.look = look;
        text.setText(initial == null ? "" : initial);
        text.setToolTipText(I18n.t("highlight.color.hint"));
        add(swatch);
        add(text);
        text.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                changed();
            }
        });
        changed();
    }

    void setOnChange(Runnable r) {
        onChange = r;
    }

    String value() {
        return text.getText().strip();
    }

    boolean valid() {
        return HighlightColor.parse(value()).isPresent();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        text.setEnabled(enabled);
        swatch.setEnabled(enabled);
        swatch.repaint();
    }

    private void changed() {
        text.putClientProperty("JComponent.outline", valid() ? null : "error");
        swatch.repaint();
        onChange.run();
    }

    private Color current() {
        var c = TextHighlighter.Patch.color(value());
        return c == null ? null : look.awt(c, false);
    }

    private void choose() {
        var popup = new JPopupMenu();
        var body = new JPanel(new BorderLayout(0, 6));
        body.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        var ansi = new JPanel(new GridLayout(2, 8, 2, 2));
        for (int i = 0; i < 16; i++) {
            ansi.add(cell(popup, i));
        }
        var cube = new JPanel(new GridLayout(12, 20, 1, 1));
        for (int i = 16; i < 256; i++) {
            cube.add(cell(popup, i));
        }
        var more = new JButton(I18n.t("highlight.color.more"));
        more.addActionListener(e -> {
            popup.setVisible(false);
            Color picked = JColorChooser.showDialog(this, I18n.t("highlight.color.title"), current());
            if (picked != null) {
                text.setText(String.format(Locale.ROOT, "#%06x", picked.getRGB() & 0xFFFFFF));
            }
        });
        var top = new JPanel(new BorderLayout(0, 2));
        top.add(new JLabel(I18n.t("highlight.color.ansi")), BorderLayout.NORTH);
        top.add(ansi, BorderLayout.CENTER);
        var middle = new JPanel(new BorderLayout(0, 2));
        middle.add(new JLabel(I18n.t("highlight.color.xterm")), BorderLayout.NORTH);
        middle.add(cube, BorderLayout.CENTER);
        body.add(top, BorderLayout.NORTH);
        body.add(middle, BorderLayout.CENTER);
        body.add(more, BorderLayout.SOUTH);
        popup.add(body);
        popup.show(swatch, 0, swatch.getHeight());
    }

    private JComponent cell(JPopupMenu popup, int index) {
        Color color = look.awt(ColorPalette.getIndexedTerminalColor(index), false);
        var cell = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(color);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        int size = index < 16 ? 18 : 12;
        cell.setPreferredSize(new Dimension(size, size));
        cell.setToolTipText(Integer.toString(index));
        cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cell.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                popup.setVisible(false);
                text.setText(Integer.toString(index));
            }
        });
        return cell;
    }

    private final class Swatch extends JComponent {
        Swatch() {
            setPreferredSize(new Dimension(22, 22));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(I18n.t("highlight.color.pick"));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (isEnabled()) {
                        choose();
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Color c = current();
            if (c != null) {
                g.setColor(c);
                g.fillRect(1, 1, getWidth() - 2, getHeight() - 2);
            } else {
                g.setColor(UIManager.getColor("Component.error.focusedBorderColor"));
                g.drawLine(2, getHeight() - 3, getWidth() - 3, 2);
            }
            g.setColor(UIManager.getColor("Component.borderColor"));
            g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        }
    }
}
