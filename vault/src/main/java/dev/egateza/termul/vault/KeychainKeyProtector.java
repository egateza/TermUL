package dev.egateza.termul.vault;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * macOS Keychain untuk opsi "Ingat di Mac ini" (ADR 0006). Keychain menyimpan <em>kunci pembungkus</em> acak 256-bit
 * (generic password, login keychain); DEK vault dibungkus AES-GCM dengan kunci itu dan blob-nya disimpan di file
 * {@code vault.bin.keychain}. Vault hanya terbuka kalau file <em>dan</em> item Keychain sama-sama ada.
 *
 * <p>Akses lewat JNA ke Security.framework ({@code SecItem*}), bukan CLI {@code security}, supaya kunci tidak pernah
 * lewat argumen proses. Item dibuat tanpa {@code kSecAttrSynchronizable}, jadi tidak ikut iCloud Keychain. Butuh
 * {@code --enable-native-access=ALL-UNNAMED}.
 */
public final class KeychainKeyProtector implements KeyProtector {

    /** Nama layanan di Keychain Access. Bagian dari format: jangan diganti (blob lama tidak bisa dibuka lagi). */
    public static final String SERVICE = "TermUL";

    private static final int KEY_LENGTH = 32;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final byte VERSION = 1;
    private static final byte[] AAD = "TermUL-vault-dek-keychain-v1".getBytes(StandardCharsets.US_ASCII);

    private static final int ERR_SEC_SUCCESS = 0;
    private static final int ERR_SEC_ITEM_NOT_FOUND = -25300;

    private final String service;
    private final String account;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param account nama item, unik per file vault (mis. hash path vault) supaya dua vault tidak saling menimpa
     */
    public KeychainKeyProtector(String account) {
        this(SERVICE, account);
    }

    /** Untuk test: layanan terpisah supaya tidak menyentuh item milik aplikasi. */
    KeychainKeyProtector(String service, String account) {
        this.service = Objects.requireNonNull(service);
        this.account = Objects.requireNonNull(account);
    }

    @Override
    public boolean isAvailable() {
        return Platform.isMac();
    }

    @Override
    public String blobSuffix() {
        return ".keychain";
    }

    /** Membuat kunci pembungkus baru (menggantikan yang lama), lalu membungkus {@code data} dengannya. */
    @Override
    public byte[] protect(byte[] data) {
        byte[] key = new byte[KEY_LENGTH];
        random.nextBytes(key);
        try {
            Keychain.delete(service, account);
            Keychain.add(service, account, key);
            byte[] nonce = new byte[NONCE_LENGTH];
            random.nextBytes(nonce);
            byte[] sealed = cipher(Cipher.ENCRYPT_MODE, key, nonce).doFinal(data);
            return ByteBuffer.allocate(1 + NONCE_LENGTH + sealed.length).put(VERSION).put(nonce).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new VaultException("Gagal membungkus key vault untuk Keychain", e);
        } finally {
            Secrets.zero(key);
        }
    }

