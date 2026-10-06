package dev.egateza.termul.app.terminal;

import com.jediterm.terminal.SubstringFinder;
import com.jediterm.terminal.ui.JediTermSearchComponent;
import com.jediterm.terminal.ui.JediTermSearchComponentListener;
import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.AppIcon;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Kotak cari di terminal, pengganti komponen bawaan JediTerm: kartu membulat di pojok kanan atas berisi field cari,
 * tombol "Aa" (cocokkan huruf besar/kecil), jumlah hasil, sebelumnya/berikutnya, dan tutup. Sengaja minimalis: tanpa
 * replace, regex, atau riwayat. Enter = berikutnya, Shift+Enter = sebelumnya, Esc = tutup. Semua method di EDT.
 */
final class TerminalSearchBar extends JPanel implements JediTermSearchComponent {

    private static final int ARC = 12;

    private final JTextField field = new JTextField(18);
    private final JToggleButton matchCase = new JToggleButton("Aa");
    private final JLabel count = new JLabel();
    private final List<JediTermSearchComponentListener> listeners = new CopyOnWriteArrayList<>();
    private final List<KeyListener> keyListeners = new CopyOnWriteArrayList<>();

    TerminalSearchBar() {
        super(new BorderLayout());
        setOpaque(false); // hanya kartu di dalamnya yang digambar; jarak dari tepi terminal transparan
        setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 8));

        field.putClientProperty("JTextField.placeholderText", I18n.t("terminal.search.placeholder"));
        field.putClientProperty("FlatLaf.style", "arc: 8");
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                settingsChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                settingsChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                settingsChanged();
            }
        });
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isShiftDown()) {
                    e.consume();
                    listeners.forEach(JediTermSearchComponentListener::selectPrevFindResult);
                    return;
                }
                // Esc (tutup), Enter/↓ (berikutnya), ↑ (sebelumnya) ditangani JediTerm
                keyListeners.forEach(l -> l.keyPressed(e));
            }
        });

        matchCase.setToolTipText(I18n.t("terminal.search.matchCase"));
        matchCase.addItemListener(e -> settingsChanged());
        var prev = iconButton(new Chevron(true), I18n.t("terminal.search.prev"));
        prev.addActionListener(e -> listeners.forEach(JediTermSearchComponentListener::selectPrevFindResult));
        var next = iconButton(new Chevron(false), I18n.t("terminal.search.next"));
        next.addActionListener(e -> listeners.forEach(JediTermSearchComponentListener::selectNextFindResult));
        var close = iconButton(AppIcon.XMARK.icon(), I18n.t("terminal.search.close"));
        close.addActionListener(e -> listeners.forEach(JediTermSearchComponentListener::hideSearchComponent));
        for (AbstractButton b : new AbstractButton[] {matchCase, prev, next, close}) {
            b.setFocusable(false);
            b.putClientProperty("JButton.buttonType", "toolBarButton");
        }
        count.setForeground(UIManager.getColor("Label.disabledForeground"));
        count.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        updateCount(null);

        var card = new Card();
        card.add(field);
        card.add(matchCase);
        card.add(count);
        card.add(prev);
        card.add(next);
        card.add(close);
        add(card, BorderLayout.CENTER);
    }

    private static JButton iconButton(Icon icon, String tooltip) {
        var b = new JButton(icon);
        b.setToolTipText(tooltip);
        return b;
    }

    private void settingsChanged() {
        String text = field.getText();
        boolean ignoreCase = !matchCase.isSelected();
        listeners.forEach(l -> l.searchSettingsChanged(text, ignoreCase));
        if (text.isEmpty()) {
            updateCount(null);
        }
    }

    private void updateCount(SubstringFinder.FindResult result) {
        if (field.getText().isEmpty() || result == null) {
            count.setText(" ");
        } else if (result.getItems().isEmpty()) {
            count.setText(I18n.t("terminal.search.none"));
        } else {
            count.setText(I18n.t("terminal.search.count", String.valueOf(result.selectedItem().getIndex()),
                    String.valueOf(result.getItems().size())));
        }
    }

    /** Teks hasil (untuk test). */
    String countText() {
        return count.getText();
    }

    JTextField field() {
        return field;
    }

    @Override
    public void onResultUpdated(SubstringFinder.FindResult results) {
        updateCount(results);
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    @Override
    public void addListener(JediTermSearchComponentListener listener) {
        listeners.add(listener);
    }

    @Override
    public void addKeyListener(KeyListener listener) {
        keyListeners.add(listener); // diteruskan dari field (lihat constructor)
    }

    @Override
    public void requestFocus() {
        field.requestFocusInWindow();
        field.selectAll();
    }

    /** Kartu membulat (latar panel + garis tepi tipis) yang berisi kontrol. */
    private static final class Card extends JPanel {
        Card() {
            super(new FlowLayout(FlowLayout.LEADING, 2, 0));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 4));
        }

        @Override
        protected void paintComponent(Graphics g) {
            var g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UIManager.getColor("Panel.background"));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
                Color border = UIManager.getColor("Component.borderColor");
                g2.setColor(border != null ? border : Color.GRAY);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }
    }

    /** Panah tipis atas/bawah dengan warna teks tema (set ikon aplikasi hanya punya panah atas). */
    private record Chevron(boolean up) implements Icon {
        private static final int SIZE = 14;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            var g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c.isEnabled() ? UIManager.getColor("Label.foreground")
                        : UIManager.getColor("Label.disabledForeground"));
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                double cx = x + SIZE / 2.0;
                double top = y + 4;
                double bottom = y + SIZE - 4;
                var path = new Path2D.Double();
                if (up) {
                    path.moveTo(cx - 4.5, bottom - 1);
                    path.lineTo(cx, top);
                    path.lineTo(cx + 4.5, bottom - 1);
                } else {
                    path.moveTo(cx - 4.5, top + 1);
                    path.lineTo(cx, bottom);
                    path.lineTo(cx + 4.5, top + 1);
                }
                g2.draw(path);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}
