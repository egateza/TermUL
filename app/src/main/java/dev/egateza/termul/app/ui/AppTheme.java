package dev.egateza.termul.app.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import dev.egateza.termul.app.themes.FlatIllustrationLaf;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Tema UI yang bisa dipilih (menu Pengaturan → Tema). Tiap tema mendukung terang, gelap, atau keduanya;
 * tema yang hanya punya satu mode tetap dipakai apa adanya meski mode yang diminta berbeda.
 * Menambah tema: tambah konstanta dengan {@link FlatLaf} per mode yang didukung.
 */
public enum AppTheme {
    DEFAULT("default", "Default", FlatLightLaf::new, FlatDarkLaf::new),
    /** Hanya terang: tombol mode di menu bar nonaktif saat tema ini dipakai. */
    FLAT_ILLUSTRATION("flat-illustration", FlatIllustrationLaf.NAME, FlatIllustrationLaf::new, null);

    private final String id;
    private final String label;
    private final Supplier<FlatLaf> light; // null = tema tidak punya mode terang
    private final Supplier<FlatLaf> dark; // null = tema tidak punya mode gelap

    AppTheme(String id, String label, Supplier<FlatLaf> light, Supplier<FlatLaf> dark) {
        if (light == null && dark == null) {
            throw new IllegalArgumentException("Tema harus punya minimal satu mode");
        }
        this.id = id;
        this.label = label;
        this.light = light;
        this.dark = dark;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public Set<ThemeMode> modes() {
        var modes = EnumSet.noneOf(ThemeMode.class);
        if (light != null) {
            modes.add(ThemeMode.LIGHT);
        }
        if (dark != null) {
            modes.add(ThemeMode.DARK);
        }
        return modes;
    }

    public boolean supports(ThemeMode mode) {
        return modes().contains(mode);
    }

    /** @return mode yang benar-benar dipakai: {@code wanted} kalau didukung, kalau tidak mode satu-satunya tema ini */
    public ThemeMode effectiveMode(ThemeMode wanted) {
        return supports(wanted) ? wanted : wanted.other();
    }

    /** Pasang tema ini. Panggil di EDT; window yang sudah tampil perlu {@link FlatLaf#updateUI()}. */
    public boolean install(ThemeMode wanted) {
        var laf = effectiveMode(wanted) == ThemeMode.LIGHT ? light.get() : dark.get();
        return FlatLaf.setup(laf);
    }

    /** @return tema dengan id tersebut, atau {@link #DEFAULT} kalau kosong/tidak dikenal */
    public static AppTheme fromId(String id) {
        for (AppTheme t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return DEFAULT;
    }
}
