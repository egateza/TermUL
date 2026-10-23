package dev.egateza.termul.sftp.ftp;

import dev.egateza.termul.sftp.RemoteFileException;

/** Koneksi FTPS ditolak karena sertifikat server tidak diterima (berubah, atau tidak dipercaya user). */
public final class FtpsCertRejectedException extends RemoteFileException {

    public FtpsCertRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
