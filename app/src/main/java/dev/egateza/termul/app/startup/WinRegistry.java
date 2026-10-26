package dev.egateza.termul.app.startup;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinReg;
import java.io.IOException;

/** Akses minimal ke {@code HKEY_CURRENT_USER} (tanpa admin). Dipisah supaya {@link WindowsAutoStart} bisa dites. */
public interface WinRegistry {

    /** @return null kalau key/value tidak ada */
    String readString(String key, String name) throws IOException;

    /** @return null kalau key/value tidak ada */
    byte[] readBinary(String key, String name) throws IOException;

    /** Key dibuat kalau belum ada. {@code name} kosong = value default key. */
    void writeString(String key, String name, String value) throws IOException;

    /** Tidak apa-apa kalau value tidak ada. */
    void delete(String key, String name) throws IOException;

    /** Hapus key beserta semua subkey-nya; tidak apa-apa kalau key tidak ada. */
    void deleteTree(String key) throws IOException;

    /** Implementasi Win32 lewat JNA; hanya dipanggil di Windows. */
    static WinRegistry jna() {
        return new WinRegistry() {
            private static final WinReg.HKEY HKCU = WinReg.HKEY_CURRENT_USER;

            @Override
            public String readString(String key, String name) throws IOException {
                try {
                    return exists(key, name) ? Advapi32Util.registryGetStringValue(HKCU, key, name) : null;
                } catch (Win32Exception e) {
                    throw new IOException("Registry " + key + " tidak bisa dibaca: " + e.getMessage(), e);
                }
            }

            @Override
            public byte[] readBinary(String key, String name) throws IOException {
                try {
                    return exists(key, name) ? Advapi32Util.registryGetBinaryValue(HKCU, key, name) : null;
                } catch (Win32Exception e) {
                    throw new IOException("Registry " + key + " tidak bisa dibaca: " + e.getMessage(), e);
                }
            }

            @Override
            public void writeString(String key, String name, String value) throws IOException {
                try {
                    if (!Advapi32Util.registryKeyExists(HKCU, key)) {
                        Advapi32Util.registryCreateKey(HKCU, key);
                    }
                    Advapi32Util.registrySetStringValue(HKCU, key, name, value);
                } catch (Win32Exception e) {
                    throw new IOException("Registry " + key + " tidak bisa ditulis: " + e.getMessage(), e);
                }
            }

            @Override
            public void delete(String key, String name) throws IOException {
                try {
                    if (exists(key, name)) {
                        Advapi32Util.registryDeleteValue(HKCU, key, name);
                    }
                } catch (Win32Exception e) {
                    throw new IOException("Registry " + key + " tidak bisa diubah: " + e.getMessage(), e);
                }
            }

            @Override
            public void deleteTree(String key) throws IOException {
                try {
                    if (Advapi32Util.registryKeyExists(HKCU, key)) {
                        deleteRecursive(key);
                    }
                } catch (Win32Exception e) {
                    throw new IOException("Registry " + key + " tidak bisa dihapus: " + e.getMessage(), e);
                }
            }

            private static void deleteRecursive(String key) {
                for (String child : Advapi32Util.registryGetKeys(HKCU, key)) {
                    deleteRecursive(key + "\\" + child);
                }
                Advapi32Util.registryDeleteKey(HKCU, key);
            }

            private static boolean exists(String key, String name) {
                return Advapi32Util.registryKeyExists(HKCU, key) && Advapi32Util.registryValueExists(HKCU, key, name);
            }
        };
    }
}
