package dev.egateza.termul.update;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class BootstrapTest {

    @Test
    void argumenWaitPidDibuang() {
        var notes = new ArrayList<String>();
        long finished = Long.MAX_VALUE - 1; // PID yang tidak ada: tidak perlu ditunggu

        String[] rest = Bootstrap.waitForPrevious(new String[] {"a", "--wait-pid=" + finished, "b"}, notes);

        assertThat(rest).containsExactly("a", "b");
        assertThat(notes).isEmpty();
    }

    @Test
    void waitPidTidakValidDicatat() {
        var notes = new ArrayList<String>();
        assertThat(Bootstrap.waitForPrevious(new String[] {"--wait-pid=abc"}, notes)).isEmpty();
        assertThat(notes).hasSize(1);
    }

    @Test
    void versiBawaanDariBuildProperties() {
        assertThat(Bootstrap.bundledVersion(loader("version=0.1.124\ncommit=abc\n"))).hasToString("0.1.124");
    }

    @Test
    void buildDevTidakPunyaVersiBawaan() {
        assertThat(Bootstrap.bundledVersion(loader("version=${termul.versionBase}.${git.total.commit.count}\n")))
                .isNull();
        assertThat(Bootstrap.bundledVersion(loader(null))).isNull();
    }

    /** Classloader yang hanya menyediakan build.properties aplikasi (null = tidak ada). */
    private static ClassLoader loader(String buildProperties) {
        return new ClassLoader(null) {
            @Override
            public InputStream getResourceAsStream(String name) {
                return buildProperties != null && name.equals(UpdateProtocol.BUILD_INFO_RESOURCE)
                        ? new ByteArrayInputStream(buildProperties.getBytes(StandardCharsets.UTF_8)) : null;
            }
        };
    }
}
