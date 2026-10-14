package dev.egateza.termul.app.ui.streak;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.monitor.NetworkIndicator;
import dev.egateza.termul.app.monitor.NetworkProbe;
import dev.egateza.termul.app.ui.UiAsync;
import dev.egateza.termul.core.streak.Streak;
import dev.egateza.termul.core.streak.StreakEvent;
import dev.egateza.termul.core.streak.StreakStatus;
import dev.egateza.termul.core.streak.StreakStore;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Point2D;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Streak api: memuat {@code streak.json}, menghitung hari saat aplikasi dibuka (dan saat tanggal berganti selagi
 * aplikasi terbuka), memutar animasi besar, dan menangani klik pada api (triple klik untuk yang padam, klik berulang
 * untuk memecahkan es). Disk I/O di executor {@code io}; sisanya EDT.
 */
public final class StreakController {

    private static final Logger log = LoggerFactory.getLogger(StreakController.class);
    /** Selang memeriksa pergantian tanggal selagi aplikasi terbuka. */
    static final int DAY_CHECK_MS = 60_000;

    private final StreakStore store;
    private final Executor io;
    private final JLayeredPane layer;
    private final Supplier<LocalDate> today;
    private final StreakChip chip = new StreakChip(this::chipClicked);
    private final TapCounter taps = new TapCounter(3);
    private final Timer dayCheck = new Timer(DAY_CHECK_MS, e -> checkDay());
    private final ComponentAdapter resize = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
            if (splash != null) {
                splash.setBounds(0, 0, layer.getWidth(), layer.getHeight());
            }
        }
    };
    private Streak streak = Streak.NONE; // EDT
    private LocalDate counted;           // EDT; hari terakhir open() dijalankan, null = belum dimuat
    private int cracks;                  // EDT; retakan es sejak dibuka
    private boolean enabled;             // EDT
    private StreakSplash splash;         // EDT; null kalau tidak tampil
    private StreakWidget widget;         // EDT; null sampai widget pertama kali ditampilkan
    private boolean loaded;              // EDT; streak.json sudah dibaca (lewat start() atau widget)
    private NetworkProbe.Status network; // EDT; status internet terakhir, null = belum diperiksa

    /** Aksi widget mengambang ke window utama; dipanggil di EDT. */
    public interface WidgetHost {
        void openApp();

        void hideRequested();

        /** Widget selesai diseret; posisi kiri atas untuk disimpan. */
        void moved(Point location);

        /** Klik status internet di widget: periksa sekarang. */
        void checkNetwork();
    }

    /**
     * @param layer   tempat animasi besar (layered pane window utama)
     * @param enabled tampilkan ikon dan animasi; hari tetap dihitung walau mati
     */
    public StreakController(StreakStore store, Executor io, JLayeredPane layer, boolean enabled) {
        this(store, io, layer, enabled, LocalDate::now);
    }

    StreakController(StreakStore store, Executor io, JLayeredPane layer, boolean enabled, Supplier<LocalDate> today) {
        this.store = Objects.requireNonNull(store, "store");
        this.io = Objects.requireNonNull(io, "io");
        this.layer = Objects.requireNonNull(layer, "layer");
        this.today = today;
        this.enabled = enabled;
        chip.setVisible(false); // tampil setelah streak dimuat
        layer.addComponentListener(resize);
    }

    /** Ikon untuk pojok kiri panel bawah. */
    public JComponent chip() {
        return chip;
    }

    /** Muat streak di background lalu hitung hari ini; panggil sekali setelah window tampil. */
    public void start() {
        UiAsync.run(io, store::load, result -> {
            if (!loaded) { // sudah dimuat widget: state di memori lebih baru (mis. sudah dinyalakan lewat widget)
                streak = result;
                loaded = true;
            }
            openToday();
            dayCheck.start();
        }, err -> log.warn("Streak tidak bisa dimuat: {}", err.toString()));
    }

    public void stop() {
        dayCheck.stop();
        closeSplash();
        if (widget != null) {
            widget.dispose();
            widget = null;
        }
    }

    /**
     * Tampilkan widget streak mengambang (selalu di depan). Kalau streak belum dimuat (start tersembunyi lewat
     * {@code --tray}), streak dibaca dulu tanpa menghitung hari ini: hari baru dihitung saat window utama dibuka.
     *
     * @param saved posisi tersimpan, atau null untuk pojok kanan bawah layar
     */
    public void showWidget(Point saved, WidgetHost host) {
        if (widget == null) {
            widget = new StreakWidget(new StreakWidget.Host() {
                @Override
                public void flameClicked() {
                    widgetClicked();
                }

                @Override
                public void openApp() {
                    host.openApp();
                }

                @Override
                public void hideRequested() {
                    host.hideRequested();
                }

                @Override
                public void moved(Point location) {
                    host.moved(location);
                }

                @Override
                public void checkNetwork() {
                    host.checkNetwork();
                }
            });
        }
        var shown = widget;
        applyNetwork(shown);
        if (loaded) {
            refreshChip();
            shown.show(saved);
            return;
        }
        UiAsync.run(io, store::load, result -> {
            if (!loaded) { // start() bisa selesai lebih dulu
                streak = result;
                loaded = true;
            }
            refreshChip();
            if (widget == shown) {
                shown.show(saved);
            }
        }, err -> log.warn("Streak tidak bisa dimuat untuk widget: {}", err.toString()));
    }

    /** Status internet terbaru dari indikator panel bawah; ditampilkan di widget saat di-hover. */
    public void setNetworkStatus(NetworkProbe.Status status) {
        network = status;
        if (widget != null) {
            applyNetwork(widget);
        }
    }

    private void applyNetwork(StreakWidget target) {
        if (network == null) {
            target.setNetwork(null, "");
            return;
        }
        var color = network instanceof NetworkProbe.Status.Offline ? StreakWidget.NET_OFFLINE
                : NetworkIndicator.color(network);
        target.setNetwork(color, NetworkIndicator.text(network));
    }

    public void hideWidget() {
        if (widget != null) {
            widget.hide();
        }
    }

    public boolean widgetShowing() {
        return widget != null && widget.showing();
    }

    public void setEnabled(boolean on) {
        enabled = on;
        chip.setVisible(on && counted != null);
        if (!on) {
            closeSplash();
        }
    }

    Streak streak() {
        return streak;
    }

    StreakSplash splash() {
        return splash;
    }

    StreakChip chipComponent() {
        return chip;
    }

    /** Tanggal berganti selagi aplikasi terbuka dihitung seperti membuka aplikasi lagi. */
    void checkDay() {
        if (counted != null && !today.get().equals(counted)) {
            openToday();
        }
    }

    private void openToday() {
        var day = today.get();
        counted = day;
        var opening = streak.open(day);
        if (!opening.streak().equals(streak)) {
            streak = opening.streak();
            save();
        }
        cracks = 0;
        taps.reset();
        refreshChip();
        chip.setVisible(enabled);
        if (enabled && layer.isShowing()) {
            play(opening.event());
        }
    }

    void play(StreakEvent event) { // package-private untuk test
        switch (event) {
            case StreakEvent.SameDay _ -> { }
            case StreakEvent.Counted(int from, int to) -> {
                var s = newSplash();
                if (to < Streak.ACTIVE_FROM) {
                    s.playKindling(to);
                } else {
                    s.playCounted(from, to);
                }
            }
            case StreakEvent.NeedsRevival _ -> newSplash().playPending(look());
            case StreakEvent.Expired(int lost, long missed) -> newSplash().playExpired(lost, missed);
        }
    }

    private StreakSplash newSplash() {
        closeSplash();
        var s = new StreakSplash(new StreakSplash.Host() {
            @Override
            public Point2D anchor(StreakSplash from) {
                var p = chip.flameAnchor();
                var converted = SwingUtilities.convertPoint(chip, (int) Math.round(p.x), (int) Math.round(p.y), from);
                return new Point2D.Double(converted.x, converted.y);
            }

            @Override
            public void flameClicked() {
                click(true);
            }

            @Override
            public void closed() {
                if (splash != null && !splash.isDisplayable()) {
                    splash = null;
                }
                chip.arrive();
            }
        });
        splash = s;
        s.setBounds(0, 0, layer.getWidth(), layer.getHeight());
        layer.add(s, JLayeredPane.MODAL_LAYER);
        chip.setIconHidden(true);
        return s;
    }

    private void closeSplash() {
        if (splash != null) {
            var s = splash;
            splash = null;
            s.close();
        }
    }

    private void chipClicked() {
        if (splash == null) {
            click(false);
        }
    }

    /** Klik pada api besar ({@code fromSplash}) atau ikon pojok. */
    void click(boolean fromSplash) {
        var effect = applyClick();
        if (fromSplash && splash != null) {
            splash.update(look(), effect);
        } else {
            chip.actor().react(effect, chip.look().tier().palette(System.nanoTime() / 1e9));
        }
        reactWidget(effect); // api widget ikut retak/menyala walau yang diklik api di window utama
    }

    /**
     * Klik pada widget mengambang: aturannya sama dengan ikon pojok. Kalau animasi besar sedang menunggu api
     * dinyalakan (padam/beku), animasi itu ikut diperbarui, jadi es yang dipecahkan lewat widget juga pecah di
     * window utama.
     */
    void widgetClicked() {
        var effect = applyClick();
        reactWidget(effect);
        if (splash != null) {
            splash.update(look(), effect);
        }
    }

    private void reactWidget(FlameActor.Effect effect) {
        if (widget != null) {
            widget.actor().react(effect, widget.look().tier().palette(System.nanoTime() / 1e9));
        }
    }

    /** Terapkan aturan klik (triple klik untuk yang padam, retakan es) dan kembalikan efek yang perlu diputar. */
    private FlameActor.Effect applyClick() {
        checkDay();
        var day = today.get();
        FlameActor.Effect effect;
        switch (streak.status(day)) {
            case StreakStatus.Gray _ -> {
                if (taps.tap(System.currentTimeMillis())) {
                    revive(day);
                    effect = FlameActor.Effect.REVIVE;
                } else {
                    effect = FlameActor.Effect.TAP;
                }
            }
            case StreakStatus.Frozen f -> {
                cracks++;
                if (cracks >= f.clicks()) {
                    revive(day);
                    effect = FlameActor.Effect.SHATTER;
                } else {
                    effect = FlameActor.Effect.CRACK;
                }
            }
            default -> effect = FlameActor.Effect.POKE;
        }
        refreshChip();
        return effect;
    }

    private void revive(LocalDate day) {
        streak = streak.revive(day);
        log.info("Streak dinyalakan lagi: {} hari", streak.count());
        cracks = 0;
        taps.reset();
        save();
    }

    private FlameLook look() {
        return FlameLook.of(streak.status(counted != null ? counted : today.get()), cracks);
    }

    private void refreshChip() {
        var look = look();
        chip.setLook(look);
        chip.setToolTipText(tooltip(look));
        if (widget != null) {
            widget.setLook(look, tooltip(look));
        }
    }

    static String tooltip(FlameLook look) {
        return switch (look.mode()) {
            case GRAY -> I18n.t("streak.tooltip.gray", look.count());
            case FROZEN -> I18n.t("streak.tooltip.frozen", look.count(), look.clicks() - look.cracks());
            case ACTIVE -> look.kindling() ? I18n.t("streak.tooltip.kindling", look.count())
                    : I18n.t("streak.tooltip.burning", look.count(), look.tier().displayName());
        };
    }

    private void save() {
        var snapshot = streak;
        UiAsync.run(io, () -> store.save(snapshot), err -> log.warn("Streak tidak tersimpan: {}", err.toString()));
    }
}
