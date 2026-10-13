package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.sftp.ActivityBar;
import dev.egateza.termul.app.ui.fab.FloatingStyle;
import org.junit.jupiter.api.Test;

class EditStatusFloatTest {

    @Test
    void statusBiasaHilangSendiriYangPerluPerhatianTetapTampil() {
        var uploading = EditStatusFloat.Look.of(ActivityBar.Level.INFO);
        var done = EditStatusFloat.Look.of(ActivityBar.Level.SUCCESS);
        var conflict = EditStatusFloat.Look.of(ActivityBar.Level.WARN);
        var failed = EditStatusFloat.Look.of(ActivityBar.Level.ERROR);

        assertThat(uploading.hideAfterMs()).isPositive();
        assertThat(done.hideAfterMs()).isPositive();
        assertThat(conflict.hideAfterMs()).isZero();
        assertThat(failed.hideAfterMs()).isZero();
        assertThat(conflict.kind()).isEqualTo(FloatingStyle.Kind.WARNING);
        assertThat(failed.kind()).isEqualTo(FloatingStyle.Kind.DANGER);
        assertThat(conflict.opensTracker()).isTrue();
        assertThat(done.opensTracker()).isFalse();
    }

    @Test
    void teksPanjangDipendekkanSatuBaris() {
        String text = "Upload /etc/nginx/sites-available/default gagal:\nConnection reset by peer while writing";

        String shown = EditStatusFloat.shorten(text);

        assertThat(shown).hasSize(EditStatusFloat.MAX_TEXT).endsWith("…").doesNotContain("\n");
        assertThat(EditStatusFloat.shorten("nginx.conf berhasil diupload")).isEqualTo("nginx.conf berhasil diupload");
    }
}
