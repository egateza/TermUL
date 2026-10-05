package dev.egateza.termul.app.update;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.app.ui.Shortcuts;
import dev.egateza.termul.core.Os;
import dev.egateza.termul.update.ReleaseVersion;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.event.HyperlinkEvent;
import org.junit.jupiter.api.Test;

class InstallGuideTest {

    private static final ReleaseVersion VERSION = ReleaseVersion.parse("0.1.140");
    private static final Path DIR = Path.of("D:\\Program Files\\TermUL");

    @Test
    void linkUnduhanZipDanHalamanRilis() {
        assertThat(InstallerInfo.windowsZipName(VERSION)).isEqualTo("TermUL-0.1.140-windows.zip");
        assertThat(InstallerInfo.windowsZip(VERSION)).hasToString(
                "https://github.com/egateza/TermUL/releases/download/v0.1.140/TermUL-0.1.140-windows.zip");
        assertThat(InstallerInfo.releasePage(VERSION)).hasToString(
                "https://github.com/egateza/TermUL/releases/tag/v0.1.140");
    }

    @Test
    void htmlBerisiLinkLangkahDanFolderInstalasi() {
        String html = InstallGuide.html(VERSION, DIR, "Ctrl");
        assertThat(html)
                .contains("href=\"https://github.com/egateza/TermUL/releases/download/v0.1.140/TermUL-0.1.140-windows.zip\"")
                .contains("href=\"https://github.com/egateza/TermUL/releases/tag/v0.1.140\"")
                .contains("D:\\Program Files\\TermUL")
                .contains("Ctrl+")
                .contains("<ol>");
    }

    @Test
    void folderInstalasiDiEscapeDiHtml() {
        // < dan > tidak boleh di path Windows; & boleh dan wajib di-escape di HTML
        assertThat(InstallGuide.html(VERSION, Path.of("D:\\R&D\\TermUL"), "Ctrl")).contains("D:\\R&amp;D\\TermUL")
                .doesNotContain("R&D");
    }

    @Test
    void linkHanyaTerbukaDenganCtrlKlik() throws Exception {
        var opened = new ArrayList<URI>();
        var guide = new InstallGuide(VERSION, DIR, opened::add);
        var url = InstallerInfo.windowsZip(VERSION).toURL();

        guide.fireHyperlinkUpdate(link(guide, url, 0));
        assertThat(opened).as("klik biasa").isEmpty();

        guide.fireHyperlinkUpdate(link(guide, url, Shortcuts.MENU));
        assertThat(opened).as("Ctrl/Cmd+klik").containsExactly(InstallerInfo.windowsZip(VERSION));
    }

    @Test
    void linkDiLuarHalamanRilisTidakDibuka() throws Exception {
        var opened = new ArrayList<URI>();
        var guide = new InstallGuide(VERSION, DIR, opened::add);

        guide.fireHyperlinkUpdate(link(guide, URI.create("https://contoh.invalid/x").toURL(), Shortcuts.MENU));

        assertThat(opened).isEmpty();
        assertThat(InstallGuide.allowed(InstallerInfo.releasePage(VERSION))).isTrue();
    }

    @Test
    void panduanHanyaUntukInstalasiWindowsTanpaOpsiHemat() {
        Map<String, String> windowsApp = Map.of(RestartCommand.JPACKAGE_APP_PATH, "D:\\Program Files\\TermUL\\TermUL.exe");
        assertThat(InstallerInfo.needsReinstall(windowsApp::get, Os.WINDOWS, List.of())).isTrue();
        assertThat(InstallerInfo.installDir(windowsApp::get, Os.WINDOWS)).contains(DIR);

        var marked = Map.of(RestartCommand.JPACKAGE_APP_PATH, "D:\\Program Files\\TermUL\\TermUL.exe",
                InstallerInfo.MEMORY_MARKER, "true");
        assertThat(InstallerInfo.needsReinstall(marked::get, Os.WINDOWS, List.of())).isFalse();
        assertThat(InstallerInfo.needsReinstall(windowsApp::get, Os.WINDOWS, List.of("-XX:+UseSerialGC")))
                .as("installer v0.1.139").isFalse();

        Map<String, String> portable = Map.of();
        assertThat(InstallerInfo.needsReinstall(portable::get, Os.WINDOWS, List.of())).as("JAR portable").isFalse();
        assertThat(InstallerInfo.needsReinstall(windowsApp::get, Os.MAC, List.of())).as("macOS").isFalse();
    }

    private static HyperlinkEvent link(InstallGuide guide, java.net.URL url, int modifiers) {
        InputEvent click = new MouseEvent(guide, MouseEvent.MOUSE_CLICKED, 0, modifiers, 1, 1, 1, false,
                MouseEvent.BUTTON1);
        return new HyperlinkEvent(guide, HyperlinkEvent.EventType.ACTIVATED, url, url.toString(), null, click);
    }
}
