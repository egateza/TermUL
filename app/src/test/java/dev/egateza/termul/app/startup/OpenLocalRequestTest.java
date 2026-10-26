package dev.egateza.termul.app.startup;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.instance.SingleInstance;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS) // path Explorer (drive letter) hanya absolut di Windows
class OpenLocalRequestTest {

    private static final UUID SHELL = UUID.fromString("0f8e0a6c-6d1c-3b7a-9a49-2c1f5d0e9b11");

    @Test
    void argumenDariExplorerDibaca() {
        var parsed = OpenLocalRequest.parse(new String[] {"--open-local=" + SHELL, "--cwd", "D:\\Proyek Saya"});
        assertThat(parsed).contains(new OpenLocalRequest(SHELL, Path.of("D:\\Proyek Saya")));
    }

    @Test
    void rootDriveDenganKutipTerescapeTetapDikenali() {
        // "%V" untuk C:\ menjadi "C:\" di command line; parser argv membacanya sebagai C:"
        assertThat(OpenLocalRequest.parse(new String[] {"--open-local=" + SHELL, "--cwd", "C:\""}))
                .contains(new OpenLocalRequest(SHELL, Path.of("C:\\")));
        assertThat(OpenLocalRequest.directoryOf("D:")).contains(Path.of("D:\\"));
    }

    @Test
    void pathYangTerpecahDisambungLagi() {
        assertThat(OpenLocalRequest.parse(new String[] {"--open-local=" + SHELL, "--cwd", "D:\\a", "b"}))
                .contains(new OpenLocalRequest(SHELL, Path.of("D:\\a b")));
    }

    @Test
    void tanpaOpenLocalAtauPathRelatifDiabaikan() {
        assertThat(OpenLocalRequest.parse(new String[] {"--tray"})).isEmpty();
        assertThat(OpenLocalRequest.parse(new String[] {"--open-local=" + SHELL, "--cwd", "relatif"})).isEmpty();
        assertThat(OpenLocalRequest.parse(new String[] {"--open-local=" + SHELL})).isEmpty();
        assertThat(OpenLocalRequest.parse(new String[] {"--open-local=" + SHELL, "--cwd", ""})).isEmpty();
    }

    @Test
    void idShellRusakMemakaiDefault() {
        assertThat(OpenLocalRequest.parse(new String[] {"--open-local=bukan-uuid", "--cwd", "D:\\x"}))
                .contains(new OpenLocalRequest(null, Path.of("D:\\x")));
    }

    @Test
    void bolakBalikLewatSingleInstance() {
        var original = new OpenLocalRequest(SHELL, Path.of("D:\\Proyek Ünicode"));
        assertThat(OpenLocalRequest.from(original.toRequest())).contains(original);

        var noShell = new OpenLocalRequest(null, Path.of("C:\\"));
        assertThat(OpenLocalRequest.from(noShell.toRequest())).contains(noShell);

        assertThat(OpenLocalRequest.from(SingleInstance.Request.show())).isEmpty();
        assertThat(OpenLocalRequest.from(new SingleInstance.Request("open-local", "tanpa-baris-baru"))).isEmpty();
    }
}
