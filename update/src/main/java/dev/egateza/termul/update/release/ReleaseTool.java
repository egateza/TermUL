package dev.egateza.termul.update.release;

import dev.egateza.termul.update.Hashes;
import dev.egateza.termul.update.ManifestSignature;
import dev.egateza.termul.update.ReleaseVersion;
import dev.egateza.termul.update.SignedRelease;
import dev.egateza.termul.update.UpdateException;
import dev.egateza.termul.update.UpdateKeys;
import dev.egateza.termul.update.UpdateManifest;
import dev.egateza.termul.update.UpdateProtocol;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Alat rilis (dijalankan di mesin rilis, bukan bagian dari alur aplikasi). Lihat {@code tools/release.ps1}.
 *
 * <pre>
 * keygen  [--key FILE]                         buat pasangan key; cetak public key untuk UpdateKeys
 * publish --input DIR --out DIR [--key FILE] [--notes FILE] [--allow-dirty]
 *         DIR input = app/target/jpackage-input (termul-app-*.jar + libs/*.jar)
 * </pre>
 * Lokasi private key default {@code ~/.termul-release/update-signing.key}, atau env {@code TERMUL_UPDATE_KEY}.
 */
public final class ReleaseTool {

    static final String KEY_ENV = "TERMUL_UPDATE_KEY";

    private ReleaseTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            return;
        }
        Map<String, String> opts = options(Arrays.copyOfRange(args, 1, args.length));
        Path keyFile = opts.containsKey("key") ? Path.of(opts.get("key")) : defaultKeyFile();
        try {
            switch (args[0]) {
                case "keygen" -> {
                    String publicKey = keygen(keyFile);
                    System.out.println("Private key ditulis ke " + keyFile);
                    System.out.println("SIMPAN CADANGAN file ini secara offline dan JANGAN di-commit.");
                    System.out.println("Public key (isi UpdateKeys.PUBLIC_KEY):");
                    System.out.println(publicKey);
                }
                case "publish" -> {
                    Path notesFile = opts.containsKey("notes") ? Path.of(opts.get("notes")) : null;
                    String notes = notesFile == null ? "" : Files.readString(notesFile, StandardCharsets.UTF_8).strip();
                    UpdateManifest m = publish(Path.of(required(opts, "input")), Path.of(required(opts, "out")),
                            loadPrivateKey(keyFile), UpdateKeys.publicKey(), notes, opts.containsKey("allow-dirty"));
                    System.out.printf("Rilis %s: %d file, %.1f MB, generation %d%n", m.version(), m.files().size(),
                            m.totalSize() / 1048576.0, m.generation());
                    System.out.println("version=" + m.version());
                }
                default -> usage();
            }
        } catch (UpdateException e) {
            System.err.println("GAGAL: " + e.getMessage());
            System.exit(1);
        }
    }

    /** @return public key (X.509, Base64) */
    static String keygen(Path keyFile) throws IOException, GeneralSecurityException, UpdateException {
        if (Files.exists(keyFile)) {
            throw new UpdateException("File key sudah ada, tidak ditimpa: " + keyFile);
        }
        KeyPair pair = KeyPairGenerator.getInstance(ManifestSignature.ALGORITHM).generateKeyPair();
        Files.createDirectories(keyFile.toAbsolutePath().getParent());
        byte[] encoded = pair.getPrivate().getEncoded();
        byte[] text = (Base64.getEncoder().encodeToString(encoded) + "\n").getBytes(StandardCharsets.US_ASCII);
        try {
            Files.createFile(keyFile, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } catch (UnsupportedOperationException e) {
            Files.createFile(keyFile); // Windows: ACL default folder profil user sudah membatasi ke user tsb.
        }
        try {
            Files.write(keyFile, text);
        } finally {
            Arrays.fill(encoded, (byte) 0);
            Arrays.fill(text, (byte) 0);
        }
        return Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    }

    static PrivateKey loadPrivateKey(Path keyFile) throws IOException, GeneralSecurityException, UpdateException {
        if (!Files.isRegularFile(keyFile)) {
            throw new UpdateException("Private key tidak ada: " + keyFile + " (buat dengan 'keygen', atau set "
                    + KEY_ENV + ")");
        }
        byte[] text = Files.readAllBytes(keyFile);
        byte[] der = null;
        try {
            der = Base64.getDecoder().decode(new String(text, StandardCharsets.US_ASCII).strip());
            return ManifestSignature.privateKey(der);
        } finally {
            Arrays.fill(text, (byte) 0);
            if (der != null) {
                Arrays.fill(der, (byte) 0);
            }
        }
    }

    /**
     * Susun folder aset rilis: semua jar (flat), {@code manifest.json}, {@code manifest.json.sig}.
     *
     * @param expected public key yang ditanam di aplikasi; tanda tangan diverifikasi dengannya supaya rilis dengan
     *                 private key yang salah tidak pernah terunggah
     */
    static UpdateManifest publish(Path input, Path out, PrivateKey key, PublicKey expected, String notes,
                                  boolean allowDirty) throws IOException, GeneralSecurityException, UpdateException {
        Path mainJar = mainJar(input);
        Properties info = buildInfo(mainJar);
        ReleaseVersion version = ReleaseVersion.parseOrNull(info.getProperty("version"));
        if (version == null) {
            throw new UpdateException("Jar " + mainJar.getFileName() + " bukan build rilis (versi: "
                    + info.getProperty("version") + "). Build lewat Maven.");
        }
        String commit = info.getProperty("commit", "");
        if (commit.endsWith("-dirty") && !allowDirty) {
            throw new UpdateException("Build dari working tree yang belum di-commit (" + commit + "). Commit dulu, "
                    + "atau pakai --allow-dirty.");
        }
        var jars = new ArrayList<Path>(List.of(mainJar));
        Path libs = input.resolve("libs");
        if (Files.isDirectory(libs)) {
            try (Stream<Path> list = Files.list(libs)) {
                list.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().forEach(jars::add);
            }
        }
        var entries = new ArrayList<UpdateManifest.FileEntry>();
        for (Path jar : jars) {
            entries.add(new UpdateManifest.FileEntry(jar.getFileName().toString(), Hashes.sha256(jar), Files.size(jar)));
        }
        UpdateManifest manifest;
        try {
            manifest = new UpdateManifest(version, UpdateProtocol.GENERATION, UpdateProtocol.DEFAULT_MAIN_CLASS, notes,
                    entries);
        } catch (IllegalArgumentException e) {
            throw new UpdateException("Manifest tidak valid: " + e.getMessage(), e);
        }
        byte[] json = manifest.toJson();
        byte[] sig = ManifestSignature.sign(json, key);
        try {
            SignedRelease.verify(json, sig, expected);
        } catch (UpdateException e) {
            throw new UpdateException("Private key tidak cocok dengan UpdateKeys.PUBLIC_KEY; rilis dibatalkan.", e);
        }

        if (Files.isDirectory(out)) {
            try (Stream<Path> existing = Files.list(out)) {
                if (existing.findAny().isPresent()) {
                    throw new UpdateException("Folder output tidak kosong: " + out);
                }
            }
        }
        Files.createDirectories(out);
        for (Path jar : jars) {
            Files.copy(jar, out.resolve(jar.getFileName().toString()));
        }
        Files.write(out.resolve(UpdateProtocol.MANIFEST), json);
        Files.write(out.resolve(UpdateProtocol.SIGNATURE), sig);
        return manifest;
    }

    private static Path mainJar(Path input) throws IOException, UpdateException {
        try (Stream<Path> list = Files.list(input)) {
            List<Path> found = list.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("termul-app-") && n.endsWith(".jar");
            }).toList();
            if (found.size() != 1) {
                throw new UpdateException("Harus ada tepat satu termul-app-*.jar di " + input + ", ditemukan "
                        + found.size());
            }
            return found.getFirst();
        }
    }

    private static Properties buildInfo(Path jar) throws IOException, UpdateException {
        try (var zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry(UpdateProtocol.BUILD_INFO_RESOURCE);
            if (entry == null) {
                throw new UpdateException(UpdateProtocol.BUILD_INFO_RESOURCE + " tidak ada di " + jar.getFileName());
            }
            var props = new Properties();
            try (InputStream in = zip.getInputStream(entry)) {
                props.load(in);
            }
            return props;
        }
    }

    private static Path defaultKeyFile() {
        String env = System.getenv(KEY_ENV);
        return env != null && !env.isBlank() ? Path.of(env)
                : Path.of(System.getProperty("user.home"), ".termul-release", "update-signing.key");
    }

    private static Map<String, String> options(String[] args) throws UpdateException {
        var opts = new HashMap<String, String>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                throw new UpdateException("Argumen tidak dikenal: " + args[i]);
            }
            String name = args[i].substring(2);
            if (name.equals("allow-dirty")) {
                opts.put(name, "true");
            } else if (i + 1 < args.length) {
                opts.put(name, args[++i]);
            } else {
                throw new UpdateException("Nilai untuk --" + name + " tidak ada");
            }
        }
        return opts;
    }

    private static String required(Map<String, String> opts, String name) throws UpdateException {
        String v = opts.get(name);
        if (v == null) {
            throw new UpdateException("--" + name + " wajib diisi");
        }
        return v;
    }

    private static void usage() {
        System.out.println("""
                ReleaseTool keygen  [--key FILE]
                ReleaseTool publish --input DIR --out DIR [--key FILE] [--notes FILE] [--allow-dirty]""");
    }
}
