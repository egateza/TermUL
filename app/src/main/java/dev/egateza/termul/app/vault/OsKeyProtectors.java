package dev.egateza.termul.app.vault;

import dev.egateza.termul.app.startup.AutoStart;
import dev.egateza.termul.core.Os;
import dev.egateza.termul.vault.DpapiKeyProtector;
import dev.egateza.termul.vault.KeyProtector;
import dev.egateza.termul.vault.KeychainKeyProtector;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Function;

/**
 * Memilih proteksi key OS untuk "Ingat di PC/Mac ini" (ADR 0006): DPAPI di Windows, Keychain di macOS. Di macOS hanya
 * untuk TermUL hasil instalasi ({@code .app}): dari JAR portable yang meminta akses Keychain adalah {@code java}, jadi
 * izin "Always Allow" berlaku untuk semua program Java milik user.
 */
public final class OsKeyProtectors {

    private OsKeyProtectors() {
    }

    public static KeyProtector forCurrentOs(Path vaultFile) {
        return choose(Os.current(), System::getProperty, vaultFile);
    }

    static KeyProtector choose(Os os, Function<String, String> props, Path vaultFile) {
        return switch (os) {
            case WINDOWS -> new DpapiKeyProtector();
            case MAC -> props.apply(AutoStart.JPACKAGE_APP_PATH) == null ? KeyProtector.UNAVAILABLE
                    : new KeychainKeyProtector(account(vaultFile));
            case OTHER -> KeyProtector.UNAVAILABLE;
        };
    }

    /** Nama item Keychain, unik per lokasi vault supaya dua folder data tidak saling menimpa kuncinya. */
    static String account(Path vaultFile) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    vaultFile.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
            return "vault-" + HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
