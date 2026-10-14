package dev.egateza.termul.app.sftp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FormatsElideTest {

    /** Lebar 1 px per karakter, supaya hasilnya bisa dihitung di kepala. */
    private static int width(String s) {
        return s.length();
    }

    @Test
    void namaPendekTidakDiubah() {
        assertThat(Formats.elideMiddle("Download a.txt", 20, FormatsElideTest::width)).isEqualTo("Download a.txt");
    }

    @Test
    void namaPanjangDisingkatDiTengahDanEkstensiTetapTerlihat() {
        String title = "Download PPOB_PLN_TEMPLATE-1.0.jar.filepart";
        String shown = Formats.elideMiddle(title, 25, FormatsElideTest::width);

        assertThat(shown).hasSize(25).contains("…").startsWith("Download PP").endsWith("jar.filepart");
    }

    @Test
    void ruangSangatSempit() {
        assertThat(Formats.elideMiddle("abcdefghij", 1, FormatsElideTest::width)).isEqualTo("…");
        assertThat(Formats.elideMiddle("abcdefghij", 4, FormatsElideTest::width)).isEqualTo("a…ij");
    }
}
