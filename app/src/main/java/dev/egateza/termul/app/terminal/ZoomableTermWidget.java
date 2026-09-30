package dev.egateza.termul.app.terminal;

import com.jediterm.terminal.model.StyleState;
import dev.egateza.termul.app.ui.ShakeEffect;
import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.TerminalPanel;
import com.jediterm.terminal.ui.settings.SettingsProvider;

/**
 * {@link JediTermWidget} yang font-nya bisa diganti saat berjalan (zoom). JediTerm 3.x tidak punya
 * API publik untuk ini, jadi {@code reinitFontAndResize()} (protected) dibuka lewat subclass panel.
 */
public final class ZoomableTermWidget extends JediTermWidget {

    public ZoomableTermWidget(SettingsProvider settings) {
        super(settings);
    }

    @Override
    protected TerminalPanel createTerminalPanel(SettingsProvider settings, StyleState styleState,
                                                TerminalTextBuffer buffer) {
        return new ZoomPanel(settings, buffer, styleState);
    }

    /** Membaca ulang font dari settings dan menyesuaikan ukuran grid (PTY ikut di-resize). Panggil di EDT. */
    public void refreshFont() {
        ((ZoomPanel) getTerminalPanel()).refreshFont();
    }

    /** Membaca ulang warna (tema) dari settings dan menggambar ulang seluruh terminal. Panggil di EDT. */
    public void refreshColors() {
        ((ZoomPanel) getTerminalPanel()).refreshColors();
    }

    private static final class ZoomPanel extends TerminalPanel {
        private final BellSettings bell; // null kalau settings bukan TerminalSettings: getar tetap aktif
        private final SettingsProvider settings;
        private final StyleState styleState;

        ZoomPanel(SettingsProvider settings, TerminalTextBuffer buffer, StyleState styleState) {
            super(settings, buffer, styleState);
            this.bell = settings instanceof TerminalSettings ts ? ts.bell() : null;
            this.settings = settings;
            this.styleState = styleState;
        }

        private BufferedImage scaled; // gambar latar yang sudah disesuaikan dengan ukuran panel; EDT
        private BufferedImage scaledSource;

        void refreshColors() {
            styleState.setDefaultStyle(settings.getDefaultStyle());
            repaint();
        }

        /**
         * Tanpa gambar latar: gambar seperti biasa. Dengan gambar latar: warna dasar dan gambar digambar dulu, lalu
         * JediTerm menggambar teks lewat {@link FillFilterGraphics2D} yang membuang isian latar default-nya.
         */
        @Override
        public void paintComponent(Graphics g) {
            var layer = settings instanceof TerminalSettings ts ? ts.backgroundLayer() : null;
            if (layer == null || getWidth() <= 0 || getHeight() <= 0) {
                super.paintComponent(g);
                return;
            }
            var g2 = (Graphics2D) g;
            g2.setColor(layer.base());
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (scaled == null || scaledSource != layer.image()
                    || scaled.getWidth() != getWidth() || scaled.getHeight() != getHeight()) {
                scaled = BackgroundImages.cover(layer.image(), getWidth(), getHeight());
                scaledSource = layer.image();
            }
            var previous = g2.getComposite();
            g2.setComposite(AlphaComposite.SrcOver.derive(layer.visibility() / 100f));
            g2.drawImage(scaled, 0, 0, null);
            g2.setComposite(previous);
            super.paintComponent(new FillFilterGraphics2D(g2, layer.base()));
        }

        void refreshFont() {
            reinitFontAndResize();
        }

        /** Dipanggil dari thread emulator saat server mengirim BEL: bunyi sistem dan/atau layar bergetar sesuai {@link BellSettings}. */
        @Override
        public void beep() {
            super.beep(); // bunyi hanya kalau audibleBell() aktif
            if (bell == null || bell.shake()) {
                SwingUtilities.invokeLater(() -> ShakeEffect.shake(this));
            }
        }
    }
}
