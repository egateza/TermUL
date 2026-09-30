package dev.egateza.termul.vault;

import dev.egateza.termul.core.io.AtomicFiles;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementasi vault sesuai ADR 0002: DEK acak dibungkus KEK (Argon2id dari master password),
 * setiap secret AES-256-GCM dengan AAD {@code profileId|secretType}. Opsional DEK dibungkus DPAPI.
 *
 * <p>Thread-safe (semua method public synchronized).
 */
public final class FileCredentialVault implements CredentialVault {

    private static final Logger log = LoggerFactory.getLogger(FileCredentialVault.class);

    private static final byte[] MAGIC = {'M', 'Y', 'T', 'V'};
    private static final int VERSION = 1;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_LENGTH = 32;
    // Bagian dari format vault: jangan diganti walaupun aplikasi di-rename (vault lama tidak bisa dibuka lagi)
    private static final byte[] CHECK_PLAINTEXT = "MYTERM-VAULT-OK".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CHECK_AAD = "key-check".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_BLOB = 1 << 20;

    private record EntryKey(UUID profileId, SecretType type) {
        byte[] aad() {
            return (profileId + "|" + type.name()).getBytes(StandardCharsets.UTF_8);
        }
    }

    private record Sealed(byte[] nonce, byte[] ciphertext) {
    }

    /** Isi file vault (tanpa DEK plaintext). */
    private record VaultFile(Argon2Params params, byte[] salt, Sealed wrappedDek, Sealed keyCheck,
                             Map<EntryKey, Sealed> entries) {
    }

    private final Path file;
    private final Path osKeyFile;
    private final Argon2Params newVaultParams;
    private final KeyProtector protector;
    private final SecureRandom random = new SecureRandom();

    private VaultFile data;   // guarded by this; null = belum dibaca
    private byte[] dek;       // guarded by this; null = terkunci

    /**
     * @param file           {@code vault.bin}
     * @param newVaultParams parameter Argon2 untuk vault baru / ganti password
     * @param protector      DPAPI (atau {@link KeyProtector#UNAVAILABLE})
     */
    public FileCredentialVault(Path file, Argon2Params newVaultParams, KeyProtector protector) {
        this.file = Objects.requireNonNull(file);
        this.osKeyFile = file.resolveSibling(file.getFileName() + ".dpapi");
        this.newVaultParams = Objects.requireNonNull(newVaultParams);
        this.protector = Objects.requireNonNull(protector);
    }

    @Override
    public synchronized boolean exists() {
        return Files.exists(file);
    }

    @Override
    public synchronized boolean isUnlocked() {
        return dek != null;
    }

    @Override
    public synchronized void create(char[] masterPassword) {
        try {
            if (Files.exists(file)) {
                throw new VaultException("Vault sudah ada: " + file);
            }
            requireStrongEnough(masterPassword);
            byte[] newDek = randomBytes(KEY_LENGTH);
            byte[] salt = Argon2Params.newSalt(random);
            byte[] kek = newVaultParams.deriveKey(masterPassword, salt);
            try {
                var header = headerBytes(newVaultParams, salt);
                var vf = new VaultFile(newVaultParams, salt, seal(kek, newDek, header),
                        seal(newDek, CHECK_PLAINTEXT, CHECK_AAD), new LinkedHashMap<>());
                write(vf);
                data = vf;
                dek = newDek;
                log.info("Vault baru dibuat di {}", file);
            } finally {
                Secrets.zero(kek);
            }
        } finally {
            Secrets.zero(masterPassword);
        }
    }

    @Override
    public synchronized void unlock(char[] masterPassword) {
        try {
            VaultFile vf = read();
            byte[] kek = vf.params().deriveKey(masterPassword, vf.salt());
            try {
                byte[] key = open(kek, vf.wrappedDek(), headerBytes(vf.params(), vf.salt()));
                if (key == null) {
                    throw new VaultException.WrongPassword();
                }
                verifyKey(vf, key);
                lockInternal();
                data = vf;
                dek = key;
            } finally {
                Secrets.zero(kek);
            }
        } finally {
            Secrets.zero(masterPassword);
        }
    }

