package dev.egateza.termul.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.egateza.termul.core.config.ValidationHooks.Hook;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidationHooksTest {

    @Test
    void doubleStarCocokLintasDirektori() {
        var hook = new Hook("/etc/nginx/**", "nginx -t");

        assertThat(hook.matches("/etc/nginx/nginx.conf")).isTrue();
        assertThat(hook.matches("/etc/nginx/sites-available/app")).isTrue();
        assertThat(hook.matches("/etc/nginxx/nginx.conf")).isFalse();
        assertThat(hook.matches("/etc/nginx")).isFalse();
    }

    @Test
    void singleStarDanTandaTanyaHanyaSatuSegmen() {
        var star = new Hook("/etc/*.conf", "true");
        var question = new Hook("/etc/a?c", "true");

        assertThat(star.matches("/etc/app.conf")).isTrue();
        assertThat(star.matches("/etc/sub/app.conf")).isFalse();
        assertThat(question.matches("/etc/abc")).isTrue();
        assertThat(question.matches("/etc/a/c")).isFalse();
    }

    @Test
    void karakterRegexDiPolaDiperlakukanLiteral() {
        var hook = new Hook("/etc/app.d/(x)+.conf", "true");

        assertThat(hook.matches("/etc/app.d/(x)+.conf")).isTrue();
        assertThat(hook.matches("/etc/appXd/xx.conf")).isFalse();
    }

    @Test
    void hookPertamaYangCocokDipakai() {
        var hooks = new ValidationHooks(List.of(
                new Hook("/etc/nginx/sites-available/special", "echo khusus"),
                new Hook("/etc/nginx/**", "nginx -t")));

        assertThat(hooks.find("/etc/nginx/sites-available/special")).map(Hook::command).contains("echo khusus");
        assertThat(hooks.find("/etc/nginx/nginx.conf")).map(Hook::command).contains("nginx -t");
        assertThat(hooks.find("/home/ega/x.conf")).isEmpty();
    }

    @Test
    void bawaanMemvalidasiNginxSshdDanSudoers() {
        var hooks = ValidationHooks.defaults();

        assertThat(hooks.find("/etc/nginx/sites-available/x")).map(Hook::command).contains("nginx -t");
        assertThat(hooks.find("/etc/ssh/sshd_config")).map(Hook::command).contains("sshd -t");
        assertThat(hooks.find("/etc/sudoers.d/90-app")).map(Hook::command).contains("visudo -c");
    }

    @Test
    void polaHarusAbsolutDanCommandSatuBaris() {
        assertThatThrownBy(() -> new Hook("etc/nginx/**", "nginx -t")).hasMessageContaining("absolut");
        assertThatThrownBy(() -> new Hook("/etc/x", " ")).hasMessageContaining("wajib");
        assertThatThrownBy(() -> new Hook("/etc/x", "true\nrm -rf /")).hasMessageContaining("satu baris");
    }

    @Test
    void textDanParseBolakBalik() {
        var hooks = ValidationHooks.defaults();

        assertThat(ValidationHooks.parse(hooks.text())).isEqualTo(hooks);
        assertThat(ValidationHooks.parse("# komentar\n\n/etc/x = test -s \"$TERMUL_FILE\"\n").hooks())
                .containsExactly(new Hook("/etc/x", "test -s \"$TERMUL_FILE\""));
    }

    @Test
    void parseMenyebutNomorBaris() {
        assertThatThrownBy(() -> ValidationHooks.parse("/etc/x = true\nsalah"))
                .hasMessageContaining("Baris 2");
        assertThatThrownBy(() -> ValidationHooks.parse("relatif = true"))
                .hasMessageContaining("Baris 1").hasMessageContaining("absolut");
    }

    @Test
    void listKosongBerartiTanpaValidasiNullBerartiBawaan() {
        assertThat(new ValidationHooks(List.of()).hooks()).isEmpty();
        assertThat(new ValidationHooks(null)).isEqualTo(ValidationHooks.defaults());
    }

    @Test
    void configLamaMemakaiBawaanDanPerubahanTersimpan(@TempDir Path dir) throws Exception {
        var file = dir.resolve("config.json");
        Files.writeString(file, "{\"terminalFontSize\": 16.0}");
        var store = new ConfigStore(file);

        assertThat(store.load().validationHooks()).isEqualTo(ValidationHooks.defaults());

        var custom = new ValidationHooks(List.of(new Hook("/srv/app/*.yml", "app check")));
        store.save(store.load().withValidationHooks(custom));

        assertThat(new ConfigStore(file).load().validationHooks()).isEqualTo(custom);
    }
}