    @Override
    public byte[] unprotect(byte[] blob) {
        if (blob.length < 1 + NONCE_LENGTH + TAG_BITS / 8 || blob[0] != VERSION) {
            throw new VaultException("Format file key Keychain tidak dikenal");
        }
        byte[] key = Keychain.find(service, account);
        if (key == null) {
            throw new VaultException("Item Keychain TermUL tidak ditemukan");
        }
        try {
            byte[] nonce = Arrays.copyOfRange(blob, 1, 1 + NONCE_LENGTH);
            return cipher(Cipher.DECRYPT_MODE, key, nonce).doFinal(blob, 1 + NONCE_LENGTH, blob.length - 1 - NONCE_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new VaultException("Key Keychain tidak cocok dengan file key vault", e);
        } finally {
            Secrets.zero(key);
        }
    }

    @Override
    public void discard() {
        Keychain.delete(service, account);
    }

    private static Cipher cipher(int mode, byte[] key, byte[] nonce) throws GeneralSecurityException {
        var c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        c.updateAAD(AAD);
        return c;
    }

    // ---------------------------------------------------------------- JNA

    /** Pembungkus tipis {@code SecItemAdd/CopyMatching/Delete} untuk item generic password. */
    private static final class Keychain {

        private interface CoreFoundation extends Library {
            CoreFoundation INSTANCE = Native.load("CoreFoundation", CoreFoundation.class);

            Pointer CFDictionaryCreateMutable(Pointer allocator, long capacity, Pointer keyCallBacks,
                                              Pointer valueCallBacks);

            void CFDictionarySetValue(Pointer dict, Pointer key, Pointer value);

            Pointer CFStringCreateWithCharacters(Pointer allocator, char[] chars, long length);

            Pointer CFDataCreate(Pointer allocator, Pointer bytes, long length);

            long CFDataGetLength(Pointer data);

            Pointer CFDataGetBytePtr(Pointer data);

            void CFRelease(Pointer ref);
        }

        private interface Security extends Library {
            Security INSTANCE = Native.load("Security", Security.class);

            int SecItemAdd(Pointer attributes, PointerByReference result);

            int SecItemCopyMatching(Pointer query, PointerByReference result);

            int SecItemDelete(Pointer query);
        }

        private static final NativeLibrary CF = NativeLibrary.getInstance("CoreFoundation");
        private static final NativeLibrary SEC = NativeLibrary.getInstance("Security");

        private Keychain() {
        }

        static void add(String service, String account, byte[] key) {
            Pointer query = query(service, account);
            Memory native_ = new Memory(key.length);
            Pointer value = null;
            try {
                native_.write(0, key, 0, key.length);
                value = CoreFoundation.INSTANCE.CFDataCreate(null, native_, key.length);
                CoreFoundation.INSTANCE.CFDictionarySetValue(query, sec("kSecValueData"), value);
                set(query, sec("kSecAttrLabel"), string("TermUL vault (Ingat di Mac ini)"));
                check(Security.INSTANCE.SecItemAdd(query, null), "menyimpan");
            } finally {
                native_.clear();
                native_.close();
                if (value != null) {
                    wipe(value);
                    CoreFoundation.INSTANCE.CFRelease(value);
                }
                CoreFoundation.INSTANCE.CFRelease(query);
            }
        }

        /** @return kunci (pemanggil wajib zero), atau null kalau item tidak ada */
        static byte[] find(String service, String account) {
            Pointer query = query(service, account);
            var result = new PointerByReference();
            try {
                CoreFoundation.INSTANCE.CFDictionarySetValue(query, sec("kSecReturnData"), cf("kCFBooleanTrue"));
                CoreFoundation.INSTANCE.CFDictionarySetValue(query, sec("kSecMatchLimit"), sec("kSecMatchLimitOne"));
                int status = Security.INSTANCE.SecItemCopyMatching(query, result);
                if (status == ERR_SEC_ITEM_NOT_FOUND) {
                    return null;
                }
                check(status, "membaca");
                Pointer data = result.getValue();
                try {
                    int length = (int) CoreFoundation.INSTANCE.CFDataGetLength(data);
                    return CoreFoundation.INSTANCE.CFDataGetBytePtr(data).getByteArray(0, length);
                } finally {
                    wipe(data);
                    CoreFoundation.INSTANCE.CFRelease(data);
                }
            } finally {
                CoreFoundation.INSTANCE.CFRelease(query);
            }
        }

        static void delete(String service, String account) {
            Pointer query = query(service, account);
            try {
                int status = Security.INSTANCE.SecItemDelete(query);
                if (status != ERR_SEC_ITEM_NOT_FOUND) {
                    check(status, "menghapus");
                }
            } finally {
                CoreFoundation.INSTANCE.CFRelease(query);
            }
        }

        private static Pointer query(String service, String account) {
            Pointer dict = CoreFoundation.INSTANCE.CFDictionaryCreateMutable(null, 0,
                    CF.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks"),
                    CF.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks"));
            CoreFoundation.INSTANCE.CFDictionarySetValue(dict, sec("kSecClass"), sec("kSecClassGenericPassword"));
            set(dict, sec("kSecAttrService"), string(service));
            set(dict, sec("kSecAttrAccount"), string(account));
            return dict;
        }

        /** Dictionary me-retain value-nya, jadi referensi kita dilepas setelah dimasukkan. */
        private static void set(Pointer dict, Pointer key, Pointer value) {
            CoreFoundation.INSTANCE.CFDictionarySetValue(dict, key, value);
            CoreFoundation.INSTANCE.CFRelease(value);
        }

        private static Pointer string(String s) {
            return CoreFoundation.INSTANCE.CFStringCreateWithCharacters(null, s.toCharArray(), s.length());
        }

        private static Pointer sec(String constant) {
            return SEC.getGlobalVariableAddress(constant).getPointer(0);
        }

        private static Pointer cf(String constant) {
            return CF.getGlobalVariableAddress(constant).getPointer(0);
        }

        /** Hapus salinan kunci di memori CFData sebelum dilepas (CFData milik kita, refcount 1). */
        private static void wipe(Pointer data) {
            long length = CoreFoundation.INSTANCE.CFDataGetLength(data);
            if (length > 0) {
                CoreFoundation.INSTANCE.CFDataGetBytePtr(data).clear(length);
            }
        }

        private static void check(int status, String action) {
            if (status != ERR_SEC_SUCCESS) {
                throw new VaultException("Gagal " + action + " item Keychain (OSStatus " + status + ")");
            }
        }
    }
}
