package dev.egateza.termul.core.localshell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalProfileStoreTest {

    @TempDir
    Path dir;

    @Test
    void simpanMuatUlangDanUrutNama() throws Exception {
        var store = new LocalProfileStore(dir.resolve("local-profiles.json"));
        store.load();
        var env = new LinkedHashMap<String, String>();
        env.put("JAVA_HOME", "/opt/jdk");
        env.put("PAGER", "");
        var git = new LocalProfile(UUID.randomUUID(), "Git Bash", List.of("C:\\Git\\bin\\bash.exe", "--login", "-i"),
                "~/proyek", env, "builtin:prod-red");
        var cmd = LocalProfile.create("cmd", List.of("cmd.exe"));
        var calls = new AtomicInteger();
        store.addListener(s -> calls.incrementAndGet());

        store.save(git);
        store.save(cmd);
        store.setDefault(git.id());

        var reloaded = new LocalProfileStore(dir.resolve("local-profiles.json")).load();
        assertThat(reloaded.profiles()).extracting(LocalProfile::name).containsExactly("cmd", "Git Bash");
        assertThat(reloaded.defaultId()).isEqualTo(git.id());
        var loaded = reloaded.profiles().get(1);
        assertThat(loaded).isEqualTo(git);
        assertThat(loaded.environment().keySet()).containsExactly("JAVA_HOME", "PAGER"); // urutan tetap
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void hapusProfilDefaultMengembalikanDefaultOtomatis() {
        var store = new LocalProfileStore(dir.resolve("p.json"));
        var p = LocalProfile.create("fish", List.of("/opt/homebrew/bin/fish", "-l"));
        store.save(p);
        store.setDefault(p.id());

        store.delete(p.id());

        assertThat(store.snapshot().profiles()).isEmpty();
        assertThat(store.snapshot().defaultId()).isNull();
    }

    @Test
    void fileRusakDisisihkan() throws Exception {
        Path file = dir.resolve("local-profiles.json");
        Files.writeString(file, "{ rusak");

        assertThat(new LocalProfileStore(file).load().profiles()).isEmpty();
        assertThat(Files.exists(file)).isFalse();
        try (var files = Files.list(dir)) {
            assertThat(files.map(f -> f.getFileName().toString()))
                    .anyMatch(n -> n.startsWith("local-profiles.json.corrupt-"));
        }
    }

    @Test
    void validasiProfil() {
        assertThatThrownBy(() -> LocalProfile.create(" ", List.of("zsh"))).hasMessageContaining("Nama");
        assertThatThrownBy(() -> LocalProfile.create("x", List.of())).hasMessageContaining("Perintah");
        assertThatThrownBy(() -> LocalProfile.create("x", List.of("zsh", "-c", "a\nb")))
                .hasMessageContaining("satu baris");
        assertThatThrownBy(() -> new LocalProfile(UUID.randomUUID(), "x", List.of("zsh"), "", Map.of("1A", "v"), null))
                .hasMessageContaining("tidak valid");
        assertThat(new LocalProfile(UUID.randomUUID(), "x", List.of("cmd.exe"), "",
                Map.of("ProgramFiles(x86)", "C:\\x"), " ").terminalTheme()).isNull();
    }
}
