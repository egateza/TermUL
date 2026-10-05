package dev.egateza.termul.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pembuat key, jar palsu, dan rilis bertanda tangan untuk test. */
final class Fixtures {

    static final String MAIN = "dev.egateza.termul.app.TermULApp";

    private Fixtures() {
    }

    static KeyPair keyPair() {
        try {
            return KeyPairGenerator.getInstance(ManifestSignature.ALGORITHM).generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Rilis berisi jar dengan isi teks; byte jar disimpan supaya bisa disajikan server palsu. */
    static final class Release {
        final Map<String, byte[]> jars = new LinkedHashMap<>();
        String version = "0.1.200";
        int generation = 1;
        String notes = "Perbaikan";

        Release jar(String name, String content) {
            jars.put(name, content.getBytes(StandardCharsets.UTF_8));
            return this;
        }

        Release version(String v) {
            version = v;
            return this;
        }

        Release generation(int g) {
            generation = g;
            return this;
        }

        UpdateManifest manifest() {
            var entries = new ArrayList<UpdateManifest.FileEntry>();
            jars.forEach((name, data) -> entries.add(new UpdateManifest.FileEntry(name, sha256(data), data.length)));
            return new UpdateManifest(ReleaseVersion.parse(version), generation, MAIN, notes, entries);
        }

        SignedRelease signed(KeyPair key) throws Exception {
            byte[] json = manifest().toJson();
            return SignedRelease.verify(json, ManifestSignature.sign(json, key.getPrivate()), key.getPublic());
        }

        /** Tulis jar ke {@code dir}; dipakai sebagai "jar lokal" (classpath bawaan). */
        List<Path> writeJars(Path dir) throws IOException {
            Files.createDirectories(dir);
            var paths = new ArrayList<Path>();
            for (var e : jars.entrySet()) {
                paths.add(Files.write(dir.resolve(e.getKey()), e.getValue()));
            }
            return paths;
        }

        /** Sumber unduhan dari memori; mencatat nama file yang diunduh. */
        FakeFeed feed(KeyPair key) throws Exception {
            return new FakeFeed(signed(key), jars);
        }
    }

    static final class FakeFeed implements ReleaseFeed {
        final SignedRelease release;
        final Map<String, byte[]> jars;
        final List<String> downloaded = new ArrayList<>();

        FakeFeed(SignedRelease release, Map<String, byte[]> jars) {
            this.release = release;
            this.jars = new LinkedHashMap<>(jars);
        }

        @Override
        public SignedRelease fetchLatest(java.security.PublicKey key) {
            return release;
        }

        @Override
        public void download(ReleaseVersion version, UpdateManifest.FileEntry entry, Path target,
                             java.util.function.LongConsumer progress) throws IOException {
            downloaded.add(entry.name());
            byte[] data = jars.get(entry.name());
            Files.write(target, data);
            progress.accept(data.length);
        }
    }

    static String sha256(byte[] data) {
        return java.util.HexFormat.of().formatHex(Hashes.newDigest().digest(data));
    }
}
