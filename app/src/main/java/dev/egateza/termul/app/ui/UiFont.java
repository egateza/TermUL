package dev.egateza.termul.app.ui;

import com.formdev.flatlaf.util.FontUtils;
import java.awt.Font;
import javax.swing.UIManager;

/**
 * Font aplikasi (menu, tabel, dialog, dll.) lewat properti {@code defaultFont} milik FlatLaf. Harus diset sebelum
 * tema dipasang; untuk window yang sudah tampil, lanjutkan dengan {@code FlatLaf.updateUI()}.
 *
 * <p>Font pilihan dibuat sebagai font <em>komposit</em> (seperti font bawaan tema): karakter yang tidak ada di font itu
 * (☀ ☾ ✔ ✖ ▲ ● ↵ ⌘ │ █ ...) diambil dari font sistem lain. Font fisik biasa ({@code new Font(family)}) tidak punya
 * fallback, sehingga simbol-simbol itu tampil sebagai kotak kosong.
 */
public final class UiFont {

    /** Ukuran dasar FlatLaf di Windows; FlatLaf sendiri yang menskalakannya untuk layar HiDPI. */
    private static final int SIZE = 12;

    private UiFont() {
    }

    /** @param family nama font, atau null/kosong/tidak terpasang untuk kembali ke font bawaan tema. EDT. */
    public static void apply(String family) {
        boolean usable = family != null && !family.isBlank() && FontCatalog.isInstalled(family)
                && FontCatalog.isReadable(family); // font simbol membuat menu tak terbaca
        UIManager.put("defaultFont", usable ? font(family) : null);
    }

    /** Font komposit {@code family} ukuran dasar (dengan fallback untuk simbol yang tidak ada di family itu). */
    static Font font(String family) {
        return FontUtils.getCompositeFont(family, Font.PLAIN, SIZE);
    }
}
