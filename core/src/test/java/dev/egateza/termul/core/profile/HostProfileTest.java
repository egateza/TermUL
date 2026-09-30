package dev.egateza.termul.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class HostProfileTest {

    @Test
    void createMemberiDefaultYangAman() {
        var p = HostProfile.create("web-1", "10.0.0.1", "ega");

        assertThat(p.port()).isEqualTo(22);
        assertThat(p.authMethod()).isEqualTo(AuthMethod.AGENT);
        assertThat(p.environment()).isEqualTo(EnvironmentTag.NONE);
        assertThat(p.autoSudo()).isFalse();
        assertThat(p.group()).isEmpty();
        assertThat(p.address()).isEqualTo("ega@10.0.0.1");
    }

    @Test
    void normalisasiGroupDanField() {
        var p = new HostProfile(UUID.randomUUID(), " web ", " Produksi // Backend\\api/ ", " host ", 2222, " u ",
                AuthMethod.PASSWORD, "  ", null, null, " ", "", false);

        assertThat(p.name()).isEqualTo("web");
        assertThat(p.group()).isEqualTo("Produksi/Backend/api");
        assertThat(p.host()).isEqualTo("host");
        assertThat(p.privateKeyPath()).isNull();
        assertThat(p.initialDirectory()).isNull();
        assertThat(p.notes()).isNull();
        assertThat(p.address()).isEqualTo("u@host:2222");
    }

    @Test
    void validasiFieldWajib() {
        var id = UUID.randomUUID();
        assertThatThrownBy(() -> new HostProfile(id, "", "", "h", 22, "u", null, null, null, null, null, null, false))
                .hasMessageContaining("Nama");
        assertThatThrownBy(() -> new HostProfile(id, "n", "", "h", 0, "u", null, null, null, null, null, null, false))
                .hasMessageContaining("Port");
        assertThatThrownBy(() -> new HostProfile(id, "n", "", "h", 22, "u", AuthMethod.KEY, null, null, null, null, null, false))
                .hasMessageContaining("private key");
        assertThatThrownBy(() -> new HostProfile(id, "n", "", "h", 22, "u", null, null, id, null, null, null, false))
                .hasMessageContaining("Jump host");
    }

    @Test
    void duplicateMenghasilkanIdBaru() {
        var p = HostProfile.create("web-1", "h", "u");
        var copy = p.duplicate();

        assertThat(copy.id()).isNotEqualTo(p.id());
        assertThat(copy.name()).isEqualTo("web-1 (salinan)");
        assertThat(copy.host()).isEqualTo(p.host());
    }
}
