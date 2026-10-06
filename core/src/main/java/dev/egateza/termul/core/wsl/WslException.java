package dev.egateza.termul.core.wsl;

/** Perintah {@code wsl.exe} gagal (distro tidak bisa dijalankan/dihentikan, timeout, dll.). Pesan untuk user. */
public class WslException extends Exception {

    public WslException(String message) {
        super(message);
    }

    public WslException(String message, Throwable cause) {
        super(message, cause);
    }
}
