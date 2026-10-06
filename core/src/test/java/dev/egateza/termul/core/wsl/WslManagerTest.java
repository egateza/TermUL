package dev.egateza.termul.core.wsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WslManagerTest {

    /** wsl.exe palsu: menyimpan distro terpasang/berjalan dan perintah yang dijalankan. */
    private static final class FakeRunner implements WslRunner {
        final List<String> installed = new ArrayList<>(List.of("Ubuntu-24.04", "Debian"));
        final Set<String> running = new HashSet<>();
        final List<List<String>> commands = new ArrayList<>();
        final List<FakeHandle> spawned = new ArrayList<>();
        boolean available = true;
        boolean startDies;
        int sshExit;

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public Result run(List<String> args, Duration timeout) {
            commands.add(args);
            if (args.equals(List.of("--list", "--quiet"))) {
                return installed.isEmpty() ? new Result(-1, "Tidak ada distro") : new Result(0, String.join("\r\n", installed));
            }
            if (args.equals(List.of("--list", "--quiet", "--running"))) {
                return running.isEmpty() ? new Result(-1, "Tidak ada distro berjalan") : new Result(0, String.join("\n", running));
            }
            if (args.getFirst().equals("--terminate")) {
                running.remove(args.get(1));
                return new Result(0, "");
            }
            if (args.getFirst().equals("--shutdown")) {
                running.clear();
                return new Result(0, "");
            }
            if (args.contains("whoami")) {
                return new Result(0, "ega\n");
            }
            if (args.contains("sh")) {
                return new Result(sshExit, sshExit == 0 ? "" : "ssh: unrecognized service");
            }
            return new Result(1, "?");
        }

        @Override
        public Handle spawn(List<String> args) {
            commands.add(args);
            var handle = new FakeHandle();
            if (startDies) {
                handle.alive = false;
            } else {
                running.add(args.get(1));
            }
            spawned.add(handle);
            return handle;
        }
    }

    private static final class FakeHandle implements WslRunner.Handle {
        boolean alive = true;

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public void stop() {
            alive = false;
        }
    }

    private final FakeRunner runner = new FakeRunner();
    private final WslManager wsl = new WslManager(runner, Duration.ofMillis(200), Duration.ofMillis(5));

    @Test
    void listMenggabungkanDistroTerpasangDanStatusBerjalan() throws Exception {
        runner.running.add("debian"); // nama dari --running bisa beda huruf besar/kecil

        assertThat(wsl.list()).containsExactly(new WslDistro("Ubuntu-24.04", false), new WslDistro("Debian", true));
    }

    @Test
    void listMelewatiDistroInternalDockerDesktop() throws Exception {
        runner.installed.addAll(List.of("docker-desktop", "docker-desktop-data"));

        assertThat(wsl.list()).extracting(WslDistro::name).containsExactly("Ubuntu-24.04", "Debian");
    }

    @Test
    void listKosongKalauWslBelumAdaDistro() throws Exception {
        runner.installed.clear();

        assertThat(wsl.list()).isEmpty();
    }

    @Test
    void wslExeTidakAdaMenjadiWslException() {
        runner.available = false;

        assertThat(wsl.available()).isFalse();
        assertThatThrownBy(wsl::list).isInstanceOf(WslException.class).hasMessageContaining("wsl.exe");
    }

    @Test
    void startMenahanKeepAliveSampaiDistroBerjalan() throws Exception {
        wsl.start("Debian");

        assertThat(runner.commands).contains(List.of("--distribution", "Debian", "--exec", "sleep", "infinity"));
        assertThat(wsl.isKeptAlive("debian")).isTrue();
        assertThat(wsl.isRunning("Debian")).isTrue();
    }

    @Test
    void startDuaKaliTidakMembuatKeepAliveKedua() throws Exception {
        wsl.start("Debian");
        wsl.start("Debian");

        assertThat(runner.spawned).hasSize(1);
    }

    @Test
    void startGagalKalauKeepAliveLangsungMati() {
        runner.startDies = true;

        assertThatThrownBy(() -> wsl.start("Debian")).isInstanceOf(WslException.class).hasMessageContaining("berhenti");
        assertThat(wsl.isKeptAlive("Debian")).isFalse();
    }

    @Test
    void stopMelepasKeepAliveLaluTerminate() throws Exception {
        wsl.start("Debian");

        wsl.stop("Debian");

        assertThat(runner.spawned.getFirst().alive).isFalse();
        assertThat(runner.commands.getLast()).containsExactly("--terminate", "Debian");
        assertThat(wsl.isKeptAlive("Debian")).isFalse();
    }

    @Test
    void shutdownAllMelepasSemuaKeepAlive() throws Exception {
        wsl.start("Debian");
        wsl.start("Ubuntu-24.04");

        wsl.shutdownAll();

        assertThat(runner.spawned).allSatisfy(h -> assertThat(h.alive).isFalse());
        assertThat(runner.commands.getLast()).containsExactly("--shutdown");
    }

    @Test
    void closeMelepasKeepAliveTanpaMenghentikanDistro() throws Exception {
        wsl.start("Debian");

        wsl.close();

        assertThat(runner.spawned.getFirst().alive).isFalse();
        assertThat(runner.commands).noneMatch(c -> c.contains("--terminate") || c.contains("--shutdown"));
    }

    @Test
    void prepareSshMenjalankanDistroLaluSshdSebagaiRoot() throws Exception {
        assertThat(wsl.prepareSsh("Debian")).isTrue();

        assertThat(runner.commands.getLast())
                .containsExactly("--distribution", "Debian", "--user", "root", "--exec", "sh", "-c", WslManager.START_SSHD);
    }

    @Test
    void sshdGagalDilaporkanFalse() throws Exception {
        runner.sshExit = 1;

        assertThat(wsl.startSsh("Debian")).isFalse();
    }

    @Test
    void defaultUserDariWhoami() throws Exception {
        assertThat(wsl.defaultUser("Debian")).contains("ega");
    }

    @Test
    void namaDistroTidakValidDitolakSebelumMenjalankanWslExe() {
        assertThatThrownBy(() -> wsl.start("Debian & calc")).isInstanceOf(WslException.class);
        assertThatThrownBy(() -> wsl.stop("-d")).isInstanceOf(WslException.class);
        assertThat(runner.commands).isEmpty();
    }

    @Test
    void namesMelewatiBarisYangBukanNamaDistro() {
        assertThat(WslManager.names("Ubuntu\r\n\r\n  docker-desktop  \nBukan nama distro\n"))
                .containsExactly("Ubuntu", "docker-desktop");
    }

    @Test
    void outputUtf16LeDanUtf8DidecodeBenar() {
        byte[] utf16 = "Ubuntu\r\nDebian\r\n".getBytes(StandardCharsets.UTF_16LE);
        byte[] bom = new byte[utf16.length + 2];
        bom[0] = (byte) 0xFF;
        bom[1] = (byte) 0xFE;
        System.arraycopy(utf16, 0, bom, 2, utf16.length);

        assertThat(WslOutput.lines(WslOutput.decode(utf16))).containsExactly("Ubuntu", "Debian");
        assertThat(WslOutput.lines(WslOutput.decode(bom))).containsExactly("Ubuntu", "Debian");
        assertThat(WslOutput.decode("ega\n".getBytes(StandardCharsets.UTF_8))).isEqualTo("ega\n");
        assertThat(WslOutput.decode(new byte[0])).isEmpty();
    }

    @Test
    void runnerAsliTanpaWslExeTidakTersedia() throws IOException {
        var missing = new ProcessWslRunner(java.nio.file.Path.of("tidak-ada", "wsl.exe"));

        assertThat(missing.available()).isFalse();
    }
}
