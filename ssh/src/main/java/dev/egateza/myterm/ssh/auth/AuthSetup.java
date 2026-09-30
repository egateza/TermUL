package dev.egateza.myterm.ssh.auth;

import dev.egateza.myterm.core.profile.HostProfile;
import dev.egateza.myterm.ssh.SshConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.sshd.client.auth.keyboard.UserInteraction;
import org.apache.sshd.client.auth.password.PasswordIdentityProvider;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.AttributeRepository.AttributeKey;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.session.SessionContext;

/**
 * Mengonfigurasi autentikasi {@link ClientSession} sesuai {@link HostProfile}.
 *
 * <p><b>Batas keamanan:</b> API MINA hanya menerima password/passphrase sebagai {@code String}.
 * Konversi {@code char[] → String} dilakukan <i>hanya</i> di sini, tepat saat MINA memintanya,
 * dan {@code char[]} asal langsung di-zero. String tersebut tidak disimpan di field mana pun,
 * tidak di-log, dan menjadi garbage setelah paket auth terkirim. Lihat docs/SECURITY.md.
 */
public final class AuthSetup {

    /** true kalau user membatalkan prompt password/passphrase. */
    public static final AttributeKey<AtomicBoolean> CANCELLED = new AttributeKey<>();

    public static final int MAX_PASSWORD_ATTEMPTS = 3;

    private static final List<String> DEFAULT_KEY_FILES = List.of("id_ed25519", "id_ecdsa", "id_rsa");

    private AuthSetup() {
    }

    public static void configure(ClientSession session, HostProfile profile, CredentialProvider credentials)
            throws SshConnectException {
        var cancelled = new AtomicBoolean();
        session.setAttribute(CANCELLED, cancelled);
        session.setUserInteraction(UserInteraction.NONE);
        session.setPasswordIdentityProvider(PasswordIdentityProvider.EMPTY_PASSWORDS_PROVIDER);
        session.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER);

        switch (profile.authMethod()) {
            case KEY -> {
                Path key = Path.of(profile.privateKeyPath());
                if (!Files.isRegularFile(key)) {
                    throw new SshConnectException("File private key tidak ditemukan: " + key);
                }
                session.setKeyIdentityProvider(keyProvider(List.of(key), profile, credentials, cancelled));
            }
            case AGENT -> {
                // Sementara: fallback ke key default di ~/.ssh (agent Windows menyusul, lihat plan-recap.md)
                var keys = defaultKeyFiles();
                if (keys.isEmpty()) {
                    throw new SshConnectException("Tidak ada key di ~/.ssh (id_ed25519/id_ecdsa/id_rsa) untuk auth agent/default");
                }
                session.setKeyIdentityProvider(keyProvider(keys, profile, credentials, cancelled));
            }
            case PASSWORD -> {
                var attempts = new AtomicInteger();
                session.setUserInteraction(new PasswordInteraction(profile, credentials, attempts, cancelled));
            }
        }
    }

    static List<Path> defaultKeyFiles() {
        Path sshDir = Path.of(System.getProperty("user.home"), ".ssh");
        return DEFAULT_KEY_FILES.stream().map(sshDir::resolve).filter(Files::isRegularFile).toList();
    }

    private static KeyIdentityProvider keyProvider(List<Path> keys, HostProfile profile, CredentialProvider credentials,
                                                   AtomicBoolean cancelled) {
        var provider = new FileKeyPairProvider(keys);
        provider.setPasswordFinder(new FilePasswordProvider() {
            @Override
            public String getPassword(SessionContext session, NamedResource resourceKey, int retryIndex) {
                char[] pass = credentials.keyPassphrase(profile, Path.of(resourceKey.getName()), retryIndex + 1);
                if (pass == null) {
                    cancelled.set(true);
                    return null;
                }
                return consume(pass);
            }

            @Override
            public ResourceDecodeResult handleDecodeAttemptResult(SessionContext session, NamedResource resourceKey,
                                                                  int retryIndex, String password, Exception err) {
                if (err == null) {
                    return ResourceDecodeResult.TERMINATE;
                }
                return retryIndex + 1 < MAX_PASSWORD_ATTEMPTS && !cancelled.get()
                        ? ResourceDecodeResult.RETRY : ResourceDecodeResult.IGNORE;
            }
        });
        return provider;
    }

    /** char[] → String untuk MINA, lalu zero array asal. */
    private static String consume(char[] secret) {
        try {
            return new String(secret);
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    /** Prompt password berikutnya (maks {@link #MAX_PASSWORD_ATTEMPTS}); null = habis/batal. */
    private static String fetch(HostProfile profile, CredentialProvider credentials, AtomicInteger attempts,
                                AtomicBoolean cancelled) {
        if (cancelled.get() || attempts.get() >= MAX_PASSWORD_ATTEMPTS) {
            return null;
        }
        char[] pw = credentials.password(profile, attempts.incrementAndGet());
        if (pw == null) {
            cancelled.set(true);
            return null;
        }
        return consume(pw);
    }

    /** keyboard-interactive (PAM): jawab prompt tanpa echo dengan password. */
    private record PasswordInteraction(HostProfile profile, CredentialProvider credentials, AtomicInteger attempts,
                                       AtomicBoolean cancelled) implements UserInteraction {

        @Override
        public boolean isInteractionAllowed(ClientSession session) {
            return !cancelled.get();
        }

        @Override
        public String[] interactive(ClientSession session, String name, String instruction, String lang,
                                    String[] prompt, boolean[] echo) {
            var answers = new String[prompt.length];
            for (int i = 0; i < prompt.length; i++) {
                if (echo[i]) {
                    return null; // prompt dengan echo (bukan password) belum didukung
                }
                answers[i] = fetch(profile, credentials, attempts, cancelled);
                if (answers[i] == null) {
                    return null;
                }
            }
            return answers;
        }

        /** Dipanggil UserAuthPassword hanya saat butuh percobaan berikutnya (lazy). */
        @Override
        public String resolveAuthPasswordAttempt(ClientSession session) {
            return fetch(profile, credentials, attempts, cancelled);
        }

        @Override
        public String getUpdatedPassword(ClientSession session, String prompt, String lang) {
            return null; // ganti password yang expired tidak didukung
        }
    }
}
