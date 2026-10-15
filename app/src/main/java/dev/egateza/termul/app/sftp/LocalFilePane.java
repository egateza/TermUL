package dev.egateza.termul.app.sftp;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.OverflowToolBar;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.Shortcuts;
import dev.egateza.termul.app.ui.UiAsync;
import dev.egateza.termul.core.Os;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.TransferHandler;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Panel file lokal di sisi kiri tampilan SFTP dua panel (ala WinSCP). Drag file ke tabel remote = upload, drag file
 * remote ke sini = download ke direktori yang sedang dibuka. Semua method dipanggil di EDT; disk I/O di virtual thread.
 */
final class LocalFilePane extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(LocalFilePane.class);
    private static final ExecutorService IO = Executors.newVirtualThreadPerTaskExecutor();
    private static final javax.swing.Icon DIR_ICON = AppIcon.FOLDER.icon(AppIcon.SIZE, () -> AppIcon.FOLDER_COLOR);
    private static final javax.swing.Icon FILE_ICON = AppIcon.FILE.icon();

    /** Direktori lokal terakhir yang dibuka; panel dua panel berikutnya mulai dari sini. EDT. */
    private static Path lastDir;

    private final LocalTableModel model = new LocalTableModel();
    private final JTable table = new JTable(model);
    private final TableRowSorter<LocalTableModel> sorter = new TableRowSorter<>(model);
    private final JTextField pathField = new JTextField();
    /** Filter nama untuk folder yang sedang dibuka (tanpa membaca ulang disk). */
    private final JTextField filterField = new JTextField();
    /** Item yang tidak muat saat panel sempit masuk ke tombol » di ujung kanan. */
    private final OverflowToolBar toolbar = new OverflowToolBar();
    private final JComboBox<Path> drives = new JComboBox<>();
    private final TypeAhead typeAhead = new TypeAhead();
    private final SystemFileIcons fileIcons;
    private final Consumer<List<Path>> upload;
    private Path currentDir;   // EDT
    private long generation;   // EDT; listing yang lebih lama dari navigasi terakhir diabaikan

    /** @param upload mengupload file lokal ke direktori remote yang sedang dibuka */
    LocalFilePane(SystemFileIcons fileIcons, Consumer<List<Path>> upload) {
        super(new BorderLayout());
        this.fileIcons = fileIcons;
        this.upload = upload;

        var bar = toolbar;
        if (Os.current().isWindows()) {
            var roots = new DefaultComboBoxModel<Path>();
            for (File root : File.listRoots()) {
                roots.addElement(root.toPath());
            }
            drives.setModel(roots);
            drives.setFocusable(false);
            drives.setToolTipText(I18n.t("sftp.local.drive.tip"));
            drives.setMaximumSize(drives.getPreferredSize());
            drives.addActionListener(e -> {
                if (drives.getSelectedItem() instanceof Path root && (currentDir == null || !root.equals(currentDir.getRoot()))) {
                    navigate(root);
                }
            });
            bar.add(drives);
        }
        bar.add(SftpPanel.button(AppIcon.ARROW_UP, null, I18n.t("sftp.toolbar.up"), this::goUp));
        bar.add(SftpPanel.button(AppIcon.HOME, null, I18n.t("sftp.toolbar.home"), () -> navigate(home())));
        bar.add(SftpPanel.button(AppIcon.REFRESH, null, I18n.t("sftp.toolbar.refresh"), this::refresh));
        bar.addSeparator();
        bar.add(SftpPanel.button(AppIcon.UPLOAD, I18n.t("sftp.toolbar.upload"), I18n.t("sftp.local.upload.tip"),
                this::uploadSelected));
        bar.addSeparator();
        bar.add(SftpPanel.button(AppIcon.FOLDER_PLUS, null, I18n.t("sftp.toolbar.mkdir.tip"), this::mkdir));
        bar.add(SftpPanel.button(AppIcon.PEN, null, I18n.t("sftp.toolbar.rename.tip"), this::renameSelected));
        bar.add(SftpPanel.button(AppIcon.TRASH, null, I18n.t("sftp.local.delete.tip"), this::deleteSelected));
        installFilter(bar);
        pathField.addActionListener(e -> navigateText(pathField.getText().strip()));
        // susunan sama dengan panel remote: toolbar, lalu path
        var top = new JPanel(new BorderLayout());
        top.add(bar, BorderLayout.NORTH);
        top.add(pathField, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setDragEnabled(true);
        for (int c = 0; c < model.getColumnCount(); c++) {
            sorter.setComparator(c, LocalTableModel.comparator(c));
        }
        table.setRowSorter(sorter);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus,
                                                           int row, int column) {
                super.getTableCellRendererComponent(t, value, sel, focus, row, column);
                int modelCol = t.convertColumnIndexToModel(column);
                setText(value instanceof LocalTableModel.Entry e ? LocalTableModel.display(e, modelCol) : "");
                setIcon(modelCol == LocalTableModel.COL_NAME && value instanceof LocalTableModel.Entry e ? icon(e) : null);
                setHorizontalAlignment(modelCol == LocalTableModel.COL_SIZE ? RIGHT : LEFT);
                return this;
            }
        });
        int[] widths = {220, 70, 90, 120};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        SftpColumns.install(table, LocalTableModel.COLUMN_IDS);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    activateSelected();
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
        bind(KeyEvent.VK_ENTER, "open", this::activateSelected);
        bind(KeyEvent.VK_BACK_SPACE, "up", this::goUp);
        bind(KeyEvent.VK_F5, "refresh", this::refresh);
        bind(KeyEvent.VK_ESCAPE, "clearFilter", () -> filterField.setText(""));
        bind(KeyEvent.VK_F2, "rename", this::renameSelected);
        bind(KeyEvent.VK_F7, "mkdir", this::mkdir);
        bind(KeyEvent.VK_DELETE, "delete", this::deleteSelected);
        table.setComponentPopupMenu(buildPopup());

        var scroll = new JScrollPane(table);
        var dnd = new LocalTransferHandler();
        table.setTransferHandler(dnd);
        scroll.setTransferHandler(dnd);
        add(scroll, BorderLayout.CENTER);

        navigate(lastDir != null ? lastDir : home());
    }

    /**
     * Filter nama di ujung kanan toolbar, sama seperti panel remote. Ctrl/Cmd+F dipasang di panel ini sehingga
     * mengalahkan binding panel SFTP selama fokus ada di sisi lokal.
     */
    private void installFilter(JToolBar bar) {
        bar.add(javax.swing.Box.createHorizontalGlue());
        filterField.putClientProperty("JTextField.placeholderText", I18n.t("sftp.filter.placeholder"));
        filterField.putClientProperty("JTextField.showClearButton", true);
        filterField.setToolTipText(I18n.t("sftp.filter.tip", Shortcuts.text(Shortcuts.menu(KeyEvent.VK_F))));
        filterField.setColumns(12);
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
        bar.add(filterField);
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(Shortcuts.menu(KeyEvent.VK_F), "focusFilter");
        getActionMap().put("focusFilter", action(() -> {
            toolbar.reveal(filterField);
            filterField.requestFocusInWindow();
            filterField.selectAll();
        }));
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

    private void applyFilter() {
        var match = SftpTableModel.namePredicate(filterField.getText());
        sorter.setRowFilter(match == null ? null : new RowFilter<LocalTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends LocalTableModel, ? extends Integer> entry) {
                return match.test(model.entryAt(entry.getIdentifier()).name());
            }
        });
    }

    Path currentDir() {
        return currentDir;
    }

    /** File dan folder yang dipilih, sesuai urutan tampilan. */
    List<Path> selectedPaths() {
        return selectedEntries().stream().map(LocalTableModel.Entry::path).toList();
    }

    private void uploadSelected() {
        var paths = selectedPaths();
        if (!paths.isEmpty()) {
            upload.accept(paths);
        }
    }

    void refresh() {
        if (currentDir != null) {
            navigate(currentDir);
        }
    }

    private static Path home() {
        return Path.of(System.getProperty("user.home"));
    }

    private void goUp() {
        if (currentDir != null && currentDir.getParent() != null) {
            Path from = currentDir;
            navigate(currentDir.getParent(), from.getFileName().toString());
        }
    }

    private void navigateText(String text) {
        try {
            navigate(Path.of(text.startsWith("~") ? System.getProperty("user.home") + text.substring(1) : text));
        } catch (InvalidPathException e) {
            Dialogs.error(this, I18n.t("sftp.local.title"), I18n.t("sftp.local.invalidPath", text));
        }
    }

    void navigate(Path dir) {
        navigate(dir, null);
    }

    /** @param select nama entri yang dipilih setelah dimuat (mis. folder asal saat naik satu tingkat), atau null */
    private void navigate(Path dir, String select) {
        long gen = ++generation;
        Path target = dir.toAbsolutePath().normalize();
        UiAsync.run(IO, () -> {
            try {
                return LocalTableModel.list(target);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, entries -> {
            if (gen != generation) {
                return;
            }
            if (!target.equals(currentDir)) {
                filterField.setText(""); // filter hanya untuk folder tempat ia diketik; refresh mempertahankannya
            }
            currentDir = target;
            lastDir = target;
            pathField.setText(target.toString());
            if (target.getRoot() != null && Os.current().isWindows()) {
                drives.setSelectedItem(target.getRoot());
            }
            model.setEntries(entries);
            if (select != null) {
                selectByName(select);
            }
        }, err -> {
            if (gen != generation) {
                return;
            }
            pathField.setText(currentDir == null ? "" : currentDir.toString());
            log.info("Gagal membuka direktori lokal {}: {}", target, err.toString());
            Dialogs.error(this, I18n.t("sftp.local.title"), I18n.t("sftp.local.listFailed", target, err.getMessage()));
        });
    }

    private void selectByName(String name) {
        for (int row = 0; row < table.getRowCount(); row++) {
            if (model.entryAt(table.convertRowIndexToModel(row)).name().equals(name)) {
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                return;
            }
        }
    }

    private void activateSelected() {
        var selected = selectedEntries();
        if (selected.isEmpty()) {
            return;
        }
        var entry = selected.getFirst();
        if (entry.directory()) {
            navigate(entry.path());
        } else {
            openLocally(entry.path());
        }
    }

    /** File lokal dibuka dengan aplikasi default OS. */
    private void openLocally(Path file) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            return;
        }
        UiAsync.run(IO, () -> {
            try {
                Desktop.getDesktop().open(file.toFile());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, err -> Dialogs.error(this, I18n.t("sftp.local.title"), err));
    }

    private List<LocalTableModel.Entry> selectedEntries() {
        var result = new ArrayList<LocalTableModel.Entry>();
        for (int viewRow : table.getSelectedRows()) {
            result.add(model.entryAt(table.convertRowIndexToModel(viewRow)));
        }
        return result;
    }

    private javax.swing.Icon icon(LocalTableModel.Entry e) {
        if (e.directory()) {
            return DIR_ICON;
        }
        var system = fileIcons.icon(e.name(), table::repaint);
        return system != null ? system : FILE_ICON;
    }

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

    private JPopupMenu buildPopup() {
        var menu = new JPopupMenu();
        var open = menuItem(I18n.t("sftp.local.menu.open"), this::activateSelected);
        open.setFont(open.getFont().deriveFont(java.awt.Font.BOLD)); // aksi double-click
        var uploadItem = menuItem(I18n.t("sftp.local.menu.upload"), this::uploadSelected);
        var reveal = menuItem(I18n.t(Os.current().isMac() ? "sftp.local.menu.revealFinder" : "sftp.local.menu.revealExplorer"),
                this::revealSelected);
        var rename = menuItem(I18n.t("sftp.menu.rename"), this::renameSelected);
        var delete = menuItem(I18n.t("sftp.local.menu.delete"), this::deleteSelected);
        var copyPath = menuItem(I18n.t("sftp.menu.copyPath"), this::copyPath);
        menu.add(open);
        menu.add(uploadItem);
        menu.add(reveal);
        menu.addSeparator();
        menu.add(rename);
        menu.add(delete);
        menu.addSeparator();
        menu.add(menuItem(I18n.t("sftp.menu.mkdir"), this::mkdir));
        menu.add(copyPath);
        menu.add(menuItem(I18n.t("sftp.menu.refresh"), this::refresh));
        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                selectRowUnderPointer();
                int n = table.getSelectedRowCount();
                open.setEnabled(n > 0);
                uploadItem.setEnabled(n > 0);
                reveal.setEnabled(n > 0 || currentDir != null);
                rename.setEnabled(n == 1);
                delete.setEnabled(n > 0 && canTrash());
                copyPath.setEnabled(n > 0 || currentDir != null);
            }

            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
            }
        });
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
        } else if (row < 0) {
            table.clearSelection(); // klik kanan di area kosong: aksi untuk folder yang sedang dibuka
        }
    }

    // ------------------------------------------------------------------ operasi file lokal

    private void mkdir() {
        if (currentDir == null) {
            return;
        }
        String name = Dialogs.input(this, I18n.t("sftp.mkdir.title"), I18n.t("sftp.mkdir.prompt", currentDir), "");
        Path target = childPath(name);
        if (target != null) {
            fileOp(() -> Files.createDirectory(target), target.getFileName().toString());
        }
    }

    private void renameSelected() {
        var selected = selectedEntries();
        if (selected.size() != 1) {
            return;
        }
        var entry = selected.getFirst();
        String name = Dialogs.input(this, I18n.t("sftp.rename.title"), I18n.t("sftp.rename.prompt", entry.name()), entry.name());
        if (name == null || name.strip().equals(entry.name())) {
            return;
        }
        Path target = childPath(name);
        if (target != null) {
            fileOp(() -> Files.move(entry.path(), target), target.getFileName().toString()); // tanpa menimpa
        }
    }

    private static boolean canTrash() {
        return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
    }

    /** Hapus ke Recycle Bin / Trash (bisa dipulihkan); tidak pernah menghapus permanen. */
    private void deleteSelected() {
        var selected = selectedPaths();
        if (selected.isEmpty()) {
            return;
        }
        if (!canTrash()) {
            Dialogs.info(this, I18n.t("sftp.local.title"), I18n.t("sftp.local.delete.unsupported"));
            return;
        }
        String what = selected.size() == 1 ? selected.getFirst().toString()
                : I18n.t("sftp.delete.items", String.valueOf(selected.size()));
        if (!Dialogs.confirm(this, I18n.t("sftp.delete.title"), I18n.t("sftp.local.delete.confirm", what))) {
            return;
        }
        fileOp(() -> {
            for (Path p : selected) {
                if (!Desktop.getDesktop().moveToTrash(p.toFile())) {
                    throw new IOException(I18n.t("sftp.local.delete.failed", p));
                }
            }
        }, null);
    }

    private void copyPath() {
        var selected = selectedPaths();
        String text = selected.isEmpty() ? String.valueOf(currentDir)
                : String.join(System.lineSeparator(), selected.stream().map(Path::toString).toList());
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text), null);
    }

    /** Buka Explorer/Finder dengan item terpilih disorot (atau folder yang sedang dibuka). */
    private void revealSelected() {
        var selected = selectedPaths();
        Path target = selected.isEmpty() ? currentDir : selected.getFirst();
        if (target == null) {
            return;
        }
        UiAsync.run(IO, () -> {
            try {
                var os = Os.current();
                if (os.isWindows()) {
                    new ProcessBuilder("explorer.exe", "/select,", target.toString()).start();
                } else if (os.isMac()) {
                    new ProcessBuilder("open", "-R", target.toString()).start();
                } else {
                    Desktop.getDesktop().open((Files.isDirectory(target) ? target : target.getParent()).toFile());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, err -> Dialogs.error(this, I18n.t("sftp.local.title"), err));
    }

    /** Path anak folder saat ini untuk nama yang diketik user; null (dengan pesan) kalau batal/tidak valid. */
    private Path childPath(String name) {
        if (name == null || name.isBlank() || currentDir == null) {
            return null;
        }
        String n = name.strip();
        Path p = null;
        try {
            if (!n.equals(".") && !n.equals("..") && n.indexOf('/') < 0 && n.indexOf('\\') < 0) {
                p = currentDir.resolve(n);
            }
        } catch (InvalidPathException e) {
            // ditangani di bawah
        }
        if (p == null || !currentDir.equals(p.getParent())) {
            Dialogs.error(this, I18n.t("sftp.local.title"), I18n.t("sftp.local.invalidName", n));
            return null;
        }
        return p;
    }

    private interface LocalOp {
        void run() throws IOException;
    }

    /** Operasi disk di virtual thread, lalu muat ulang folder (memilih {@code select} kalau ada). */
    private void fileOp(LocalOp op, String select) {
        Path dir = currentDir;
        UiAsync.run(IO, () -> {
            try {
                op.run();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, err -> {
            Throwable cause = err instanceof UncheckedIOException u ? u.getCause() : err;
            String message = cause instanceof java.nio.file.FileAlreadyExistsException f
                    ? I18n.t("sftp.local.exists", f.getFile())
                    : cause instanceof java.nio.file.AccessDeniedException a ? I18n.t("sftp.local.accessDenied", a.getFile())
                    : String.valueOf(cause.getMessage());
            Dialogs.error(this, I18n.t("sftp.local.title"), message);
            refresh();
        }).thenRun(() -> javax.swing.SwingUtilities.invokeLater(() -> {
            if (dir.equals(currentDir)) {
                navigate(dir, select);
            }
        }));
    }

    private static JMenuItem menuItem(String label, Runnable action) {
        var item = new JMenuItem(label);
        item.addActionListener(e -> action.run());
        return item;
    }

    private void bind(int key, String name, Runnable action) {
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), name);
        table.getActionMap().put(name, action(action));
    }

    private static AbstractAction action(Runnable action) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        };
    }

    /**
     * Drag keluar: daftar file lokal (diterima tabel remote = upload, juga Explorer/Finder). Drop masuk: hanya file
     * remote dari tabel SFTP, diunduh ke direktori yang sedang dibuka.
     */
    private final class LocalTransferHandler extends TransferHandler {
        @Override
        public int getSourceActions(JComponent c) {
            return COPY;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            var files = selectedEntries().stream().map(e -> e.path().toFile()).toList();
            return files.isEmpty() ? null : new FileListSelection(files);
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return currentDir != null && support.isDataFlavorSupported(RemoteSelection.FLAVOR);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                var selection = (RemoteSelection) support.getTransferable().getTransferData(RemoteSelection.FLAVOR);
                selection.source().downloadTo(selection.entries(), currentDir);
                return true;
            } catch (UnsupportedFlavorException | IOException e) {
                log.warn("Drop file remote gagal", e);
                return false;
            }
        }
    }

    private record FileListSelection(List<File> files) implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {DataFlavor.javaFileListFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.javaFileListFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return files;
        }
    }
}
