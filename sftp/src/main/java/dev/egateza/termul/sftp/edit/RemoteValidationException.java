package dev.egateza.termul.sftp.edit;

import dev.egateza.termul.sftp.RemoteFileException;

/**
 * Hook validasi gagal setelah file dipasang.
 *
 * @see SudoWriter
 */
public final class RemoteValidationException extends RemoteFileException {

    private final String command;
    private final String output;
    private final boolean rolledBack;

    public RemoteValidationException(String path, String command, String output, boolean rolledBack) {
        super(rolledBack
                ? "Validasi '" + command + "' gagal; " + path + " dikembalikan ke versi sebelumnya.\n\n" + output
                : "Validasi '" + command + "' gagal DAN rollback " + path + " gagal. Periksa file di server segera!\n\n"
                        + output);
        this.command = command;
        this.output = output;
        this.rolledBack = rolledBack;
    }

    public String command() {
        return command;
    }

    /** Output validator (stdout + stderr). */
    public String output() {
        return output;
    }

    /** true kalau file server sudah kembali ke isi sebelum upload. */
    public boolean rolledBack() {
        return rolledBack;
    }
}
