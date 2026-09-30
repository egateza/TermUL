package dev.egateza.myterm.core.profile;

/** Gagal membaca atau menulis file profil. */
public class ProfileStoreException extends RuntimeException {

    public ProfileStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
