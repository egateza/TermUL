package dev.egateza.termul.app.monitor;

import dev.egateza.termul.app.i18n.I18n;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sampler resource JVM yang untuk bar "Host status". Sampel diambil
 * tiap detik di thread daemon sendiri (tepat setelah pergantian detik, supaya jam di bar bawah tidak melompat), lalu
 * diteruskan ke EDT. Sampler hanya jalan selama bar terlihat ({@link #setActive}). Riwayat grafik dibatasi
 * {@value #HISTORY} sampel. Semua method dipanggil di EDT kecuali disebut lain.
 */
public final class ResourceMonitor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ResourceMonitor.class);

    static final int HISTORY = 60;
    private static final long PERIOD_MS = 1000;

    private final Executor io;
    private final History cpu = new History(HISTORY);
    private final History heap = new History(HISTORY);
    private final List<Consumer<JvmSample>> views = new ArrayList<>(); // EDT
    private ScheduledExecutorService timer; // EDT; null = berhenti
    private JvmSample last; // EDT

    /** @param io executor untuk GC manual (supaya tidak di EDT) */
    public ResourceMonitor(Executor io) {
        this.io = io;
    }

    History cpuHistory() {
        return cpu;
    }

    History heapHistory() {
        return heap;
    }

    /** Sampel terakhir, atau null kalau belum ada. */
    JvmSample last() {
        return last;
    }

    /** Didaftarkan oleh tampilan; dipanggil di EDT setiap ada sampel baru. */
    void addView(Consumer<JvmSample> view) {
        views.add(view);
        if (last != null) {
            view.accept(last);
        }
    }

    /** Jalankan sampler selama bar terlihat; hentikan kalau disembunyikan (tidak memakai CPU sama sekali). */
    public void setActive(boolean active) {
        if (active == (timer != null)) {
            return;
        }
        if (!active) {
            timer.shutdownNow();
            timer = null;
            return;
        }
        var sampler = new JvmSampler();
        timer = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("jvm-monitor").factory());
        Runnable tick = () -> {
            try {
                var sample = sampler.sample();
                SwingUtilities.invokeLater(() -> publish(sample));
            } catch (RuntimeException e) {
                log.warn("Gagal membaca resource JVM", e);
            }
        };
        timer.execute(tick); // sampel pertama langsung (CPU-nya masih NaN)
        long delay = PERIOD_MS - System.currentTimeMillis() % PERIOD_MS + 20; // selaras dengan pergantian detik
        timer.scheduleAtFixedRate(tick, delay, PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    private void publish(JvmSample sample) {
        if (timer == null) {
            return; // sampel yang terlambat datang setelah dihentikan
        }
        last = sample;
        cpu.add(sample.processCpu());
        heap.add(sample.heapRatio());
        for (var v : views) {
            v.accept(sample);
        }
    }

    /** Minta JVM menjalankan GC sekarang (di thread io). Hasilnya terlihat di sampel berikutnya. */
    public void runGc() {
        io.execute(() -> {
            log.info("GC manual diminta dari monitor resource");
            System.gc();
        });
    }

    @Override
    public void close() {
        setActive(false);
    }

    // --- format teks untuk tampilan ---

    /** @return mis. {@code 7%}, atau {@code –} kalau tidak diketahui */
    static String percent(double ratio) {
        return Double.isNaN(ratio) ? "–" : Math.round(ratio * 100) + "%";
    }

    /** @return ukuran dalam MB (atau GB kalau ≥ 10 GB), mis. {@code 123 MB}; {@code –} kalau tidak diketahui */
    static String size(long bytes) {
        if (bytes < 0) {
            return "–";
        }
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 10 * 1024) {
            return String.format(Locale.ROOT, "%.1f GB", mb / 1024);
        }
        return Math.round(mb) + " MB";
    }

    /** Tooltip rinci untuk grafik monitor. */
    static String tooltip(JvmSample s) {
        if (s == null) {
            return I18n.t("monitor.tooltip.waiting");
        }
        String physical = s.physicalTotal() > 0
                ? I18n.t("monitor.tooltip.physical", size(s.physicalTotal() - s.physicalFree()), size(s.physicalTotal()))
                : "";
        return "<html>" + I18n.t("monitor.tooltip",
                percent(s.processCpu()), String.valueOf(Runtime.getRuntime().availableProcessors()),
                percent(s.systemCpu()),
                size(s.heapUsed()), size(s.heapLimit()), percent(s.heapRatio()), size(s.heapCommitted()),
                s.heapMax() > 0 ? I18n.t("monitor.tooltip.limitXmx") : I18n.t("monitor.tooltip.limitCommitted"),
                size(s.nonHeapUsed()), String.valueOf(s.threads()), String.valueOf(s.gcCount()),
                String.valueOf(s.gcMillis()))
                + physical + "<br><br><i>" + I18n.t("monitor.tooltip.hint") + "</i></html>";
    }
}
