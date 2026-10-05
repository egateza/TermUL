package dev.egateza.termul.app.update;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.core.AppPaths;
import dev.egateza.termul.update.InstallPlan;
import dev.egateza.termul.update.SignedRelease;
import dev.egateza.termul.update.UpdateClient;
import dev.egateza.termul.update.UpdateException;
import dev.egateza.termul.update.UpdateProtocol;
import dev.egateza.termul.update.Updater;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bantuan → Periksa update. Memeriksa rilis terbaru di GitHub, lalu mengunduh dan memasangnya ke folder update
 * (aktif saat TermUL dibuka ulang). Jaringan dan disk berjalan di virtual thread; UI hanya disentuh di EDT.
 * Menutup dialog membatalkan pekerjaan yang sedang jalan (interrupt), versi terpasang tidak berubah.
 */
public final class UpdateDialog extends JDialog {

    private static final Logger log = LoggerFactory.getLogger(UpdateDialog.class);

    private final Consumer<List<String>> restart;
    private final UpdateClient client = new UpdateClient();
    private final Updater updater;
    private final JTextArea status = new JTextArea();
    private final JTextArea notes = new JTextArea(8, 50);
    private final JScrollPane notesScroll = new JScrollPane(notes);
    private final JProgressBar progress = new JProgressBar();
    private final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    /** Pekerjaan background yang sedang jalan; hanya diakses di EDT. */
    private Thread worker;

