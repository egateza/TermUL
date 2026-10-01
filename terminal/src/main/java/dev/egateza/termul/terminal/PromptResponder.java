package dev.egateza.termul.terminal;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Auto-trigger inject password sudo/su dengan semua guard dari {@code docs/SECURITY.md}. Password hanya dikirim kalau:
 * <ol>
 *   <li><b>armed</b>: user menekan Enter pada baris perintah (setelah prompt shell) yang diawali {@code sudo } atau
 *       berupa {@code su}/{@code su ...};</li>
 *   <li>prompt password muncul <b>dalam {@value #ARM_WINDOW_SECONDS} detik</b> sejak armed;</li>
 *   <li>prompt adalah <b>baris terakhir</b> output (ANSI di-strip), cocok penuh dengan
 *       {@code [sudo] password for <user>:} (user = username profil) atau {@code Password:} untuk su;</li>
 *   <li><b>one-shot</b>: paling banyak satu kali per perintah yang di-arm;</li>
 *   <li><b>berhenti saat gagal</b>: {@code Sorry, try again}/{@code Authentication failure} setelah password dikirim
 *       → disarm dan {@link Listener#rejected} (tidak pernah dikirim ulang).</li>
 * </ol>
 * Output yang muncul sebelum Enter tidak pernah ikut dicocokkan (buffer dikosongkan saat armed), jadi prompt palsu
 * dari {@code cat log} atau script tidak memicu apa pun.
 *
 * <p>Thread-safe: {@link #onEnter} dipanggil dari EDT, {@link #onOutput} dari thread emulator. Listener dipanggil di
 * thread output tanpa memegang lock, dan tidak boleh blocking.
 */
public final class PromptResponder implements SshTtyConnector.OutputListener {

    private static final Logger log = LoggerFactory.getLogger(PromptResponder.class);

    static final int ARM_WINDOW_SECONDS = 5;
    static final Duration ARM_WINDOW = Duration.ofSeconds(ARM_WINDOW_SECONDS);
    /** Lama pengamatan pesan gagal setelah password terkirim. */
    static final Duration FAILURE_WINDOW = Duration.ofSeconds(15);
    static final int TAIL_MAX = 512;

    private static final Pattern SUDO_COMMAND = Pattern.compile("sudo\\s+\\S.*");
    private static final Pattern SU_COMMAND = Pattern.compile("su(\\s+.*)?");
    private static final Pattern SUDO_PROMPT = Pattern.compile("\\[sudo\\] (?:password for|kata sandi untuk) (\\S+):\\s*");
    private static final Pattern SU_PROMPT = Pattern.compile("(?:Password|Kata sandi):\\s*");
    private static final Pattern FAILURE = Pattern.compile(
            "Sorry, try again|Maaf, coba lagi|Authentication failure|Otentikasi gagal|incorrect password",
            Pattern.CASE_INSENSITIVE);

    /** Jenis prompt; menentukan secret yang dikirim (sudo → password sudo, su → password root). */
    public enum Kind { SUDO, SU }

    public interface Listener {
        /** Semua guard lolos: kirim secret untuk {@code kind} sekarang (diikuti Enter). */
        void respond(Kind kind);

        /** Server menolak password yang dikirim otomatis. Tidak ada percobaan ulang. */
        void rejected(Kind kind);
    }

    private final String username;
    private final Listener listener;
    private final LongSupplier nanoClock;
    private final StringBuilder tail = new StringBuilder(); // guarded by this
    private AnsiStripper stripper = new AnsiStripper();     // guarded by this
    private Kind armed;                                     // guarded by this; null = disarmed
    private long armedAt;                                   // guarded by this
    private boolean fired;                                  // guarded by this
    private long firedAt;                                   // guarded by this

    public PromptResponder(String username, Listener listener) {
        this(username, listener, System::nanoTime);
    }

    PromptResponder(String username, Listener listener, LongSupplier nanoClock) {
        this.username = Objects.requireNonNull(username);
        this.listener = Objects.requireNonNull(listener);
        this.nanoClock = Objects.requireNonNull(nanoClock);
    }

    /** Jenis perintah yang boleh meng-arm, atau null. */
    static Kind kindOf(String command) {
        if (SUDO_COMMAND.matcher(command).matches()) {
            return Kind.SUDO;
        }
        if (SU_COMMAND.matcher(command).matches()) {
            return Kind.SU;
        }
        return null;
    }

    /**
     * User menekan Enter. Baris kursor (dari buffer layar) menentukan apakah responder di-arm; Enter lain
     * (termasuk mengetik password manual) men-disarm.
     */
    public synchronized void onEnter(String cursorLine) {
        Kind kind = ExitGuard.commandAfterPrompt(cursorLine).map(PromptResponder::kindOf).orElse(null);
        armed = kind;
        armedAt = nanoClock.getAsLong();
        fired = false;
        tail.setLength(0);
        stripper = new AnsiStripper();
        if (kind != null) {
            log.info("Auto-inject {} di-arm", kind);
        }
    }

    public synchronized boolean isArmed() {
        return armed != null;
    }

    @Override
    public void onOutput(char[] buf, int offset, int length) {
        Kind respond = null;
        Kind rejected = null;
        synchronized (this) {
            if (armed == null) {
                return;
            }
            long now = nanoClock.getAsLong();
            if (!fired && now - armedAt > ARM_WINDOW.toNanos()) {
                log.info("Auto-inject {} batal: prompt password tidak muncul dalam {} detik", armed, ARM_WINDOW_SECONDS);
                disarm();
                return;
            }
            if (fired && now - firedAt > FAILURE_WINDOW.toNanos()) {
                disarm();
                return;
            }
            stripper.strip(buf, offset, length, tail);
            if (tail.length() > TAIL_MAX) {
                tail.delete(0, tail.length() - TAIL_MAX);
            }
            if (!fired) {
                String line = lastLine(tail);
                String otherUser = foreignSudoUser(armed, line);
                if (otherUser != null) {
                    log.info("Auto-inject batal: prompt sudo untuk user '{}', bukan user profil '{}'", otherUser, username);
                    disarm();
                    return;
                }
                if (isPasswordPrompt(armed, line)) {
                    fired = true;
                    firedAt = now;
                    tail.setLength(0); // yang diamati berikutnya hanya output setelah password terkirim
                    respond = armed;
                }
            } else if (FAILURE.matcher(tail).find()) {
                rejected = armed;
                disarm();
            }
        }
        if (respond != null) {
            log.info("Prompt password {} terdeteksi setelah perintah user; mengirim password otomatis", respond);
            listener.respond(respond);
        }
        if (rejected != null) {
            log.warn("Password {} yang dikirim otomatis ditolak server; tidak dikirim ulang", rejected);
            listener.rejected(rejected);
        }
    }

    private boolean isPasswordPrompt(Kind kind, String line) {
        return switch (kind) {
            case SUDO -> {
                var m = SUDO_PROMPT.matcher(line);
                yield m.matches() && m.group(1).equals(username);
            }
            case SU -> SU_PROMPT.matcher(line).matches();
        };
    }

    /** User di prompt sudo yang bukan user profil (tidak pernah di-inject), atau null. */
    private String foreignSudoUser(Kind kind, String line) {
        if (kind != Kind.SUDO) {
            return null;
        }
        var m = SUDO_PROMPT.matcher(line);
        return m.matches() && !m.group(1).equals(username) ? m.group(1) : null;
    }

    private static String lastLine(CharSequence text) {
        int i = text.length() - 1;
        while (i >= 0 && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
            i--;
        }
        return text.subSequence(i + 1, text.length()).toString();
    }

    private void disarm() {
        armed = null;
        fired = false;
        tail.setLength(0);
    }
}
