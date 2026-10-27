package dev.egateza.termul.app.terminal;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.KeyEvent;
import javax.swing.JPanel;

/**
 * Satu panel terminal di tab (atau di split). Sealed supaya {@code MainFrame} menangani setiap jenis secara eksplisit:
 * fitur khusus SSH (SFTP, inject password, remote edit, profil host) hanya lewat pattern matching ke
 * {@link TerminalTab}, bukan flag; {@link LocalTerminalTab} adalah shell lokal (ADR 0005). Semua method dipanggil di EDT.
 */
public abstract sealed class TermPane extends JPanel permits TerminalTab, LocalTerminalTab {

    /** Sumber snippet dan pengaturan tombol snippet (dari MainFrame). Semua method dipanggil di EDT. */
    public interface SnippetHost {
        /** Menempel/menyalin snippet (berisi store snippet). */
        dev.egateza.termul.app.snippet.SnippetSender sender();

        boolean buttonVisible();

        /** Sudut tombol, nilai {@code AppConfig.SNIPPET_*}. */
        String corner();

        /** Tombol diseret ke sudut lain; simpan untuk semua tab. */
        void cornerChanged(String corner);

        /** Buka Snippet manager. */
        void manage();
    }

    /** Terminal ini sebagai tempat menempel snippet. */
    public abstract dev.egateza.termul.app.snippet.SnippetTarget snippetTarget();

    /** Buka menu snippet untuk host ini (tombol snippet / shortcut); tidak apa-apa kalau tidak ada terminal. */
    public abstract void openSnippetMenu();

    protected TermPane() {
        super(new BorderLayout());
    }

    /** Nama di judul tab dan dialog, mis. nama host (+ " (SFTP)"). */
    public abstract String displayName();

    /** Keterangan kedua di tooltip tab dan dialog, mis. {@code user@host:port}. */
    public abstract String displayAddress();

    /** Warna environment (titik di judul tab); null = tanpa warna. */
    public abstract Color environmentColor();

    /** false untuk panel tanpa shell (tab "SFTP saja"). */
    public abstract boolean hasTerminal();

    /** true kalau panel menampilkan SFTP (tab "SFTP saja" atau panel SFTP terbuka), untuk ikon tab. */
    public abstract boolean showsSftp();

    /** true kalau terminal sedang tersambung/berjalan. */
    public abstract boolean isConnected();

    /** true kalau menutup panel ini akan memutus sesi atau menghentikan proses yang masih berjalan. */
    public abstract boolean isSessionActive();

    /** Transfer SFTP yang berjalan/antre; 0 untuk panel tanpa SFTP. */
    public int activeTransfers() {
        return 0;
    }

    /** Sambung ulang / jalankan ulang sesi. */
    public abstract void reconnect();

    public abstract void focusTerminal();

    /** @param direction 1 = perbesar, -1 = perkecil, 0 = ukuran default */
    public abstract void zoom(int direction);

    public abstract void reloadFont();

    public abstract void reloadCursor();

    public abstract void reloadColors();

    public abstract void refreshSnippetButton();

    /**
     * Dipanggil dari dispatcher key global sebelum JediTerm menerima key.
     *
     * @return true kalau event sudah ditangani (jangan diteruskan ke terminal)
     */
    public abstract boolean interceptKey(KeyEvent e);

    /** Menutup panel dan sesinya (tab ditutup). Idempotent. */
    public abstract void dispose();

    private TextHighlighter.Rules highlightRules; // EDT; null = tanpa highlight
    private TextHighlighter highlighter;          // EDT; milik widget yang sedang tampil

    /** Profil highlight teks untuk panel ini (null = mati); berlaku juga untuk terminal yang dibuka ulang. EDT. */
    public final void setHighlight(TextHighlighter.Rules rules) {
        highlightRules = rules;
        if (highlighter != null) {
            highlighter.setRules(rules);
        }
    }

    /** Pasang highlight ke widget terminal baru (setelah sambung/jalankan ulang). EDT. */
    protected final void attachHighlighter(com.jediterm.terminal.ui.JediTermWidget widget) {
        detachHighlighter();
        var panel = widget.getTerminalPanel();
        highlighter = new TextHighlighter(widget.getTerminalTextBuffer(),
                () -> javax.swing.SwingUtilities.invokeLater(panel::repaint));
        highlighter.setRules(highlightRules);
    }

    /** Lepas highlight dari widget yang ditutup. EDT. */
    protected final void detachHighlighter() {
        if (highlighter != null) {
            highlighter.close();
            highlighter = null;
        }
    }
}
