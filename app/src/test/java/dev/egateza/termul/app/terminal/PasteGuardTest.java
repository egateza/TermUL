package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasteGuardTest {

    @Test
    void hanyaHostSensitifDenganBarisBaruYangPerluKonfirmasi() {
        assertThat(PasteGuard.needsConfirmation("rm -rf /tmp/x\nls", true)).isTrue();
        assertThat(PasteGuard.needsConfirmation("ls\r", true)).isTrue(); // satu baris + Enter tetap langsung jalan
        assertThat(PasteGuard.needsConfirmation("ls -la", true)).isFalse();
        assertThat(PasteGuard.needsConfirmation("a\nb", false)).isFalse();
        assertThat(PasteGuard.needsConfirmation("a\nb", false)).isFalse();
        assertThat(PasteGuard.needsConfirmation(null, true)).isFalse();
    }

    @Test
    void barisDihitungDariCrlfCrLfTanpaBarisKosongDiAkhir() {
        assertThat(PasteGuard.lines("a\r\nb\rc\n\n")).containsExactly("a", "b", "c");
        assertThat(PasteGuard.lines("a\n\nb\n")).containsExactly("a", "", "b");
    }

    @Test
    void cuplikanDibatasi() {
        var lines = PasteGuard.lines("1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n" + "x".repeat(200));

        String preview = PasteGuard.preview(lines);

        assertThat(preview.lines()).hasSize(PasteGuard.PREVIEW_LINES + 1);
        assertThat(preview).startsWith("1\n2\n").endsWith("… (+3)\n");
        assertThat(PasteGuard.preview(PasteGuard.lines("y".repeat(200)))).hasSize(PasteGuard.PREVIEW_COLUMNS + 2);
    }
}
