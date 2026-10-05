package dev.egateza.termul.ssh;

/** Connect dibatalkan lewat {@link ConnectCancel} (bukan kegagalan jaringan/autentikasi). */
public final class SshConnectCancelledException extends SshConnectException {

    public SshConnectCancelledException(String address) {
        super("Koneksi ke " + address + " dibatalkan.");
    }
}
