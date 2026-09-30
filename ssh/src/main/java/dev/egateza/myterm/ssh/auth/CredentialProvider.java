package dev.egateza.myterm.ssh.auth;

import dev.egateza.myterm.core.profile.HostProfile;
import java.nio.file.Path;

/**
 * Sumber secret untuk autentikasi (prompt UI sekarang, vault di Fase 2).
 *
 * <p>Array yang dikembalikan menjadi milik pemanggil dan akan di-zero setelah dipakai.
 * Dipanggil dari thread I/O MINA; implementasi UI harus pindah ke EDT sendiri.
 */
public interface CredentialProvider {

    /**
     * @param attempt percobaan ke- (mulai 1); percobaan &gt; 1 berarti password sebelumnya ditolak
     * @return password, atau null kalau user membatalkan
     */
    char[] password(HostProfile profile, int attempt);

    /** @return passphrase private key, atau null kalau user membatalkan */
    char[] keyPassphrase(HostProfile profile, Path keyFile, int attempt);

    /** Tidak pernah memberi secret (untuk test / mode non-interaktif). */
    CredentialProvider NONE = new CredentialProvider() {
        @Override
        public char[] password(HostProfile profile, int attempt) {
            return null;
        }

        @Override
        public char[] keyPassphrase(HostProfile profile, Path keyFile, int attempt) {
            return null;
        }
    };
}
