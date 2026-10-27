package dev.egateza.termul.app.sftp;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.Shortcuts;
import dev.egateza.termul.core.bookmark.BookmarkStore.Side;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Dialog "Buka folder" ala WinSCP: path yang bisa diketik (dengan riwayat folder panel), dan dua daftar bookmark,
 * yaitu milik host ini dan bersama untuk semua host. Perubahan bookmark langsung disimpan (juga kalau dialog dibatalkan);
 * OK membuka path di kolom atas. Semua method dipanggil di EDT.
 */
final class BookmarkDialog {

    /** Tab yang terakhir dipakai per sisi panel, supaya dialog berikutnya dibuka di tab yang sama. EDT. */
    private static final Map<Side, Integer> lastTab = new EnumMap<>(Side.class);

    /**
     * @param host     id profil untuk bookmark host
     * @param hostName nama profil (judul tab bookmark host)
     * @param current  folder yang sedang dibuka panel (isi awal kolom path), atau null
     * @param recent   folder yang baru dibuka panel ini, terbaru dulu (isi dropdown kolom path)
     */
    record Request(Side side, UUID host, String hostName, String current, List<String> recent) {
    }

    private final SftpBookmarks bookmarks;
    private final Request request;
    private final JDialog dialog;
    private final JComboBox<String> path;
    private final JTextField pathField;
    private final JTabbedPane tabs = new JTabbedPane();
    private final List<JList<String>> lists = new ArrayList<>(); // urut tab: host, bersama
    private final JButton add = new JButton(I18n.t("sftp.bookmark.add"));
    private final JButton remove = new JButton(I18n.t("sftp.bookmark.remove"));
    private final JButton up = new JButton(I18n.t("sftp.bookmark.up"));
    private final JButton down = new JButton(I18n.t("sftp.bookmark.down"));
    private String result;

