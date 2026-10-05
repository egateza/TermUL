package dev.egateza.termul.launcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherTest {

    @TempDir
    Path dir;

    @Test
    void versiMayor() {
        assertThat(Launcher.major("1.8")).isEqualTo(8);
        assertThat(Launcher.major("1.8.0_452")).isEqualTo(8);
        assertThat(Launcher.major("11")).isEqualTo(11);
        assertThat(Launcher.major("25.0.2")).isEqualTo(25);
        assertThat(Launcher.major("26-ea")).isEqualTo(26);
        assertThat(Launcher.major("")).isEqualTo(-1);
        assertThat(Launcher.major(null)).isEqualTo(-1);
    }

    @Test
    void memilihJdk25KeAtasYangTertinggi() throws Exception {
        Path jdk11 = jdk("jdk-11", "11.0.17", true);
        Path jdk25 = jdk("jdk-25", "25.0.2", true);
        Path jdk26 = jdk("jdk-26", "26", true);

        assertThat(Launcher.pickJava(List.of(jdk11, jdk25), true))
                .isEqualTo(jdk25.resolve("bin").resolve("javaw.exe"));
        assertThat(Launcher.pickJava(List.of(jdk25, jdk26, jdk11), true))
                .isEqualTo(jdk26.resolve("bin").resolve("javaw.exe"));
    }

    @Test
    void jdkLamaAtauTanpaExecutableDiabaikan() throws Exception {
        Path jdk11 = jdk("jdk-11", "11.0.17", true);
        Path broken = jdk("jdk-25-rusak", "25.0.2", false);
        Path jdk8 = jdk("jdk-8", "1.8.0_452", true);

        assertThat(Launcher.pickJava(List.of(jdk11, broken, jdk8), true)).isNull();
        assertThat(Launcher.majorOfHome(dir.resolve("tidak-ada"))).isEqualTo(-1);
        assertThat(Launcher.majorOfHome(jdk8)).isEqualTo(8);
    }

    private Path jdk(String name, String version, boolean withExe) throws Exception {
        Path home = Files.createDirectories(dir.resolve(name));
        Files.writeString(home.resolve("release"), "IMPLEMENTOR=\"Eclipse Adoptium\"\nJAVA_VERSION=\"" + version + "\"\n");
        if (withExe) {
            Files.createDirectories(home.resolve("bin"));
            Files.writeString(home.resolve("bin").resolve("javaw.exe"), "");
        }
        return home;
    }
}
