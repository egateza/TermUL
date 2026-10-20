package dev.egateza.termul.localterm;

/** Shell lokal tidak bisa dijalankan (executable tidak ada, library native PTY gagal dimuat, dll.). */
public final class LocalShellException extends Exception {

    public LocalShellException(String message, Throwable cause) {
        super(message, cause);
    }
}
