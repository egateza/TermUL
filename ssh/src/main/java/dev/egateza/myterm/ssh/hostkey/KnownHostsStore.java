package dev.egateza.myterm.ssh.hostkey;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.sshd.client.config.hosts.KnownHostEntry;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File known_hosts milik aplikasi (format OpenSSH). Mendukung entry hashed dan pola host saat
 * membaca; entry baru ditulis plain ({@code host} atau {@code [host]:port}).
 * Thread-safe (semua akses file di-synchronize pada instance).
 */
public final class KnownHostsStore {

    private static final Logger log = LoggerFactory.getLogger(KnownHostsStore.class);

    private final Path file;

    public KnownHostsStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public Path file() {
        return file;
    }

    /** Semua key yang tercatat untuk host:port (entry {@code @revoked} diabaikan, jadi dianggap tidak cocok). */
    public synchronized List<PublicKey> lookup(String host, int port) {
        var result = new ArrayList<PublicKey>();
        for (KnownHostEntry entry : readEntries()) {
            if (entry.getMarker() != null || !entry.isHostMatch(host, port)) {
                continue;
            }
            PublicKey key = resolve(entry.getKeyEntry());
            if (key != null) {
                result.add(key);
            }
        }
        return result;
    }

    /** Menambah entry baru di akhir file. */
    public synchronized void add(String host, int port, PublicKey key) {
        String line = hostPattern(host, port) + " " + PublicKeyEntry.toString(key) + System.lineSeparator();
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            log.info("Host key {} ({}) ditambahkan ke known_hosts", hostPattern(host, port), KeyUtils.getKeyType(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal menulis known_hosts: " + file, e);
        }
    }

    /** Pola host format OpenSSH. */
    public static String hostPattern(String host, int port) {
        return port == 22 ? host : "[" + host + "]:" + port;
    }

    private List<KnownHostEntry> readEntries() {
        try {
            return KnownHostEntry.readKnownHostEntries(file);
        } catch (NoSuchFileException e) {
            return List.of();
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal membaca known_hosts: " + file, e);
        }
    }

    private static PublicKey resolve(AuthorizedKeyEntry entry) {
        if (entry == null) {
            return null;
        }
        try {
            return entry.resolvePublicKey(null, PublicKeyEntryResolver.IGNORING);
        } catch (IOException | GeneralSecurityException e) {
            log.warn("Entry known_hosts tidak bisa dibaca: {}", e.toString());
            return null;
        }
    }
}
