package dev.egateza.termul.app.ui.anim;

import java.awt.Graphics2D;

/**
 * Satu animasi kecil (loading / status bar). State berjalan per frame lewat {@link #step()} dan digambar di
 * {@link #paint}; keduanya dipanggil di EDT oleh {@link AnimationView}. Tidak ada I/O.
 */
public interface Animation {

    /** Maju satu frame ({@value AnimationView#FRAME_MS} ms). */
    void step();

    /**
     * Gambar frame saat ini di area {@code w}×{@code h} px dengan titik (0,0) di kiri atas. {@code g} adalah salinan
     * (boleh diubah) dan sudah antialiasing.
     */
    void paint(Graphics2D g, int w, int h, Palette c);

    /** Ukuran area gambar. */
    enum Size {
        /** Layar "Menghubungkan…" di tab. */
        LARGE(264, 44),
        /** Bar "Host status"; tinggi sama dengan grafik CPU/heap. */
        COMPACT(200, 18);

        private final int width;
        private final int height;

        Size(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        boolean large() {
            return this == LARGE;
        }
    }
}
