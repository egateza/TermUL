package dev.egateza.termul.app.sftp;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.AppIcon;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.TooManyListenersException;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;

/**
 * Baris path di bawah toolbar panel SFTP (remote dan lokal): breadcrumb yang bisa diklik untuk pindah ke folder di
 * atasnya, dan field teks untuk mengetik path sendiri.
 *
 * <ul>
 *   <li>Klik segmen = buka folder itu. Segmen terakhir (folder sekarang) tebal dan tidak bisa diklik.</li>
 *   <li>Klik area kosong di kanan breadcrumb, tombol pensil, atau menu klik kanan "Edit path" = mode edit. Enter =
 *       buka path, Esc / klik di luar = batal. Path yang gagal dibuka membuat field tetap terbuka dengan tanda error,
 *       supaya ketikan tidak hilang.</li>
 *   <li>Path yang tidak muat: segmen di kiri dilipat ke tombol "…" (dropdown); segmen terakhir selalu terlihat.</li>
 *   <li>Klik kanan segmen: salin path / edit path. File yang di-drop ke segmen ditangani {@code dropHandler}.</li>
 * </ul>
 * Semua method dipanggil di EDT.
 */
final class PathBar extends JPanel {

    /** Satu segmen breadcrumb: teks yang tampil dan path lengkap folder itu. */
    record Crumb(String label, String path) {
    }

    private static final String CARD_CRUMBS = "crumbs";
    private static final String CARD_EDIT = "edit";
    private static final String SEPARATOR = "\u203A"; // ›

