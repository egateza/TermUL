package dev.egateza.termul.app.sftp;

import dev.egateza.termul.app.edit.EditorLauncher;
import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.sftp.ActivityBar.Level;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.OverflowToolBar;
import dev.egateza.termul.app.ui.fab.FloatingOverlay;
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
import dev.egateza.termul.sftp.TreeTransfer;
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
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
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
    /** Semua panel SFTP yang belum ditutup, untuk menu "Upload ke" dan drag & drop antar-panel. EDT. */
    private static final List<SftpPanel> OPEN_PANELS = new ArrayList<>();
    private final SftpLinks links;
    protected final ExecutorService sshOps;
    protected final SftpTableModel model = new SftpTableModel();
    protected final JTable table = new JTable(model) {
        @Override
        protected void processMouseEvent(MouseEvent e) {
            if (e.getID() == MouseEvent.MOUSE_PRESSED) {
                // diputuskan sebelum UI tabel memproses tekan: drag = transfer kalau ada tujuan, selain itu pilih baris
                setDragEnabled(dragTargetAvailable());
            }
            super.processMouseEvent(e);
        }
    };
    protected final JTextField pathField = new JTextField();
    /** Filter nama untuk folder yang sedang dibuka (client-side, tanpa request ke server). */
    private final JTextField filterField = new JTextField();
    private final TableRowSorter<SftpTableModel> sorter = new TableRowSorter<>(model);
    /** Keterangan aktivitas panel ini (upload, download, operasi file, reconnect, edit remote). */
    protected final ActivityBar activity = new ActivityBar();
    /** Item yang tidak muat saat panel sempit masuk ke tombol » di ujung kanan. */
    protected final OverflowToolBar toolbar = new OverflowToolBar();
    private volatile SftpConnection link;        // koneksi bersama profil; ditulis sekali setelah connect
    private Runnable unsubscribeLink = () -> { }; // EDT
    private Runnable unsubscribeEdits = () -> { }; // EDT
    private boolean disposed;                    // EDT
    protected String currentDir;                 // EDT
    private boolean connecting;                  // EDT
    private SftpConnection pendingLink;          // EDT; slot yang sudah diambil selagi connect masih berjalan

    private final EditActions editActions;
    private final SystemFileIcons fileIcons;
    private final Consumer<Boolean> layoutChanged;
    /** Isi panel: panel remote saja, atau split lokal | remote; tiap panel membawa toolbar + path sendiri. */
    private final JPanel body = new JPanel(new BorderLayout());
    /** Toolbar + path + tabel remote. */
    private final JPanel remotePane = new JPanel(new BorderLayout());
    private JButton uploadButton;   // disembunyikan di dua panel: upload ada di toolbar panel lokal
    private JButton downloadButton; // dua panel: hanya ikon supaya toolbar muat di setengah lebar
    private final JToggleButton dualToggle = new JToggleButton(AppIcon.FOLDER_OPEN.icon());
    private LocalFilePane local;          // EDT; null = belum pernah tampil dua panel
    private JSplitPane dualSplit;         // EDT; null = tampilan satu panel

    /** Hubungan panel ke editor lokal (diwujudkan di modul wiring). */
    public interface EditActions {
        /**
         * @param command template editor pilihan user, {@link EditorLauncher#SYSTEM_DEFAULT} (aplikasi default
         *                Windows), atau null = editor default sesuai ekstensi
         */
        void edit(HostProfile profile, String remotePath, String command);

        /** Lihat saja: salinan sementara dibuka dengan aplikasi default file; perubahan tidak pernah di-upload. */
        void view(HostProfile profile, String remotePath);

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

    /**
     * @param dual          mulai dengan tampilan dua panel (file lokal di kiri)
     * @param layoutChanged dipanggil di EDT saat user mengganti tampilan lewat tombol toolbar (true = dua panel)
     */
    public SftpPanel(HostProfile profile, SftpLinks links, ExecutorService sshOps, EditActions editActions,
                     SystemFileIcons fileIcons, boolean dual, Consumer<Boolean> layoutChanged) {
        super(new BorderLayout());
        OPEN_PANELS.add(this);
        this.profile = profile;
        this.links = links;
        this.sshOps = sshOps;
        this.editActions = editActions;
        this.fileIcons = fileIcons;
        this.layoutChanged = layoutChanged;

        toolbar.add(button(AppIcon.ARROW_UP, null, I18n.t("sftp.toolbar.up"), this::goUp));
        toolbar.add(button(AppIcon.HOME, null, I18n.t("sftp.toolbar.home"), this::goHome));
        toolbar.add(button(AppIcon.REFRESH, null, I18n.t("sftp.toolbar.refresh"), this::refresh));
        toolbar.addSeparator();

        pathField.addActionListener(e -> navigate(pathField.getText().strip()));
        var header = new JPanel(new BorderLayout());
        header.add(toolbar, BorderLayout.NORTH);
        header.add(pathField, BorderLayout.SOUTH);
        remotePane.add(header, BorderLayout.NORTH);

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
        SftpColumns.install(table, SftpTableModel.COLUMN_IDS);
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

        toolbar.add(uploadButton = button(AppIcon.UPLOAD, I18n.t("sftp.toolbar.upload"), I18n.t("sftp.toolbar.upload.tip",
                dev.egateza.termul.core.Os.current().isMac() ? "Finder" : "Explorer"), this::chooseUpload));
        toolbar.add(downloadButton = button(AppIcon.DOWNLOAD, I18n.t("sftp.toolbar.download"), I18n.t("sftp.toolbar.download.tip"), this::downloadSelected));
        toolbar.addSeparator();
        toolbar.add(button(AppIcon.FOLDER_PLUS, null, I18n.t("sftp.toolbar.mkdir.tip"), this::mkdir));
        toolbar.add(button(AppIcon.PEN, null, I18n.t("sftp.toolbar.rename.tip"), this::renameSelected));
        toolbar.add(button(AppIcon.PERMISSION, null, I18n.t("sftp.toolbar.chmod.tip"), this::chmodSelected));
        toolbar.add(button(AppIcon.TRASH, null, I18n.t("sftp.toolbar.delete.tip"), this::deleteSelected));
        toolbar.addSeparator();
        dualToggle.setToolTipText(I18n.t("sftp.toolbar.dual.tip"));
        dualToggle.setFocusable(false);
        dualToggle.addActionListener(e -> {
            setDualPane(dualToggle.isSelected());
            layoutChanged.accept(dualToggle.isSelected());
        });
        toolbar.add(dualToggle);
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

        transfers = new TransferQueue(profile.name());
        var tableArea = new FloatingOverlay();
        tableArea.setBase(scroll);
        tableArea.setMargins(24, 16); // jangan menutupi scrollbar tabel
        transferBadge = new TransferBadge(transfers, tableArea);
        remotePane.add(tableArea, BorderLayout.CENTER);
        remotePane.add(new SelectionStatus(table, row -> model.entryAt(row).isDirectory(),
                row -> model.entryAt(row).size()), BorderLayout.SOUTH);
        body.add(remotePane, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        // garis pemisah antara info seleksi di bawah tabel dan antrean transfer + log aktivitas
        var bottom = new JPanel(new BorderLayout()) {
            @Override
            public void updateUI() {
                super.updateUI();
                setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Component.borderColor")));
            }
        };
        bottom.add(transfers, BorderLayout.NORTH);
        bottom.add(activity, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
        setDualPane(dual);
    }

    private final TransferQueue transfers;
    private final TransferBadge transferBadge;

    private Consumer<RemoteFileService> connectedListener = svc -> { }; // EDT

    /** Dipanggil (di EDT) saat panel pertama kali tersambung, mis. untuk mendeteksi OS host lewat SFTP. */
    public void setConnectedListener(Consumer<RemoteFileService> listener) {
        this.connectedListener = listener;
    }

    /** Property change (Boolean) yang dikirim saat tampilan satu/dua panel berganti. */
    public static final String DUAL_PANE_PROPERTY = "dualPane";

    /** Menampilkan/menyembunyikan panel file lokal di kiri (ala WinSCP). Panel lokal dibuat saat pertama dibutuhkan. */
    public void setDualPane(boolean on) {
        dualToggle.setSelected(on);
        uploadButton.setVisible(!on);
        downloadButton.setText(on ? null : I18n.t("sftp.toolbar.download"));
        downloadButton.setToolTipText(I18n.t(on ? "sftp.toolbar.download.dualTip" : "sftp.toolbar.download.tip"));
        if (on == (dualSplit != null)) {
            return;
        }
        body.removeAll();
        if (on) {
            if (local == null) {
                local = new LocalFilePane(fileIcons, this::upload);
            }
            dualSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, local, remotePane);
            dualSplit.setContinuousLayout(true);
            dualSplit.setResizeWeight(0.5);
            body.add(dualSplit, BorderLayout.CENTER);
            SwingUtilities.invokeLater(() -> {
                if (dualSplit != null) {
                    dualSplit.setDividerLocation(0.5);
                }
            });
        } else {
            dualSplit = null;
            body.add(remotePane, BorderLayout.CENTER);
        }
        body.revalidate();
        body.repaint();
        firePropertyChange(DUAL_PANE_PROPERTY, !on, on);
    }

    public boolean isDualPane() {
        return dualSplit != null;
    }

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
            toolbar.reveal(filterField);
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
        var view = menuItem(I18n.t("sftp.menu.view"), this::viewSelected);
        view.setToolTipText(I18n.t("sftp.menu.view.tip"));
        menu.add(open);
        menu.add(view);
        menu.add(edit);
        menu.add(editWith);
        if (!profile.isFtp()) { // sudo butuh exec lewat SSH
            menu.add(editAsRoot);
        }
        menu.addSeparator();
        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                selectRowUnderPointer();
                boolean file = selectedEntries().stream().anyMatch(SftpPanel::maybeFile);
                open.setEnabled(file);
                view.setEnabled(file);
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
        var uploadTo = new javax.swing.JMenu(I18n.t("sftp.menu.uploadTo"));
        uploadTo.setToolTipText(I18n.t("sftp.menu.uploadTo.tip"));
        menu.add(uploadTo);
        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                rebuildUploadTo(uploadTo);
            }

            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
            }
        });
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
        if (isDualPane() && !local.selectedPaths().isEmpty()) {
            upload(local.selectedPaths()); // dua panel: yang dipilih di panel lokal, tanpa dialog
            return;
        }
        var chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setDialogTitle(I18n.t("sftp.upload.chooserTitle", currentDir));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            upload(List.of(chooser.getSelectedFiles()).stream().map(File::toPath).toList());
        }
    }

    /** Upload file dan folder (beserta isinya) ke direktori saat ini. */
    public void upload(List<Path> paths) {
        if (link == null || currentDir == null) {
            return;
        }
        String dir = currentDir;
        for (Path path : paths) {
            String name = path.getFileName().toString();
            if (Files.isDirectory(path)) {
                uploadFolder(path, name, dir);
                continue;
            }
            String target = RemotePaths.join(dir, name);
            var existsSkipped = new AtomicBoolean();
            activity.report(Level.INFO, I18n.t("sftp.upload.queued", name, dir));
            transfers.submit(I18n.t("sftp.upload.title", name), listener -> {
                activity.report(Level.INFO, I18n.t("sftp.upload.running", name, dir));
                link.execute(() -> {
                    if (svc().exists(target) && !confirmOverwrite(target)) {
                        existsSkipped.set(true);
                        return null;
                    }
                    svc().upload(path, target, null, listener);
                    return null;
                });
            }, () -> {
                if (existsSkipped.get()) {
                    activity.report(Level.WARN, I18n.t("sftp.upload.skipped", name));
                    return;
                }
                activity.report(Level.SUCCESS, I18n.t("sftp.upload.done", name, dir));
                refreshIfShowing(dir);
            }, err -> {
                activity.report(Level.ERROR, I18n.t("sftp.upload.failed", name, err.getMessage()));
                Dialogs.error(this, I18n.t("sftp.upload.failedTitle"), err);
            }, () -> activity.report(Level.WARN, I18n.t("sftp.upload.cancelled", name)));
        }
    }

    private void uploadFolder(Path folder, String name, String dir) {
        var result = new AtomicReference<TreeTransfer.Result>();
        String label = name + "/";
        activity.report(Level.INFO, I18n.t("sftp.upload.queued", label, dir));
        transfers.submit(I18n.t("sftp.upload.title", label), listener -> {
            activity.report(Level.INFO, I18n.t("sftp.upload.running", label, dir));
            result.set(link.execute(() -> TreeTransfer.upload(svc(), folder, dir, this::confirmMerge, listener)));
        }, () -> {
            var r = result.get();
            if (r == null) {
                activity.report(Level.WARN, I18n.t("sftp.upload.skipped", label));
                return;
            }
            activity.report(Level.SUCCESS, I18n.t("sftp.upload.folderDone", name, String.valueOf(r.files()), dir));
            reportSkipped(I18n.t("sftp.upload.infoTitle"), r.skipped());
            refreshIfShowing(dir);
        }, err -> {
            activity.report(Level.ERROR, I18n.t("sftp.upload.failed", label, err.getMessage()));
            Dialogs.error(this, I18n.t("sftp.upload.failedTitle"), err);
            refreshIfShowing(dir); // sebagian isi mungkin sudah terupload
        }, () -> {
            activity.report(Level.WARN, I18n.t("sftp.upload.cancelled", label));
            refreshIfShowing(dir);
        });
    }

    // ------------------------------------------------------------------ upload ke panel SFTP lain

    /**
     * Drag baris memulai transfer kalau ada yang bisa menerimanya: panel lokal (dua panel = download) atau panel SFTP
     * lain yang terbuka (upload antar-server). Tanpa itu, drag dipakai untuk memilih beberapa baris.
     */
    private boolean dragTargetAvailable() {
        return isDualPane() || !uploadTargets().isEmpty();
    }

    /** Panel ini bisa menerima upload dari panel lain: tersambung, sudah membuka folder, dan tampil di tab. */
    private boolean isOpenTarget() {
        return !disposed && link != null && currentDir != null
                && SwingUtilities.getAncestorOfClass(javax.swing.JTabbedPane.class, this) != null;
    }

    /** Komponen tab tempat panel ini berada (panel dalam satu grup split berbagi komponen tab yang sama). */
    private Component tabComponent() {
        Component c = this;
        while (c != null && !(c.getParent() instanceof javax.swing.JTabbedPane)) {
            c = c.getParent();
        }
        return c;
    }

    private int tabIndex() {
        Component tab = tabComponent();
        return tab != null && tab.getParent() instanceof javax.swing.JTabbedPane tabs ? tabs.indexOfComponent(tab) : -1;
    }

    /** Panel tujuan "Upload ke": satu grup split dengan panel ini dulu, lalu panel di tab lain urut posisi tab. */
    List<SftpPanel> uploadTargets() {
        Component myTab = tabComponent();
        return OPEN_PANELS.stream()
                .filter(p -> p != this && p.isOpenTarget())
                .sorted(java.util.Comparator.comparing((SftpPanel p) -> p.tabComponent() != myTab)
                        .thenComparingInt(SftpPanel::tabIndex))
                .toList();
    }

    private void rebuildUploadTo(javax.swing.JMenu menu) {
        menu.removeAll();
        var selected = selectedEntries();
        var targets = uploadTargets();
        menu.setEnabled(link != null && !selected.isEmpty());
        if (targets.isEmpty()) {
            var none = new JMenuItem(I18n.t("sftp.uploadTo.none"));
            none.setEnabled(false);
            menu.add(none);
            return;
        }
        Component myTab = tabComponent();
        Boolean section = null;
        for (SftpPanel target : targets) {
            boolean sameGroup = target.tabComponent() == myTab;
            if (!Boolean.valueOf(sameGroup).equals(section)) {
                if (section != null) {
                    menu.addSeparator();
                }
                var header = new JMenuItem(I18n.t(sameGroup ? "sftp.uploadTo.sameGroup" : "sftp.uploadTo.otherTabs"));
                header.setEnabled(false);
                menu.add(header);
                section = sameGroup;
            }
            var item = menuItem(I18n.t("sftp.uploadTo.item", target.profile.name(), shortDir(target.currentDir)),
                    () -> target.copyFrom(this, selectedEntries()));
            item.setToolTipText(target.profile.name() + ": " + target.currentDir);
            menu.add(item);
        }
    }

    /** Path panjang dipotong di depan supaya menu tidak terlalu lebar: …/var/www/app. */
    static String shortDir(String dir) {
        return dir.length() <= 40 ? dir : "…" + dir.substring(dir.length() - 39);
    }

    /**
     * Salin {@code entries} dari panel {@code source} (server lain atau server yang sama) ke folder yang sedang dibuka
     * di panel ini, setelah konfirmasi. Transfer berjalan di antrean panel ini; isi file langsung dari stream SFTP
     * sumber ke stream SFTP tujuan tanpa file sementara di disk lokal. EDT.
     */
    void copyFrom(SftpPanel source, List<RemoteEntry> entries) {
        if (link == null || currentDir == null || entries.isEmpty() || source == this) {
            return;
        }
        String dir = currentDir;
        if (!confirmCopy(source, entries, dir)) {
            return;
        }
        boolean sameHost = source.profile.id().equals(profile.id());
        for (RemoteEntry entry : entries) {
            String label = entry.isDirectory() ? entry.name() + "/" : entry.name();
            String problem = copyProblem(sameHost, entry.path(), entry.isDirectory(), dir);
            if (problem != null) {
                activity.report(Level.WARN, I18n.t(problem, label));
                continue;
            }
            String from = source.profile.name();
            var skipped = new AtomicBoolean();
            var folder = new AtomicReference<TreeTransfer.Result>();
            activity.report(Level.INFO, I18n.t("sftp.copy.queued", label, from, dir));
            transfers.submit(I18n.t("sftp.copy.title", label, from), listener -> {
                activity.report(Level.INFO, I18n.t("sftp.copy.running", label, from, dir));
                link.execute(() -> {
                    var sourceLink = source.link;
                    if (sourceLink == null) {
                        throw new dev.egateza.termul.sftp.RemoteFileException(I18n.t("sftp.copy.sourceClosed", from));
                    }
                    var in = sourceLink.ensureLive();
                    boolean isDir = entry.isDirectory() || (entry.type() == RemoteEntry.Type.SYMLINK
                            && in.stat(entry.path()).isDirectory());
                    if (isDir) {
                        var r = TreeTransfer.copy(in, entry.path(), svc(), dir, this::confirmMerge, listener);
                        skipped.set(r == null);
                        folder.set(r);
                        return null;
                    }
                    String target = RemotePaths.join(dir, entry.name());
                    if (svc().exists(target) && !confirmOverwrite(target)) {
                        skipped.set(true);
                        return null;
                    }
                    svc().copyFrom(in, entry.path(), target, listener);
                    return null;
                });
            }, () -> {
                var r = folder.get();
                if (skipped.get()) {
                    activity.report(Level.WARN, I18n.t("sftp.upload.skipped", label));
                } else if (r != null) {
                    activity.report(Level.SUCCESS, I18n.t("sftp.upload.folderDone", entry.name(),
                            String.valueOf(r.files()), dir));
                    reportSkipped(I18n.t("sftp.upload.infoTitle"), r.skipped());
                } else {
                    activity.report(Level.SUCCESS, I18n.t("sftp.upload.done", entry.name(), dir));
                }
                refreshIfShowing(dir);
            }, err -> {
                activity.report(Level.ERROR, I18n.t("sftp.upload.failed", label, err.getMessage()));
                Dialogs.error(this, I18n.t("sftp.upload.failedTitle"), err);
                refreshIfShowing(dir);
            }, () -> {
                activity.report(Level.WARN, I18n.t("sftp.upload.cancelled", label));
                refreshIfShowing(dir);
            });
        }
    }

    /**
     * Salinan yang tidak masuk akal di server yang sama: ke lokasinya sendiri, atau folder ke dalam dirinya sendiri.
     *
     * @return kunci pesan i18n, atau null kalau boleh
     */
    static String copyProblem(boolean sameHost, String sourcePath, boolean directory, String targetDir) {
        if (!sameHost) {
            return null;
        }
        if (RemotePaths.join(targetDir, RemotePaths.name(sourcePath)).equals(sourcePath)) {
            return "sftp.copy.sameLocation";
        }
        if (directory && (targetDir.equals(sourcePath) || targetDir.startsWith(sourcePath + "/"))) {
            return "sftp.copy.intoItself";
        }
        return null;
    }

    /** Konfirmasi sebelum upload antar-panel: dari mana, item apa, ke host dan folder mana. Peringatan untuk PROD. */
    private boolean confirmCopy(SftpPanel source, List<RemoteEntry> entries, String dir) {
        var names = entries.stream().limit(8)
                .map(e -> "  • " + (e.isDirectory() ? e.name() + "/" : e.name())).toList();
        String more = entries.size() > names.size()
                ? "\n  " + I18n.t("sftp.copy.confirm.more", String.valueOf(entries.size() - names.size())) : "";
        String message = I18n.t("sftp.copy.confirm.message", String.valueOf(entries.size()), source.profile.name(),
                source.currentDir == null ? "" : source.currentDir, String.join("\n", names) + more,
                profile.name(), profile.address(), dir);
        boolean prod = profile.environment().isProduction();
        if (prod) {
            message += "\n\n" + I18n.t("sftp.copy.confirm.prod", profile.name());
        }
        Object[] options = {I18n.t("sftp.copy.confirm.ok"), I18n.t("sftp.copy.confirm.cancel")};
        return javax.swing.JOptionPane.showOptionDialog(this, message, I18n.t("sftp.copy.confirm.title", profile.name()),
                javax.swing.JOptionPane.OK_CANCEL_OPTION,
                prod ? javax.swing.JOptionPane.WARNING_MESSAGE : javax.swing.JOptionPane.QUESTION_MESSAGE,
                null, options, options[1]) == 0;
    }

    private void refreshIfShowing(String dir) {
        if (dir.equals(currentDir)) {
            refresh();
        }
    }

    private boolean confirmOverwrite(String target) {
        var ok = new AtomicBoolean();
        Edt.runAndWait(() -> ok.set(Dialogs.confirm(this, I18n.t("sftp.overwrite.title"), I18n.t("sftp.overwrite.confirm", target))));
        return ok.get();
    }

    /** Folder tujuan sudah ada: gabungkan (file bernama sama ditimpa)? Dipanggil dari thread transfer. */
    private boolean confirmMerge(String target) {
        var ok = new AtomicBoolean();
        Edt.runAndWait(() -> ok.set(Dialogs.confirm(this, I18n.t("sftp.merge.title"), I18n.t("sftp.merge.confirm", target))));
        return ok.get();
    }

    /** Item yang tidak ikut disalin (symlink ke folder, file khusus, nama tidak valid di OS ini). */
    private void reportSkipped(String title, List<String> skipped) {
        if (skipped.isEmpty()) {
            return;
        }
        var shown = skipped.stream().limit(10).toList();
        String more = skipped.size() > shown.size()
                ? "\n" + I18n.t("sftp.transfer.skippedMore", String.valueOf(skipped.size() - shown.size())) : "";
        Dialogs.info(this, title, I18n.t("sftp.transfer.skipped", String.join("\n", shown)) + more);
    }

    private void downloadSelected() {
        var selected = selectedEntries();
        if (selected.isEmpty()) {
            return;
        }
        if (isDualPane() && local.currentDir() != null) {
            downloadTo(selected, local.currentDir()); // dua panel: langsung ke folder lokal yang sedang dibuka
            return;
        }
        var chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(I18n.t("sftp.download.chooserTitle", String.valueOf(selected.size())));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        downloadTo(selected, chooser.getSelectedFile().toPath());
    }

    /**
     * Download file dan folder (beserta isinya) ke direktori lokal; juga dipanggil saat item remote di-drop ke panel
     * lokal. Symlink diperiksa targetnya: ke folder = folder, ke file = file.
     */
    void downloadTo(List<RemoteEntry> entries, Path targetDir) {
        if (link == null) {
            return;
        }
        for (RemoteEntry entry : entries) {
            String label = entry.isDirectory() ? entry.name() + "/" : entry.name();
            var skipped = new AtomicBoolean();
            var folder = new AtomicReference<TreeTransfer.Result>();
            Path target = targetDir.resolve(entry.name());
            activity.report(Level.INFO, I18n.t("sftp.download.queued", label));
            transfers.submit(I18n.t("sftp.download.title", label), listener -> {
                activity.report(Level.INFO, I18n.t("sftp.download.running", entry.path()));
                link.execute(() -> {
                    boolean dir = entry.isDirectory() || (entry.type() == RemoteEntry.Type.SYMLINK
                            && svc().stat(entry.path()).isDirectory());
                    if (dir) {
                        var r = TreeTransfer.download(svc(), entry.path(), targetDir, this::confirmMerge, listener);
                        skipped.set(r == null);
                        folder.set(r);
                    } else if (Files.exists(target) && !confirmOverwrite(target.toString())) {
                        skipped.set(true);
                    } else {
                        svc().download(entry.path(), target, listener);
                    }
                    return null;
                });
            }, () -> {
                var r = folder.get();
                if (skipped.get()) {
                    activity.report(Level.WARN, I18n.t("sftp.download.skipped", label));
                } else if (r != null) {
                    activity.report(Level.SUCCESS, I18n.t("sftp.download.folderDone", entry.name(),
                            String.valueOf(r.files()), target));
                    reportSkipped(I18n.t("sftp.download.infoTitle"), r.skipped());
                } else {
                    activity.report(Level.SUCCESS, I18n.t("sftp.download.done", entry.name(), target));
                }
                refreshLocal(targetDir);
            }, err -> {
                activity.report(Level.ERROR, I18n.t("sftp.download.failed", label, err.getMessage()));
                Dialogs.error(this, I18n.t("sftp.download.failedTitle"), err);
                refreshLocal(targetDir);
            }, () -> {
                activity.report(Level.WARN, I18n.t("sftp.download.cancelled", label));
                refreshLocal(targetDir);
            });
        }
    }

    private void refreshLocal(Path dir) {
        if (local != null && dir.equals(local.currentDir())) {
            local.refresh();
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

    /**
     * Drag & drop file dari Windows Explorer atau panel lokal → upload ke direktori saat ini; dari panel SFTP lain →
     * upload antar-server (dengan konfirmasi). Drag keluar dari tabel bisa di-drop ke panel lokal (download) atau ke
     * panel SFTP lain.
     */
    private final class FileDropHandler extends TransferHandler {
        @Override
        public int getSourceActions(JComponent c) {
            return COPY;
        }

        @Override
        protected java.awt.datatransfer.Transferable createTransferable(JComponent c) {
            var selected = selectedEntries();
            return link == null || selected.isEmpty() ? null : new RemoteSelection(SftpPanel.this, selected);
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (link == null) {
                return false;
            }
            if (support.isDataFlavorSupported(RemoteSelection.FLAVOR)) {
                // sebagian platform belum memberi isi transferable selama drag: terima dulu, dicek lagi saat drop
                return remoteSelection(support).map(sel -> sel.source() != SftpPanel.this).orElse(true);
            }
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        private Optional<RemoteSelection> remoteSelection(TransferSupport support) {
            try {
                return Optional.of((RemoteSelection) support.getTransferable().getTransferData(RemoteSelection.FLAVOR));
            } catch (UnsupportedFlavorException | IOException | java.awt.dnd.InvalidDnDOperationException e) {
                return Optional.empty();
            }
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            if (support.isDataFlavorSupported(RemoteSelection.FLAVOR)) {
                var sel = remoteSelection(support);
                // dialog konfirmasi setelah drop selesai, bukan di tengah operasi drag
                sel = sel.filter(r -> r.source() != SftpPanel.this);
                sel.ifPresent(r -> SwingUtilities.invokeLater(() -> copyFrom(r.source(), r.entries())));
                return sel.isPresent();
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
            connectedListener.accept(l.service());
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

    /**
     * Double-click / Enter: direktori dibuka; file ditangani {@link #openFile}. Symlink dicek dulu targetnya
     * (mis. {@code sites-enabled/*.conf} menunjuk ke file, bukan folder).
     */
    protected void activate(RemoteEntry entry) {
        if (entry.isDirectory()) {
            navigate(entry.path());
        } else if (entry.type() == RemoteEntry.Type.SYMLINK) {
            if (link != null) {
                call(() -> svc().stat(entry.path()), target -> {
                    if (target.type() == RemoteEntry.Type.FILE) {
                        openFile(entry);
                    } else {
                        navigate(entry.path());
                    }
                });
            }
        } else {
            openFile(entry);
        }
    }

    /** File biasa, atau symlink yang mungkin menunjuk ke file (targetnya dicek saat dibuka/di-download). */
    private static boolean maybeFile(RemoteEntry e) {
        return e.type() == RemoteEntry.Type.FILE || e.type() == RemoteEntry.Type.SYMLINK;
    }

    /**
     * Membuka file dengan aplikasi default Windows (auto-upload saat disimpan). File executable / tanpa ekstensi
     * dibuka dengan editor dari pengaturan (lihat {@link EditorLauncher}).
     */
    protected void openFile(RemoteEntry entry) {
        editActions.edit(profile, entry.path(), EditorLauncher.SYSTEM_DEFAULT);
    }

    private void editSelected(String command) {
        selectedEntries().stream().filter(SftpPanel::maybeFile).findFirst().ifPresent(entry -> {
            editActions.edit(profile, entry.path(), command);
        });
    }

    private void viewSelected() {
        selectedEntries().stream().filter(SftpPanel::maybeFile).findFirst()
                .ifPresent(entry -> editActions.view(profile, entry.path()));
    }

    private void editSelectedAsRoot() {
        selectedEntries().stream().filter(SftpPanel::maybeFile).findFirst()
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
        OPEN_PANELS.remove(this);
        transferBadge.dispose();
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
