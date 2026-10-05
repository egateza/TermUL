package dev.egateza.termul.app.sftp;

import dev.egateza.termul.app.edit.EditorLauncher;
import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.sftp.ActivityBar.Level;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.Edt;
import dev.egateza.termul.app.ui.Shortcuts;
import dev.egateza.termul.app.ui.UiAsync;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.sftp.RemoteEntry;
import dev.egateza.termul.sftp.RemoteFileService;
import dev.egateza.termul.sftp.SftpConnection;
import dev.egateza.termul.sftp.SftpLinks;
import dev.egateza.termul.sftp.RemotePaths;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.RowFilter;
import javax.swing.table.TableRowSorter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Panel browser direktori remote (SFTP) untuk satu profil. Memakai koneksi SSH yang sama dengan
 * terminal (lease sendiri dari {@link SessionManager}). Semua method dipanggil di EDT; I/O di {@code sshOps}.
 */
public class SftpPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(SftpPanel.class);

    protected final HostProfile profile;
    private final SftpLinks links;
    protected final ExecutorService sshOps;
    protected final SftpTableModel model = new SftpTableModel();
    protected final JTable table = new JTable(model);
    protected final JTextField pathField = new JTextField();
    /** Filter nama untuk folder yang sedang dibuka (client-side, tanpa request ke server). */
    private final JTextField filterField = new JTextField();
    private final TableRowSorter<SftpTableModel> sorter = new TableRowSorter<>(model);
    /** Keterangan aktivitas panel ini (upload, download, operasi file, reconnect, edit remote). */
    protected final ActivityBar activity = new ActivityBar();
    protected final JToolBar toolbar = new JToolBar();
    private volatile SftpConnection link;        // koneksi bersama profil; ditulis sekali setelah connect
    private Runnable unsubscribeLink = () -> { }; // EDT
    private Runnable unsubscribeEdits = () -> { }; // EDT
    private boolean disposed;                    // EDT
    protected String currentDir;                 // EDT
    private boolean connecting;                  // EDT
    private SftpConnection pendingLink;          // EDT; slot yang sudah diambil selagi connect masih berjalan

    private final EditActions editActions;
    private final SystemFileIcons fileIcons;

    /** Hubungan panel ke editor lokal (diwujudkan di modul wiring). */
    public interface EditActions {
        /**
         * @param command template editor pilihan user, {@link EditorLauncher#SYSTEM_DEFAULT} (aplikasi default
         *                Windows), atau null = editor default sesuai ekstensi
         */
        void edit(HostProfile profile, String remotePath, String command);

        /** Edit sebagai root: perubahan dipasang lewat sudo (backup, validasi, rollback). */
        void editAsRoot(HostProfile profile, String remotePath);

        /** Editor bernama untuk submenu "Edit dengan"; dibaca setiap menu dibuka. */
        java.util.List<dev.egateza.termul.core.config.EditorConfig.NamedEditor> editors();

        /** Buka dialog pengaturan editor. */
        void configure();

        /**
         * Berlangganan keterangan aktivitas edit remote untuk profil ini (buka, upload mulai/selesai, error).
         *
         * @return pemanggil untuk berhenti berlangganan
         */
        Runnable onActivity(java.util.UUID profileId, java.util.function.BiConsumer<Level, String> sink);
    }

    public SftpPanel(HostProfile profile, SftpLinks links, ExecutorService sshOps, EditActions editActions,
                     SystemFileIcons fileIcons) {
        super(new BorderLayout());
        this.profile = profile;
        this.links = links;
        this.sshOps = sshOps;
        this.editActions = editActions;
        this.fileIcons = fileIcons;

        toolbar.setFloatable(false);
        toolbar.add(button(AppIcon.ARROW_UP, null, I18n.t("sftp.toolbar.up"), this::goUp));
        toolbar.add(button(AppIcon.HOME, null, I18n.t("sftp.toolbar.home"), this::goHome));
        toolbar.add(button(AppIcon.REFRESH, null, I18n.t("sftp.toolbar.refresh"), this::refresh));
        toolbar.addSeparator();

        pathField.addActionListener(e -> navigate(pathField.getText().strip()));
        var top = new JPanel(new BorderLayout());
        top.add(toolbar, BorderLayout.NORTH);
        top.add(pathField, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        table.setAutoCreateRowSorter(false);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        for (int c = 0; c < model.getColumnCount(); c++) {
            sorter.setComparator(c, SftpTableModel.comparator(c));
        }
        table.setRowSorter(sorter);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus,
                                                           int row, int column) {
                super.getTableCellRendererComponent(t, value, sel, focus, row, column);
                int modelCol = t.convertColumnIndexToModel(column);
                setText(value instanceof RemoteEntry e ? SftpTableModel.display(e, modelCol) : "");
                setIcon(modelCol == SftpTableModel.COL_NAME && value instanceof RemoteEntry e ? entryIcon(e) : null);
                setHorizontalAlignment(modelCol == SftpTableModel.COL_SIZE ? RIGHT : LEFT);
                return this;
            }
        });
        int[] widths = {220, 70, 120, 80, 70, 70};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    selectedEntries().stream().findFirst().ifPresent(SftpPanel.this::activate);
                }
            }
        });
        table.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (!e.isControlDown() && !e.isAltDown() && !e.isMetaDown() && jumpTo(e.getKeyChar(), e.getWhen())) {
                    e.consume();
                }
            }
        });
        bind(KeyEvent.VK_ENTER, 0, "open", () -> selectedEntries().stream().findFirst().ifPresent(this::activate));
        bind(KeyEvent.VK_BACK_SPACE, 0, "up", this::goUp);
        bind(KeyEvent.VK_F5, 0, "refresh", this::refresh);

        toolbar.add(button(AppIcon.UPLOAD, I18n.t("sftp.toolbar.upload"), I18n.t("sftp.toolbar.upload.tip",
                dev.egateza.termul.core.Os.current().isMac() ? "Finder" : "Explorer"), this::chooseUpload));
        toolbar.add(button(AppIcon.DOWNLOAD, I18n.t("sftp.toolbar.download"), I18n.t("sftp.toolbar.download.tip"), this::downloadSelected));
        toolbar.addSeparator();
        toolbar.add(button(AppIcon.FOLDER_PLUS, null, I18n.t("sftp.toolbar.mkdir.tip"), this::mkdir));
        toolbar.add(button(AppIcon.PEN, null, I18n.t("sftp.toolbar.rename.tip"), this::renameSelected));
        toolbar.add(button(AppIcon.PERMISSION, null, I18n.t("sftp.toolbar.chmod.tip"), this::chmodSelected));
        toolbar.add(button(AppIcon.TRASH, null, I18n.t("sftp.toolbar.delete.tip"), this::deleteSelected));
        bind(KeyEvent.VK_F2, 0, "rename", this::renameSelected);
        bind(KeyEvent.VK_F7, 0, "mkdir", this::mkdir);
        bind(KeyEvent.VK_DELETE, 0, "delete", this::deleteSelected);
        bind(KeyEvent.VK_ESCAPE, 0, "clearFilter", () -> filterField.setText(""));
        installFilter();
        table.setComponentPopupMenu(buildPopup());

        var scroll = new JScrollPane(table);
        var dnd = new FileDropHandler();
        table.setTransferHandler(dnd);
        scroll.setTransferHandler(dnd);
        add(scroll, BorderLayout.CENTER);

        transfers = new TransferQueue(profile.name());
        var bottom = new JPanel(new BorderLayout());
        bottom.add(transfers, BorderLayout.NORTH);
        bottom.add(activity, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
    }

    private final TransferQueue transfers;

    /**
     * Kolom filter di ujung kanan toolbar. Ctrl/Cmd+F dipasang sebagai binding ancestor panel ini, sehingga
     * mengalahkan accelerator menu "Cari host" hanya selama fokus ada di dalam panel SFTP.
     */
    private void installFilter() {
        toolbar.add(javax.swing.Box.createHorizontalGlue());
        filterField.putClientProperty("JTextField.placeholderText", I18n.t("sftp.filter.placeholder"));
        filterField.putClientProperty("JTextField.showClearButton", true);
        filterField.setToolTipText(I18n.t("sftp.filter.tip", Shortcuts.text(Shortcuts.menu(KeyEvent.VK_F))));
        filterField.setColumns(14);
        filterField.setMaximumSize(filterField.getPreferredSize());
        filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }
        });
        toolbar.add(filterField);

        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(Shortcuts.menu(KeyEvent.VK_F), "focusFilter");
        getActionMap().put("focusFilter", action(() -> {
            filterField.requestFocusInWindow();
            filterField.selectAll();
        }));
        // Esc: kosongkan filter, atau kembali ke tabel kalau sudah kosong. Enter/↓: lanjut pilih di tabel.
        filterField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearOrBack");
        filterField.getActionMap().put("clearOrBack", action(() -> {
            if (filterField.getText().isEmpty()) {
                table.requestFocusInWindow();
            } else {
                filterField.setText("");
            }
        }));
        filterField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "toTable");
        filterField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "toTable");
        filterField.getActionMap().put("toTable", action(() -> {
            if (table.getRowCount() > 0 && table.getSelectedRow() < 0) {
                table.setRowSelectionInterval(0, 0);
            }
            table.requestFocusInWindow();
        }));
    }

    private final TypeAhead typeAhead = new TypeAhead();

    /** Type-ahead: pilih baris pertama (urutan tampilan, sesudah filter) yang namanya berawalan teks yang diketik. */
    private boolean jumpTo(char c, long when) {
        String text = typeAhead.type(c, when);
        if (text == null) {
            return false;
        }
        var names = new ArrayList<String>(table.getRowCount());
        for (int row = 0; row < table.getRowCount(); row++) {
            names.add(model.entryAt(table.convertRowIndexToModel(row)).name());
        }
        int row = TypeAhead.find(names, table.getSelectionModel().getLeadSelectionIndex(), text);
        if (row >= 0) {
            table.setRowSelectionInterval(row, row);
            table.scrollRectToVisible(table.getCellRect(row, 0, true));
        }
        return true;
    }

    private void applyFilter() {
        var match = SftpTableModel.nameFilter(filterField.getText());
        sorter.setRowFilter(match == null ? null : new RowFilter<SftpTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends SftpTableModel, ? extends Integer> entry) {
                return match.test(model.entryAt(entry.getIdentifier()));
            }
        });
    }

    private static AbstractAction action(Runnable action) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        };
    }

    private static final javax.swing.Icon DIR_ICON = AppIcon.FOLDER.icon(AppIcon.SIZE, () -> AppIcon.FOLDER_COLOR);
    private static final javax.swing.Icon FILE_ICON = AppIcon.FILE.icon();
    private static final javax.swing.Icon LINK_ICON = AppIcon.LINK.icon();

    /** File biasa memakai icon association Windows (seperti Explorer); folder & link tetap SVG aplikasi. */
    private javax.swing.Icon entryIcon(RemoteEntry e) {
        return switch (e.type()) {
            case DIRECTORY -> DIR_ICON;
            case SYMLINK -> LINK_ICON;
            case FILE -> {
                var system = fileIcons.icon(e.name(), table::repaint);
                yield system != null ? system : FILE_ICON;
            }
            case OTHER -> FILE_ICON;
        };
    }

    private JPopupMenu buildPopup() {
        var menu = new JPopupMenu();
        var open = menuItem(I18n.t("sftp.menu.open"), () -> editSelected(EditorLauncher.SYSTEM_DEFAULT));
        open.setFont(open.getFont().deriveFont(java.awt.Font.BOLD)); // aksi double-click
        var edit = menuItem(I18n.t("sftp.menu.edit"), () -> editSelected(null));
        var editWith = new javax.swing.JMenu(I18n.t("sftp.menu.editWith"));
        var editAsRoot = menuItem(I18n.t("sftp.menu.editAsRoot"), this::editSelectedAsRoot);
        menu.add(open);
        menu.add(edit);
        menu.add(editWith);
        menu.add(editAsRoot);
        menu.addSeparator();
        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                selectRowUnderPointer();
                boolean file = selectedEntries().stream().anyMatch(en -> en.type() == RemoteEntry.Type.FILE);
                open.setEnabled(file);
                edit.setEnabled(file);
                editWith.setEnabled(file);
                editAsRoot.setEnabled(file);
                rebuildEditWith(editWith);
            }

            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
            }
        });
        menu.add(menuItem(I18n.t("sftp.menu.download"), this::downloadSelected));
        menu.add(menuItem(I18n.t("sftp.menu.uploadHere"), this::chooseUpload));
        menu.addSeparator();
        menu.add(menuItem(I18n.t("sftp.menu.rename"), this::renameSelected));
        menu.add(menuItem(I18n.t("sftp.menu.chmod"), this::chmodSelected));
        menu.add(menuItem(I18n.t("sftp.menu.delete"), this::deleteSelected));
        menu.addSeparator();
        menu.add(menuItem(I18n.t("sftp.menu.mkdir"), this::mkdir));
        menu.add(menuItem(I18n.t("sftp.menu.copyPath"), () -> selectedEntries().stream().findFirst().ifPresent(e ->
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(e.path()), null))));
        menu.add(menuItem(I18n.t("sftp.menu.refresh"), this::refresh));
        return menu;
    }

    /** Klik kanan pada baris yang belum terpilih memilih baris itu dulu (perilaku file manager). */
    private void selectRowUnderPointer() {
        var pointer = java.awt.MouseInfo.getPointerInfo();
        if (pointer == null || !table.isShowing()) {
            return;
        }
        var p = pointer.getLocation();
        javax.swing.SwingUtilities.convertPointFromScreen(p, table);
        int row = table.rowAtPoint(p);
        if (row >= 0 && !table.isRowSelected(row)) {
            table.setRowSelectionInterval(row, row);
        }
    }

    private void rebuildEditWith(javax.swing.JMenu menu) {
        menu.removeAll();
        for (var editor : editActions.editors()) {
            menu.add(menuItem(editor.name(), () -> editSelected(editor.command())));
        }
        if (menu.getItemCount() > 0) {
            menu.addSeparator();
        }
        menu.add(menuItem(I18n.t("sftp.menu.configureEditors"), editActions::configure));
    }

    private static JMenuItem menuItem(String label, Runnable action) {
        var item = new JMenuItem(label);
        item.addActionListener(e -> action.run());
        return item;
    }

    // ------------------------------------------------------------------ transfer

    private void chooseUpload() {
        if (link == null || currentDir == null) {
            return;
        }
        var chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle(I18n.t("sftp.upload.chooserTitle", currentDir));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            upload(List.of(chooser.getSelectedFiles()).stream().map(File::toPath).toList());
        }
    }

    /** Upload file ke direktori saat ini. Direktori lokal dilewati (belum didukung). */
    public void upload(List<Path> files) {
        if (link == null || currentDir == null) {
            return;
        }
        String dir = currentDir;
        var skipped = new ArrayList<String>();
        for (Path file : files) {
            if (!Files.isRegularFile(file)) {
                skipped.add(file.getFileName().toString());
                continue;
            }
            String target = RemotePaths.join(dir, file.getFileName().toString());
            String name = file.getFileName().toString();
            var existsSkipped = new AtomicBoolean();
            activity.report(Level.INFO, I18n.t("sftp.upload.queued", name, dir));
            transfers.submit(I18n.t("sftp.upload.title", name), listener -> {
                activity.report(Level.INFO, I18n.t("sftp.upload.running", name, dir));
                link.execute(() -> {
                    if (svc().exists(target) && !confirmOverwrite(target)) {
                        existsSkipped.set(true);
                        return null;
                    }
                    svc().upload(file, target, null, listener);
                    return null;
                });
            }, () -> {
                if (existsSkipped.get()) {
                    activity.report(Level.WARN, I18n.t("sftp.upload.skipped", name));
                    return;
                }
                activity.report(Level.SUCCESS, I18n.t("sftp.upload.done", name, dir));
                if (dir.equals(currentDir)) {
                    refresh();
                }
            }, err -> {
                activity.report(Level.ERROR, I18n.t("sftp.upload.failed", name, err.getMessage()));
                Dialogs.error(this, I18n.t("sftp.upload.failedTitle"), err);
            }, () -> activity.report(Level.WARN, I18n.t("sftp.upload.cancelled", name)));
        }
        if (!skipped.isEmpty()) {
            Dialogs.info(this, I18n.t("sftp.upload.infoTitle"), I18n.t("sftp.upload.dirUnsupported", String.join(", ", skipped)));
        }
    }

    private boolean confirmOverwrite(String target) {
        var ok = new AtomicBoolean();
        Edt.runAndWait(() -> ok.set(Dialogs.confirm(this, I18n.t("sftp.overwrite.title"), I18n.t("sftp.overwrite.confirm", target))));
        return ok.get();
    }

    private void downloadSelected() {
        var selected = selectedEntries().stream().filter(e -> e.type() == RemoteEntry.Type.FILE).toList();
        if (selected.isEmpty()) {
            return;
        }
        var chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(I18n.t("sftp.download.chooserTitle", String.valueOf(selected.size())));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path targetDir = chooser.getSelectedFile().toPath();
        for (RemoteEntry entry : selected) {
            Path target = targetDir.resolve(entry.name());
            var skipped = new AtomicBoolean();
            activity.report(Level.INFO, I18n.t("sftp.download.queued", entry.name()));
            transfers.submit(I18n.t("sftp.download.title", entry.name()), listener -> {
                activity.report(Level.INFO, I18n.t("sftp.download.running", entry.path()));
                link.execute(() -> {
                    if (Files.exists(target) && !confirmOverwrite(target.toString())) {
                        skipped.set(true);
                        return null;
                    }
                    svc().download(entry.path(), target, listener);
                    return null;
                });
            }, () -> activity.report(skipped.get() ? Level.WARN : Level.SUCCESS,
                    skipped.get() ? I18n.t("sftp.download.skipped", entry.name())
                            : I18n.t("sftp.download.done", entry.name(), target)),
                    err -> {
                        activity.report(Level.ERROR, I18n.t("sftp.download.failed", entry.name(), err.getMessage()));
                        Dialogs.error(this, I18n.t("sftp.download.failedTitle"), err);
                    }, () -> activity.report(Level.WARN, I18n.t("sftp.download.cancelled", entry.name())));
        }
    }

    // ------------------------------------------------------------------ operasi file

    private void mkdir() {
        if (link == null || currentDir == null) {
            return;
        }
        String name = Dialogs.input(this, I18n.t("sftp.mkdir.title"), I18n.t("sftp.mkdir.prompt", currentDir), "");
        if (name != null) {
            String path = RemotePaths.join(currentDir, name);
            call(I18n.t("sftp.mkdir.doing", path), I18n.t("sftp.mkdir.done", path), () -> {
                svc().mkdir(path);
                return path;
            }, p -> refresh());
        }
    }

    private void renameSelected() {
        var selected = selectedEntries();
        if (selected.size() != 1) {
            return;
        }
        RemoteEntry entry = selected.getFirst();
        String name = Dialogs.input(this, I18n.t("sftp.rename.title"), I18n.t("sftp.rename.prompt", entry.name()), entry.name());
        if (name != null && !name.equals(entry.name())) {
            String target = RemotePaths.join(RemotePaths.parent(entry.path()), name);
            call(I18n.t("sftp.rename.doing", entry.name(), name),
                    I18n.t("sftp.rename.done", entry.name(), name), () -> {
                        svc().rename(entry.path(), target);
                        return target;
                    }, t -> refresh());
        }
    }

    private void chmodSelected() {
        var selected = selectedEntries();
        if (selected.isEmpty()) {
            return;
        }
        String initial = String.format("%04o", selected.getFirst().mode());
        String text = Dialogs.input(this, I18n.t("sftp.chmod.title"), I18n.t("sftp.chmod.prompt", String.valueOf(selected.size())), initial);
        if (text == null) {
            return;
        }
        int mode;
        try {
            mode = Formats.parseMode(text);
        } catch (IllegalArgumentException e) {
            Dialogs.error(this, I18n.t("sftp.chmod.title"), e.getMessage());
            return;
        }
        String modeText = String.format("%04o", mode);
        call(I18n.t("sftp.chmod.doing", String.valueOf(selected.size()), modeText),
                I18n.t("sftp.chmod.done", String.valueOf(selected.size()), modeText), () -> {
                    for (RemoteEntry e : selected) {
                        svc().chmod(e.path(), mode);
                    }
                    return selected.size();
                }, n -> refresh());
    }

    private void deleteSelected() {
        var selected = selectedEntries();
        if (selected.isEmpty()) {
            return;
        }
        long dirs = selected.stream().filter(RemoteEntry::isDirectory).count();
        String what = selected.size() == 1 ? selected.getFirst().path() : I18n.t("sftp.delete.items", String.valueOf(selected.size()));
        String warning = dirs > 0 ? I18n.t("sftp.delete.warning") : "";
        if (!Dialogs.confirm(this, I18n.t("sftp.delete.title"), I18n.t("sftp.delete.confirm", what, warning))) {
            return;
        }
        call(I18n.t("sftp.delete.doing", what), I18n.t("sftp.delete.done", what), () -> {
            for (RemoteEntry e : selected) {
                svc().delete(e.path(), e.isDirectory());
            }
            return selected.size();
        }, n -> refresh());
    }

    /** Drag & drop file dari Windows Explorer → upload ke direktori saat ini. */
    private final class FileDropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return link != null && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                @SuppressWarnings("unchecked")
                var files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                upload(files.stream().map(File::toPath).toList());
                return true;
            } catch (UnsupportedFlavorException | IOException e) {
                log.warn("Drop gagal", e);
                return false;
            }
        }
    }

    /** Connect (sekali) lalu buka direktori awal profil atau home. */
    public void connect() {
        if (link != null || connecting || disposed) {
            return;
        }
        connecting = true;
        activity.progress(I18n.t("sftp.connect.opening", profile.address()));
        // slot diambil di sini (tanpa I/O) supaya dispose() bisa melepasnya walaupun connect belum selesai:
        // kalau panel ini pemakai terakhir, koneksi ditutup dan connect yang berjalan ikut dihentikan
        SftpConnection l = links.acquire(profile);
        pendingLink = l;
        unsubscribeLink = l.addListener(this::onLinkStatus);
        UiAsync.run(sshOps, () -> {
            try {
                var svc = l.ensureLive();
                return profile.initialDirectory() != null ? svc.canonicalize(profile.initialDirectory()) : svc.home();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, start -> {
            connecting = false;
            if (disposed) { // tab ditutup selagi menyambung: slot sudah dilepas di dispose()
                return;
            }
            pendingLink = null;
            link = l;
            unsubscribeEdits = editActions.onActivity(profile.id(), activity::report);
            navigate(start);
        }, err -> {
            connecting = false;
            if (disposed) {
                return;
            }
            pendingLink = null;
            unsubscribeLink.run();
            unsubscribeLink = () -> { };
            sshOps.execute(() -> links.release(profile));
            activity.report(Level.ERROR, I18n.t("sftp.connect.failed", err.getMessage()));
        });
    }

    /**
     * Status koneksi SFTP, yang mengikuti sesi terminal tab ini (dipanggil bukan di EDT). Reconnect dilakukan dari
     * terminal; panel hanya menampilkan status dan memuat ulang daftar begitu tersambung kembali.
     */
    private void onLinkStatus(SftpConnection.Status st) {
        switch (st.state()) {
            case CONNECTED -> {
                boolean again = link != null;
                activity.report(Level.SUCCESS, again ? st.message() : I18n.t("sftp.connect.connected", profile.address()));
                if (again) {
                    SwingUtilities.invokeLater(this::refresh);
                }
            }
            case WAITING -> activity.report(Level.WARN, st.message());
            case DISCONNECTED -> activity.report(Level.ERROR, st.message());
            case IDLE, CLOSED -> { }
        }
    }

    private RemoteFileService svc() {
        return link.service();
    }

    public int activeTransfers() {
        return transfers.activeCount();
    }

    public boolean isConnected() {
        return link != null;
    }

    /** true kalau SFTP pernah tersambung tetapi kanalnya sekarang mati (koneksi putus). Tanpa I/O. */
    public boolean isChannelDead() {
        var l = link;
        return l != null && !l.service().isOpen();
    }

    public String currentDir() {
        return currentDir;
    }

    public void navigate(String dir) {
        if (link == null || dir.isEmpty()) {
            return;
        }
        activity.progress(I18n.t("sftp.navigate.loading", dir));
        call(() -> {
            String canonical = svc().canonicalize(dir);
            return new Listing(canonical, svc().list(canonical));
        }, listing -> {
            if (!listing.dir().equals(currentDir)) {
                filterField.setText(""); // filter hanya berlaku untuk folder tempat ia diketik; refresh mempertahankannya
            }
            currentDir = listing.dir();
            pathField.setText(listing.dir());
            model.setEntries(listing.entries());
            activity.report(Level.INFO, I18n.t("sftp.navigate.count", listing.dir(), String.valueOf(listing.entries().size())));
        });
    }

    private record Listing(String dir, List<RemoteEntry> entries) {
    }

    public void refresh() {
        if (currentDir != null) {
            navigate(currentDir);
        }
    }

    public void goUp() {
        if (currentDir != null) {
            navigate(RemotePaths.parent(currentDir));
        }
    }

    public void goHome() {
        if (link != null) {
            call(() -> svc().home(), this::navigate);
        }
    }

    /** Double-click / Enter: direktori dibuka; file ditangani {@link #openFile}. */
    protected void activate(RemoteEntry entry) {
        if (entry.isDirectory() || entry.type() == RemoteEntry.Type.SYMLINK) {
            navigate(entry.path());
        } else {
            openFile(entry);
        }
    }

    /**
     * Membuka file dengan aplikasi default Windows (auto-upload saat disimpan). File executable / tanpa ekstensi
     * dibuka dengan editor dari pengaturan (lihat {@link EditorLauncher}).
     */
    protected void openFile(RemoteEntry entry) {
        editActions.edit(profile, entry.path(), EditorLauncher.SYSTEM_DEFAULT);
    }

    private void editSelected(String command) {
        selectedEntries().stream().filter(e -> e.type() == RemoteEntry.Type.FILE).findFirst().ifPresent(entry -> {
            editActions.edit(profile, entry.path(), command);
        });
    }

    private void editSelectedAsRoot() {
        selectedEntries().stream().filter(e -> e.type() == RemoteEntry.Type.FILE).findFirst()
                .ifPresent(entry -> editActions.editAsRoot(profile, entry.path()));
    }

    protected List<RemoteEntry> selectedEntries() {
        var result = new ArrayList<RemoteEntry>();
        for (int viewRow : table.getSelectedRows()) {
            result.add(model.entryAt(table.convertRowIndexToModel(viewRow)));
        }
        return result;
    }

    /**
     * Menjalankan operasi remote di {@code sshOps} lewat {@link SftpConnection#execute}: kalau koneksi terputus,
     * operasi menunggu penyambungan ulang bertahap dulu. Error ditampilkan sebagai dialog dan di baris aktivitas.
     */
    protected <T> void call(Callable<T> work, Consumer<T> onSuccess) {
        call(null, null, work, onSuccess);
    }

    /** @param doing keterangan saat mulai (null = tanpa); @param done keterangan sukses (null = tanpa) */
    protected <T> void call(String doing, String done, Callable<T> work, Consumer<T> onSuccess) {
        if (doing != null) {
            activity.report(Level.INFO, doing + " ...");
        }
        var l = link;
        UiAsync.run(sshOps, () -> {
            try {
                return l.execute(work);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, result -> {
            if (done != null) {
                activity.report(Level.SUCCESS, done);
            }
            onSuccess.accept(result);
        }, err -> {
            activity.report(Level.ERROR, (doing != null ? I18n.t("sftp.call.failedDoing", doing, err.getMessage()) : I18n.t("sftp.call.failed", err.getMessage())));
            log.info("Operasi SFTP gagal: {}", err.getMessage());
            Dialogs.error(this, "SFTP", err);
        });
    }

    /** Tombol toolbar; {@code text} null = hanya ikon. */
    protected static JButton button(AppIcon icon, String text, String tooltip, Runnable action) {
        var b = new JButton(text, icon.icon());
        b.setToolTipText(tooltip);
        b.setFocusable(false);
        b.addActionListener(e -> action.run());
        return b;
    }

    protected void bind(int key, int modifiers, String name, Runnable action) {
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, modifiers), name);
        table.getActionMap().put(name, action(action));
    }

    /** Menutup SFTP (tab ditutup). Koneksi bersama baru ditutup kalau tidak dipakai lagi (mis. sesi edit). */
    public void dispose() {
        disposed = true;
        transfers.shutdown();
        unsubscribeLink.run();
        unsubscribeEdits.run();
        var l = link != null ? link : pendingLink; // pendingLink: masih connect, release menghentikannya
        link = null;
        pendingLink = null;
        if (l != null) {
            sshOps.execute(() -> links.release(profile));
        }
    }
}
