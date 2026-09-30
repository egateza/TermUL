package dev.egateza.termul.sftp.edit;

import dev.egateza.termul.sftp.RemoteEntry;
import dev.egateza.termul.sftp.RemoteFileException;

/** File remote berubah sejak dibuka (mtime/size beda dari baseline). */
public final class RemoteConflictException extends RemoteFileException {

    private final RemoteEntry current;

    public RemoteConflictException(String path, RemoteEntry current) {
        super("File " + path + " sudah diubah di server sejak dibuka.");
        this.current = current;
    }

    public RemoteEntry current() {
        return current;
    }
}
