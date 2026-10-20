package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class LocalProfilesDialogTest {

    @Test
    void environmentSatuVariabelPerBaris() {
        var env = LocalProfilesDialog.parseEnv("""
                # komentar
                JAVA_HOME=/opt/jdk
                PAGER=
                OPTS=-Dx=1 -Dy=2

                """);

        assertThat(env).containsExactly(java.util.Map.entry("JAVA_HOME", "/opt/jdk"), java.util.Map.entry("PAGER", ""),
                java.util.Map.entry("OPTS", "-Dx=1 -Dy=2"));
        assertThat(LocalProfilesDialog.formatEnv(new LinkedHashMap<>(env)))
                .isEqualTo("JAVA_HOME=/opt/jdk\nPAGER=\nOPTS=-Dx=1 -Dy=2\n");
    }

    @Test
    void barisTanpaSamaDenganDitolak() {
        assertThatThrownBy(() -> LocalProfilesDialog.parseEnv("JAVA_HOME")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LocalProfilesDialog.parseEnv("=x")).isInstanceOf(IllegalArgumentException.class);
    }
}
