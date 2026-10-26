package dev.egateza.termul.core.config;

import dev.egateza.termul.core.Os;

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
 * @param windowOpacity     opasitas bagian window selain terminal (title bar, tab, panel, status bar; teks ikut pudar),
 *                          persen {@value #MIN_WINDOW_OPACITY}..100 (100 = solid)
 * @param terminalOpacity   opasitas latar area terminal (teks terminal tetap solid), persen
 *                          {@value #MIN_TERMINAL_OPACITY}..100 (100 = solid; 0 di config lama = solid)
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
 * @param panelCorners      bentuk sudut panel host, area terminal, dan panel log: {@value #PANEL_ROUNDED} atau
 *                          {@value #PANEL_SQUARE} (null di config lama / nilai tidak dikenal = membulat)
 * @param tabStyle          gaya tab terminal: {@value #TAB_CARD} (kotak per tab, ala VS Code) atau
 *                          {@value #TAB_UNDERLINED} (garis bawah); null di config lama / nilai tidak dikenal = kartu
 * @param rightClick        klik kanan di terminal: {@value #RIGHT_CLICK_COPY_PASTE} (gaya Windows: copy selection, atau
 *                          paste kalau tidak ada selection) atau {@value #RIGHT_CLICK_MENU} (menu konteks); null di
 *                          config lama / nilai tidak dikenal = {@link #defaultRightClick(Os)}
 * @param wslManager        manajemen WSL (start/stop distro, grup WSL di panel host); hanya berlaku di Windows
 *                          (null di config lama = mati)
 * @param snippetButton     tombol snippet mengambang di atas terminal (null di config lama = tampil)
 * @param snippetCorner     sudut tombol snippet: {@link #SNIPPET_TOP_RIGHT} (default) dan lainnya
 * @param streak            api streak di pojok kiri panel bawah dan animasinya saat aplikasi dibuka; hari tetap dihitung
 *                          walau disembunyikan (null di config lama = tampil)
 * @param sftpLayout        tampilan panel SFTP: {@value #SFTP_SINGLE} (remote saja) atau {@value #SFTP_DUAL} (lokal |
 *                          remote, ala WinSCP); null di config lama / nilai tidak dikenal = satu panel
 * @param cursorShape       bentuk kursor teks terminal: {@value #CURSOR_BLOCK}, {@value #CURSOR_UNDERLINE} atau
 *                          {@value #CURSOR_BAR}; program di server (mis. vim) tetap boleh menggantinya sementara
 *                          (null di config lama / nilai tidak dikenal = blok)
 * @param cursorBlink       kursor terminal berkedip (null di config lama = berkedip)
 * @param tray              ikon tray dan perilaku window terhadapnya (null di config lama = semua mati)
 * @param streakWidget      widget streak mengambang (null di config lama = tidak tampil)
 * @param sftpHiddenColumns id kolom tabel SFTP (remote & lokal) yang disembunyikan, mis. {@code owner}, {@code group};
 *                          kolom nama selalu tampil (null di config lama = semua tampil)
 * @param sftpFollowTerminal panel SFTP di samping terminal ikut pindah ke folder kerja shell ("Ikuti folder terminal";
 *                          null di config lama = mati)
 * @param collapsedHostGroups grup di panel host yang ditutup user, mis. {@code group:Produksi}, {@code builtin:favorites};
 *                          grup lain terbuka (null di config lama = semua terbuka)
 */
public record AppConfig(EditorConfig editors, float terminalFontSize, String iconSet, int hostButtonOpacity,
                        String hostPanelMode, String theme, String themeMode, String language, Boolean bellSound,
                        Boolean bellShake, String uiFontFamily, String terminalFontFamily,
                        Boolean terminalThemeLinked, String terminalTheme, int windowOpacity, int terminalOpacity,
                        ValidationHooks validationHooks, String splitMode, Boolean hostStatusBar,
                        Boolean autoUpdateCheck, String loadingAnimation, String statusAnimation,
                        String hostStatusMode, String memoryMode, String hostToggleStyle, Integer idleMinutes,
                        String idleAnimation, String panelCorners,
                        String tabStyle, String rightClick, Boolean wslManager,
                        Boolean snippetButton, String snippetCorner, Boolean streak,
                        String sftpLayout, String cursorShape, Boolean cursorBlink, TrayConfig tray,
                        StreakWidgetConfig streakWidget, java.util.List<String> sftpHiddenColumns,
                        Boolean sftpFollowTerminal, java.util.List<String> collapsedHostGroups) {

    /** Sudut tombol snippet di atas terminal (lihat {@link #snippetCorner}). */
    public static final String SNIPPET_TOP_LEFT = "top-left";
    public static final String SNIPPET_TOP_RIGHT = "top-right";
    public static final String SNIPPET_BOTTOM_LEFT = "bottom-left";
    public static final String SNIPPET_BOTTOM_RIGHT = "bottom-right";
    public static final java.util.List<String> SNIPPET_CORNERS = java.util.List.of(SNIPPET_TOP_LEFT, SNIPPET_TOP_RIGHT,
            SNIPPET_BOTTOM_LEFT, SNIPPET_BOTTOM_RIGHT);

    public static final String CURSOR_BLOCK = "block";
    public static final String CURSOR_UNDERLINE = "underline";
    public static final String CURSOR_BAR = "bar";
    public static final java.util.List<String> CURSOR_SHAPES = java.util.List.of(CURSOR_BLOCK, CURSOR_UNDERLINE, CURSOR_BAR);

    public static final String SFTP_SINGLE = "single";
    public static final String SFTP_DUAL = "dual";
    /** Id kolom nama di tabel SFTP; tidak bisa disembunyikan. */
    public static final String SFTP_COLUMN_NAME = "name";

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
    public static final int MIN_TERMINAL_OPACITY = 10;
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
    public static final String PANEL_ROUNDED = "rounded";
    public static final String PANEL_SQUARE = "square";
    public static final String TAB_CARD = "card";
    public static final String TAB_UNDERLINED = "underlined";
    public static final String RIGHT_CLICK_COPY_PASTE = "copyPaste";
    public static final String RIGHT_CLICK_MENU = "menu";

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
        if (terminalOpacity < MIN_TERMINAL_OPACITY || terminalOpacity > 100) {
            terminalOpacity = DEFAULT_OPACITY;
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
        if (!PANEL_SQUARE.equals(panelCorners)) {
            panelCorners = PANEL_ROUNDED;
        }
        if (!TAB_UNDERLINED.equals(tabStyle)) {
            tabStyle = TAB_CARD;
        }
        if (!RIGHT_CLICK_COPY_PASTE.equals(rightClick) && !RIGHT_CLICK_MENU.equals(rightClick)) {
            rightClick = defaultRightClick(Os.current());
        }
        wslManager = wslManager != null && wslManager; // opt-in: config lama = mati
        snippetButton = snippetButton == null || snippetButton; // config lama = tampil
        if (snippetCorner == null || !SNIPPET_CORNERS.contains(snippetCorner)) { // List.of menolak contains(null)
            snippetCorner = SNIPPET_TOP_RIGHT;
        }
        streak = streak == null || streak; // config lama = tampil
        if (!SFTP_DUAL.equals(sftpLayout)) {
            sftpLayout = SFTP_SINGLE;
        }
        if (cursorShape == null || !CURSOR_SHAPES.contains(cursorShape)) { // List.of menolak contains(null)
            cursorShape = CURSOR_BLOCK;
        }
        cursorBlink = cursorBlink == null || cursorBlink; // config lama = berkedip (bawaan JediTerm)
        if (tray == null) {
            tray = TrayConfig.defaults();
        }
        if (streakWidget == null) {
            streakWidget = StreakWidgetConfig.defaults();
        }
        sftpHiddenColumns = sftpHiddenColumns == null ? java.util.List.of()
                : sftpHiddenColumns.stream().filter(java.util.Objects::nonNull).map(String::strip)
                        .filter(id -> !id.isEmpty() && !SFTP_COLUMN_NAME.equals(id)).distinct().toList();
        sftpFollowTerminal = sftpFollowTerminal != null && sftpFollowTerminal; // opt-in: config lama = mati
        collapsedHostGroups = collapsedHostGroups == null ? java.util.List.of()
                : collapsedHostGroups.stream().filter(java.util.Objects::nonNull).distinct().sorted().toList();
    }

    /** Gaya Windows (console, PuTTY) di Windows; di macOS menu, sesuai kebiasaan Terminal/iTerm. */
    public static String defaultRightClick(Os os) {
        return os.isWindows() ? RIGHT_CLICK_COPY_PASTE : RIGHT_CLICK_MENU;
    }

    public static AppConfig defaults() {
        return new AppConfig(EditorConfig.defaults(), 14f, DEFAULT_ICON_SET, DEFAULT_OPACITY, HOST_DOCKED,
                DEFAULT_THEME, MODE_DARK, DEFAULT_LANGUAGE, true, true, null, null, true, null, DEFAULT_OPACITY, DEFAULT_OPACITY, ValidationHooks.defaults(),
                SPLIT_HORIZONTAL, true, true, DEFAULT_ANIMATION, DEFAULT_ANIMATION, HOST_STATUS_ALL, MEMORY_SAVER, DEFAULT_HOST_TOGGLE, DEFAULT_IDLE_MINUTES, ANIMATION_RANDOM, PANEL_ROUNDED, TAB_CARD, defaultRightClick(Os.current()), false, true, SNIPPET_TOP_RIGHT, true, SFTP_SINGLE, CURSOR_BLOCK, true, TrayConfig.defaults(), StreakWidgetConfig.defaults(), java.util.List.of(), false, java.util.List.of());
    }

    public AppConfig withEditors(EditorConfig e) {
        return new AppConfig(e, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    public AppConfig withIconSet(String id) {
        return new AppConfig(editors, terminalFontSize, id, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    public AppConfig withHostButtonOpacity(int percent) {
        return new AppConfig(editors, terminalFontSize, iconSet, percent, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    public AppConfig withHostPanelMode(String mode) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, mode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    public AppConfig withTheme(String id) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, id, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    public AppConfig withThemeMode(String mode) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, mode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    public AppConfig withLanguage(String tag) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                tag, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    public AppConfig withBellSound(boolean on) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, on, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    public AppConfig withBellShake(boolean on) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, on, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** @param family nama font aplikasi, atau null untuk font bawaan tema */
    public AppConfig withUiFontFamily(String family) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, family, terminalFontFamily, terminalThemeLinked, terminalTheme, windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** @param family nama font terminal, atau null untuk pilihan otomatis */
    public AppConfig withTerminalFontFamily(String family) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, family, terminalThemeLinked, terminalTheme, windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    public AppConfig withTerminalThemeLinked(boolean linked) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, linked, terminalTheme, windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** @param id id tema custom untuk warna terminal, atau null untuk warna bawaan */
    public AppConfig withTerminalTheme(String id) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, id, windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    public AppConfig withWindowOpacity(int percent) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                percent, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    public AppConfig withTerminalOpacity(int percent) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, percent, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** @return true kalau salah satu transparansi aktif (window perlu alpha per piksel) */
    public boolean translucent() {
        return windowOpacity < 100 || terminalOpacity < 100;
    }

    public AppConfig withValidationHooks(ValidationHooks hooks) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, hooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** @param mode {@value #SPLIT_HORIZONTAL} (menyamping) atau {@value #SPLIT_VERTICAL} (atas-bawah) */
    public AppConfig withSplitMode(String mode) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, mode, hostStatusBar, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    public AppConfig withHostStatusBar(boolean on) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, on, autoUpdateCheck, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    public AppConfig withAutoUpdateCheck(boolean on) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, on, loadingAnimation, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** @param id id animasi layar "Menghubungkan…", atau {@value #ANIMATION_OFF} */
    public AppConfig withLoadingAnimation(String id) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, id, statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** @param id id animasi panel bawah, atau {@value #ANIMATION_OFF} / {@value #ANIMATION_RANDOM} */
    public AppConfig withStatusAnimation(String id) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation, id, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** @param mode {@value #HOST_STATUS_ALL}, {@value #HOST_STATUS_GRAPH} atau {@value #HOST_STATUS_TEXT} */
    public AppConfig withHostStatusMode(String mode) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, mode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** @param mode {@value #MEMORY_SAVER} atau {@value #MEMORY_NORMAL} */
    public AppConfig withMemoryMode(String mode) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, mode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** @param id id gaya tombol panel host */
    public AppConfig withHostToggleStyle(String id) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, id, idleMinutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** @param minutes menit tanpa input sebelum layar idle tampil; 0 = mati */
    public AppConfig withIdleMinutes(int minutes) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, minutes, idleAnimation, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** @param id id animasi layar idle, atau {@value #ANIMATION_OFF} / {@value #ANIMATION_RANDOM} */
    public AppConfig withIdleAnimation(String id) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, id, panelCorners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** @param corners {@value #PANEL_ROUNDED} atau {@value #PANEL_SQUARE} */
    public AppConfig withPanelCorners(String corners) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, corners, tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** @param style {@value #TAB_CARD} atau {@value #TAB_UNDERLINED} */
    public AppConfig withTabStyle(String style) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners,
                style, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** @param mode {@value #RIGHT_CLICK_COPY_PASTE} atau {@value #RIGHT_CLICK_MENU} */
    public AppConfig withRightClick(String mode) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners,
                tabStyle, mode, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    public AppConfig withWslManager(boolean on) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners,
                tabStyle, rightClick, on, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** Tampilkan/sembunyikan tombol snippet di atas terminal. */
    public AppConfig withSnippetButton(boolean on) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners,
                tabStyle, rightClick, wslManager, on, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** Sudut tombol snippet; nilai tidak dikenal kembali ke kanan atas. */
    public AppConfig withSnippetCorner(String corner) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners,
                tabStyle, rightClick, wslManager, snippetButton, corner, streak, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** Tampilkan/sembunyikan api streak dan animasinya. */
    public AppConfig withStreak(boolean on) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners,
                tabStyle, rightClick, wslManager, snippetButton, snippetCorner, on, sftpLayout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** Tampilan panel SFTP; nilai tidak dikenal kembali ke satu panel. */
    public AppConfig withSftpLayout(String layout) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners,
                tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, layout, cursorShape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** Bentuk kursor terminal; nilai tidak dikenal kembali ke blok. */
    public AppConfig withCursorShape(String shape) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners,
                tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, shape, cursorBlink, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    public AppConfig withCursorBlink(boolean on) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners,
                tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, on, tray, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** Ikon tray dan perilaku window terhadapnya. */
    public AppConfig withTray(TrayConfig t) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners,
                tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, t, streakWidget, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** Widget streak mengambang (tampil, posisi). */
    public AppConfig withStreakWidget(StreakWidgetConfig w) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners,
                tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray, w, sftpHiddenColumns, sftpFollowTerminal, collapsedHostGroups);
    }

    /** Kolom tabel SFTP yang disembunyikan (id kolom, lihat {@link #sftpHiddenColumns}). */
    public AppConfig withSftpHiddenColumns(java.util.List<String> hidden) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners,
                tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray,
                streakWidget, hidden, sftpFollowTerminal, collapsedHostGroups);
    }

    /** Panel SFTP ikut folder kerja shell terminal (lihat {@link #sftpFollowTerminal}). */
    public AppConfig withSftpFollowTerminal(boolean on) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners,
                tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray,
                streakWidget, sftpHiddenColumns, on, collapsedHostGroups);
    }

    /** Grup panel host yang ditutup (lihat {@link #collapsedHostGroups}). */
    public AppConfig withCollapsedHostGroups(java.util.Collection<String> groups) {
        return new AppConfig(editors, terminalFontSize, iconSet, hostButtonOpacity, hostPanelMode, theme, themeMode,
                language, bellSound, bellShake, uiFontFamily, terminalFontFamily, terminalThemeLinked, terminalTheme,
                windowOpacity, terminalOpacity, validationHooks, splitMode, hostStatusBar, autoUpdateCheck, loadingAnimation,
                statusAnimation, hostStatusMode, memoryMode, hostToggleStyle, idleMinutes, idleAnimation, panelCorners,
                tabStyle, rightClick, wslManager, snippetButton, snippetCorner, streak, sftpLayout, cursorShape, cursorBlink, tray,
                streakWidget, sftpHiddenColumns, sftpFollowTerminal, java.util.List.copyOf(groups));
    }
}
