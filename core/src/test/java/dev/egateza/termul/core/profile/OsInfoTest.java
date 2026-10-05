package dev.egateza.termul.core.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OsInfoTest {

    @TempDir
    Path dir;

    @Test
    void parseUbuntu() {
        String content = """
                PRETTY_NAME="Ubuntu 22.04.4 LTS"
                NAME="Ubuntu"
                VERSION_ID="22.04"
                VERSION="22.04.4 LTS (Jammy Jellyfish)"
                ID=ubuntu
                ID_LIKE=debian
                """;
        assertThat(OsInfo.parseOsRelease(content)).hasValue(new OsInfo("ubuntu", "22.04", "Ubuntu 22.04.4 LTS"));
    }

    @Test
    void parseAlpineDanQuoteTunggal() {
        assertThat(OsInfo.parseOsRelease("NAME=\"Alpine Linux\"\nID=alpine\nVERSION_ID=3.20.3\n"))
                .get().extracting(OsInfo::id, OsInfo::label).containsExactly("alpine", "alpine 3.20.3");
        assertThat(OsInfo.parseOsRelease("ID='rocky'\n")).get().extracting(OsInfo::id).isEqualTo("rocky");
    }

    @Test
    void inputTidakValidDitolakAtauDibersihkan() {
        assertThat(OsInfo.parseOsRelease("")).isEmpty();
        assertThat(OsInfo.parseOsRelease("bash: /etc/os-release: No such file")).isEmpty();
        assertThat(OsInfo.parseOsRelease("ID=\"<script>\"")).isEmpty();
        assertThat(OsInfo.parseOsRelease("ID=" + "a".repeat(100))).isEmpty();

        var os = OsInfo.parseOsRelease("ID=Debian\nPRETTY_NAME=\"Debian\u001b[31m 12\"\n").orElseThrow();
        assertThat(os.id()).isEqualTo("debian");
        assertThat(os.prettyName()).isEqualTo("Debian[31m 12");
        assertThat(OsInfo.parseOsRelease("ID=x\nPRETTY_NAME=" + "p".repeat(500)).orElseThrow().prettyName()).hasSize(80);
    }

    @Test
    void osTersimpanDiProfilDanJsonLamaTetapTerbaca() throws Exception {
        Path file = dir.resolve("profiles.json");
        var store = new ProfileStore(file);
        var p = HostProfile.create("web", "10.0.0.1", "dev").withOs(new OsInfo("ubuntu", "22.04", "Ubuntu 22.04 LTS"));
        store.save(p);
        assertThat(new ProfileStore(file).load().find(p.id())).get()
                .extracting(HostProfile::os).isEqualTo(p.os());
        assertThat(p.duplicate().os()).isEqualTo(p.os());
        assertThat(p.withGroup("X").os()).isEqualTo(p.os());

        String legacy = Files.readString(file).replaceAll("(?s),\\s*\"os\"\\s*:\\s*\\{[^}]*\\}", "");
        assertThat(legacy).doesNotContain("\"os\"");
        Files.writeString(file, legacy);
        assertThat(new ProfileStore(file).load().find(p.id())).get().extracting(HostProfile::os).isNull();
    }
}
