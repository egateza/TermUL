package dev.egateza.termul.app.terminal;

import com.jediterm.terminal.model.StyleState;
import dev.egateza.termul.app.ui.ShakeEffect;
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

    private static final class ZoomPanel extends TerminalPanel {
        private final BellSettings bell; // null kalau settings bukan TerminalSettings: getar tetap aktif

        ZoomPanel(SettingsProvider settings, TerminalTextBuffer buffer, StyleState styleState) {
            super(settings, buffer, styleState);
            this.bell = settings instanceof TerminalSettings ts ? ts.bell() : null;
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
