package dev.egateza.termul.app.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class I18nTest {

    @AfterEach
    void reset() {
        I18n.use(I18n.FALLBACK);
    }

    @Test
    void semuaBahasaPunyaKeyYangSamaDenganBahasaAcuan() {
        var reference = I18n.keys(I18n.FALLBACK);
        for (var language : I18n.SUPPORTED) {
            assertThat(I18n.keys(language.tag()))
                    .as("key bundle %s", language.tag())
                    .containsExactlyInAnyOrderElementsOf(reference);
        }
    }

    @Test
    void bahasaTerpilihDipakai() {
        I18n.use("en");
        assertThat(I18n.t("exit.confirm")).isEqualTo("Close TermUL?");
        I18n.use("id");
        assertThat(I18n.t("exit.confirm")).isEqualTo("Tutup TermUL?");
    }

    @Test
    void tagTidakDikenalJatuhKeBahasaAcuan() {
        I18n.use("xx");
        assertThat(I18n.current()).isEqualTo(I18n.FALLBACK);
        I18n.use(null);
        assertThat(I18n.current()).isEqualTo(I18n.FALLBACK);
    }

    @Test
    void keyTidakAdaTampilSebagaiKey() {
        assertThat(I18n.t("tidak.ada")).isEqualTo("tidak.ada");
    }

    @Test
    void argumenDiformat() {
        assertThat(I18n.tIn("en", "menu.settings.language.restart", "English"))
                .isEqualTo("Language changed to English. Restart TermUL for it to apply everywhere.");
    }

    @Test
    void tIndalamBahasaLainTanpaMengubahBahasaAktif() {
        I18n.use("id");
        assertThat(I18n.tIn("en", "exit.title")).isEqualTo("Exit TermUL");
        assertThat(I18n.current()).isEqualTo("id");
    }
}
