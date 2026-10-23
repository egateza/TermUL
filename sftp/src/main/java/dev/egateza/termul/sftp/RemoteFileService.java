package dev.egateza.termul.sftp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Operasi file remote yang dipakai panel file, transfer, dan remote edit, terlepas dari protokolnya:
 * {@link SftpFileService} (SFTP di atas sesi SSH) atau {@link dev.egateza.termul.sftp.ftp.FtpFileService} (FTP/FTPS).
 *
 * <p>Semua method blocking (network): panggil dari executor, jangan di EDT. Implementasi boleh dipakai beberapa thread.
 * Path selalu POSIX absolut ({@link RemotePaths}).
 */
public interface RemoteFileService extends AutoCloseable {

    /** true kalau koneksinya masih hidup. */
    boolean isOpen();

    /**
     * Apakah file ini bisa ditimpa user login tanpa sudo. Kalau tidak bisa dipastikan, dianggap bisa: upload biasa
     * yang akan melaporkan errornya.
     */
    boolean canWrite(String path);

    /** Direktori home user / direktori awal login (path absolut). */
    String home() throws RemoteFileException;

    String canonicalize(String path) throws RemoteFileException;

    /** Isi direktori (tanpa {@code .} dan {@code ..}), direktori dulu lalu nama. */
    List<RemoteEntry> list(String dir) throws RemoteFileException;

    /**
     * Isi file teks kecil (UTF-8), dipotong di {@code maxBytes}.
     *
     * @return isi file, atau null kalau file tidak ada/tidak bisa dibaca
     */
    String readSmallText(String path, int maxBytes);

    /** Stat (mengikuti symlink kalau protokolnya bisa). */
    RemoteEntry stat(String path) throws RemoteFileException;

    boolean exists(String path) throws RemoteFileException;

    void mkdir(String path) throws RemoteFileException;

    /** Rename/move; gagal kalau tujuan sudah ada. */
    void rename(String from, String to) throws RemoteFileException;

    /** Hapus file, symlink, atau direktori (rekursif kalau {@code recursive}). */
    void delete(String path, boolean recursive) throws RemoteFileException;

    /** Mengubah permission ({@code mode} mis. {@code 0640}). */
    void chmod(String path, int mode) throws RemoteFileException;

    /** Download ke file lokal (ditulis ke temp lalu di-rename; file lokal lama tidak rusak kalau gagal/batal). */
    void download(String remote, Path local, TransferListener listener) throws RemoteFileException;

    /**
     * Upload: tulis ke file temp di direktori tujuan, set mode, lalu rename ke tujuan. Mode file lama dipertahankan;
     * untuk file baru dipakai {@code newFileMode} (atau default server kalau null).
     */
    void upload(Path local, String remoteOrLink, Integer newFileMode, TransferListener listener)
            throws RemoteFileException;

    /**
     * Salin file dari server lain (atau server yang sama, protokol apa pun) ke {@code targetOrLink} di server ini,
     * langsung dari stream ke stream tanpa salinan di disk lokal. Aturan tulisnya sama dengan {@link #upload}.
     */
    void copyFrom(RemoteFileService source, String sourcePath, String targetOrLink, TransferListener listener)
            throws RemoteFileException;

    /** Stream baca file remote (kanal transfer); pemanggil wajib menutupnya. */
    InputStream openRead(String remote) throws IOException;

    @Override
    void close();
}
