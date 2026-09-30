package dev.egateza.myterm.sftp;

import java.io.IOException;

/** Operasi file remote gagal. Pesan untuk user (Bahasa Indonesia). */
public class RemoteFileException extends IOException {

    public RemoteFileException(String message) {
        super(message);
    }

    public RemoteFileException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Transfer dibatalkan user. */
    public static final class Cancelled extends RemoteFileException {
        public Cancelled() {
            super("Transfer dibatalkan.");
        }
    }
}