    private BookmarkDialog(Component parent, SftpBookmarks bookmarks, Request request) {
        this.bookmarks = bookmarks;
        this.request = request;
        var window = SwingUtilities.getWindowAncestor(parent);
        dialog = new JDialog(window, I18n.t("sftp.bookmark.title"), java.awt.Dialog.ModalityType.APPLICATION_MODAL);

        var items = new ArrayList<String>();
        if (request.current() != null) {
            items.add(request.current());
        }
        request.recent().stream().filter(p -> !items.contains(p)).forEach(items::add);
        path = new JComboBox<>(items.toArray(String[]::new));
        path.setEditable(true);
        path.setSelectedItem(request.current() == null ? "" : request.current());
        pathField = (JTextField) path.getEditor().getEditorComponent();
        pathField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshButtons();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshButtons();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshButtons();
            }
        });

        addTab(I18n.t("sftp.bookmark.tab.host", request.hostName()), request.host());
        addTab(I18n.t("sftp.bookmark.tab.shared"), null);
        tabs.setSelectedIndex(Math.min(lastTab.getOrDefault(request.side(), 0), tabs.getTabCount() - 1));
        tabs.addChangeListener(e -> {
            lastTab.put(request.side(), tabs.getSelectedIndex());
            refreshButtons();
        });
        tabs.setToolTipTextAt(0, I18n.t("sftp.bookmark.tab.host.tip"));
        tabs.setToolTipTextAt(1, I18n.t("sftp.bookmark.tab.shared.tip"));

        add.setToolTipText(I18n.t("sftp.bookmark.add.tip"));
        add.addActionListener(e -> addCurrent());
        remove.addActionListener(e -> removeSelected());
        up.addActionListener(e -> move(-1));
        down.addActionListener(e -> move(1));

        dialog.add(body(), BorderLayout.CENTER);
        dialog.add(footer(), BorderLayout.SOUTH);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        refreshButtons();
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        SwingUtilities.invokeLater(() -> {
            pathField.requestFocusInWindow();
            pathField.selectAll();
        });
    }

    /** Batas riwayat folder per panel. */
    static final int RECENT_LIMIT = 15;

    /** Catat folder yang baru dibuka di depan riwayat panel (tanpa duplikat, paling banyak {@link #RECENT_LIMIT}). EDT. */
    static void remember(java.util.Deque<String> recent, String dir) {
        recent.remove(dir);
        recent.addFirst(dir);
        while (recent.size() > RECENT_LIMIT) {
            recent.removeLast();
        }
    }

    /** @return path yang dipilih untuk dibuka, atau kosong kalau dibatalkan */
    static Optional<String> show(Component parent, SftpBookmarks bookmarks, Request request) {
        var d = new BookmarkDialog(parent, bookmarks, request);
        d.dialog.setVisible(true);
        return Optional.ofNullable(d.result);
    }

    private JPanel body() {
        var top = new JPanel(new BorderLayout(10, 4));
        top.add(new JLabel(AppIcon.FOLDER_OPEN.icon(32, () -> AppIcon.FOLDER_COLOR)), BorderLayout.WEST);
        var label = new JLabel(I18n.t("sftp.bookmark.path"));
        label.setLabelFor(pathField);
        var pathRow = new JPanel(new BorderLayout(6, 0));
        pathRow.add(path, BorderLayout.CENTER);
        if (request.side() == Side.LOCAL) {
            var browse = new JButton(I18n.t("sftp.bookmark.browse"));
            browse.addActionListener(e -> browse());
            pathRow.add(browse, BorderLayout.EAST);
        }
        var fields = new JPanel(new BorderLayout(0, 4));
        fields.add(label, BorderLayout.NORTH);
        fields.add(pathRow, BorderLayout.CENTER);
        top.add(fields, BorderLayout.CENTER);

        var buttons = new JPanel(new GridLayout(0, 1, 0, 6));
        for (var b : new JButton[] {add, remove, up, down}) {
            buttons.add(b);
        }
        var side = new JPanel(new BorderLayout());
        side.add(buttons, BorderLayout.NORTH);

        var center = new JPanel(new BorderLayout(8, 0));
        tabs.setPreferredSize(new Dimension(440, 260));
        center.add(tabs, BorderLayout.CENTER);
        center.add(side, BorderLayout.EAST);

        var body = new JPanel(new BorderLayout(0, 12));
        body.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        body.add(top, BorderLayout.NORTH);
        body.add(center, BorderLayout.CENTER);
        body.add(new JLabel(I18n.t("sftp.bookmark.hint", Shortcuts.text(Shortcuts.menu(KeyEvent.VK_B)))),
                BorderLayout.SOUTH);
        return body;
    }

    private JPanel footer() {
        var ok = new JButton(I18n.t("sftp.bookmark.ok"));
        var cancel = new JButton(I18n.t("sftp.bookmark.cancel"));
        ok.addActionListener(e -> accept());
        cancel.addActionListener(e -> dialog.dispose());
        dialog.getRootPane().setDefaultButton(ok);
        var footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(ok);
        footer.add(cancel);
        return footer;
    }

    private void addTab(String title, UUID host) {
        var model = new DefaultListModel<String>();
        bookmarks.list(request.side(), host).forEach(model::addElement);
        var list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                path.setSelectedItem(list.getSelectedValue());
            }
            refreshButtons();
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && list.getSelectedValue() != null) {
                    accept();
                }
            }
        });
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "removeBookmark");
        list.getActionMap().put("removeBookmark", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                removeSelected();
            }
        });
        lists.add(list);
        tabs.addTab(title, AppIcon.BOOKMARK.icon(), new JScrollPane(list));
    }

    private JList<String> list() {
        return lists.get(tabs.getSelectedIndex());
    }

    private DefaultListModel<String> model() {
        return (DefaultListModel<String>) list().getModel();
    }

    /** Host dari tab yang sedang dipilih: null = bookmark bersama. */
    private UUID host() {
        return tabs.getSelectedIndex() == 0 ? request.host() : null;
    }

    private String typed() {
        return pathField.getText().strip();
    }

    private void refreshButtons() {
        var list = list();
        int row = list.getSelectedIndex();
        int size = list.getModel().getSize();
        String text = typed();
        add.setEnabled(!text.isEmpty() && !model().contains(text));
        remove.setEnabled(row >= 0);
        up.setEnabled(row > 0);
        down.setEnabled(row >= 0 && row < size - 1);
    }

    private void addCurrent() {
        String text = typed();
        if (text.isEmpty() || model().contains(text)) {
            return;
        }
        model().addElement(text);
        list().setSelectedIndex(model().getSize() - 1);
        save();
    }

    private void removeSelected() {
        int row = list().getSelectedIndex();
        if (row < 0) {
            return;
        }
        model().remove(row);
        if (!model().isEmpty()) {
            list().setSelectedIndex(Math.min(row, model().getSize() - 1));
        }
        save();
        refreshButtons();
    }

    private void move(int delta) {
        int row = list().getSelectedIndex();
        int target = row + delta;
        if (row < 0 || target < 0 || target >= model().getSize()) {
            return;
        }
        var items = Collections.list(model().elements());
        Collections.swap(items, row, target);
        model().clear();
        items.forEach(model()::addElement);
        list().setSelectedIndex(target);
        save();
    }

    private void save() {
        bookmarks.set(dialog, request.side(), host(), Collections.list(model().elements()));
    }

    private void browse() {
        var chooser = new JFileChooser(typed().isEmpty() ? null : typed());
        chooser.setDialogTitle(I18n.t("sftp.bookmark.browse.title"));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
            path.setSelectedItem(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void accept() {
        String text = typed();
        if (text.isEmpty()) {
            pathField.requestFocusInWindow();
            return;
        }
        result = text;
        dialog.dispose();
    }
}
