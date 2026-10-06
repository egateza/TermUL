package dev.egateza.termul.app.terminal;

import com.jediterm.core.TerminalCoordinates;
import com.jediterm.terminal.TerminalCopyPasteHandler;
import com.jediterm.terminal.model.StyleState;
import dev.egateza.termul.app.ui.ShakeEffect;
import com.jediterm.terminal.TextStyle;
import java.awt.AlphaComposite;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.UnaryOperator;
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

    /**
     * Filter untuk semua paste (shortcut, menu klik kanan, klik tengah). Dipanggil di EDT dengan teks clipboard;
     * kembalikan teks yang dikirim, atau null untuk membatalkan.
     */
    public void setPasteFilter(UnaryOperator<String> filter) {
        ((ZoomPanel) getTerminalPanel()).pasteFilter = filter;
    }

    /** Ukuran satu sel karakter, px (untuk test koordinat mouse). */
    java.awt.Dimension charSize() {
        return ((ZoomPanel) getTerminalPanel()).charSize();
    }

    private static final class ZoomPanel extends TerminalPanel {
        // tanpa initializer: createCopyPasteHandler() dipanggil dari constructor TerminalPanel
        private volatile UnaryOperator<String> pasteFilter;

        @Override
        protected TerminalCopyPasteHandler createCopyPasteHandler() {
            var delegate = super.createCopyPasteHandler();
            return new TerminalCopyPasteHandler() {
                @Override
                public void setContents(String text, boolean useSystemSelectionClipboardIfAvailable) {
                    delegate.setContents(text, useSystemSelectionClipboardIfAvailable);
                }

                @Override
                public String getContents(boolean useSystemSelectionClipboardIfAvailable) {
                    String text = delegate.getContents(useSystemSelectionClipboardIfAvailable);
                    var filter = pasteFilter;
                    return text == null || filter == null ? text : filter.apply(text);
                }
            };
        }

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

        java.awt.Dimension charSize() {
            return new java.awt.Dimension(myCharSize);
        }

        void refreshFont() {
            reinitFontAndResize();
        }

        /** Karakter yang tidak ada di font terminal (✔, spinner Braille docker) digambar dengan font fallback. */
        @Override
        protected Font getFontToDisplay(char[] text, int start, int end, TextStyle style) {
            return GlyphFallback.system().fontFor(super.getFontToDisplay(text, start, end, style), text, start, end);
        }

        private volatile TerminalCoordinates coords;

        @Override
        public void setCoordAccessor(TerminalCoordinates coords) {
            super.setCoordAccessor(coords);
            this.coords = coords;
        }

        /**
         * Bug JediTerm 3.76: "Clear Buffer" dengan baris terakhir dipertahankan memasang cursor Y terminal ke 0,
         * padahal 1-based. Akibatnya akses baris cursor jadi {@code getLine(-1)} ("Attempt to get line out of
         * bounds: -1 < 0"). Baris yang dipertahankan ada di baris pertama layar, jadi cursor dikembalikan ke Y=1.
         */
        @Override
        protected void clearBuffer(boolean keepLastLine) {
            super.clearBuffer(keepLastLine);
            var c = coords;
            if (c != null && c.getY() < 1) {
                c.setY(1);
            }
        }

        /**
         * JediTerm 3.76 tidak menghapus selection saat user mengetik, jadi blok tetap tersorot padahal teks di
         * bawahnya sudah berubah. Setiap karakter yang diketik (KEY_TYPED, termasuk Enter/Backspace) menghapus
         * selection, kecuali kombinasi Cmd (shortcut macOS seperti Cmd+C tetap mempertahankan blok).
         */
        @Override
        public void processKeyEvent(KeyEvent e) {
            super.processKeyEvent(e);
            if (e.getID() == KeyEvent.KEY_TYPED && !e.isMetaDown() && getSelection() != null) {
                scrollArea(0, 0, 0); // satu-satunya jalur publik ke updateSelection(null); dy=0 tidak menggeser apa pun
                repaint();
            }
        }

        /**
         * Shift+klik kiri memperluas selection sampai titik klik, dengan titik awal tetap: awal selection yang ada,
         * atau titik klik biasa terakhir kalau belum ada selection. Caranya, klik diteruskan ke JediTerm sebagai drag
         * tanpa Shift, karena drag JediTerm memang memperpanjang selection dari titik awal itu.
         */
        @Override
        protected void processMouseEvent(MouseEvent e) {
            if (e.getID() == MouseEvent.MOUSE_PRESSED && e.getClickCount() == 1 && extendsSelection(e)) {
                requestFocusInWindow();
                super.processMouseMotionEvent(asPlainDrag(e));
                e.consume();
                return;
            }
            super.processMouseEvent(e);
        }

        /** Shift+drag melanjutkan selection; JediTerm 3.76 mengabaikan drag dengan Shift saat mouse reporting mati. */
        @Override
        protected void processMouseMotionEvent(MouseEvent e) {
            if (e.getID() == MouseEvent.MOUSE_DRAGGED && extendsSelection(e)) {
                super.processMouseMotionEvent(asPlainDrag(e));
                return;
            }
            super.processMouseMotionEvent(e);
        }

        /**
         * Hanya saat aplikasi remote tidak memakai mouse reporting. Kalau memakai (htop, vim dengan mouse), Shift tetap
         * berarti "seleksi lokal" bawaan JediTerm.
         */
        private boolean extendsSelection(MouseEvent e) {
            return e.isShiftDown() && SwingUtilities.isLeftMouseButton(e) && !isRemoteMouseAction(asPlainDrag(e));
        }

        private MouseEvent asPlainDrag(MouseEvent e) {
            return new MouseEvent(this, MouseEvent.MOUSE_DRAGGED, e.getWhen(),
                    e.getModifiersEx() & ~InputEvent.SHIFT_DOWN_MASK, e.getX(), e.getY(),
                    e.getXOnScreen(), e.getYOnScreen(), 0, false, MouseEvent.NOBUTTON);
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
