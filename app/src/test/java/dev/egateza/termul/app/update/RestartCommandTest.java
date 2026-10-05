package dev.egateza.termul.app.update;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.Os;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RestartCommandTest {

    @Test
    void appImageJpackageMemakaiExeLauncher() {
        var props = Map.of("jpackage.app-path", "C:\\Program Files\\TermUL\\TermUL.exe",
                "java.class.path", "a.jar" + File.pathSeparator + "b.jar");

        assertThat(RestartCommand.of(props::get, Optional.of("java"), 42, Os.WINDOWS))
                .contains(List.of("C:\\Program Files\\TermUL\\TermUL.exe", "--wait-pid=42"));
    }

    @Test
    void fatJarDijalankanUlangDenganJavaYangSama() {
        var props = Map.of("java.class.path", "/Users/x/TermUL/TermUL.jar", "termul.update.generation", "1",
                "termul.home", "/tmp/dev");

        assertThat(RestartCommand.of(props::get, Optional.of("/jdk/bin/java"), 7, Os.MAC)).contains(List.of(
                "/jdk/bin/java", "--enable-native-access=ALL-UNNAMED", "-Dtermul.home=/tmp/dev", "-Xdock:name=TermUL",
                "-jar", "/Users/x/TermUL/TermUL.jar", "--wait-pid=7"));
    }

    @Test
    void dariIdeTidakBisaRestart() {
        var props = Map.of("java.class.path", "core/target/classes" + File.pathSeparator + "x.jar");
        assertThat(RestartCommand.of(props::get, Optional.of("java"), 1, Os.WINDOWS)).isEmpty();
    }

    @Test
    void jarTanpaBootstrapTidakDirestart() {
        var props = Map.of("java.class.path", "TermUL.jar");
        assertThat(RestartCommand.of(props::get, Optional.of("java"), 1, Os.WINDOWS)).isEmpty();
    }
}
