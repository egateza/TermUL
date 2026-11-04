package dev.egateza.termul.app.tunnel;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.snippet.SnippetUi;
import dev.egateza.termul.app.ui.ShortcutAction;
import dev.egateza.termul.app.ui.Shortcuts;
import dev.egateza.termul.ssh.tunnel.TunnelManager;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

/**
 * Chip Port forwarding di panel bawah (prototipe {@code .tun-chip}): ikon panah + jumlah tunnel aktif; klik membuka
 * dialog Port forwarding. Jumlah ikut status {@link TunnelManager}. EDT.
 */
public final class TunnelChip extends JButton {

    private final TunnelManager manager;
    private final Runnable changed = () -> SwingUtilities.invokeLater(this::refresh);

    public TunnelChip(TunnelManager manager, Runnable open) {
        this.manager = manager;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setOpaque(false);
        setIconTextGap(5);
        setMargin(new Insets(0, 0, 0, 0));
        setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        setIcon(TunnelUi.arrows(14, () -> active() > 0 ? SnippetUi.fg() : SnippetUi.muted()));
        addActionListener(e -> open.run());
        refresh();
    }

    private int active() {
        return manager.runningCount();
    }

    /** Teks shortcut ikut diperbarui saat shortcut diganti (dipanggil MainFrame). */
    public void refresh() {
        int n = active();
        setText(String.valueOf(n));
        setForeground(n > 0 ? SnippetUi.fg() : SnippetUi.muted());
        String tip = I18n.t("tunnel.chip.tooltip", n, Shortcuts.text(ShortcutAction.TUNNELS));
        setToolTipText(tip);
        getAccessibleContext().setAccessibleName(I18n.t("tunnel.chip.name", n));
        repaint();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        manager.addListener(changed);
        refresh();
    }

    @Override
    public void removeNotify() {
        manager.removeListener(changed);
        super.removeNotify();
    }

    @Override
    public Dimension getPreferredSize() {
        // Dihitung sendiri: FlatLaf memberi tombol lebar minimum (Button.minimumWidth), jauh lebih lebar dari chip
        // prototipe (padding 6px + ikon 14 + gap 5 + angka).
        var in = getInsets();
        int text = getFontMetrics(getFont()).stringWidth(getText());
        return new Dimension(in.left + getIcon().getIconWidth() + getIconTextGap() + text + in.right, 22);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (getModel().isRollover() || isFocusOwner()) {
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(SnippetUi.hover());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            if (isFocusOwner()) {
                g2.setColor(SnippetUi.accent());
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 7, 7);
            }
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
