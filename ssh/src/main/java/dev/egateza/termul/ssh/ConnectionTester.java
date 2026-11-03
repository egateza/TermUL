package dev.egateza.termul.ssh;

import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.ssh.auth.CredentialProvider;
import dev.egateza.termul.ssh.hostkey.HostKeyInfo;
import dev.egateza.termul.ssh.hostkey.HostKeyPrompt;
import dev.egateza.termul.ssh.hostkey.KnownHostsStore;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Tombol Test di Profile manager: mencoba connect dan login ke profil (juga yang belum disimpan) tanpa membuka
 * terminal, lewat {@link SessionManager} tersendiri yang ditutup setelah selesai.
 *
 * <p>Aman: host key tetap diverifikasi terhadap {@code known_hosts} aplikasi, tetapi tanpa dialog. Host baru
 * <b>tidak</b> dipercaya otomatis dan tidak ditambahkan; test berhenti sebelum login, jadi tidak ada password yang
 * dikirim ke host yang belum diverifikasi. Host key yang berubah = gagal. Secret tidak pernah diminta lewat dialog:
 * hanya yang diberikan {@code credentials} (dari form atau vault); tanpa secret, login tidak dites.
 */
public final class ConnectionTester {

    public enum HostKey {
        /** cocok dengan known_hosts */
        KNOWN,
        /** belum dikenal: user akan diminta mempercayainya saat connect pertama */
        NEW,
        /** berbeda dari yang tersimpan: koneksi ditolak */
        CHANGED,
        /** host tidak tercapai */
        UNREACHED
    }

    /**
     * @param signedIn        login berhasil
     * @param passwordMissing login butuh password/passphrase yang tidak tersedia (tidak dites)
     * @param millis          lama connect (sampai login selesai atau gagal)
     * @param error           pesan error untuk user, null kalau berhasil
     */
    public record Result(HostKey hostKey, boolean signedIn, boolean passwordMissing, long millis, String error) {

        public boolean ok() {
            return signedIn || hostKey == HostKey.NEW || passwordMissing && hostKey == HostKey.KNOWN;
        }
    }

    private ConnectionTester() {
    }

    /**
     * Blocking: panggil dari luar EDT.
     *
     * @param profile     profil yang dites (boleh draft belum tersimpan)
     * @param lookup      profil lain menurut id (untuk jump host); draft diutamakan untuk id-nya sendiri
     * @param credentials secret dari form/vault; null = tidak tersedia. Array yang dikembalikan dikosongkan setelah
     *                    dipakai, jadi kembalikan salinan baru setiap kali
     */
    public static Result test(HostProfile profile, Function<UUID, Optional<HostProfile>> lookup,
                              KnownHostsStore knownHosts, CredentialProvider credentials, Duration timeout) {
        var prompt = new RecordingPrompt();
        var creds = new NoRetryCredentials(credentials);
        var settings = new SshSettings(timeout, timeout, timeout, Duration.ZERO, 0, Duration.ZERO);
        Function<UUID, Optional<HostProfile>> profiles = id -> id.equals(profile.id())
                ? Optional.of(profile) : lookup.apply(id);
        long start = System.nanoTime();
        try (var manager = new SessionManager(knownHosts, prompt, creds, settings, profiles)) {
            try (var lease = manager.acquire(profile)) {
                return new Result(HostKey.KNOWN, true, false, elapsed(start), null);
            }
        } catch (SshConnectException | RuntimeException e) {
            long ms = elapsed(start);
            if (prompt.changed) {
                return new Result(HostKey.CHANGED, false, false, ms, e.getMessage());
            }
            if (prompt.unknown) {
                return new Result(HostKey.NEW, false, false, ms, null);
            }
            if (creds.missing) {
                return new Result(HostKey.KNOWN, false, true, ms, null);
            }
            return new Result(creds.asked ? HostKey.KNOWN : HostKey.UNREACHED, false, false, ms,
                    e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    private static long elapsed(long start) {
        return Duration.ofNanos(System.nanoTime() - start).toMillis();
    }

    /** Tidak pernah mempercayai host baru (dan tidak menulis known_hosts); hanya mencatat hasilnya. */
    private static final class RecordingPrompt implements HostKeyPrompt {
        volatile boolean unknown;
        volatile boolean changed;

        @Override
        public boolean confirmUnknownHost(HostKeyInfo info) {
            unknown = true;
            return false;
        }

        @Override
        public void hostKeyChanged(HostKeyInfo presented, List<String> knownFingerprints) {
            changed = true;
        }
    }

    /** Satu kali coba: password yang ditolak tidak diminta ulang. */
    private static final class NoRetryCredentials implements CredentialProvider {
        private final CredentialProvider source;
        volatile boolean asked;
        volatile boolean missing;

        NoRetryCredentials(CredentialProvider source) {
            this.source = source;
        }

        @Override
        public char[] password(HostProfile profile, int attempt) {
            asked = true;
            if (attempt > 1) {
                return null;
            }
            char[] pw = source.password(profile, attempt);
            missing |= pw == null;
            return pw;
        }

        @Override
        public char[] keyPassphrase(HostProfile profile, Path keyFile, int attempt) {
            asked = true;
            if (attempt > 1) {
                return null;
            }
            char[] pass = source.keyPassphrase(profile, keyFile, attempt);
            missing |= pass == null;
            return pass;
        }
    }
}
