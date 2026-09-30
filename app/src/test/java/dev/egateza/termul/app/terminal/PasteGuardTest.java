package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.profile.EnvironmentTag;
import org.junit.jupiter.api.Test;

class PasteGuardTest {

    @Test
    void hanyaProdDenganBarisBaruYangPerluKonfirmasi() {
        assertThat(PasteGuard.needsConfirmation("rm -rf /tmp/x\nls", EnvironmentTag.PROD)).isTrue();
        assertThat(PasteGuard.needsConfirmation("ls\r", EnvironmentTag.PROD)).isTrue(); // satu baris + Enter tetap langsung jalan
        assertThat(PasteGuard.needsConfirmation("ls -la", EnvironmentTag.PROD)).isFalse();
        assertThat(PasteGuard.needsConfirmation("a\nb", EnvironmentTag.STAGING)).isFalse();
        assertThat(PasteGuard.needsConfirmation("a\nb", EnvironmentTag.NONE)).isFalse();
        assertThat(PasteGuard.needsConfirmation(null, EnvironmentTag.PROD)).isFalse();
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
