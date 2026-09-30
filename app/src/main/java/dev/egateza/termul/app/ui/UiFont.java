package dev.egateza.termul.app.ui;

import java.awt.Font;
import javax.swing.UIManager;

/**
 * Font aplikasi (menu, tabel, dialog, dll.) lewat properti {@code defaultFont} milik FlatLaf. Harus diset sebelum
 * tema dipasang; untuk window yang sudah tampil, lanjutkan dengan {@code FlatLaf.updateUI()}.
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
        UIManager.put("defaultFont", usable ? new Font(family, Font.PLAIN, SIZE) : null);
    }
}
