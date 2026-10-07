package dev.egateza.termul.app.wsl;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.UiAsync;
import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.EnvironmentTag;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import dev.egateza.termul.core.wsl.WslDistro;
import dev.egateza.termul.core.wsl.WslException;
import dev.egateza.termul.core.wsl.WslManager;
import dev.egateza.termul.ssh.SshConnectException;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manajemen WSL di UI: memantau distro yang berjalan (untuk grup WSL di panel host), WSL Manager (start/stop distro),
 * profil SSH untuk distro, dan persiapan sebelum connect ke profil WSL. Hanya aktif kalau pengaturannya dinyalakan
 * (dan di Windows); selama mati tidak ada {@code wsl.exe} yang dijalankan. Method publik dipanggil di EDT, kecuali
 * {@link #prepare}.
 */
public final class WslController implements WslManagerDialog.Actions {

    private static final Logger log = LoggerFactory.getLogger(WslController.class);

    /** Port sshd profil WSL pertama; port 22 sering dipakai OpenSSH Server milik Windows sendiri. */
    static final int FIRST_PORT = 2222;
    private static final int POLL_MS = 15_000;

    /** Callback ke window utama. EDT. */
    public interface Host {
        /** @param running nama distro yang berjalan; null = manajemen WSL mati */
        void wslRunningChanged(List<String> running);

        /**
         * Buka dialog profil baru yang terisi dari {@code template} di atas {@code parent}; kosong kalau dibatalkan.
         */
        Optional<HostProfile> createProfile(java.awt.Component parent, HostProfile template);

        /** Simpan perubahan profil (di luar EDT). */
        void saveProfile(HostProfile profile);
    }

    @FunctionalInterface
    private interface Task {
        String run() throws WslException;
    }

    private final Window owner;
    private final WslManager wsl;
    private final BooleanSupplier enabled;
    private final Supplier<ProfileSnapshot> profiles;
    private final Executor ops;
    private final Host host;
    private final Timer poll = new Timer(POLL_MS, e -> refresh());
    private List<WslDistro> distros = List.of(); // EDT
    private boolean listed; // EDT; daftar distro sudah pernah terbaca sejak fitur aktif
    private boolean refreshing; // EDT
    private boolean refreshAgain; // EDT; ada permintaan refresh selama refresh berjalan
    private WslManagerDialog dialog; // EDT
    /** Port sshd per distro (huruf kecil) yang diisi user di WSL Manager, untuk distro yang belum punya profil. EDT. */
    private final Map<String, Integer> portInput = new HashMap<>();
    /** Port sshd per distro (huruf kecil) yang terdeteksi dari sshd_config. EDT. */
    private final Map<String, Integer> portDetected = new HashMap<>();
    private final Set<String> detecting = new HashSet<>(); // EDT

    /**
     * @param enabled  pengaturan manajemen WSL aktif dan OS-nya Windows; dibaca ulang setiap kali dipakai
     * @param ops      executor untuk perintah {@code wsl.exe} (blocking)
     */
    public WslController(Window owner, WslManager wsl, BooleanSupplier enabled, Supplier<ProfileSnapshot> profiles,
                         Executor ops, Host host) {
        this.owner = owner;
        this.wsl = wsl;
        this.enabled = enabled;
        this.profiles = profiles;
        this.ops = ops;
        this.host = host;
    }

    public boolean enabled() {
        return enabled.getAsBoolean();
    }

    /** Sesuaikan dengan pengaturan: dipanggil saat window dibuat dan setelah pengaturan diubah. */
    public void apply() {
        if (enabled()) {
            poll.start();
            refresh();
        } else {
            poll.stop();
            distros = List.of();
            listed = false;
            portDetected.clear();
            if (dialog != null) {
                dialog.dispose();
                dialog = null;
            }
            host.wslRunningChanged(null);
        }
    }

    /** Distro terpasang yang terakhir terbaca (untuk pilihan di dialog profil); kosong kalau manajemen WSL mati. */
    public List<String> installedNames() {
        return enabled() ? distros.stream().map(WslDistro::name).toList() : List.of();
    }

    /** Baca ulang daftar distro di background. */
    @Override
    public void refresh() {
        if (!enabled()) {
            return;
        }
        if (refreshing) {
            refreshAgain = true;
            return;
        }
        refreshing = true;
        UiAsync.run(ops, () -> call(wsl::list), this::refreshed, err -> {
            log.warn("Daftar distro WSL tidak bisa dibaca: {}", err.getMessage());
            refreshed(List.of());
        });
    }

    private void refreshed(List<WslDistro> list) {
        refreshing = false;
        if (!enabled()) {
            return;
        }
        distros = list;
        listed = true;
        host.wslRunningChanged(list.stream().filter(WslDistro::running).map(WslDistro::name).toList());
        if (dialog != null) {
            dialog.setDistros(list, profiles.get(), this::knownPort);
            detectPorts();
        }
        if (refreshAgain) {
            refreshAgain = false;
            refresh();
        }
    }

    /** Panel host / dialog profil berubah: segarkan kolom profil di WSL Manager. */
    public void profilesChanged() {
        if (dialog != null && listed) {
            dialog.setDistros(distros, profiles.get(), this::knownPort);
        }
    }

    public void openManager() {
        if (!enabled()) {
            return;
        }
        if (dialog == null) {
            dialog = new WslManagerDialog(owner, this);
            var opened = dialog;
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    if (dialog == opened) {
                        dialog = null;
                    }
                }
            });
            if (listed) {
                dialog.setDistros(distros, profiles.get(), this::knownPort);
            } // belum: dialog menampilkan "membaca daftar..." sampai refresh di bawah selesai
        }
        if (!wsl.available()) {
            dialog.setIdle(I18n.t("wsl.error.notInstalled"));
        }
        refresh(); // sebelum setVisible: dialog modal memblokir sampai ditutup, hasil refresh masuk lewat EDT
        dialog.setVisible(true);
    }

    /** Parent dialog turunan: WSL Manager kalau terbuka (modal), selain itu window utama. */
    private java.awt.Component parent() {
        return dialog != null ? dialog : owner;
    }

    /** Lepas timer dan dialog (window utama ditutup). Keep-alive dilepas oleh pemilik {@link WslManager}. */
    public void close() {
        poll.stop();
        if (dialog != null) {
            dialog.dispose();
            dialog = null;
        }
    }

    // --- WslManagerDialog.Actions ---

    @Override
    public void start(String distro) {
        boolean hasProfile = !linked(distro).isEmpty();
        runOp(I18n.t("wsl.manager.starting", distro), () -> {
            wsl.start(distro);
            if (hasProfile && !wsl.startSsh(distro)) {
                return I18n.t("wsl.manager.startedNoSsh", distro);
            }
            return I18n.t("wsl.manager.started", distro);
        });
    }

    @Override
    public void stop(String distro) {
        runOp(I18n.t("wsl.manager.stopping", distro), () -> {
            wsl.stop(distro);
            return I18n.t("wsl.manager.stopped", distro);
        });
    }

    @Override
    public void shutdownAll() {
        runOp(I18n.t("wsl.manager.shuttingDown"), () -> {
            wsl.shutdownAll();
            return I18n.t("wsl.manager.shutDown");
        });
    }

    /** Profil SSH baru untuk distro: user default distro dan port sshd berikutnya sudah terisi. */
    @Override
    public void newProfile(String distro) {
        if (!enabled()) {
            return;
        }
        var snapshot = profiles.get();
        Integer known = knownPort(distro);
        if (dialog != null) {
            dialog.setBusy(I18n.t("wsl.manager.preparingProfile", distro));
        }
        UiAsync.run(ops, () -> call(() -> {
                    String user = wsl.defaultUser(distro).orElse("root"); // menjalankan distro juga
                    Integer port = known;
                    if (port == null) {
                        port = pickPort(wsl.checkSsh(distro).configPorts());
                    }
                    return template(distro, user, port != null ? port : nextPort(snapshot));
                }),
                // tidak langsung menampilkan panduan: periksa dulu, laporan hanya muncul kalau sshd belum siap
                template -> {
                    if (dialog != null) {
                        dialog.setIdle(null);
                    }
                    host.createProfile(parent(), template).ifPresent(
                            saved -> runCheck(distro, () -> profilePort(saved),
                                    I18n.t("wsl.manager.profileReady", saved.name())));
                },
                err -> {
                    if (dialog != null) {
                        dialog.setIdle(null);
                    }
                    Dialogs.error(parent(), I18n.t("wsl.error.title"), err);
                });
    }

    @Override
    public void guide(String distro) {
        int port = portFor(distro);
        WslManagerDialog.showGuide(parent(), distro, port);
    }

    @Override
    public void check(String distro) {
        runCheck(distro, () -> explicitPort(distro), null);
    }

    @Override
    public void setPort(String distro, int port) {
        var linked = linked(distro);
        if (linked.isEmpty()) {
            portInput.put(key(distro), port);
        } else {
            linked.forEach(p -> host.saveProfile(p.withPort(port)));
        }
    }

    /** Port sshd distro yang diketahui: profil, isian user, lalu hasil deteksi; null kalau belum ada. EDT. */
    Integer knownPort(String distro) {
        Integer explicit = explicitPort(distro);
        return explicit != null ? explicit : portDetected.get(key(distro));
    }

    /** Port yang ditentukan user: port profil distro, lalu isian di tabel; null kalau belum ada. EDT. */
    Integer explicitPort(String distro) {
        var linked = linked(distro);
        if (!linked.isEmpty()) {
            return linked.getFirst().port();
        }
        return portInput.get(key(distro));
    }

    /** Port terbaru profil (bisa sudah diubah sejak pemeriksaan pertama). EDT. */
    private int profilePort(HostProfile profile) {
        return profiles.get().find(profile.id()).orElse(profile).port();
    }

    /** Port yang dipakai untuk distro: {@link #knownPort}, atau port cadangan berikutnya. EDT. */
    private int portFor(String distro) {
        Integer known = knownPort(distro);
        return known != null ? known : nextPort(profiles.get());
    }

    /**
     * Baca port dari sshd_config untuk distro yang sedang berjalan tapi belum punya port (hanya selama WSL Manager
     * terbuka, sekali per distro). Distro yang berhenti tidak dijalankan untuk ini.
     */
    private void detectPorts() {
        if (dialog == null) {
            return;
        }
        for (var d : distros) {
            String k = key(d.name());
            if (d.running() && knownPort(d.name()) == null && !portDetected.containsKey(k) && detecting.add(k)) {
                UiAsync.run(ops, () -> call(() -> pickPort(wsl.checkSsh(d.name()).configPorts())), port -> {
                    detecting.remove(k);
                    if (port != null) {
                        portDetected.put(k, port);
                        profilesChanged();
                    }
                }, err -> {
                    detecting.remove(k);
                    log.debug("Port sshd {} tidak terdeteksi: {}", d.name(), err.getMessage());
                });
            }
        }
    }

    /** Port dari sshd_config: yang bukan 22 diutamakan (22 sering milik OpenSSH Server Windows). Null kalau kosong. */
    static Integer pickPort(Set<Integer> configPorts) {
        return configPorts.stream().sorted().filter(p -> p != 22).findFirst()
                .orElse(configPorts.contains(22) ? 22 : null);
    }

    private static String key(String distro) {
        return distro.toLowerCase(Locale.ROOT);
    }

    /** "Periksa SSH" untuk profil WSL (klik kanan host). */
    public void checkProfile(HostProfile profile) {
        if (enabled() && profile.wslDistro() != null) {
            runCheck(profile.wslDistro(), () -> profilePort(profile), null);
        }
    }

    /**
     * Port dibaca ulang dari {@code port} setiap kali (juga saat "Periksa lagi"), supaya port yang diubah user atau
     * terdeteksi sejak pemeriksaan sebelumnya ikut terpakai.
     *
     * @param port    port profil/isian user (EDT); null = belum ditentukan, dipakai port dari sshd_config setelah
     *                distro berjalan, baru port cadangan kalau sshd_config tidak terbaca
     * @param quietOk kalau tidak null dan sshd siap, laporan tidak ditampilkan; pesan ini muncul di status WSL Manager
     */
    private void runCheck(String distro, Supplier<Integer> port, String quietOk) {
        // pemeriksaan menjalankan distro + sshd; distro yang berhenti hanya dinyalakan atas persetujuan user
        if (isStopped(distro) && !Dialogs.confirm(parent(), I18n.t("wsl.check.title", distro),
                I18n.t("wsl.check.startConfirm", distro))) {
            return;
        }
        Integer explicit = port.get();
        int fallback = nextPort(profiles.get());
        if (dialog != null) {
            dialog.setBusy(I18n.t("wsl.manager.checking", distro));
        }
        UiAsync.run(ops, () -> call(() -> report(distro, explicit, fallback)), report -> {
            if (explicit == null) {
                Integer detected = pickPort(report.check().configPorts());
                if (detected != null) {
                    portDetected.put(key(distro), detected);
                } else {
                    portDetected.remove(key(distro));
                }
            }
            // distro bisa baru dijalankan oleh pemeriksaan ini: status di WSL Manager/panel host dulu, baru laporan
            refresh();
            boolean quiet = quietOk != null && report.ok();
            if (dialog != null) {
                dialog.setIdle(quiet ? quietOk : null);
            }
            if (!quiet) {
                WslManagerDialog.showCheck(parent(), report, () -> runCheck(distro, port, null),
                        p -> {
                            setPort(distro, p);
                            profilesChanged();
                            runCheck(distro, port, null);
                        });
            }
        }, err -> {
            if (dialog != null) {
                dialog.setIdle(null);
            }
            Dialogs.error(parent(), I18n.t("wsl.error.title"), err);
        });
    }

    /** true kalau daftar distro terakhir mencatat distro ini berhenti; false kalau berjalan atau belum diketahui. EDT. */
    boolean isStopped(String distro) {
        return listed && distros.stream().anyMatch(d -> d.name().equalsIgnoreCase(distro) && !d.running());
    }

    /**
     * Siapkan distro seperti saat connect (distro + sshd), lalu periksa sshd dan jangkauan port dari Windows.
     *
     * @param explicit port profil/isian user; null = port dari sshd_config (distro sudah berjalan), lalu {@code fallback}
     */
    SshCheckReport report(String distro, Integer explicit, int fallback) throws WslException {
        wsl.prepareSsh(distro); // gagal di sini tetap diperiksa: hasil cek yang menjelaskan sebabnya
        var check = wsl.checkSsh(distro);
        Integer detected = explicit != null ? explicit : pickPort(check.configPorts());
        int port = detected != null ? detected : fallback;
        return new SshCheckReport(distro, port, check, reachable(port));
    }

    /** true kalau {@code localhost:port} menerima koneksi TCP dari Windows (tanpa SSH handshake). */
    static boolean reachable(int port) {
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", port), 2000);
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /**
     * Dipanggil sebelum setiap percobaan connect (blocking, di luar EDT). Profil WSL: jalankan distro (keep-alive) dan
     * sshd-nya. Sambung ulang otomatis tidak menyalakan distro yang sedang berhenti, supaya distro yang sengaja di-stop
     * tidak hidup lagi karena tab yang masih terbuka.
     */
    public void prepare(HostProfile profile, boolean automatic) throws SshConnectException {
        HostProfile latest = profiles.get().find(profile.id()).orElse(profile);
        String distro = latest.wslDistro();
        if (distro == null || !enabled()) {
            return;
        }
        try {
            if (automatic && !wsl.isRunning(distro)) {
                throw new SshConnectException(I18n.t("wsl.error.notRunning", distro));
            }
            boolean started = wsl.prepareSsh(distro);
            // pesan yang menyebut masalahnya (mis. port lain, ssh.socket) lebih berguna daripada "connection refused"
            String problem = problemOf(distro, latest.port());
            if (problem != null) {
                throw new SshConnectException(I18n.t("wsl.error.sshProblem", distro, problem));
            }
            if (!started) {
                throw new SshConnectException(I18n.t("wsl.error.sshd", distro));
            }
        } catch (WslException e) {
            throw new SshConnectException(e.getMessage(), e);
        } finally {
            SwingUtilities.invokeLater(this::refresh); // status distro di panel host/WSL Manager
        }
    }

    /** Masalah sshd untuk port ini, atau null kalau tidak ada / pemeriksaan sendiri gagal (connect tetap dicoba). */
    private String problemOf(String distro, int port) {
        try {
            return new SshCheckReport(distro, port, wsl.checkSsh(distro), true).firstProblem();
        } catch (WslException e) {
            log.debug("Pemeriksaan sshd di {} gagal: {}", distro, e.getMessage());
            return null;
        }
    }

    private void runOp(String busyMessage, Task task) {
        if (dialog != null) {
            dialog.setBusy(busyMessage);
        }
        UiAsync.run(ops, () -> call(task::run), message -> {
            if (dialog != null) {
                dialog.setIdle(message);
            }
            refresh();
        }, err -> {
            if (dialog != null) {
                dialog.setIdle(null);
            }
            Dialogs.error(parent(), I18n.t("wsl.error.title"), err);
            refresh();
        });
    }

    private List<HostProfile> linked(String distro) {
        return profiles.get().profiles().stream().filter(p -> distro.equalsIgnoreCase(p.wslDistro())).toList();
    }

    /** Port sshd untuk profil WSL baru: setelah port profil WSL yang sudah ada, mulai {@value #FIRST_PORT}. */
    static int nextPort(ProfileSnapshot snapshot) {
        int max = snapshot.profiles().stream().filter(p -> p.wslDistro() != null)
                .mapToInt(HostProfile::port).max().orElse(FIRST_PORT - 1);
        return Math.clamp(max + 1L, FIRST_PORT, 65_535);
    }

    /** Profil SSH ke sshd di distro lewat {@code localhost}; tampil di grup bawaan WSL, jadi tanpa grup sendiri. */
    static HostProfile template(String distro, String user, int port) {
        return new HostProfile(UUID.randomUUID(), distro, "", "localhost", port, user,
                AuthMethod.PASSWORD, null, null, EnvironmentTag.DEV, null, null, false, null, null, distro);
    }

    @FunctionalInterface
    private interface WslCall<T> {
        T call() throws WslException;
    }

    private static <T> T call(WslCall<T> work) {
        try {
            return work.call();
        } catch (WslException e) {
            throw new CompletionException(e);
        }
    }
}
