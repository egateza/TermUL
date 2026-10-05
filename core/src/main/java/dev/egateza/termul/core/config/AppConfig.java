package dev.egateza.termul.core.config;

/**
 * Preferensi aplikasi ({@code config.json}). Tidak berisi secret.
 *
 * @param editors           editor lokal per ekstensi
 * @param terminalFontSize  ukuran font terminal
 * @param iconSet           id set ikon UI (mis. {@code fontawesome}, {@code material}); diterjemahkan di modul app
 * @param hostButtonOpacity opasitas tombol melayang panel host, persen {@value #MIN_OPACITY}..100 (100 = solid)
 * @param hostPanelMode     {@value #HOST_DOCKED} = panel host di samping (tetap), {@value #HOST_FLOATING} = tombol
 *                          melayang yang menampilkan panel di atas terminal
 * @param theme             id tema UI (bawaan, mis. {@code default}, atau id tema custom); diterjemahkan di modul app
 * @param themeMode         {@value #MODE_LIGHT} atau {@value #MODE_DARK}; dipakai kalau tema mendukungnya
 * @param language          tag bahasa UI (mis. {@code id}, {@code en}); diterjemahkan di modul app
 * @param bellSound         bunyikan suara sistem saat terminal menerima BEL (null di config lama = aktif)
 * @param bellShake         getarkan layar saat terminal menerima BEL (null di config lama = aktif)
 * @param uiFontFamily      nama font aplikasi (menu, tabel, dialog); null = font bawaan tema
 * @param terminalFontFamily nama font terminal; null = pilihan otomatis (font monospace pertama yang terpasang)
 * @param terminalThemeLinked tema UI dan tema terminal satu paket: warna terminal mengikuti tema UI
 *                          (null di config lama = aktif)
 * @param terminalTheme     id tema custom untuk warna terminal, dipakai kalau {@code terminalThemeLinked} mati;
 *                          null = warna bawaan JediTerm
 * @param windowOpacity     opasitas seluruh window, persen {@value #MIN_WINDOW_OPACITY}..100 (100 = solid)
 * @param validationHooks   validasi per pola path untuk edit file root (sudo); null di config lama = bawaan
 * @param splitMode         arah panel di tab yang digabung (split): {@value #SPLIT_HORIZONTAL} = menyamping
 *                          (kiri-kanan, default), {@value #SPLIT_VERTICAL} = atas-bawah
 * @param hostStatusBar     tampilkan "Host status" (CPU/memory JVM) di kanan baris menu (null di config lama = tampil);
 *                          panel bawah (animasi + jam) selalu tampil
 * @param autoUpdateCheck   periksa rilis baru di GitHub di background dan tampilkan badge; memasang tetap manual
 *                          (null di config lama = aktif)
 * @param loadingAnimation  id animasi di layar "Menghubungkan…" (mis. {@code pacman}, {@code ssh}) atau
 *                          {@value #ANIMATION_OFF} / {@value #ANIMATION_RANDOM}; diterjemahkan di modul app (null di config lama =
 *                          {@value #DEFAULT_ANIMATION})
 * @param statusAnimation   id animasi kecil di panel bawah, aturannya sama dengan {@code loadingAnimation}
 * @param hostStatusMode    isi "Host status": {@value #HOST_STATUS_ALL} (grafik + teks), {@value #HOST_STATUS_GRAPH}
 *                          (grafik saja) atau {@value #HOST_STATUS_TEXT} (teks saja); null di config lama = semua
 * @param memoryMode        {@value #MEMORY_SAVER} = kembalikan memori yang tidak terpakai ke OS saat idle,
 *                          {@value #MEMORY_NORMAL} = perilaku bawaan JVM; null di config lama = hemat
 * @param hostToggleStyle   id gaya tombol buka/tutup panel host (mis. {@code edgeCircle}, {@code edgeTab});
 *                          diterjemahkan di modul app (null di config lama = {@value #DEFAULT_HOST_TOGGLE})
 * @param idleMinutes       layar idle menutupi window setelah sekian menit tanpa input; 0 = mati (null di config lama =
 *                          {@value #DEFAULT_IDLE_MINUTES})
 * @param idleAnimation     id animasi di layar idle dan layar beranda (tanpa tab), aturannya sama dengan
 *                          {@code loadingAnimation} (null di config lama = acak)
 */
