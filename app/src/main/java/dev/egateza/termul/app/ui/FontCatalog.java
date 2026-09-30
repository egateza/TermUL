package dev.egateza.termul.app.ui;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Daftar font terpasang. Menyaring font monospace (lebar 'i' = 'W') butuh memuat ratusan font, jadi dihitung
 * sekali di thread lain lewat {@link #preload()}; {@link #monospaced()} menunggu hasilnya kalau belum selesai.
 */
public final class FontCatalog {

    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static final CompletableFuture<List<String>> MONOSPACED = new CompletableFuture<>();
    private static final CompletableFuture<List<String>> READABLE = new CompletableFuture<>();
    private static final String SAMPLE = "Abcdefghijklmnopqrstuvwxyz ABCDEFGHIJ 0123456789";
    private static volatile boolean started;

    private FontCatalog() {
    }

    /** Mulai menghitung daftar font monospace di virtual thread; aman dipanggil berulang kali. */
    public static void preload() {
        if (started) {
            return;
        }
        synchronized (MONOSPACED) {
            if (started) {
                return;
            }
            started = true;
        }
        Thread.startVirtualThread(() -> {
            try {
                MONOSPACED.complete(Arrays.stream(allFamilies()).filter(FontCatalog::isMonospaced).toList());
            } catch (RuntimeException e) {
                MONOSPACED.complete(List.of());
            }
            try {
                READABLE.complete(Arrays.stream(allFamilies()).filter(FontCatalog::isReadable).toList());
            } catch (RuntimeException e) {
                READABLE.complete(List.of());
            }
        });
    }

    /** Semua family font terpasang, urut abjad. */
    public static String[] allFamilies() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
    }

    /** Family monospace terpasang. Menunggu {@link #preload()} selesai (dimulai sendiri kalau belum). */
    public static List<String> monospaced() {
        preload();
        return MONOSPACED.join();
    }

    /** Family yang bisa menampilkan teks Latin biasa (font simbol seperti Webdings dibuang). Menunggu {@link #preload()}. */
    public static List<String> readable() {
        preload();
        return READABLE.join();
    }

    /** @return true kalau family terpasang di sistem (bukan fallback Dialog milik Java) */
    public static boolean isInstalled(String family) {
        return family != null && Set.of(allFamilies()).contains(family);
    }

    /** @return true kalau family bisa menampilkan huruf dan angka Latin (layak untuk teks menu) */
    public static boolean isReadable(String family) {
        return new Font(family, Font.PLAIN, 12).canDisplayUpTo(SAMPLE) == -1;
    }

    static boolean isMonospaced(String family) {
        var font = new Font(family, Font.PLAIN, 12);
        if (!font.canDisplay('i') || !font.canDisplay('W')) {
            return false;
        }
        double narrow = font.getStringBounds("i", FRC).getWidth();
        double wide = font.getStringBounds("W", FRC).getWidth();
        return narrow > 0 && Math.abs(narrow - wide) < 0.01;
    }
}
