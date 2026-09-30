package dev.egateza.myterm.ssh.testing;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Container OpenSSH asli (linuxserver/openssh-server) untuk integration test.
 * User {@value #USER} / {@value #PASSWORD}, sudo diizinkan dengan password.
 */
public final class OpenSshContainer extends GenericContainer<OpenSshContainer> {

    public static final String USER = "dev";
    public static final String PASSWORD = "devpass";
    public static final int PORT = 2222;

    public OpenSshContainer() {
        super(DockerImageName.parse("lscr.io/linuxserver/openssh-server:latest"));
        withEnv("USER_NAME", USER);
        withEnv("USER_PASSWORD", PASSWORD);
        withEnv("PASSWORD_ACCESS", "true");
        withEnv("SUDO_ACCESS", "true");
        withEnv("PUID", "1000");
        withEnv("PGID", "1000");
        withExposedPorts(PORT);
        waitingFor(Wait.forLogMessage(".*\\[ls\\.io-init\\] done\\..*", 1).withStartupTimeout(Duration.ofMinutes(3)));
    }

    public int sshPort() {
        return getMappedPort(PORT);
    }
}
