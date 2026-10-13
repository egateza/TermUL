package dev.egateza.termul.app.monitor;

import dev.egateza.termul.app.i18n.I18n;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Indikator status internet di panel bawah: titik hijau + latensi saat online (kuning kalau lambat), merah
 * "Offline" saat terputus. Pemeriksaan ({@link NetworkProbe}) berjalan di thread daemon sendiri hanya selama
 * komponen terpasang: tiap {@value #ONLINE_PERIOD_MS} ms saat online, lebih sering saat offline supaya pulihnya cepat
 * terlihat. Klik kiri memeriksa sekarang. EDT.
 */
public final class NetworkIndicator extends JLabel {

    private static final Logger log = LoggerFactory.getLogger(NetworkIndicator.class);

    static final long ONLINE_PERIOD_MS = 15_000;
    static final long OFFLINE_PERIOD_MS = 3_000;
    /** Latensi TCP handshake mulai dianggap lambat. */
    static final long SLOW_MS = 300;
    static final Color GREEN = new Color(0x30A46C);
    static final Color YELLOW = new Color(0xE0A000);
    static final Color RED = new Color(0xE5484D);
    static final Color GRAY = new Color(0x8B8D98);
    private static final int DOT = 8;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final NetworkProbe probe;
    private ScheduledExecutorService timer; // EDT; null = berhenti
    private long generation;                 // EDT; hasil dari generasi lama diabaikan
    private NetworkProbe.Status status;      // EDT; null = belum pernah diperiksa
    private Color dot = GRAY;                // EDT

    public NetworkIndicator() {
        this(new NetworkProbe());
    }

    NetworkIndicator(NetworkProbe probe) {
        this.probe = probe;
        setIcon(new Dot());
        setIconTextGap(5);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        getAccessibleContext().setAccessibleName(I18n.t("net.name"));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    checkNow();
                }
            }
        });
        show(null);
    }

    /** Hasil pemeriksaan terakhir, atau null kalau belum ada. */
    NetworkProbe.Status status() {
        return status;
    }

    /** Periksa sekarang (membatalkan jadwal berikutnya); tidak berbuat apa-apa kalau komponen belum terpasang. */
    public void checkNow() {
        if (timer != null) {
            setText(I18n.t("net.checking"));
            schedule(0);
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (timer == null) {
            timer = Executors.newSingleThreadScheduledExecutor(
                    Thread.ofPlatform().daemon().name("net-probe").factory());
            schedule(0);
        }
    }

    @Override
    public void removeNotify() {
        if (timer != null) {
            timer.shutdownNow();
            timer = null;
        }
        super.removeNotify();
    }

    private void schedule(long delayMs) {
        long gen = ++generation;
        timer.schedule(() -> {
            var result = probe.probe();
            SwingUtilities.invokeLater(() -> publish(gen, result));
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void publish(long gen, NetworkProbe.Status result) {
        if (timer == null || gen != generation) {
            return; // komponen sudah dilepas, atau sudah ada pemeriksaan yang lebih baru
        }
        if (status != null && online(status) != online(result)) {
            log.info(online(result) ? "Koneksi internet pulih ({})" : "Koneksi internet terputus ({})", detail(result));
        }
        show(result);
        schedule(online(result) ? ONLINE_PERIOD_MS : OFFLINE_PERIOD_MS);
    }

    void show(NetworkProbe.Status result) {
        status = result;
        dot = color(result);
        setText(text(result));
        setForeground(result instanceof NetworkProbe.Status.Offline ? RED : null);
        setToolTipText(tooltip(result));
        repaint();
    }

    // --- tampilan ---

    private static boolean online(NetworkProbe.Status s) {
        return s instanceof NetworkProbe.Status.Online;
    }

    private static String detail(NetworkProbe.Status s) {
        return switch (s) {
            case NetworkProbe.Status.Online(var target, var latency, _) -> target + ", " + latency.toMillis() + " ms";
            case NetworkProbe.Status.Offline(var tried, _) -> tried;
        };
    }

    static Color color(NetworkProbe.Status s) {
        return switch (s) {
            case null -> GRAY;
            case NetworkProbe.Status.Online o -> o.latency().toMillis() >= SLOW_MS ? YELLOW : GREEN;
            case NetworkProbe.Status.Offline _ -> RED;
        };
    }

    static String text(NetworkProbe.Status s) {
        return switch (s) {
            case null -> I18n.t("net.checking");
            case NetworkProbe.Status.Online o -> I18n.t("net.latency", String.valueOf(o.latency().toMillis()));
            case NetworkProbe.Status.Offline _ -> I18n.t("net.offline");
        };
    }

    static String tooltip(NetworkProbe.Status s) {
        String body = switch (s) {
            case null -> I18n.t("net.tooltip.checking");
            case NetworkProbe.Status.Online(var target, var latency, var at) -> I18n.t("net.tooltip.online",
                    target, String.valueOf(latency.toMillis()), time(at))
                    + (latency.toMillis() >= SLOW_MS ? "<br>" + I18n.t("net.tooltip.slow") : "");
            case NetworkProbe.Status.Offline(var tried, var at) -> I18n.t("net.tooltip.offline",
                    tried, time(at), String.valueOf(OFFLINE_PERIOD_MS / 1000));
        };
        return "<html>" + body + "<br><br><i>" + I18n.t("net.tooltip.hint") + "</i></html>";
    }

    private static String time(Instant at) {
        return TIME.format(at.atZone(ZoneId.systemDefault()));
    }

    /** Titik bulat berwarna status. */
    private final class Dot implements Icon {

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            var g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(dot);
                g2.fillOval(x, y, DOT, DOT);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return DOT;
        }

        @Override
        public int getIconHeight() {
            return DOT;
        }
    }
}
