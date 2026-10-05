package dev.egateza.termul.app.sftp;

import dev.egateza.termul.app.i18n.I18n;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Baris status panel SFTP: menampilkan keterangan aktivitas terakhir (upload mulai/selesai, error, reconnect, ...)
 * dengan warna sesuai {@link Level}, dan menyimpan riwayatnya (klik baris untuk melihat). Boleh dipanggil dari thread
 * mana pun; tampilan diperbarui di EDT.
 */
public final class ActivityBar extends JPanel {

    public enum Level {
        INFO("●"), SUCCESS("✔"), WARN("▲"), ERROR("✖");

        private final String symbol;

        Level(String symbol) {
            this.symbol = symbol;
        }
    }

    /** Satu catatan di riwayat. */
    public record Entry(LocalTime time, Level level, String message) {
        String text() {
            return TIME.format(time) + "  " + level.symbol + "  " + message;
        }
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_HISTORY = 200;

    private static final int MAX_LINES = 4;

    /**
     * Teks aktivitas di-wrap mengikuti lebar panel SFTP (bukan satu baris panjang): lebar minimum/preferred sengaja
     * kecil supaya pesan panjang (mis. error) tidak mengunci split pane, dan tinggi dibatasi {@link #MAX_LINES} baris.
     * Teks utuh tetap ada di tooltip/riwayat.
     */
    private final JTextArea label = new JTextArea(" ") {
        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, lineHeight());
        }

        @Override
        public Dimension getPreferredSize() {
            var size = super.getPreferredSize();
            return new Dimension(Math.min(size.width, 120), Math.min(size.height, lineHeight() * MAX_LINES));
        }

        @Override
        public void updateUI() {
            super.updateUI();
            setFont(UIManager.getFont("Label.font")); // tetap tampil seperti label, juga setelah ganti tema
            setOpaque(false);
            setBorder(null);
        }

        private int lineHeight() {
            return getFontMetrics(getFont()).getHeight();
        }
    };
    private final Deque<Entry> history = new ArrayDeque<>(); // EDT

    public ActivityBar() {
        super(new BorderLayout(6, 0));
        setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        label.setLineWrap(true);
        label.setWrapStyleWord(true);
        label.setEditable(false);
        label.setFocusable(false);
        label.setHighlighter(null);
        label.addComponentListener(new ComponentAdapter() {
            private int lastWidth = -1;

            @Override
            public void componentResized(ComponentEvent e) {
                // Jumlah baris wrap bergantung pada lebar: hitung ulang tinggi saat lebar panel berubah.
                if (label.getWidth() != lastWidth) {
                    lastWidth = label.getWidth();
                    SwingUtilities.invokeLater(ActivityBar.this::revalidate);
                }
            }
        });
        label.setToolTipText(I18n.t("activity.tooltip"));
        label.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showHistory();
            }
        });
        add(label, BorderLayout.CENTER);
    }

    /** Catat dan tampilkan aktivitas. */
    public void report(Level level, String message) {
        var entry = new Entry(LocalTime.now(), level, message);
        onEdt(() -> {
            history.addFirst(entry);
            while (history.size() > MAX_HISTORY) {
                history.removeLast();
            }
            show(entry);
        });
    }

    /** Tampilkan keterangan sementara (mis. hitung mundur) tanpa masuk riwayat. */
    public void progress(String message) {
        onEdt(() -> show(new Entry(LocalTime.now(), Level.INFO, message)));
    }

    /** Teks yang sedang tampil (untuk test). */
    public String text() {
        return label.getText();
    }

    /** Jumlah catatan di riwayat (untuk test). Panggil setelah antrean EDT kosong. */
    int historySize() {
        return history.size();
    }

    private void show(Entry entry) {
        label.setText(entry.text());
        label.setToolTipText(entry.text() + " — " + I18n.t("activity.tooltip"));
        label.setForeground(switch (entry.level()) {
            case SUCCESS -> new Color(0x2E9E5B);
            case WARN -> new Color(0xD9A441);
            case ERROR -> new Color(0xE5484D);
            case INFO -> UIManager.getColor("Label.foreground");
        });
    }

    private void showHistory() {
        var model = new DefaultListModel<String>();
        List<Entry> entries = new ArrayList<>(history);
        entries.forEach(e -> model.addElement(e.text()));
        if (entries.isEmpty()) {
            model.addElement(I18n.t("activity.empty"));
        }
        var list = new JList<>(model);
        var scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(Math.max(420, getWidth() - 12), 220));
        var popup = new JPopupMenu(I18n.t("activity.history"));
        popup.add(scroll);
        popup.show(this, 0, -scroll.getPreferredSize().height - 4);
    }

    private static void onEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }
}
