package dev.egateza.termul.ssh.hostkey;

import dev.egateza.termul.core.io.AtomicFiles;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

    /**
     * Satu entry known_hosts untuk ditampilkan/dihapus.
     *
     * @param line        isi baris apa adanya (identitas saat {@link #remove})
     * @param hosts       pola host, atau {@code null} kalau entry di-hash ({@code |1|...})
     * @param marker      {@code @revoked}/{@code @cert-authority} tanpa {@code @}, atau {@code null}
     * @param algorithm   tipe key, mis. {@code ssh-ed25519}
     * @param fingerprint {@code SHA256:...}, atau {@code null} kalau key tidak bisa dibaca
     */
    public record Entry(String line, String hosts, String marker, String algorithm, String fingerprint) {
        public boolean hashed() {
            return hosts == null;
        }
    }

    /** Semua entry yang bisa di-parse, urut sesuai file (komentar dan baris rusak dilewati). */
    public synchronized List<Entry> entries() {
        var result = new ArrayList<Entry>();
        for (String line : readLines()) {
            Entry entry = parse(line);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Entry (bukan {@code @revoked}/{@code @cert-authority}) yang cocok dengan host:port, termasuk entry hashed. */
    public synchronized List<Entry> entriesFor(String host, int port) {
        var result = new ArrayList<Entry>();
        for (String line : readLines()) {
            Entry entry = parse(line);
            if (entry != null && entry.marker() == null && matches(line, host, port)) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Menghapus baris yang isinya sama persis dengan {@link Entry#line()} entry yang diberikan. Baris lain (komentar,
     * {@code @revoked}, dll.) tetap utuh; file ditulis ulang secara atomic.
     *
     * @return jumlah baris yang dihapus
     */
    public synchronized int remove(Collection<Entry> toRemove) {
        Set<String> lines = new HashSet<>();
        toRemove.forEach(e -> lines.add(e.line()));
        var kept = new ArrayList<String>();
        int removed = 0;
        for (String line : readLines()) {
            if (lines.contains(line)) {
                removed++;
            } else {
                kept.add(line);
            }
        }
        if (removed == 0) {
            return 0;
        }
        var content = new StringBuilder();
        kept.forEach(l -> content.append(l).append(System.lineSeparator()));
        try {
            AtomicFiles.write(file, content.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal menulis known_hosts: " + file, e);
        }
        log.info("{} entry dihapus dari known_hosts", removed);
        return removed;
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

    private List<String> readLines() {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return List.of();
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal membaca known_hosts: " + file, e);
        }
    }

    private static KnownHostEntry parseEntry(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        try {
            return KnownHostEntry.parseKnownHostEntry(trimmed);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Entry parse(String line) {
        KnownHostEntry entry = parseEntry(line);
        if (entry == null || entry.getKeyEntry() == null) {
            return null;
        }
        String[] tokens = line.strip().split("\\s+");
        int hostsIndex = entry.getMarker() != null ? 1 : 0;
        String hosts = entry.getHashedEntry() != null || tokens.length <= hostsIndex ? null : tokens[hostsIndex];
        PublicKey key = resolve(entry.getKeyEntry());
        return new Entry(line, hosts, entry.getMarker(), entry.getKeyEntry().getKeyType(),
                key == null ? null : HostKeyInfo.fingerprint(key));
    }

    private static boolean matches(String line, String host, int port) {
        KnownHostEntry entry = parseEntry(line);
        return entry != null && entry.isHostMatch(host, port);
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
