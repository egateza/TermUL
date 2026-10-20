package dev.egateza.termul.app.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.localshell.LocalProfile;
import dev.egateza.termul.core.localshell.LocalProfileStore;
import dev.egateza.termul.localterm.DetectedShell;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalProfilesTest {

    @TempDir
    Path dir;

    private final DetectedShell zsh = new DetectedShell("/bin/zsh", "zsh", List.of("/bin/zsh", "-l"));
    private final DetectedShell bash = new DetectedShell("/bin/bash", "bash", List.of("/bin/bash", "-l"));

    private LocalProfiles profiles(LocalProfileStore store) {
        var p = new LocalProfiles(store, distros -> distros.stream()
                .map(d -> new DetectedShell("wsl:" + d, "WSL · " + d, List.of("wsl.exe", "-d", d))).toList());
        p.setDetected(List.of(zsh, bash));
        return p;
    }

    @Test
    void tanpaPilihanDefaultAdalahShellTerdeteksiPertama() {
        var store = new LocalProfileStore(dir.resolve("p.json"));
        var custom = LocalProfile.create("Proyek", List.of("/bin/zsh", "-l"));
        store.save(custom);

        var choices = profiles(store).choices(List.of());

        assertThat(choices).extracting(LocalProfiles.Choice::name).containsExactly("zsh", "bash", "Proyek");
        assertThat(choices).extracting(LocalProfiles.Choice::isDefault).containsExactly(true, false, false);
        assertThat(choices.getFirst().detected()).isTrue();
        assertThat(choices.get(2).detected()).isFalse();
    }

    @Test
    void defaultTersimpanDitaruhPalingAtas() {
        var store = new LocalProfileStore(dir.resolve("p.json"));
        var custom = LocalProfile.create("Proyek", List.of("/bin/zsh", "-l"));
        store.save(custom);
        store.setDefault(custom.id());

        var p = profiles(store);

        assertThat(p.choices(List.of())).extracting(LocalProfiles.Choice::name).containsExactly("Proyek", "zsh", "bash");
        assertThat(p.defaultChoice(List.of())).map(LocalProfiles.Choice::id).contains(custom.id());
    }

    @Test
    void defaultShellTerdeteksiDanWslBolehDipilih() {
        var store = new LocalProfileStore(dir.resolve("p.json"));
        var p = profiles(store);
        var ubuntuId = new DetectedShell("wsl:Ubuntu", "x", List.of("x")).id();
        store.setDefault(ubuntuId);

        assertThat(p.defaultChoice(List.of("Ubuntu"))).map(LocalProfiles.Choice::name).contains("WSL · Ubuntu");
        // distro tidak lagi terpasang / WSL dimatikan: kembali ke shell terdeteksi pertama
        assertThat(p.defaultChoice(List.of())).map(LocalProfiles.Choice::name).contains("zsh");
        assertThat(p.find(bash.id(), List.of())).map(LocalProfiles.Choice::commandLine).contains("/bin/bash -l");
    }
}
