package dev.egateza.termul.app.ui;

/**
 * Item mana di menu Terminal yang boleh dipakai, berdasarkan tab yang sedang dipilih.
 *
 * @param tabActions duplikat, reconnect, tutup tab, dan zoom: cukup ada tab (reconnect justru dipakai saat sesi putus)
 * @param sftp       panel SFTP: butuh sesi tersambung, atau panelnya sedang terbuka (supaya masih bisa ditutup)
 * @param inject     inject password sudo/root: butuh sesi tersambung
 */
record TerminalMenuState(boolean tabActions, boolean sftp, boolean inject) {

    static final TerminalMenuState NO_TAB = new TerminalMenuState(false, false, false);

    static TerminalMenuState of(boolean hasTab, boolean connected, boolean sftpOpen) {
        return of(hasTab, connected, sftpOpen, true);
    }

    /** @param hasTerminal false untuk tab "SFTP saja": tanpa shell, jadi panel SFTP dan inject password tidak berlaku */
    static TerminalMenuState of(boolean hasTab, boolean connected, boolean sftpOpen, boolean hasTerminal) {
        if (!hasTab) {
            return NO_TAB;
        }
        if (!hasTerminal) {
            return new TerminalMenuState(true, false, false);
        }
        return new TerminalMenuState(true, connected || sftpOpen, connected);
    }
}
