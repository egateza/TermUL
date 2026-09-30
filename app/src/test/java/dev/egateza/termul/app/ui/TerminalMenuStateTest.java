package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TerminalMenuStateTest {

    @Test
    void noTabDisablesEverything() {
        assertThat(TerminalMenuState.of(false, false, false)).isEqualTo(TerminalMenuState.NO_TAB);
        // status lain diabaikan kalau tidak ada tab
        assertThat(TerminalMenuState.of(false, true, true)).isEqualTo(TerminalMenuState.NO_TAB);
    }

    @Test
    void connectedEnablesEverything() {
        var s = TerminalMenuState.of(true, true, false);

        assertThat(s.tabActions()).isTrue();
        assertThat(s.sftp()).isTrue();
        assertThat(s.inject()).isTrue();
        assertThat(s.split()).isTrue();
    }

    @Test
    void tabWithoutSessionKeepsOnlyTabActionsAndSplit() {
        var s = TerminalMenuState.of(true, false, false);

        assertThat(s.tabActions()).isTrue();
        assertThat(s.sftp()).isFalse();
        assertThat(s.inject()).isFalse();
        assertThat(s.split()).isTrue(); // panel baru membuka koneksinya sendiri
    }

    @Test
    void sftpOnlyTabKeepsTabActionsButHasNoSftpToggleInjectOrSplit() {
        var s = TerminalMenuState.of(true, false, true, false);

        assertThat(s.tabActions()).isTrue();
        assertThat(s.sftp()).isFalse();
        assertThat(s.inject()).isFalse();
        assertThat(s.split()).isFalse();
    }

    @Test
    void openSftpPanelCanStillBeClosedAfterSessionEnds() {
        var s = TerminalMenuState.of(true, false, true);

        assertThat(s.sftp()).isTrue();
        assertThat(s.inject()).isFalse();
    }
}
