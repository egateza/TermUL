package dev.egateza.myterm.ssh;

/** Koneksi SSH gagal. Pesan ditujukan ke user (Bahasa Indonesia) dan tidak pernah berisi secret. */
public class SshConnectException extends Exception {

    public SshConnectException(String message) {
        super(message);
    }

    public SshConnectException(String message, Throwable cause) {
        super(message, cause);
    }
}
