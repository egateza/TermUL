package dev.egateza.termul.app.wsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.egateza.termul.core.profile.AuthMethod;
import dev.egateza.termul.core.profile.HostProfile;
import dev.egateza.termul.core.profile.ProfileSnapshot;
import dev.egateza.termul.core.wsl.WslManager;
import dev.egateza.termul.core.wsl.WslRunner;
import dev.egateza.termul.ssh.SshConnectException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class WslControllerTest {

    /** wsl.exe palsu dengan satu distro "Ubuntu". */
    private static final class FakeRunner implements WslRunner {
        final Set<String> running = new HashSet<>();
        final List<List<String>> commands = new CopyOnWriteArrayList<>(); // refresh di EDT ikut menulis
        int sshExit;
        boolean socket;

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public Result run(List<String> args, Duration timeout) {
            commands.add(args);
            if (args.equals(List.of("--list", "--quiet"))) {
                return new Result(0, "Ubuntu");
            }
            if (args.equals(List.of("--list", "--quiet", "--running"))) {
                return running.isEmpty() ? new Result(1, "") : new Result(0, String.join("\n", running));
            }
            if (args.getLast().contains("configtest")) { // pemeriksaan sshd
                return new Result(0, "installed=yes\nsystemd=yes\nsocket=" + (socket ? "active" : "inactive")
                        + "\nconfigtest=ok\nconfig_port=2211\nlisten_tool=ss\nlisten=" + (socket ? 22 : 2211) + "\n");
            }
            return args.contains("sh") ? new Result(sshExit, "") : new Result(0, "");
        }

        @Override
        public Handle spawn(List<String> args) {
            commands.add(args);
            running.add(args.get(1));
            return new Handle() {
                @Override
                public boolean isAlive() {
                    return true;
                }

                @Override
                public void stop() {
                }
            };
        }

        boolean spawned() {
            return commands.stream().anyMatch(c -> c.contains("sleep"));
        }
    }

    private final FakeRunner runner = new FakeRunner();
    private final HostProfile wslProfile =
            new HostProfile(java.util.UUID.randomUUID(), "Ubuntu", "", "localhost", 2211, "ega", null, null, null, null,
                    null, null, false, null, null, "Ubuntu");
    private final HostProfile server = HostProfile.create("web", "10.0.0.1", "dev");
    private ProfileSnapshot snapshot = new ProfileSnapshot(1, List.of(), List.of(wslProfile, server));
    private boolean enabled = true;
    private final List<HostProfile> saved = new CopyOnWriteArrayList<>();

    private WslController controller() {
        return new WslController(null, new WslManager(runner), () -> enabled, () -> snapshot, Runnable::run,
                new WslController.Host() {
                    @Override
                    public void wslRunningChanged(List<String> running) {
                    }

                    @Override
                    public Optional<HostProfile> createProfile(java.awt.Component parent, HostProfile template) {
                        return Optional.empty();
                    }

                    @Override
                    public void saveProfile(HostProfile profile) {
                        saved.add(profile);
                    }
                });
    }

    @Test
    void prepareMenjalankanDistroDanSshdUntukProfilWsl() throws Exception {
        controller().prepare(wslProfile, false);

        assertThat(runner.spawned()).isTrue();
        assertThat(runner.commands).anyMatch(c -> c.containsAll(List.of("--user", "root", "sh")));
    }

    @Test
    void prepareTidakMelakukanApaPunUntukServerBiasaAtauSaatFiturMati() throws Exception {
        controller().prepare(server, false);
        enabled = false;
        controller().prepare(wslProfile, false);

        assertThat(runner.commands).isEmpty();
    }

    @Test
    void reconnectOtomatisTidakMenyalakanDistroYangBerhenti() {
        assertThatThrownBy(() -> controller().prepare(wslProfile, true))
                .isInstanceOf(SshConnectException.class)
                .hasMessageContaining("Ubuntu");
        assertThat(runner.spawned()).isFalse();
    }

    @Test
    void reconnectOtomatisKeDistroYangBerjalanTetapMenyiapkanSshd() throws Exception {
        runner.running.add("Ubuntu");

        controller().prepare(wslProfile, true);

        assertThat(runner.commands).anyMatch(c -> c.contains("sh"));
    }

    @Test
    void sshSocketDiPortLainMenjadiPesanYangMenyebutMasalahnya() {
        runner.socket = true;

        assertThatThrownBy(() -> controller().prepare(wslProfile, false))
                .isInstanceOf(SshConnectException.class)
                .hasMessageContaining("ssh.socket")
                .hasMessageContaining("Periksa SSH");
    }

    @Test
    void sshdGagalMenjadiSshConnectException() {
        runner.sshExit = 1;

        assertThatThrownBy(() -> controller().prepare(wslProfile, false))
                .isInstanceOf(SshConnectException.class)
                .hasMessageContaining("Periksa SSH");
    }

    @Test
    void prepareMemakaiProfilTerbaruDariStore() throws Exception {
        snapshot = new ProfileSnapshot(1, List.of(), List.of(wslProfile.withWslDistro(null)));

        controller().prepare(wslProfile, false); // distro sudah dilepas dari profil di store

        assertThat(runner.commands).isEmpty();
    }

    @Test
    void portBerikutnyaSetelahPortProfilWsl() {
        assertThat(WslController.nextPort(new ProfileSnapshot(1, List.of(), List.of(server))))
                .isEqualTo(WslController.FIRST_PORT);

        var a = HostProfile.create("a", "localhost", "u").withWslDistro("A");
        var b = new HostProfile(a.id(), "b", "", "localhost", 2224, "u", null, null, null, null, null, null, false,
                null, null, "B");
        assertThat(WslController.nextPort(new ProfileSnapshot(1, List.of(), List.of(server, b)))).isEqualTo(2225);
        // profil WSL lama di port 22 tidak membuat port baru turun di bawah 2222
        assertThat(WslController.nextPort(new ProfileSnapshot(1, List.of(), List.of(a)))).isEqualTo(2222);
    }

    @Test
    void templateProfilSshKeLocalhost() {
        var t = WslController.template("Ubuntu-22.04", "ega", 2222);

        assertThat(t.name()).isEqualTo("Ubuntu-22.04");
        assertThat(t.host()).isEqualTo("localhost");
        assertThat(t.port()).isEqualTo(2222);
        assertThat(t.username()).isEqualTo("ega");
        assertThat(t.group()).isEmpty();
        assertThat(t.authMethod()).isEqualTo(AuthMethod.PASSWORD);
        assertThat(t.wslDistro()).isEqualTo("Ubuntu-22.04");
    }

    @Test
    void installedNamesKosongSaatFiturMati() {
        enabled = false;

        assertThat(controller().installedNames()).isEmpty();
    }

    @Test
    void panduanMemakaiPortProfil() {
        String commands = WslManagerDialog.guideCommands(2223);

        assertThat(commands).contains("Port 2223").contains("openssh-server").doesNotContain("{0}");
    }

    @Test
    void portDariSshdConfigMengutamakanSelain22() {
        assertThat(WslController.pickPort(Set.of(22, 2211))).isEqualTo(2211);
        assertThat(WslController.pickPort(Set.of(2300, 2211))).isEqualTo(2211);
        assertThat(WslController.pickPort(Set.of(22))).isEqualTo(22);
        assertThat(WslController.pickPort(Set.of())).isNull();
    }

    @Test
    void portDiTabelUntukDistroTanpaProfilDipakaiSetelahnya() {
        var c = controller();
        assertThat(c.knownPort("Debian")).isNull();

        c.setPort("Debian", 2299);

        assertThat(c.knownPort("debian")).isEqualTo(2299);
        assertThat(saved).isEmpty();
    }

    @Test
    void portDiTabelUntukDistroBerprofilMengubahProfil() {
        var c = controller();
        assertThat(c.knownPort("Ubuntu")).isEqualTo(2211);

        c.setPort("Ubuntu", 2233);

        assertThat(saved).singleElement().satisfies(p -> {
            assertThat(p.id()).isEqualTo(wslProfile.id());
            assertThat(p.port()).isEqualTo(2233);
            assertThat(p.wslDistro()).isEqualTo("Ubuntu");
        });
    }
}
