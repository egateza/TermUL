package dev.egateza.termul.app.ui.profiles;

import dev.egateza.termul.app.snippet.SnippetUi;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;

/** Dialog kecil Profile manager bergaya prototipe. EDT. */
final class PmDialogs {

    private PmDialogs() {
    }

    /**
     * Konfirmasi tindakan berbahaya (prototipe {@code role=alertdialog}: bingkai dan judul merah, catatan kuning, tombol
     * bahaya + tombol batal yang jadi default).
     *
     * @return true kalau user memilih {@code okLabel}
     */
    static boolean confirmDanger(Window owner, String title, String body, List<String> notes, String okLabel,
                                 String cancelLabel) {
        var result = new boolean[1];
        var dialog = dangerDialog(owner, title, body, notes, okLabel, cancelLabel, result);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return result[0];
    }

    /** Dialog {@link #confirmDanger} yang sudah berukuran, belum ditampilkan; {@code result[0]} = pilihan user. */
    static JDialog dangerDialog(Window owner, String title, String body, List<String> notes, String okLabel,
                                String cancelLabel, boolean[] result) {
        var dialog = new JDialog(owner, title, JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        boolean round = SnippetUi.roundWindow(dialog);
        var root = new SnippetUi.RoundPanel(new BorderLayout(), SnippetUi::panel, SnippetUi::danger, round ? 16 : 0);
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        var column = new SnippetUi.TrackingPanel(null);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        var t = SnippetUi.label(title, SnippetUi.font(1, Font.BOLD), SnippetUi.danger());
        add(column, t, 12);
        add(column, SnippetUi.text(body, SnippetUi.font(0, Font.PLAIN), SnippetUi.fg()), 12);
        for (String n : notes) {
            add(column, ProfileManagerDialog.noteBox(n), 12);
        }
        var ok = new SnippetUi.Button(okLabel, null, SnippetUi.Variant.DANGER);
        ok.addActionListener(e -> {
            result[0] = true;
            dialog.dispose();
        });
        var cancel = new SnippetUi.Button(cancelLabel, null, SnippetUi.Variant.PRIMARY);
        cancel.addActionListener(e -> dialog.dispose());
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(ok);
        buttons.add(cancel);
        add(column, buttons, 0);
        root.add(column, BorderLayout.CENTER);
        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(cancel);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        fitHeight(dialog, owner, 440);
        cancel.requestFocusInWindow();
        return dialog;
    }

    /**
     * Tinggi dialog = tinggi isi pada lebar {@code width}. Paragraf yang membungkus baru tahu tingginya setelah lebarnya
     * diketahui, jadi isi disusun dulu di lebar akhir lalu diukur (diulang sampai stabil); menghitung dari preferred
     * size sebelum tata letak membuat dialog terlalu pendek dan tombol bawah terpotong.
     */
    static void fitHeight(JDialog dialog, Window owner, int width) {
        int max = owner == null ? 600 : Math.max(200, owner.getHeight() - 32);
        var content = dialog.getContentPane();
        int height = 0;
        for (int i = 0; i < 3; i++) {
            content.setSize(width, Math.max(height, 1));
            layoutTree(content); // validate() tidak menyusun apa pun sebelum dialog punya peer
            invalidateTree(content); // ukuran preferred induk masih tersimpan dari sebelum paragraf tahu lebarnya
            int next = Math.min(max, content.getPreferredSize().height);
            if (next == height) {
                break;
            }
            height = next;
        }
        dialog.setSize(width, height);
    }

    /** Susun {@code c} dan semua isinya sekarang, pada ukurannya saat ini (juga sebelum ditampilkan). */
    static void layoutTree(java.awt.Container c) {
        invalidateTree(c);
        doLayoutTree(c);
    }

    private static void doLayoutTree(java.awt.Container c) {
        c.doLayout();
        for (var child : c.getComponents()) {
            if (child instanceof java.awt.Container k) {
                doLayoutTree(k);
            }
        }
    }

    private static void invalidateTree(java.awt.Container c) {
        c.invalidate();
        for (var child : c.getComponents()) {
            if (child instanceof java.awt.Container k) {
                invalidateTree(k);
            }
        }
    }

    /**
     * Bingkai dialog anak (Manage folders, Export, Import): kartu membulat berbingkai, isi di tengah, Esc menutup.
     * Ukuran: lebar {@code width}, tinggi mengikuti isi (maks. tinggi owner − 32).
     */
    static void shell(JDialog dialog, Window owner, JComponent content, int width, int height) {
        dialog.setUndecorated(true);
        boolean round = SnippetUi.roundWindow(dialog);
        var root = new SnippetUi.RoundPanel(new BorderLayout(), SnippetUi::panel, SnippetUi::frame, round ? 16 : 0);
        root.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        root.add(content, BorderLayout.CENTER);
        dialog.setContentPane(root);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        int max = owner == null ? 800 : Math.max(320, owner.getHeight() - 32);
        dialog.setSize(width, Math.min(max, height));
        SnippetUi.keepSizeAcrossScreens(dialog);
        dialog.setLocationRelativeTo(owner);
    }

    /** Baris atas dialog anak: judul + keterangan, opsional teks di kanan. */
    static JComponent head(String title, String sub, JComponent right) {
        var p = new JPanel(new BorderLayout(10, 0));
        p.setBackground(SnippetUi.panel());
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(16, 18, 12, 18)));
        var texts = new SnippetUi.TrackingPanel(null);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        texts.setOpaque(false);
        add(texts, SnippetUi.label(title, SnippetUi.font(1, Font.BOLD), SnippetUi.fg()), sub == null ? 0 : 4);
        if (sub != null) {
            add(texts, SnippetUi.text(sub, SnippetUi.font(-0.5f, Font.PLAIN), SnippetUi.muted()), 0);
        }
        p.add(texts, BorderLayout.CENTER);
        if (right != null) {
            var wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false);
            wrap.add(right, BorderLayout.NORTH);
            p.add(wrap, BorderLayout.EAST);
        }
        return p;
    }

    /** Baris tombol bawah dialog anak (rata kanan, latar bar). */
    static JPanel foot(JComponent... parts) {
        var p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setBackground(SnippetUi.panel());
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SnippetUi.border()),
                BorderFactory.createEmptyBorder(12, 18, 12, 18)));
        for (var c : parts) {
            if (c == null) {
                p.add(javax.swing.Box.createHorizontalGlue());
            } else {
                if (p.getComponentCount() > 0 && p.getComponent(p.getComponentCount() - 1) instanceof JComponent prev
                        && !(prev instanceof javax.swing.Box.Filler)) {
                    p.add(javax.swing.Box.createHorizontalStrut(8));
                }
                p.add(c);
            }
        }
        return p;
    }

    /**
     * Tambahkan {@code c} ke kolom BoxLayout vertikal, dibungkus baris yang tingginya selalu mengikuti tinggi
     * preferred saat ini (paragraf yang membungkus ikut bertambah tinggi saat lebar diketahui, dan tidak melar mengisi
     * ruang kosong).
     */
    static void add(JPanel column, JComponent c, int gap) {
        var row = new Row(c);
        column.add(row);
        if (gap > 0) {
            column.add(javax.swing.Box.createVerticalStrut(gap));
        }
    }

    /** Pembungkus satu baris kolom: lebar penuh, tinggi = tinggi preferred isinya (0 kalau isinya disembunyikan). */
    static final class Row extends JPanel {
        Row(JComponent content) {
            super(new BorderLayout());
            setOpaque(false);
            setAlignmentX(0f);
            add(content, BorderLayout.CENTER);
        }

        @Override
        public java.awt.Dimension getMaximumSize() {
            return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }
}
