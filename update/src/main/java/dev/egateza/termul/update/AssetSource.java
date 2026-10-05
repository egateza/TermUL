package dev.egateza.termul.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.LongConsumer;

/** Sumber jar sebuah rilis (GitHub Releases di produksi, server lokal di test). */
public interface AssetSource {

    /**
     * Unduh satu jar ke {@code target}. Hash belum tentu diverifikasi di sini; pemanggil wajib memverifikasi.
     *
     * @param progress dipanggil dengan jumlah byte yang baru diterima
     * @throws InterruptedException kalau thread di-interrupt (user membatalkan)
     */
    void download(ReleaseVersion version, UpdateManifest.FileEntry entry, Path target, LongConsumer progress)
            throws IOException, UpdateException, InterruptedException;
}
