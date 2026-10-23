package dev.egateza.termul.app.terminal;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.fab.FloatingButton;
import dev.egateza.termul.app.ui.fab.FloatingStyle;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Lapisan merah transparan di atas terminal profil sensitif yang sedang terkunci ({@link SensitiveLock}): nama host
 * besar di tengah dan cara membukanya. Klik di lapisan ditelan (tidak membuka kunci, supaya klik untuk memfokuskan
 * terminal tidak sekaligus melewati peringatan); buka lewat Enter atau tombol. Tidak bisa difokus, jadi key tetap sampai
 * ke terminal dan ditahan {@code TerminalTab#interceptKey}. EDT.
 */
final class LockCover extends JPanel {

    static final Color TINT = new Color(0xE5, 0x48, 0x4D, 70);
    private static final Color CARD = new Color(0x2A, 0x10, 0x12, 235);

    private final JLabel host = new JLabel();
    private final JLabel address = new JLabel();

    LockCover(Runnable unlock) {
        super(new GridBagLayout());
        setOpaque(false);
        var swallow = new MouseAdapter() { };
        addMouseListener(swallow);
        addMouseMotionListener(swallow);

        var card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(CARD);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));

        var title = label(I18n.t("tab.lock.title"), Font.BOLD, 1f, new Color(0xFF8A8D));
        host.setFont(host.getFont().deriveFont(Font.BOLD, host.getFont().getSize2D() * 1.8f));
        host.setForeground(Color.WHITE);
        address.setForeground(new Color(0xE0E0E0));
        var hint = label(I18n.t("tab.lock.hint"), Font.PLAIN, 1f, new Color(0xE0E0E0));
        var open = new FloatingButton(AppIcon.PERMISSION, I18n.t("tab.lock.unlock"), FloatingStyle.Kind.DANGER,
                FloatingButton.Size.MINI);
        open.setFocusable(false); // fokus tetap di terminal
        open.addActionListener(e -> unlock.run());

        for (JComponent c : new JComponent[] {title, host, address, hint, open}) {
            c.setAlignmentX(CENTER_ALIGNMENT);
        }
        card.add(title);
        card.add(gap(6));
        card.add(host);
        card.add(address);
        card.add(gap(12));
        card.add(hint);
        card.add(gap(10));
        card.add(open);
        var c = new GridBagConstraints();
        c.insets = new Insets(16, 16, 16, 16);
        add(card, c);
    }

    /** Teks yang tampil: nama profil dan {@code user@host}. */
    void show(String name, String userAtHost) {
        host.setText(name);
        address.setText(userAtHost);
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(TINT);
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    private static JLabel label(String text, int style, float scale, Color color) {
        var l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(style, l.getFont().getSize2D() * scale));
        l.setForeground(color);
        return l;
    }

    private static JComponent gap(int height) {
        var g = new JPanel();
        g.setOpaque(false);
        g.setMaximumSize(new java.awt.Dimension(1, height));
        g.setPreferredSize(new java.awt.Dimension(1, height));
        return g;
    }
}
