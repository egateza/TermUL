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
