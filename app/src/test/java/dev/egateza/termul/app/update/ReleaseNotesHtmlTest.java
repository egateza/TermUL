package dev.egateza.termul.app.update;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReleaseNotesHtmlTest {

    @Test
    void bulletMenjadiDaftar() {
        assertThat(ReleaseNotesHtml.toHtml("- Perbaikan: koneksi\n- Baru: tombol salin"))
                .isEqualTo("<ul><li>Perbaikan: koneksi</li><li>Baru: tombol salin</li></ul>");
    }

    @Test
    void headingParagrafDanDaftar() {
        String html = ReleaseNotesHtml.toHtml("""
                ## Sorotan
                Rilis ini
                memperbaiki SFTP.

                ### Terminal
                * Seleksi teks lebih rapi
                Catatan penutup""");

        assertThat(html).isEqualTo("<h3>Sorotan</h3><p>Rilis ini memperbaiki SFTP.</p>"
                + "<h4>Terminal</h4><ul><li>Seleksi teks lebih rapi</li></ul><p>Catatan penutup</p>");
    }

    @Test
    void inlineTebalKodeLinkDanIssue() {
        assertThat(ReleaseNotesHtml.inline("**Baru** `sudo` [docs](https://example.com/a?b=1&c=2) di #42"))
                .isEqualTo("<b>Baru</b> <code>sudo</code> <a href=\"https://example.com/a?b=1&amp;c=2\">docs</a> di "
                        + "<a href=\"https://github.com/egateza/TermUL/issues/42\">#42</a>");
    }

    @Test
    void htmlDiEscapeDanLinkNonHttpTetapTeks() {
        assertThat(ReleaseNotesHtml.inline("<img src=x> [klik](javascript:alert(1)) <b>"))
                .isEqualTo("&lt;img src=x&gt; [klik](javascript:alert(1)) &lt;b&gt;");
        assertThat(ReleaseNotesHtml.inline("**<script>**")).isEqualTo("<b>&lt;script&gt;</b>");
    }

    @Test
    void bintangTebalBukanBullet() {
        assertThat(ReleaseNotesHtml.toHtml("**Penting**: pasang ulang installer"))
                .isEqualTo("<p><b>Penting</b>: pasang ulang installer</p>");
    }
}
