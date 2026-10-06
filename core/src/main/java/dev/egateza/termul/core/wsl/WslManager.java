package dev.egateza.termul.core.wsl;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Start/stop distro WSL lewat {@code wsl.exe}. Semua method blocking: panggil di luar EDT.
 *
 * <p>WSL mematikan distro yang tidak punya sesi {@code wsl.exe} aktif, termasuk kalau di dalamnya ada service seperti
 * {@code sshd}. Karena itu {@link #start} menahan satu proses keep-alive ({@code wsl.exe -d <distro> --exec sleep
 * infinity}) sampai {@link #stop} atau {@link #close} (aplikasi ditutup); setelah keep-alive dilepas, distro mati
 * sendiri saat idle kecuali ada sesi lain.
 */
public final class WslManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WslManager.class);

    private static final Duration LIST_TIMEOUT = Duration.ofSeconds(15);
    /** Boot pertama distro (atau VM WSL2) bisa lama. */
    private static final Duration START_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern USER = Pattern.compile("[a-z_][a-z0-9_-]{0,31}");

    /**
     * Menjalankan sshd dengan systemd kalau aktif di distro, kalau tidak dengan script init ({@code service}). Nama
     * service berbeda per distro: {@code ssh} (Debian/Ubuntu) atau {@code sshd} (Fedora, Arch, dll.).
     */
    static final String START_SSHD = "if [ -d /run/systemd/system ]; then systemctl start ssh 2>/dev/null"
            + " || systemctl start sshd; else service ssh start 2>/dev/null || service sshd start; fi";

    /**
     * Membaca kondisi sshd (lihat {@link SshCheck#parse}) tanpa mengubah konfigurasi. Satu-satunya yang ditulis adalah
     * {@code /run/sshd} (direktori runtime yang dibutuhkan {@code sshd -t} di Debian/Ubuntu, dibuat juga oleh service
     * sshd sendiri). Tanpa tanda kutip ganda: argumen ini melewati command line Windows.
     */
    static final String CHECK_SSHD = String.join("; ",
            "if command -v sshd >/dev/null 2>&1 || [ -x /usr/sbin/sshd ]; then echo installed=yes; else echo installed=no; fi",
            "if [ -d /run/systemd/system ]; then echo systemd=yes; echo socket=$(systemctl is-active ssh.socket 2>/dev/null);"
                    + " else echo systemd=no; fi",
            "S=$(command -v sshd || echo /usr/sbin/sshd)",
            "[ -d /run/sshd ] || mkdir -p /run/sshd 2>/dev/null",
            "if $S -t >/dev/null 2>&1; then echo configtest=ok; $S -T 2>/dev/null | sed -n 's/^port /config_port=/p';"
                    + " else echo configtest=fail; fi",
            "if command -v ss >/dev/null 2>&1; then echo listen_tool=ss; ss -ltn | tail -n +2 | awk '{print $4}'"
                    + " | sed 's/.*:/listen=/'; elif command -v netstat >/dev/null 2>&1; then echo listen_tool=netstat;"
                    + " netstat -ltn | tail -n +3 | awk '{print $4}' | sed 's/.*:/listen=/'; else echo listen_tool=none; fi");

    private final WslRunner runner;
    private final Duration startTimeout;
    private final Duration pollInterval;
    private final Map<String, WslRunner.Handle> keepAlive = new ConcurrentHashMap<>();

    public WslManager(WslRunner runner) {
        this(runner, START_TIMEOUT, Duration.ofMillis(300));
    }

    WslManager(WslRunner runner, Duration startTimeout, Duration pollInterval) {
        this.runner = runner;
        this.startTimeout = startTimeout;
        this.pollInterval = pollInterval;
    }

    /** true kalau {@code wsl.exe} ada (WSL mungkin tetap belum terpasang; lihat {@link #list()}). */
    public boolean available() {
        return runner.available();
    }

    /**
     * Distro yang terpasang beserta statusnya, urut seperti {@code wsl --list}. Distro internal Docker Desktop
     * dilewati. Kosong kalau WSL belum terpasang atau belum ada distro (keduanya membuat {@code wsl --list} keluar
     * dengan kode bukan 0).
     */
    public List<WslDistro> list() throws WslException {
        var installed = run(List.of("--list", "--quiet"), LIST_TIMEOUT);
        if (!installed.ok()) {
            log.debug("wsl --list keluar dengan kode {}: {}", installed.exitCode(), installed.output().strip());
            return List.of();
        }
        Set<String> running = running();
        var result = new ArrayList<WslDistro>();
        for (String name : names(installed.output())) {
            if (isInternal(name)) {
                continue;
            }
            result.add(new WslDistro(name, running.contains(name.toLowerCase(Locale.ROOT))));
        }
        return List.copyOf(result);
    }

    /** Nama distro yang sedang berjalan, huruf kecil (nama distro WSL tidak case-sensitive). */
    public Set<String> running() throws WslException {
        var r = run(List.of("--list", "--quiet", "--running"), LIST_TIMEOUT);
        if (!r.ok()) {
            return Set.of(); // tidak ada distro yang berjalan juga keluar dengan kode bukan 0
        }
        var result = new LinkedHashSet<String>();
        names(r.output()).forEach(n -> result.add(n.toLowerCase(Locale.ROOT)));
        return Set.copyOf(result);
    }

    public boolean isRunning(String distro) throws WslException {
        return running().contains(checkName(distro).toLowerCase(Locale.ROOT));
    }

    /**
     * Jalankan distro dan tahan tetap hidup (keep-alive) sampai {@link #stop}. Idempoten: distro yang sudah berjalan
     * hanya ditambah keep-alive kalau belum ada. Menunggu sampai distro tercatat berjalan.
     */
    public void start(String distro) throws WslException {
        String name = checkName(distro);
        String key = name.toLowerCase(Locale.ROOT);
        WslRunner.Handle handle = keepAlive.get(key);
        if (handle == null || !handle.isAlive()) {
            try {
                handle = runner.spawn(List.of("--distribution", name, "--exec", "sleep", "infinity"));
            } catch (IOException e) {
                throw new WslException("Distro " + name + " gagal dijalankan: " + e.getMessage(), e);
            }
            var previous = keepAlive.put(key, handle);
            if (previous != null) {
                previous.stop();
            }
            log.info("Distro WSL {} dijalankan (keep-alive)", name);
        }
        long deadline = System.nanoTime() + startTimeout.toNanos();
        while (!running().contains(key)) {
            if (!handle.isAlive()) {
                keepAlive.remove(key, handle);
                throw new WslException("Distro " + name + " berhenti saat dijalankan. Coba jalankan \"wsl -d " + name
                        + "\" di Command Prompt untuk melihat pesan error-nya.");
            }
            if (System.nanoTime() > deadline) {
                throw new WslException("Distro " + name + " belum berjalan setelah " + startTimeout.toSeconds()
                        + " detik.");
            }
            sleep();
        }
    }

    /**
     * Jalankan sshd di distro (sebagai root). Distro ikut dijalankan WSL kalau belum.
     *
     * @return false kalau sshd tidak bisa dijalankan (mis. {@code openssh-server} belum terpasang); detail di log
     */
    public boolean startSsh(String distro) throws WslException {
        String name = checkName(distro);
        var r = run(List.of("--distribution", name, "--user", "root", "--exec", "sh", "-c", START_SSHD),
                COMMAND_TIMEOUT);
        if (!r.ok()) {
            log.warn("sshd di distro WSL {} tidak bisa dijalankan (kode {}): {}", name, r.exitCode(),
                    r.output().strip());
        }
        return r.ok();
    }

    /** Periksa sshd di distro (sebagai root, hanya membaca). Distro ikut dijalankan WSL kalau belum. */
    public SshCheck checkSsh(String distro) throws WslException {
        String name = checkName(distro);
        var r = run(List.of("--distribution", name, "--user", "root", "--exec", "sh", "-c", CHECK_SSHD),
                COMMAND_TIMEOUT);
        return SshCheck.parse(r.output());
    }

    /** Persiapan sebelum connect SSH ke distro: jalankan distro (keep-alive) lalu sshd. */
    public boolean prepareSsh(String distro) throws WslException {
        start(distro);
        return startSsh(distro);
    }

    /** User default distro (yang dipakai {@code wsl -d <distro>}), kalau bisa dibaca. Distro ikut dijalankan. */
    public Optional<String> defaultUser(String distro) throws WslException {
        var r = run(List.of("--distribution", checkName(distro), "--exec", "whoami"), COMMAND_TIMEOUT);
        String user = r.output().strip();
        return r.ok() && USER.matcher(user).matches() ? Optional.of(user) : Optional.empty();
    }

    /** Lepas keep-alive lalu hentikan distro ({@code wsl --terminate}); sesi yang terbuka ke distro ikut putus. */
    public void stop(String distro) throws WslException {
        String name = checkName(distro);
        var handle = keepAlive.remove(name.toLowerCase(Locale.ROOT));
        if (handle != null) {
            handle.stop();
        }
        var r = run(List.of("--terminate", name), COMMAND_TIMEOUT);
        if (!r.ok()) {
            throw new WslException("Distro " + name + " gagal dihentikan: " + r.output().strip());
        }
        log.info("Distro WSL {} dihentikan", name);
    }

    /** Hentikan semua distro dan VM WSL ({@code wsl --shutdown}), termasuk yang tidak dijalankan dari TermUL. */
    public void shutdownAll() throws WslException {
        releaseAll();
        var r = run(List.of("--shutdown"), COMMAND_TIMEOUT);
        if (!r.ok()) {
            throw new WslException("WSL gagal dihentikan: " + r.output().strip());
        }
        log.info("Semua distro WSL dihentikan (wsl --shutdown)");
    }

    /** true kalau TermUL sedang menahan distro ini tetap hidup. */
    public boolean isKeptAlive(String distro) {
        var handle = keepAlive.get(distro.toLowerCase(Locale.ROOT));
        return handle != null && handle.isAlive();
    }

    /** Lepas semua keep-alive tanpa menghentikan distro (saat aplikasi ditutup). */
    @Override
    public void close() {
        releaseAll();
    }

    private void releaseAll() {
        for (var key : List.copyOf(keepAlive.keySet())) {
            var handle = keepAlive.remove(key);
            if (handle != null) {
                handle.stop();
            }
        }
    }

    private WslRunner.Result run(List<String> args, Duration timeout) throws WslException {
        if (!runner.available()) {
            throw new WslException("WSL tidak ditemukan di sistem ini (wsl.exe tidak ada).");
        }
        try {
            return runner.run(args, timeout);
        } catch (IOException e) {
            throw new WslException("Perintah wsl.exe gagal: " + e.getMessage(), e);
        }
    }

    /** Nama distro dari output {@code wsl --list --quiet}; baris yang bukan nama distro valid dilewati. */
    static List<String> names(String output) {
        return WslOutput.lines(output).stream().filter(WslDistro::isValidName).toList();
    }

    /** Distro milik Docker Desktop ({@code docker-desktop}, {@code docker-desktop-data}): dikelola Docker sendiri. */
    static boolean isInternal(String name) {
        return name.toLowerCase(Locale.ROOT).startsWith("docker-desktop");
    }

    private static String checkName(String distro) throws WslException {
        if (!WslDistro.isValidName(distro)) {
            throw new WslException("Nama distro WSL tidak valid: " + distro);
        }
        return distro;
    }

    private void sleep() throws WslException {
        try {
            Thread.sleep(pollInterval);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WslException("Dibatalkan", e);
        }
    }
}
