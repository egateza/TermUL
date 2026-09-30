package dev.egateza.myterm.app.sftp;

import dev.egateza.myterm.app.ui.Dialogs;
import dev.egateza.myterm.app.ui.UiAsync;
import dev.egateza.myterm.core.profile.HostProfile;
import dev.egateza.myterm.sftp.RemoteEntry;
import dev.egateza.myterm.sftp.RemoteFileService;
import dev.egateza.myterm.sftp.RemotePaths;
import dev.egateza.myterm.ssh.SessionManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
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
    protected final SessionManager sessions;
    protected final ExecutorService sshOps;
    protected final SftpTableModel model = new SftpTableModel();
    protected final JTable table = new JTable(model);
    protected final JTextField pathField = new JTextField();
    protected final JLabel status = new JLabel(" ");
    protected final JToolBar toolbar = new JToolBar();
    private volatile RemoteFileService service; // ditulis sekali setelah connect
    protected String currentDir;                 // EDT
    private boolean connecting;                  // EDT

    public SftpPanel(HostProfile profile, SessionManager sessions, ExecutorService sshOps) {
        super(new BorderLayout());
        this.profile = profile;
        this.sessions = sessions;
        this.sshOps = sshOps;

        toolbar.setFloatable(false);
        toolbar.add(button("↑", "Direktori induk (Backspace)", this::goUp));
        toolbar.add(button("⌂", "Home", this::goHome));
        toolbar.add(button("⟳", "Refresh (F5)", this::refresh));
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

        add(new JScrollPane(table), BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        add(status, BorderLayout.SOUTH);
    }

    /** Connect (sekali) lalu buka direktori awal profil atau home. */
    public void connect() {
        if (service != null || connecting) {
            return;
        }
        connecting = true;
        status.setText("Membuka SFTP ke " + profile.address() + " ...");
        UiAsync.run(sshOps, () -> {
            try {
                var svc = RemoteFileService.open(sessions.acquire(profile));
                String start = profile.initialDirectory() != null ? svc.canonicalize(profile.initialDirectory()) : svc.home();
                return new Start(svc, start);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, start -> {
            connecting = false;
            service = start.service();
            navigate(start.dir());
        }, err -> {
            connecting = false;
            status.setText("SFTP gagal: " + err.getMessage());
        });
    }

    private record Start(RemoteFileService service, String dir) {
    }

    public boolean isConnected() {
        return service != null;
    }

    public String currentDir() {
        return currentDir;
    }

    public void navigate(String dir) {
        if (service == null || dir.isEmpty()) {
            return;
        }
        status.setText("Memuat " + dir + " ...");
        call(() -> {
            String canonical = service.canonicalize(dir);
            return new Listing(canonical, service.list(canonical));
        }, listing -> {
            currentDir = listing.dir();
            pathField.setText(listing.dir());
            model.setEntries(listing.entries());
            status.setText(listing.entries().size() + " item");
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
        if (service != null) {
            call(service::home, this::navigate);
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

    /** Hook untuk membuka file (Fase 4: edit dengan editor lokal). */
    protected void openFile(RemoteEntry entry) {
        status.setText(entry.name() + ": " + Formats.size(entry.size()));
    }

    protected List<RemoteEntry> selectedEntries() {
        var result = new ArrayList<RemoteEntry>();
        for (int viewRow : table.getSelectedRows()) {
            result.add(model.entryAt(table.convertRowIndexToModel(viewRow)));
        }
        return result;
    }

    protected RemoteFileService service() {
        return service;
    }

    /** Menjalankan operasi remote di {@code sshOps}; error ditampilkan sebagai dialog. */
    protected <T> void call(Callable<T> work, Consumer<T> onSuccess) {
        UiAsync.run(sshOps, () -> {
            try {
                return work.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, onSuccess, err -> {
            status.setText(" ");
            log.info("Operasi SFTP gagal: {}", err.getMessage());
            Dialogs.error(this, "SFTP", err);
        });
    }

    protected static JButton button(String text, String tooltip, Runnable action) {
        var b = new JButton(text);
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

    /** Menutup SFTP (tab ditutup). */
    public void dispose() {
        var svc = service;
        service = null;
        if (svc != null) {
            sshOps.execute(svc::close);
        }
    }
}
