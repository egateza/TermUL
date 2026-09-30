package dev.egateza.termul.app.log;

import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.UiAsync;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Panel log aplikasi di bagian bawah window. Mengambil baris baru dari {@link LogBuffer} dengan polling
 * (Swing {@link Timer}, hanya saat panel terlihat), jadi logging dari thread lain tidak pernah menyentuh EDT.
 */
public final class LogPanel extends JPanel {

    static final int POLL_MS = 300;
    static final int MAX_PARAGRAPHS = 5000;

    private final LogBuffer buffer;
    private final JTextPane text = new JTextPane();
    private final JCheckBox autoScroll = new JCheckBox("Auto-scroll", true);
    private final SimpleAttributeSet normal = new SimpleAttributeSet();
    private final SimpleAttributeSet warning = new SimpleAttributeSet();
    private final Timer timer = new Timer(POLL_MS, e -> {
        if (isShowing()) {
            poll();
        }
    });
    private long lastSeq; // EDT

    /**
     * @param logDir  folder file log (tombol "Buka folder log")
     * @param io      executor untuk membuka folder (di luar EDT)
     * @param onClose dipanggil saat tombol tutup ditekan
     */
    public LogPanel(LogBuffer buffer, Path logDir, Executor io, Runnable onClose) {
        super(new BorderLayout());
        this.buffer = buffer;

        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, text.getFont().getSize()));
        ((DefaultCaret) text.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        Color red = UIManager.getColor("Actions.Red");
        StyleConstants.setForeground(warning, red != null ? red : new Color(0xE0, 0x55, 0x55));

        var clear = new JButton("Bersihkan", AppIcon.BROOM.icon());
        clear.setToolTipText("Kosongkan tampilan (file log tidak dihapus)");
        clear.addActionListener(e -> text.setText(""));
        var openDir = new JButton("Buka folder log", AppIcon.FOLDER_OPEN.icon());
        openDir.setToolTipText(logDir.toString());
        openDir.addActionListener(e -> UiAsync.run(io, () -> openFolder(logDir),
                err -> Dialogs.error(this, "Gagal membuka folder log", err)));
        var close = new JButton(AppIcon.XMARK.icon());
        close.setToolTipText("Sembunyikan panel log");
        close.addActionListener(e -> onClose.run());

        var bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(new JLabel(" Log aplikasi "));
        bar.addSeparator();
        bar.add(autoScroll);
        bar.add(clear);
        bar.add(openDir);
        bar.add(javax.swing.Box.createHorizontalGlue());
        bar.add(close);

        var scroll = new JScrollPane(text);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(bar, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Component.borderColor")));
    }

    /** Buka folder di Explorer (dibuat dulu kalau belum ada). Jangan dipanggil di EDT. */
    public static void openFolder(Path dir) {
        try {
            Files.createDirectories(dir);
            Desktop.getDesktop().open(dir.toFile());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Folder log tidak bisa dibuka: " + dir, e);
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        timer.start();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    /** Ambil baris baru dari buffer. EDT. */
    void poll() {
        List<LogBuffer.Entry> fresh = buffer.since(lastSeq);
        if (fresh.isEmpty()) {
            return;
        }
        StyledDocument doc = text.getStyledDocument();
        try {
            long skipped = fresh.getFirst().seq() - lastSeq - 1;
            if (lastSeq > 0 && skipped > 0) {
                doc.insertString(doc.getLength(), "… " + skipped + " baris log terlewat (lihat file log)\n", warning);
            }
            for (var entry : fresh) {
                doc.insertString(doc.getLength(), entry.text() + "\n", entry.warning() ? warning : normal);
            }
            trim(doc);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        lastSeq = fresh.getLast().seq();
        if (autoScroll.isSelected()) {
            text.setCaretPosition(doc.getLength());
        }
    }

    private static void trim(StyledDocument doc) throws BadLocationException {
        var root = doc.getDefaultRootElement();
        int excess = root.getElementCount() - MAX_PARAGRAPHS;
        if (excess > 0) {
            doc.remove(0, root.getElement(excess).getStartOffset());
        }
    }

    /** Isi teks yang sedang tampil (untuk test). */
    String displayedText() {
        var doc = text.getDocument();
        try {
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }
}
