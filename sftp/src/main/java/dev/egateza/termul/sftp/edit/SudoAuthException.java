package dev.egateza.termul.sftp.edit;

import dev.egateza.termul.sftp.RemoteFileException;

/** sudo menolak: password salah, password dibutuhkan tapi tidak ada, atau user tidak punya hak sudo. */
public final class SudoAuthException extends RemoteFileException {

    public SudoAuthException(String message) {
        super(message);
    }
}
