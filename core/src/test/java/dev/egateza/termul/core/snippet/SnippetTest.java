package dev.egateza.termul.core.snippet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SnippetTest {

    @Test
    void snippetTidakBolehBerisiEnterAtauKarakterKontrol() {
        assertThat(Snippet.create("hapus", "rm -rf /tmp/x\n", "").command())
                .as("Enter di akhir dibuang, jadi snippet tetap tidak menjalankan dirinya sendiri")
                .isEqualTo("rm -rf /tmp/x");
        for (String bad : new String[] {"ls\nrm -rf /", "echo a\rb", "printf '\u001b[2J'", "tab\there", "a\u0085b"}) {
            assertThatThrownBy(() -> Snippet.create("x", bad, ""))
                    .as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void namaDanPerintahWajibDiisi() {
        assertThatThrownBy(() -> Snippet.create(" ", "ls", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Snippet.create("ls", "   ", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Snippet.create("x".repeat(Snippet.MAX_NAME + 1), "ls", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void berlakuUntukGrupDanSubgrupnya() {
        var global = Snippet.create("Disk", "df -h", "");
        var db = Snippet.create("Status postgres", "systemctl status postgresql", " Prod / DB ");

        assertThat(db.group()).isEqualTo("Prod/DB");
        assertThat(global.appliesTo("Staging")).isTrue();
        assertThat(db.appliesTo("Prod/DB")).isTrue();
        assertThat(db.appliesTo("Prod/DB/Replica")).isTrue();
        assertThat(db.appliesTo("Prod")).isFalse();
        assertThat(db.appliesTo("Prod/DBX")).as("bukan subgrup, hanya awalan nama yang sama").isFalse();
        assertThat(db.appliesTo("")).isFalse();
    }
}
