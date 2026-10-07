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

    /**
     * Scrollbar bergaya FlatLaf seperti panel lain (bawaan JediTerm memakai {@code BasicScrollBarUI} polos), tetap
     * dengan penanda hasil cari. Dipanggil dari constructor JediTermWidget: semua yang dibaca di sini dibaca saat
     * menggambar, bukan sekarang.
     */
    @Override
    protected javax.swing.JScrollBar createScrollBar() {
        return new TerminalScrollBar(() -> getTerminalPanel().getFindResult(), () -> {
            var found = mySettingsProvider.getFoundPatternColor().getBackground();
            return found == null ? null : com.jediterm.terminal.ui.AwtTransformers.toAwtColor(
                    mySettingsProvider.getTerminalColorPalette().getBackground(found));
        }, () -> getTerminalPanel().getBackground());
    }

    /** Kotak cari membulat bergaya aplikasi, pengganti komponen bawaan JediTerm. */
    @Override
    protected com.jediterm.terminal.ui.JediTermSearchComponent createSearchComponent() {
        return new TerminalSearchBar();
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

    /** Ganti clipboard sistem (untuk test, yang bisa berjalan headless). */
    void useClipboard(TerminalCopyPasteHandler clipboard) {
        ((ZoomPanel) getTerminalPanel()).clipboard = clipboard;
    }

    /** Ukuran satu sel karakter, px (untuk test koordinat mouse). */
    java.awt.Dimension charSize() {
        return ((ZoomPanel) getTerminalPanel()).charSize();
    }

    private static final class ZoomPanel extends TerminalPanel {
        // tanpa initializer: createCopyPasteHandler() dipanggil dari constructor TerminalPanel
        private volatile UnaryOperator<String> pasteFilter;
        private volatile TerminalCopyPasteHandler clipboard; // null = clipboard sistem

        @Override
        protected TerminalCopyPasteHandler createCopyPasteHandler() {
            var system = super.createCopyPasteHandler();
            return new TerminalCopyPasteHandler() {
                @Override
                public void setContents(String text, boolean useSystemSelectionClipboardIfAvailable) {
                    delegate().setContents(text, useSystemSelectionClipboardIfAvailable);
                }

                @Override
                public String getContents(boolean useSystemSelectionClipboardIfAvailable) {
                    String text = delegate().getContents(useSystemSelectionClipboardIfAvailable);
                    var filter = pasteFilter;
                    return text == null || filter == null ? text : filter.apply(text);
                }

                private TerminalCopyPasteHandler delegate() {
                    var c = clipboard;
                    return c == null ? system : c;
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

        private SmoothWheel smoothWheel; // EDT; dibuat saat wheel pertama (model scroll baru ada setelah constructor)

        /**
         * Scroll wheel lokal (bukan yang diteruskan ke aplikasi remote, mis. vim/less dengan mouse reporting)
         * dianimasikan seperti panel lain, bukan melompat beberapa baris sekaligus.
         */
        @Override
        protected void handleMouseWheelEvent(java.awt.event.MouseWheelEvent e, javax.swing.JScrollBar scrollBar) {
            if (e.isShiftDown() || Math.abs(e.getPreciseWheelRotation()) < 0.001) {
                return;
            }
            if (smoothWheel == null) {
                smoothWheel = new SmoothWheel(getVerticalScrollModel());
            }
            var model = getVerticalScrollModel();
            // "satu layar per putaran" di pengaturan Windows → satu halaman terminal per putaran
            double perNotch = e.getScrollType() == java.awt.event.MouseWheelEvent.WHEEL_BLOCK_SCROLL
                    ? Math.max(1, model.getExtent()) : e.getScrollAmount();
            smoothWheel.scroll(e.getPreciseWheelRotation() * perNotch);
            e.consume();
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
            if (e.getID() == MouseEvent.MOUSE_PRESSED && e.getButton() == MouseEvent.BUTTON1) {
                pressPoint = e.getPoint();
            }
            if (copyPasteClick(e)) {
                if (e.getID() == MouseEvent.MOUSE_PRESSED) {
                    requestFocusInWindow();
                    copyOrPaste();
                }
                e.consume(); // press, release, dan click: JediTerm membuka menunya di mouseClicked
                return;
            }
            if (e.getID() == MouseEvent.MOUSE_PRESSED && e.getClickCount() == 1 && extendsSelection(e)) {
                requestFocusInWindow();
                if (!extendByWord(e.getPoint())) {
                    super.processMouseMotionEvent(asPlainDrag(e));
                }
                e.consume();
                return;
            }
            boolean leftLocal = e.getButton() == MouseEvent.BUTTON1 && !e.isShiftDown() && isLocalMouseAction(e);
            if (e.getID() == MouseEvent.MOUSE_PRESSED && e.getButton() == MouseEvent.BUTTON1) {
                wordAnchor = null;
                wordDrag = false;
            } else if (e.getID() == MouseEvent.MOUSE_RELEASED && e.getButton() == MouseEvent.BUTTON1) {
                wordDrag = false;
            } else if (e.getID() == MouseEvent.MOUSE_CLICKED && e.getClickCount() == 2 && leftLocal
                    && wordAnchor != null) {
                requestFocusInWindow();
                e.consume(); // kata sudah dipilih saat ditekan; JediTerm akan memilih ulang sampai spasi
                return;
            }
            super.processMouseEvent(e);
            if (e.getID() == MouseEvent.MOUSE_PRESSED && e.getClickCount() == 2 && leftLocal) {
                selectWord(e);
            }
        }

        /** Kata hasil double-click terakhir; drag/Shift+klik memperluas per kata selama selection-nya masih ini. EDT. */
        private record WordAnchor(com.jediterm.terminal.model.TerminalSelection selection, int row, int start, int end) {
        }

        private WordAnchor wordAnchor;
        private boolean wordDrag; // EDT; tombol kiri masih ditahan setelah double-click

        /**
         * Seperti Windows Terminal: double-click langsung memilih satu kata (huruf, angka, {@code _}), jadi pada
         * {@code dashboard.pelangi.co.id-le-ssl.conf} hanya {@code dashboard} yang terpilih; titik, {@code -},
         * {@code /} dan tanda baca lain menjadi pemisah. Dipilih saat tombol ditekan supaya drag sesudahnya (tombol
         * masih ditahan) memperluas per kata. Double-click pada tanda baca/spasi memakai pilihan bawaan JediTerm.
         */
        private void selectWord(MouseEvent e) {
            var p = e.getPoint();
            int row = row(p);
            int[] word = wordAround(row, column(p));
            if (word == null) {
                return;
            }
            if (getSelection() == null) {
                // updateSelection JediTerm private: selection dibuat lewat drag (dari titik klik pertama), lalu diatur
                super.processMouseMotionEvent(new MouseEvent(this, MouseEvent.MOUSE_DRAGGED, e.getWhen(),
                        InputEvent.BUTTON1_DOWN_MASK, e.getX(), e.getY(), e.getXOnScreen(), e.getYOnScreen(), 0, false,
                        MouseEvent.NOBUTTON));
            }
            var selection = getSelection();
            if (selection == null) {
                return;
            }
            selection.getStart().setLocation(word[0], row); // getStart() = titik internal selection
            selection.updateEnd(new com.jediterm.core.compatibility.Point(word[1], row));
            wordAnchor = new WordAnchor(selection, row, word[0], word[1]);
            wordDrag = true;
            repaint();
        }

        /**
         * Shift+klik/drag setelah double-click: selection diperluas per kata, dengan kata awal tetap terpilih utuh.
         *
         * @return false kalau tidak sedang dalam mode kata (perluasan per karakter biasa)
         */
        private boolean extendByWord(java.awt.Point p) {
            var anchor = wordAnchor;
            var selection = getSelection();
            if (anchor == null || selection == null || selection != anchor.selection()) {
                wordAnchor = null;
                return false;
            }
            int row = row(p);
            int col = column(p);
            int[] word = wordAround(row, col);
            boolean before = row < anchor.row() || (row == anchor.row() && col < anchor.start());
            int edge = word == null ? col : before ? word[0] : word[1];
            selection.getStart().setLocation(before ? anchor.end() : anchor.start(), anchor.row());
            selection.updateEnd(new com.jediterm.core.compatibility.Point(edge, row));
            repaint();
            return true;
        }

        /** {@code [awal, akhir]} (inklusif) kata di sel ini, atau null kalau sel itu bukan bagian kata. */
        private int[] wordAround(int row, int col) {
            var buffer = getTerminalTextBuffer();
            buffer.lock();
            try {
                String text = buffer.getLine(row).getText();
                if (col < 0 || col >= text.length() || !isWordChar(text.charAt(col))) {
                    return null;
                }
                int start = col;
                int end = col;
                while (start > 0 && isWordChar(text.charAt(start - 1))) {
                    start--;
                }
                while (end + 1 < text.length() && isWordChar(text.charAt(end + 1))) {
                    end++;
                }
                return new int[] {start, end};
            } finally {
                buffer.unlock();
            }
        }

        static boolean isWordChar(char c) {
            return Character.isLetterOrDigit(c) || c == '_' || c == ''; //  = sel kedua karakter lebar (CJK)
        }

        /** Kolom/baris buffer di bawah titik panel, sama seperti perhitungan JediTerm (private di sana). */
        private int column(java.awt.Point p) {
            return Math.clamp(cellX(p), 0, Math.max(0, getTerminalTextBuffer().getWidth() - 1));
        }

        private int row(java.awt.Point p) {
            return Math.clamp(cellY(p), 0, Math.max(0, getTerminalTextBuffer().getHeight() - 1))
                    + getVerticalScrollModel().getValue();
        }

        /**
         * Klik kanan gaya Windows (kalau aktif di pengaturan). Shift+klik kanan tetap membuka menu bawaan JediTerm.
         * Saat aplikasi remote memakai mouse reporting (htop, mc), klik kanan tetap diteruskan ke aplikasi itu.
         */
        private boolean copyPasteClick(MouseEvent e) {
            return e.getButton() == MouseEvent.BUTTON3 && !e.isShiftDown()
                    && settings instanceof TerminalSettings ts && ts.rightClickCopyPaste() && !isRemoteMouseAction(e);
        }

        /**
         * Ada selection: copy lalu selection dihapus. Tidak ada: paste, tetap lewat {@link #pasteFilter}. Copy/paste
         * JediTerm private, jadi dijalankan lewat action-nya (yang juga dipakai menu klik kanan).
         */
        private void copyOrPaste() {
            boolean copy = getSelection() != null;
            String name = (copy ? settings.getCopyActionPresentation() : settings.getPasteActionPresentation()).getName();
            for (var action : getActions()) {
                if (action.getName().equals(name)) {
                    action.actionPerformed(null); // bukan isEnabled(): paste-nya membaca clipboard lewat pasteFilter
                    break;
                }
            }
            if (copy) {
                scrollArea(0, 0, 0); // satu-satunya jalur publik ke updateSelection(null); dy=0 tidak menggeser apa pun
                repaint();
            }
        }

        private java.awt.Point pressPoint; // EDT; titik klik kiri terakhir

        /**
         * Shift+drag melanjutkan selection; JediTerm 3.76 mengabaikan drag dengan Shift saat mouse reporting mati.
         *
         * <p>JediTerm membuat selection pada drag pertama, sekecil apa pun. Mouse/touchpad sering bergeser satu-dua
         * pixel saat diklik, sehingga klik biasa tiba-tiba memblok satu karakter. Drag yang belum keluar dari sel
         * tempat klik dimulai diabaikan; begitu pindah sel, selection dibuat dari titik klik seperti biasa.
         */
        @Override
        protected void processMouseMotionEvent(MouseEvent e) {
            if (e.getID() == MouseEvent.MOUSE_DRAGGED && extendsSelection(e)) {
                if (!extendByWord(e.getPoint())) {
                    super.processMouseMotionEvent(asPlainDrag(e));
                }
                return;
            }
            if (e.getID() == MouseEvent.MOUSE_DRAGGED && wordDrag && SwingUtilities.isLeftMouseButton(e)
                    && extendByWord(e.getPoint())) {
                e.consume();
                return;
            }
            if (e.getID() == MouseEvent.MOUSE_DRAGGED && (jitterOfClick(e) || nonLeftDrag(e))) {
                e.consume();
                return;
            }
            super.processMouseMotionEvent(e);
        }

        /**
         * JediTerm 3.76 tidak memeriksa tombol saat drag: drag klik kanan/tengah ikut membuat selection, dari titik
         * klik kiri terakhir (titik awal hanya diperbarui oleh klik kiri). Selection lokal hanya dari tombol kiri.
         */
        private boolean nonLeftDrag(MouseEvent e) {
            return !SwingUtilities.isLeftMouseButton(e) && !isRemoteMouseAction(e);
        }

        private boolean jitterOfClick(MouseEvent e) {
            var p = pressPoint;
            return p != null && SwingUtilities.isLeftMouseButton(e) && getSelection() == null
                    && !isRemoteMouseAction(e) && cellX(p) == cellX(e.getPoint()) && cellY(p) == cellY(e.getPoint());
        }

        private int cellX(java.awt.Point p) {
            return Math.floorDiv(p.x - getInsetX(), Math.max(1, myCharSize.width));
        }

        private int cellY(java.awt.Point p) {
            return Math.floorDiv(p.y, Math.max(1, myCharSize.height));
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
