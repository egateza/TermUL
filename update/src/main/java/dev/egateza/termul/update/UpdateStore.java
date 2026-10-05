package dev.egateza.termul.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.PublicKey;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongConsumer;
import java.util.stream.Stream;

/**
 * Folder update di disk ({@code <cacheDir>/updates}):
 *
 * <pre>
 * current                    versi aktif (teks), ditulis atomic
 * 0.1.130/manifest.json      byte asli yang ditandatangani
 * 0.1.130/manifest.json.sig
 * 0.1.130/*.jar              classpath lengkap
 * 0.1.130/.attempts          jumlah start sebelum ditandai sehat
 * 0.1.130/.healthy           ada = pernah sampai window utama tampil
 * .staging-*                 folder sementara saat memasang
 * </pre>
 *
 * Kelas ini dipakai Bootstrap, jadi <b>tidak boleh</b> memakai SLF4J (logger harus pertama dimuat oleh classloader
 * aplikasi); alasan penolakan dikembalikan sebagai catatan.
 */
public final class UpdateStore {

    static final String CURRENT = "current";
    static final String HEALTHY = ".healthy";
    static final String ATTEMPTS = ".attempts";
    static final String STAGING_PREFIX = ".staging-";
    /** Start berturut-turut tanpa pernah sehat sebelum Bootstrap kembali ke versi bawaan. */
    static final int MAX_UNHEALTHY_STARTS = 3;
    private static final int MAX_MANIFEST_BYTES = 256 * 1024;

    private final Path dir;

    /** Versi yang terpasang dan sudah diverifikasi (tanda tangan + hash semua jar). */
    public record Installed(UpdateManifest manifest, Path dir) {
        /** Classpath sesuai urutan manifest. */
        public List<Path> jars() {
            return manifest.files().stream().map(f -> dir.resolve(f.name())).toList();
        }
    }

    public UpdateStore(Path dir) {
        this.dir = dir;
    }

    public Path dir() {
        return dir;
    }

