package dev.egateza.termul.app.ui;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.QuickOpenItems.Host;
import dev.egateza.termul.app.ui.QuickOpenItems.Item;
import dev.egateza.termul.app.ui.QuickOpenItems.Local;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Popup Tab baru (⌘T / Ctrl+Shift+T): ketik untuk menyaring, ↑/↓ memilih, Enter membuka terminal, Shift+Enter membuka
 * tab SFTP saja, Esc menutup. Tidak membuka sidebar. Hanya membaca snapshot profil di memori (tanpa I/O). Semua di EDT.
 */
final class QuickOpenPopup extends JDialog {

    /** Aksi yang sudah ada di MainFrame. */
    interface Actions {
        void openLocalProfile(java.util.UUID id);

        void open(HostProfile profile);

        void openSftp(HostProfile profile);

        /** Popup ditutup tanpa memilih: kembalikan fokus ke terminal. */
        void cancelled();
    }

    private static final int WIDTH = 560;
    private static final int VISIBLE_ROWS = 12;

    private final ProfileSnapshot snapshot;
    private final boolean wslEnabled;
    private final java.util.List<dev.egateza.termul.app.terminal.LocalProfiles.Choice> locals;
    private final Actions actions;
    private final JTextField search = new JTextField();
    private final DefaultListModel<Item> model = new DefaultListModel<>();
    private final JList<Item> list = new JList<>(model);
    private boolean done; // EDT; sudah memilih atau dibatalkan (window deactivated ikut terpanggil saat dispose)

    private QuickOpenPopup(Frame owner, ProfileSnapshot snapshot, boolean wslEnabled,
                           java.util.List<dev.egateza.termul.app.terminal.LocalProfiles.Choice> locals, Actions actions) {
        super(owner, false);
        this.snapshot = snapshot;
        this.wslEnabled = wslEnabled;
        this.locals = locals;
        this.actions = actions;
        setUndecorated(true);
        search.putClientProperty("JTextField.placeholderText", I18n.t("quickOpen.placeholder"));
        search.putClientProperty("JTextField.showClearButton", true);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refresh();
            }
        });
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFocusable(false); // ketikan selalu ke field pencarian
        list.setCellRenderer(new Renderer());
        list.setVisibleRowCount(VISIBLE_ROWS);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    choose(e.isShiftDown());
                }
            }
        });
        bind(KeyEvent.VK_UP, 0, "up", () -> move(-1));
        bind(KeyEvent.VK_DOWN, 0, "down", () -> move(1));
        bind(KeyEvent.VK_PAGE_UP, 0, "pageUp", () -> move(-VISIBLE_ROWS));
        bind(KeyEvent.VK_PAGE_DOWN, 0, "pageDown", () -> move(VISIBLE_ROWS));
        bind(KeyEvent.VK_ENTER, 0, "open", () -> choose(false));
        bind(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK, "openSftp", () -> choose(true));
        bind(KeyEvent.VK_ESCAPE, 0, "cancel", this::cancel);

        var hint = new JLabel(I18n.t("quickOpen.hint"));
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        hint.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        var scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        var panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(8, 8, 0, 8)));
        panel.add(search, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(hint, BorderLayout.SOUTH);
        setContentPane(panel);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowDeactivated(WindowEvent e) {
                cancel(); // klik di luar popup
            }
        });
        refresh();
    }

    /** Tampilkan popup di tengah-atas {@code owner}. */
    static void show(Frame owner, ProfileSnapshot snapshot, boolean wslEnabled,
                     java.util.List<dev.egateza.termul.app.terminal.LocalProfiles.Choice> locals, Actions actions) {
        var popup = new QuickOpenPopup(owner, snapshot, wslEnabled, locals, actions);
        popup.pack();
        popup.setSize(new Dimension(WIDTH, popup.getHeight()));
        var o = owner.getBounds();
        popup.setLocation(o.x + (o.width - WIDTH) / 2, o.y + Math.min(80, o.height / 8));
        popup.setVisible(true);
        popup.search.requestFocusInWindow();
    }

    private void refresh() {
        model.clear();
        QuickOpenItems.build(snapshot, search.getText(), wslEnabled, locals).forEach(model::addElement);
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
            list.ensureIndexIsVisible(0);
        }
    }

    private void move(int delta) {
        if (model.isEmpty()) {
            return;
        }
        int next = Math.clamp((long) list.getSelectedIndex() + delta, 0, model.size() - 1);
        list.setSelectedIndex(next);
        list.ensureIndexIsVisible(next);
    }

    /** @param sftpOnly Shift+Enter: tab SFTP saja (tidak berlaku untuk terminal lokal) */
    private void choose(boolean sftpOnly) {
        Item item = list.getSelectedValue();
        if (item == null || done) {
            return;
        }
        done = true;
        dispose();
        switch (item) {
            case Local l -> actions.openLocalProfile(l.choice().id());
            case Host h when sftpOnly -> actions.openSftp(h.profile());
            case Host h -> actions.open(h.profile());
        }
    }

    private void cancel() {
        if (done) {
            return;
        }
        done = true;
        dispose();
        actions.cancelled();
    }

    private void bind(int key, int modifiers, String name, Runnable action) {
        search.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, modifiers), name);
        search.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    /** Baris: ikon OS + nama (titik warna environment) + alamat dan grup abu-abu; ★ untuk Favorites/Last used. */
    private static final class Renderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                      boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            switch ((Item) value) {
                case Local(var c) -> {
                    setIcon(OsIcons.local());
                    String def = c.isDefault() ? " &middot; " + escape(I18n.t("tree.local.default")) : "";
                    setText("<html>" + escape(I18n.t("local.tab.name", c.name())) + " <font color='gray'>"
                            + escape(c.commandLine()) + def + "</font></html>");
                }
                case Host(HostProfile p, boolean pinned) -> {
                    setIcon(OsIcons.forHost(p));
                    var color = EnvColors.of(p.environment());
                    String dot = color == null ? ""
                            : "<font color='#%06x'>&#9679;</font> ".formatted(color.getRGB() & 0xFFFFFF);
                    String group = p.group().isEmpty() ? "" : " &middot; " + escape(p.group());
                    setText("<html>" + (pinned ? "&#9733; " : "") + dot + escape(p.name()) + " <font color='gray'>"
                            + escape(p.address()) + group + "</font></html>");
                }
            }
            return this;
        }

        private static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
