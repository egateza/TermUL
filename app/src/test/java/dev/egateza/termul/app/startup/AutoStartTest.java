package dev.egateza.termul.app.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.egateza.termul.core.Os;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AutoStartTest {

    private static final String EXE = "C:\\Program Files\\TermUL\\TermUL.exe";

    @TempDir
    Path home;

    /** Registry di memori; key + "|" + name. */
    static final class FakeRegistry implements WinRegistry {
        final Map<String, Object> values = new HashMap<>();

        @Override
        public String readString(String key, String name) {
            return values.get(key + "|" + name) instanceof String s ? s : null;
        }

        @Override
        public byte[] readBinary(String key, String name) {
            return values.get(key + "|" + name) instanceof byte[] b ? b : null;
        }

        @Override
        public void writeString(String key, String name, String value) {
            values.put(key + "|" + name, value);
        }

        @Override
        public void delete(String key, String name) {
            values.remove(key + "|" + name);
        }

        @Override
        public void deleteTree(String key) {
            values.keySet().removeIf(k -> k.startsWith(key + "|") || k.startsWith(key + "\\"));
        }
    }

    @Test
    void tanpaLauncherJpackageTidakDidukung() {
        var autoStart = AutoStart.of(Os.WINDOWS, k -> null, home, FakeRegistry::new);
        assertThat(autoStart.status()).isEqualTo(AutoStart.Status.UNSUPPORTED);
        assertThatThrownBy(() -> autoStart.setEnabled(true)).isInstanceOf(IOException.class);
        assertThat(AutoStart.of(Os.OTHER, k -> EXE, home, FakeRegistry::new)).as("opsi disembunyikan").isNull();
    }

    @Test
    void windowsMenulisDanMenghapusEntriRun() throws Exception {
        var registry = new FakeRegistry();
        var autoStart = windows(registry);
        assertThat(autoStart.status()).isEqualTo(AutoStart.Status.DISABLED);

        autoStart.setEnabled(true);
        assertThat(registry.readString(WindowsAutoStart.RUN_KEY, WindowsAutoStart.VALUE_NAME))
                .isEqualTo("\"" + EXE + "\" --tray");
        assertThat(autoStart.status()).isEqualTo(AutoStart.Status.ENABLED);

        autoStart.setEnabled(false);
        assertThat(registry.values).isEmpty();
        assertThat(autoStart.status()).isEqualTo(AutoStart.Status.DISABLED);
    }

    @Test
    void windowsMengenaliEntriYangDimatikanDiSettings() throws Exception {
        var registry = new FakeRegistry();
        var autoStart = windows(registry);
        autoStart.setEnabled(true);
        registry.values.put(WindowsAutoStart.APPROVED_KEY + "|" + WindowsAutoStart.VALUE_NAME,
                new byte[] {3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        assertThat(autoStart.status()).isEqualTo(AutoStart.Status.DISABLED_BY_SYSTEM);

        autoStart.setEnabled(true); // centang lagi di TermUL = nyalakan lagi
        assertThat(autoStart.status()).isEqualTo(AutoStart.Status.ENABLED);

        registry.values.put(WindowsAutoStart.APPROVED_KEY + "|" + WindowsAutoStart.VALUE_NAME,
                new byte[] {2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        assertThat(autoStart.status()).as("byte genap = aktif").isEqualTo(AutoStart.Status.ENABLED);
    }

    @Test
    void windowsEntriDariLokasiLainDianggapBelumAktif() {
        var registry = new FakeRegistry();
        registry.writeString(WindowsAutoStart.RUN_KEY, WindowsAutoStart.VALUE_NAME, "\"D:\\lama\\TermUL.exe\" --tray");
        assertThat(windows(registry).status()).isEqualTo(AutoStart.Status.DISABLED);
    }

    @Test
    void macMenulisLaunchAgent() throws Exception {
        var autoStart = (MacAutoStart) AutoStart.of(Os.MAC,
                k -> AutoStart.JPACKAGE_APP_PATH.equals(k) ? "/Applications/A&B.app/Contents/MacOS/TermUL" : null,
                home, () -> {
                    throw new AssertionError("registry tidak dipakai di macOS");
                });
        assertThat(autoStart.status()).isEqualTo(AutoStart.Status.DISABLED);

        autoStart.setEnabled(true);
        String plist = Files.readString(home.resolve("Library/LaunchAgents/dev.egateza.termul.plist"));
        assertThat(plist).contains("<string>/Applications/A&amp;B.app/Contents/MacOS/TermUL</string>")
                .contains("<string>--tray</string>").contains("<key>RunAtLoad</key>");
        assertThat(autoStart.status()).isEqualTo(AutoStart.Status.ENABLED);

        autoStart.setEnabled(false);
        assertThat(autoStart.plist()).doesNotExist();
        assertThat(autoStart.status()).isEqualTo(AutoStart.Status.DISABLED);
    }

    private AutoStart windows(FakeRegistry registry) {
        return AutoStart.of(Os.WINDOWS, k -> AutoStart.JPACKAGE_APP_PATH.equals(k) ? EXE : null, home, () -> registry);
    }
}