public record AppConfig(EditorConfig editors, float terminalFontSize, String iconSet, int hostButtonOpacity,
                        String hostPanelMode, String theme, String themeMode, String language, Boolean bellSound,
                        Boolean bellShake, String uiFontFamily, String terminalFontFamily,
                        Boolean terminalThemeLinked, String terminalTheme, int windowOpacity,
                        ValidationHooks validationHooks, String splitMode, Boolean hostStatusBar,
                        Boolean autoUpdateCheck, String loadingAnimation, String statusAnimation,
                        String hostStatusMode, String memoryMode, String hostToggleStyle, Integer idleMinutes,
                        String idleAnimation) {

    public static final String HOST_DOCKED = "docked";
    public static final String HOST_FLOATING = "floating";

    public static final String SPLIT_HORIZONTAL = "horizontal";
    public static final String SPLIT_VERTICAL = "vertical";

    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";

    public static final String DEFAULT_LANGUAGE = "id";
    public static final String DEFAULT_THEME = "default";
    public static final String DEFAULT_ICON_SET = "fontawesome";
    public static final int MIN_OPACITY = 10;
    public static final int DEFAULT_OPACITY = 100;
    public static final int MIN_WINDOW_OPACITY = 30;
    public static final String DEFAULT_ANIMATION = "pacman";
    public static final String ANIMATION_OFF = "off";
    public static final String ANIMATION_RANDOM = "random";
    public static final String HOST_STATUS_ALL = "all";
    public static final String HOST_STATUS_GRAPH = "graph";
    public static final String HOST_STATUS_TEXT = "text";
    public static final String MEMORY_NORMAL = "normal";
    public static final String MEMORY_SAVER = "saver";
    public static final String DEFAULT_HOST_TOGGLE = "edgeCircle";
    public static final int DEFAULT_IDLE_MINUTES = 15;
    public static final int MAX_IDLE_MINUTES = 24 * 60;

    public AppConfig {
        if (editors == null) {
            editors = EditorConfig.defaults();
        }
        if (terminalFontSize < 6 || terminalFontSize > 48) {
            terminalFontSize = 14f;
        }
        if (iconSet == null || iconSet.isBlank()) {
            iconSet = DEFAULT_ICON_SET;
        }
        // config lama tanpa field ini terbaca 0 -> default
        if (hostButtonOpacity < MIN_OPACITY || hostButtonOpacity > 100) {
            hostButtonOpacity = DEFAULT_OPACITY;
        }
        if (!HOST_FLOATING.equals(hostPanelMode)) {
            hostPanelMode = HOST_DOCKED;
        }
        if (theme == null || theme.isBlank()) {
            theme = DEFAULT_THEME;
        }
        if (!MODE_LIGHT.equals(themeMode)) {
            themeMode = MODE_DARK; // tampilan sebelum ada pilihan tema
        }
        if (language == null || language.isBlank()) {
            language = DEFAULT_LANGUAGE; // config lama; UI sebelum ada pilihan bahasa berbahasa Indonesia
        }
        // Boolean (bukan boolean): field yang tidak ada di config lama terbaca null, bukan false
        bellSound = bellSound == null || bellSound;
        bellShake = bellShake == null || bellShake;
        uiFontFamily = uiFontFamily == null || uiFontFamily.isBlank() ? null : uiFontFamily.strip();
        terminalFontFamily = terminalFontFamily == null || terminalFontFamily.isBlank() ? null : terminalFontFamily.strip();
        terminalThemeLinked = terminalThemeLinked == null || terminalThemeLinked;
        terminalTheme = terminalTheme == null || terminalTheme.isBlank() ? null : terminalTheme.strip();
        // config lama tanpa field ini terbaca 0 -> solid
        if (windowOpacity < MIN_WINDOW_OPACITY || windowOpacity > 100) {
            windowOpacity = DEFAULT_OPACITY;
        }
        if (validationHooks == null) {
            validationHooks = ValidationHooks.defaults();
        }
        if (!SPLIT_VERTICAL.equals(splitMode)) {
            splitMode = SPLIT_HORIZONTAL; // config lama tanpa field ini: menyamping
        }
        hostStatusBar = hostStatusBar == null || hostStatusBar; // config lama: bar "Host status" tampil
        autoUpdateCheck = autoUpdateCheck == null || autoUpdateCheck; // config lama: aktif
        loadingAnimation = loadingAnimation == null || loadingAnimation.isBlank() ? DEFAULT_ANIMATION : loadingAnimation.strip();
        statusAnimation = statusAnimation == null || statusAnimation.isBlank() ? DEFAULT_ANIMATION : statusAnimation.strip();
        if (!HOST_STATUS_GRAPH.equals(hostStatusMode) && !HOST_STATUS_TEXT.equals(hostStatusMode)) {
            hostStatusMode = HOST_STATUS_ALL; // config lama / nilai tidak dikenal: grafik + teks
        }
        if (!MEMORY_NORMAL.equals(memoryMode)) {
            memoryMode = MEMORY_SAVER; // config lama / nilai tidak dikenal: hemat
        }
        hostToggleStyle = hostToggleStyle == null || hostToggleStyle.isBlank() ? DEFAULT_HOST_TOGGLE : hostToggleStyle.strip();
        idleMinutes = idleMinutes == null ? DEFAULT_IDLE_MINUTES : Math.clamp(idleMinutes, 0, MAX_IDLE_MINUTES);
        idleAnimation = idleAnimation == null || idleAnimation.isBlank() ? ANIMATION_RANDOM : idleAnimation.strip();
    }

    public static AppConfig defaults() {
        return new AppConfig(EditorConfig.defaults(), 14f, DEFAULT_ICON_SET, DEFAULT_OPACITY, HOST_DOCKED,
                DEFAULT_THEME, MODE_DARK, DEFAULT_LANGUAGE, true, true, null, null, true, null, DEFAULT_OPACITY, ValidationHooks.defaults(),
                SPLIT_HORIZONTAL, true, true, DEFAULT_ANIMATION, DEFAULT_ANIMATION, HOST_STATUS_ALL, MEMORY_SAVER, DEFAULT_HOST_TOGGLE, DEFAULT_IDLE_MINUTES, ANIMATION_RANDOM);
    }

    public AppConfig withEditors(EditorConfig e) {
        return new AppConfig(e, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    public AppConfig withIconSet(String id) {
        return new AppConfig(editors, terminalFontSize, id, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    public AppConfig withHostButtonOpacity(int percent) {
        return new AppConfig(editors, terminalFontSize, iconSet, percent, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    public AppConfig withHostPanelMode(String mode) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, mode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    public AppConfig withTheme(String id) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, id, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    public AppConfig withThemeMode(String mode) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, mode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    public AppConfig withLanguage(String tag) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                tag, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    public AppConfig withBellSound(boolean on) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, on, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    public AppConfig withBellShake(boolean on) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, on, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    /** @param family nama font aplikasi, atau null untuk font bawaan tema */
    public AppConfig withUiFontFamily(String family) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, family, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    /** @param family nama font terminal, atau null untuk pilihan otomatis */
    public AppConfig withTerminalFontFamily(String family) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, family, terminalThemeLinked, terminalTheme, windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    public AppConfig withTerminalThemeLinked(boolean linked) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, linked, terminalTheme, windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    /** @param id id tema custom untuk warna terminal, atau null untuk warna bawaan */
    public AppConfig withTerminalTheme(String id) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, id, windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    public AppConfig withWindowOpacity(int percent) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                percent, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    public AppConfig withValidationHooks(ValidationHooks hooks) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, hooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    /** @param mode {@value #SPLIT_HORIZONTAL} (menyamping) atau {@value #SPLIT_VERTICAL} (atas-bawah) */
    public AppConfig withSplitMode(String mode) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, validationHooks, mode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    public AppConfig withHostStatusBar(boolean on) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, validationHooks, splitMode, on, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    public AppConfig withAutoUpdateCheck(boolean on) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, validationHooks, splitMode, hostStatusBar, on, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    /** @param id id animasi layar "Menghubungkan…", atau {@value #ANIMATION_OFF} */
    public AppConfig withLoadingAnimation(String id) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, id, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    /** @param id id animasi panel bawah, atau {@value #ANIMATION_OFF} / {@value #ANIMATION_RANDOM} */
    public AppConfig withStatusAnimation(String id) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, id, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    /** @param mode {@value #HOST_STATUS_ALL}, {@value #HOST_STATUS_GRAPH} atau {@value #HOST_STATUS_TEXT} */
    public AppConfig withHostStatusMode(String mode) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, mode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    /** @param mode {@value #MEMORY_SAVER} atau {@value #MEMORY_NORMAL} */
    public AppConfig withMemoryMode(String mode) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, mode, hostToggleStyle, idleMinutes, idleAnimation);
    }

    /** @param id id gaya tombol panel host */
    public AppConfig withHostToggleStyle(String id) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, id, idleMinutes, idleAnimation);
    }

    /** @param minutes menit tanpa input sebelum layar idle tampil; 0 = mati */
    public AppConfig withIdleMinutes(int minutes) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, minutes, idleAnimation);
    }

    /** @param id id animasi layar idle, atau {@value #ANIMATION_OFF} / {@value #ANIMATION_RANDOM} */
    public AppConfig withIdleAnimation(String id) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, id);
    }
}
