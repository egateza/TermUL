package dev.egateza.termul.sftp.ftp;

import dev.egateza.termul.core.io.AtomicFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Sertifikat FTPS yang dipercaya user (TOFU) untuk server yang sertifikatnya tidak lolos validasi CA, mis. self-signed.
 * Satu baris per server: {@code host port SHA256:<base64>} (fingerprint sertifikat leaf, format seperti OpenSSH).
 * Sertifikat yang lolos validasi CA + hostname tidak dicatat di sini (supaya rotasi sertifikat tidak memutus koneksi).
 *
 * <p>Thread-safe (semua akses file di-synchronize pada instance).
 */
public final class FtpsCertStore {

    /** Satu sertifikat yang dipercaya. */
    public record Entry(String host, int port, String fingerprint) {
        public Entry {
            host = normalizeHost(host);
            Objects.requireNonNull(fingerprint, "fingerprint");
        }

        public String hostLabel() {
            return hostLabel(host, port);
        }

        static String hostLabel(String host, int port) {
            return host.contains(":") ? "[" + host + "]:" + port : host + ":" + port;
        }
    }

    private static final String HEADER = "# TermUL: sertifikat FTPS yang dipercaya (host port fingerprint)";

    private final Path file;

    public FtpsCertStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public Path file() {
        return file;
    }

    public synchronized List<Entry> entries() {
        var result = new ArrayList<Entry>();
        for (String line : readLines()) {
            Entry e = parse(line);
            if (e != null) {
                result.add(e);
            }
        }
        return result;
    }

    /** Fingerprint yang dipercaya untuk host:port ini, kalau ada. */
    public synchronized Optional<String> find(String host, int port) {
        String h = normalizeHost(host);
        return entries().stream()
                .filter(e -> e.host().equals(h) && e.port() == port)
                .map(Entry::fingerprint)
                .findFirst();
    }

    /** Catat (atau ganti) sertifikat yang dipercaya untuk host:port ini. */
    public synchronized void trust(String host, int port, String fingerprint) {
        var entries = new ArrayList<>(entries());
        var added = new Entry(host, port, fingerprint);
        entries.removeIf(e -> e.host().equals(added.host()) && e.port() == port);
        entries.add(added);
        write(entries);
    }

    /** @return true kalau ada entry yang dihapus */
    public synchronized boolean remove(String host, int port) {
        var entries = new ArrayList<>(entries());
        String h = normalizeHost(host);
        boolean removed = entries.removeIf(e -> e.host().equals(h) && e.port() == port);
        if (removed) {
            write(entries);
        }
        return removed;
    }

    /** Fingerprint {@code SHA256:<base64 tanpa padding>} dari encoding DER sertifikat. */
    public static String fingerprint(X509Certificate cert) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            throw new IllegalStateException("Fingerprint sertifikat tidak bisa dihitung", e);
        }
    }

    private static String normalizeHost(String host) {
        return Objects.requireNonNull(host, "host").strip().toLowerCase(Locale.ROOT);
    }

    private static Entry parse(String line) {
        String t = line.strip();
        if (t.isEmpty() || t.startsWith("#")) {
            return null;
        }
        String[] parts = t.split("\\s+");
        if (parts.length != 3 || !parts[2].startsWith("SHA256:")) {
            return null;
        }
        try {
            int port = Integer.parseInt(parts[1]);
            return port < 1 || port > 65_535 ? null : new Entry(parts[0], port, parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> readLines() {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return List.of();
        } catch (IOException e) {
            throw new UncheckedIOException("File sertifikat FTPS tidak bisa dibaca: " + file, e);
        }
    }

    private void write(List<Entry> entries) {
        var sb = new StringBuilder(HEADER).append('\n');
        for (Entry e : entries) {
            sb.append(e.host()).append(' ').append(e.port()).append(' ').append(e.fingerprint()).append('\n');
        }
        try {
            AtomicFiles.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("File sertifikat FTPS tidak bisa ditulis: " + file, e);
        }
    }
}
