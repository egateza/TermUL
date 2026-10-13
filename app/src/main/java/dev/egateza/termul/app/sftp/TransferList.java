package dev.egateza.termul.app.sftp;

import java.util.ArrayList;
import java.util.List;

/**
 * Daftar transfer yang sedang berjalan dan antre, untuk ditampilkan (badge + daftar progres). Urutan = urutan
 * antrean; transfer yang selesai, gagal, atau dibatalkan dihapus. Hanya diakses di EDT.
 */
final class TransferList {

    /**
     * @param done  byte yang sudah terkirim
     * @param total ukuran total; 0 = belum diketahui
     */
    record Item(long id, String title, boolean running, long done, long total) {

        /** @return 0..100, atau -1 kalau total belum diketahui */
        int percent() {
            return total <= 0 ? -1 : (int) Math.min(100, done * 100 / total);
        }
    }

    private final List<Item> items = new ArrayList<>();

    void add(long id, String title) {
        items.add(new Item(id, title, false, 0, 0));
    }

    void start(long id) {
        replace(id, i -> new Item(i.id(), i.title(), true, 0, 0));
    }

    void progress(long id, long done, long total) {
        replace(id, i -> new Item(i.id(), i.title(), true, done, total));
    }

    void remove(long id) {
        items.removeIf(i -> i.id() == id);
    }

    List<Item> items() {
        return List.copyOf(items);
    }

    int size() {
        return items.size();
    }

    private void replace(long id, java.util.function.UnaryOperator<Item> change) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id() == id) {
                items.set(i, change.apply(items.get(i)));
                return;
            }
        }
    }
}