    @Override
    public synchronized boolean unlockWithOsKey() {
        if (!protector.isAvailable() || !Files.exists(osKeyFile) || !Files.exists(file)) {
            return false;
        }
        try {
            VaultFile vf = read();
            byte[] key = protector.unprotect(Files.readAllBytes(osKeyFile));
            try {
                verifyKey(vf, key);
            } catch (VaultException e) {
                Secrets.zero(key);
                log.warn("Key DPAPI tidak cocok dengan vault; opsi 'ingat di PC ini' diabaikan");
                return false;
            }
            lockInternal();
            data = vf;
            dek = key;
            return true;
        } catch (IOException | RuntimeException e) {
            log.warn("Unlock via DPAPI gagal: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public synchronized void lock() {
        lockInternal();
    }

    @Override
    public synchronized char[] get(UUID profileId, SecretType type) {
        requireUnlocked();
        var key = new EntryKey(profileId, type);
        Sealed sealed = data.entries().get(key);
        if (sealed == null) {
            return null;
        }
        byte[] plain = open(dek, sealed, key.aad());
        if (plain == null) {
            throw new VaultException("Entry vault rusak atau dimodifikasi.");
        }
        try {
            return Secrets.fromUtf8(plain);
        } finally {
            Secrets.zero(plain);
        }
    }

    @Override
    public synchronized boolean has(UUID profileId, SecretType type) {
        if (data == null) {
            if (!Files.exists(file)) {
                return false;
            }
            data = read(); // metadata (id + tipe) tidak rahasia; ciphertext tetap butuh DEK
        }
        return data.entries().containsKey(new EntryKey(profileId, type));
    }

    @Override
    public synchronized void put(UUID profileId, SecretType type, char[] secret) {
        try {
            requireUnlocked();
            Objects.requireNonNull(profileId);
            Objects.requireNonNull(type);
            var key = new EntryKey(profileId, type);
            byte[] plain = Secrets.toUtf8(secret);
            try {
                var entries = new LinkedHashMap<>(data.entries());
                entries.put(key, seal(dek, plain, key.aad()));
                replaceEntries(entries);
            } finally {
                Secrets.zero(plain);
            }
        } finally {
            Secrets.zero(secret);
        }
    }

    @Override
    public synchronized void remove(UUID profileId, SecretType type) {
        requireUnlocked();
        var entries = new LinkedHashMap<>(data.entries());
        if (entries.remove(new EntryKey(profileId, type)) != null) {
            replaceEntries(entries);
        }
    }

    @Override
    public synchronized void removeProfile(UUID profileId) {
        requireUnlocked();
        var entries = new LinkedHashMap<>(data.entries());
        if (entries.keySet().removeIf(k -> k.profileId().equals(profileId))) {
            replaceEntries(entries);
        }
    }

    @Override
    public synchronized void changeMasterPassword(char[] oldPassword, char[] newPassword) {
        try {
            VaultFile vf = read();
            byte[] oldKek = vf.params().deriveKey(oldPassword, vf.salt());
            byte[] key;
            try {
                key = open(oldKek, vf.wrappedDek(), headerBytes(vf.params(), vf.salt()));
            } finally {
                Secrets.zero(oldKek);
            }
            if (key == null) {
                throw new VaultException.WrongPassword();
            }
            try {
                requireStrongEnough(newPassword);
                byte[] salt = Argon2Params.newSalt(random);
                byte[] newKek = newVaultParams.deriveKey(newPassword, salt);
                try {
                    var updated = new VaultFile(newVaultParams, salt,
                            seal(newKek, key, headerBytes(newVaultParams, salt)), vf.keyCheck(), vf.entries());
                    write(updated);
                    data = updated;
                } finally {
                    Secrets.zero(newKek);
                }
            } finally {
                Secrets.zero(key);
            }
            log.info("Master password vault diganti");
        } finally {
            Secrets.zero(oldPassword);
            Secrets.zero(newPassword);
        }
    }

    @Override
    public synchronized boolean isRememberedOnThisPc() {
        return Files.exists(osKeyFile);
    }

    @Override
    public synchronized void setRememberOnThisPc(boolean remember) {
        try {
            if (!remember) {
                Files.deleteIfExists(osKeyFile);
                return;
            }
            requireUnlocked();
            if (!protector.isAvailable()) {
                throw new VaultException("Opsi 'ingat di PC ini' hanya tersedia di Windows.");
            }
            AtomicFiles.write(osKeyFile, protector.protect(dek));
        } catch (IOException e) {
            throw new VaultException("Gagal menyimpan pengaturan 'ingat di PC ini'", e);
        }
    }

    // ---------------------------------------------------------------- internal

    private void lockInternal() {
        Secrets.zero(dek);
        dek = null;
    }

    private void requireUnlocked() {
        if (dek == null) {
            throw new VaultException.Locked();
        }
    }

    private static void requireStrongEnough(char[] password) {
        if (password == null || password.length < 8) {
            throw new VaultException("Master password minimal 8 karakter.");
        }
    }

    private void verifyKey(VaultFile vf, byte[] key) {
        byte[] check = open(key, vf.keyCheck(), CHECK_AAD);
        if (check == null || !Arrays.equals(check, CHECK_PLAINTEXT)) {
            throw new VaultException("File vault rusak (key check gagal).");
        }
    }

    private void replaceEntries(Map<EntryKey, Sealed> entries) {
        var updated = new VaultFile(data.params(), data.salt(), data.wrappedDek(), data.keyCheck(), entries);
        write(updated);
        data = updated;
    }

    private byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        random.nextBytes(b);
        return b;
    }

    private Sealed seal(byte[] key, byte[] plaintext, byte[] aad) {
        try {
            byte[] nonce = randomBytes(NONCE_LENGTH);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            c.updateAAD(aad);
            return new Sealed(nonce, c.doFinal(plaintext));
        } catch (GeneralSecurityException e) {
            throw new VaultException("Enkripsi gagal", e);
        }
    }

    /** @return plaintext, atau null kalau tag GCM tidak valid (key salah / data dimodifikasi) */
    private static byte[] open(byte[] key, Sealed sealed, byte[] aad) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, sealed.nonce()));
            c.updateAAD(aad);
            return c.doFinal(sealed.ciphertext());
        } catch (AEADBadTagException e) {
            return null;
        } catch (GeneralSecurityException e) {
            throw new VaultException("Dekripsi gagal", e);
        }
    }

    private static byte[] headerBytes(Argon2Params p, byte[] salt) {
        var bos = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bos)) {
            out.write(MAGIC);
            out.writeByte(VERSION);
            out.writeInt(p.memoryKiB());
            out.writeInt(p.iterations());
            out.writeInt(p.parallelism());
            out.write(salt);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bos.toByteArray();
    }

    private void write(VaultFile vf) {
        var bos = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bos)) {
            out.write(headerBytes(vf.params(), vf.salt()));
            writeSealed(out, vf.wrappedDek());
            writeSealed(out, vf.keyCheck());
            out.writeInt(vf.entries().size());
            for (var e : vf.entries().entrySet()) {
                out.writeLong(e.getKey().profileId().getMostSignificantBits());
                out.writeLong(e.getKey().profileId().getLeastSignificantBits());
                out.writeByte(e.getKey().type().code());
                writeSealed(out, e.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            AtomicFiles.write(file, bos.toByteArray());
        } catch (IOException e) {
            throw new VaultException("Gagal menyimpan vault: " + file, e);
        }
    }

    private static void writeSealed(DataOutputStream out, Sealed s) throws IOException {
        out.write(s.nonce());
        out.writeInt(s.ciphertext().length);
        out.write(s.ciphertext());
    }

    private VaultFile read() {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new VaultException("Vault tidak bisa dibaca: " + file, e);
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            byte[] magic = in.readNBytes(MAGIC.length);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new VaultException("Bukan file vault TermUL: " + file);
            }
            int version = in.readUnsignedByte();
            if (version != VERSION) {
                throw new VaultException("Versi vault tidak didukung: " + version);
            }
            var params = new Argon2Params(in.readInt(), in.readInt(), in.readInt());
            byte[] salt = readExact(in, Argon2Params.SALT_LENGTH);
            Sealed wrapped = readSealed(in);
            Sealed check = readSealed(in);
            int count = in.readInt();
            if (count < 0 || count > 100_000) {
                throw new VaultException("File vault rusak (jumlah entry).");
            }
            var entries = new LinkedHashMap<EntryKey, Sealed>();
            for (int i = 0; i < count; i++) {
                var id = new UUID(in.readLong(), in.readLong());
                var type = SecretType.fromCode(in.readUnsignedByte());
                entries.put(new EntryKey(id, type), readSealed(in));
            }
            if (in.read() != -1) {
                throw new VaultException("File vault rusak (data berlebih).");
            }
            return new VaultFile(params, salt, wrapped, check, entries);
        } catch (EOFException e) {
            throw new VaultException("File vault rusak (terpotong).", e);
        } catch (IOException | IllegalArgumentException e) {
            throw new VaultException("File vault rusak.", e);
        }
    }

    private static Sealed readSealed(DataInputStream in) throws IOException {
        byte[] nonce = readExact(in, NONCE_LENGTH);
        int len = in.readInt();
        if (len < 16 || len > MAX_BLOB) {
            throw new VaultException("File vault rusak (panjang blob).");
        }
        return new Sealed(nonce, readExact(in, len));
    }

    private static byte[] readExact(DataInputStream in, int n) throws IOException {
        byte[] b = new byte[n];
        in.readFully(b);
        return b;
    }
}