    private final CardLayout cards = new CardLayout();
    private final JPanel crumbsCard = new JPanel(new BorderLayout());
    /** Segmen breadcrumb; boleh lebih sempit dari isinya (segmen dilipat sendiri lewat {@link #rebuild}). */
    private final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)) {
        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, getPreferredSize().height);
        }
    };
    /** Wadah {@link #row}: memusatkannya secara vertikal; lebarnya = ruang yang tersedia untuk segmen. */
    private final JPanel middle = new JPanel(new GridBagLayout());
    private final JButton editButton = new JButton(AppIcon.PEN.icon());
    private final JTextField field = new JTextField();
    private final Consumer<String> submit;
    private final Function<String, TransferHandler> dropHandler;
    private List<Crumb> crumbs = List.of();
    private String path = "";
    private boolean editing;
    private boolean pending; // path dari field sedang dibuka: jangan batal karena fokus pindah (dialog, dsb.)

    /**
     * @param submit      dipanggil dengan teks dari field (Enter) atau path segmen yang diklik; pemilik membuka
     *                    folder lalu memanggil {@link #setPath} (berhasil) atau {@link #showError} (gagal)
     * @param dropHandler handler drop untuk folder tujuan tertentu; null = segmen tidak menerima drop
     */
    PathBar(Consumer<String> submit, Function<String, TransferHandler> dropHandler) {
        this.submit = submit;
        this.dropHandler = dropHandler;
        setLayout(cards);

        row.setOpaque(false);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)); // area kosong = klik untuk mengetik
        ((FlowLayout) row.getLayout()).setAlignOnBaseline(true); // tombol segmen dan label tebal sejajar teksnya
        // baris segmen di tengah secara vertikal: tinggi kartu mengikuti tombol pensil / field edit yang lebih tinggi
        middle.setOpaque(false);
        middle.setCursor(row.getCursor());
        var cell = new GridBagConstraints();
        cell.weightx = 1;
        cell.fill = GridBagConstraints.HORIZONTAL;
        middle.add(row, cell);
        crumbsCard.add(middle, BorderLayout.CENTER);
        editButton.setToolTipText(I18n.t("sftp.path.edit"));
        editButton.setFocusable(false);
        editButton.putClientProperty("JButton.buttonType", "toolBarButton");
        editButton.setCursor(Cursor.getDefaultCursor());
        editButton.addActionListener(e -> startEdit());
        crumbsCard.add(editButton, BorderLayout.EAST);
        var emptyClick = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    startEdit();
                }
            }
        };
        row.addMouseListener(emptyClick); // klik pada segmen tidak sampai ke sini: tombol memakan event-nya
        middle.addMouseListener(emptyClick);
        crumbsCard.addMouseListener(emptyClick);
        add(crumbsCard, CARD_CRUMBS);

        field.addActionListener(e -> {
            pending = true;
            field.putClientProperty("JComponent.outline", null);
            submit.accept(field.getText().strip());
        });
        field.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelEdit");
        field.getActionMap().put("cancelEdit", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                cancelEdit();
            }
        });
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (!e.isTemporary() && editing && !pending) {
                    cancelEdit();
                }
            }
        });
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                clearError();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                clearError();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
            }
        });
        add(field, CARD_EDIT);

        middle.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                rebuild(); // jumlah segmen yang muat bergantung lebar
            }
        });
        updateLook();
    }

    /**
     * Folder yang sedang dibuka. Mode edit ditutup kalau path ketikan user berhasil dibuka; kalau folder berganti dari
     * tempat lain (mis. "Ikuti folder terminal") selagi user mengetik, ketikannya dibiarkan.
     */
    void setPath(String path, List<Crumb> crumbs) {
        this.path = path;
        this.crumbs = List.copyOf(crumbs);
        if (!editing) {
            field.setText(path);
        } else if (pending) {
            stopEdit();
        }
        rebuild();
    }

    /** Path dari field gagal dibuka: field tetap terbuka dengan tanda error dan pesannya sebagai tooltip. */
    void showError(String message) {
        pending = false;
        if (!editing) {
            return;
        }
        field.putClientProperty("JComponent.outline", "error");
        field.setToolTipText(message);
        field.requestFocusInWindow();
    }

    boolean isEditing() {
        return editing;
    }

    void startEdit() {
        editing = true;
        pending = false;
        field.setText(path);
        cards.show(this, CARD_EDIT);
        field.requestFocusInWindow();
        field.selectAll();
    }

    private void cancelEdit() {
        stopEdit();
        field.setText(path);
    }

    private void stopEdit() {
        editing = false;
        pending = false;
        clearError();
        cards.show(this, CARD_CRUMBS);
    }

    private void clearError() {
        if (field.getClientProperty("JComponent.outline") != null) {
            field.putClientProperty("JComponent.outline", null);
            field.setToolTipText(null);
        }
    }

    // ------------------------------------------------------------------ breadcrumb

    /** Susun ulang segmen untuk lebar sekarang: yang tidak muat di kiri masuk ke tombol "…". */
    private void rebuild() {
        row.removeAll();
        if (!crumbs.isEmpty()) {
            var parts = new ArrayList<JComponent>(crumbs.size());
            int[] widths = new int[crumbs.size()];
            for (int i = 0; i < crumbs.size(); i++) {
                boolean last = i == crumbs.size() - 1;
                var c = crumbComponent(crumbs.get(i), last);
                parts.add(c);
                widths[i] = c.getPreferredSize().width + (last ? 0 : separator().getPreferredSize().width);
            }
            var more = moreButton(List.of());
            int available = middle.getWidth() > 0 ? middle.getWidth() - 4 : Integer.MAX_VALUE;
            int first = firstVisible(widths, more.getPreferredSize().width, available);
            if (first > 0) {
                row.add(moreButton(crumbs.subList(0, first)));
                row.add(separator());
            }
            for (int i = first; i < parts.size(); i++) {
                row.add(parts.get(i));
                if (i < parts.size() - 1) {
                    row.add(separator());
                }
            }
        }
        row.revalidate();
        row.repaint();
    }

    /**
     * Indeks segmen pertama yang tampil (segmen sebelumnya dilipat ke tombol "…"). Segmen terakhir selalu tampil,
     * walau tidak muat.
     *
     * @param widths    lebar tiap segmen termasuk pemisah sesudahnya
     * @param moreWidth lebar tombol "…" termasuk pemisah sesudahnya
     */
    static int firstVisible(int[] widths, int moreWidth, int available) {
        int total = 0;
        for (int w : widths) {
            total += w;
        }
        if (total <= available) {
            return 0;
        }
        int used = moreWidth;
        int first = widths.length;
        while (first > 0 && (first == widths.length || used + widths[first - 1] <= available)) {
            used += widths[--first];
        }
        // ada yang dilipat: minimal satu segmen di dalam "…", tapi segmen terakhir tidak pernah ikut dilipat
        return Math.min(Math.max(first, 1), widths.length - 1);
    }

    private JComponent crumbComponent(Crumb crumb, boolean last) {
        JComponent c;
        if (last) {
            var label = new JLabel(crumb.label());
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            label.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            c = label;
        } else {
            var button = new JButton(crumb.label());
            button.putClientProperty("JButton.buttonType", "toolBarButton");
            button.setFocusable(false);
            button.setMargin(new java.awt.Insets(1, 3, 1, 3));
            button.setToolTipText(crumb.path());
            button.addActionListener(e -> submit.accept(crumb.path()));
            c = button;
        }
        c.setCursor(Cursor.getDefaultCursor());
        c.setComponentPopupMenu(crumbMenu(crumb));
        installDrop(c, crumb.path());
        return c;
    }

    private JLabel separator() {
        var label = new JLabel(SEPARATOR);
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        label.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        return label;
    }

    /** Tombol "…" berisi segmen yang dilipat, urut dari root. */
    private JButton moreButton(List<Crumb> folded) {
        var button = new JButton("\u2026");
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setFocusable(false);
        button.setMargin(new java.awt.Insets(1, 3, 1, 3));
        button.setCursor(Cursor.getDefaultCursor());
        button.setToolTipText(I18n.t("sftp.path.more"));
        button.addActionListener(e -> {
            var menu = new JPopupMenu();
            for (var crumb : folded) {
                var item = new JMenuItem(crumb.path());
                item.addActionListener(a -> submit.accept(crumb.path()));
                menu.add(item);
            }
            menu.show(button, 0, button.getHeight());
        });
        return button;
    }

    private JPopupMenu crumbMenu(Crumb crumb) {
        var menu = new JPopupMenu();
        var copy = new JMenuItem(I18n.t("sftp.path.copy"));
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(crumb.path()), null));
        menu.add(copy);
        var edit = new JMenuItem(I18n.t("sftp.path.edit"));
        edit.addActionListener(e -> startEdit());
        menu.add(edit);
        return menu;
    }

    /** Segmen menerima drop ke foldernya, dengan latar aksen selama file diseret di atasnya. */
    private void installDrop(JComponent c, String dir) {
        if (dropHandler == null) {
            return;
        }
        c.setTransferHandler(dropHandler.apply(dir));
        var target = c.getDropTarget(); // dibuat oleh setTransferHandler
        if (target == null) {
            return;
        }
        try {
            target.addDropTargetListener(new DropTargetAdapter() {
                @Override
                public void dragEnter(DropTargetDragEvent e) {
                    highlight(c, true);
                }

                @Override
                public void dragExit(DropTargetEvent e) {
                    highlight(c, false);
                }

                @Override
                public void drop(DropTargetDropEvent e) {
                    highlight(c, false);
                }
            });
        } catch (TooManyListenersException e) {
            // DropTarget Swing menerima banyak listener; kalau tidak, cukup tanpa highlight
        }
    }

    private static void highlight(JComponent c, boolean on) {
        c.setOpaque(on);
        c.setBackground(on ? UIManager.getColor("Component.focusColor") : null);
        c.repaint();
    }

    // ------------------------------------------------------------------ tampilan

    @Override
    public void updateUI() {
        super.updateUI();
        if (field != null) { // dipanggil dari konstruktor JPanel sebelum field terisi
            updateLook();
        }
    }

    /** Breadcrumb tampil seperti field teks (border + latar), supaya jelas bisa diklik untuk mengetik. */
    private void updateLook() {
        crumbsCard.setBorder(UIManager.getBorder("TextField.border"));
        Color bg = UIManager.getColor("TextField.background");
        crumbsCard.setBackground(bg);
        crumbsCard.setOpaque(bg != null);
    }

    @Override
    public Dimension getPreferredSize() {
        var d = super.getPreferredSize();
        return new Dimension(d.width, Math.max(field.getPreferredSize().height, d.height));
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, getPreferredSize().height); // boleh sempit: segmen dilipat
    }

    // ------------------------------------------------------------------ pemecahan path

    /** Segmen path remote POSIX: {@code /etc/nginx} → {@code /}, {@code etc}, {@code nginx}. */
    static List<Crumb> remoteCrumbs(String path) {
        var names = new ArrayList<String>();
        for (String part : path.split("/")) {
            if (!part.isEmpty()) {
                names.add(part);
            }
        }
        return crumbs("/", names, "/");
    }

    /** Segmen path lokal, sesuai aturan path OS ini (root {@code /}, drive {@code C:\}, atau UNC). */
    static List<Crumb> localCrumbs(java.nio.file.Path path) {
        var root = path.getRoot();
        var names = new ArrayList<String>();
        for (var name : path) {
            names.add(name.toString());
        }
        String separator = path.getFileSystem().getSeparator();
        return crumbs(root == null ? "" : root.toString(), names, separator);
    }

    /**
     * Segmen dari root + nama-nama folder. Path segmen ke-i = root + nama[0..i] digabung {@code separator}; root
     * sudah diakhiri pemisah ({@code /}, {@code C:\}).
     */
    static List<Crumb> crumbs(String root, List<String> names, String separator) {
        var result = new ArrayList<Crumb>(names.size() + 1);
        var path = new StringBuilder(root);
        if (!root.isEmpty()) {
            result.add(new Crumb(rootLabel(root, separator), root));
        }
        for (int i = 0; i < names.size(); i++) {
            if (i > 0 || (!root.isEmpty() && !root.endsWith(separator))) {
                path.append(separator);
            }
            path.append(names.get(i));
            result.add(new Crumb(names.get(i), path.toString()));
        }
        return result;
    }

    /** Teks root: {@code /} tetap, {@code C:\} jadi {@code C:} (pemisah sudah tampil sebagai ›). */
    private static String rootLabel(String root, String separator) {
        return root.length() > 1 && root.endsWith(separator) ? root.substring(0, root.length() - 1) : root;
    }
}
