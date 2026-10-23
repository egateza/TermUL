package dev.egateza.termul.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class HostProfileTest {

    @Test
    void createMemberiDefaultYangAman() {
        var p = HostProfile.create("web-1", "10.0.0.1", "dev");

        assertThat(p.port()).isEqualTo(22);
        assertThat(p.authMethod()).isEqualTo(AuthMethod.AGENT);
        assertThat(p.environment()).isEqualTo(EnvironmentTag.NONE);
        assertThat(p.autoSudo()).isFalse();
        assertThat(p.group()).isEmpty();
        assertThat(p.address()).isEqualTo("dev@10.0.0.1");
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

    @Test
    void distroWslOpsionalDanDipertahankanSaatProfilDiubah() {
        var p = HostProfile.create("Ubuntu", "localhost", "ega");
        assertThat(p.wslDistro()).isNull();

        var wsl = p.withWslDistro(" Ubuntu-24.04 ");

        assertThat(wsl.wslDistro()).isEqualTo("Ubuntu-24.04");
        assertThat(wsl.withGroup("WSL").withOs(null).withTerminalTheme("x").withJumpHostId(null).wslDistro())
                .isEqualTo("Ubuntu-24.04");
        assertThat(wsl.duplicate().wslDistro()).isEqualTo("Ubuntu-24.04");
        assertThat(wsl.withPort(2211).port()).isEqualTo(2211);
        assertThat(wsl.withPort(2211).wslDistro()).isEqualTo("Ubuntu-24.04");
        assertThat(wsl.withWslDistro(" ").wslDistro()).isNull();
    }

    @Test
    void distroWslTidakValidDitolak() {
        var p = HostProfile.create("x", "localhost", "u");

        assertThatThrownBy(() -> p.withWslDistro("Ubuntu & calc")).hasMessageContaining("distro WSL");
        assertThatThrownBy(() -> p.withWslDistro("-d")).hasMessageContaining("distro WSL");
    }

    @Test
    void profilFtpMengabaikanFieldKhususSsh() {
        var jump = UUID.randomUUID();
        var p = new HostProfile(UUID.randomUUID(), "ftp", "", "files.example.com", 21, "web", AuthMethod.KEY,
                "C:/keys/id", jump, EnvironmentTag.PROD, null, null, true, null, null, "Ubuntu", Protocol.FTPS);

        assertThat(p.isFtp()).isTrue();
        assertThat(p.authMethod()).isEqualTo(AuthMethod.PASSWORD);
        assertThat(p.privateKeyPath()).isNull();
        assertThat(p.jumpHostId()).isNull();
        assertThat(p.autoSudo()).isFalse();
        assertThat(p.wslDistro()).isNull();
        assertThat(p.address()).isEqualTo("ftps://web@files.example.com");
    }

    @Test
    void protokolDefaultSshDanPortIkutProtokol() {
        var ssh = HostProfile.create("h", "h", "u");
        assertThat(ssh.protocol()).isEqualTo(Protocol.SSH);
        assertThat(ssh.isFtp()).isFalse();

        var implicit = ssh.withProtocol(Protocol.FTPS_IMPLICIT);
        assertThat(implicit.port()).isEqualTo(990);
        assertThat(implicit.address()).isEqualTo("ftps://u@h");
        var plain = implicit.withProtocol(Protocol.FTP);
        assertThat(plain.port()).isEqualTo(21);
        assertThat(plain.address()).isEqualTo("ftp://u@h");
        assertThat(plain.withProtocol(Protocol.SSH).port()).isEqualTo(22);

        var custom = ssh.withPort(2121).withProtocol(Protocol.FTP);
        assertThat(custom.port()).isEqualTo(2121); // port pilihan user tidak diubah
        assertThat(custom.address()).isEqualTo("ftp://u@h:2121");
        assertThat(Protocol.FTP.isEncrypted()).isFalse();
        assertThat(Protocol.FTPS.isEncrypted()).isTrue();
    }

    @Test
    void sensitifIkutEnvironmentKecualiDipilihEksplisit() {
        var p = HostProfile.create("h", "h", "u");
        assertThat(p.sensitive()).isNull();
        assertThat(p.needsGuard()).isFalse();

        var prod = new HostProfile(p.id(), "h", "", "h", 22, "u", null, null, null, EnvironmentTag.PROD, null, null,
                false);
        assertThat(prod.needsGuard()).isTrue();
        assertThat(prod.withSensitive(false).needsGuard()).isFalse(); // PROD tapi user memilih tidak sensitif
        assertThat(p.withSensitive(true).needsGuard()).isTrue();     // mis. staging berisi data asli
    }

    @Test
    void flagSensitifDipertahankanSaatProfilDiubah() {
        var p = HostProfile.create("h", "h", "u").withSensitive(true);

        assertThat(p.withGroup("A").sensitive()).isTrue();
        assertThat(p.withPort(2222).sensitive()).isTrue();
        assertThat(p.withOs(null).sensitive()).isTrue();
        assertThat(p.withTerminalTheme("x").sensitive()).isTrue();
        assertThat(p.withWslDistro("Ubuntu").sensitive()).isTrue();
        assertThat(p.withJumpHostId(null).sensitive()).isTrue();
        assertThat(p.withProtocol(Protocol.FTP).sensitive()).isTrue();
        assertThat(p.duplicate().sensitive()).isTrue();
    }
}
