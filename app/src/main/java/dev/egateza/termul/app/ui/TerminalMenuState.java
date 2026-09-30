package dev.egateza.termul.app.ui;

/**
 * Item mana di menu Terminal yang boleh dipakai, berdasarkan tab yang sedang dipilih.
 *
 * @param tabActions duplikat, reconnect, tutup tab/panel, dan zoom: cukup ada tab (reconnect justru dipakai saat sesi putus)
 * @param sftp       panel SFTP: butuh sesi tersambung, atau panelnya sedang terbuka (supaya masih bisa ditutup)
 * @param inject     inject password sudo/root: butuh sesi tersambung
 * @param split      split terminal (kanan/bawah): butuh tab terminal; tab "SFTP saja" tidak bisa di-split
 */
record TerminalMenuState(boolean tabActions, boolean sftp, boolean inject, boolean split) {

    static final TerminalMenuState NO_TAB = new TerminalMenuState(false, false, false, false);

    static TerminalMenuState of(boolean hasTab, boolean connected, boolean sftpOpen) {
        return of(hasTab, connected, sftpOpen, true);
    }

    /** @param hasTerminal false untuk tab "SFTP saja": tanpa shell, jadi panel SFTP, inject password, dan split tidak berlaku */
    static TerminalMenuState of(boolean hasTab, boolean connected, boolean sftpOpen, boolean hasTerminal) {
        if (!hasTab) {
            return NO_TAB;
        }
        if (!hasTerminal) {
            return new TerminalMenuState(true, false, false, false);
        }
        return new TerminalMenuState(true, connected || sftpOpen, connected, true);
    }
}
