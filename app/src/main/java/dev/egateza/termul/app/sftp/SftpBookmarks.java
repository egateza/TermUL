package dev.egateza.termul.app.sftp;

import dev.egateza.termul.app.i18n.I18n;
import dev.egateza.termul.app.ui.Dialogs;
import dev.egateza.termul.app.ui.UiAsync;
import dev.egateza.termul.core.bookmark.BookmarkStore;
import dev.egateza.termul.core.bookmark.BookmarkStore.Side;
import java.awt.Component;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Bookmark folder untuk panel file (remote & lokal): baca dari {@link BookmarkStore} di EDT (data di memori), tulis ke
 * disk di executor I/O. Dipakai oleh dialog "Buka folder" ({@link BookmarkDialog}) dan Ctrl/Cmd+B.
 */
public final class SftpBookmarks {

    private final BookmarkStore store;
    private final ExecutorService io;

    public SftpBookmarks(BookmarkStore store, ExecutorService io) {
        this.store = Objects.requireNonNull(store, "store");
        this.io = Objects.requireNonNull(io, "io");
    }

    /** @param host id profil, atau null untuk bookmark bersama */
    List<String> list(Side side, UUID host) {
        return store.list(side, host);
    }

    /** Simpan daftar baru di background; gagal = dialog error (daftar di memori tidak berubah). EDT. */
    void set(Component parent, Side side, UUID host, List<String> paths) {
        var copy = List.copyOf(paths);
        UiAsync.run(io, () -> store.set(side, host, copy),
                err -> Dialogs.error(parent, I18n.t("sftp.bookmark.saveFailed"), err));
    }

    /**
     * Tambah path ke akhir daftar di background. EDT.
     *
     * @param done dipanggil di EDT: true = ditambahkan, false = sudah ada
     */
    void add(Component parent, Side side, UUID host, String path, Consumer<Boolean> done) {
        UiAsync.run(io, () -> store.add(side, host, path), done,
                err -> Dialogs.error(parent, I18n.t("sftp.bookmark.saveFailed"), err));
    }
}