    /** @return versi yang ditunjuk {@code current}, belum diverifikasi */
    public Optional<ReleaseVersion> current() {
        try {
            Path pointer = dir.resolve(CURRENT);
            if (!Files.isRegularFile(pointer) || Files.size(pointer) > 64) {
                return Optional.empty();
            }
            return Optional.ofNullable(ReleaseVersion.parseOrNull(Files.readString(pointer, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public Path versionDir(ReleaseVersion version) {
        return dir.resolve(version.toString());
    }

    /**
     * Dipanggil Bootstrap: update yang boleh dijalankan, atau kosong (pakai versi bawaan). Alasan penolakan
     * ditambahkan ke {@code notes}.
     *
     * @param bundled    versi bawaan installer
     * @param generation generation Bootstrap yang sedang berjalan
     */
    public Optional<Installed> select(PublicKey key, ReleaseVersion bundled, int generation, List<String> notes) {
        Optional<ReleaseVersion> pointer = current();
        if (pointer.isEmpty()) {
            return Optional.empty();
        }
        ReleaseVersion version = pointer.get();
        if (!version.isNewerThan(bundled)) {
            notes.add("Update " + version + " tidak dipakai: versi bawaan installer (" + bundled + ") sama atau lebih baru");
            return Optional.empty();
        }
        Installed installed;
        try {
            installed = verify(versionDir(version), key);
        } catch (UpdateException e) {
            notes.add("Update " + version + " tidak dipakai: " + e.getMessage());
            return Optional.empty();
        }
        if (!installed.manifest().version().equals(version)) {
            notes.add("Update " + version + " tidak dipakai: versi di manifest berbeda ("
                    + installed.manifest().version() + ")");
            return Optional.empty();
        }
        if (installed.manifest().generation() > generation) {
            notes.add("Update " + version + " tidak dipakai: butuh installer generation "
                    + installed.manifest().generation() + ", terpasang " + generation);
            return Optional.empty();
        }
        if (!isHealthy(version) && attempts(version) >= MAX_UNHEALTHY_STARTS) {
            notes.add("Update " + version + " gagal start " + MAX_UNHEALTHY_STARTS
                    + " kali berturut-turut; kembali ke versi bawaan " + bundled);
            return Optional.empty();
        }
        return Optional.of(installed);
    }

    /** Bootstrap: catat satu percobaan start (hanya selama versi belum pernah sehat). */
    public void recordStart(ReleaseVersion version) {
        if (isHealthy(version)) {
            return;
        }
        try {
            Files.writeString(versionDir(version).resolve(ATTEMPTS), String.valueOf(attempts(version) + 1));
        } catch (IOException e) {
            // tanpa penghitung, rollback otomatis tidak jalan; aplikasi tetap bisa start
        }
    }

    /** Aplikasi: versi ini berhasil sampai window utama tampil. */
    public void markHealthy(ReleaseVersion version) throws IOException {
        Path d = versionDir(version);
        if (Files.isDirectory(d) && !isHealthy(version)) {
            Files.writeString(d.resolve(HEALTHY), "");
            Files.deleteIfExists(d.resolve(ATTEMPTS));
        }
    }

    boolean isHealthy(ReleaseVersion version) {
        return Files.exists(versionDir(version).resolve(HEALTHY));
    }

    int attempts(ReleaseVersion version) {
        try {
            return Integer.parseInt(Files.readString(versionDir(version).resolve(ATTEMPTS)).strip());
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    /** Verifikasi tanda tangan manifest dan ukuran + SHA-256 setiap jar di {@code versionDir}. */
    public static Installed verify(Path versionDir, PublicKey key) throws UpdateException {
        if (!Files.isDirectory(versionDir)) {
            throw new UpdateException("folder " + versionDir + " tidak ada");
        }
        UpdateManifest manifest = SignedRelease.verify(read(versionDir.resolve(UpdateProtocol.MANIFEST)),
                read(versionDir.resolve(UpdateProtocol.SIGNATURE)), key).manifest();
        for (var entry : manifest.files()) {
            if (!Hashes.matches(versionDir.resolve(entry.name()), entry)) {
                throw new UpdateException("file " + entry.name() + " hilang atau berubah");
            }
        }
        return new Installed(manifest, versionDir);
    }

    /**
     * Pasang rilis: jar lokal disalin, sisanya diunduh, semua diverifikasi, lalu folder staging di-rename atomic dan
     * pointer {@code current} dipindah. Kalau gagal/dibatalkan, folder staging dihapus dan versi aktif tidak berubah.
     *
     * @param progress dipanggil dengan jumlah byte unduhan yang baru diterima
     */
    public Installed install(InstallPlan plan, AssetSource source, PublicKey key, LongConsumer progress)
            throws IOException, UpdateException, InterruptedException {
        SignedRelease release = plan.release();
        ReleaseVersion version = release.version();
        Path target = versionDir(version);
        Files.createDirectories(dir);
        if (Files.isDirectory(target)) {
            try {
                Installed existing = verify(target, key);
                writePointer(version);
                return existing;
            } catch (UpdateException e) {
                deleteTree(target); // sisa pemasangan lama yang rusak: pasang ulang
            }
        }
        Path staging = Files.createTempDirectory(dir, STAGING_PREFIX);
        try {
            for (var entry : release.manifest().files()) {
                checkInterrupted();
                Path file = staging.resolve(entry.name());
                Path local = plan.local().get(entry);
                if (local != null) {
                    Files.copy(local, file, StandardCopyOption.REPLACE_EXISTING);
                    if (Hashes.matches(file, entry)) {
                        continue;
                    }
                    Files.delete(file); // jar lokal berubah sejak direncanakan: unduh saja
                }
                source.download(version, entry, file, progress);
                if (!Hashes.matches(file, entry)) {
                    throw new UpdateException("File " + entry.name() + " rusak saat diunduh (hash tidak cocok). "
                            + "Coba lagi.");
                }
            }
            Files.write(staging.resolve(UpdateProtocol.MANIFEST), release.manifestJson());
            Files.write(staging.resolve(UpdateProtocol.SIGNATURE), release.signature());
            Installed staged = verify(staging, key);
            checkInterrupted();
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            writePointer(version);
            return new Installed(staged.manifest(), target);
        } finally {
            if (Files.exists(staging)) {
                deleteTree(staging);
            }
        }
    }

    /**
     * Hapus folder versi selain {@code keep} dan sisa staging. Gagal hapus diabaikan (mis. jar masih dipakai proses
     * TermUL lain di Windows); dicoba lagi pada pemasangan berikutnya.
     */
    public void cleanup(Set<ReleaseVersion> keep) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(dir)) {
            for (Path child : children) {
                String name = child.getFileName().toString();
                ReleaseVersion v = ReleaseVersion.parseOrNull(name);
                boolean stale = name.startsWith(STAGING_PREFIX) || (v != null && !keep.contains(v));
                if (stale && Files.isDirectory(child)) {
                    deleteTree(child);
                }
            }
        } catch (IOException e) {
            // tidak kritis
        }
    }

    private void writePointer(ReleaseVersion version) throws IOException {
        Path tmp = Files.createTempFile(dir, ".current-", ".tmp");
        try {
            Files.writeString(tmp, version.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, dir.resolve(CURRENT), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static byte[] read(Path file) throws UpdateException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] data = in.readNBytes(MAX_MANIFEST_BYTES + 1);
            if (data.length > MAX_MANIFEST_BYTES) {
                throw new UpdateException(file.getFileName() + " terlalu besar");
            }
            return data;
        } catch (IOException e) {
            throw new UpdateException(file.getFileName() + " tidak bisa dibaca", e);
        }
    }

    private static void checkInterrupted() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException("Pemasangan update dibatalkan");
        }
    }

    static void deleteTree(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    // file terkunci: biarkan
                }
            });
        } catch (IOException e) {
            // biarkan
        }
    }
}
