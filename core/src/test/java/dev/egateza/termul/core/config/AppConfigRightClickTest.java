package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.Os;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigRightClickTest {

    @TempDir
    Path dir;

    @Test
    void defaultGayaWindowsHanyaDiWindows() {
        assertThat(AppConfig.defaultRightClick(Os.WINDOWS)).isEqualTo(AppConfig.RIGHT_CLICK_COPY_PASTE);
        assertThat(AppConfig.defaultRightClick(Os.MAC)).isEqualTo(AppConfig.RIGHT_CLICK_MENU);
        assertThat(AppConfig.defaultRightClick(Os.OTHER)).isEqualTo(AppConfig.RIGHT_CLICK_MENU);
    }

    @Test
    void configLamaDanNilaiTidakDikenalMemakaiDefaultOs() throws Exception {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16}");
        String expected = AppConfig.defaultRightClick(Os.current());

        assertThat(new ConfigStore(file).load().rightClick()).isEqualTo(expected);
        assertThat(AppConfig.defaults().rightClick()).isEqualTo(expected);
        assertThat(AppConfig.defaults().withRightClick("aneh").rightClick()).isEqualTo(expected);
        assertThat(AppConfig.defaults().withRightClick(null).rightClick()).isEqualTo(expected);
    }

    @Test
    void pilihanTersimpanDanTidakHilangSaatFieldLainDiubah() {
        Path file = dir.resolve("config.json");
        for (String mode : new String[] {AppConfig.RIGHT_CLICK_MENU, AppConfig.RIGHT_CLICK_COPY_PASTE}) {
            new ConfigStore(file).save(AppConfig.defaults().withRightClick(mode).withTabStyle(AppConfig.TAB_UNDERLINED));

            var loaded = new ConfigStore(file).load();

            assertThat(loaded.rightClick()).isEqualTo(mode);
            assertThat(loaded.withTheme("x").withIdleMinutes(3).rightClick()).isEqualTo(mode);
        }
    }
}
