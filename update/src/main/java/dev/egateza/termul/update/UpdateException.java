package dev.egateza.termul.update;

/** Gagal memeriksa, mengunduh, memverifikasi, atau memasang update. Pesan untuk user (Bahasa Indonesia). */
public class UpdateException extends Exception {

    public UpdateException(String message) {
        super(message);
    }

    public UpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
