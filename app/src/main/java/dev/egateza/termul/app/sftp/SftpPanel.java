package dev.egateza.termul.app.sftp;

import dev.egateza.termul.app.sftp.ActivityBar.Level;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.Edt;
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
    /** Keterangan aktivitas panel ini (upload, download, operasi file, reconnect, edit remote). */
    protected final ActivityBar activity = new ActivityBar();
    protected final JToolBar toolbar = new JToolBar();
    private volatile SftpConnection link;        // koneksi bersama profil; ditulis sekali setelah connect
    private Runnable unsubscribeLink = () -> { }; // EDT
    private Runnable unsubscribeEdits = () -> { }; // EDT
    private boolean disposed;                    // EDT
    protected String currentDir;                 // EDT
    private boolean connecting;                  // EDT

    private final EditActions editActions;

    /** Hubungan panel ke editor lokal (diwujudkan di modul wiring). */
    public interface EditActions {
        /** @param command template editor pilihan user; null = editor default sesuai ekstensi */
        void edit(HostProfile profile, String remotePath, String command);

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

    public SftpPanel(HostProfile profile, SftpLinks links, ExecutorService sshOps, EditActions editActions) {
        super(new BorderLayout());
        this.profile = profile;
        this.links = links;
        this.sshOps = sshOps;
        this.editActions = editActions;

        toolbar.setFloatable(false);
        toolbar.add(button(AppIcon.ARROW_UP, null, "Direktori induk (Backspace)", this::goUp));
        toolbar.add(button(AppIcon.HOME, null, "Home", this::goHome));
        toolbar.add(button(AppIcon.REFRESH, null, "Refresh (F5)", this::refresh));
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
        var sorter = new TableRowSorter<>(model);
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
        bind(KeyEvent.VK_ENTER, 0, "open", () -> selectedEntries().stream().findFirst().ifPresent(this::activate));
        bind(KeyEvent.VK_BACK_SPACE, 0, "up", this::goUp);
        bind(KeyEvent.VK_F5, 0, "refresh", this::refresh);

        toolbar.add(button(AppIcon.UPLOAD, "Upload", "Upload file lokal ke direktori ini (atau drag & drop dari Explorer)", this::chooseUpload));
        toolbar.add(button(AppIcon.DOWNLOAD, "Download", "Download file terpilih", this::downloadSelected));
        toolbar.addSeparator();
        toolbar.add(button(AppIcon.FOLDER_PLUS, null, "Direktori baru (F7)", this::mkdir));
        toolbar.add(button(AppIcon.PEN, null, "Rename (F2)", this::renameSelected));
        toolbar.add(button(AppIcon.PERMISSION, null, "chmod: ubah permission", this::chmodSelected));
        toolbar.add(button(AppIcon.TRASH, null, "Hapus (Delete)", this::deleteSelected));
        bind(KeyEvent.VK_F2, 0, "rename", this::renameSelected);
        bind(KeyEvent.VK_F7, 0, "mkdir", this::mkdir);
        bind(KeyEvent.VK_DELETE, 0, "delete", this::deleteSelected);
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

    private static final javax.swing.Icon DIR_ICON = AppIcon.FOLDER.icon(AppIcon.SIZE, () -> AppIcon.FOLDER_COLOR);
    private static final javax.swing.Icon FILE_ICON = AppIcon.FILE.icon();
    private static final javax.swing.Icon LINK_ICON = AppIcon.LINK.icon();

    private static javax.swing.Icon entryIcon(RemoteEntry e) {
        return switch (e.type()) {
            case DIRECTORY -> DIR_ICON;
            case SYMLINK -> LINK_ICON;
            case FILE, OTHER -> FILE_ICON;
        };
    }

    private JPopupMenu buildPopup() {
        var menu = new JPopupMenu();
        var edit = menuItem("Edit", () -> editSelected(null));
        var editWith = new javax.swing.JMenu("Edit dengan");
        menu.add(edit);
        menu.add(editWith);
        menu.addSeparator();
        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                selectRowUnderPointer();
                boolean file = selectedEntries().stream().anyMatch(en -> en.type() == RemoteEntry.Type.FILE);
                edit.setEnabled(file);
                editWith.setEnabled(file);
                rebuildEditWith(editWith);
            }

            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
            }
        });
        menu.add(menuItem("Download...", this::downloadSelected));
        menu.add(menuItem("Upload ke sini...", this::chooseUpload));
        menu.addSeparator();
        menu.add(menuItem("Rename...", this::renameSelected));
        menu.add(menuItem("chmod...", this::chmodSelected));
        menu.add(menuItem("Hapus...", this::deleteSelected));
        menu.addSeparator();
        menu.add(menuItem("Direktori baru...", this::mkdir));
        menu.add(menuItem("Salin path", () -> selectedEntries().stream().findFirst().ifPresent(e ->
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(e.path()), null))));
        menu.add(menuItem("Refresh", this::refresh));
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
        menu.add(menuItem("Konfigurasi editor...", editActions::configure));
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
        chooser.setDialogTitle("Upload ke " + currentDir);
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
            activity.report(Level.INFO, "Upload " + name + " ke " + dir + " diantrekan");
            transfers.submit("Upload " + name, listener -> {
                activity.report(Level.INFO, "Mengupload " + name + " ke " + dir + " ...");
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
                    activity.report(Level.WARN, "Upload " + name + " dilewati (file sudah ada dan tidak ditimpa)");
                    return;
                }
                activity.report(Level.SUCCESS, name + " berhasil diupload ke " + dir);
                if (dir.equals(currentDir)) {
                    refresh();
                }
            }, err -> {
                activity.report(Level.ERROR, "Upload " + name + " gagal: " + err.getMessage());
                Dialogs.error(this, "Upload gagal", err);
            }, () -> activity.report(Level.WARN, "Upload " + name + " dibatalkan"));
        }
        if (!skipped.isEmpty()) {
            Dialogs.info(this, "Upload", "Upload direktori belum didukung, dilewati: " + String.join(", ", skipped));
        }
    }

    private boolean confirmOverwrite(String target) {
        var ok = new AtomicBoolean();
        Edt.runAndWait(() -> ok.set(Dialogs.confirm(this, "File sudah ada", "Timpa " + target + "?")));
        return ok.get();
    }

    private void downloadSelected() {
        var selected = selectedEntries().stream().filter(e -> e.type() == RemoteEntry.Type.FILE).toList();
        if (selected.isEmpty()) {
            return;
        }
        var chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Download " + selected.size() + " file ke...");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path targetDir = chooser.getSelectedFile().toPath();
        for (RemoteEntry entry : selected) {
            Path target = targetDir.resolve(entry.name());
            var skipped = new AtomicBoolean();
            activity.report(Level.INFO, "Download " + entry.name() + " diantrekan");
            transfers.submit("Download " + entry.name(), listener -> {
                activity.report(Level.INFO, "Mengunduh " + entry.path() + " ...");
                link.execute(() -> {
                    if (Files.exists(target) && !confirmOverwrite(target.toString())) {
                        skipped.set(true);
                        return null;
                    }
                    svc().download(entry.path(), target, listener);
                    return null;
                });
            }, () -> activity.report(skipped.get() ? Level.WARN : Level.SUCCESS,
                    skipped.get() ? "Download " + entry.name() + " dilewati (file lokal sudah ada)"
                            : entry.name() + " berhasil diunduh ke " + target),
                    err -> {
                        activity.report(Level.ERROR, "Download " + entry.name() + " gagal: " + err.getMessage());
                        Dialogs.error(this, "Download gagal", err);
                    }, () -> activity.report(Level.WARN, "Download " + entry.name() + " dibatalkan"));
        }
    }

    // ------------------------------------------------------------------ operasi file

    private void mkdir() {
        if (link == null || currentDir == null) {
            return;
        }
        String name = Dialogs.input(this, "Direktori baru", "Nama direktori di " + currentDir + ":", "");
        if (name != null) {
            String path = RemotePaths.join(currentDir, name);
            call("Membuat direktori " + path, "Direktori " + path + " dibuat", () -> {
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
        String name = Dialogs.input(this, "Rename", "Nama baru untuk " + entry.name() + ":", entry.name());
        if (name != null && !name.equals(entry.name())) {
            String target = RemotePaths.join(RemotePaths.parent(entry.path()), name);
            call("Mengganti nama " + entry.name() + " menjadi " + name,
                    entry.name() + " diganti namanya menjadi " + name, () -> {
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
        String text = Dialogs.input(this, "chmod", "Mode oktal untuk " + selected.size() + " item (mis. 644, 0755):", initial);
        if (text == null) {
            return;
        }
        int mode;
        try {
            mode = Formats.parseMode(text);
        } catch (IllegalArgumentException e) {
            Dialogs.error(this, "chmod", e.getMessage());
            return;
        }
        String modeText = String.format("%04o", mode);
        call("Mengubah permission " + selected.size() + " item menjadi " + modeText,
                "Permission " + selected.size() + " item diubah menjadi " + modeText, () -> {
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
        String what = selected.size() == 1 ? selected.getFirst().path() : selected.size() + " item";
        String warning = dirs > 0 ? "\n\nPERHATIAN: direktori dihapus beserta seluruh isinya." : "";
        if (!Dialogs.confirm(this, "Hapus", "Hapus " + what + " secara permanen?" + warning)) {
            return;
        }
        call("Menghapus " + what, what + " dihapus", () -> {
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
        if (link != null || connecting) {
            return;
        }
        connecting = true;
        activity.progress("Membuka SFTP ke " + profile.address() + " ...");
        UiAsync.run(sshOps, () -> {
            SftpConnection l = links.acquire(profile);
            Runnable unsubscribe = l.addListener(this::onLinkStatus);
            try {
                var svc = l.ensureLive();
                String start = profile.initialDirectory() != null
                        ? svc.canonicalize(profile.initialDirectory()) : svc.home();
                return new Start(l, unsubscribe, start);
            } catch (Exception e) {
                unsubscribe.run();
                links.release(profile);
                throw new CompletionException(e);
            }
        }, start -> {
            connecting = false;
            if (disposed) { // tab ditutup selagi menyambung
                start.unsubscribe().run();
                sshOps.execute(() -> links.release(profile));
                return;
            }
            link = start.link();
            unsubscribeLink = start.unsubscribe();
            unsubscribeEdits = editActions.onActivity(profile.id(), activity::report);
            navigate(start.dir());
        }, err -> {
            connecting = false;
            activity.report(Level.ERROR, "SFTP gagal: " + err.getMessage());
        });
    }

    private record Start(SftpConnection link, Runnable unsubscribe, String dir) {
    }

    /**
     * Status koneksi SFTP, yang mengikuti sesi terminal tab ini (dipanggil bukan di EDT). Reconnect dilakukan dari
     * terminal; panel hanya menampilkan status dan memuat ulang daftar begitu tersambung kembali.
     */
    private void onLinkStatus(SftpConnection.Status st) {
        switch (st.state()) {
            case CONNECTED -> {
                boolean again = link != null;
                activity.report(Level.SUCCESS, again ? st.message() : "Tersambung ke " + profile.address());
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
        activity.progress("Memuat " + dir + " ...");
        call(() -> {
            String canonical = svc().canonicalize(dir);
            return new Listing(canonical, svc().list(canonical));
        }, listing -> {
            currentDir = listing.dir();
            pathField.setText(listing.dir());
            model.setEntries(listing.entries());
            activity.report(Level.INFO, listing.dir() + ": " + listing.entries().size() + " item");
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

    /** Membuka file di editor lokal (auto-upload saat disimpan). */
    protected void openFile(RemoteEntry entry) {
        editActions.edit(profile, entry.path(), null);
    }

    private void editSelected(String command) {
        selectedEntries().stream().filter(e -> e.type() == RemoteEntry.Type.FILE).findFirst().ifPresent(entry -> {
            editActions.edit(profile, entry.path(), command);
        });
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
            activity.report(Level.ERROR, (doing != null ? doing + " gagal: " : "Gagal: ") + err.getMessage());
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
        table.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    /** Menutup SFTP (tab ditutup). Koneksi bersama baru ditutup kalau tidak dipakai lagi (mis. sesi edit). */
    public void dispose() {
        disposed = true;
        transfers.shutdown();
        unsubscribeLink.run();
        unsubscribeEdits.run();
        var l = link;
        link = null;
        if (l != null) {
            sshOps.execute(() -> links.release(profile));
        }
    }
}
