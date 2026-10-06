package dev.egateza.termul.app.terminal;

import com.formdev.flatlaf.ui.FlatScrollBarUI;
import com.jediterm.terminal.SubstringFinder;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JScrollBar;

/**
 * Scrollbar terminal dengan gaya FlatLaf yang sama dengan panel lain (thumb membulat, tanpa tombol panah). JediTerm
 * memakai {@code BasicScrollBarUI} polos supaya bisa menggambar penanda hasil cari di track; penanda itu dipertahankan
 * di sini. UI dipasang ulang di {@link #updateUI()}, jadi tetap berlaku setelah tema diganti.
 */
final class TerminalScrollBar extends JScrollBar {

    private Supplier<SubstringFinder.FindResult> findResult = () -> null; // null saat constructor JScrollBar jalan
    private Supplier<Color> markerColor = () -> null;
    private Supplier<Color> trackColor = () -> null;

    /**
     * @param findResult  hasil cari yang sedang tampil di terminal, null kalau tidak ada
     * @param markerColor warna penanda hasil cari (warna latar teks yang cocok), null = warna aksen tema
     * @param trackColor  latar track = latar terminal, supaya scrollbar menyatu dengan terminal; null = warna tema
     */
    TerminalScrollBar(Supplier<SubstringFinder.FindResult> findResult, Supplier<Color> markerColor,
                      Supplier<Color> trackColor) {
        this.findResult = findResult;
        this.markerColor = markerColor;
        this.trackColor = trackColor;
    }

    private Color track() {
        return trackColor == null ? null : trackColor.get();
    }

    @Override
    public void updateUI() {
        setUI(new MarkerUI());
    }

    private final class MarkerUI extends FlatScrollBarUI {
        @Override
        public void update(Graphics g, JComponent c) {
            Color bg = track();
            if (bg == null) {
                super.update(g, c);
                return;
            }
            g.setColor(bg);
            g.fillRect(0, 0, c.getWidth(), c.getHeight());
            paint(g, c);
        }

        @Override
        protected Color getTrackColor(JComponent c, boolean hover, boolean pressed) {
            Color bg = track();
            // tanpa hover/tekan: track tidak digambar terpisah (latar terminal dari update); saat hover tetap seperti tema
            return bg != null && !hover && !pressed ? bg : super.getTrackColor(c, hover, pressed);
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle track) {
            super.paintTrack(g, c, track);
            var result = findResult == null ? null : findResult.get();
            int range = scrollbar.getModel().getMaximum() - scrollbar.getModel().getMinimum();
            if (result == null || result.getItems().isEmpty() || range <= 0 || track.height <= 0) {
                return;
            }
            Color color = markerColor == null ? null : markerColor.get();
            if (color == null) {
                color = javax.swing.UIManager.getColor("Component.accentColor");
            }
            var g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color != null ? color : Color.ORANGE);
                int height = Math.max(2, track.height / range);
                int inset = Math.max(1, track.width / 4);
                for (var item : result.getItems()) {
                    // sama dengan JediTerm: y hasil cari dihitung dari baris pertama history
                    int y = track.y + (int) ((long) track.height * item.getStart().y / range);
                    g2.fillRoundRect(track.x + inset, y, track.width - 2 * inset, height, 2, 2);
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
