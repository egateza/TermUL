package dev.egateza.termul.app.terminal;

import dev.egateza.termul.core.theme.CustomTheme.TerminalPalette;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Memuat dan menyesuaikan gambar latar terminal. */
public final class BackgroundImages {

    /** Sisi terpanjang gambar yang disimpan di memori; gambar lebih besar diperkecil saat dimuat. */
    static final int MAX_SIDE = 2560;

    /** Radius blur maksimum (blur 100%), sebagai bagian dari sisi terpendek area. */
    static final double MAX_BLUR_FRACTION = 0.05;

    /** Kunci {@link #key} untuk "wallpaper desktop"; bukan path sah di OS mana pun. */
    static final String WALLPAPER_KEY = "desktop-wallpaper:";

    private BackgroundImages() {
    }

    /**
     * Identitas gambar latar yang diminta palet, untuk menentukan perlu tidaknya memuat ulang.
     *
     * @return path file, {@link #WALLPAPER_KEY} untuk wallpaper desktop, atau null kalau tanpa gambar latar
     */
    public static String key(TerminalPalette palette) {
        if (palette.desktopWallpaper()) {
            return WALLPAPER_KEY;
        }
        return palette.backgroundImage();
    }

    /**
     * Muat gambar latar yang diminta palet, dari file atau dari wallpaper desktop (blocking: jangan panggil di EDT).
     *
     * @return gambar, atau null kalau tidak ada yang diminta atau tidak bisa dimuat
     */
    public static BufferedImage loadFor(TerminalPalette palette) {
        if (palette.desktopWallpaper()) {
            return DesktopWallpaper.currentPath().map(BackgroundImages::load).orElse(null);
        }
        return load(palette.backgroundImage());
    }

    /**
     * Baca gambar dari disk (blocking: jangan panggil di EDT).
     *
     * @return gambar, atau null kalau file tidak ada, bukan gambar yang dikenal, atau tidak terbaca
     */
    public static BufferedImage load(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            Path file = Path.of(path);
            if (!Files.isRegularFile(file)) {
                return null;
            }
            BufferedImage image = ImageIO.read(file.toFile());
            return image == null ? null : shrink(image);
        } catch (IOException | RuntimeException e) { // RuntimeException: path tidak valid, gambar rusak
            return null;
        }
    }

    /** @return {@code image} apa adanya kalau sisi terpanjangnya {@link #MAX_SIDE} atau kurang */
    static BufferedImage shrink(BufferedImage image) {
        int longest = Math.max(image.getWidth(), image.getHeight());
        if (longest <= MAX_SIDE) {
            return image;
        }
        double factor = (double) MAX_SIDE / longest;
        return scaleTo(image, (int) Math.max(1, Math.round(image.getWidth() * factor)),
                (int) Math.max(1, Math.round(image.getHeight() * factor)), 0, 0, image.getWidth(), image.getHeight());
    }

    /**
     * Isi penuh area {@code width}x{@code height}: gambar diperbesar/diperkecil tanpa mengubah proporsi, lalu bagian
     * yang berlebih di tepi dipotong (pusat gambar tetap terlihat).
     */
    public static BufferedImage cover(BufferedImage image, int width, int height) {
        Rectangle src = coverSource(image.getWidth(), image.getHeight(), width, height);
        return scaleTo(image, width, height, src.x, src.y, src.width, src.height);
    }

    /**
     * Seperti {@link #cover(BufferedImage, int, int)}, lalu di-blur. Kekuatan blur relatif terhadap ukuran area (bukan
     * resolusi gambar), jadi tampak sama di panel kecil maupun besar.
     *
     * @param blur persen 0..100; 100 = radius {@link #MAX_BLUR_FRACTION} dari sisi terpendek area
     */
    public static BufferedImage cover(BufferedImage image, int width, int height, int blur) {
        int radius = blurRadius(width, height, blur);
        if (radius <= 0) {
            return cover(image, width, height);
        }
        // Blur di ukuran kecil lalu diperbesar: hasilnya tetap halus (isinya memang kabur) dan jauh lebih cepat,
        // penting karena dibuat ulang setiap ukuran panel berubah.
        int factor = Math.max(1, radius / 4);
        int smallW = Math.max(1, width / factor);
        int smallH = Math.max(1, height / factor);
        BufferedImage small = cover(image, smallW, smallH);
        boxBlur(small, Math.max(1, radius / factor));
        return factor == 1 ? small : scaleTo(small, width, height, 0, 0, smallW, smallH);
    }

    /** @return radius blur dalam piksel area tujuan; 0 = tanpa blur */
    static int blurRadius(int width, int height, int blur) {
        if (blur <= 0 || width <= 0 || height <= 0) {
            return 0;
        }
        return (int) Math.round(Math.min(width, height) * MAX_BLUR_FRACTION * Math.min(100, blur) / 100.0);
    }

    /**
     * Blur di tempat: tiga putaran box blur horizontal + vertikal (mendekati blur Gaussian). Waktunya tidak bergantung
     * pada radius. Tepi gambar diperpanjang (piksel tepi diulang), jadi pinggiran tidak menggelap.
     */
    static void boxBlur(BufferedImage image, int radius) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] a = image.getRGB(0, 0, w, h, null, 0, w);
        int[] b = new int[a.length];
        for (int pass = 0; pass < 3; pass++) {
            blurLine(a, b, w, h, 1, w, radius); // horizontal: a -> b
            blurLine(b, a, h, w, w, 1, radius); // vertikal: b -> a
        }
        image.setRGB(0, 0, w, h, a, 0, w);
    }

    /**
     * Satu arah box blur untuk {@code lines} baris sepanjang {@code length}. {@code step} = jarak antar-piksel dalam
     * baris, {@code lineStep} = jarak antar-baris (dalam indeks array).
     */
    private static void blurLine(int[] src, int[] dst, int length, int lines, int step, int lineStep, int radius) {
        int window = radius * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int start = line * lineStep;
            int r = 0;
            int g = 0;
            int bl = 0;
            for (int i = -radius; i <= radius; i++) {
                int p = src[start + Math.clamp(i, 0, length - 1) * step];
                r += (p >> 16) & 0xFF;
                g += (p >> 8) & 0xFF;
                bl += p & 0xFF;
            }
            for (int i = 0; i < length; i++) {
                dst[start + i * step] = 0xFF00_0000 | (r / window) << 16 | (g / window) << 8 | (bl / window);
                int out = src[start + Math.max(i - radius, 0) * step];
                int in = src[start + Math.min(i + radius + 1, length - 1) * step];
                r += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
                g += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                bl += (in & 0xFF) - (out & 0xFF);
            }
        }
    }

    /** @return bagian gambar sumber (di tengah) yang proporsinya sama dengan area tujuan */
    static Rectangle coverSource(int imageW, int imageH, int width, int height) {
        // bandingkan proporsi dengan perkalian silang supaya tidak ada pembulatan
        if ((long) imageW * height > (long) imageH * width) {
            int w = (int) Math.max(1, Math.round((double) imageH * width / height)); // gambar lebih lebar: potong kiri-kanan
            return new Rectangle((imageW - w) / 2, 0, w, imageH);
        }
        int h = (int) Math.max(1, Math.round((double) imageW * height / width)); // gambar lebih tinggi: potong atas-bawah
        return new Rectangle(0, (imageH - h) / 2, imageW, h);
    }

    private static BufferedImage scaleTo(BufferedImage image, int width, int height, int sx, int sy, int sw, int sh) {
        var out = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_RGB);
        var g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, out.getWidth(), out.getHeight(), sx, sy, sx + sw, sy + sh, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
