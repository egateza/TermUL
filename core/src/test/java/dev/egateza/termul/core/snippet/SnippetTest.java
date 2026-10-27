package dev.egateza.termul.core.snippet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SnippetTest {

    private static Snippet of(String command, boolean enter) {
        return new Snippet(UUID.randomUUID(), "x", command, enter, "", false, false, SnippetScope.ALL, Map.of());
    }

    @Test
    void barisBaruDiAkhirBerartiLangsungDijalankan() {
        var run = Snippet.create("bersih", "clear\n");
        assertThat(run.command()).isEqualTo("clear");
        assertThat(run.enter()).isTrue();
        assertThat(run.commandWithEnter()).isEqualTo("clear\n");

        var paste = Snippet.create("disk", "  df -h  ");
        assertThat(paste.command()).isEqualTo("df -h");
        assertThat(paste.enter()).as("tanpa baris baru di akhir: menunggu Enter dari user").isFalse();
    }

    @Test
    void bolehBeberapaBarisTapiTanpaKarakterKontrolLain() {
        var multi = of("sudo systemctl stop nginx\r\nsudo systemctl start nginx", false);
        assertThat(multi.lines()).containsExactly("sudo systemctl stop nginx", "sudo systemctl start nginx");
        for (String bad : new String[] {"printf '\u001b[2J'", "tab\there", "a\u0085b", "echo\u0007"}) {
            assertThatThrownBy(() -> of(bad, false)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void judulDanPerintahWajibDiisi() {
        assertThatThrownBy(() -> Snippet.create(" ", "ls")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Snippet.create("ls", "  \n ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Snippet.create("x".repeat(Snippet.MAX_TITLE + 1), "ls"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Snippet.create("a\nb", "ls")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void berbahayaKarenaDitandaiAtauPolaPerintah() {
        assertThat(of("sudo rm -rf /var/cache/nginx/*", false).isDangerous()).isTrue();
        assertThat(of("df -h", false).isDangerous()).isFalse();
        var marked = new Snippet(UUID.randomUUID(), "x", "./deploy.sh", false, "", false, true, SnippetScope.ALL,
                Map.of());
        assertThat(marked.isDangerous()).isTrue();
    }

    @Test
    void defaultVariabelYangTidakValidDibuang() {
        var s = new Snippet(UUID.randomUUID(), "x", "tail -n ${lines} ${file}", false, "", false, false,
                SnippetScope.ALL, Map.of("lines", "50", "9bad", "x", "file", "a\nb"));
        assertThat(s.variables()).containsExactly(Map.entry("lines", "50"));
        assertThat(s.variableNames()).containsExactly("lines", "file");
    }

    @Test
    void cakupanSemuaGrupDanHost() {
        assertThat(SnippetScope.ALL.appliesTo("web", "")).isTrue();

        var db = new SnippetScope.Group(" Prod / DB ");
        assertThat(db.path()).isEqualTo("Prod/DB");
        assertThat(db.appliesTo("x", "Prod/DB")).isTrue();
        assertThat(db.appliesTo("x", "Prod/DB/Replica")).isTrue();
        assertThat(db.appliesTo("x", "Prod")).isFalse();
        assertThat(db.appliesTo("x", "Prod/DBX")).as("bukan subgrup, hanya awalan nama yang sama").isFalse();

        var gateway = new SnippetScope.Host("gateway");
        assertThat(gateway.appliesTo("Gateway", "Prod")).isTrue();
        assertThat(gateway.appliesTo("gateway-2", "Prod")).isFalse();
    }
}
