package dev.egateza.termul.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class BuildInfoTest {

    @Test
    void versiDanCommitDitampilkanLengkap() {
        var info = BuildInfo.from(props("0.1.105", "1b5275d-dirty"));
        assertThat(info).isEqualTo(new BuildInfo("0.1.105", "1b5275d-dirty"));
        assertThat(info.display()).isEqualTo("0.1.105 (1b5275d-dirty)");
    }

    @Test
    void tanpaCommitHanyaVersi() {
        assertThat(BuildInfo.from(props("0.1.105", "")).display()).isEqualTo("0.1.105");
    }

    @Test
    void placeholderMavenYangTidakTerResolveDianggapDev() {
        var info = BuildInfo.from(props("${termul.versionBase}.${git.total.commit.count}", "${git.commit.id.describe}"));
        assertThat(info).isEqualTo(BuildInfo.DEV);
    }

    @Test
    void commitPlaceholderTidakIkutDitampilkan() {
        assertThat(BuildInfo.from(props("0.1.105", "${git.commit.id.describe}")).display()).isEqualTo("0.1.105");
    }

    @Test
    void resourceHasilBuildMavenTerbaca() {
        // target/classes sudah di-filter oleh Maven saat test dijalankan via mvnw
        var info = BuildInfo.load();
        assertThat(info.version()).matches("\\d+\\.\\d+\\.\\d+|dev");
    }

    private static Properties props(String version, String commit) {
        var p = new Properties();
        p.setProperty("version", version);
        p.setProperty("commit", commit);
        return p;
    }
}
