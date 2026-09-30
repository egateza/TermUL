package dev.egateza.myterm.sftp;

/** Progress + pembatalan transfer. Dipanggil dari thread transfer (UI harus invokeLater sendiri). */
public interface TransferListener {

    /** @param total total byte, atau -1 kalau tidak diketahui */
    void progress(long transferred, long total);

    /** Dicek setiap chunk; true = batalkan transfer. */
    default boolean isCancelled() {
        return Thread.currentThread().isInterrupted();
    }

    TransferListener NONE = (done, total) -> { };
}