    /** @param restart dipanggil di EDT dengan perintah buka ulang; pemanggil yang menutup aplikasi */
    public UpdateDialog(Window owner, AppPaths paths, Consumer<List<String>> restart) {
        super(owner, I18n.t("update.title"), ModalityType.APPLICATION_MODAL);
        this.restart = restart;
        this.updater = AppUpdates.updater(paths, client);

        status.setEditable(false);
        status.setFocusable(false);
        status.setOpaque(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setFont(UIManager.getFont("Label.font"));
        status.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notesScroll.setBorder(BorderFactory.createTitledBorder(I18n.t("update.notes")));
        notesScroll.setVisible(false);
        progress.setStringPainted(false);

        var center = new JPanel(new BorderLayout(0, 8));
        center.add(status, BorderLayout.NORTH);
        center.add(notesScroll, BorderLayout.CENTER);
        center.add(progress, BorderLayout.SOUTH);
        var content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        content.add(center, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(460, 160));

        check();
        pack();
        setLocationRelativeTo(owner);
    }

    @Override
    public void dispose() {
        if (worker != null) {
            worker.interrupt();
        }
        client.close();
        super.dispose();
    }

    private void check() {
        showStatus(I18n.t("update.checking"), true);
        setButtons();
        runInBackground("update-check", () -> {
            Updater.Check result = updater.check();
            log.info("Periksa update: {} (versi berjalan {})", result, updater.environment().running());
            return result;
        }, this::showResult, err -> {
            showStatus(I18n.t("update.error", err.getMessage()), false);
            setButtons(button(I18n.t("update.retry"), this::check), closeButton());
        });
    }

    private void showResult(Updater.Check result) {
        SignedRelease latest = result.latest();
        String version = latest.version().toString();
        switch (result) {
            case Updater.UpToDate _ -> {
                showStatus(I18n.t("update.upToDate", runningText()), false);
                setButtons(closeButton());
            }
            case Updater.Available(var r, var plan) -> {
                showStatus(I18n.t("update.available", version, runningText(), bytes(plan.downloadBytes())), false);
                showNotes(r);
                setButtons(button(I18n.t("update.install"), () -> install(plan)), button(I18n.t("update.later"),
                        this::dispose));
            }
            case Updater.ManualOnly(var r, var reason) -> {
                String key = switch (reason) {
                    case DEV_BUILD -> "update.manual.dev";
                    case NO_BOOTSTRAP -> "update.manual.noBootstrap";
                    case NEEDS_INSTALLER -> "update.manual.installer";
                };
                showStatus(I18n.t(key, version), false);
                showNotes(r);
                setButtons(button(I18n.t("update.openPage"), () -> openReleasePage(r)), closeButton());
            }
        }
        pack();
    }

    private void install(InstallPlan plan) {
        long total = plan.downloadBytes();
        var received = new AtomicLong();
        showStatus(I18n.t("update.downloading", bytes(0), bytes(total)), true);
        progress.setIndeterminate(total == 0); // semua jar tersedia lokal: hanya disalin
        progress.setMaximum(1000);
        progress.setValue(0);
        setButtons(button(I18n.t("update.cancel"), this::dispose));
        runInBackground("update-install", () -> updater.install(plan, n -> {
            long done = received.addAndGet(n);
            SwingUtilities.invokeLater(() -> {
                status.setText(I18n.t("update.downloading", bytes(done), bytes(total)));
                progress.setValue(total == 0 ? 0 : (int) (done * 1000 / total));
            });
        }), installed -> {
            String version = installed.manifest().version().toString();
            log.info("Update {} terpasang di {}", version, installed.dir());
            Optional<List<String>> command = RestartCommand.current();
            if (command.isPresent()) {
                showStatus(I18n.t("update.installed", version), false);
                setButtons(button(I18n.t("update.restartNow"), () -> {
                    super.dispose(); // pekerjaan sudah selesai; restart menutup aplikasi
                    restart.accept(command.get());
                }), button(I18n.t("update.later"), this::dispose));
            } else {
                showStatus(I18n.t("update.restartManual", version), false);
                setButtons(closeButton());
            }
        }, err -> {
            showStatus(I18n.t("update.installError", err.getMessage()), false);
            setButtons(button(I18n.t("update.retry"), () -> install(plan)), closeButton());
        });
    }

    /** Pekerjaan blocking yang bisa dibatalkan. {@code InterruptedException} = dialog ditutup, diam saja. */
    private <T> void runInBackground(String name, Work<T> work, Consumer<T> onSuccess, Consumer<Exception> onError) {
        worker = Thread.ofVirtual().name(name).start(() -> {
            try {
                T result = work.run();
                SwingUtilities.invokeLater(() -> {
                    if (isDisplayable()) {
                        onSuccess.accept(result);
                    }
                });
            } catch (InterruptedException e) {
                log.info("{} dibatalkan", name);
            } catch (UpdateException | IOException | RuntimeException e) {
                log.warn("{} gagal", name, e);
                SwingUtilities.invokeLater(() -> {
                    if (isDisplayable()) {
                        onError.accept(e);
                    }
                });
            }
        });
    }

    @FunctionalInterface
    private interface Work<T> {
        T run() throws UpdateException, IOException, InterruptedException;
    }

    private void showStatus(String text, boolean busy) {
        status.setText(text);
        progress.setIndeterminate(busy);
        progress.setVisible(busy);
    }

    private void showNotes(SignedRelease release) {
        String text = release.manifest().notes();
        notes.setText(text);
        notes.setCaretPosition(0);
        notesScroll.setVisible(!text.isBlank());
    }

    private void setButtons(JButton... list) {
        buttons.removeAll();
        for (JButton b : list) {
            buttons.add(b);
        }
        if (list.length > 0) {
            getRootPane().setDefaultButton(list[0]);
        }
        buttons.revalidate();
        buttons.repaint();
    }

    private JButton closeButton() {
        return button(I18n.t("update.close"), this::dispose);
    }

    private static JButton button(String text, Runnable action) {
        var b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    private String runningText() {
        var running = updater.environment().running();
        return running == null ? "dev" : running.toString();
    }

    private void openReleasePage(SignedRelease release) {
        URI page = UpdateProtocol.RELEASES.resolve("tag/" + release.version().tag());
        Thread.ofVirtual().name("open-release-page").start(() -> {
            try {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    throw new IOException("browser tidak tersedia");
                }
                Desktop.getDesktop().browse(page);
            } catch (IOException | RuntimeException e) {
                log.warn("Halaman rilis tidak bisa dibuka: {}", e.toString());
                SwingUtilities.invokeLater(() -> status.setText(status.getText() + "\n\n" + page));
            }
        });
    }

    /** Ukuran untuk dibaca manusia, mis. {@code 742 KB}, {@code 7,2 MB}. */
    static String bytes(long n) {
        if (n < 1024) {
            return n + " B";
        }
        if (n < 1024 * 1024) {
            return Math.round(n / 1024.0) + " KB";
        }
        return String.format(Locale.forLanguageTag(I18n.current()), "%.1f MB", n / 1048576.0);
    }
}
