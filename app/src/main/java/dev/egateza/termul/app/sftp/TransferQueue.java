package dev.egateza.termul.app.sftp;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.AppIcon;
import dev.egateza.termul.sftp.RemoteFileException;
import dev.egateza.termul.sftp.TransferListener;
import java.awt.BorderLayout;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Antrean transfer berurutan (satu per satu) dengan progress bar dan tombol batal.
 * Transfer berjalan di thread sendiri; update UI di-throttle (maks ±10x/detik) supaya
 * transfer besar tidak membanjiri EDT.
 */
public final class TransferQueue extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(TransferQueue.class);
    private static final long UI_INTERVAL_NANOS = 100_000_000L;

    /** Pekerjaan transfer; memakai listener yang diberikan untuk progress + cek batal. */
    @FunctionalInterface
    public interface Job {
        void run(TransferListener listener) throws Exception;
    }

    private final ExecutorService worker;
    private final JProgressBar bar = new JProgressBar(0, 1000);
    private final JLabel label = new JLabel();
    private final JButton cancel = new JButton(I18n.t("transfer.cancel"), AppIcon.XMARK.icon());
    private final AtomicInteger queued = new AtomicInteger();
    private volatile AtomicBoolean currentCancel = new AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicLong ids = new java.util.concurrent.atomic.AtomicLong();
    private final TransferList list = new TransferList(); // EDT
    private final java.util.List<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public TransferQueue(String name) {
        super(new BorderLayout(6, 0));
        // jarak tepi sama dengan ActivityBar di bawahnya: label & tombol Batal tidak menempel ke sudut panel membulat
        setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 6, 3, 6));
        this.worker = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("sftp-transfer-" + name).factory());
        bar.setStringPainted(true);
        cancel.addActionListener(e -> currentCancel.set(true));
        add(label, BorderLayout.WEST);
        add(bar, BorderLayout.CENTER);
        add(cancel, BorderLayout.EAST);
        setVisible(false);
    }

    /**
     * @param title    teks yang ditampilkan, mis. "Upload config.yml"
     * @param job      pekerjaan (di thread transfer)
     * @param onDone   dipanggil di EDT setelah sukses
     * @param onError  dipanggil di EDT kalau gagal (tidak dipanggil kalau dibatalkan)
     */
    public void submit(String title, Job job, Runnable onDone, Consumer<Throwable> onError) {
        submit(title, job, onDone, onError, () -> { });
    }

    /** @param onCancelled dipanggil di EDT kalau transfer dibatalkan user */
    public void submit(String title, Job job, Runnable onDone, Consumer<Throwable> onError, Runnable onCancelled) {
        queued.incrementAndGet();
        setVisible(true);
        long id = ids.incrementAndGet();
        onEdt(() -> {
            list.add(id, title);
            fireChanged();
        });
        worker.execute(() -> {
            var cancelFlag = new AtomicBoolean();
            currentCancel = cancelFlag;
            SwingUtilities.invokeLater(() -> {
                list.start(id);
                fireChanged();
                showLabel(title, queued.get() - 1);
                bar.setValue(0);
                bar.setString("0%");
                cancel.setEnabled(true);
            });
            var listener = new TransferListener() {
                private long lastUi;

                @Override
                public void progress(long done, long total) {
                    long now = System.nanoTime();
                    if (now - lastUi < UI_INTERVAL_NANOS && done != total) {
                        return;
                    }
                    lastUi = now;
                    SwingUtilities.invokeLater(() -> {
                        list.progress(id, done, total);
                        fireChanged();
                        if (total > 0) {
                            bar.setIndeterminate(false);
                            bar.setValue((int) (done * 1000 / total));
                            bar.setString(Formats.size(done) + " / " + Formats.size(total));
                        } else {
                            bar.setIndeterminate(true);
                            bar.setString(Formats.size(done));
                        }
                    });
                }

                @Override
                public boolean isCancelled() {
                    return cancelFlag.get() || Thread.currentThread().isInterrupted();
                }
            };
            Throwable error = null;
            boolean cancelled = false;
            try {
                job.run(listener);
            } catch (RemoteFileException.Cancelled e) {
                cancelled = true;
                log.info("{} dibatalkan", title);
            } catch (Exception e) {
                error = e;
            }
            Throwable finalError = error;
            boolean finalCancelled = cancelled || cancelFlag.get();
            SwingUtilities.invokeLater(() -> {
                list.remove(id);
                fireChanged();
                if (queued.decrementAndGet() == 0) {
                    setVisible(false);
                }
                if (finalError != null) {
                    onError.accept(finalError);
                } else if (finalCancelled) {
                    onCancelled.run();
                } else {
                    onDone.run();
                }
            });
        });
    }

    /** Lebar maksimum label transfer, px; nama yang lebih panjang disingkat di tengah (lengkapnya di tooltip). */
    static final int MAX_LABEL_WIDTH = 260;

    /** Label "Download nama (+N antre)" dengan nama disingkat supaya progress bar tidak terdesak. EDT. */
    private void showLabel(String title, int waiting) {
        var fm = label.getFontMetrics(label.getFont());
        String suffix = waiting > 0 ? I18n.t("transfer.label", "", String.valueOf(waiting)) : "";
        int max = getWidth() > 0 ? Math.min(MAX_LABEL_WIDTH, getWidth() * 2 / 5) : MAX_LABEL_WIDTH;
        String shown = Formats.elideMiddle(title, Math.max(40, max - fm.stringWidth(suffix)), fm::stringWidth);
        label.setText(waiting > 0 ? I18n.t("transfer.label", shown, String.valueOf(waiting)) : shown);
        label.setToolTipText(shown.equals(title) ? null : title);
    }

    /** Transfer yang berjalan + antre, sesuai urutan antrean. EDT. */
    List<TransferList.Item> transfers() {
        return list.items();
    }

    /**
     * Dipanggil di EDT setiap daftar transfer atau progresnya berubah (progres paling sering ±10x/detik).
     *
     * @return pelepas listener
     */
    public Runnable addChangeListener(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private void fireChanged() {
        listeners.forEach(Runnable::run);
    }

    private static void onEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    /** Jumlah transfer yang sedang berjalan + antre. */
    public int activeCount() {
        return queued.get();
    }

    public void shutdown() {
        currentCancel.set(true);
        worker.shutdownNow();
    }
}
