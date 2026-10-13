package dev.egateza.termul.app.sftp;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.fab.FloatingButton;
import dev.egateza.termul.app.ui.fab.FloatingOverlay;
import dev.egateza.termul.app.ui.fab.FloatingStyle;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.UIManager;

/**
 * Tombol mengambang di atas daftar file SFTP selama ada transfer: badge berisi jumlah transfer berjalan + antre,
 * klik menampilkan daftar dan progres masing-masing (diperbarui langsung selama terbuka). EDT.
 */
final class TransferBadge {

    static final int MAX_TITLE = 42;

    final FloatingButton button = new FloatingButton(AppIcon.SFTP, null, FloatingStyle.Kind.PRIMARY,
            FloatingButton.Size.NORMAL);
    private final TransferQueue queue;
    private final FloatingOverlay overlay;
    private final JPopupMenu popup = new JPopupMenu();
    private final JPanel rows = new JPanel();
    private final Runnable unsubscribe;

    TransferBadge(TransferQueue queue, FloatingOverlay overlay) {
        this.queue = queue;
        this.overlay = overlay;
        button.setVisible(false);
        button.getAccessibleContext().setAccessibleName(I18n.t("fab.transfers.title"));
        button.addActionListener(e -> togglePopup());
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        var title = new JLabel(I18n.t("fab.transfers.title"));
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD));
        title.setBorder(BorderFactory.createEmptyBorder(8, 10, 0, 10));
        popup.setLayout(new BorderLayout());
        popup.add(title, BorderLayout.NORTH);
        popup.add(rows, BorderLayout.CENTER);
        popup.setFocusable(false); // ketikan tetap di panel/terminal
        overlay.addFloating(button, FloatingOverlay.Anchor.BOTTOM_RIGHT);
        unsubscribe = queue.addChangeListener(this::refresh);
    }

    private void refresh() {
        List<TransferList.Item> items = queue.transfers();
        boolean any = !items.isEmpty();
        button.setBadge(any ? String.valueOf(items.size()) : null);
        button.setToolTipText(I18n.t("fab.transfers.tooltip", items.size()));
        if (button.isVisible() != any) {
            button.setVisible(any);
            overlay.revalidate();
            overlay.repaint();
        }
        if (!any) {
            popup.setVisible(false);
        } else if (popup.isVisible()) {
            fill(items);
            popup.pack();
        }
    }

    private void togglePopup() {
        if (popup.isVisible()) {
            popup.setVisible(false);
            return;
        }
        fill(queue.transfers());
        Dimension size = popup.getPreferredSize();
        popup.show(button, button.getWidth() - size.width, -size.height - 6);
    }

    private void fill(List<TransferList.Item> items) {
        rows.removeAll();
        for (var item : items) {
            var name = new JLabel(shorten(item.title()));
            name.setToolTipText(item.title());
            var status = new JLabel(statusText(item));
            status.setForeground(UIManager.getColor("Label.disabledForeground"));
            var head = new JPanel(new BorderLayout(12, 0));
            head.setOpaque(false);
            head.add(name, BorderLayout.CENTER);
            head.add(status, BorderLayout.EAST);
            var bar = new JProgressBar(0, 100);
            int pct = item.percent();
            bar.setIndeterminate(item.running() && pct < 0);
            bar.setValue(Math.max(0, pct));
            bar.setPreferredSize(new Dimension(280, 6));
            var row = new JPanel(new BorderLayout(0, 4));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
            row.add(head, BorderLayout.NORTH);
            row.add(bar, BorderLayout.CENTER);
            rows.add(row);
        }
        rows.revalidate();
        rows.repaint();
    }

    static String statusText(TransferList.Item item) {
        if (!item.running()) {
            return I18n.t("fab.transfers.queued");
        }
        int pct = item.percent();
        return pct < 0 ? Formats.size(item.done()) : pct + "%";
    }

    static String shorten(String title) {
        return title.length() <= MAX_TITLE ? title : title.substring(0, MAX_TITLE - 1) + "…";
    }

    void dispose() {
        unsubscribe.run();
        popup.setVisible(false);
    }
}
